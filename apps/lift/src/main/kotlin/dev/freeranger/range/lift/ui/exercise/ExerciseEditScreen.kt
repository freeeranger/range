package dev.freeranger.range.lift.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.Load
import dev.freeranger.range.lift.data.Measure
import dev.freeranger.range.lift.data.Muscle
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace

/**
 * Adds an exercise when [id] is null, straight into the workout [workoutId] if there is one, or
 * edits exercise [id].
 */
@Composable
fun ExerciseEditRoute(id: Long?, workoutId: Long?, onSaved: () -> Unit, onCancel: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { ExerciseEditViewModel(id, workoutId, container.repository, container.scope) }
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    // Wait for the exercise being edited, so the form starts from it.
    if (id != null && exercise == null) return
    ExerciseEditScreen(
        exercise = exercise,
        onSave = {
            viewModel.save(it)
            onSaved()
        },
        onCancel = onCancel,
    )
}

/** What an exercise is called, what it trains, and how its sets are logged. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseEditScreen(
    exercise: Exercise?,
    onSave: (Exercise) -> Unit,
    onCancel: () -> Unit,
    autoFocus: Boolean = exercise == null,
) {
    val format = rememberLiftFormat()
    val builtInName = exercise?.let(format::builtInName)
    val name = rememberTextFieldState(exercise?.let(format::name).orEmpty())
    var muscle by rememberSaveable { mutableStateOf(exercise?.muscle) }
    var measure by rememberSaveable { mutableStateOf(exercise?.measure ?: Measure.Reps) }
    var load by rememberSaveable { mutableStateOf(exercise?.load ?: Load.Weight) }
    var perSide by rememberSaveable { mutableStateOf(exercise?.perSide ?: false) }
    val focus = remember { FocusRequester() }
    val canSave = name.text.isNotBlank() && muscle != null

    val save = {
        val typed = name.text.toString().trim()
        onSave(
            (exercise ?: Exercise(muscle = muscle!!)).copy(
                // A built-in exercise left with its own name keeps following the app's language.
                name = if (typed == builtInName) "" else typed,
                muscle = muscle!!,
                measure = measure,
                load = load,
                perSide = perSide,
            ),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (exercise == null) R.string.exercise_new else R.string.exercise_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(RangeIcons.Close, contentDescription = stringResource(R.string.dialog_cancel)) }
                },
                actions = {
                    TextButton(onClick = save, enabled = canSave) { Text(stringResource(R.string.exercise_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = RangeSpace.s6),
        ) {
            OutlinedTextField(
                state = name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2)
                    .focusRequester(focus),
                label = { Text(stringResource(R.string.exercise_name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            RangeSectionHeader(stringResource(R.string.exercise_muscle))
            FlowRow(
                modifier = Modifier.padding(horizontal = RangeSpace.gutter),
                horizontalArrangement = Arrangement.spacedBy(RangeSpace.s2),
            ) {
                Muscle.entries.forEach {
                    FilterChip(selected = muscle == it, onClick = { muscle = it }, label = { Text(format.muscle(it)) })
                }
            }

            RangeSectionHeader(stringResource(R.string.exercise_measure))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter)) {
                Measure.entries.forEachIndexed { i, it ->
                    SegmentedButton(
                        selected = measure == it,
                        onClick = { measure = it },
                        shape = SegmentedButtonDefaults.itemShape(i, Measure.entries.size),
                        label = { Text(format.measure(it)) },
                    )
                }
            }

            RangeSectionHeader(stringResource(R.string.exercise_load))
            Column(Modifier.selectableGroup()) {
                Load.entries.forEach {
                    ListItem(
                        headlineContent = { Text(format.load(it)) },
                        leadingContent = { RadioButton(selected = load == it, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.selectable(selected = load == it, role = Role.RadioButton) { load = it },
                    )
                }
            }

            RangeSectionHeader(stringResource(R.string.exercise_more))
            ListItem(
                headlineContent = { Text(stringResource(R.string.exercise_each_side)) },
                trailingContent = { Switch(checked = perSide, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.toggleable(value = perSide, role = Role.Switch) { perSide = it },
            )
        }
    }

    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
}
