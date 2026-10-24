package dev.freeranger.range.lift.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.WorkoutTemplate
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import kotlinx.coroutines.launch

/**
 * What to start: an empty workout, or a template, tapped to start it as it is. A template's edit
 * button opens it to look over or change first. With no templates yet, it offers making one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSheet(templates: List<WorkoutTemplate>, actions: HomeActions, onDismiss: () -> Unit, expanded: Boolean = false) {
    val format = rememberLiftFormat()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expanded)
    val scope = rememberCoroutineScope()
    // The sheet slides away first, then the next screen opens.
    val then = { action: () -> Unit ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    val rowColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = RangeSpace.s4)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.start_empty)) },
                leadingContent = { Icon(RangeIcons.Add, contentDescription = null) },
                colors = rowColors,
                modifier = Modifier.clickable { then(actions.start) },
            )
            if (templates.isEmpty()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.template_new)) },
                    leadingContent = { Icon(RangeIcons.BookmarkAdd, contentDescription = null) },
                    colors = rowColors,
                    modifier = Modifier.clickable { then { actions.openTemplates(true) } },
                )
            } else {
                RangeSectionHeader(stringResource(R.string.home_templates))
            }
            templates.forEach { template ->
                val id = template.template.id
                ListItem(
                    headlineContent = { Text(template.template.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = template.exercises.takeIf { it.isNotEmpty() }?.let { exercises ->
                        {
                            Text(
                                exercises.joinToString(", ") { format.name(it.exercise) },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingContent = { Icon(RangeIcons.PlayArrow, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { then { actions.openTemplate(id) } }) {
                            Icon(RangeIcons.Edit, contentDescription = stringResource(R.string.template_edit, template.template.name))
                        }
                    },
                    colors = rowColors,
                    // An empty template has nothing to start yet: open it to fill it in.
                    modifier = Modifier.clickable { then { if (template.exercises.isEmpty()) actions.openTemplate(id) else actions.startTemplate(id) } },
                )
            }
        }
    }
}
