package dev.freeranger.range.calc.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.freeranger.range.calc.R
import dev.freeranger.range.calc.math.CalcUnit
import dev.freeranger.range.calc.math.Value
import dev.freeranger.range.calc.math.ValueFormat
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Writes answers in the app's language with the phone's region, as the other Range apps do. */
class CalcFormat(context: Context) {
    private val res = context.resources
    private val region: String = res.configuration.locales[0].country

    val locale: Locale = Locale.Builder().setLanguage(res.getString(R.string.format_language)).setRegion(region).build()

    /** What "kr" means here. */
    val localKrona: String = when (region) {
        "NO" -> "NOK"
        "DK" -> "DKK"
        "IS" -> "ISK"
        else -> "SEK"
    }

    private val shortDate = pattern("EEEdMMM")
    private val longDate = pattern("EEEdMMMy")
    private val dayMonth = pattern("dMMM")

    private val value = ValueFormat(
        locale,
        date = { date -> date.format(if (date.year == LocalDate.now().year) shortDate else longDate).replaceFirstChar { it.titlecase(locale) } },
        unitName = { unit, amount -> unitName(unit, amount) },
        words = ValueFormat.SolutionWords(
            or = res.getString(R.string.solve_or),
            none = res.getString(R.string.solve_none),
            any = { res.getString(R.string.solve_any, it) },
        ),
    )

    val decimal: Char get() = value.decimal
    val grouping: Char get() = value.grouping

    fun format(answer: Value): String = value.format(answer)

    /** "23 Oct", for when the currency rates are from. */
    fun day(date: LocalDate): String = date.format(dayMonth)

    private fun unitName(unit: CalcUnit, amount: BigDecimal): String {
        val count = if (amount.abs().compareTo(BigDecimal.ONE) == 0) 1 else 2
        return when (unit.symbol) {
            "d" -> res.getQuantityString(R.plurals.unit_days, count)
            "wk" -> res.getQuantityString(R.plurals.unit_weeks, count)
            "mo" -> res.getQuantityString(R.plurals.unit_months, count)
            "yr" -> res.getQuantityString(R.plurals.unit_years, count)
            else -> unit.symbol
        }
    }

    private fun pattern(skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

/** A [CalcFormat] that refreshes when the phone's language or region changes. */
@Composable
fun rememberCalcFormat(): CalcFormat {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { CalcFormat(context) }
}
