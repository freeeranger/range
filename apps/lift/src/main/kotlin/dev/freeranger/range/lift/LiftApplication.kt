package dev.freeranger.range.lift

import android.app.Application
import android.content.Context
import dev.freeranger.range.lift.data.LiftDatabase
import dev.freeranger.range.lift.data.LiftPreferences
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.rest.AndroidRestStopwatch
import dev.freeranger.range.ui.settings.RangeAppearanceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Everything that lives as long as the app. Built by hand: the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    /** For work that must outlive a screen, like saving on exit. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val appearance = RangeAppearanceStore(context)
    val preferences = LiftPreferences(context)
    val repository = LiftRepository(LiftDatabase.create(context).lift(), AndroidRestStopwatch(context))
}

class LiftApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AndroidRestStopwatch.createChannel(this)
        container.scope.launch { container.repository.addBuiltInExercises() }
    }
}
