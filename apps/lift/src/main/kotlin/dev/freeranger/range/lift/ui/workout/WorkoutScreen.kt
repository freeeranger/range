package dev.freeranger.range.lift.ui.workout

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.LoggedExercise
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.NameSheet
import dev.freeranger.range.lift.ui.common.Stat
import dev.freeranger.range.lift.ui.common.StatsCard
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.lift.ui.common.rememberNow
import dev.freeranger.range.ui.components.RangeCheckbox
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Instant

@Composable
fun WorkoutRoute(id: Long, onBack: () -> Unit, onAddExercise: () -> Unit, onOpenExercise: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { WorkoutViewModel(id, container.repository, container.scope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutScreen(
        state = state,
        now = rememberNow(),
        actions = WorkoutActions(
            back = onBack,
            rename = viewModel::rename,
            addExercise = onAddExercise,
            openExercise = onOpenExercise,
            removeExercise = viewModel::removeExercise,
            addSet = viewModel::addSet,
            updateSet = viewModel::updateSet,
            deleteSet = viewModel::deleteSet,
            finish = {
                viewModel.finish()
                onBack()
            },
            delete = {
                viewModel.delete()
                onBack()
            },
        ),
    )
}

/** Everything the workout screen can ask for. */
data class WorkoutActions(
    val back: () -> Unit = {},
    val rename: (String) -> Unit = {},
    val addExercise: () -> Unit = {},
    val openExercise: (exerciseId: Long) -> Unit = {},
    val removeExercise: (entryId: Long) -> Unit = {},
    val addSet: (entryId: Long) -> Unit = {},
    val updateSet: (WorkoutSet) -> Unit = {},
    val deleteSet: (setId: Long) -> Unit = {},
    val finish: () -> Unit = {},
    val delete: () -> Unit = {},
)

enum class WorkoutDialog { Rename, Finish, Delete }

/**
 * A workout. In progress, it's where sets are logged: each exercise lists its sets with what was
 * done last time beside them, ready to adjust and tick off. Finished, it's a read-only summary.
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(format.name(workout), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (workout.isFinished) {
                                format.day(workout.startedAt, today)
                            } else {
                                stringResource(R.string.workout_started, format.time(workout.startedAt), format.duration(workout.startedAt, now))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.back) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    if (!workout.isFinished) {
                        Button(
                            onClick = { if (log.exercises.any { e -> e.sets.any { !it.done } }) dialog = WorkoutDialog.Finish else actions.finish() },
                            enabled = log.doneSets.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.workout_finish))
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(RangeIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + RangeSpace.s6),
        ) {
            if (workout.isFinished) {
                finishedWorkout(log, format, actions)
            } else {
                workoutInProgress(log, state.previous, format, actions)
            }
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
        WorkoutDialog.Finish -> ConfirmDialog(
            title = stringResource(R.string.workout_finish_title),
            text = stringResource(R.string.workout_finish_text),
            confirm = stringResource(R.string.workout_finish),
            dismiss = stringResource(R.string.workout_keep_going),
            onConfirm = {
                dialog = null
                actions.finish()
            },
            onDismiss = { dialog = null },
        )
        WorkoutDialog.Delete -> ConfirmDialog(
            title = stringResource(if (workout.isFinished) R.string.workout_delete_title else R.string.workout_discard_title),
            text = stringResource(if (workout.isFinished) R.string.workout_delete_text else R.string.workout_discard_text),
            confirm = stringResource(if (workout.isFinished) R.string.workout_delete_confirm else R.string.workout_discard_confirm),
            dismiss = stringResource(R.string.dialog_cancel),
            onConfirm = {
                dialog = null
                actions.delete()
            },
            onDismiss = { dialog = null },
        )
        null -> {}
    }
}

private val SetColumn = 32.dp
private val WeightColumn = 76.dp
private val RepsColumn = 60.dp
private val CheckColumn = 48.dp

private fun LazyListScope.workoutInProgress(
    log: WorkoutLog,
    previous: Map<Long, List<WorkoutSet>>,
    format: LiftFormat,
    actions: WorkoutActions,
) {
    log.exercises.forEach { logged ->
        val entryId = logged.entry.id
        item(key = "exercise $entryId") { ExerciseHeader(logged, format, actions, removable = true) }
        item(key = "columns $entryId") { ColumnHeaders() }
        itemsIndexed(logged.sets, key = { _, set -> set.id }) { index, set ->
            SetRow(index, set, previous[entryId]?.getOrNull(index), format, actions)
        }
        item(key = "add set $entryId") {
            TextButton(onClick = { actions.addSet(entryId) }, modifier = Modifier.padding(start = RangeSpace.s2)) {
                Icon(RangeIcons.Add, contentDescription = null)
                Text(stringResource(R.string.workout_add_set), modifier = Modifier.padding(start = RangeSpace.s2))
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

private fun LazyListScope.finishedWorkout(log: WorkoutLog, format: LiftFormat, actions: WorkoutActions) {
    val workout = log.workout
    item(key = "stats") {
        StatsCard {
            Stat(stringResource(R.string.stat_duration), format.duration(workout.startedAt, workout.finishedAt ?: workout.startedAt))
            Stat(stringResource(R.string.stat_sets), log.doneSets.size.toString())
            Stat(stringResource(R.string.stat_volume), format.volume(log.volume))
        }
    }
    log.exercises.forEach { logged ->
        item(key = "exercise ${logged.entry.id}") { ExerciseHeader(logged, format, actions, removable = false) }
        itemsIndexed(logged.sets, key = { _, set -> set.id }) { index, set ->
            Row(Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s1)) {
                Text(
                    "${index + 1}",
                    modifier = Modifier.width(SetColumn),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(format.set(set), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ExerciseHeader(logged: LoggedExercise, format: LiftFormat, actions: WorkoutActions, removable: Boolean) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = RangeSpace.s4)
            .padding(start = RangeSpace.gutter, end = RangeSpace.s1)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            format.name(logged.exercise),
            modifier = Modifier
                .weight(1f)
                .clickable { actions.openExercise(logged.exercise.id) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (removable) {
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

@Composable
private fun ColumnHeaders() {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(horizontal = RangeSpace.gutter),
        horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2),
    ) {
        Text(stringResource(R.string.workout_column_set), Modifier.width(SetColumn), style = style, color = color, textAlign = TextAlign.Center)
        Text(stringResource(R.string.workout_column_previous), Modifier.weight(1f), style = style, color = color)
        Text(stringResource(R.string.unit_kg_label), Modifier.width(WeightColumn), style = style, color = color, textAlign = TextAlign.Center)
        Text(stringResource(R.string.workout_column_reps), Modifier.width(RepsColumn), style = style, color = color, textAlign = TextAlign.Center)
        Box(Modifier.width(CheckColumn))
    }
}

/** One set: last time's numbers, this time's weight and reps, and a tick. Swipe it away to delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetRow(index: Int, set: WorkoutSet, previous: WorkoutSet?, format: LiftFormat, actions: WorkoutActions) {
    val colors = MaterialTheme.colorScheme
    val number = index + 1
    // Typed text is kept here, not re-read from the database, so the cursor never jumps while typing.
    var weight by remember(set.id) { mutableStateOf(set.weight?.let(format::number).orEmpty()) }
    var reps by remember(set.id) { mutableStateOf(set.reps?.toString().orEmpty()) }
    val save = { done: Boolean -> actions.updateSet(set.copy(weight = format.parseWeight(weight), reps = reps.toIntOrNull(), done = done)) }

    val dismiss = rememberSwipeToDismissBoxState()
    // A light wash of the accent, opaque so the delete background stays hidden until swiped.
    val done = colors.secondaryContainer.copy(alpha = 0.35f).compositeOver(colors.surface)
    val background by animateColorAsState(if (set.done) done else colors.surface, label = "setBackground")
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
                .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s1),
            horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$number", Modifier.width(SetColumn), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            Text(
                previous?.let(format::setShort) ?: "–",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            NumberField(
                value = weight,
                onValueChange = {
                    if (it.length <= 6 && it.all { c -> c.isDigit() || c == '.' || c == ',' } && it.count { c -> c == '.' || c == ',' } <= 1) {
                        weight = it
                        save(set.done)
                    }
                },
                description = stringResource(R.string.workout_set_weight, number),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.width(WeightColumn),
            )
            NumberField(
                value = reps,
                onValueChange = {
                    if (it.length <= 3 && it.all(Char::isDigit)) {
                        reps = it
                        save(set.done)
                    }
                },
                description = stringResource(R.string.workout_set_reps, number),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.width(RepsColumn),
            )
            Box(Modifier.width(CheckColumn), contentAlignment = Alignment.Center) {
                RangeCheckbox(
                    checked = set.done,
                    onCheckedChange = { save(it) },
                    contentDescription = stringResource(R.string.workout_set_done, number),
                )
            }
        }
    }
}

/** A compact Material text field for a number in a set row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, description: String, keyboardType: KeyboardType, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = colors.surfaceContainerHighest,
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedBorderColor = Color.Transparent,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(40.dp)
            .semantics { contentDescription = description },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface, textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        interactionSource = interactions,
        decorationBox = { field ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = field,
                enabled = true,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactions,
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

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismiss) } },
    )
}
