package dev.freeranger.range.lift

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.freeranger.range.ui.theme.RangeAccents
import dev.freeranger.range.ui.theme.RangeTheme
import dev.freeranger.range.ui.theme.isDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as LiftApplication).container
        // A few small preferences: read them before the first frame so a chosen theme never flashes the wrong one.
        val initialAppearance = runBlocking { container.appearance.appearance.first() }

        setContent {
            val appearance by container.appearance.appearance.collectAsStateWithLifecycle(initialAppearance)
            val dark = appearance.isDark()
            DisposableEffect(dark) {
                // Transparent bars with icons that contrast with the app's theme, not the system's.
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            RangeTheme(accent = RangeAccents.Lift, appearance = appearance) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    LiftApp()
                }
            }
        }
    }
}
