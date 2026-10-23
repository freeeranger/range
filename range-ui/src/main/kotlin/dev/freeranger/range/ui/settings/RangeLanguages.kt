package dev.freeranger.range.ui.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList

/**
 * The per-app language, stored by Android itself (Android 13+), so it also shows under
 * the system's Settings › Apps › Language and survives reinstalls from backup.
 */
object RangeLanguages {
    /** Languages every Range app is translated into, as language tags with their own names. */
    val supported: List<Pair<String, String>> = listOf(
        "en" to "English",
        "sv" to "Svenska",
    )

    /** Choosing an app language needs Android 13; older phones follow the system language. */
    val canChoose: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** The chosen language tag, or null when the app follows the system language. */
    fun current(context: Context): String? {
        if (!canChoose) return null
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].language
    }

    /** Pass null to follow the system language again. Android restarts the visible screens to apply it. */
    fun set(context: Context, tag: String?) {
        if (!canChoose) return
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
}
