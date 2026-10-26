package dev.freeranger.range.calc.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.freeranger.range.calc.math.AngleMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.calc by preferencesDataStore(name = "calc")

/** The sheet's lines and the angle setting, kept between visits. */
class CalcStore(context: Context) {
    private val store = context.applicationContext.calc

    suspend fun lines(): List<String> = store.data.first()[LINES]?.split('\n') ?: listOf("")

    suspend fun setLines(lines: List<String>) {
        store.edit { it[LINES] = lines.joinToString("\n") }
    }

    val angles: Flow<AngleMode> = store.data.map { prefs -> prefs[ANGLES]?.let(AngleMode::valueOf) ?: AngleMode.Degrees }

    suspend fun setAngles(mode: AngleMode) {
        store.edit { it[ANGLES] = mode.name }
    }

    private companion object {
        val LINES = stringPreferencesKey("lines")
        val ANGLES = stringPreferencesKey("angles")
    }
}
