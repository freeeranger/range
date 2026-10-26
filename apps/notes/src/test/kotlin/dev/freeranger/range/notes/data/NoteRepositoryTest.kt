package dev.freeranger.range.notes.data

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.io.File
import java.time.ZoneOffset
import kotlin.io.path.createTempDirectory

/** Runs the real Room database in memory. */
@RunWith(RobolectricTestRunner::class)
class NoteRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), NotesDatabase::class.java)
        .setDriver(AndroidSQLiteDriver())
        .build()
    private var now: Instant = Instant.parse("2026-10-23T08:00:00Z")
    private val cipher = FakeCipher()
    private val dir = createTempDirectory("images").toFile()
    private val images = NoteImages(dir, cipher)
    private val repository = NoteRepository(database.notes(), cipher, images) { Clock.fixed(now, ZoneOffset.UTC) }

    @After
    fun close() = database.close()

    @Test
    fun `a note left blank is discarded, and one written in is kept`() = runTest {
        val blank = repository.create(checklist = true)
        repository.discardIfBlank(blank)
        assertNull(repository.get(blank))

        val written = repository.create(checklist = false)
        repository.save(repository.get(written)!!.copy(body = "Call the plumber"))
        repository.discardIfBlank(written)
        assertEquals("Call the plumber", repository.get(written)!!.body)
    }

    @Test
    fun `saving only moves the edit time when something changed, and pinning never does`() = runTest {
        val id = repository.create(checklist = false)
        val created = repository.get(id)!!
        now = now.plusSeconds(60)
        repository.save(created)
        assertEquals(created.editedAt, repository.get(id)!!.editedAt)

        repository.save(created.copy(title = "Groceries"))
        assertEquals(now, repository.get(id)!!.editedAt)

        now = now.plusSeconds(60)
        repository.setPinned(id, true)
        assertTrue(repository.get(id)!!.pinned)
        assertEquals(now.minusSeconds(60), repository.get(id)!!.editedAt)
    }

    @Test
    fun `pinned notes come first, then the latest edited`() = runTest {
        val old = repository.create(false).also { repository.save(repository.get(it)!!.copy(title = "old")) }
        now = now.plusSeconds(60)
        val new = repository.create(false).also { repository.save(repository.get(it)!!.copy(title = "new")) }
        val pinned = repository.create(false).also { repository.save(repository.get(it)!!.copy(title = "pinned")) }
        repository.setPinned(pinned, true)
        now = now.plusSeconds(60)
        repository.save(repository.get(old)!!.copy(title = "old, edited"))
        assertEquals(listOf(pinned, old, new), repository.notes.first().map { it.id })
    }

    @Test
    fun `undoing a delete puts the note back as it was`() = runTest {
        val id = repository.create(checklist = true)
        repository.save(repository.get(id)!!.copy(title = "Packing", items = listOf(ChecklistItem(1, "Charger", checked = true))))
        val note = repository.get(id)!!
        val deleted = repository.delete(id)!!
        assertNull(repository.get(id))
        repository.restore(deleted)
        assertEquals(note, repository.get(id))
    }

    @Test
    fun `a backup restores every note exactly, and anything else is turned away untouched`() = runTest {
        val text = repository.create(false).also { repository.save(repository.get(it)!!.copy(title = "Ideas", body = "# Big\n- **one**")) }
        val list = repository.create(true).also {
            repository.save(repository.get(it)!!.copy(items = listOf(ChecklistItem(1, "Milk"), ChecklistItem(2, "Eggs", checked = true))))
        }
        repository.setPinned(list, true)
        val notes = repository.notes.first()
        val backup = repository.exportBackup()

        repository.delete(text)
        assertFalse(repository.importBackup("""{"format":1,"tasks":[],"completions":[]}"""))
        assertFalse(repository.importBackup("""{"format":2,"exercises":[],"workouts":[],"entries":[],"sets":[]}"""))
        assertEquals(1, repository.notes.first().size)

        assertTrue(repository.importBackup(backup))
        assertEquals(notes, repository.notes.first())
    }

    @Test
    fun `text and checklists turn into each other line by line`() {
        val note = Note(title = "Shop", body = "- milk\n\n- eggs\nbread", createdAt = now, editedAt = now)
        var next = 0L
        val checklist = note.toChecklist { ++next }
        assertEquals(listOf("milk", "eggs", "bread"), checklist.items!!.map { it.text })
        assertEquals("", checklist.body)

        val ticked = checklist.copy(items = checklist.items!!.mapIndexed { i, it -> it.copy(checked = i == 0) })
        assertEquals("eggs\nbread\nmilk", ticked.toText().body)
        assertEquals("Shop\n\n☑ milk\n☐ eggs\n☐ bread", ticked.shareText())
    }

    @Test
    fun `a locked note keeps only its title in the clear, and comes back whole once unlocked`() = runTest {
        val id = repository.create(checklist = true)
        repository.save(repository.get(id)!!.copy(title = "Bank", items = listOf(ChecklistItem(1, "PIN 4821"))))
        val editedAt = repository.get(id)!!.editedAt
        now = now.plusSeconds(60)
        repository.setLocked(id, true)

        val stored = repository.get(id)!!
        assertTrue(stored.locked)
        assertEquals("", stored.body)
        assertNull(stored.items)
        assertFalse("4821" in stored.secret!!)
        assertEquals(editedAt, stored.editedAt)
        assertEquals(listOf("PIN 4821"), repository.reveal(stored).items!!.map { it.text })

        // Edits while unlocked stay encrypted.
        repository.save(repository.reveal(stored).copy(items = listOf(ChecklistItem(1, "PIN 9930"))))
        assertFalse("9930" in repository.get(id)!!.secret!!)
        assertEquals("PIN 9930", repository.reveal(repository.get(id)!!).items!!.single().text)

        repository.setLocked(id, false)
        val unlocked = repository.get(id)!!
        assertFalse(unlocked.locked)
        assertNull(unlocked.secret)
        assertEquals("PIN 9930", unlocked.items!!.single().text)
    }

    @Test
    fun `locked notes are found by title only, and never discarded as blank`() = runTest {
        val id = repository.create(checklist = false)
        repository.save(repository.get(id)!!.copy(body = "door code 1234"))
        repository.setLocked(id, true)
        val stored = repository.get(id)!!
        assertFalse(stored.matches("1234"))
        repository.discardIfBlank(id)
        assertTrue(repository.get(id) != null)
        repository.save(repository.reveal(stored).copy(title = "Codes"))
        assertTrue(repository.get(id)!!.matches("codes"))
    }

    @Test
    fun `a backup carries locked notes, locked again on restore, or unlocked on a phone that can't lock them`() = runTest {
        val id = repository.create(checklist = false)
        repository.save(repository.get(id)!!.copy(title = "Diary", body = "Dear diary"))
        repository.setLocked(id, true)
        val backup = repository.exportBackup()
        assertTrue("Dear diary" in backup)

        assertTrue(repository.importBackup(backup))
        val restored = repository.get(id)!!
        assertTrue(restored.locked)
        assertEquals("Dear diary", repository.reveal(restored).body)

        cipher.isUnlocked = false
        assertTrue(repository.importBackup(backup))
        assertEquals("Dear diary", repository.get(id)!!.body)
        assertFalse(repository.get(id)!!.locked)
    }

    @Test
    fun `a locked note's pictures are encrypted with it, and come back out when the lock is taken off`() = runTest {
        val photo = "a photo".toByteArray()
        val name = images.write("sea", photo, sealed = false)
        val id = repository.createShared("Holiday", "", listOf(name))
        repository.setLocked(id, true)

        val sealed = repository.get(id)!!.images.single()
        assertTrue(NoteImages.isSealed(sealed))
        assertFalse(File(dir, name).exists())
        assertFalse(File(dir, sealed).readBytes().contentEquals(photo))
        assertTrue(images.read(sealed)!!.contentEquals(photo))
        cipher.isUnlocked = false
        assertNull(images.read(sealed))
        cipher.isUnlocked = true

        repository.setLocked(id, false)
        val plain = repository.get(id)!!.images.single()
        assertFalse(NoteImages.isSealed(plain))
        assertTrue(File(dir, plain).readBytes().contentEquals(photo))
    }

    @Test
    fun `a backup carries every picture, locked notes' too`() = runTest {
        val open = repository.createShared("", "", listOf(images.write("sea", "sea".toByteArray(), sealed = false)))
        val locked = repository.createShared("Passport", "", listOf(images.write("passport", "passport".toByteArray(), sealed = false)))
        repository.setLocked(locked, true)
        val backup = repository.exportBackup()
        dir.listFiles()!!.forEach { it.delete() }

        assertTrue(repository.importBackup(backup))
        assertTrue(images.read(repository.get(open)!!.images.single())!!.contentEquals("sea".toByteArray()))
        val restored = repository.get(locked)!!
        assertTrue(restored.locked && NoteImages.isSealed(restored.images.single()))
        assertTrue(images.read(restored.images.single())!!.contentEquals("passport".toByteArray()))
    }

    @Test
    fun `pictures no note has any more are deleted, once they're an hour old`() = runTest {
        val kept = images.write("kept", byteArrayOf(1), sealed = false)
        repository.createShared("", "", listOf(kept))
        val dropped = images.write("dropped", byteArrayOf(2), sealed = false)
        val fresh = images.write("fresh", byteArrayOf(3), sealed = false)
        listOf(kept, dropped).forEach { File(dir, it).setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000) }

        repository.tidyImages()
        assertEquals(setOf(kept, fresh), dir.list()!!.toSet())
    }

    /** Scrambles reversibly, so plain text never shows in what's stored. */
    private class FakeCipher : NoteCipher {
        override var isUnlocked = true
        override fun encrypt(plain: ByteArray): ByteArray = plain.reversedArray().map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray): ByteArray = sealed.map { (it.toInt() xor 0x5A).toByte() }.toByteArray().reversedArray()
    }
}
