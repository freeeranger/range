package dev.freeranger.range.tasks.ui.schedule

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.freeranger.range.tasks.LocalAllDayReminder
import dev.freeranger.range.tasks.R
import dev.freeranger.range.tasks.data.Repeat
import dev.freeranger.range.tasks.data.RepeatEnd
import dev.freeranger.range.tasks.data.RepeatUnit
import dev.freeranger.range.tasks.data.Schedule
import dev.freeranger.range.tasks.data.Task
import dev.freeranger.range.tasks.ui.common.rememberTaskFormat
import dev.freeranger.range.ui.components.RangeDatePickerDialog
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.components.RangeStepper
import dev.freeranger.range.ui.components.RangeTimePickerDialog
import dev.freeranger.range.ui.components.rememberRangeDatePickerState
import dev.freeranger.range.ui.components.selectedDate
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private sealed interface TimeEdit {
    data object New : TimeEdit
    data class Existing(val time: LocalTime) : TimeEdit
}

/**
 * Picks a date, any number of times on that day, and an optional repeat.
 * [onClear] adds a Clear button that removes the schedule altogether.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSheet(
    initial: Schedule?,
    today: LocalDate,
    onDone: (Schedule) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val format = rememberTaskFormat()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val initialRepeat = initial?.repeat
    val calendar = rememberRangeDatePickerState(initial?.date ?: today, format.locale)

    var times by remember { mutableStateOf(initial?.times.orEmpty()) }
    var repeatOn by remember { mutableStateOf(initialRepeat != null) }
    var every by remember { mutableIntStateOf(initialRepeat?.every ?: 1) }
    var unit by remember { mutableStateOf(initialRepeat?.unit ?: RepeatUnit.Day) }
    var weekdays by remember { mutableStateOf(initialRepeat?.weekdays.orEmpty()) }
    var end by remember { mutableStateOf(initialRepeat?.end ?: RepeatEnd.Never) }
    var endCount by remember { mutableIntStateOf((end as? RepeatEnd.After)?.count ?: 10) }

    var timeEdit by remember { mutableStateOf<TimeEdit?>(null) }
    var pickingEndDate by remember { mutableStateOf(false) }

    val date = calendar.selectedDate ?: today
    var endDate by remember { mutableStateOf((end as? RepeatEnd.OnDate)?.date ?: date.plusMonths(1)) }
    // A weekly repeat with no days picked means the start date's weekday, so show that one as selected.
    val effectiveWeekdays = weekdays.ifEmpty { setOf(date.dayOfWeek.value) }

    fun close(then: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            then()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = RangeSpace.s4),
        ) {
            DatePicker(
                state = calendar,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
            )

            RangeSectionHeader(stringResource(R.string.schedule_reminders))
            FlowRow(
                modifier = Modifier.padding(horizontal = RangeSpace.gutter),
                horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2),
            ) {
                times.forEach { time ->
                    InputChip(
                        selected = false,
                        onClick = { timeEdit = TimeEdit.Existing(time) },
                        label = { Text(format.time(time)) },
                        leadingIcon = { Icon(RangeIcons.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(onClick = { times = times - time }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    RangeIcons.Close,
                                    contentDescription = stringResource(R.string.schedule_remove_time, format.time(time)),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                }
                AssistChip(
                    onClick = { timeEdit = TimeEdit.New },
                    label = { Text(stringResource(if (times.isEmpty()) R.string.schedule_add_reminder else R.string.schedule_add_another_reminder)) },
                    leadingIcon = { Icon(RangeIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            val hint = when {
                times.isEmpty() -> stringResource(R.string.schedule_all_day_hint, format.time(LocalAllDayReminder.current))
                times.size > 1 -> stringResource(R.string.schedule_several_hint, times.size)
                else -> null
            }
            if (hint != null) {
                Text(
                    hint,
                    modifier = Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ListItem(
                modifier = Modifier
                    .padding(top = RangeSpace.s4)
                    .toggleable(value = repeatOn, role = Role.Switch) { repeatOn = it },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(RangeIcons.Repeat, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.schedule_repeat)) },
                trailingContent = { Switch(checked = repeatOn, onCheckedChange = null) },
            )

            AnimatedVisibility(visible = repeatOn) {
                Column(
                    modifier = Modifier.padding(horizontal = RangeSpace.gutter),
                    verticalArrangement = Arrangement.spacedBy(RangeSpace.s4),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RangeSpace.s4)) {
                        Text(stringResource(R.string.schedule_every), style = MaterialTheme.typography.bodyLarge)
                        RangeStepper(
                            value = every,
                            onValueChange = { every = it },
                            decreaseDescription = stringResource(R.string.schedule_every_less),
                            increaseDescription = stringResource(R.string.schedule_every_more),
                        )
                    }
                    val units = listOf(R.plurals.unit_day, R.plurals.unit_week, R.plurals.unit_month, R.plurals.unit_year)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        units.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = unit.ordinal == index,
                                onClick = { unit = RepeatUnit.entries[index] },
                                shape = SegmentedButtonDefaults.itemShape(index, units.size),
                                label = { Text(pluralStringResource(label, every), maxLines = 1) },
                            )
                        }
                    }
                    if (unit == RepeatUnit.Week) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2)) {
                            format.weekdays.forEach { day ->
                                val selected = day.value in effectiveWeekdays
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val toggled = if (selected) effectiveWeekdays - day.value else effectiveWeekdays + day.value
                                        // At least one day must stay picked.
                                        if (toggled.isNotEmpty()) weekdays = toggled
                                    },
                                    label = { Text(format.weekday(day)) },
                                )
                            }
                        }
                    }

                    Text(stringResource(R.string.schedule_ends), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    val ends = listOf(R.string.schedule_ends_never, R.string.schedule_ends_on, R.string.schedule_ends_after)
                    val endIndex = when (end) {
                        RepeatEnd.Never -> 0
                        is RepeatEnd.OnDate -> 1
                        is RepeatEnd.After -> 2
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ends.forEachIndexed { index, label ->
                            SegmentedButton(
                                selected = endIndex == index,
                                onClick = {
                                    end = when (index) {
                                        1 -> RepeatEnd.OnDate(endDate)
                                        2 -> RepeatEnd.After(endCount)
                                        else -> RepeatEnd.Never
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, ends.size),
                                label = { Text(stringResource(label), maxLines = 1) },
                            )
                        }
                    }
                    when (end) {
                        is RepeatEnd.OnDate -> AssistChip(
                            onClick = { pickingEndDate = true },
                            label = { Text(format.day(endDate, today)) },
                            leadingIcon = { Icon(RangeIcons.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                        is RepeatEnd.After -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2),
                        ) {
                            RangeStepper(
                                value = endCount,
                                onValueChange = {
                                    endCount = it
                                    end = RepeatEnd.After(it)
                                },
                                decreaseDescription = stringResource(R.string.schedule_count_less),
                                increaseDescription = stringResource(R.string.schedule_count_more),
                                range = 1..999,
                            )
                            Text(pluralStringResource(R.plurals.unit_times, endCount), style = MaterialTheme.typography.bodyLarge)
                        }
                        RepeatEnd.Never -> Unit
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RangeSpace.gutter)
                    .padding(top = RangeSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onClear != null) {
                    TextButton(onClick = { close(onClear) }) { Text(stringResource(R.string.schedule_clear)) }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        val schedule = Schedule(
                            date = date,
                            times = times,
                            repeat = if (!repeatOn) null else Repeat(
                                every = every,
                                unit = unit,
                                weekdays = if (unit == RepeatUnit.Week) effectiveWeekdays else emptySet(),
                                end = end,
                                anchor = date,
                            ),
                        )
                        close { onDone(schedule) }
                    },
                ) {
                    Text(stringResource(R.string.schedule_done))
                }
            }
        }
    }

    timeEdit?.let { edit ->
        RangeTimePickerDialog(
            initial = when (edit) {
                is TimeEdit.Existing -> edit.time
                TimeEdit.New -> LocalTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS)
            },
            title = stringResource(R.string.schedule_select_time),
            confirmText = stringResource(R.string.dialog_ok),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = { picked ->
                val others = if (edit is TimeEdit.Existing) times - edit.time else times
                times = (others + picked).distinct().sorted()
                timeEdit = null
            },
            onDismiss = { timeEdit = null },
        )
    }

    if (pickingEndDate) {
        RangeDatePickerDialog(
            initial = endDate,
            locale = format.locale,
            confirmText = stringResource(R.string.dialog_ok),
            dismissText = stringResource(R.string.dialog_cancel),
            onConfirm = {
                endDate = it
                end = RepeatEnd.OnDate(it)
                pickingEndDate = false
            },
            onDismiss = { pickingEndDate = false },
        )
    }
}
