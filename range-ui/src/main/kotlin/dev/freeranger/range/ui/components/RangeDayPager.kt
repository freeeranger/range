package dev.freeranger.range.ui.components

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** The days the day pager and week strip reach: further back and ahead than anyone scrolls. */
object RangeDays {
    val first: LocalDate = LocalDate.of(2000, 1, 1)
    val last: LocalDate = LocalDate.of(2099, 12, 31)
}

/**
 * One page per day, swiped sideways like a calendar's day view and kept in step with [selected]:
 * swiping selects the day swiped to, and a day selected elsewhere is brought into view.
 */
@Composable
fun RangeDayPager(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (LocalDate) -> Unit,
) {
    val pages = ChronoUnit.DAYS.between(RangeDays.first, RangeDays.last).toInt() + 1
    val page = ChronoUnit.DAYS.between(RangeDays.first, selected.coerceIn(RangeDays.first, RangeDays.last)).toInt()
    val state = rememberPagerState(initialPage = page) { pages }
    FollowSelection(state, page) { onSelect(RangeDays.first.plusDays(it.toLong())) }
    HorizontalPager(state, modifier, key = { it }, verticalAlignment = Alignment.Top) {
        content(RangeDays.first.plusDays(it.toLong()))
    }
}

/**
 * Keeps [state] on [page] and reports the page a swipe settles on. Jumps of more than one page
 * happen at once rather than scrolling through every page in between. Only a swipe reports:
 * a scroll made here to follow the selection can stop part-way when the selection changes
 * again, and that stop mustn't be taken as a new selection.
 */
@Composable
internal fun FollowSelection(state: PagerState, page: Int, onSettle: (Int) -> Unit) {
    val selectedPage by rememberUpdatedState(page)
    val settle by rememberUpdatedState(onSettle)
    val swipe = remember { SwipeFlag() }
    LaunchedEffect(state) {
        state.interactionSource.interactions.collect { if (it is DragInteraction.Start) swipe.pending = true }
    }
    LaunchedEffect(page) {
        if (state.currentPage == page) return@LaunchedEffect
        swipe.pending = false
        if (abs(state.currentPage - page) > 1) state.scrollToPage(page) else state.animateScrollToPage(page)
    }
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }.collect {
            if (swipe.pending && it != selectedPage) settle(it)
            swipe.pending = false
        }
    }
}

private class SwipeFlag(var pending: Boolean = false)
