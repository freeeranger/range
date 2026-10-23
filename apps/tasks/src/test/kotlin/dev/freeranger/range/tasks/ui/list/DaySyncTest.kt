package dev.freeranger.range.tasks.ui.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import dev.freeranger.range.ui.components.RangeDayPager
import dev.freeranger.range.ui.components.RangeWeekStrip
import dev.freeranger.range.ui.theme.RangeAccents
import dev.freeranger.range.ui.theme.RangeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

/** The week strip, the day pager and the selection must agree however fast they're tapped or swiped. */
@RunWith(RobolectricTestRunner::class)
class DaySyncTest {
    @get:Rule
    val compose = createComposeRule()

    private val friday = LocalDate.of(2026, 10, 23)
    private var selected by mutableStateOf(friday)

    private fun setContent() = compose.setContent {
        RangeTheme(accent = RangeAccents.Tasks) {
            Column(Modifier.fillMaxSize()) {
                RangeWeekStrip(
                    selected = selected,
                    today = friday,
                    firstDayOfWeek = DayOfWeek.MONDAY,
                    onSelect = { selected = it },
                    label = { it.dayOfWeek.name.take(3) },
                    describe = { "strip $it" },
                    marked = { false },
                    modifier = Modifier.testTag("strip"),
                )
                RangeDayPager(selected, onSelect = { selected = it }, modifier = Modifier.fillMaxWidth().height(400.dp).testTag("pager")) {
                    Text("page $it")
                }
            }
        }
    }

    /** The pager rests exactly on the selected day, and the strip shows the selected day's week. */
    private fun assertInSync() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val page = compose.onNodeWithText("page $selected").fetchSemanticsNode().boundsInRoot
        val pager = compose.onNodeWithTag("pager").fetchSemanticsNode().boundsInRoot
        assertEquals("page for $selected lines up with the pager", 0f, abs(page.left - pager.left), 1f)
        compose.onNodeWithContentDescription("strip $selected").assertExists()
    }

    @Test
    fun `taps faster than the slide still end on the last day tapped`() {
        setContent()
        compose.mainClock.autoAdvance = false
        // All in the week on screen, Monday the 19th to Sunday the 25th. Going two days ahead
        // passes through the day in between: tapping it then must still finish the slide there.
        val runs = listOf(listOf(2L, 1L), listOf(-4L, -3L), listOf(1L, -2L, -4L, 0L, -1L, 2L))
        for (run in runs) for (gap in listOf(16L, 50L, 120L, 200L)) {
            compose.mainClock.autoAdvance = false
            for (offset in run) {
                compose.onNodeWithContentDescription("strip ${friday.plusDays(offset)}").performClick()
                compose.mainClock.advanceTimeBy(gap)
            }
            assertEquals(friday.plusDays(run.last()), selected)
            assertInSync()
            compose.onNodeWithContentDescription("strip $friday").performClick()
            assertInSync()
        }
    }

    @Test
    fun `swipes faster than the pager settles each move one day`() {
        setContent()
        // Gaps finer than a frame, so some second swipe starts just as the first one settles.
        for (gap in 0L..800L step 4L) {
            val start = selected
            compose.mainClock.autoAdvance = false
            repeat(3) {
                compose.onNodeWithTag("pager").performTouchInput { swipeLeft(durationMillis = 120) }
                compose.mainClock.advanceTimeBy(gap)
            }
            assertInSync()
            assertEquals("after 3 swipes $gap ms apart", start.plusDays(3), selected)
        }
    }

    @Test
    fun `swiping the strip and tapping a day mid-swipe keeps the pager on the selection`() {
        setContent()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("strip").performTouchInput { swipeLeft(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("pager").performTouchInput { swipeRight(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(50)
        assertInSync()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("strip").performTouchInput { swipeLeft(durationMillis = 120) }
        compose.mainClock.advanceTimeBy(30)
        compose.onNodeWithContentDescription("strip ${selected.plusDays(1)}").performClick()
        assertInSync()
    }
}
