package dev.freeranger.range.ui.components

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.freeranger.range.ui.theme.RangeTransitions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/** The days the day pager and week strip reach: further back and ahead than anyone scrolls. */
object RangeDays {
    val first: LocalDate = LocalDate.of(2000, 1, 1)
    val last: LocalDate = LocalDate.of(2099, 12, 31)
}

/**
 * One page per day, swiped sideways like a calendar's day view and kept in step with [selected]:
 * swiping selects the day swiped to, and a day selected elsewhere is brought into view. Pages end
 * at [last], e.g. today for a log of what's been done.
 */
@Composable
fun RangeDayPager(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    last: LocalDate = RangeDays.last,
    content: @Composable (LocalDate) -> Unit,
) {
    val pages = ChronoUnit.DAYS.between(RangeDays.first, last).toInt() + 1
    val page = ChronoUnit.DAYS.between(RangeDays.first, selected.coerceIn(RangeDays.first, last)).toInt()
    val state = rememberPagerState(initialPage = page) { pages }
    FollowSelection(state, page) { onSelect(RangeDays.first.plusDays(it.toLong())) }
    HorizontalPager(state, modifier, key = { it }, verticalAlignment = Alignment.Top) {
        content(RangeDays.first.plusDays(it.toLong()))
    }
}

/**
 * Keeps [state] on [page] and reports the page a swipe settles on. Every change is the same
 * short slide: a far jump starts from the page next to the target rather than racing through
 * every page in between.
 *
 * Taps and swipes can come faster than slides finish, so nothing is decided mid-scroll: whenever
 * the pager comes to rest it either reports where a swipe left it or slides to the selection,
 * whichever happened last. A slide cut short part-way is finished, and a swipe started before
 * the previous one's selection arrived still counts.
 */
@Composable
internal fun FollowSelection(state: PagerState, page: Int, onSettle: (Int) -> Unit) {
    val selectedPage by rememberUpdatedState(page)
    val settle by rememberUpdatedState(onSettle)
    LaunchedEffect(state) {
        // A drag moved the pager since it last rested, so where it rests is the user's choice.
        var swiped = false
        // The page last reported, until the selection it asked for arrives.
        var reported: Int? = null
        var followed = selectedPage
        var slide: Job? = null
        launch {
            state.interactionSource.interactions.collect { if (it is DragInteraction.Start) swiped = true }
        }
        snapshotFlow { selectedPage to state.isScrollInProgress }.collect { (target, scrolling) ->
            if (target != followed) {
                // A day picked elsewhere outranks a swipe in flight; the day a swipe reported doesn't.
                if (target != reported) {
                    swiped = false
                    slide?.cancel()
                }
                followed = target
                reported = null
            }
            if (swiped) {
                if (scrolling) return@collect
                swiped = false
                if (state.currentPage != target) {
                    reported = state.currentPage
                    settle(state.currentPage)
                    return@collect
                }
            }
            val off = state.currentPage != target || abs(state.currentPageOffsetFraction) > 0.001f
            if (off && slide?.isActive != true) slide = launch { state.slideTo(target) }
        }
    }
}

private suspend fun PagerState.slideTo(page: Int) {
    if (abs(currentPage - page) > 1) scrollToPage(if (page > currentPage) page - 1 else page + 1)
    animateScrollToPage(page, animationSpec = RangeTransitions.pageSlide)
}

/** A stretch of days [RangePeriodPager] pages by: whole weeks starting on [Week.firstDayOfWeek], or calendar months. */
sealed interface RangePeriod {
    /** The first day of the period [day] is in. */
    fun start(day: LocalDate): LocalDate

    /** The first day of the period [count] periods after the one starting [start]. */
    fun plus(start: LocalDate, count: Long): LocalDate

    /** Whole periods from the one starting [from] to the one starting [to]. */
    fun between(from: LocalDate, to: LocalDate): Long

    data class Week(val firstDayOfWeek: DayOfWeek) : RangePeriod {
        override fun start(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        override fun plus(start: LocalDate, count: Long): LocalDate = start.plusWeeks(count)
        override fun between(from: LocalDate, to: LocalDate): Long = ChronoUnit.WEEKS.between(from, to)
    }

    data object Month : RangePeriod {
        override fun start(day: LocalDate): LocalDate = day.withDayOfMonth(1)
        override fun plus(start: LocalDate, count: Long): LocalDate = start.plusMonths(count)
        override fun between(from: LocalDate, to: LocalDate): Long = ChronoUnit.MONTHS.between(from, to)
    }
}

/**
 * One page per week or month, swiped sideways like [RangeDayPager]: the page shows the period
 * that has [selected], and swiping hands [onSelect] the first day of the period swiped to.
 * Pages end at the period that has [last]. [content] gets each page's first day.
 */
@Composable
fun RangePeriodPager(
    selected: LocalDate,
    period: RangePeriod,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    last: LocalDate = RangeDays.last,
    content: @Composable (LocalDate) -> Unit,
) {
    val origin = period.start(RangeDays.first)
    fun pageOf(day: LocalDate) = period.between(origin, period.start(day.coerceIn(RangeDays.first, last))).toInt()
    val state = rememberPagerState(initialPage = pageOf(selected)) { pageOf(last) + 1 }
    FollowSelection(state, pageOf(selected)) { onSelect(period.plus(origin, it.toLong())) }
    HorizontalPager(state, modifier, key = { it }, verticalAlignment = Alignment.Top) {
        content(period.plus(origin, it.toLong()))
    }
}
