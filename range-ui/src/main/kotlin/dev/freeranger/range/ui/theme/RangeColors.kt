package dev.freeranger.range.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * An app's own colours, used when Material You is off (or the phone doesn't support it):
 * one Tailwind colour scale on neutral greys, in a light and a dark variant.
 */
@Immutable
data class RangeAccent(val light: ColorScheme, val dark: ColorScheme)

object RangeAccents {
    /** Tailwind indigo. */
    val Tasks = RangeAccent(
        light = rangeLight(
            primary = Color(0xFF4F46E5),
            container = Color(0xFFE0E7FF),
            onContainer = Color(0xFF312E81),
        ),
        dark = rangeDark(
            primary = Color(0xFFA5B4FC),
            onPrimary = Color(0xFF1E1B4B),
            container = Color(0xFF3730A3),
            onContainer = Color(0xFFE0E7FF),
            selection = Color(0xFF312E81),
        ),
    )

    /** Tailwind orange. 700 rather than 600 in light mode, so white text on it stays readable. */
    val Lift = RangeAccent(
        light = rangeLight(
            primary = Color(0xFFC2410C),
            container = Color(0xFFFFEDD5),
            onContainer = Color(0xFF7C2D12),
        ),
        dark = rangeDark(
            primary = Color(0xFFFDBA74),
            onPrimary = Color(0xFF431407),
            container = Color(0xFF9A3412),
            onContainer = Color(0xFFFFEDD5),
            selection = Color(0xFF7C2D12),
        ),
    )
}

private fun rangeLight(primary: Color, container: Color, onContainer: Color) = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = container,
    onPrimaryContainer = onContainer,
    inversePrimary = container,
    secondary = primary,
    onSecondary = Color.White,
    secondaryContainer = container,
    onSecondaryContainer = onContainer,
    tertiary = primary,
    onTertiary = Color.White,
    tertiaryContainer = container,
    onTertiaryContainer = onContainer,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF525252),
    surfaceTint = primary,
    inverseSurface = Color(0xFF262626),
    inverseOnSurface = Color(0xFFF5F5F5),
    surfaceBright = Color(0xFFFAFAFA),
    surfaceDim = Color(0xFFDADADA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFEFEFEF),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE2E2E2),
    outline = Color(0xFF737373),
    outlineVariant = Color(0xFFD4D4D4),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

private fun rangeDark(primary: Color, onPrimary: Color, container: Color, onContainer: Color, selection: Color) = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = container,
    onPrimaryContainer = onContainer,
    inversePrimary = container,
    secondary = primary,
    onSecondary = onPrimary,
    secondaryContainer = selection,
    onSecondaryContainer = onContainer,
    tertiary = primary,
    onTertiary = onPrimary,
    tertiaryContainer = container,
    onTertiaryContainer = onContainer,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE5E5E5),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE5E5E5),
    surfaceVariant = Color(0xFF333333),
    onSurfaceVariant = Color(0xFFA3A3A3),
    surfaceTint = primary,
    inverseSurface = Color(0xFFE5E5E5),
    inverseOnSurface = Color(0xFF171717),
    surfaceBright = Color(0xFF383838),
    surfaceDim = Color(0xFF121212),
    surfaceContainerLowest = Color(0xFF0D0D0D),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF333333),
    outline = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFF3A3A3A),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

/** Dark mode on a true black page, for OLED screens. Raised surfaces keep their colour so they still stand out. */
internal fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
)
