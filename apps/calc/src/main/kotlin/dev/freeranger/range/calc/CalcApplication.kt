package dev.freeranger.range.calc

import android.app.Application
import android.content.Context
import dev.freeranger.range.calc.data.CalcStore
import dev.freeranger.range.calc.data.RatesStore
import dev.freeranger.range.ui.settings.RangeAppearanceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Everything that lives as long as the app. Built by hand: the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    /** For work that must outlive a screen, like saving the sheet as it's left. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val appearance = RangeAppearanceStore(context)
    val store = CalcStore(context)
    val rates = RatesStore(context)
}

class CalcApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            container.rates.load()
            container.rates.refresh()
        }
    }
}
