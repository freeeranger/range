package dev.freeranger.range.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * A scrolling row of days: weekday above the date, the selected day filled with the accent,
 * today's date in the accent, and a dot under days that have something on them.
 * [label] gives the short weekday; [describe] the full date read out by screen readers.
 */
@Composable
fun RangeDayStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    label: (LocalDate) -> String,
    describe: (LocalDate) -> String,
    marked: (LocalDate) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    // Bring a day picked elsewhere (the calendar, Back) into view, a few days in from the edge.
    LaunchedEffect(selected) {
        val index = days.indexOf(selected)
        val visible = state.layoutInfo.visibleItemsInfo.map { it.index }
        if (index >= 0 && index !in visible.drop(1).dropLast(1)) state.animateScrollToItem((index - 3).coerceAtLeast(0))
    }

    LazyRow(
        state = state,
        modifier = modifier.selectableGroup(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(days, key = { it.toEpochDay() }) { day ->
            DayCell(
                weekday = label(day),
                number = day.dayOfMonth.toString(),
                description = describe(day),
                selected = day == selected,
                today = day == today,
                marked = marked(day),
                onClick = { onSelect(day) },
            )
        }
    }
}

@Composable
private fun DayCell(
    weekday: String,
    number: String,
    description: String,
    selected: Boolean,
    today: Boolean,
    marked: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (selected) colors.primary else Color.Transparent, label = "dayContainer")
    val content by animateColorAsState(
        when {
            selected -> colors.onPrimary
            today -> colors.primary
            else -> colors.onSurface
        },
        label = "dayContent",
    )
    Column(
        modifier = Modifier
            .width(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(weekday, style = MaterialTheme.typography.labelMedium, color = if (selected) content else colors.onSurfaceVariant)
        Text(
            number,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (today || selected) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
        )
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (marked) content.copy(alpha = if (selected) 1f else 0.6f) else Color.Transparent),
        )
    }
}
