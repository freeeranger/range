package dev.freeranger.range.lift.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.lift.ui.common.rememberNow
import dev.freeranger.range.lift.ui.exercise.ExerciseList
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Instant
import java.time.LocalDate

@Composable
fun HomeRoute(onOpenWorkout: (Long) -> Unit, onOpenExercise: (Long) -> Unit, onNewExercise: () -> Unit, onOpenSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { HomeViewModel(container.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        now = rememberNow(),
        actions = HomeActions(
            start = { viewModel.start(onOpenWorkout) },
            openWorkout = onOpenWorkout,
            openExercise = onOpenExercise,
            newExercise = onNewExercise,
            openSettings = onOpenSettings,
        ),
    )
}

/** Everything the home screen can ask for. */
data class HomeActions(
    val start: () -> Unit = {},
    val openWorkout: (id: Long) -> Unit = {},
    val openExercise: (id: Long) -> Unit = {},
    val newExercise: () -> Unit = {},
    val openSettings: () -> Unit = {},
)

enum class HomeTab { Workouts, Exercises }

/** Past workouts and the exercise library, a tab each, as StrengthLog splits them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    now: Instant,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    initialTab: HomeTab = HomeTab.Workouts,
) {
    val format = rememberLiftFormat()
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = {
                    IconButton(onClick = actions.openSettings) {
                        Icon(RangeIcons.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == HomeTab.Workouts,
                    onClick = { tab = HomeTab.Workouts },
                    icon = { Icon(RangeIcons.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_workouts)) },
                )
                NavigationBarItem(
                    selected = tab == HomeTab.Exercises,
                    onClick = { tab = HomeTab.Exercises },
                    icon = { Icon(RangeIcons.FitnessCenter, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_exercises)) },
                )
            }
        },
        floatingActionButton = {
            // The theme's main accent (wallpaper-based with Material You), not Material's paler default.
            val container = MaterialTheme.colorScheme.primary
            val content = MaterialTheme.colorScheme.onPrimary
            when (tab) {
                HomeTab.Workouts -> ExtendedFloatingActionButton(
                    text = { Text(stringResource(if (state.inProgress != null) R.string.workout_continue else R.string.workout_start)) },
                    icon = { Icon(RangeIcons.FitnessCenter, contentDescription = null) },
                    onClick = actions.start,
                    containerColor = container,
                    contentColor = content,
                )
                HomeTab.Exercises -> FloatingActionButton(onClick = actions.newExercise, containerColor = container, contentColor = content) {
                    Icon(RangeIcons.Add, contentDescription = stringResource(R.string.exercise_new))
                }
            }
        },
    ) { padding ->
        // Leaves room for the button over the end of the list.
        val contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp)
        when (tab) {
            HomeTab.Workouts -> WorkoutHistory(state, now, format, actions, contentPadding)
            HomeTab.Exercises -> ExerciseList(
                exercises = state.exercises,
                bests = state.bests,
                onClick = { actions.openExercise(it.id) },
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun WorkoutHistory(state: HomeState, now: Instant, format: LiftFormat, actions: HomeActions, contentPadding: PaddingValues) {
    val today = now.atZone(format.zone).toLocalDate()
    val months = remember(state.logs, format) {
        state.history.groupBy { it.workout.startedAt.atZone(format.zone).toLocalDate().withDayOfMonth(1) }
    }
    if (state.loaded && months.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(RangeSpace.s8), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.workouts_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        months.forEach { (month, logs) ->
            item(key = "month $month") { RangeSectionHeader(format.month(month)) }
            items(logs, key = { it.workout.id }) { log ->
                WorkoutCard(log, today, format, onClick = { actions.openWorkout(log.workout.id) })
            }
        }
    }
}

@Composable
private fun WorkoutCard(log: WorkoutLog, today: LocalDate, format: LiftFormat, onClick: () -> Unit) {
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
                    format.day(workout.startedAt, today),
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
