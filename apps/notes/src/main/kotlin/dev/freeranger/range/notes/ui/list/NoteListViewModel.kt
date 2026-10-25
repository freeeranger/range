package dev.freeranger.range.notes.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.freeranger.range.notes.data.Note
import dev.freeranger.range.notes.data.NoteRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NoteListState(
    /** Pinned first, then the most recently edited. */
    val notes: List<Note> = emptyList(),
    /** False until the database has answered, so the empty state doesn't flash on launch. */
    val loaded: Boolean = false,
)

class NoteListViewModel(private val repository: NoteRepository) : ViewModel() {
    // A note just started has nothing in it yet; it shows once something is written.
    val state: StateFlow<NoteListState> = repository.notes
        .map { notes -> NoteListState(notes.filterNot { it.isBlank }, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NoteListState())

    private val deletions = Channel<Note>(Channel.CONFLATED)

    /** Each deleted note, for the Undo snackbar. */
    val deleted: Flow<Note> = deletions.receiveAsFlow()

    /** Starts a note, or a checklist, and hands its id to [open]. */
    fun create(checklist: Boolean, open: (Long) -> Unit) {
        viewModelScope.launch { open(repository.create(checklist)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            // A note deleted before anything was written in it is just gone: nothing to undo.
            repository.delete(id)?.takeUnless { it.isBlank }?.let { deletions.send(it) }
        }
    }

    fun undoDelete(note: Note) {
        viewModelScope.launch { repository.restore(note) }
    }
}
