package dev.freeranger.range.lift.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.LiftRepository
import dev.freeranger.range.lift.data.Record
import dev.freeranger.range.lift.data.WorkoutLog
import dev.freeranger.range.lift.data.WorkoutTemplate
import dev.freeranger.range.lift.data.newRecords
import dev.freeranger.range.lift.data.weekTotals
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.NameSheet
import dev.freeranger.range.lift.ui.common.Stat
import dev.freeranger.range.lift.ui.common.StatsCard
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.lift.ui.common.title
import dev.freeranger.range.ui.components.RangeDatePickerDialog
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.components.RangeTimePickerDialog
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

data class SummaryState(
    val log: WorkoutLog? = null,
    val records: List<Record> = emptyList(),
    /** Every workout, for counting this week's. */
    val logs: List<WorkoutLog> = emptyList(),
    /** The template the workout came from or was saved as, if it still exists. */
    val template: WorkoutTemplate? = null,
)

class SummaryViewModel(
    private val id: Long,
    private val repository: LiftRepository,
    /** Outlives this screen, so notes typed right before leaving are saved. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<SummaryState> = combine(repository.logs, repository.templates) { logs, templates ->
        val log = logs.firstOrNull { it.workout.id == id } ?: return@combine SummaryState()
        SummaryState(
            log = log,
            records = newRecords(log, logs),
            logs = logs,
            template = templates.firstOrNull { it.template.id == log.workout.templateId },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SummaryState())

    fun setNotes(notes: String) {
        appScope.launch { repository.setNotes(id, notes) }
    }

    fun setTimes(startedAt: Instant, finishedAt: Instant) {
        appScope.launch { repository.setTimes(id, startedAt, finishedAt) }
    }

    fun saveTemplate(name: String) {
        appScope.launch { repository.saveTemplate(id, name) }
    }

    fun updateTemplate(templateId: Long) {
        appScope.launch { repository.updateTemplate(templateId, id) }
    }
}

@Composable
fun SummaryRoute(id: Long, onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { SummaryViewModel(id, container.repository, container.scope) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryScreen(
        state = state,
        now = Instant.now(),
        onNotes = viewModel::setNotes,
        onSaveTemplate = viewModel::saveTemplate,
        onUpdateTemplate = viewModel::updateTemplate,
        onDone = onDone,
        onTimes = viewModel::setTimes,
    )
}

/**
 * Right after finishing: how long it took, when it started and ended (to correct, if Finish was
 * tapped late), the records it set, a place for notes, and saving it as a template or updating
 * the one it came from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    state: SummaryState,
    now: Instant,
    onNotes: (String) -> Unit,
    onSaveTemplate: (String) -> Unit,
    onUpdateTemplate: (Long) -> Unit,
    onDone: () -> Unit,
    onTimes: (startedAt: Instant, finishedAt: Instant) -> Unit = { _, _ -> },
    initiallySaving: Boolean = false,
) {
    val format = rememberLiftFormat()
    val log = state.log ?: return
    val workout = log.workout
    val today = now.atZone(format.zone).toLocalDate()
    val notes = rememberTextFieldState(workout.notes)
    var saving by rememberSaveable { mutableStateOf(initiallySaving) }
    var updated by rememberSaveable { mutableStateOf(false) }
    val weekWorkouts = remember(state.logs, today, format) { weekTotals(state.logs, today, format.firstDayOfWeek, format.zone).workouts }

    // However the screen is left, the notes are kept.
    val saveNotes by rememberUpdatedState(onNotes)
    DisposableEffect(Unit) { onDispose { saveNotes(notes.text.toString()) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.summary_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(RangeIcons.Close, contentDescription = stringResource(R.string.summary_done)) }
                },
            )
        },
        bottomBar = {
            Surface {
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s3),
                ) { Text(stringResource(R.string.summary_done)) }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + RangeSpace.s4),
        ) {
            item(key = "hero") {
                Column(Modifier.padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2)) {
                    Text(
                        format.duration(workout.startedAt, workout.finishedAt ?: now),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        format.labelled(format.name(workout), pluralStringResource(R.plurals.summary_week, weekWorkouts, weekWorkouts)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "times") { Times(workout.startedAt, workout.finishedAt ?: now, today, format, onTimes) }
            item(key = "stats") {
                StatsCard {
                    Stat(stringResource(R.string.stat_sets), log.doneSets.size.toString())
                    Stat(stringResource(R.string.stat_volume), format.volume(log.volume))
                }
            }
            if (state.records.isNotEmpty()) {
                item(key = "records header") { RangeSectionHeader(stringResource(R.string.summary_records)) }
                items(state.records, key = { "record ${it.exercise.id} ${it.metric}" }) { record -> RecordRow(record, format) }
            }
            item(key = "notes") {
                RangeSectionHeader(stringResource(R.string.workout_notes))
                OutlinedTextField(
                    state = notes,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter),
                    placeholder = { Text(stringResource(R.string.workout_notes_placeholder)) },
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            item(key = "template") {
                TemplateChoice(state, log, format, updated, onSave = { saving = true }, onUpdate = {
                    onUpdateTemplate(it)
                    updated = true
                })
            }
        }
    }

    if (saving) {
        NameSheet(
            placeholder = stringResource(R.string.workout_default_name),
            saveLabel = stringResource(R.string.template_save),
            onSave = onSaveTemplate,
            onDismiss = { saving = false },
            initial = format.name(workout),
            autoFocus = !initiallySaving,
        )
    }
}

/** Save the workout as a template, or bring the template it came from up to date with it. */
@Composable
private fun TemplateChoice(state: SummaryState, log: WorkoutLog, format: LiftFormat, updated: Boolean, onSave: () -> Unit, onUpdate: (Long) -> Unit) {
    val template = state.template
    Column(
        Modifier.padding(horizontal = RangeSpace.gutter).padding(top = RangeSpace.s6),
        verticalArrangement = Arrangement.spacedBy(RangeSpace.s2),
    ) {
        when {
            template == null -> OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Icon(RangeIcons.BookmarkAdd, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.template_save))
            }
            template.matches(log) -> Done(stringResource(if (updated) R.string.template_updated else R.string.template_saved))
            else -> OutlinedButton(onClick = { onUpdate(template.template.id) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.template_update, template.template.name.ifEmpty { format.name(log.workout) }))
            }
        }
    }
}

@Composable
private fun Done(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(RangeIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, modifier = Modifier.padding(start = RangeSpace.s2), style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Bench press · Estimated 1RM, was 98 kg" and the new record. */
@Composable
private fun RecordRow(record: Record, format: LiftFormat) {
    val was = record.previous?.let { stringResource(R.string.summary_records_was, format.metric(record.metric, it)) }
    ListItem(
        leadingContent = { Icon(RangeIcons.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
        headlineContent = { Text(format.name(record.exercise)) },
        supportingContent = { Text(listOfNotNull(stringResource(record.metric.title), was).joinToString(", ")) },
        trailingContent = { Text(format.metric(record.metric, record.value), style = MaterialTheme.typography.titleMedium) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

enum class TimeField { StartDate, StartTime, EndDate, EndTime }

/**
 * When the workout started and ended, each day and time a button that opens a picker. Moving one
 * past the other takes the other along, keeping the workout's length.
 */
@Composable
private fun Times(startedAt: Instant, finishedAt: Instant, today: LocalDate, format: LiftFormat, onTimes: (Instant, Instant) -> Unit) {
    var picking by rememberSaveable { mutableStateOf<TimeField?>(null) }
    val length = Duration.between(startedAt, finishedAt)
    val start = startedAt.atZone(format.zone)
    val end = finishedAt.atZone(format.zone)
    fun newStart(at: ZonedDateTime) = at.toInstant().let { onTimes(it, if (it > finishedAt) it + length else finishedAt) }
    fun newEnd(at: ZonedDateTime) = at.toInstant().let { onTimes(if (it < startedAt) it - length else startedAt, it) }

    Column(Modifier.padding(horizontal = RangeSpace.gutter - RangeSpace.s3)) {
        TimeRow(stringResource(R.string.summary_started), format.day(startedAt, today), format.time(startedAt), { picking = TimeField.StartDate }, { picking = TimeField.StartTime })
        TimeRow(stringResource(R.string.summary_ended), format.day(finishedAt, today), format.time(finishedAt), { picking = TimeField.EndDate }, { picking = TimeField.EndTime })
    }

    val close = { picking = null }
    when (picking) {
        TimeField.StartDate, TimeField.EndDate -> {
            val at = if (picking == TimeField.StartDate) start else end
            RangeDatePickerDialog(
                initial = at.toLocalDate(),
                locale = format.locale,
                confirmText = stringResource(R.string.dialog_ok),
                dismissText = stringResource(R.string.dialog_cancel),
                onConfirm = { date ->
                    val moved = at.with(date)
                    if (picking == TimeField.StartDate) newStart(moved) else newEnd(moved)
                    close()
                },
                onDismiss = close,
                last = today,
            )
        }
        TimeField.StartTime, TimeField.EndTime -> {
            val isStart = picking == TimeField.StartTime
            val at = if (isStart) start else end
            RangeTimePickerDialog(
                initial = at.toLocalTime(),
                title = stringResource(if (isStart) R.string.summary_started else R.string.summary_ended),
                confirmText = stringResource(R.string.dialog_ok),
                dismissText = stringResource(R.string.dialog_cancel),
                onConfirm = { time ->
                    val moved = at.with(time)
                    if (isStart) newStart(moved) else newEnd(moved)
                    close()
                },
                onDismiss = close,
            )
        }
        null -> Unit
    }
}

@Composable
private fun TimeRow(label: String, day: String, time: String, onDay: () -> Unit, onTime: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f).padding(start = RangeSpace.s3),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onDay) { Text(day) }
        TextButton(onClick = onTime) { Text(time, style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")) }
    }
}
