package dev.freeranger.range.tasks.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.tasks.LocalAppContainer
import dev.freeranger.range.tasks.R
import dev.freeranger.range.tasks.data.Schedule
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.ui.common.TaskFormat
import dev.freeranger.range.tasks.ui.common.rememberNow
import dev.freeranger.range.tasks.ui.common.rememberTaskFormat
import dev.freeranger.range.ui.components.RangeCheckbox
import dev.freeranger.range.ui.components.RangeDatePickerDialog
import dev.freeranger.range.ui.components.RangeDayStrip
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.LocalDate
import java.time.LocalDateTime

/** How far ahead the day strip reaches; the calendar button goes further. */
private const val STRIP_DAYS = 90L

@Composable
fun TaskListRoute(onOpenTask: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { TaskListViewModel(container.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources

    // Every tick gets an Undo, as in Google Tasks: the one way to take back any tick.
    LaunchedEffect(viewModel) {
        viewModel.ticked.collect { (before, after) ->
            val message = if (after.doneCount > before.doneCount && after.doneCount < after.steps) {
                resources.getString(R.string.snackbar_progress, after.title, after.doneCount, after.steps)
            } else {
                resources.getString(R.string.snackbar_done, after.title)
            }
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = resources.getString(R.string.snackbar_undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(before)
        }
    }

    TaskListScreen(
        state = state,
        now = rememberNow(),
        snackbar = snackbar,
        actions = TaskListActions(
            add = viewModel::add,
            check = viewModel::check,
            reopen = viewModel::reopen,
            open = onOpenTask,
            openSettings = onOpenSettings,
        ),
    )
}

/** Everything the list can ask for, so the screen takes one parameter instead of six. */
data class TaskListActions(
    val add: (title: String, details: String, schedule: Schedule?) -> Unit = { _, _, _ -> },
    val check: (id: Long) -> Unit = {},
    val reopen: (id: Long) -> Unit = {},
    val open: (id: Long) -> Unit = {},
    val openSettings: () -> Unit = {},
)

/**
 * The task list for one day at a time: today by default, with a strip of days to look ahead.
 * Today shows overdue, today's and undated tasks; other days show what falls on them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    state: TaskListState,
    now: LocalDateTime,
    actions: TaskListActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    initiallySelected: LocalDate? = null,
    initiallyAdding: Boolean = false,
    initiallyShowingCompleted: Boolean = false,
) {
    val format = rememberTaskFormat()
    val today = now.toLocalDate()
    // Null means "today", so the list moves on by itself at midnight.
    var picked by rememberSaveable { mutableStateOf(initiallySelected) }
    val day = picked?.takeIf { it != today } ?: today
    var adding by rememberSaveable { mutableStateOf(initiallyAdding) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var showCompleted by rememberSaveable { mutableStateOf(initiallyShowingCompleted) }

    val days = remember(today) { (0 until STRIP_DAYS).map { today.plusDays(it) } }
    val marked = remember(state.tasks, today) { markedDays(state.tasks, today, today.plusDays(STRIP_DAYS)) }
    val view = remember(state.tasks, day, today) { dayView(state.tasks, day, today) }

    BackHandler(enabled = day != today) { picked = null }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_title)) },
                    actions = {
                        IconButton(onClick = { pickingDate = true }) {
                            Icon(RangeIcons.CalendarMonth, contentDescription = stringResource(R.string.action_go_to_date))
                        }
                        IconButton(onClick = actions.openSettings) {
                            Icon(RangeIcons.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    },
                )
                RangeDayStrip(
                    days = days,
                    selected = day,
                    today = today,
                    onSelect = { picked = it },
                    label = { format.weekdayShort(it) },
                    describe = { format.header(it) },
                    marked = { it in marked },
                    modifier = Modifier.padding(bottom = RangeSpace.s2),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // The theme's main accent (wallpaper-based with Material You), not Material's paler default.
            FloatingActionButton(
                onClick = { adding = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(RangeIcons.Add, contentDescription = stringResource(R.string.tasks_add))
            }
        },
    ) { padding ->
        LazyColumn(
            // Leaves room for the + button over the end of the list.
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            if (day != today) {
                item(key = "day-header") {
                    DayHeader(text = format.header(day), onToday = { picked = null }, modifier = Modifier.animateItem())
                }
            }
            if (state.loaded && !view.hasOpenTasks) {
                item(key = "empty") {
                    val message = when {
                        day != today -> R.string.day_empty
                        state.tasks.isEmpty() -> R.string.tasks_empty
                        else -> R.string.day_empty_today
                    }
                    Text(
                        text = stringResource(message),
                        modifier = Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s6),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            section("overdue", R.string.section_overdue, view.overdue, overdue = true) { task, itemModifier ->
                TaskRow(task, done = false, now, format, actions, due = format.due(task, today), modifier = itemModifier)
            }
            section("today", R.string.section_today, view.due.takeIf { day == today }.orEmpty()) { task, itemModifier ->
                TaskRow(task, done = false, now, format, actions, due = timesText(task, format, today), modifier = itemModifier)
            }
            if (day != today) {
                items(view.due, key = { it.id }) { task ->
                    // A repeat on a later day than the one the task is on is a preview: shown, not tickable.
                    val preview = task.dueDate != day
                    TaskRow(task, done = false, now, format, actions, due = timesText(task, format, today), preview = preview, modifier = Modifier.animateItem())
                }
            }
            section("anytime", R.string.section_anytime, view.anytime) { task, itemModifier ->
                TaskRow(task, done = false, now, format, actions, due = null, modifier = itemModifier)
            }
            if (view.completed.isNotEmpty()) {
                item(key = "completed-header") {
                    CompletedHeader(
                        count = view.completed.size,
                        expanded = showCompleted,
                        onToggle = { showCompleted = !showCompleted },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (showCompleted) {
                    items(view.completed, key = { it.id }) { task ->
                        TaskRow(task, done = true, now, format, actions, due = null, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }

    if (adding) {
        NewTaskSheet(
            today = today,
            initialDate = day.takeIf { it != today },
            onSave = actions.add,
            onDismiss = { adding = false },
            autoFocus = !initiallyAdding,
        )
    }

    if (pickingDate) {
        RangeDatePickerDialog(
            initial = day,
            locale = format.locale,
            confirmText = stringResource(R.string.dialog_ok),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                picked = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** A titled group of tasks; left out entirely when empty. */
private fun LazyListScope.section(
    key: String,
    title: Int,
    tasks: List<Task>,
    overdue: Boolean = false,
    row: @Composable (Task, Modifier) -> Unit,
) {
    if (tasks.isEmpty()) return
    item(key = "section-$key") {
        RangeSectionHeader(
            stringResource(title),
            modifier = Modifier.animateItem(),
            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
    items(tasks, key = { it.id }) { task -> row(task, Modifier.animateItem()) }
}

/** A task's times on the day being shown, or its repeat if it has no times. The section or header already names the day. */
private fun timesText(task: Task, format: TaskFormat, today: LocalDate): String? = when {
    task.times.isNotEmpty() -> task.times.joinToString(", ", transform = format::time)
    task.repeat != null -> format.repeat(task.repeat, today)
    else -> null
}

@Composable
private fun DayHeader(text: String, onToday: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = RangeSpace.gutter, end = RangeSpace.s2, top = RangeSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onToday) { Text(stringResource(R.string.date_today)) }
    }
}

/**
 * One task. [done] rows sit under Completed and untick on tap. On open rows the ring ticks off
 * once; a task due several times a day fills a segment per tick and is done after the last.
 * A repeating task on a day that hasn't come yet can't be ticked, and neither can a [preview]
 * of one of its later repeats.
 */
@Composable
private fun TaskRow(
    task: Task,
    done: Boolean,
    now: LocalDateTime,
    format: TaskFormat,
    actions: TaskListActions,
    due: String?,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
) {
    val today = now.toLocalDate()
    val canCheck = !preview && task.canCheck(today)
    val colors = MaterialTheme.colorScheme
    val doneCount = if (preview) 0 else task.doneCount

    ListItem(
        modifier = modifier.clickable { actions.open(task.id) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            RangeCheckbox(
                checked = done,
                onCheckedChange = { if (done) actions.reopen(task.id) else actions.check(task.id) },
                contentDescription = stringResource(if (done) R.string.tasks_reopen else R.string.tasks_complete, task.title),
                enabled = done || canCheck,
                steps = task.steps,
                stepsDone = if (done) task.steps else doneCount,
            )
        },
        headlineContent = {
            Text(
                task.title,
                color = if (done) colors.onSurfaceVariant else colors.onSurface,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = when {
            // A repeating task done for today: say when it's next due.
            done && !task.isCompleted && task.dueDate != null -> {
                {
                    DueLine(
                        stringResource(R.string.tasks_next, format.moment(task.dueDate, task.times.firstOrNull(), today)),
                        RangeIcons.Repeat,
                        colors.onSurfaceVariant,
                    )
                }
            }
            done -> null
            task.details.isBlank() && due == null -> null
            else -> {
                {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (task.details.isNotBlank()) {
                            Text(task.details, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (due != null) {
                            val color = if (!preview && task.isOverdue(now)) colors.error else colors.onSurfaceVariant
                            val icon = when {
                                task.repeat != null -> RangeIcons.Repeat
                                task.times.isNotEmpty() -> RangeIcons.Schedule
                                else -> RangeIcons.CalendarToday
                            }
                            // "1 of 2" once a task done several times a day is under way.
                            val progress = if (task.steps > 1 && doneCount > 0) {
                                " · " + stringResource(R.string.tasks_progress, doneCount, task.steps)
                            } else {
                                ""
                            }
                            DueLine(due + progress, icon, color)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DueLine(text: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val state = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    ListItem(
        modifier = modifier
            .clickable(onClick = onToggle)
            .semantics {
                heading()
                stateDescription = state
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(stringResource(R.string.tasks_completed_section, count), style = MaterialTheme.typography.titleSmall)
        },
        trailingContent = {
            Icon(RangeIcons.ExpandMore, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = rotation })
        },
    )
}
