package dev.freeranger.range.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

/** How the app looks. The defaults follow the phone: its dark mode setting and its wallpaper colours. */
data class RangeAppearance(
    val theme: ThemeMode = ThemeMode.System,
    /** Material You: colours taken from the wallpaper instead of the app's own. */
    val dynamicColor: Boolean = true,
    /** A true black page in dark mode, for OLED screens. */
    val pureBlack: Boolean = false,
)

private val Context.rangeSettings by preferencesDataStore(name = "range_settings")

/** Saves the appearance settings on the phone, per app. */
class RangeAppearanceStore(context: Context) {
    private val store = context.applicationContext.rangeSettings

    val appearance: Flow<RangeAppearance> = store.data.map { prefs ->
        RangeAppearance(
            theme = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
            dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
            pureBlack = prefs[PURE_BLACK] ?: false,
        )
    }

    suspend fun set(appearance: RangeAppearance) {
        store.edit { prefs ->
            prefs[THEME] = appearance.theme.name
            prefs[DYNAMIC_COLOR] = appearance.dynamicColor
            prefs[PURE_BLACK] = appearance.pureBlack
        }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
    }
}
