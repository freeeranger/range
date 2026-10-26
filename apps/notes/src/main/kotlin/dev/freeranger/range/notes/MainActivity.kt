package dev.freeranger.range.notes

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.freeranger.range.notes.ui.common.LocalImageLoader
import dev.freeranger.range.ui.theme.RangeAccents
import dev.freeranger.range.ui.theme.RangeTheme
import dev.freeranger.range.ui.theme.isDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** A note just made from a shortcut or something shared, to show. */
    private val opening = MutableStateFlow<NoteKey?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as NotesApplication).container
        if (savedInstanceState == null) handle(intent)
        val loadImage: suspend (String, Int) -> ImageBitmap? = { name, width ->
            withContext(Dispatchers.IO) { container.images.load(name, width)?.asImageBitmap() }
        }
        // A few small preferences: read them before the first frame so a chosen theme never flashes the wrong one.
        val initialAppearance = runBlocking { container.appearance.appearance.first() }

        setContent {
            val appearance by container.appearance.appearance.collectAsStateWithLifecycle(initialAppearance)
            val dark = appearance.isDark()
            DisposableEffect(dark) {
                // Transparent bars with icons that contrast with the app's theme, not the system's.
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            RangeTheme(accent = RangeAccents.Notes, appearance = appearance) {
                CompositionLocalProvider(LocalAppContainer provides container, LocalImageLoader provides loadImage) {
                    val open by opening.collectAsStateWithLifecycle()
                    NotesApp(opening = open, onOpened = { opening.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** A launcher shortcut starts a note or checklist; text, links and pictures shared from other apps become one. */
    private fun handle(intent: Intent) {
        val container = (application as NotesApplication).container
        when (intent.action) {
            ACTION_NEW_NOTE, ACTION_NEW_CHECKLIST -> container.scope.launch {
                opening.value = NoteKey(container.repository.create(checklist = intent.action == ACTION_NEW_CHECKLIST), isNew = true)
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val title = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val pictures = listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)) +
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                val resolver = contentResolver
                container.scope.launch(Dispatchers.IO) {
                    val names = pictures.distinct().mapNotNull { container.images.add(resolver, it) }
                    if (title.isBlank() && text.isBlank() && names.isEmpty()) return@launch
                    opening.value = NoteKey(container.repository.createShared(title, text, names))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as NotesApplication).container.lock.appReturned()
    }

    // Leaving the app (or turning the screen off) locks locked notes again; turning the phone doesn't.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) (application as NotesApplication).container.lock.appLeft()
    }

    private companion object {
        // The launcher shortcuts' actions, in res/xml/shortcuts.xml.
        const val ACTION_NEW_NOTE = "dev.freeranger.range.notes.NEW_NOTE"
        const val ACTION_NEW_CHECKLIST = "dev.freeranger.range.notes.NEW_CHECKLIST"
    }
}
