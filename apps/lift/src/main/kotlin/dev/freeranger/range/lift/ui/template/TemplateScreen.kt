package dev.freeranger.range.lift.ui.template

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.data.PlannedExercise
import dev.freeranger.range.lift.data.TemplateExercise
import dev.freeranger.range.lift.ui.common.DragHandle
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.NameSheet
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.lift.ui.common.rememberNow
import dev.freeranger.range.ui.components.RangeStepper
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Instant
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun TemplateRoute(
    id: Long,
    onBack: () -> Unit,
    onStarted: (workoutId: Long) -> Unit,
    onAddExercise: () -> Unit,
    onSwapExercise: (entryId: Long) -> Unit,
    onOpenExercise: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { TemplateViewModel(id, container.repository, container.scope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TemplateScreen(
        state = state,
        now = rememberNow(),
        actions = TemplateActions(
            back = onBack,
            rename = viewModel::rename,
            delete = {
                viewModel.delete()
                onBack()
            },
            start = { viewModel.start(onStarted) },
            addExercise = onAddExercise,
            swapExercise = onSwapExercise,
            openExercise = onOpenExercise,
            setSets = viewModel::setSets,
            remove = viewModel::remove,
            restore = viewModel::restore,
            reorder = viewModel::reorder,
        ),
    )
}

/** Everything the template screen can ask for. */
data class TemplateActions(
    val back: () -> Unit = {},
    val rename: (String) -> Unit = {},
    val delete: () -> Unit = {},
    val start: () -> Unit = {},
    val addExercise: () -> Unit = {},
    /** Picks another exercise to take the place of the template's exercise [entryId]. */
    val swapExercise: (entryId: Long) -> Unit = {},
    val openExercise: (exerciseId: Long) -> Unit = {},
    val setSets: (entryId: Long, sets: Int) -> Unit = { _, _ -> },
    val remove: (TemplateExercise) -> Unit = {},
    /** Puts back an exercise just removed. */
    val restore: (TemplateExercise) -> Unit = {},
    val reorder: (entryIds: List<Long>) -> Unit = {},
)

enum class TemplateDialog { Rename, Delete }

/**
 * A template at a glance, and the place to change it: its exercises in order, each with its number
 * of sets to step up or down, dragged by its handle to move, and a menu to replace or remove it.
 * Add exercise puts more on the end. Start begins a workout from it, unless one is in progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(state: TemplateState, now: Instant, actions: TemplateActions, initialDialog: TemplateDialog? = null) {
    val format = rememberLiftFormat()
    val workoutTemplate = state.template ?: return
    val template = workoutTemplate.template
    val today = now.atZone(format.zone).toLocalDate()
    var dialog by rememberSaveable { mutableStateOf(initialDialog) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val removed = stringResource(R.string.workout_exercise_removed)
    val undo = stringResource(R.string.action_undo)
    val editing = actions.copy(
        remove = { entry ->
            actions.remove(entry)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                if (snackbar.showSnackbar(removed, undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) actions.restore(entry)
            }
        },
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(template.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            summary(workoutTemplate, state.lastDone, today, format),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.back) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
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
                                    dialog = TemplateDialog.Rename
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.template_delete)) },
                                leadingIcon = { Icon(RangeIcons.Delete, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    dialog = TemplateDialog.Delete
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface {
                Button(
                    onClick = actions.start,
                    enabled = workoutTemplate.exercises.isNotEmpty() && !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s3),
                ) {
                    Icon(RangeIcons.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.workout_start), modifier = Modifier.padding(start = RangeSpace.s2))
                }
            }
        },
    ) { padding ->
        PlannedExercises(
            workoutTemplate.exercises,
            format,
            editing,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + RangeSpace.s2, bottom = padding.calculateBottomPadding() + RangeSpace.s4),
        )
    }

    when (dialog) {
        TemplateDialog.Rename -> NameSheet(
            placeholder = stringResource(R.string.template_new),
            saveLabel = stringResource(R.string.workout_save),
            onSave = actions.rename,
            onDismiss = { dialog = null },
            initial = template.name,
            autoFocus = initialDialog != TemplateDialog.Rename,
        )
        TemplateDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(RangeIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.template_delete_title, template.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialog = null
                        actions.delete()
                    },
                ) { Text(stringResource(R.string.workout_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
        null -> {}
    }
}

@Composable
private fun PlannedExercises(
    exercises: List<PlannedExercise>,
    format: LiftFormat,
    actions: TemplateActions,
    contentPadding: PaddingValues,
) {
    val haptics = LocalHapticFeedback.current
    val byEntry = exercises.associateBy { it.entry.id }
    // The order on screen, moved as rows are dragged and saved when one is dropped.
    val saved = exercises.map { it.entry.id }
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

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        items(order.mapNotNull(byEntry::get), key = { it.entry.id }) { planned ->
            ReorderableItem(reorderState, key = planned.entry.id) { dragging ->
                val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, label = "rowElevation")
                val handle = Modifier.draggableHandle(
                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                    onDragStopped = { actions.reorder(order) },
                )
                Surface(shadowElevation = elevation, tonalElevation = elevation) {
                    PlannedRow(planned, format, actions) { DragHandle(handle) }
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

/** An exercise in the template: its name (opening its page), its sets, and a menu to replace or remove it. */
@Composable
private fun PlannedRow(
    planned: PlannedExercise,
    format: LiftFormat,
    actions: TemplateActions,
    handle: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(end = RangeSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        handle()
        Text(
            format.name(planned.exercise),
            modifier = Modifier
                .weight(1f)
                .clickable { actions.openExercise(planned.exercise.id) }
                .padding(vertical = RangeSpace.s3),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        RangeStepper(
            value = planned.sets,
            onValueChange = { actions.setSets(planned.entry.id, it) },
            decreaseDescription = stringResource(R.string.template_sets_fewer),
            increaseDescription = stringResource(R.string.template_sets_more),
            range = 1..LiftRepository.MAX_SETS,
        )
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
                        actions.swapExercise(planned.entry.id)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.workout_remove_exercise)) },
                    leadingIcon = { Icon(RangeIcons.Delete, contentDescription = null) },
                    onClick = {
                        menu = false
                        actions.remove(planned.entry)
                    },
                )
            }
        }
    }
}
