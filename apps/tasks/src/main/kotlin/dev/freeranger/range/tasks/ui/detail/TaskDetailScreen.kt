package dev.freeranger.range.tasks.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.tasks.LocalAppContainer
import dev.freeranger.range.tasks.R
import dev.freeranger.range.tasks.data.Schedule
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.ui.common.rememberNotificationPermissionRequest
import dev.freeranger.range.tasks.ui.common.rememberNow
import dev.freeranger.range.tasks.ui.common.rememberTaskFormat
import dev.freeranger.range.tasks.ui.schedule.ScheduleSheet
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun TaskDetailRoute(id: Long, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel(key = "task-$id") { TaskDetailViewModel(id, container.repository, container.scope) }
    val task by viewModel.task.collectAsStateWithLifecycle()

    TaskDetailScreen(
        task = task ?: return,
        now = rememberNow(),
        actions = TaskDetailActions(
            back = onBack,
            save = viewModel::save,
            check = { viewModel.check(onDayDone = onBack) },
            uncheck = viewModel::uncheck,
            reopen = viewModel::reopen,
            delete = {
                viewModel.delete()
                onBack()
            },
        ),
    )
}

/** Everything the task page can ask for. */
data class TaskDetailActions(
    val back: () -> Unit = {},
    val save: (title: String, details: String, schedule: Schedule?, scheduleChanged: Boolean) -> Unit = { _, _, _, _ -> },
    val check: () -> Unit = {},
    val uncheck: () -> Unit = {},
    val reopen: () -> Unit = {},
    val delete: () -> Unit = {},
)

/** Shows a task; the Edit button swaps in the editor until Save or Cancel. */
@Composable
fun TaskDetailScreen(
    task: Task,
    now: LocalDateTime,
    actions: TaskDetailActions,
    modifier: Modifier = Modifier,
    initiallyEditing: Boolean = false,
) {
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    Crossfade(targetState = editing, modifier = modifier, label = "editing") { isEditing ->
        if (isEditing) {
            TaskEditor(
                task = task,
                today = now.toLocalDate(),
                onCancel = { editing = false },
                onSave = { title, details, schedule, changed ->
                    actions.save(title, details, schedule, changed)
                    editing = false
                },
            )
        } else {
            TaskView(task, now, actions, onEdit = { editing = true })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskView(task: Task, now: LocalDateTime, actions: TaskDetailActions, onEdit: () -> Unit) {
    val format = rememberTaskFormat()
    val today = now.toLocalDate()
    val done = task.isDone(today)
    val canCheck = task.canCheck(today)
    var confirmingDelete by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions.back) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = onEdit) { Icon(RangeIcons.Edit, contentDescription = stringResource(R.string.detail_edit)) }
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(RangeIcons.Delete, contentDescription = stringResource(R.string.detail_delete))
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(RangeSpace.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RangeSpace.s4),
            ) {
                val lockedUntil = task.dueDate?.takeIf { !done && !canCheck }
                if (lockedUntil != null) {
                    Text(
                        stringResource(R.string.detail_locked, format.day(lockedUntil, today)),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (done) {
                    OutlinedButton(onClick = actions.reopen) { Text(stringResource(R.string.detail_mark_not_done)) }
                } else {
                    Button(onClick = actions.check, enabled = canCheck, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                        Icon(RangeIcons.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.detail_mark_done))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                task.title,
                modifier = Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
                style = MaterialTheme.typography.headlineMedium,
                color = if (done) colors.onSurfaceVariant else colors.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            if (task.details.isNotBlank()) {
                Text(
                    task.details,
                    modifier = Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(RangeSpace.s4))
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(RangeIcons.CalendarToday, contentDescription = null) },
                headlineContent = {
                    Text(
                        format.due(task, today) ?: stringResource(R.string.detail_no_date),
                        color = when {
                            task.dueDate == null -> colors.onSurfaceVariant
                            task.isOverdue(now) -> colors.error
                            else -> colors.onSurface
                        },
                    )
                },
                supportingContent = if (task.dueDate == null && !done) {
                    { Text(stringResource(R.string.detail_anytime_hint)) }
                } else {
                    null
                },
            )
            if (task.steps > 1 && !done) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(RangeIcons.CheckCircle, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.detail_progress, task.doneCount, task.steps)) },
                    trailingContent = if (task.doneCount > 0) {
                        { TextButton(onClick = actions.uncheck) { Text(stringResource(R.string.snackbar_undo)) } }
                    } else {
                        null
                    },
                )
            }
            task.repeat?.let { repeat ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(RangeIcons.Repeat, contentDescription = null) },
                    headlineContent = { Text(format.repeat(repeat, today)) },
                )
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            icon = { Icon(RangeIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_text, task.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        actions.delete()
                    },
                ) { Text(stringResource(R.string.detail_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditor(
    task: Task,
    today: LocalDate,
    onCancel: () -> Unit,
    onSave: (title: String, details: String, schedule: Schedule?, scheduleChanged: Boolean) -> Unit,
) {
    val format = rememberTaskFormat()
    val title = rememberTextFieldState(task.title)
    val details = rememberTextFieldState(task.details)
    var schedule by remember { mutableStateOf(task.schedule) }
    var scheduleChanged by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf(false) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    val requestNotifications = rememberNotificationPermissionRequest()

    val changed = title.text.toString() != task.title || details.text.toString() != task.details || scheduleChanged
    val cancel = { if (changed) confirmingDiscard = true else onCancel() }
    BackHandler(onBack = cancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = cancel) { Icon(RangeIcons.Close, contentDescription = stringResource(R.string.dialog_cancel)) }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(title.text.toString(), details.text.toString(), schedule, scheduleChanged) },
                        enabled = title.text.isNotBlank(),
                    ) { Text(stringResource(R.string.detail_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
                verticalArrangement = Arrangement.spacedBy(RangeSpace.s4),
            ) {
                OutlinedTextField(
                    state = title,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.detail_title)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    state = details,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.detail_details_label)) },
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            val current = schedule
            ListItem(
                modifier = Modifier.clickable { editingSchedule = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(RangeIcons.CalendarToday, contentDescription = null) },
                headlineContent = { Text(current?.let { format.schedule(it, today) } ?: stringResource(R.string.schedule_add)) },
                supportingContent = current?.repeat?.let { repeat -> { Text(format.repeat(repeat, today)) } },
                trailingContent = current?.let {
                    {
                        IconButton(
                            onClick = {
                                schedule = null
                                scheduleChanged = true
                            },
                        ) { Icon(RangeIcons.Close, contentDescription = stringResource(R.string.schedule_remove)) }
                    }
                },
            )
        }
    }

    if (editingSchedule) {
        ScheduleSheet(
            initial = schedule,
            today = today,
            onDone = {
                schedule = it
                scheduleChanged = true
                requestNotifications()
            },
            onDismiss = { editingSchedule = false },
            onClear = if (schedule != null) {
                {
                    schedule = null
                    scheduleChanged = true
                }
            } else {
                null
            },
        )
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.detail_discard_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDiscard = false
                        onCancel()
                    },
                ) { Text(stringResource(R.string.detail_discard)) }
            },
            dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text(stringResource(R.string.detail_keep_editing)) } },
        )
    }
}
