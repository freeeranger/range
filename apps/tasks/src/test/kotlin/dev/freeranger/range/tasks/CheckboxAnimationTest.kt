package dev.freeranger.range.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.freeranger.range.ui.components.RangeCheckbox
import dev.freeranger.range.ui.settings.RangeAppearance
import dev.freeranger.range.ui.theme.RangeAccents
import dev.freeranger.range.ui.theme.RangeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Frames of the tick animation, with the clock stopped: a two-step task getting its first tick
 * (left) and a one-off task getting ticked (right), at the same moments.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w220dp-h80dp-night-xxhdpi")
class CheckboxAnimationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tickFrames() {
        var ticks by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            RangeTheme(RangeAccents.Tasks, RangeAppearance(dynamicColor = false)) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        RangeCheckbox(checked = false, onCheckedChange = {}, contentDescription = "", steps = 2, stepsDone = ticks)
                        RangeCheckbox(checked = ticks > 0, onCheckedChange = {}, contentDescription = "")
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        ticks = 1
        listOf(0L, 80L, 160L, 320L, 700L, 1200L).zipWithNext().forEachIndexed { index, (from, to) ->
            compose.mainClock.advanceTimeBy(to - from)
            compose.onRoot().captureRoboImage("screenshots/${javaClass.name}.tick-$index-${to}ms.png")
        }
    }
}
