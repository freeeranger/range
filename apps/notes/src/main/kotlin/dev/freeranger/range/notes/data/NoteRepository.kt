package dev.freeranger.range.notes.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock

/**
 * The only way the app changes notes. Locked notes' text and items only ever reach the database
 * encrypted through [cipher]; [reveal] decrypts them for showing while notes are unlocked.
 */
class NoteRepository(
    private val dao: NoteDao,
    private val cipher: NoteCipher,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    /** Locked notes come with their title only. */
    val notes: Flow<List<Note>> = dao.observeAll()

    fun note(id: Long): Flow<Note?> = dao.observe(id)

    /** The note as stored: a locked one without its text and items. */
    suspend fun get(id: Long): Note? = dao.get(id)

    /** A locked note with its text and items decrypted, for while notes are unlocked; other notes as they are. */
    fun reveal(note: Note): Note {
        val secret = note.secret ?: return note
        val content = json.decodeFromString(Content.serializer(), cipher.decrypt(secret))
        return note.copy(body = content.body, items = content.items)
    }

    suspend fun anyLocked(): Boolean = dao.anyLocked()

    /** Starts an empty note, a checklist with one empty item if [checklist]. Left blank, [discardIfBlank] removes it. */
    suspend fun create(checklist: Boolean): Long {
        val now = clock().instant()
        return dao.insert(Note(items = if (checklist) listOf(ChecklistItem(1, "")) else null, createdAt = now, editedAt = now))
    }

    /** Saves the note's title, text and items, encrypted if it's locked. Nothing changes, not even the edit time, if they're the same. */
    suspend fun save(note: Note) {
        val stored = dao.get(note.id) ?: return
        if (stored.locked) {
            val before = reveal(stored)
            if (before.title == note.title && before.body == note.body && before.items == note.items) return
            dao.update(stored.copy(title = note.title, secret = seal(note), editedAt = clock().instant()))
            return
        }
        val edited = stored.copy(title = note.title, body = note.body, items = note.items)
        if (edited != stored) dao.update(edited.copy(editedAt = clock().instant()))
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        val note = dao.get(id) ?: return
        if (note.pinned != pinned) dao.update(note.copy(pinned = pinned))
    }

    /** Locks the note, encrypting its text and items, or takes the lock off. Notes must be unlocked. */
    suspend fun setLocked(id: Long, locked: Boolean) {
        val note = dao.get(id) ?: return
        if (note.locked == locked) return
        dao.update(if (locked) note.locked() else reveal(note).copy(locked = false, secret = null))
    }

    /** Removes a note that was left with nothing written in it. */
    suspend fun discardIfBlank(id: Long) {
        if (dao.get(id)?.isBlank == true) dao.delete(id)
    }

    /** Deletes the note and returns it, so Undo can put it back. */
    suspend fun delete(id: Long): Note? {
        val note = dao.get(id) ?: return null
        dao.delete(id)
        return note
    }

    /** Undoes [delete]: the note comes back as it was, in its old place. */
    suspend fun restore(note: Note) {
        if (dao.get(note.id) == null) dao.insert(note)
    }

    /** Everything, as a backup file's text, locked notes decrypted and marked locked. Notes must be unlocked if any are locked. */
    suspend fun exportBackup(): String = Backup(dao.all().map(::reveal)).encode()

    /**
     * Replaces everything with a backup file's contents. Locked notes are locked again, if notes are
     * unlocked, or else (on a phone with no screen lock to lock them with) come back unlocked.
     * False, and nothing changed, if it isn't a Notes backup.
     */
    suspend fun importBackup(text: String): Boolean {
        val backup = Backup.decode(text) ?: return false
        val notes = backup.notes.map { if (it.locked && cipher.isUnlocked) it.copy(locked = false).locked() else it.copy(locked = false) }
        return runCatching { dao.replaceAll(notes) }.isSuccess
    }

    private fun Note.locked(): Note = copy(locked = true, secret = seal(this), body = "", items = null)

    private fun seal(note: Note): String = cipher.encrypt(json.encodeToString(Content.serializer(), Content(note.body, note.items)))

    /** What a locked note keeps encrypted. */
    @Serializable
    private data class Content(val body: String = "", val items: List<ChecklistItem>? = null)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
