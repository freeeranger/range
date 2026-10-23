package dev.freeranger.range.lift.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.weekTotals
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.Stat
import dev.freeranger.range.lift.ui.common.StatsCard
import dev.freeranger.range.ui.components.RangeSectionHeader
import java.time.Instant

/** How training is going: for now, this week's totals. */
@Composable
fun Dashboard(logs: List<WorkoutLog>, now: Instant, format: LiftFormat, contentPadding: PaddingValues) {
    val today = now.atZone(format.zone).toLocalDate()
    val week = remember(logs, today, format) { weekTotals(logs, today, format.firstDayOfWeek, format.zone) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "week") {
            RangeSectionHeader(stringResource(R.string.home_this_week))
            StatsCard {
                Stat(stringResource(R.string.stat_workouts), week.workouts.toString())
                Stat(stringResource(R.string.stat_sets), week.sets.toString())
                Stat(stringResource(R.string.stat_volume), format.volume(week.volume))
            }
        }
    }
}
