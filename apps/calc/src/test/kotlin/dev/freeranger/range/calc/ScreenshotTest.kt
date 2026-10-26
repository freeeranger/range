package dev.freeranger.range.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.freeranger.range.calc.math.AngleMode
import dev.freeranger.range.calc.math.Rates
import dev.freeranger.range.calc.ui.CalcActions
import dev.freeranger.range.calc.ui.CalcScreen
import dev.freeranger.range.calc.ui.CalcSettingsScreen
import dev.freeranger.range.calc.ui.InputMode
import dev.freeranger.range.ui.settings.RangeAboutScreen
import dev.freeranger.range.ui.settings.RangeAppIcon
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.RangeLicense
import dev.freeranger.range.ui.settings.RangeLicenseScreen
import dev.freeranger.range.ui.settings.ThemeMode
import dev.freeranger.range.ui.theme.RangeAccents
import dev.freeranger.range.ui.theme.RangeTheme
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen on the JVM, keypad and keyboard modes included.
 * `./gradlew :apps:calc:recordRoborazziDebug` updates apps/calc/screenshots.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val friday = LocalDate.of(2026, 10, 23)
    private val rates = Rates(friday, mapOf("SEK" to BigDecimal("11.40"), "USD" to BigDecimal("1.12"), "NOK" to BigDecimal("11.70"), "GBP" to BigDecimal("0.87")))

    private val sums = listOf("1 250 × 12", "15 000 × 0,7", "89 × 4 + 25 %", "10 500 ÷ 3 + 250")
    private val trip = listOf(
        "# Gotland trip",
        "// three of us, four days",
        "Ferry 2 × 495 kr",
        "Cabin 3 nights × 1 150 kr",
        "Food 4 × 400 kr",
        "Total = sum",
        "Per person = Total / 3",
        "Total in EUR",
        "",
        "5 km in miles",
        "186 km ÷ 80 km/h",
        "today + 3 weeks",
        "20 % off 1 250 kr",
    )

    @Composable
    private fun Calc(
        lines: List<String>,
        editing: Int = lines.lastIndex,
        mode: InputMode = InputMode.Keypad,
        keypad: Boolean = true,
        expanded: Boolean = false,
        snackbar: SnackbarHostState = remember { SnackbarHostState() },
    ) = CalcScreen(
        lines = lines,
        editing = editing,
        field = TextFieldValue(lines[editing], TextRange(lines[editing].length)),
        angles = AngleMode.Degrees,
        rates = rates,
        today = friday,
        actions = CalcActions(),
        initialMode = mode,
        initiallyShowingKeypad = keypad,
        initiallyExpanded = expanded,
        snackbar = snackbar,
    )

    @Test
    fun keypad() = capture { Calc(sums) }

    @Test
    fun keypadEmpty() = capture { Calc(listOf("")) }

    @Test
    fun keypadCarryingOn() = capture { Calc(sums + "ans × 12") }

    @Test
    fun keypadLight() = capture(RangeAppearance(theme = ThemeMode.Light, dynamicColor = false)) { Calc(sums) }

    @Test
    fun keypadPureBlack() = capture(RangeAppearance(dynamicColor = false, pureBlack = true)) { Calc(sums) }

    @Test
    @Config(qualifiers = "sv-rSE-$SIZE")
    fun keypadSwedish() = capture { Calc(sums + "today + 3 weeks") }

    @Test
    fun scientific() = capture(RangeAppearance(theme = ThemeMode.Light, dynamicColor = false)) { Calc(listOf("sin(30)", "sin(30) + ln(e²)"), expanded = true) }

    @Test
    fun equations() = capture { Calc(listOf("2x + 3 = 11", "x × 2", "x² − 5x + 6 = 0", "3^x = 81", "x² = −1"), expanded = true) }

    @Test
    @Config(qualifiers = "sv-rSE-$SIZE")
    fun equationsSwedish() = capture { Calc(listOf("x² − 5x + 6 = 0", "2x = x + x"), keypad = false) }

    @Test
    fun keyboard() = capture { Calc(trip.take(6), mode = InputMode.Keyboard) }

    @Test
    fun sheet() = capture { Calc(trip, keypad = false) }

    @Test
    fun sheetEditingEarlierLine() = capture { Calc(trip, editing = 4, mode = InputMode.Keyboard) }

    @Test
    fun cleared() = capture {
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { snackbar.showSnackbar("Sheet cleared", actionLabel = "Undo", duration = SnackbarDuration.Indefinite) }
        Calc(listOf(""), snackbar = snackbar)
    }

    @Test
    fun settings() = capture { CalcSettingsScreen(RangeAppearance(), onAppearanceChange = {}, rates = rates, onRefreshRates = {}, onBack = {}, onOpenAbout = {}) }

    @Test
    fun icon() = capture {
        Row(Modifier.padding(32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            RangeAppIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, size = 160.dp)
            RangeAppIcon(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground, size = 64.dp)
        }
    }

    @Test
    fun about() = capture {
        RangeAboutScreen(
            appName = "Range Calculator",
            iconBackground = R.drawable.ic_launcher_background,
            iconForeground = R.drawable.ic_launcher_foreground,
            onBack = {},
            onOpenLicense = {},
            onOpenComponents = {},
        )
    }

    @Test
    fun license() = capture { RangeLicenseScreen(RangeLicense.Gpl3, onBack = {}) }

    /** Material You is off so screenshots don't depend on a wallpaper. */
    private fun capture(appearance: RangeAppearance = RangeAppearance(dynamicColor = false), content: @Composable () -> Unit) {
        compose.setContent { RangeTheme(accent = RangeAccents.Calc, appearance = appearance, content = content) }
        compose.waitForIdle()
        captureScreenRoboImage()
    }
}

private const val SIZE = "w412dp-h915dp-night-xxhdpi"

/** English with a Swedish region, as on the phone it's built for: "10 500", "0,7". */
private const val PHONE = "en-rSE-$SIZE"
