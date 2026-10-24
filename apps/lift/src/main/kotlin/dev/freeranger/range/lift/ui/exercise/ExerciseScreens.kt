package dev.freeranger.range.lift.ui.exercise

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.PickTarget
import dev.freeranger.range.lift.data.ProgressMetric
import dev.freeranger.range.lift.data.ProgressPoint
import dev.freeranger.range.lift.data.Progression
import dev.freeranger.range.lift.data.Record
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.lift.ui.common.rememberNow
import dev.freeranger.range.lift.ui.common.title
import dev.freeranger.range.ui.components.RangeLineChart
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.LocalDate

@Composable
fun ExercisePickerRoute(target: PickTarget, onDone: () -> Unit, onCreate: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { ExercisePickerViewModel(target, container.repository, container.scope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExercisePickerScreen(
        state = state,
        swapping = target.swaps,
        onPick = {
            viewModel.pick(it)
            onDone()
        },
        onCreate = onCreate,
        onBack = onDone,
    )
}

/**
 * The exercise library, opened from a workout or template, recent exercises first: tap the ones
 * to add, in order, then Add. When [swapping] one exercise for another, tapping one picks it. A
 * new exercise goes straight in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    state: PickerState,
    swapping: Boolean,
    onPick: (List<Long>) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    initialSelection: List<Long> = emptyList(),
) {
    var selected by rememberSaveable { mutableStateOf(initialSelection) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (swapping) R.string.exercise_swap_title else R.string.exercise_pick_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = onCreate) { Icon(RangeIcons.Add, contentDescription = stringResource(R.string.exercise_new)) }
                },
            )
        },
        floatingActionButton = {
            if (selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(pluralStringResource(R.plurals.exercise_pick_add, selected.size, selected.size)) },
                    icon = { Icon(RangeIcons.Check, contentDescription = null) },
                    onClick = { onPick(selected) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        ExerciseList(
            exercises = state.exercises,
            bests = state.bests,
            onClick = { exercise ->
                when {
                    swapping -> onPick(listOf(exercise.id))
                    exercise.id in selected -> selected = selected - exercise.id
                    else -> selected = selected + exercise.id
                }
            },
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            recent = state.recent,
            selected = selected.takeUnless { swapping },
        )
    }
}

@Composable
fun ExerciseDetailRoute(id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { ExerciseDetailViewModel(id, container.repository, container.scope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = rememberNow().atZone(rememberLiftFormat().zone).toLocalDate()
    ExerciseDetailScreen(
        state = state,
        today = today,
        onBack = onBack,
        onEdit = onEdit,
        onRemove = {
            viewModel.setRemoved(true)
            onBack()
        },
        onRestore = { viewModel.setRemoved(false) },
    )
}

/** One exercise: its best set, estimated one-rep max, and every workout it was done in. Edited or removed from its menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    state: ExerciseDetailState,
    today: LocalDate,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onRemove: () -> Unit = {},
    onRestore: () -> Unit = {},
    initiallyRemoving: Boolean = false,
) {
    val format = rememberLiftFormat()
    val exercise = state.exercise
    var menu by remember { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(initiallyRemoving) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(exercise?.let(format::name).orEmpty())
                        exercise?.let {
                            Text(format.about(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    if (exercise == null) return@TopAppBar
                    IconButton(onClick = onEdit) { Icon(RangeIcons.Edit, contentDescription = stringResource(R.string.exercise_edit)) }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(RangeIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (exercise.archived) R.string.exercise_restore else R.string.exercise_remove)) },
                                leadingIcon = { Icon(if (exercise.archived) RangeIcons.Add else RangeIcons.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    if (exercise.archived) onRestore() else removing = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + RangeSpace.s4)) {
            if (state.records.isNotEmpty()) {
                item(key = "records") { RecordsCard(state.records, today, format) }
            }
            // A line needs two workouts.
            state.progression?.takeIf { it.points.size >= 2 }?.let { progression ->
                item(key = "progress") { ProgressCard(progression, today, format) }
            }
            if (state.sessions.isNotEmpty()) {
                item(key = "history") {
                    val count = pluralStringResource(R.plurals.exercise_workouts, state.sessions.size, state.sessions.size)
                    RangeSectionHeader(format.labelled(stringResource(R.string.exercise_history), count))
                }
            }
            items(state.sessions, key = { it.workout.id }) { session ->
                Column(Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2)) {
                    Text(format.day(session.workout.startedAt, today), style = MaterialTheme.typography.titleSmall)
                    session.sets.forEachIndexed { index, set ->
                        Row {
                            Text(
                                "${index + 1}",
                                modifier = Modifier.width(32.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(exercise?.let { format.set(set, it) }.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (removing && exercise != null) {
        AlertDialog(
            onDismissRequest = { removing = false },
            icon = { Icon(RangeIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.exercise_remove_title, format.name(exercise))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        removing = false
                        onRemove()
                    },
                ) { Text(stringResource(R.string.exercise_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

/**
 * How the exercise has gone, workout by workout: one number each, chosen so sets of different
 * weights and reps compare (see [ProgressMetric]). Needs two workouts to draw a line.
 */
@Composable
private fun ProgressCard(progression: Progression, today: LocalDate, format: LiftFormat) {
    // Only ranges that show something different, with a line's worth of points, are offered.
    val ranges = remember(progression, today, format) {
        ChartRange.entries.filter { range -> range == ChartRange.All || progression.within(range, today, format).size in 2 until progression.points.size }
    }
    var range by rememberSaveable { mutableStateOf(ChartRange.All) }
    val points = progression.within(range.takeIf { it in ranges } ?: ChartRange.All, today, format)
    val metric = progression.metric
    val value = { v: Double -> format.metric(metric, v) }
    Card(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2)) {
        Column(Modifier.padding(RangeSpace.s4), verticalArrangement = Arrangement.spacedBy(RangeSpace.s3)) {
            Text(stringResource(metric.title), style = MaterialTheme.typography.titleMedium)
            if (ranges.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2)) {
                    ranges.forEach {
                        FilterChip(selected = range == it, onClick = { range = it }, label = { Text(stringResource(it.label)) })
                    }
                }
            }
            RangeLineChart(
                values = points.map { it.value },
                xLabel = { i -> points.getOrNull(i)?.let { format.shortDate(it.at.atZone(format.zone).toLocalDate()) }.orEmpty() },
                valueLabel = { if (metric == ProgressMetric.Time) format.seconds(it.toInt()) else format.number(it) },
                markerLabel = { i -> points.getOrNull(i)?.let { format.labelled(value(it.value), format.day(it.at, today)) }.orEmpty() },
                description = stringResource(R.string.progress_description, stringResource(metric.title), points.size, value(points.last().value)),
            )
        }
    }
}

/** How far back a progress chart looks. */
enum class ChartRange(val months: Long?, @StringRes val label: Int) {
    ThreeMonths(3, R.string.chart_three_months),
    Year(12, R.string.chart_year),
    All(null, R.string.chart_all),
}

/** The points no older than [range] reaches back from [today]. */
private fun Progression.within(range: ChartRange, today: LocalDate, format: LiftFormat): List<ProgressPoint> {
    val from = range.months?.let { today.minusMonths(it) } ?: return points
    return points.filter { it.at.atZone(format.zone).toLocalDate() >= from }
}

/** The exercise's best ever by each measure it keeps, with the set and the day. */
@Composable
private fun RecordsCard(records: List<Record>, today: LocalDate, format: LiftFormat) {
    Card(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2)) {
        Column(Modifier.padding(RangeSpace.s4), verticalArrangement = Arrangement.spacedBy(RangeSpace.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(RangeIcons.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Text(stringResource(R.string.exercise_records), Modifier.padding(start = RangeSpace.s2), style = MaterialTheme.typography.titleMedium)
            }
            records.forEach { record ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(record.metric.title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            format.labelled(format.set(record.set, record.exercise), format.day(record.at, today)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(format.metric(record.metric, record.value), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
