package dev.freeranger.range.lift.ui.template

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.freeranger.range.lift.LocalAppContainer
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.WorkoutTemplate
import dev.freeranger.range.lift.ui.common.LiftFormat
import dev.freeranger.range.lift.ui.common.NameSheet
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.lift.ui.common.rememberNow
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Instant
import java.time.LocalDate

/** [creating] opens straight onto naming a new template. */
@Composable
fun TemplatesRoute(creating: Boolean, onBack: () -> Unit, onOpen: (Long) -> Unit, onCreated: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel = viewModel { TemplatesViewModel(container.repository) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TemplatesScreen(
        state = state,
        now = rememberNow(),
        onBack = onBack,
        onOpen = onOpen,
        onCreate = { name -> viewModel.create(name, onCreated) },
        initiallyCreating = creating,
    )
}

/** Every template, each showing what's in it and when it was last done. Tap one to see it whole, change it or start it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    state: TemplatesState,
    now: Instant,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: (String) -> Unit,
    initiallyCreating: Boolean = false,
    autoFocus: Boolean = true,
) {
    val format = rememberLiftFormat()
    val today = now.atZone(format.zone).toLocalDate()
    var creating by rememberSaveable { mutableStateOf(initiallyCreating) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_templates)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(RangeIcons.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.template_new)) },
                icon = { Icon(RangeIcons.Add, contentDescription = null) },
                onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + RangeSpace.s2, bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            items(state.templates, key = { it.template.id }) { template ->
                TemplateCard(template, state.lastDone[template.template.id], today, format, onClick = { onOpen(template.template.id) })
            }
        }
    }

    if (creating) {
        NameSheet(
            placeholder = stringResource(R.string.template_new),
            saveLabel = stringResource(R.string.workout_save),
            onSave = onCreate,
            onDismiss = { creating = false },
            autoFocus = autoFocus,
        )
    }
}

@Composable
private fun TemplateCard(template: WorkoutTemplate, lastDone: Instant?, today: LocalDate, format: LiftFormat, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s1)) {
        Column(Modifier.padding(RangeSpace.s4)) {
            Text(template.template.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (template.exercises.isNotEmpty()) {
                Text(
                    template.exercises.joinToString(", ") { format.name(it.exercise) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            summary(template, lastDone, today, format).takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = RangeSpace.s1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** "9 sets · Last done Yesterday"; empty for a template with nothing in it yet. */
@Composable
internal fun summary(template: WorkoutTemplate, lastDone: Instant?, today: LocalDate, format: LiftFormat): String =
    listOfNotNull(
        format.sets(template.sets).takeIf { template.sets > 0 },
        lastDone?.let { stringResource(R.string.template_last, format.day(it, today)) },
    ).joinToString(" · ")
