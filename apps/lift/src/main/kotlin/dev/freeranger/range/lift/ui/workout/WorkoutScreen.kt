package dev.freeranger.range.lift.ui.workout

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
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
import dev.freeranger.range.lift.data.LoggedExercise
import dev.freeranger.range.lift.data.Measure
import dev.freeranger.range.lift.data.ProgressMetric
import dev.freeranger.range.lift.data.Record
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.data.filledFrom
import dev.freeranger.range.lift.data.isComplete
import dev.freeranger.range.lift.data.placeholders
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
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun WorkoutRoute(id: Long, onBack: () -> Unit, onFinished: () -> Unit, onAddExercise: () -> Unit, onOpenExercise: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { WorkoutViewModel(id, container.repository, container.scope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScreen(
        state = state,
        // Each second, for the stopwatch.
        now = rememberNow(period = 1_000),
        actions = WorkoutActions(
            back = onBack,
            rename = viewModel::rename,
            addExercise = onAddExercise,
            openExercise = onOpenExercise,
            reorder = viewModel::reorder,
            removeExercise = viewModel::removeExercise,
            addSet = viewModel::addSet,
            updateSet = viewModel::updateSet,
            deleteSet = viewModel::deleteSet,
            finish = {
                viewModel.finish()
                onFinished()
            },
            delete = {
                viewModel.delete()
                onBack()
            },
            startRest = viewModel::startRest,
            addRest = viewModel::addRest,
            skipRest = viewModel::skipRest,
            setNotes = viewModel::setNotes,
            saveTemplate = viewModel::saveTemplate,
        ),
    )
}

/** Everything the workout screen can ask for. */
data class WorkoutActions(
    val back: () -> Unit = {},
    val rename: (String) -> Unit = {},
    val addExercise: () -> Unit = {},
    val openExercise: (exerciseId: Long) -> Unit = {},
    val reorder: (entryIds: List<Long>) -> Unit = {},
    val removeExercise: (entryId: Long) -> Unit = {},
    val addSet: (entryId: Long) -> Unit = {},
    val updateSet: (WorkoutSet) -> Unit = {},
    val deleteSet: (setId: Long) -> Unit = {},
    val finish: () -> Unit = {},
    val delete: () -> Unit = {},
    /** Starts the rest after a set of the exercise [label] names. */
    val startRest: (seconds: Int, label: String) -> Unit = { _, _ -> },
    val addRest: (seconds: Int) -> Unit = {},
    val skipRest: () -> Unit = {},
    val setNotes: (String) -> Unit = {},
    val saveTemplate: (name: String) -> Unit = {},
)

enum class WorkoutDialog { Rename, Delete, SaveTemplate, Notes }

/**
 * A workout. In progress, it's where sets are logged: a card per exercise, dragged by its handle
 * into any order, each set with last time's numbers as placeholders and its number to tap once
 * it's done, which starts the rest timer. Finishing needs every set ticked off. Finished, it's a
 * read-only summary with its notes. Sets that beat a record get a trophy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    state: WorkoutState,
    now: Instant,
    actions: WorkoutActions,
    modifier: Modifier = Modifier,
    initialDialog: WorkoutDialog? = null,
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
    // The rest timer's notification is the reason to ask, so ask when the first one starts.
    val withRest = actions.copy(
        startRest = { seconds, label ->
            if (seconds > 0) requestNotifications()
            actions.startRest(seconds, label)
        },
    )
    AnnounceRecords(state.records, enabled = !workout.isFinished, format, snackbar)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val left = workout.restLeft(now).takeIf { !workout.isFinished }
            // Keeps showing the last time while it slides away.
            var shown by remember { mutableStateOf(left) }
            if (left != null) shown = left
            AnimatedVisibility(visible = left != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                shown?.let { RestBar(it, workout.restSeconds ?: it.seconds.toInt(), format, actions) }
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
                    if (!workout.isFinished) {
                        // Lights up once every set is ticked off.
                        Button(onClick = actions.finish, enabled = ready) {
                            Text(stringResource(R.string.workout_finish))
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
        if (workout.isFinished) {
            FinishedWorkout(log, recordSets, format, actions, contentPadding)
        } else {
            WorkoutInProgress(log, state.previous, recordSets, format, withRest, contentPadding)
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
            placeholder = stringResource(R.string.summary_notes_placeholder),
            saveLabel = stringResource(R.string.workout_save),
            onSave = actions.setNotes,
            onDismiss = { dialog = null },
            initial = workout.notes,
            blankAllowed = true,
            autoFocus = initialDialog != WorkoutDialog.Notes,
            multiLine = true,
        )
        WorkoutDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(if (workout.isFinished) R.string.workout_delete_title else R.string.workout_discard_title)) },
            text = { Text(stringResource(if (workout.isFinished) R.string.workout_delete_text else R.string.workout_discard_text)) },
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
                if (log.exercises.isEmpty()) {
                    Text(
                        stringResource(R.string.workout_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = RangeSpace.s6),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
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
            format.kind(logged.exercise)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (handle != null) {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(RangeIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.workout_remove_exercise)) },
                        leadingIcon = { Icon(RangeIcons.Delete, contentDescription = null) },
                        onClick = {
                            menu = false
                            actions.removeExercise(logged.entry.id)
                        },
                    )
                }
            }
        }
    }
}

/** Pressed and dragged to move its card; [modifier] carries the reorder library's drag handling. */
@Composable
private fun DragHandle(modifier: Modifier) {
    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Icon(
            RangeIcons.DragIndicator,
            contentDescription = stringResource(R.string.workout_drag),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        if (exercise.weighted) {
            Text(format.weightLabel(exercise.load), Modifier.weight(1f), style = style, color = color, textAlign = TextAlign.Center)
        }
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
    var weight by remember(set.id) { mutableStateOf(set.weight?.let(format::number).orEmpty()) }
    var amount by remember(set.id) { mutableStateOf(amountText(set)) }
    val amountFocus = remember { FocusRequester() }
    fun typed(done: Boolean) = set.copy(
        weight = if (exercise.weighted) format.parseWeight(weight) else null,
        reps = if (timed) set.reps else amount.toIntOrNull(),
        seconds = if (timed) parseTimeDigits(amount) else set.seconds,
        done = done,
    )
    val tick: (Boolean) -> Unit = { checked ->
        if (!checked) {
            actions.updateSet(typed(false))
        } else {
            val filled = typed(true).filledFrom(placeholder)
            if (filled.isComplete(exercise)) {
                weight = filled.weight?.let(format::number).orEmpty()
                amount = amountText(filled)
                actions.updateSet(filled)
                actions.startRest(exercise.restSeconds, format.name(exercise))
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
        onDismiss = { actions.deleteSet(set.id) },
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
            if (exercise.weighted) {
                NumberField(
                    value = weight,
                    onValueChange = {
                        if (it.length <= 6 && it.all { c -> c.isDigit() || c == '.' || c == ',' } && it.count { c -> c == '.' || c == ',' } <= 1) {
                            weight = it
                            actions.updateSet(typed(set.done))
                        }
                    },
                    placeholder = placeholder?.weight?.let(format::number),
                    description = stringResource(R.string.workout_set_weight, number),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
            NumberField(
                value = amount,
                onValueChange = {
                    if (it.length <= (if (timed) 4 else 3) && it.all(Char::isDigit)) {
                        amount = it
                        actions.updateSet(typed(set.done))
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
) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = colors.surfaceContainerHighest,
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedBorderColor = Color.Transparent,
    )
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center, fontFeatureSettings = "tnum")
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

/** The running rest: time left, a bar running down, and buttons to shorten, lengthen or skip it. */
@Composable
private fun RestBar(left: Duration, total: Int, format: LiftFormat, actions: WorkoutActions) {
    val seconds = ((left.toMillis() + 999) / 1000).toInt()
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { (seconds / total.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Row(
                Modifier.padding(start = RangeSpace.gutter, end = RangeSpace.s3, top = RangeSpace.s2, bottom = RangeSpace.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(RangeIcons.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = RangeSpace.s3)) {
                    Text(stringResource(R.string.rest_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(format.seconds(seconds), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"))
                }
                TextButton(onClick = { actions.addRest(-REST_STEP) }) { Text(stringResource(R.string.rest_subtract, REST_STEP)) }
                TextButton(onClick = { actions.addRest(REST_STEP) }) { Text(stringResource(R.string.rest_add, REST_STEP)) }
                FilledTonalButton(onClick = actions.skipRest) { Text(stringResource(R.string.rest_skip)) }
            }
        }
    }
}

/** What the rest bar's buttons take off or add. */
private const val REST_STEP = 15
