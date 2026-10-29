package dev.freeranger.range.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * A month at a glance, a row per week under the weekdays: [marked] days filled with the accent's
 * container, today's date in the accent, days after [last] faded and out of reach. Tapping a day
 * hands it to [onSelect]. [label] gives the short weekday; [describe] the full date read out by
 * screen readers, with [markedDescription] added for marked days.
 */
@Composable
fun RangeMonthGrid(
    month: LocalDate,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    onSelect: (LocalDate) -> Unit,
    label: (LocalDate) -> String,
    describe: (LocalDate) -> String,
    marked: (LocalDate) -> Boolean,
    markedDescription: String,
    modifier: Modifier = Modifier,
    last: LocalDate = RangeDays.last,
) {
    val first = month.withDayOfMonth(1)
    val start = first.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val end = first.plusMonths(1)
    val colors = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            for (offset in 0L until 7L) {
                Text(
                    label(start.plusDays(offset)),
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        var week = start
        while (week < end) {
            Row(Modifier.fillMaxWidth()) {
                for (offset in 0L until 7L) {
                    val day = week.plusDays(offset)
                    Box(Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                        if (day.month == first.month) {
                            val isMarked = marked(day)
                            val enabled = day <= last
                            val description = if (isMarked) "${describe(day)}, $markedDescription" else describe(day)
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isMarked) colors.primaryContainer else Color.Transparent)
                                    .clickable(enabled = enabled, role = Role.Button) { onSelect(day) }
                                    .semantics { contentDescription = description },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = if (day == today || isMarked) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        day == today && !isMarked -> colors.primary
                                        isMarked -> colors.onPrimaryContainer
                                        enabled -> colors.onSurface
                                        else -> colors.onSurface.copy(alpha = 0.38f)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            week = week.plusWeeks(1)
        }
    }
}
