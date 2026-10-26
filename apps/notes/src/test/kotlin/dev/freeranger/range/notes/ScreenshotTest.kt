package dev.freeranger.range.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.freeranger.range.notes.data.ChecklistItem
import dev.freeranger.range.notes.data.Note
import dev.freeranger.range.notes.ui.list.NoteListScreen
import dev.freeranger.range.notes.ui.list.NoteListState
import dev.freeranger.range.notes.ui.common.LocalImageLoader
import dev.freeranger.range.notes.ui.note.ImageScreen
import dev.freeranger.range.notes.ui.note.LockedNoteScreen
import dev.freeranger.range.notes.ui.note.NoteActions
import dev.freeranger.range.notes.ui.note.NoteScreen
import dev.freeranger.range.ui.settings.RangeAboutScreen
import dev.freeranger.range.ui.settings.RangeAppIcon
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeBackup
import dev.freeranger.range.ui.settings.RangeLicense
import dev.freeranger.range.ui.settings.RangeLicenseScreen
import dev.freeranger.range.ui.settings.RangeSettingsScreen
import dev.freeranger.range.ui.settings.ThemeMode
import dev.freeranger.range.ui.theme.RangeAccents
import dev.freeranger.range.ui.theme.RangeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen on the JVM, menus included.
 * `./gradlew :apps:notes:recordRoborazziDebug` updates apps/notes/screenshots.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val friday = LocalDate.of(2026, 10, 23)

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant()

    private fun items(vararg texts: String, ticked: Set<String> = emptySet()) =
        texts.mapIndexed { i, text -> ChecklistItem(i + 1L, text, checked = text in ticked) }

    private fun note(
        id: Long,
        title: String = "",
        body: String = "",
        items: List<ChecklistItem>? = null,
        pinned: Boolean = false,
        edited: java.time.Instant = at(friday, 9),
        images: List<String> = emptyList(),
    ) = Note(id, title, body, items, pinned, createdAt = edited, editedAt = edited, images = images)

    private val trip = note(
        1, "Gotland trip",
        items = items("Passport", "Charger", "Swimsuit", "Sunscreen", "A good book", ticked = setOf("Passport", "Charger")),
        pinned = true,
    )
    private val ideas = note(
        2, "App ideas",
        body = "# Range\n- **Notes** with checklists\n- A _habit_ tracker\n- Something for the budget\n\nKeep each one small.",
        edited = at(friday, 8, 42),
    )
    private val groceries = note(3, "Groceries", items = items("Oat milk", "Eggs", "Rye bread", "Coffee", ticked = setOf("Coffee")), edited = at(friday.minusDays(1), 18))
    private val plumber = note(4, body = "Call the plumber about the kitchen tap before Friday.", edited = at(friday.minusDays(3), 12))
    private val wifi = note(5, "Wifi", body = "Network: **Home**\nPassword: correct-horse-battery", pinned = true, edited = at(friday.minusDays(40), 10))
    private val books = note(
        6, "Books to read",
        body = "- The Left Hand of Darkness\n- Piranesi\n- A Psalm for the Wild-Built\n- The Dispossessed",
        edited = at(friday.minusDays(400), 10),
    )

    private val bank = note(7, "Bank", pinned = true).copy(locked = true, secret = "sealed")
    private val diary = note(8, "Diary", body = "Slept well. Long walk by the sea.", edited = at(friday.minusDays(2), 21)).copy(locked = true)

    private val beach = note(9, "Gotland", body = "Ferry at 7:30 from Nynäshamn. Booking at **ferry.example.com**, or see https://www.gotland.example", images = listOf("sea.jpg"), edited = at(friday, 7, 50))
    private val plants = note(10, images = listOf("plants.jpg"), edited = at(friday.minusDays(5), 15))

    private val state = NoteListState(listOf(trip, wifi, bank, beach, ideas, groceries, plumber, plants, books), loaded = true)

    @Composable
    private fun List(listState: NoteListState = state, query: String = "") =
        NoteListScreen(listState, onOpen = {}, onCreate = {}, onOpenSettings = {}, initialQuery = query)

    @Test
    fun list() = capture { List() }

    @Test
    fun listLight() = capture(RangeAppearance(theme = ThemeMode.Light, dynamicColor = false)) { List() }

    @Test
    fun listPureBlack() = capture(RangeAppearance(dynamicColor = false, pureBlack = true)) { List() }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun listSwedish() = capture { List() }

    @Test
    fun listEmpty() = capture { List(NoteListState(loaded = true)) }

    @Test
    fun listSearching() = capture { List(query = "milk") }

    @Test
    fun listNoMatches() = capture { List(query = "zebra") }

    @Test
    fun listDeleted() = capture {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { snackbar.showSnackbar("Note deleted", actionLabel = "Undo", duration = SnackbarDuration.Indefinite) }
        NoteListScreen(state, onOpen = {}, onCreate = {}, onOpenSettings = {}, snackbar = snackbar)
    }

    @Test
    fun note() = capture { NoteScreen(ideas, friday, NoteActions()) }

    @Test
    fun noteLight() = capture(RangeAppearance(theme = ThemeMode.Light, dynamicColor = false)) { NoteScreen(ideas, friday, NoteActions()) }

    @Test
    fun noteEditing() = capture { NoteScreen(ideas, friday, NoteActions(), initiallyEditing = true) }

    @Test
    fun noteNew() = capture { NoteScreen(note(9, edited = at(friday, 10)), friday, NoteActions(), initiallyEditing = true) }

    @Test
    fun noteMenu() = capture { NoteScreen(ideas, friday, NoteActions(), initiallyShowingMenu = true) }

    @Test
    fun checklist() = capture { NoteScreen(trip, friday, NoteActions()) }

    @Test
    fun checklistEditing() = capture { NoteScreen(trip, friday, NoteActions(), initiallyEditing = true) }

    @Test
    fun checklistMenu() = capture { NoteScreen(trip, friday, NoteActions(), initiallyShowingMenu = true) }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun checklistSwedish() = capture { NoteScreen(groceries, friday, NoteActions()) }

    @Test
    fun noteLocked() = capture { LockedNoteScreen(bank, onBack = {}, onUnlock = {}) }

    @Test
    @Config(qualifiers = "sv-rSE-$PHONE")
    fun noteLockedSwedish() = capture { LockedNoteScreen(bank, onBack = {}, onUnlock = {}) }

    @Test
    fun noteUnlockedMenu() = capture { NoteScreen(diary, friday, NoteActions(), initiallyShowingMenu = true) }

    @Test
    fun notePicture() = capture { NoteScreen(beach, friday, NoteActions()) }

    @Test
    fun notePictureEditing() = capture { NoteScreen(beach, friday, NoteActions(), initiallyEditing = true) }

    @Test
    fun picture() = capture { ImageScreen("sea.jpg", onBack = {}) }

    @Test
    fun checklistRemovedTicked() = capture {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { snackbar.showSnackbar("2 ticked items removed", actionLabel = "Undo", duration = SnackbarDuration.Indefinite) }
        NoteScreen(trip.copy(items = trip.items!!.filterNot { it.checked }), friday, NoteActions(), snackbar = snackbar)
    }

    @Test
    fun icon() = capture {
        Row(Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            RangeAppIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, size = 160.dp)
            RangeAppIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, size = 64.dp)
        }
    }

    @Test
    fun settings() = capture {
        RangeSettingsScreen(
            RangeAppearance(),
            onAppearanceChange = {},
            onBack = {},
            onOpenAbout = {},
            backup = RangeBackup("Notes", "notes", CoroutineScope(Dispatchers.Unconfined), { "" }, { true }, { true }),
        )
    }

    @Test
    fun about() = capture {
        RangeAboutScreen(
            appName = "Range Notes",
            iconBackground = R.drawable.ic_launcher_background,
            iconForeground = R.drawable.ic_launcher_foreground,
            onBack = {},
            onOpenLicense = {},
            onOpenComponents = {},
        )
    }

    @Test
    fun license() = capture { RangeLicenseScreen(RangeLicense.Gpl3, onBack = {}) }

    /** Material You is off so screenshots don't depend on a wallpaper; pictures are painted here. */
    private fun capture(appearance: RangeAppearance = RangeAppearance(dynamicColor = false), content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalImageLoader provides { name, width -> scene(name, width) }) {
                RangeTheme(accent = RangeAccents.Notes, appearance = appearance, content = content)
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage()
    }
}

private const val PHONE = "w412dp-h915dp-night-xxhdpi"

/** A simple painted picture standing in for a photo: a beach, or leaves. */
private fun scene(name: String, width: Int): ImageBitmap {
    val w = width.coerceIn(200, 900)
    val h = w * 2 / 3
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun fill(top: Float, bottom: Float, from: Int, to: Int) {
        paint.shader = LinearGradient(0f, top, 0f, bottom, from, to, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, top, w.toFloat(), bottom, paint)
        paint.shader = null
    }
    if (name.startsWith("sea")) {
        fill(0f, h * 0.55f, 0xFF7DD3FC.toInt(), 0xFFE0F2FE.toInt())
        fill(h * 0.55f, h * 0.78f, 0xFF0EA5E9.toInt(), 0xFF0369A1.toInt())
        fill(h * 0.78f, h.toFloat(), 0xFFFDE68A.toInt(), 0xFFF59E0B.toInt())
        paint.color = 0xFFFEF08A.toInt()
        canvas.drawCircle(w * 0.74f, h * 0.28f, h * 0.12f, paint)
    } else {
        fill(0f, h.toFloat(), 0xFF14532D.toInt(), 0xFF4D7C0F.toInt())
        paint.color = 0xFF86EFAC.toInt()
        listOf(0.2f to 0.3f, 0.55f to 0.6f, 0.8f to 0.25f, 0.35f to 0.75f).forEach { (x, y) -> canvas.drawOval(w * x - h * 0.18f, h * y - h * 0.08f, w * x + h * 0.18f, h * y + h * 0.08f, paint) }
    }
    return bitmap.asImageBitmap()
}
