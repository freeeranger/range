package dev.freeranger.range.lift.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.liftSettings by preferencesDataStore(name = "lift_settings")

/** Lift's own settings, saved on the phone. */
class LiftPreferences(context: Context) {
    private val store = context.applicationContext.liftSettings

    /** The screen stays on while a workout is in progress. Off unless the user turns it on. */
    val keepScreenOn: Flow<Boolean> = store.data.map { it[KEEP_SCREEN_ON] ?: false }

    suspend fun setKeepScreenOn(on: Boolean) {
        store.edit { it[KEEP_SCREEN_ON] = on }
    }

    private companion object {
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }
}
