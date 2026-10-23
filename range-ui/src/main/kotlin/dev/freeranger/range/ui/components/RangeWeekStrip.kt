package dev.freeranger.range.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * A week of days, swiped a week at a time: weekday above the date, the selected day filled with
 * the accent, today's date in the accent, and a dot under days that have something on them.
 * Swiping to another week selects the same weekday there. [label] gives the short weekday;
 * [describe] the full date read out by screen readers.
 */
@Composable
fun RangeWeekStrip(
    selected: LocalDate,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    onSelect: (LocalDate) -> Unit,
    label: (LocalDate) -> String,
    describe: (LocalDate) -> String,
    marked: (LocalDate) -> Boolean,
    modifier: Modifier = Modifier,
) {
    fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val origin = RangeDays.first.weekStart()
    fun pageOf(day: LocalDate) = ChronoUnit.WEEKS.between(origin, day.coerceIn(RangeDays.first, RangeDays.last).weekStart()).toInt()

    val state = rememberPagerState(initialPage = pageOf(selected)) { pageOf(RangeDays.last) + 1 }
    FollowSelection(state, pageOf(selected)) { page ->
        val weekday = ChronoUnit.DAYS.between(selected.weekStart(), selected)
        onSelect(origin.plusWeeks(page.toLong()).plusDays(weekday).coerceIn(RangeDays.first, RangeDays.last))
    }

    HorizontalPager(state, modifier, key = { it }) { page ->
        val start = origin.plusWeeks(page.toLong())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (offset in 0L until 7L) {
                val day = start.plusDays(offset)
                DayCell(
                    weekday = label(day),
                    number = day.dayOfMonth.toString(),
                    description = describe(day),
                    selected = day == selected,
                    today = day == today,
                    marked = marked(day),
                    onClick = { onSelect(day) },
                    modifier = Modifier.weight(1f),
                )
            }
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
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(weekday, style = MaterialTheme.typography.labelMedium, color = if (selected) content else colors.onSurfaceVariant, maxLines = 1)
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
