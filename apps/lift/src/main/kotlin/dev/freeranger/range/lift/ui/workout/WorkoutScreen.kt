package dev.freeranger.range.lift.ui.workout

import android.text.InputType
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.Load
import dev.freeranger.range.lift.data.LoggedExercise
import dev.freeranger.range.lift.data.Measure
import dev.freeranger.range.lift.data.ProgressMetric
import dev.freeranger.range.lift.data.Record
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.data.filledFrom
import dev.freeranger.range.lift.data.isComplete
import dev.freeranger.range.lift.data.placeholders
import dev.freeranger.range.lift.ui.common.DragHandle
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.NameSheet
import dev.freeranger.range.lift.ui.common.Stat
import dev.freeranger.range.lift.ui.common.StatsCard
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.lift.ui.common.rememberNotificationPermissionRequest
import dev.freeranger.range.lift.ui.common.rememberNow
import dev.freeranger.range.lift.ui.common.title
import dev.freeranger.range.ui.components.RangeNumberCheck
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun WorkoutRoute(
    id: Long,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onAddExercise: () -> Unit,
    onSwapExercise: (entryId: Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
    onRepeated: (workoutId: Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { WorkoutViewModel(id, container.repository, container.scope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keepScreenOn by container.preferences.keepScreenOn.collectAsStateWithLifecycle(false)
    KeepScreenOn(keepScreenOn && state.log?.workout?.isFinished == false)
    WorkoutScreen(
        state = state,
        // Each second, for the stopwatch.
        now = rememberNow(period = 1_000),
        actions = WorkoutActions(
            back = onBack,
            rename = viewModel::rename,
            addExercise = onAddExercise,
            swapExercise = onSwapExercise,
            openExercise = onOpenExercise,
            reorder = viewModel::reorder,
            removeExercise = viewModel::removeExercise,
            restoreExercise = viewModel::restoreExercise,
            addSet = viewModel::addSet,
            updateSet = viewModel::updateSet,
            deleteSet = viewModel::deleteSet,
            restoreSet = viewModel::restoreSet,
            tidy = viewModel::tidy,
            finish = {
                viewModel.finish()
                onFinished()
            },
            delete = {
                viewModel.delete()
                onBack()
            },
            setDone = viewModel::setDone,
            setNotes = viewModel::setNotes,
            repeat = { viewModel.repeat(onRepeated) },
            saveTemplate = viewModel::saveTemplate,
        ),
    )
}

/** Everything the workout screen can ask for. */
data class WorkoutActions(
    val back: () -> Unit = {},
    val rename: (String) -> Unit = {},
    val addExercise: () -> Unit = {},
    /** Picks another exercise to take the place of the workout's exercise [entryId]. */
    val swapExercise: (entryId: Long) -> Unit = {},
    val openExercise: (exerciseId: Long) -> Unit = {},
    val reorder: (entryIds: List<Long>) -> Unit = {},
    val removeExercise: (LoggedExercise) -> Unit = {},
    /** Puts back an exercise just removed, with its sets. */
    val restoreExercise: (LoggedExercise) -> Unit = {},
    val addSet: (entryId: Long) -> Unit = {},
    val updateSet: (WorkoutSet) -> Unit = {},
    val deleteSet: (WorkoutSet) -> Unit = {},
    /** Puts back a set just deleted. */
    val restoreSet: (WorkoutSet) -> Unit = {},
    /** Drops unticked sets once a finished workout is edited. */
    val tidy: () -> Unit = {},
    val finish: () -> Unit = {},
    val delete: () -> Unit = {},
    /** A set was ticked off: the rest stopwatch starts again. */
    val setDone: () -> Unit = {},
    val setNotes: (String) -> Unit = {},
    val saveTemplate: (name: String) -> Unit = {},
    /** Starts a finished workout again, as a new one. */
    val repeat: () -> Unit = {},
)

enum class WorkoutDialog { Rename, Delete, SaveTemplate, Notes, DropUnticked }

/**
 * A workout. In progress, it's where sets are logged: a card per exercise, dragged by its handle
 * into any order, each set with last time's numbers as placeholders and its number to tap once
 * it's done, which restarts the rest stopwatch. Finishing needs every set ticked off. Finished, it's a
 * read-only summary with its notes, which can be opened again for editing to fix a mistake. Sets
 * that beat a record get a trophy. Deleted sets and removed exercises can be put back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    state: WorkoutState,
    now: Instant,
    actions: WorkoutActions,
    modifier: Modifier = Modifier,
    initialDialog: WorkoutDialog? = null,
    initiallyEditing: Boolean = false,
) {
    val format = rememberLiftFormat()
    val log = state.log ?: return
    val workout = log.workout
    val today = now.atZone(format.zone).toLocalDate()
    var dialog by rememberSaveable { mutableStateOf(initialDialog) }
    var menu by remember { mutableStateOf(false) }
    val ready = log.exercises.any { it.sets.isNotEmpty() } && log.exercises.all { logged -> logged.sets.all { it.done } }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.template_saved)
    val requestNotifications = rememberNotificationPermissionRequest()
    // The rest stopwatch's notification is the reason to ask, so ask when it first starts.
    // A finished workout can be opened for editing, to fix a mistyped set.
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    val stopEditing = {
        actions.tidy()
        editing = false
    }
    BackHandler(enabled = editing, onBack = stopEditing)
    val unticked = log.exercises.sumOf { logged -> logged.sets.count { !it.done } }
    // Finishing the workout, or the editing of a finished one: unticked sets go either way.
    val end = if (workout.isFinished) stopEditing else actions.finish
    val endLabel = stringResource(if (workout.isFinished) R.string.summary_done else R.string.workout_finish)
    val setDeleted = stringResource(R.string.workout_set_deleted)
    val exerciseRemoved = stringResource(R.string.workout_exercise_removed)
    val undo = stringResource(R.string.action_undo)
    val offerUndo = { message: String, restore: () -> Unit ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) restore()
        }
    }
    val logging = actions.copy(
        setDone = {
            if (!workout.isFinished) {
                requestNotifications()
                actions.setDone()
            }
        },
        deleteSet = { set ->
            actions.deleteSet(set)
            offerUndo(setDeleted) { actions.restoreSet(set) }
        },
        removeExercise = { logged ->
            actions.removeExercise(logged)
            offerUndo(exerciseRemoved) { actions.restoreExercise(logged) }
        },
    )
    AnnounceRecords(state.records, enabled = !workout.isFinished, format, snackbar)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val since = workout.sinceLastSet(now)
            AnimatedVisibility(visible = since != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                RestBar(since ?: Duration.ZERO, format)
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(format.name(workout), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (workout.isFinished) format.day(workout.startedAt, today) else format.stopwatch(workout.startedAt, now),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.back) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    // Each lights up once every set is ticked off. Before then it looks off but still
                    // works, offering to drop what's unticked; with nothing ticked at all, it is off.
                    if (!workout.isFinished || editing) {
                        val colors = MaterialTheme.colorScheme
                        Button(
                            onClick = { if (ready) end() else dialog = WorkoutDialog.DropUnticked },
                            enabled = unticked < log.exercises.sumOf { it.sets.size },
                            colors = if (ready) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.buttonColors(containerColor = colors.onSurface.copy(alpha = 0.12f), contentColor = colors.onSurface.copy(alpha = 0.38f))
                            },
                        ) {
                            Text(endLabel)
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(RangeIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (log.exercises.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.template_save)) },
                                    leadingIcon = { Icon(RangeIcons.BookmarkAdd, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        dialog = WorkoutDialog.SaveTemplate
                                    },
                                )
                            }
                            if (workout.isFinished && !editing) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.workout_repeat)) },
                                    leadingIcon = { Icon(RangeIcons.Repeat, contentDescription = null) },
                                    enabled = !state.busy,
                                    onClick = {
                                        menu = false
                                        actions.repeat()
                                    },
                                )
                            }
                            if (workout.isFinished && !editing) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.workout_edit)) },
                                    leadingIcon = { Icon(RangeIcons.EditNote, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        editing = true
                                    },
                                )
                            }
                            if (workout.isFinished) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.workout_edit_notes)) },
                                    leadingIcon = { Icon(RangeIcons.StickyNote2, contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        dialog = WorkoutDialog.Notes
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.workout_rename)) },
                                leadingIcon = { Icon(RangeIcons.Edit, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    dialog = WorkoutDialog.Rename
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (workout.isFinished) R.string.workout_delete else R.string.workout_discard)) },
                                leadingIcon = { Icon(RangeIcons.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    dialog = WorkoutDialog.Delete
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + RangeSpace.s6)
        val recordSets = state.records.map { it.set.id }.toSet()
        if (workout.isFinished && !editing) {
            FinishedWorkout(log, recordSets, format, actions, contentPadding)
        } else {
            WorkoutInProgress(log, state.previous, recordSets, format, logging, contentPadding)
        }
    }

    when (dialog) {
        WorkoutDialog.Rename -> NameSheet(
            placeholder = stringResource(R.string.workout_default_name),
            saveLabel = stringResource(R.string.workout_save),
            onSave = actions.rename,
            onDismiss = { dialog = null },
            initial = workout.name,
            // Left blank, it's called "Workout" again.
            blankAllowed = true,
            // Screenshots show the sheet without a keyboard.
            autoFocus = initialDialog != WorkoutDialog.Rename,
        )
        WorkoutDialog.SaveTemplate -> NameSheet(
            placeholder = stringResource(R.string.workout_default_name),
            saveLabel = stringResource(R.string.template_save),
            onSave = {
                actions.saveTemplate(it)
                scope.launch { snackbar.showSnackbar(savedMessage) }
            },
            onDismiss = { dialog = null },
            initial = format.name(workout),
            autoFocus = initialDialog != WorkoutDialog.SaveTemplate,
        )
        WorkoutDialog.Notes -> NameSheet(
            placeholder = stringResource(R.string.workout_notes),
            saveLabel = stringResource(R.string.workout_save),
            onSave = actions.setNotes,
            onDismiss = { dialog = null },
            initial = workout.notes,
            blankAllowed = true,
            autoFocus = initialDialog != WorkoutDialog.Notes,
            multiLine = true,
        )
        WorkoutDialog.DropUnticked -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(pluralStringResource(R.plurals.workout_drop_unticked, unticked, unticked)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        end()
                    },
                ) { Text(endLabel) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
        WorkoutDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (workout.isFinished) R.string.workout_delete_title else R.string.workout_discard_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        actions.delete()
                    },
                ) { Text(stringResource(if (workout.isFinished) R.string.workout_delete_confirm else R.string.workout_discard_confirm)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
        null -> {}
    }
}

private val SetColumn = 48.dp

@Composable
private fun WorkoutInProgress(
    log: WorkoutLog,
    previous: Map<Long, List<WorkoutSet>>,
    recordSets: Set<Long>,
    format: LiftFormat,
    actions: WorkoutActions,
    contentPadding: PaddingValues,
) {
    val haptics = LocalHapticFeedback.current
    val byEntry = log.exercises.associateBy { it.entry.id }
    // The order on screen, moved as cards are dragged and saved when one is dropped.
    val saved = log.exercises.map { it.entry.id }
    var order by remember { mutableStateOf(saved) }
    LaunchedEffect(saved) { order = saved }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = order.indexOf(from.key)
        val toIndex = order.indexOf(to.key)
        if (fromIndex >= 0 && toIndex >= 0) {
            order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        state = listState,
        contentPadding = contentPadding,
    ) {
        items(order.mapNotNull(byEntry::get), key = { it.entry.id }) { logged ->
            ReorderableItem(reorderState, key = logged.entry.id) { dragging ->
                val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "cardElevation")
                val handle = Modifier.draggableHandle(
                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                    onDragStopped = { actions.reorder(order) },
                )
                ExerciseCard(elevation) {
                    ExerciseHeader(logged, format, actions) { DragHandle(handle) }
                    ColumnHeaders(logged.exercise, format)
                    val shown = placeholders(logged.sets, previous[logged.entry.id].orEmpty())
                    logged.sets.forEachIndexed { index, set ->
                        key(set.id) { SetRow(index, set, shown[index], set.id in recordSets, logged.exercise, format, actions) }
                    }
                    TextButton(onClick = { actions.addSet(logged.entry.id) }, modifier = Modifier.padding(start = RangeSpace.s2, bottom = RangeSpace.s1)) {
                        Icon(RangeIcons.Add, contentDescription = null)
                        Text(stringResource(R.string.workout_add_set), modifier = Modifier.padding(start = RangeSpace.s2))
                    }
                }
            }
        }
        item(key = "add exercise") {
            Column(Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s4)) {
                FilledTonalButton(onClick = actions.addExercise, modifier = Modifier.fillMaxWidth()) {
                    Icon(RangeIcons.Add, contentDescription = null)
                    Text(stringResource(R.string.workout_add_exercise), modifier = Modifier.padding(start = RangeSpace.s2))
                }
            }
        }
    }
}

@Composable
private fun FinishedWorkout(log: WorkoutLog, recordSets: Set<Long>, format: LiftFormat, actions: WorkoutActions, contentPadding: PaddingValues) {
    val workout = log.workout
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "stats") {
            StatsCard {
                Stat(stringResource(R.string.stat_duration), format.duration(workout.startedAt, workout.finishedAt ?: workout.startedAt))
                Stat(stringResource(R.string.stat_sets), log.doneSets.size.toString())
                Stat(stringResource(R.string.stat_volume), format.volume(log.volume))
            }
        }
        if (workout.notes.isNotBlank()) {
            item(key = "notes") {
                ExerciseCard(elevation = 0.dp) {
                    Column(Modifier.padding(RangeSpace.s4)) {
                        Text(stringResource(R.string.workout_notes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(workout.notes, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        items(log.exercises, key = { it.entry.id }) { logged ->
            ExerciseCard(elevation = 0.dp) {
                ExerciseHeader(logged, format, actions)
                logged.sets.forEachIndexed { index, set ->
                    Row(
                        Modifier.padding(horizontal = RangeSpace.s4).padding(bottom = RangeSpace.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.width(32.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(format.set(set, logged.exercise), style = MaterialTheme.typography.bodyLarge)
                        if (set.id in recordSets) RecordBadge(Modifier.padding(start = RangeSpace.s2))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(elevation: Dp, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RangeSpace.s3, vertical = RangeSpace.s1),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
    ) {
        content()
    }
}

/** The exercise's name, opening its page, with a drag [handle] and a menu while the workout is in progress. */
@Composable
private fun ExerciseHeader(logged: LoggedExercise, format: LiftFormat, actions: WorkoutActions, handle: (@Composable () -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (handle != null) 0.dp else RangeSpace.s4, end = RangeSpace.s1)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        handle?.invoke()
        Column(
            Modifier
                .weight(1f)
                .clickable { actions.openExercise(logged.exercise.id) },
        ) {
            Text(
                format.name(logged.exercise),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (handle != null) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(RangeIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.exercise_swap)) },
                        leadingIcon = { Icon(RangeIcons.SwapHoriz, contentDescription = null) },
                        onClick = {
                            menu = false
                            actions.swapExercise(logged.entry.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workout_remove_exercise)) },
                        leadingIcon = { Icon(RangeIcons.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            actions.removeExercise(logged)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnHeaders(exercise: Exercise, format: LiftFormat) {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(start = RangeSpace.s1, end = RangeSpace.s4),
        horizontalArrangement = Arrangement.spacedBy(RangeSpace.s3),
    ) {
        Text(stringResource(R.string.workout_column_set), Modifier.width(SetColumn), style = style, color = color, textAlign = TextAlign.Center)
        Text(format.weightLabel(exercise.load), Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.Center)
        Text(format.measure(exercise.measure), Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.Center)
    }
}

/**
 * One set: its number, tapped once it's done, then the weight and reps or time. Empty fields show
 * [placeholder] and take its numbers when the set is ticked off. Swipe it away to delete it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetRow(
    index: Int,
    set: WorkoutSet,
    placeholder: WorkoutSet?,
    record: Boolean,
    exercise: Exercise,
    format: LiftFormat,
    actions: WorkoutActions,
) {
    val colors = MaterialTheme.colorScheme
    val number = index + 1
    val timed = exercise.measure == Measure.Time
    fun amountText(set: WorkoutSet?) = (if (timed) set?.seconds?.let(::timeDigits) else set?.reps?.toString()).orEmpty()

    // Typed text is kept here, not re-read from the database, so the cursor never jumps while typing.
    val weightText = { kg: Double? -> kg?.let { format.typedWeight(it, exercise.load) } }
    var weight by remember(set.id) { mutableStateOf(weightText(set.weight).orEmpty()) }
    var amount by remember(set.id) { mutableStateOf(amountText(set)) }
    val amountFocus = remember { FocusRequester() }
    val latest by rememberUpdatedState(set)
    val save by rememberUpdatedState(actions.updateSet)
    fun typed(done: Boolean) = latest.copy(
        weight = format.parseWeight(weight),
        reps = if (timed) set.reps else amount.toIntOrNull(),
        seconds = if (timed) parseTimeDigits(amount) else set.seconds,
        done = done,
    )
    // Typing is saved once it pauses, not on every key: each save works the whole history out again.
    var unsaved by remember(set.id) { mutableStateOf(false) }
    LaunchedEffect(weight, amount, unsaved) {
        if (!unsaved) return@LaunchedEffect
        delay(SAVE_AFTER)
        save(typed(latest.done))
        unsaved = false
    }
    DisposableEffect(set.id) { onDispose { if (unsaved) save(typed(latest.done)) } }
    // Ticked off elsewhere, from the notification: show the numbers it took, where nothing was typed.
    LaunchedEffect(set.done) {
        if (set.done && !unsaved) {
            if (weight.isEmpty()) weight = weightText(set.weight).orEmpty()
            if (amount.isEmpty()) amount = amountText(set)
        }
    }

    val tick: (Boolean) -> Unit = { checked ->
        unsaved = false
        if (!checked) {
            actions.updateSet(typed(false))
        } else {
            val filled = typed(true).filledFrom(placeholder)
            if (filled.isComplete(exercise)) {
                weight = weightText(filled.weight).orEmpty()
                amount = amountText(filled)
                actions.updateSet(filled)
                actions.setDone()
            } else {
                // Nothing to go on: ask for the reps or time first.
                amountFocus.requestFocus()
            }
        }
    }

    val dismiss = rememberSwipeToDismissBoxState()
    val card = colors.surfaceContainer
    // A light wash of the accent, opaque so the delete background stays hidden until swiped.
    // Dark containers are deeper, so dark themes take less of it.
    val done = colors.secondaryContainer.copy(alpha = if (card.luminance() < 0.5f) 0.3f else 0.45f).compositeOver(card)
    val background by animateColorAsState(if (set.done) done else card, label = "setBackground")
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        onDismiss = { actions.deleteSet(set) },
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.errorContainer)
                    .padding(horizontal = RangeSpace.gutter),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(RangeIcons.Delete, contentDescription = stringResource(R.string.workout_delete_set), tint = colors.onErrorContainer)
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(start = RangeSpace.s1, end = RangeSpace.s4),
            horizontalArrangement = Arrangement.spacedBy(RangeSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                RangeNumberCheck(
                    number = number,
                    checked = set.done,
                    onCheckedChange = tick,
                    contentDescription = stringResource(R.string.workout_set_done, number),
                )
                if (record) RecordBadge(Modifier.align(Alignment.TopEnd))
            }
            // Bodyweight takes a sign: plus for weight added, minus for help.
            val signed = exercise.load == Load.Bodyweight
            NumberField(
                value = weight,
                onValueChange = {
                    if (isWeight(it, signed)) {
                        weight = it
                        unsaved = true
                    }
                },
                placeholder = weightText(placeholder?.weight),
                description = stringResource(R.string.workout_set_weight, number),
                keyboardType = KeyboardType.Decimal,
                signed = signed,
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = amount,
                onValueChange = {
                    if (it.length <= (if (timed) 4 else 3) && it.all(Char::isDigit)) {
                        amount = it
                        unsaved = true
                    }
                },
                placeholder = if (timed) placeholder?.seconds?.let(format::seconds) else placeholder?.reps?.toString(),
                description = stringResource(if (timed) R.string.workout_set_time else R.string.workout_set_reps, number),
                keyboardType = KeyboardType.Number,
                visualTransformation = if (timed) TimeDigits else VisualTransformation.None,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(amountFocus),
            )
        }
    }
}

/** A compact Material text field for a number in a set row, showing [placeholder] while empty. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String?,
    description: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    signed: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = colors.surfaceContainerHighest,
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedBorderColor = Color.Transparent,
    )
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center, fontFeatureSettings = "tnum")
    SignedKeyboard(signed) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .height(40.dp)
                .semantics { contentDescription = description },
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            visualTransformation = visualTransformation,
            interactionSource = interactions,
            decorationBox = { field ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = field,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = visualTransformation,
                    interactionSource = interactions,
                    placeholder = placeholder?.let {
                        { Text(it, Modifier.fillMaxWidth(), style = textStyle.copy(color = colors.onSurfaceVariant.copy(alpha = 0.6f))) }
                    },
                    colors = fieldColors,
                    contentPadding = PaddingValues(horizontal = RangeSpace.s1),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = false,
                            interactionSource = interactions,
                            colors = fieldColors,
                            shape = RoundedCornerShape(8.dp),
                        )
                    },
                )
            },
        )
    }
}

/** Keeps the phone from locking while [enabled]: between sets, with the setting on. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** A weight as it's typed: up to six digits with one decimal mark, and a leading sign if [signed]. */
private fun isWeight(text: String, signed: Boolean): Boolean {
    val digits = if (signed) text.removePrefix("-").removePrefix("−").removePrefix("+") else text
    return digits.length <= 6 && digits.all { it.isDigit() || it == '.' || it == ',' } && digits.count { it == '.' || it == ',' } <= 1
}

/** Asks the keyboard for a minus key when [enabled], which Compose's number keyboards leave out. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SignedKeyboard(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) return content()
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                PlatformTextInputMethodRequest { attributes ->
                    request.createInputConnection(attributes).also {
                        attributes.inputType = attributes.inputType or InputType.TYPE_NUMBER_FLAG_SIGNED
                    }
                },
            )
        },
        content = content,
    )
}

/** A time as typed: digits only, the last two being seconds, so "130" is 1:30 and "45" is 45 seconds. */
private fun timeDigits(seconds: Int): String =
    if (seconds < 60) seconds.toString() else "${seconds / 60}${(seconds % 60).toString().padStart(2, '0')}"

private fun parseTimeDigits(digits: String): Int? {
    val n = digits.toIntOrNull() ?: return null
    return if (digits.length <= 2) n else n / 100 * 60 + n % 100
}

/** Shows typed time digits with a colon before the seconds: "130" reads "1:30". */
private object TimeDigits : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        if (digits.length < 3) return TransformedText(text, OffsetMapping.Identity)
        val cut = digits.length - 2
        return TransformedText(
            AnnotatedString(digits.substring(0, cut) + ":" + digits.substring(cut)),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = if (offset <= cut) offset else offset + 1
                override fun transformedToOriginal(offset: Int) = if (offset <= cut) offset else offset - 1
            },
        )
    }
}

/** A small trophy beside a set that beat a record. */
@Composable
private fun RecordBadge(modifier: Modifier = Modifier) {
    Icon(
        RangeIcons.EmojiEvents,
        contentDescription = stringResource(R.string.workout_record),
        tint = MaterialTheme.colorScheme.tertiary,
        modifier = modifier.size(18.dp),
    )
}

/**
 * Says so when a newly ticked set beats a record. Records already there when the screen opens
 * aren't announced again, and one that moves to a better set in the same workout is announced once more.
 */
@Composable
private fun AnnounceRecords(records: List<Record>, enabled: Boolean, format: LiftFormat, snackbar: SnackbarHostState) {
    val messages = records.associate { record ->
        (record.set.id to record.metric) to stringResource(
            R.string.record_new,
            format.name(record.exercise),
            stringResource(record.metric.title) + " " + format.metric(record.metric, record.value),
        )
    }
    var announced by remember { mutableStateOf<Set<Pair<Long, ProgressMetric>>?>(null) }
    LaunchedEffect(messages.keys) {
        val before = announced
        announced = messages.keys
        if (before == null || !enabled) return@LaunchedEffect
        (messages.keys - before).forEach { snackbar.showSnackbar(messages.getValue(it)) }
    }
}

/** Time since the last set, counting up. */
@Composable
private fun RestBar(since: Duration, format: LiftFormat) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(RangeIcons.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.rest_label),
                modifier = Modifier.weight(1f).padding(start = RangeSpace.s3),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(format.seconds(since.seconds.toInt()), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
        }
    }
}

/** How long typing pauses before it's saved. */
private const val SAVE_AFTER = 600L
