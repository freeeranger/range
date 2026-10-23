package dev.freeranger.range.lift.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.oneRepMaxHistory
import dev.freeranger.range.lift.data.progressExercises
import dev.freeranger.range.lift.data.setsPerMuscle
import dev.freeranger.range.lift.data.weeklyTotals
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.Stat
import dev.freeranger.range.lift.ui.common.StatsCard
import dev.freeranger.range.ui.components.RangeBarList
import dev.freeranger.range.ui.components.RangeColumnChart
import dev.freeranger.range.ui.components.RangeLineChart
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * How training is going: this week's totals, volume week by week, how the estimated one-rep max
 * of the main lifts moves, and which muscle groups get the work.
 */
@Composable
fun Dashboard(logs: List<WorkoutLog>, now: Instant, format: LiftFormat, contentPadding: PaddingValues) {
    val today = now.atZone(format.zone).toLocalDate()
    if (logs.none { it.workout.isFinished }) {
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(RangeSpace.s8), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.home_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val weeks = remember(logs, today, format) { weeklyTotals(logs, today, format.firstDayOfWeek, format.zone) }
    val lifts = remember(logs) { progressExercises(logs) }
    val muscles = remember(logs, today) { setsPerMuscle(logs, now.minus(Duration.ofDays(30))) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "week") {
            RangeSectionHeader(stringResource(R.string.home_this_week))
            val week = weeks.last()
            StatsCard {
                Stat(stringResource(R.string.stat_workouts), week.workouts.toString())
                Stat(stringResource(R.string.stat_sets), week.sets.toString())
                Stat(stringResource(R.string.stat_volume), format.volume(week.volume))
            }
        }
        item(key = "volume") {
            var selected by rememberSaveable(weeks.size) { mutableIntStateOf(weeks.lastIndex) }
            val week = weeks[selected]
            ChartCard(
                title = stringResource(R.string.home_volume_title),
                value = format.volume(week.volume),
                caption = if (selected == weeks.lastIndex) {
                    stringResource(R.string.home_this_week)
                } else {
                    stringResource(R.string.home_week_of, format.shortDate(week.start))
                },
            ) {
                RangeColumnChart(
                    values = weeks.map { it.volume },
                    selected = selected,
                    onSelect = { selected = it },
                    // Every fourth week, ending on this one.
                    xLabel = { i -> format.shortDate(weeks[i].start).takeIf { (weeks.lastIndex - i) % 4 == 0 } },
                    valueLabel = format::compact,
                    description = stringResource(R.string.home_volume_description, weeks.size, format.volume(weeks.last().volume)),
                )
            }
        }
        if (lifts.isNotEmpty()) {
            item(key = "strength") { StrengthCard(logs, lifts, today, format) }
        }
        item(key = "muscles") {
            ChartCard(title = stringResource(R.string.home_muscles_title), caption = stringResource(R.string.home_muscles_period)) {
                RangeBarList(
                    items = muscles.map { (muscle, sets) -> format.muscle(muscle) to sets.toDouble() },
                    valueLabel = { it.toInt().toString() },
                )
            }
        }
    }
}

/** The estimated one-rep max of one of the most-done lifts, picked with chips, over every workout. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrengthCard(logs: List<WorkoutLog>, lifts: List<Exercise>, today: LocalDate, format: LiftFormat) {
    var liftId by rememberSaveable { mutableLongStateOf(lifts.first().id) }
    val lift = lifts.firstOrNull { it.id == liftId } ?: lifts.first()
    val history = remember(logs, lift.id) { oneRepMaxHistory(logs, lift.id) }
    var selected by rememberSaveable(lift.id, history.size) { mutableIntStateOf(history.lastIndex) }
    val point = history.getOrNull(selected) ?: history.last()
    ChartCard(
        title = stringResource(R.string.home_strength_title),
        value = format.kg(Math.round(point.kg).toDouble()),
        caption = format.day(point.at, today),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2)) {
            lifts.forEach {
                FilterChip(selected = it.id == lift.id, onClick = { liftId = it.id }, label = { Text(format.name(it)) })
            }
        }
        RangeLineChart(
            x = history.map { it.at.atZone(format.zone).toLocalDate().toEpochDay().toDouble() },
            y = history.map { it.kg },
            selected = selected,
            onSelect = { selected = it },
            xLabel = { format.shortDate(LocalDate.ofEpochDay(it.toLong())) },
            valueLabel = { format.number(it) },
            description = stringResource(
                R.string.home_strength_description,
                format.name(lift),
                history.size,
                format.kg(Math.round(history.last().kg).toDouble()),
            ),
            // The card's own colour, so each dot stands off the line.
            ringColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

/** A chart on a card: what it shows, the picked value large, and what that value is of. */
@Composable
private fun ChartCard(title: String, caption: String, value: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
    ) {
        Column(Modifier.padding(RangeSpace.s4), verticalArrangement = Arrangement.spacedBy(RangeSpace.s3)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (value != null) Text(value, style = MaterialTheme.typography.headlineMedium)
                Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}
