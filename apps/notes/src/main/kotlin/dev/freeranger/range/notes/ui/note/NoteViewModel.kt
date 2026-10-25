package dev.freeranger.range.notes.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.notes.data.ChecklistItem
import dev.freeranger.range.notes.data.Note
import dev.freeranger.range.notes.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * The note being edited. Edits land in [note] straight away, for the text fields, and are saved
 * shortly after typing stops; leaving the note saves what's left, and discards it if it's blank.
 */
class NoteViewModel(
    private val id: Long,
    private val repository: NoteRepository,
    /** Outlives this screen, for saving as it's left. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    var note by mutableStateOf<Note?>(null)
        private set

    private var pendingSave: Job? = null

    init {
        viewModelScope.launch { note = repository.get(id) }
    }

    fun setTitle(title: String) = edit { copy(title = title) }

    fun setBody(body: String) = edit { copy(body = body) }

    fun setItemText(itemId: Long, text: String) = editItems { items -> items.map { if (it.id == itemId) it.copy(text = text) else it } }

    fun setChecked(itemId: Long, checked: Boolean) = editItems { items -> items.map { if (it.id == itemId) it.copy(checked = checked) else it } }

    fun removeItem(itemId: Long) = editItems { items -> items.filterNot { it.id == itemId } }

    /** Adds an item after [afterId], or at the end of the unticked ones, and returns its id to focus. */
    fun addItem(afterId: Long? = null, text: String = ""): Long? {
        val items = note?.items ?: return null
        val item = ChecklistItem(nextItemId(items), text)
        val at = afterId?.let { id -> items.indexOfFirst { it.id == id } + 1 }?.takeIf { it > 0 }
            ?: (items.indexOfLast { !it.checked } + 1)
        editItems { it.subList(0, at) + item + it.subList(at, it.size) }
        return item.id
    }

    fun togglePinned() {
        val current = note ?: return
        note = current.copy(pinned = !current.pinned)
        appScope.launch { repository.setPinned(id, !current.pinned) }
    }

    /** Turns text into a checklist, a line per item, or a checklist back into text. */
    fun toggleChecklist() {
        val current = note ?: return
        var next = 0L
        note = if (current.isChecklist) current.toText() else current.toChecklist { ++next }
        saveNow()
    }

    /** Saves first, then runs [then], so the note is deleted as it was last seen. */
    fun delete(then: () -> Unit) {
        pendingSave?.cancel()
        val latest = note
        appScope.launch {
            latest?.let { repository.save(it) }
            withContext(Dispatchers.Main) { then() }
        }
    }

    override fun onCleared() {
        pendingSave?.cancel()
        val latest = note
        appScope.launch {
            latest?.let { repository.save(it) }
            repository.discardIfBlank(id)
        }
    }

    private fun editItems(transform: (List<ChecklistItem>) -> List<ChecklistItem>) = edit { copy(items = items?.let(transform)) }

    private fun edit(transform: Note.() -> Note) {
        val current = note ?: return
        val edited = current.transform()
        if (edited == current) return
        // Shown as "Edited just now" straight away; the stored time is set when it's saved.
        note = edited.copy(editedAt = Instant.now())
        pendingSave?.cancel()
        pendingSave = appScope.launch {
            delay(SAVE_DELAY_MS)
            note?.let { repository.save(it) }
        }
    }

    private fun saveNow() {
        pendingSave?.cancel()
        val latest = note ?: return
        appScope.launch { repository.save(latest) }
    }

    private fun nextItemId(items: List<ChecklistItem>) = (items.maxOfOrNull { it.id } ?: 0) + 1

    private companion object {
        const val SAVE_DELAY_MS = 400L
    }
}
