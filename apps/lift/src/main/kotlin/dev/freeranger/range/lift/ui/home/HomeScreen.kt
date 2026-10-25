package dev.freeranger.range.lift.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import dev.freeranger.range.ui.components.RangeDatePickerDialog
import dev.freeranger.range.ui.components.RangeDayPager
import dev.freeranger.range.ui.components.RangeWeekStrip
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Instant
import java.time.LocalDate

@Composable
fun HomeRoute(
    onOpenWorkout: (Long) -> Unit,
    onOpenTemplates: (creating: Boolean) -> Unit,
    onOpenTools: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onNewExercise: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { HomeViewModel(container.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        now = rememberNow(),
        actions = HomeActions(
            start = { viewModel.start(onOpenWorkout) },
            startTemplate = { viewModel.startTemplate(it, onOpenWorkout) },
            openTemplates = onOpenTemplates,
            openTools = onOpenTools,
            openWorkout = onOpenWorkout,
            openExercise = onOpenExercise,
            newExercise = onNewExercise,
            openSettings = onOpenSettings,
        ),
    )
}

/** Everything the home screen can ask for. */
data class HomeActions(
    /** Starts an empty workout. */
    val start: () -> Unit = {},
    val startTemplate: (templateId: Long) -> Unit = {},
    /** The templates page, [creating] a new one straight away. */
    val openTemplates: (creating: Boolean) -> Unit = {},
    val openTools: () -> Unit = {},
    val openWorkout: (id: Long) -> Unit = {},
    val openExercise: (id: Long) -> Unit = {},
    val newExercise: () -> Unit = {},
    val openSettings: () -> Unit = {},
)

enum class HomeTab { Workouts, Home, Exercises }

/**
 * Three tabs: past workouts a day at a time under a week strip, the dashboard in the middle where
 * the app opens, and the exercise library. Starting a workout offers an empty one or a template;
 * the templates have their own page, from the dashboard's top bar, as do the tools.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeState,
    now: Instant,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    initialTab: HomeTab = HomeTab.Home,
    initialDay: LocalDate? = null,
    initiallyStarting: Boolean = false,
) {
    val format = rememberLiftFormat()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val today = now.atZone(format.zone).toLocalDate()
    // The log opens on the last day trained, which is more use than today's usually empty page.
    val trained = remember(state.history, format) { state.history.map { it.workout.startedAt.atZone(format.zone).toLocalDate() }.toSet() }
    val lastTrained = trained.maxOrNull()?.coerceAtMost(today) ?: today
    var picked by rememberSaveable { mutableStateOf(initialDay) }
    val day = picked ?: lastTrained
    val select = { date: LocalDate -> picked = date.coerceAtMost(today) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var starting by rememberSaveable { mutableStateOf(initiallyStarting) }
    val log = tab == HomeTab.Workouts

    BackHandler(enabled = log && picked != null) { picked = null }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(if (log) format.month(day, today) else stringResource(R.string.app_title)) },
                    actions = {
                        if (log) {
                            if (day != today) {
                                TextButton(onClick = { select(today) }) { Text(stringResource(R.string.date_today)) }
                            }
                            IconButton(onClick = { pickingDate = true }) {
                                Icon(RangeIcons.CalendarMonth, contentDescription = stringResource(R.string.action_go_to_date))
                            }
                        }
                        if (tab == HomeTab.Home) {
                            IconButton(onClick = actions.openTools) {
                                Icon(RangeIcons.Handyman, contentDescription = stringResource(R.string.tools_title))
                            }
                            IconButton(onClick = { actions.openTemplates(false) }) {
                                Icon(RangeIcons.Bookmarks, contentDescription = stringResource(R.string.home_templates))
                            }
                        }
                        IconButton(onClick = actions.openSettings) {
                            Icon(RangeIcons.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    },
                )
                if (log) {
                    RangeWeekStrip(
                        selected = day,
                        today = today,
                        firstDayOfWeek = format.firstDayOfWeek,
                        onSelect = select,
                        label = format::weekdayShort,
                        describe = format::header,
                        marked = { it in trained },
                        modifier = Modifier.padding(bottom = RangeSpace.s2),
                        last = today,
                        // A log looks back: it's the days still to come that are grayed out.
                        faded = { it > today },
                    )
                }
            }
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
                    selected = tab == HomeTab.Home,
                    onClick = { tab = HomeTab.Home },
                    icon = { Icon(RangeIcons.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_home)) },
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
                HomeTab.Workouts, HomeTab.Home -> ExtendedFloatingActionButton(
                    text = { Text(stringResource(if (state.inProgress != null) R.string.workout_continue else R.string.workout_start)) },
                    icon = { Icon(RangeIcons.FitnessCenter, contentDescription = null) },
                    onClick = { state.inProgress?.let { actions.openWorkout(it.workout.id) } ?: run { starting = true } },
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
            HomeTab.Workouts -> RangeDayPager(selected = day, onSelect = select, modifier = Modifier.fillMaxSize(), last = today) { page ->
                DayLog(state, page, format, actions, contentPadding)
            }
            HomeTab.Home -> Dashboard(state, now, format, contentPadding)
            HomeTab.Exercises -> ExerciseList(
                exercises = state.exercises,
                bests = state.bests,
                onClick = { actions.openExercise(it.id) },
                contentPadding = contentPadding,
                showEmpty = state.loaded,
            )
        }
    }

    if (starting) {
        StartSheet(state.templates.filter { it.exercises.isNotEmpty() }, actions, onDismiss = { starting = false })
    }

    if (pickingDate) {
        RangeDatePickerDialog(
            initial = day,
            locale = format.locale,
            confirmText = stringResource(R.string.dialog_ok),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                select(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
            last = today,
        )
    }
}

/** One day's page of the log: the workouts done that day, or a rest day. */
@Composable
private fun DayLog(state: HomeState, day: LocalDate, format: LiftFormat, actions: HomeActions, contentPadding: PaddingValues) {
    val workouts = remember(state.history, day, format) {
        state.history.filter { it.workout.startedAt.atZone(format.zone).toLocalDate() == day }.sortedBy { it.workout.startedAt }
    }
    if (workouts.isEmpty()) {
        if (!state.loaded) return
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(RangeSpace.s8), contentAlignment = Alignment.Center) {
            Text(
                stringResource(if (state.history.isEmpty()) R.string.workouts_empty else R.string.workouts_rest_day),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(workouts, key = { it.workout.id }) { log ->
            WorkoutCard(log, format, onClick = { actions.openWorkout(log.workout.id) })
        }
    }
}

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
