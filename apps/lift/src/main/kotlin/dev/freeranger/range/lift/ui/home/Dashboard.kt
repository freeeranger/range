package dev.freeranger.range.lift.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.WeekCount
import dev.freeranger.range.lift.data.averagePerWeek
import dev.freeranger.range.lift.data.weekTotals
import dev.freeranger.range.lift.data.weeklyWorkouts
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.Stat
import dev.freeranger.range.lift.ui.common.StatsCard
import dev.freeranger.range.ui.components.RangeColumnChart
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Instant

/** Where the app opens: this week's totals, and how often workouts have happened week by week. */
@Composable
fun Dashboard(state: HomeState, now: Instant, format: LiftFormat, contentPadding: PaddingValues) {
    val today = now.atZone(format.zone).toLocalDate()
    val week = remember(state.logs, today, format) { weekTotals(state.logs, today, format.firstDayOfWeek, format.zone) }
    val weeks = remember(state.logs, today, format) { weeklyWorkouts(state.logs, today, format.firstDayOfWeek, format.zone) }

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
            item(key = "frequency") { FrequencyCard(weeks, format) }
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
