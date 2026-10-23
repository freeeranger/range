package dev.freeranger.range.lift.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.freeranger.range.lift.R
import dev.freeranger.range.lift.data.Exercise
import dev.freeranger.range.lift.data.WorkoutSet
import dev.freeranger.range.lift.ui.common.rememberLiftFormat
import dev.freeranger.range.ui.components.RangeSectionHeader
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace

/** The exercise library, searchable and grouped by muscle, each with its best set so far. Removed exercises are left out. */
@Composable
fun ExerciseList(
    exercises: List<Exercise>,
    bests: Map<Long, WorkoutSet>,
    onClick: (Exercise) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val format = rememberLiftFormat()
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(exercises, query, format) {
        exercises
            .filterNot { it.archived }
            .map { it to format.name(it) }
            .filter { (_, name) -> name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(compareBy<Pair<Exercise, String>> { it.first.muscle.ordinal }.thenBy(format.byName) { it.second })
            .groupBy { it.first.muscle }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RangeSpace.gutter, vertical = RangeSpace.s2),
                placeholder = { Text(stringResource(R.string.exercises_search)) },
                leadingIcon = { Icon(RangeIcons.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
            )
        }
        if (groups.isEmpty() && query.isNotBlank()) {
            item(key = "none") {
                Text(
                    stringResource(R.string.exercises_none_found, query.trim()),
                    modifier = Modifier.padding(RangeSpace.gutter),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        groups.forEach { (muscle, entries) ->
            item(key = "muscle $muscle") { RangeSectionHeader(format.muscle(muscle)) }
            items(entries, key = { it.first.id }) { (exercise, name) ->
                val best = bests[exercise.id]?.let { stringResource(R.string.exercise_best, format.set(it, exercise)) }
                val supporting = listOfNotNull(format.kind(exercise), best).joinToString(" · ")
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = supporting.takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                    modifier = Modifier.clickable { onClick(exercise) },
                )
            }
        }
    }
}
