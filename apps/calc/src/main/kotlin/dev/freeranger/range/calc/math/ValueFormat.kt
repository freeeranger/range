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
    /** The words around an equation's answers: "or", "no solution", "any x". */
    private val words: SolutionWords = SolutionWords(),
) {
    data class SolutionWords(val or: String = "or", val none: String = "no solution", val any: (String) -> String = { "any $it" })

    private val symbols = DecimalFormatSymbols.getInstance(locale)
    val decimal: Char = symbols.decimalSeparator
    val grouping: Char = symbols.groupingSeparator

    /**
     * The answer, as text. With [fractions], plain numbers whose decimals would run on come out as
     * fractions (1/3, not 0,3333333333) and equations' answers exactly where they can be.
     */
    fun format(value: Value, fractions: Boolean = true): String = when (value) {
        is Quantity -> {
            val unit = value.unit
            val number = when {
                unit == null -> if (fractions) fractionOrNumber(value.number) else number(value.number, 10)
                unit.dimension == Dimension.Currency -> number(value.number, if (value.number.isWholeAt(2)) 0 else 2, minFraction = if (value.number.isWholeAt(2)) 0 else 2)
                value.number.abs() < BigDecimal.ONE -> number(value.number.round(MathContext(3)), 10)
                else -> number(value.number, 2)
            }
            val text = if (unit == null) number else "$number ${unitName(unit, value.number)}"
            if (value.approx) "≈ $text" else text
        }
        is Percent -> "${number(value.number, 10)} %"
        is DateValue -> date(value.date)
        is Solution -> when (val s = value.solutions) {
            is Solutions.Roots -> "${value.name} = " + s.values.indices.joinToString(" ${words.or} ") { i ->
                val exact = s.exact.getOrNull(i)
                when {
                    !fractions -> number(s.values[i], 10)
                    // A root stays a root; a fraction follows the same rule as any number.
                    exact != null && exact.surd.num.signum() != 0 -> exact(exact)
                    else -> fractionOrNumber(s.values[i])
                }
            }
            Solutions.None -> words.none
            Solutions.Any -> words.any(value.name)
        }
    }

    /** Whether [format] wrote [value] as a fraction or root, which the decimal can then go with. */
    fun isExact(value: Value): Boolean = format(value) != format(value, fractions = false)

    /** A fraction when the decimals would run on past ten places, else the number: 1/3, 2,5. */
    private fun fractionOrNumber(x: BigDecimal): String {
        if (x.stripTrailingZeros().scale() <= 10) return number(x, 10)
        val fraction = Fraction.near(x) ?: return number(x, 10)
        return fraction(fraction)
    }

    private fun fraction(f: Fraction): String =
        if (f.isWhole) number(BigDecimal(f.num), 0) else "${number(BigDecimal(f.num), 0)}/${number(BigDecimal(f.den), 0)}"

    /** 5/2 − √13/2 as (5 − √13)/2, over one bottom. */
    private fun exact(e: Exact): String {
        if (e.surd.num.signum() == 0) return fraction(e.rational)
        val den = lcm(e.rational.den, e.surd.den)
        val r = e.rational.num * (den / e.rational.den)
        val s = e.surd.num * (den / e.surd.den)
        val minus = symbols.minusSign
        val root = (if (s.abs() == java.math.BigInteger.ONE) "" else number(BigDecimal(s.abs()), 0)) + "√" + number(BigDecimal(e.radicand), 0)
        val top = when {
            r.signum() == 0 -> (if (s.signum() < 0) minus.toString() else "") + root
            else -> number(BigDecimal(r), 0) + (if (s.signum() < 0) " $minus " else " + ") + root
        }
        return when {
            den == java.math.BigInteger.ONE -> top
            r.signum() == 0 -> "$top/${number(BigDecimal(den), 0)}"
            else -> "($top)/${number(BigDecimal(den), 0)}"
        }
    }

    private fun lcm(a: java.math.BigInteger, b: java.math.BigInteger) = a / a.gcd(b) * b

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
