package dev.freeranger.range.tasks.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.freeranger.range.tasks.R
import dev.freeranger.range.tasks.data.Schedule
import dev.freeranger.range.tasks.ui.common.rememberNotificationPermissionRequest
import dev.freeranger.range.tasks.ui.common.rememberTaskFormat
import dev.freeranger.range.tasks.ui.schedule.ScheduleSheet
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The quick-add sheet behind the + button: a title, optional details, an optional schedule. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaskSheet(
    today: LocalDate,
    /** Pre-fills the date, for tasks added while looking at a future day. */
    initialDate: LocalDate? = null,
    onSave: (title: String, details: String, schedule: Schedule?) -> Unit,
    onDismiss: () -> Unit,
    autoFocus: Boolean = true,
) {
    val format = rememberTaskFormat()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val title = rememberTextFieldState()
    val details = rememberTextFieldState()
    var schedule by remember { mutableStateOf(initialDate?.let { Schedule(it) }) }
    var editingSchedule by remember { mutableStateOf(false) }
    val requestNotifications = rememberNotificationPermissionRequest()
    val focus = remember { FocusRequester() }

    val save = {
        if (title.text.isNotBlank()) {
            onSave(title.text.toString(), details.text.toString(), schedule)
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }
    // Borderless fields, as in Google Tasks' quick-add sheet.
    val bare = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = RangeSpace.s1)
                .padding(bottom = RangeSpace.s4),
        ) {
            TextField(
                state = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                placeholder = { Text(stringResource(R.string.new_task_title)) },
                textStyle = MaterialTheme.typography.titleLarge,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                onKeyboardAction = { save() },
                colors = bare,
            )
            TextField(
                state = details,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.new_task_details)) },
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = bare,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RangeSpace.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2),
            ) {
                val current = schedule
                if (current == null) {
                    IconButton(onClick = { editingSchedule = true }) {
                        Icon(RangeIcons.CalendarToday, contentDescription = stringResource(R.string.schedule_add))
                    }
                } else {
                    InputChip(
                        selected = true,
                        onClick = { editingSchedule = true },
                        label = { Text(format.schedule(current, today), maxLines = 1) },
                        leadingIcon = {
                            Icon(if (current.repeat != null) RangeIcons.Repeat else RangeIcons.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { schedule = null }, modifier = Modifier.size(24.dp)) {
                                Icon(RangeIcons.Close, contentDescription = stringResource(R.string.schedule_remove), modifier = Modifier.size(18.dp))
                            }
                        },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = save, enabled = title.text.isNotBlank()) {
                    Text(stringResource(R.string.new_task_save))
                }
            }
        }
    }

    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }

    if (editingSchedule) {
        ScheduleSheet(
            initial = schedule,
            today = today,
            onDone = {
                schedule = it
                requestNotifications()
            },
            onDismiss = { editingSchedule = false },
        )
    }
}
