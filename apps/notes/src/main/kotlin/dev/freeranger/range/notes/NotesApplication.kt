package dev.freeranger.range.notes

import android.app.Application
import android.content.Context
import dev.freeranger.range.notes.data.NoteImages
import dev.freeranger.range.notes.data.NoteLock
import dev.freeranger.range.notes.data.NoteRepository
import dev.freeranger.range.notes.data.NotesDatabase
import dev.freeranger.range.ui.settings.RangeAppearanceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Everything that lives as long as the app. Built by hand: the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    /** For work that must outlive a screen, like saving a note as it's left. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val appearance = RangeAppearanceStore(context)
    val lock = NoteLock(context, scope)
    val images = NoteImages(File(context.filesDir, "images"), lock)
    val repository = NoteRepository(NotesDatabase.create(context).notes(), lock, images)
}

class NotesApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            container.repository.tidyImages()
            // Decrypted pictures leave memory along with the note key.
            container.lock.unlocked.collect { if (!it) container.images.forgetSealed() }
        }
    }
}
