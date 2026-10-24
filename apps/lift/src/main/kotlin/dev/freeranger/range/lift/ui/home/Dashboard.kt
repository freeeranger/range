package dev.freeranger.range.lift.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Muscle
import dev.freeranger.range.lift.data.WeekCount
import dev.freeranger.range.lift.data.averagePerWeek
import dev.freeranger.range.lift.data.weekSetsByMuscle
import dev.freeranger.range.lift.data.weekTotals
import dev.freeranger.range.lift.data.weeklyWorkouts
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.Stat
import dev.freeranger.range.lift.ui.common.StatsCard
import dev.freeranger.range.ui.components.RangeColumnChart
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Instant

/** Where the app opens: this week's totals and sets per muscle, and how often workouts have happened week by week. */
@Composable
fun Dashboard(state: HomeState, now: Instant, format: LiftFormat, contentPadding: PaddingValues) {
    val today = now.atZone(format.zone).toLocalDate()
    val week = remember(state.logs, today, format) { weekTotals(state.logs, today, format.firstDayOfWeek, format.zone) }
    val weeks = remember(state.logs, today, format) { weeklyWorkouts(state.logs, today, format.firstDayOfWeek, format.zone) }
    val muscles = remember(state.logs, today, format) { weekSetsByMuscle(state.logs, today, format.firstDayOfWeek, format.zone) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "week") {
            RangeSectionHeader(stringResource(R.string.home_this_week))
            StatsCard {
                Stat(stringResource(R.string.stat_workouts), week.workouts.toString())
                Stat(stringResource(R.string.stat_sets), week.sets.toString())
                Stat(stringResource(R.string.stat_volume), format.volume(week.volume))
            }
        }
        if (state.history.isNotEmpty()) {
            item(key = "muscles") { MuscleSetsCard(muscles, format) }
            item(key = "frequency") { FrequencyCard(weeks, format) }
        }
    }
}

/** This week's sets for each muscle, every one listed so the ones left out show too. */
@Composable
private fun MuscleSetsCard(sets: Map<Muscle, Int>, format: LiftFormat) {
    val most = sets.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Card(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter).padding(top = RangeSpace.s2)) {
        Column(Modifier.padding(RangeSpace.s4), verticalArrangement = Arrangement.spacedBy(RangeSpace.s2)) {
            Text(stringResource(R.string.home_muscle_sets), Modifier.padding(bottom = RangeSpace.s1), style = MaterialTheme.typography.titleMedium)
            sets.forEach { (muscle, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RangeSpace.s3)) {
                    Text(format.muscle(muscle), Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(
                        progress = { count.toFloat() / most },
                        modifier = Modifier.weight(1f).height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Text(
                        count.toString(),
                        Modifier.width(24.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

/** A bar per week for the number of workouts in it, with the usual number a week. */
@Composable
private fun FrequencyCard(weeks: List<WeekCount>, format: LiftFormat) {
    val average = remember(weeks) { averagePerWeek(weeks) }
    val counts = weeks.map { it.workouts }
    Card(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter).padding(top = RangeSpace.s4)) {
        Column(Modifier.padding(RangeSpace.s4), verticalArrangement = Arrangement.spacedBy(RangeSpace.s3)) {
            Column {
                Text(stringResource(R.string.home_frequency), style = MaterialTheme.typography.titleMedium)
                average?.let {
                    Text(
                        stringResource(R.string.home_frequency_average, format.average(it)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val workouts = weeks.map { pluralStringResource(R.plurals.exercise_workouts, it.workouts, it.workouts) }
            RangeColumnChart(
                values = counts,
                xLabel = { i -> weeks.getOrNull(i)?.let { format.shortDate(it.start) }.orEmpty() },
                markerLabel = { i -> weeks.getOrNull(i)?.let { format.labelled(workouts[i], format.shortDate(it.start)) }.orEmpty() },
                description = stringResource(R.string.home_frequency_description, counts.joinToString(", ")),
                modifier = Modifier.height(160.dp),
            )
        }
    }
}
