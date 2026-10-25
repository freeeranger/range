package dev.freeranger.range.notes.ui.common

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.freeranger.range.notes.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Turns moments into the short phrases notes show. */
class NoteFormat(context: Context) {
    private val res = context.resources

    /** The app's language with the phone's region, as in the other Range apps. */
    val locale: Locale = Locale.Builder()
        .setLanguage(res.getString(R.string.format_language))
        .setRegion(res.configuration.locales[0].country)
        .build()

    val zone: ZoneId = ZoneId.systemDefault()

    private val timeFormat = pattern(if (DateFormat.is24HourFormat(context)) "Hm" else "hma")
    private val dateFormat = pattern("dMMM")
    private val yearFormat = pattern("dMMMy")

    /** "Edited 10:42" today, "Edited Yesterday", "Edited 23 Oct", or with the year when it isn't this one. */
    fun edited(at: Instant, today: LocalDate): String {
        val time = at.atZone(zone)
        val day = time.toLocalDate()
        val text = when {
            day == today -> time.format(timeFormat)
            day == today.minusDays(1) -> res.getString(R.string.date_yesterday)
            day.year == today.year -> time.format(dateFormat)
            else -> time.format(yearFormat)
        }
        return res.getString(R.string.note_edited, text)
    }

    private fun pattern(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

/** A [NoteFormat] that refreshes when the phone's language, region or clock setting changes. */
@Composable
fun rememberNoteFormat(): NoteFormat {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { NoteFormat(context) }
}
