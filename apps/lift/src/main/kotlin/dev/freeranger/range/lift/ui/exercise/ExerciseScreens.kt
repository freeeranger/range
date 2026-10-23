package dev.freeranger.range.lift.ui.exercise

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
import androidx.compose.material3.FloatingActionButton
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
import dev.freeranger.range.lift.data.ProgressMetric
import dev.freeranger.range.lift.data.Progression
import dev.freeranger.range.lift.data.Record
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.hint
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.lift.ui.common.rememberNow
import dev.freeranger.range.lift.ui.common.title
import dev.freeranger.range.ui.components.RangeLineChart
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.LocalDate

@Composable
fun ExercisePickerRoute(workoutId: Long, onDone: () -> Unit, onCreate: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { ExercisePickerViewModel(workoutId, container.repository, container.scope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExercisePickerScreen(
        exercises = state.exercises,
        bests = state.bests,
        onPick = {
            viewModel.pick(it.id)
            onDone()
        },
        onCreate = onCreate,
        onBack = onDone,
    )
}

/** The exercise library, opened from a workout: tapping one adds it, and a new one is added straight in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    exercises: List<Exercise>,
    bests: Map<Long, WorkoutSet>,
    onPick: (Exercise) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercise_pick_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(RangeIcons.Add, contentDescription = stringResource(R.string.exercise_new))
            }
        },
    ) { padding ->
        ExerciseList(
            exercises = exercises,
            bests = bests,
            onClick = onPick,
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
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
            if (exercise?.archived == true) {
                item(key = "removed") {
                    Text(
                        stringResource(R.string.exercise_removed_note),
                        modifier = Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.records.isNotEmpty()) {
                item(key = "records") { RecordsCard(state.records, today, format) }
            }
            state.progression?.takeIf { it.points.isNotEmpty() }?.let { progression ->
                item(key = "progress") { ProgressCard(progression, today, format) }
            }
            item(key = "history") {
                val count = pluralStringResource(R.plurals.exercise_workouts, state.sessions.size, state.sessions.size)
                RangeSectionHeader(if (state.sessions.isEmpty()) stringResource(R.string.exercise_history) else format.labelled(stringResource(R.string.exercise_history), count))
            }
            if (state.sessions.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.exercise_no_history),
                        modifier = Modifier.padding(horizontal = RangeSpace.gutter),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            text = { Text(stringResource(R.string.exercise_remove_text)) },
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
    val points = progression.points
    val metric = progression.metric
    val value = { v: Double -> format.metric(metric, v) }
    Card(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2)) {
        Column(Modifier.padding(RangeSpace.s4), verticalArrangement = Arrangement.spacedBy(RangeSpace.s3)) {
            Column {
                Text(stringResource(metric.title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(metric.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (points.size < 2) {
                Text(stringResource(R.string.progress_need_more), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
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
