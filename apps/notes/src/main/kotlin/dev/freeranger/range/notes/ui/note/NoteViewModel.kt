package dev.freeranger.range.notes.ui.note

import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.notes.data.ChecklistItem
import dev.freeranger.range.notes.data.Note
import dev.freeranger.range.notes.data.NoteImages
import dev.freeranger.range.notes.data.NoteLock
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
 * A locked note only has its text and items here while notes are unlocked, and is saved before they lock.
 */
class NoteViewModel(
    private val id: Long,
    private val repository: NoteRepository,
    private val lock: NoteLock,
    private val images: NoteImages,
    /** Outlives this screen, for saving as it's left. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    var note by mutableStateOf<Note?>(null)
        private set

    /** Whether a locked note's text and items are here. */
    private var revealed by mutableStateOf(false)

    /** A locked note while notes are locked: only its title is known, and nothing can be changed. */
    val hidden: Boolean get() = note?.let { it.locked && !revealed } ?: false

    private var pendingSave: Job? = null
    private val stopSavingBeforeLock = lock.beforeLocking { flush() }

    init {
        viewModelScope.launch {
            lock.unlocked.collect { unlocked ->
                val stored = repository.get(id) ?: return@collect
                when {
                    stored.locked && unlocked -> {
                        note = repository.reveal(stored)
                        revealed = true
                    }
                    stored.locked -> {
                        note = stored
                        revealed = false
                    }
                    note == null -> note = stored
                }
            }
        }
    }

    /** Locks the note or takes its lock off. Notes must be unlocked to lock one. */
    fun setLocked(locked: Boolean) {
        val current = note ?: return
        pendingSave?.cancel()
        note = current.copy(locked = locked)
        revealed = locked
        appScope.launch {
            repository.save(current)
            repository.setLocked(id, locked)
        }
    }

    fun setTitle(title: String) = edit { copy(title = title) }

    fun setBody(body: String) = edit { copy(body = body) }

    fun setItemText(itemId: Long, text: String) = editItems { items -> items.map { if (it.id == itemId) it.copy(text = text) else it } }

    fun setChecked(itemId: Long, checked: Boolean) = editItems { items -> items.map { if (it.id == itemId) it.copy(checked = checked) else it } }

    fun removeItem(itemId: Long) = editItems { items -> items.filterNot { it.id == itemId } }

    fun untickAll() = editItems { items -> items.map { it.copy(checked = false) } }

    /** Removes ticked items and returns the list as it was, for Undo. */
    fun removeTicked(): List<ChecklistItem>? {
        val before = note?.items ?: return null
        editItems { items -> items.filterNot { it.checked } }
        return before
    }

    fun restoreItems(items: List<ChecklistItem>) = editItems { items }

    /** Puts the unticked items in [order], by id; ticked ones follow as they were. */
    fun reorder(order: List<Long>) = editItems { items ->
        val byId = items.associateBy { it.id }
        order.mapNotNull(byId::get) + items.filter { it.id !in order }
    }

    /** Copies pictures in and adds them after the note's others; encrypted ones for a locked note. */
    fun addImages(resolver: ContentResolver, uris: List<Uri>) {
        val sealed = note?.locked == true
        appScope.launch {
            val names = withContext(Dispatchers.IO) {
                uris.mapNotNull { images.add(resolver, it) }.map { if (sealed) images.reseal(it, sealed = true) else it }
            }
            withContext(Dispatchers.Main) { edit { copy(images = this.images + names) } }
        }
    }

    /** Takes a picture off the note and returns where it was, for Undo; the file goes once nothing has it. */
    fun removeImage(name: String): Int {
        val at = note?.images?.indexOf(name) ?: -1
        edit { copy(images = images - name) }
        return at
    }

    fun restoreImage(name: String, at: Int) = edit { copy(images = images.toMutableList().apply { add(at.coerceIn(0, size), name) }) }

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
        appScope.launch {
            flush()
            withContext(Dispatchers.Main) { then() }
        }
    }

    override fun onCleared() {
        stopSavingBeforeLock()
        pendingSave?.cancel()
        val latest = note?.takeUnless { hidden }
        appScope.launch {
            latest?.let { repository.save(it) }
            repository.discardIfBlank(id)
        }
    }

    /** Saves what's waiting to be saved, now. */
    private suspend fun flush() {
        pendingSave?.cancel()
        note?.takeUnless { hidden }?.let { repository.save(it) }
    }

    private fun editItems(transform: (List<ChecklistItem>) -> List<ChecklistItem>) = edit { copy(items = items?.let(transform)) }

    private fun edit(transform: Note.() -> Note) {
        val current = note?.takeUnless { hidden } ?: return
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
        val latest = note?.takeUnless { hidden } ?: return
        appScope.launch { repository.save(latest) }
    }

    private fun nextItemId(items: List<ChecklistItem>) = (items.maxOfOrNull { it.id } ?: 0) + 1

    private companion object {
        const val SAVE_DELAY_MS = 400L
    }
}
