package dev.freeranger.range.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.freeranger.range.ui.icons.RangeIcons
import dev.freeranger.range.ui.theme.RangeSpace
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

/** A small heading above a group of items, in the accent colour as Material's settings lists do. */
@Composable
fun RangeSectionHeader(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = text,
        modifier = modifier
            .padding(horizontal = RangeSpace.gutter)
            .padding(top = RangeSpace.s6, bottom = RangeSpace.s2)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = color,
    )
}

/** A number with minus and plus buttons, for small counts like "every 2 weeks". Material has none. */
@Composable
fun RangeStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
    range: IntRange = 1..99,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onValueChange(value - 1) }, enabled = value > range.first) {
            Icon(RangeIcons.Remove, contentDescription = decreaseDescription)
        }
        Text(
            text = value.toString(),
            modifier = Modifier.widthIn(min = 40.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = { onValueChange(value + 1) }, enabled = value < range.last) {
            Icon(RangeIcons.Add, contentDescription = increaseDescription)
        }
    }
}

/** Material's time picker dialog, following the phone's 12/24-hour setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeTimePickerDialog(
    initial: LocalTime,
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    ) {
        TimePicker(state = state)
    }
}

/**
 * A month calendar whose month and weekday names follow [locale], the app's formatting locale.
 * Days after [last] can't be picked, if it's given.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberRangeDatePickerState(initial: LocalDate, locale: Locale, last: LocalDate? = null): DatePickerState =
    remember(locale, last) {
        DatePickerState(
            locale = locale,
            initialSelectedDateMillis = initial.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = last == null || utcTimeMillis <= last.toUtcMillis()
                override fun isSelectableYear(year: Int) = last == null || year <= last.year
            },
        )
    }

/** The selected day, from Material's UTC-midnight milliseconds. */
@OptIn(ExperimentalMaterial3Api::class)
val DatePickerState.selectedDate: LocalDate?
    get() = selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

/** Material's date picker dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RangeDatePickerDialog(
    initial: LocalDate,
    locale: Locale,
    confirmText: String,
    dismissText: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    last: LocalDate? = null,
) {
    val state = rememberRangeDatePickerState(initial, locale, last)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(state.selectedDate ?: initial) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
