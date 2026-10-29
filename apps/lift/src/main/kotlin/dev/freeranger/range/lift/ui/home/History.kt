package dev.freeranger.range.lift.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Totals
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.totals
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.ui.components.RangeMonthGrid
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.LocalDate

/** How much of the log the Workouts tab shows at once. */
enum class HistoryRange { Day, Week, Month, All }

private fun WorkoutLog.date(format: LiftFormat): LocalDate = workout.startedAt.atZone(format.zone).toLocalDate()

/** The finished workouts started from [from] up to, not including, [until], oldest first. */
private fun HomeState.between(from: LocalDate, until: LocalDate, format: LiftFormat): List<WorkoutLog> =
    history.filter { it.date(format).let { date -> date >= from && date < until } }.sortedBy { it.workout.startedAt }

/** One day's page of the log: the workouts done that day, or a rest day. */
@Composable
internal fun DayLog(state: HomeState, day: LocalDate, format: LiftFormat, actions: HomeActions, contentPadding: PaddingValues) {
    val workouts = remember(state.history, day, format) { state.between(day, day.plusDays(1), format) }
    if (workouts.isEmpty()) {
        Empty(state, R.string.workouts_rest_day, contentPadding)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(workouts, key = { it.workout.id }) { log ->
            WorkoutCard(log, format, onClick = { actions.openWorkout(log.workout.id) })
        }
    }
}

/** A week's page: what it added up to, then its workouts in full under the day they were done. */
@Composable
internal fun WeekLog(state: HomeState, start: LocalDate, format: LiftFormat, actions: HomeActions, contentPadding: PaddingValues) {
    val until = start.plusWeeks(1)
    val workouts = remember(state.history, start, format) { state.between(start, until, format) }
    if (workouts.isEmpty()) {
        Empty(state, R.string.history_none, contentPadding)
        return
    }
    val totals = remember(state.history, start, format) { totals(state.history, start, until, format.zone) }
    val days = workouts.groupBy { it.date(format) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "totals") { TotalsLine(totals, format) }
        days.forEach { (day, logs) ->
            item(key = day.toString()) { RangeSectionHeader(format.header(day)) }
            items(logs, key = { it.workout.id }) { log ->
                WorkoutCard(log, format, onClick = { actions.openWorkout(log.workout.id) })
            }
        }
    }
}

/** A month's page: a calendar with the days trained filled in, what it added up to, and a line per workout. */
@Composable
internal fun MonthLog(
    state: HomeState,
    start: LocalDate,
    today: LocalDate,
    trained: Set<LocalDate>,
    format: LiftFormat,
    actions: HomeActions,
    onOpenDay: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
) {
    val until = start.plusMonths(1)
    val workouts = remember(state.history, start, format) { state.between(start, until, format) }
    val totals = remember(state.history, start, format) { totals(state.history, start, until, format.zone) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "calendar") {
            RangeMonthGrid(
                month = start,
                today = today,
                firstDayOfWeek = format.firstDayOfWeek,
                onSelect = onOpenDay,
                label = format::weekdayShort,
                describe = format::header,
                marked = { it in trained },
                markedDescription = stringResource(R.string.history_trained),
                last = today,
            )
        }
        if (workouts.isEmpty()) {
            if (state.loaded) {
                item(key = "none") {
                    Text(
                        stringResource(if (state.history.isEmpty()) R.string.workouts_empty else R.string.history_none),
                        Modifier.fillMaxWidth().padding(RangeSpace.s8),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            item(key = "totals") { TotalsLine(totals, format, Modifier.padding(top = RangeSpace.s4)) }
            items(workouts, key = { it.workout.id }) { log ->
                WorkoutRow(log, format, onClick = { actions.openWorkout(log.workout.id) })
            }
        }
    }
}

/** Every workout, newest first, under the month it was done in. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AllLog(state: HomeState, today: LocalDate, format: LiftFormat, actions: HomeActions, contentPadding: PaddingValues) {
    if (state.history.isEmpty()) {
        Empty(state, R.string.workouts_empty, contentPadding)
        return
    }
    val months = remember(state.history, format) {
        state.history.sortedByDescending { it.workout.startedAt }.groupBy { it.date(format).withDayOfMonth(1) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        months.forEach { (month, logs) ->
            stickyHeader(key = month.toString()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = RangeSpace.gutter)
                        .padding(top = RangeSpace.s4, bottom = RangeSpace.s2)
                        .semantics(mergeDescendants = true) { heading() },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        format.month(month, today),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        pluralStringResource(R.plurals.exercise_workouts, logs.size, logs.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(logs, key = { it.workout.id }) { log ->
                WorkoutRow(log, format, onClick = { actions.openWorkout(log.workout.id) })
            }
        }
    }
}

@Composable
private fun Empty(state: HomeState, text: Int, contentPadding: PaddingValues) {
    if (!state.loaded) return
    Box(Modifier.fillMaxSize().padding(contentPadding).padding(RangeSpace.s8), contentAlignment = Alignment.Center) {
        Text(
            stringResource(if (state.history.isEmpty()) R.string.workouts_empty else text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "3 workouts · 46 sets · 12,400 kg" */
@Composable
private fun TotalsLine(totals: Totals, format: LiftFormat, modifier: Modifier = Modifier) {
    Text(
        stringResource(
            R.string.history_totals,
            pluralStringResource(R.plurals.exercise_workouts, totals.workouts, totals.workouts),
            format.sets(totals.sets),
            format.volume(totals.volume),
        ),
        modifier.padding(horizontal = RangeSpace.gutter).padding(bottom = RangeSpace.s1),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A workout in full: when, how long and how much, then each exercise's sets and best set. */
@Composable
private fun WorkoutCard(log: WorkoutLog, format: LiftFormat, onClick: () -> Unit) {
    val workout = log.workout
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s1),
    ) {
        Column(Modifier.padding(RangeSpace.s4)) {
            Text(format.name(workout), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    R.string.workout_summary,
                    format.time(workout.startedAt),
                    format.duration(workout.startedAt, workout.finishedAt ?: workout.startedAt),
                    format.volume(log.volume),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (log.exercises.isNotEmpty()) Spacer(Modifier.height(RangeSpace.s3))
            log.exercises.forEach { logged ->
                Row(horizontalArrangement = Arrangement.spacedBy(RangeSpace.s3)) {
                    Text(
                        "${logged.sets.count { it.done }} × ${format.name(logged.exercise)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    logged.best?.let {
                        Text(format.set(it, logged.exercise), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A workout on one line, for scanning many: the date, its name and exercises, and how long it took. */
@Composable
private fun WorkoutRow(log: WorkoutLog, format: LiftFormat, onClick: () -> Unit) {
    val workout = log.workout
    val date = log.date(format)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(format.weekdayShort(date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
            }
        },
        headlineContent = { Text(format.name(workout), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (log.exercises.isEmpty()) null else {
            { Text(log.exercises.joinToString(", ") { format.name(it.exercise) }, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Text(format.duration(workout.startedAt, workout.finishedAt ?: workout.startedAt), style = MaterialTheme.typography.labelMedium)
        },
    )
}
