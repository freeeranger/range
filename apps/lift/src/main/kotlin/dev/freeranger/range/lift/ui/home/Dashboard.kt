package dev.freeranger.range.lift.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.WorkoutTemplate
import dev.freeranger.range.lift.data.weekTotals
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.NameSheet
import dev.freeranger.range.lift.ui.common.Stat
import dev.freeranger.range.lift.ui.common.StatsCard
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Instant
import java.time.LocalDate

/** Where the app opens: this week's totals, and the templates to start a workout from in one tap. */
@Composable
fun Dashboard(state: HomeState, now: Instant, format: LiftFormat, actions: HomeActions, contentPadding: PaddingValues) {
    val today = now.atZone(format.zone).toLocalDate()
    val week = remember(state.logs, today, format) { weekTotals(state.logs, today, format.firstDayOfWeek, format.zone) }
    // When each template was last done, from the workouts started from it.
    val lastDone = remember(state.logs) {
        state.history.filter { it.workout.templateId != null }.groupBy { it.workout.templateId!! }.mapValues { (_, logs) -> logs.maxOf { it.workout.startedAt } }
    }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "week") {
            RangeSectionHeader(stringResource(R.string.home_this_week))
            StatsCard {
                Stat(stringResource(R.string.stat_workouts), week.workouts.toString())
                Stat(stringResource(R.string.stat_sets), week.sets.toString())
                Stat(stringResource(R.string.stat_volume), format.volume(week.volume))
            }
        }
        item(key = "templates") {
            RangeSectionHeader(stringResource(R.string.home_templates))
            val hint = when {
                state.templates.isEmpty() -> R.string.home_templates_empty
                state.inProgress != null -> R.string.home_templates_busy
                else -> null
            }
            hint?.let {
                Text(
                    stringResource(it),
                    modifier = Modifier.padding(horizontal = RangeSpace.gutter).padding(bottom = RangeSpace.s2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.templates, key = { it.template.id }) { template ->
            TemplateCard(
                template = template,
                lastDone = lastDone[template.template.id],
                today = today,
                format = format,
                canStart = state.inProgress == null,
                onStart = { actions.startTemplate(template.template.id) },
                onRename = { renaming = template.template.id },
                onDelete = { deleting = template.template.id },
            )
        }
    }

    state.templates.firstOrNull { it.template.id == renaming }?.let { template ->
        NameSheet(
            placeholder = stringResource(R.string.workout_default_name),
            saveLabel = stringResource(R.string.workout_save),
            onSave = { actions.renameTemplate(template.template.id, it) },
            onDismiss = { renaming = null },
            initial = template.template.name,
        )
    }
    state.templates.firstOrNull { it.template.id == deleting }?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(RangeIcons.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.template_delete_title, template.template.name)) },
            text = { Text(stringResource(R.string.template_delete_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        actions.deleteTemplate(template.template.id)
                    },
                ) { Text(stringResource(R.string.workout_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}

/** A template: its name, its exercises, when it was last done, and Start. */
@Composable
private fun TemplateCard(
    template: WorkoutTemplate,
    lastDone: Instant?,
    today: LocalDate,
    format: LiftFormat,
    canStart: Boolean,
    onStart: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s1)) {
        Column(Modifier.padding(start = RangeSpace.s4, top = RangeSpace.s2, end = RangeSpace.s1, bottom = RangeSpace.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    template.template.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.template_delete)) },
                            leadingIcon = { Icon(RangeIcons.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Row(Modifier.padding(end = RangeSpace.s3), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(RangeSpace.s3)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        template.exercises.joinToString(", ") { (exercise, _) -> format.name(exercise) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        listOfNotNull(
                            format.sets(template.exercises.sumOf { it.second }),
                            lastDone?.let { stringResource(R.string.template_last, format.day(it, today)) },
                        ).joinToString(" · "),
                        modifier = Modifier.padding(top = RangeSpace.s1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onStart, enabled = canStart) { Text(stringResource(R.string.template_start)) }
            }
        }
    }
}
