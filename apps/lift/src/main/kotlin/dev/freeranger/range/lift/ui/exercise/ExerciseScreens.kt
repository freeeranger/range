package dev.freeranger.range.lift.ui.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.Muscle
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.ui.common.Stat
import dev.freeranger.range.lift.ui.common.StatsCard
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.lift.ui.common.rememberNow
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.LocalDate

@Composable
fun ExercisePickerRoute(workoutId: Long, onDone: () -> Unit) {
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
        onCreate = { name, muscle ->
            viewModel.create(name, muscle)
            onDone()
        },
        onBack = onDone,
    )
}

/** The exercise library, opened from a workout: tapping one adds it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerScreen(
    exercises: List<Exercise>,
    bests: Map<Long, WorkoutSet>,
    onPick: (Exercise) -> Unit,
    onCreate: (String, Muscle) -> Unit,
    onBack: () -> Unit,
    initiallyAdding: Boolean = false,
) {
    var adding by rememberSaveable { mutableStateOf(initiallyAdding) }
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
                onClick = { adding = true },
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
    if (adding) NewExerciseSheet(onAdd = onCreate, onDismiss = { adding = false }, autoFocus = !initiallyAdding)
}

@Composable
fun ExerciseDetailRoute(id: Long, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { ExerciseDetailViewModel(id, container.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = rememberNow().atZone(rememberLiftFormat().zone).toLocalDate()
    ExerciseDetailScreen(state, today, onBack)
}

/** One exercise: its best set, estimated one-rep max, and every workout it was done in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(state: ExerciseDetailState, today: LocalDate, onBack: () -> Unit) {
    val format = rememberLiftFormat()
    val exercise = state.exercise
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(exercise?.let(format::name).orEmpty())
                        exercise?.let {
                            Text(format.muscle(it.muscle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + RangeSpace.s4)) {
            item(key = "stats") {
                StatsCard {
                    Stat(stringResource(R.string.exercise_best_set), state.best?.let(format::set) ?: "–")
                    Stat(stringResource(R.string.exercise_one_rep_max), state.best?.estimatedMax?.let { format.kg(Math.round(it).toDouble()) } ?: "–")
                    Stat(stringResource(R.string.exercise_sessions), state.sessions.size.toString())
                }
            }
            item(key = "history") { RangeSectionHeader(stringResource(R.string.exercise_history)) }
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
                            Text(format.set(set), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
