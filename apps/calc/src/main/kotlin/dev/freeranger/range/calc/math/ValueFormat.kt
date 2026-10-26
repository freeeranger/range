package dev.freeranger.range.calc.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.util.Locale

/**
 * Writes answers the way the phone's region writes numbers ("1 946,67 kr" in Sweden). Plain
 * numbers keep up to ten decimals, amounts with units two, money two unless it's whole.
 */
class ValueFormat(
    locale: Locale,
    private val date: (LocalDate) -> String = LocalDate::toString,
    /** A unit's name after a number, for time units in the app's language ("3 veckor"). */
    private val unitName: (CalcUnit, BigDecimal) -> String = { unit, _ -> unit.symbol },
) {
    private val symbols = DecimalFormatSymbols.getInstance(locale)
    val decimal: Char = symbols.decimalSeparator
    val grouping: Char = symbols.groupingSeparator

    fun format(value: Value): String = when (value) {
        is Quantity -> {
            val unit = value.unit
            val number = when {
                unit == null -> number(value.number, 10)
                unit.dimension == Dimension.Currency -> number(value.number, if (value.number.isWholeAt(2)) 0 else 2, minFraction = if (value.number.isWholeAt(2)) 0 else 2)
                value.number.abs() < BigDecimal.ONE -> number(value.number.round(MathContext(3)), 10)
                else -> number(value.number, 2)
            }
            val text = if (unit == null) number else "$number ${unitName(unit, value.number)}"
            if (value.approx) "≈ $text" else text
        }
        is Percent -> "${number(value.number, 10)} %"
        is DateValue -> date(value.date)
    }

    /** A plain number, as typed into the keypad would show it. */
    fun number(x: BigDecimal, maxFraction: Int, minFraction: Int = 0): String {
        val abs = x.abs()
        if (abs.signum() != 0 && (abs >= BigDecimal("1E15") || abs < BigDecimal("1E-9"))) {
            return DecimalFormat("0.#########E0", symbols).format(x)
        }
        val rounded = x.round(MathContext(15)).setScale(maxFraction, RoundingMode.HALF_UP).stripTrailingZeros()
        val format = DecimalFormat("#,##0", symbols).apply {
            minimumFractionDigits = minFraction
            maximumFractionDigits = maxOf(rounded.scale(), minFraction).coerceAtLeast(0)
        }
        return format.format(rounded)
    }

    private fun BigDecimal.isWholeAt(scale: Int) = setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().scale() <= 0
}
