package dev.freeranger.range.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.freeranger.range.ui.R
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.settings.ThemeMode

private fun spaceGrotesk(weight: Int) = Font(
    resId = R.font.space_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Space Grotesk ships as one variable font (300–700), so every weight comes from the same file. */
val SpaceGrotesk = FontFamily(
    spaceGrotesk(300),
    spaceGrotesk(400),
    spaceGrotesk(500),
    spaceGrotesk(600),
    spaceGrotesk(700),
)

/** Material's type scale, set in Space Grotesk. */
private val RangeTypography = Typography().run {
    fun TextStyle.range() = copy(fontFamily = SpaceGrotesk)
    copy(
        displayLarge = displayLarge.range(),
        displayMedium = displayMedium.range(),
        displaySmall = displaySmall.range(),
        headlineLarge = headlineLarge.range(),
        headlineMedium = headlineMedium.range(),
        headlineSmall = headlineSmall.range(),
        titleLarge = titleLarge.range(),
        titleMedium = titleMedium.range(),
        titleSmall = titleSmall.range(),
        bodyLarge = bodyLarge.range(),
        bodyMedium = bodyMedium.range(),
        bodySmall = bodySmall.range(),
        labelLarge = labelLarge.range(),
        labelMedium = labelMedium.range(),
        labelSmall = labelSmall.range(),
    )
}

/** Whether these settings put the app in dark mode right now. */
@Composable
fun RangeAppearance.isDark(): Boolean = when (theme) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * Material 3 in Space Grotesk. Colours come from the wallpaper (Material You) by default,
 * otherwise from the app's [accent]; light or dark follows [appearance].
 * Screens use Material components and `MaterialTheme` directly.
 */
@Composable
fun RangeTheme(accent: RangeAccent, appearance: RangeAppearance = RangeAppearance(), content: @Composable () -> Unit) {
    val dark = appearance.isDark()
    val context = LocalContext.current
    val colors = remember(accent, appearance, dark, context) {
        val base = when {
            appearance.dynamicColor -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> accent.dark
            else -> accent.light
        }
        if (dark && appearance.pureBlack) base.pureBlack() else base
    }
    MaterialTheme(colorScheme = colors, typography = RangeTypography, content = content)
}
