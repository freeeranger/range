package dev.freeranger.range.notes.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/** Loads a note's picture at a width in pixels. The app reads them from `NoteImages` off the main thread; screenshot tests draw their own. */
val LocalImageLoader = staticCompositionLocalOf<suspend (name: String, width: Int) -> ImageBitmap?> { { _, _ -> null } }

/** The picture once it's loaded, or null until then (or if it's gone). */
@Composable
fun rememberNoteImage(name: String, width: Int): ImageBitmap? {
    val load = LocalImageLoader.current
    val image by produceState<ImageBitmap?>(null, name, width) { value = load(name, width) }
    return image
}
