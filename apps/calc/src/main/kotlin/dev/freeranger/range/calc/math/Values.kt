package dev.freeranger.range.calc.math

import java.math.BigDecimal
import java.time.LocalDate

/** What a line comes to. */
sealed interface Value

/** A number, with a unit or currency if it has one. [approx] when a currency rate went into it. */
data class Quantity(val number: BigDecimal, val unit: CalcUnit? = null, val approx: Boolean = false) : Value

/** "25 %": added to or taken off what it meets, or a share of it with "of". */
data class Percent(val number: BigDecimal) : Value

data class DateValue(val date: LocalDate) : Value

/** An equation's answer: what [name], its unknown, has to be. */
data class Solution(val name: String, val solutions: Solutions) : Value

/** Something that can't be worked out, like dividing by zero or adding metres to kilograms. */
class CalcException(message: String) : Exception(message)
