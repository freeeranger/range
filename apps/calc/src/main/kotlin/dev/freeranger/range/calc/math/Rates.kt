package dev.freeranger.range.calc.math

import java.math.BigDecimal
import java.time.LocalDate

/** Currency rates from the European Central Bank: how many of each currency one euro buys, on [date]. */
data class Rates(val date: LocalDate?, val perEuro: Map<String, BigDecimal>) {
    fun has(code: String) = code == "EUR" || code in perEuro

    /** [amount] of [from] in [to], or null without a rate for either. */
    fun convert(amount: BigDecimal, from: String, to: String): BigDecimal? {
        if (from == to) return amount
        val fromRate = if (from == "EUR") BigDecimal.ONE else perEuro[from] ?: return null
        val toRate = if (to == "EUR") BigDecimal.ONE else perEuro[to] ?: return null
        return amount.divide(fromRate, MATH).multiply(toRate, MATH)
    }

    companion object {
        val NONE = Rates(null, emptyMap())

        /** Reads the ECB's daily reference rates (eurofxref-daily.xml), or null if it isn't that. */
        fun parseEcb(xml: String): Rates? {
            val date = Regex("""time=['"](\d{4}-\d{2}-\d{2})['"]""").find(xml)?.groupValues?.get(1)?.let(LocalDate::parse)
            val rates = Regex("""currency=['"]([A-Z]{3})['"]\s+rate=['"]([0-9.]+)['"]""").findAll(xml)
                .associate { it.groupValues[1] to BigDecimal(it.groupValues[2]) }
            return if (date == null || rates.isEmpty()) null else Rates(date, rates)
        }
    }
}
