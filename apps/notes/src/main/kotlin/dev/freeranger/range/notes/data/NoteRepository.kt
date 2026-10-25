package dev.freeranger.range.notes.data

import kotlinx.coroutines.flow.Flow
import java.time.Clock

/** The only way the app changes notes. */
class NoteRepository(
    private val dao: NoteDao,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) {
    val notes: Flow<List<Note>> = dao.observeAll()

    fun note(id: Long): Flow<Note?> = dao.observe(id)

    suspend fun get(id: Long): Note? = dao.get(id)

    /** Starts an empty note, a checklist with one empty item if [checklist]. Left blank, [discardIfBlank] removes it. */
    suspend fun create(checklist: Boolean): Long {
        val now = clock().instant()
        return dao.insert(Note(items = if (checklist) listOf(ChecklistItem(1, "")) else null, createdAt = now, editedAt = now))
    }

    /** Saves the note's title, text and items. Nothing changes, not even the edit time, if they're the same. */
    suspend fun save(note: Note) {
        val stored = dao.get(note.id) ?: return
        val edited = stored.copy(title = note.title, body = note.body, items = note.items)
        if (edited != stored) dao.update(edited.copy(editedAt = clock().instant()))
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        val note = dao.get(id) ?: return
        if (note.pinned != pinned) dao.update(note.copy(pinned = pinned))
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

    /** Everything, as a backup file's text. */
    suspend fun exportBackup(): String = Backup(dao.all()).encode()

    /** Replaces everything with a backup file's contents. False, and nothing changed, if it isn't a Notes backup. */
    suspend fun importBackup(text: String): Boolean {
        val backup = Backup.decode(text) ?: return false
        return runCatching { dao.replaceAll(backup.notes) }.isSuccess
    }
}
