package dev.freeranger.range.calc.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** Precision for every sum: 34 significant digits, so 0,1 + 0,2 is 0,3 and money adds up. */
internal val MATH: MathContext = MathContext.DECIMAL128

/** Degrees or radians, for sin, cos, tan and their inverses. */
enum class AngleMode { Degrees, Radians }

/**
 * Works out one line's [Token]s. Precedence, loosest first: conversion ("in"), + and −, × ÷ and
 * "of"/"off"/"on", signs and √, ^, then ! % and units after a value. Values side by side multiply: 2π.
 */
class Evaluator(
    private val tokens: List<Token>,
    private val rates: Rates,
    private val angles: AngleMode,
    private val lookup: (name: String) -> Value?,
    private val previous: Value?,
    private val sum: () -> Value?,
) {
    private var at = 0

    fun evaluate(): Value {
        val value = conversion()
        if (at < tokens.size) throw CalcException("Unexpected ${tokens[at]}")
        return value
    }

    private fun peek() = tokens.getOrNull(at)
    private fun next() = tokens[at++]
    private fun isOp(c: Char) = (peek() as? Token.Op)?.char == c

    private fun conversion(): Value {
        var value = additive()
        while (peek() == Token.Into) {
            next()
            val unit = when (val target = next()) {
                is Token.Unit -> target.unit
                is Token.Prefix -> target.unit
                else -> throw CalcException("No unit to convert to")
            }
            value = convert(value, unit)
        }
        return value
    }

    private fun additive(): Value {
        var value = multiplicative()
        while (isOp('+') || isOp('-')) {
            val op = (next() as Token.Op).char
            val right = multiplicative()
            value = if (op == '+') add(value, right) else subtract(value, right)
        }
        return value
    }

    private fun multiplicative(): Value {
        var value = unary()
        while (true) {
            value = when {
                isOp('×') -> { next(); multiply(value, unary()) }
                isOp('÷') -> { next(); divide(value, unary()) }
                peek() == Token.Of -> { next(); share(value, unary()) { p, x -> x.multiply(p) } }
                peek() == Token.Off -> { next(); share(value, unary()) { p, x -> x.multiply(BigDecimal.ONE - p) } }
                peek() == Token.On -> { next(); share(value, unary()) { p, x -> x.multiply(BigDecimal.ONE + p) } }
                peek() == Token.Mod -> { next(); remainder(value, unary()) }
                startsValue(peek()) -> {
                    val right = unary()
                    // Amounts side by side that measure the same thing add up: "1 h 30 min", "1 m 80 cm".
                    if (value is Quantity && right is Quantity && value.unit != null && right.unit?.dimension == value.unit.dimension) {
                        add(value, right)
                    } else {
                        multiply(value, right)
                    }
                }
                else -> return value
            }
        }
    }

    private fun startsValue(token: Token?) = token is Token.Num || token is Token.Const || token is Token.Var || token is Token.Func ||
        token is Token.Ans || token is Token.Sum || token is Token.Prefix || (token is Token.Op && (token.char == '(' || token.char == '√'))

    private fun unary(): Value = when {
        isOp('-') -> { next(); negate(unary()) }
        isOp('+') -> { next(); unary() }
        isOp('√') -> { next(); function("sqrt", unary()) }
        else -> power()
    }

    private fun power(): Value {
        val base = postfix()
        if (!isOp('^')) return base
        next()
        val exponent = plain(unary())
        val b = plain(base)
        if (base is Quantity && base.unit != null) throw CalcException("Powers of units")
        return Quantity(pow(b, exponent))
    }

    private fun postfix(): Value {
        var value = primary()
        while (true) {
            value = when (val token = peek()) {
                is Token.Op -> when (token.char) {
                    '!' -> { next(); Quantity(factorial(plain(value))) }
                    '%' -> { next(); Percent(plain(value)) }
                    else -> return value
                }
                is Token.Unit -> {
                    next()
                    if (value is Quantity && value.unit == null) value.copy(unit = token.unit) else return value.also { at-- }
                }
                else -> return value
            }
        }
    }

    private fun primary(): Value = when (val token = next()) {
        is Token.Num -> Quantity(token.value)
        is Token.Const -> Quantity(token.value)
        is Token.Prefix -> {
            val amount = postfix()
            if (amount !is Quantity || amount.unit != null) throw CalcException("Currency sign before a unit")
            amount.copy(unit = token.unit)
        }
        is Token.Var -> lookup(token.name) ?: throw CalcException("Unknown ${token.name}")
        is Token.Ans -> previous ?: throw CalcException("No answer yet")
        is Token.Sum -> sum() ?: throw CalcException("Nothing to add up")
        is Token.Date -> DateValue(token.date)
        is Token.Func -> function(token.name, if (isOp('(')) { next(); conversion().also { close() } } else postfix())
        is Token.Op -> when (token.char) {
            '(' -> conversion().also { close() }
            else -> throw CalcException("Unexpected ${token.char}")
        }
        // A unit on its own is one of it: "m² in cm²", "kr in EUR".
        is Token.Unit -> Quantity(BigDecimal.ONE, token.unit)
        else -> throw CalcException("Unexpected $token")
    }

    /** A missing ")" at the end is fine, as Google's calculator closes them for you. */
    private fun close() {
        if (isOp(')')) next() else if (at < tokens.size) throw CalcException("Missing )")
    }

    // Arithmetic

    private fun add(a: Value, b: Value): Value = when {
        b is Percent && a is Quantity -> a.copy(number = a.number.multiply(BigDecimal.ONE + b.fraction(), MATH))
        a is Percent && b is Percent -> Percent(a.number + b.number)
        a is DateValue && b is Quantity -> shift(a, b, 1)
        a is Quantity && b is DateValue -> shift(b, a, 1)
        a is Quantity && b is Quantity -> combine(a, b) { x, y -> x.add(y, MATH) }
        else -> throw CalcException("Can't add these")
    }

    private fun subtract(a: Value, b: Value): Value = when {
        b is Percent && a is Quantity -> a.copy(number = a.number.multiply(BigDecimal.ONE - b.fraction(), MATH))
        a is Percent && b is Percent -> Percent(a.number - b.number)
        a is DateValue && b is Quantity -> shift(a, b, -1)
        a is DateValue && b is DateValue -> Quantity(BigDecimal(ChronoUnit.DAYS.between(b.date, a.date)), DAY)
        a is Quantity && b is Quantity -> combine(a, b) { x, y -> x.subtract(y, MATH) }
        else -> throw CalcException("Can't subtract these")
    }

    /** Adds or subtracts in [a]'s unit; a plain number takes the other's unit. */
    private fun combine(a: Quantity, b: Quantity, op: (BigDecimal, BigDecimal) -> BigDecimal): Quantity {
        val unit = a.unit ?: b.unit ?: return Quantity(op(a.number, b.number))
        val other = if (b.unit == null || b.unit == unit) b.number else convertNumber(b, unit)
        return Quantity(op(a.number, other), unit, a.approx || b.approx || (b.unit != null && isRateConversion(b.unit, unit)))
    }

    private fun multiply(a: Value, b: Value): Value = when {
        a is Percent && b is Percent -> Percent(a.number.multiply(b.fraction(), MATH))
        b is Percent && a is Quantity -> a.copy(number = a.number.multiply(b.fraction(), MATH))
        a is Percent && b is Quantity -> b.copy(number = b.number.multiply(a.fraction(), MATH))
        a is Quantity && b is Quantity -> when {
            a.unit == null -> b.copy(number = a.number.multiply(b.number, MATH), approx = a.approx || b.approx)
            b.unit == null -> a.copy(number = a.number.multiply(b.number, MATH), approx = a.approx || b.approx)
            else -> derived(a, b, multiply = true)
        }
        else -> throw CalcException("Can't multiply these")
    }

    private fun divide(a: Value, b: Value): Value {
        val x = a as? Quantity ?: (a as? Percent)?.let { Quantity(it.fraction()) } ?: throw CalcException("Can't divide this")
        val y = b as? Quantity ?: (b as? Percent)?.let { Quantity(it.fraction()) } ?: throw CalcException("Can't divide by this")
        if (y.number.signum() == 0) throw CalcException("Division by zero")
        return when {
            y.unit == null -> x.copy(number = x.number.divide(y.number, MATH), approx = x.approx || y.approx)
            x.unit == null -> throw CalcException("Number divided by a unit")
            x.unit.dimension == y.unit.dimension -> Quantity(x.number.divide(convertNumber(y, x.unit), MATH), null, x.approx || y.approx)
            else -> derived(x, y, multiply = false)
        }
    }

    /** Units that make another: m × m = m², 186 km ÷ 80 km/h = 2,33 h, 3 h × 80 km/h = 240 km. */
    private fun derived(a: Quantity, b: Quantity, multiply: Boolean): Quantity {
        val (ua, ub) = a.unit!! to b.unit!!
        val (result, scale) = DERIVED[Triple(ua.dimension, ub.dimension, multiply)] ?: throw CalcException("Can't combine ${ua.symbol} and ${ub.symbol}")
        val base = if (multiply) toBase(a).multiply(toBase(b), MATH) else toBase(a).divide(toBase(b), MATH)
        return Quantity(base.multiply(scale, MATH).divide(result.factor, MATH), result, a.approx || b.approx)
    }

    /** The remainder, never negative for a positive divisor: −7 mod 3 is 2. Units stay with the left side. */
    private fun remainder(a: Value, b: Value): Value {
        val x = a as? Quantity ?: throw CalcException("mod needs numbers")
        val y = (b as? Quantity)?.let { if (it.unit != null && x.unit != null) convertNumber(it, x.unit) else it.number } ?: throw CalcException("mod needs numbers")
        if (y.signum() == 0) throw CalcException("Division by zero")
        var r = x.number.remainder(y, MATH)
        if (r.signum() != 0 && r.signum() != y.signum()) r = r.add(y)
        return x.copy(number = r)
    }

    private fun share(percent: Value, of: Value, op: (BigDecimal, BigDecimal) -> BigDecimal): Value {
        if (percent !is Percent || of !is Quantity) throw CalcException("of/off/on need a percentage first")
        return of.copy(number = op(percent.fraction(), of.number).round(MATH))
    }

    private fun negate(value: Value): Value = when (value) {
        is Quantity -> value.copy(number = value.number.negate())
        is Percent -> Percent(value.number.negate())
        is DateValue, is Solution -> throw CalcException("Can't negate this")
    }

    private fun shift(date: DateValue, by: Quantity, sign: Int): DateValue {
        val unit = by.unit ?: DAY
        if (unit.dimension != Dimension.Time) throw CalcException("Dates move by time")
        val amount = by.number.multiply(BigDecimal(sign))
        val shifted = when (unit.symbol) {
            "mo" -> date.date.plusMonths(amount.wholeOrThrow())
            "yr" -> date.date.plusYears(amount.wholeOrThrow())
            else -> {
                val days = toBase(by.copy(number = amount, unit = unit)).divide(DAY.factor, MATH)
                date.date.plusDays(days.wholeOrThrow())
            }
        }
        return DateValue(shifted)
    }

    private fun convert(value: Value, unit: CalcUnit): Value = when (value) {
        is Quantity -> when {
            value.unit == null -> value.copy(unit = unit)
            value.unit.dimension != unit.dimension -> throw CalcException("Can't turn ${value.unit.symbol} into ${unit.symbol}")
            else -> Quantity(convertNumber(value, unit), unit, value.approx || isRateConversion(value.unit, unit))
        }
        else -> throw CalcException("Only amounts convert")
    }

    private fun isRateConversion(from: CalcUnit, to: CalcUnit) = from.dimension == Dimension.Currency && from.code != to.code

    /** [q]'s number in [unit], which must measure the same thing. */
    private fun convertNumber(q: Quantity, unit: CalcUnit): BigDecimal {
        val from = q.unit!!
        if (from.dimension != unit.dimension) throw CalcException("Can't mix ${from.symbol} and ${unit.symbol}")
        if (from.dimension == Dimension.Currency) {
            return rates.convert(q.number, from.code!!, unit.code!!) ?: throw CalcException("No rate for ${from.code} or ${unit.code}")
        }
        return toBase(q).subtract(unit.offset).divide(unit.factor, MATH)
    }

    private fun toBase(q: Quantity): BigDecimal {
        val unit = q.unit ?: return q.number
        if (unit.dimension == Dimension.Currency) throw CalcException("Currencies don't combine with units")
        return q.number.multiply(unit.factor, MATH).add(unit.offset)
    }

    // Numbers

    private fun plain(value: Value): BigDecimal = when (value) {
        is Quantity -> value.number
        is Percent -> value.fraction()
        is DateValue, is Solution -> throw CalcException("Not a number")
    }

    private fun function(name: String, argument: Value): Value {
        if (argument is Quantity && argument.unit != null && name in setOf("abs", "round", "floor", "ceil")) {
            return argument.copy(number = rounding(name, argument.number))
        }
        val x = plain(argument)
        val radians = { v: Double -> if (angles == AngleMode.Degrees) Math.toRadians(v) else v }
        val fromRadians = { v: Double -> if (angles == AngleMode.Degrees) Math.toDegrees(v) else v }
        val result = when (name) {
            "sin" -> exactTrig(x, { kotlin.math.sin(it) }, radians)
            "cos" -> exactTrig(x, { kotlin.math.cos(it) }, radians)
            "tan" -> {
                if (angles == AngleMode.Degrees && x.remainder(BigDecimal(180)).abs().compareTo(BigDecimal(90)) == 0) throw CalcException("tan(90°)")
                exactTrig(x, { kotlin.math.tan(it) }, radians)
            }
            "asin" -> fromRadians(kotlin.math.asin(x.toDouble())).toBig()
            "acos" -> fromRadians(kotlin.math.acos(x.toDouble())).toBig()
            "atan" -> fromRadians(kotlin.math.atan(x.toDouble())).toBig()
            "ln" -> positive(x).let { kotlin.math.ln(it.toDouble()).toBig() }
            "log" -> positive(x).let { kotlin.math.log10(it.toDouble()).toBig() }
            "log2" -> positive(x).let { (kotlin.math.ln(it.toDouble()) / kotlin.math.ln(2.0)).toBig() }
            "cbrt" -> Math.cbrt(x.toDouble()).toBig()
            "exp" -> kotlin.math.exp(x.toDouble()).toBig()
            "sqrt" -> {
                if (x.signum() < 0) throw CalcException("Square root of a negative number")
                x.sqrt(MATH)
            }
            else -> rounding(name, x)
        }
        return Quantity(result)
    }

    private fun rounding(name: String, x: BigDecimal): BigDecimal = when (name) {
        "abs" -> x.abs()
        "round" -> x.setScale(0, RoundingMode.HALF_UP)
        "floor" -> x.setScale(0, RoundingMode.FLOOR)
        "ceil" -> x.setScale(0, RoundingMode.CEILING)
        else -> throw CalcException("Unknown function $name")
    }

    /** sin(30°) is exactly 0,5, not 0,49999999999999994. */
    private fun exactTrig(x: BigDecimal, f: (Double) -> Double, radians: (Double) -> Double): BigDecimal =
        BigDecimal(f(radians(x.toDouble()))).round(MathContext(15)).stripTrailingZeros()

    private fun positive(x: BigDecimal): BigDecimal = if (x.signum() > 0) x else throw CalcException("Logarithm of zero or less")

    private fun pow(base: BigDecimal, exponent: BigDecimal): BigDecimal {
        val whole = exponent.stripTrailingZeros().let { if (it.scale() <= 0) it.toBigIntegerExact().toInt() else null }
        if (whole != null && whole in -999..999) {
            if (whole < 0 && base.signum() == 0) throw CalcException("Division by zero")
            return if (whole >= 0) base.pow(whole, MATH) else BigDecimal.ONE.divide(base.pow(-whole, MATH), MATH)
        }
        val result = Math.pow(base.toDouble(), exponent.toDouble())
        if (result.isNaN() || result.isInfinite()) throw CalcException("No real answer")
        return result.toBig()
    }

    private fun factorial(x: BigDecimal): BigDecimal {
        val n = x.wholeOrThrow()
        if (n < 0 || n > 1000) throw CalcException("Factorial out of range")
        var result = BigDecimal.ONE
        for (i in 2..n) result = result.multiply(BigDecimal(i))
        return result
    }

    private fun Double.toBig(): BigDecimal {
        if (isNaN() || isInfinite()) throw CalcException("No real answer")
        return BigDecimal(this).round(MathContext(15)).stripTrailingZeros()
    }

    private fun BigDecimal.wholeOrThrow(): Long {
        val rounded = setScale(0, RoundingMode.HALF_UP)
        if (subtract(rounded).abs() > BigDecimal("1E-9")) throw CalcException("Needs a whole number")
        return rounded.longValueExact()
    }

    companion object {
        val DAY = CalcUnit("d", Dimension.Time, BigDecimal(86400))
        private val SQUARE_METRE = CalcUnit("m²", Dimension.Area)
        private val LITRE = CalcUnit("l", Dimension.Volume)
        private val HOUR = CalcUnit("h", Dimension.Time, BigDecimal(3600))
        private val METRE = CalcUnit("m", Dimension.Length)
        private val KMH = CalcUnit("km/h", Dimension.Speed, BigDecimal("0.2777777777777777777777777777777778"))
        private val KM = CalcUnit("km", Dimension.Length, BigDecimal(1000))

        /**
         * What two units make: (left, right, multiplying) → the unit to show it in, and what the
         * product of their base units is in the result's base (m³ is 1 000 l).
         */
        private val DERIVED = mapOf(
            Triple(Dimension.Length, Dimension.Length, true) to (SQUARE_METRE to BigDecimal.ONE),
            Triple(Dimension.Area, Dimension.Length, true) to (LITRE to BigDecimal(1000)),
            Triple(Dimension.Length, Dimension.Area, true) to (LITRE to BigDecimal(1000)),
            Triple(Dimension.Speed, Dimension.Time, true) to (KM to BigDecimal.ONE),
            Triple(Dimension.Time, Dimension.Speed, true) to (KM to BigDecimal.ONE),
            Triple(Dimension.Length, Dimension.Time, false) to (KMH to BigDecimal.ONE),
            Triple(Dimension.Length, Dimension.Speed, false) to (HOUR to BigDecimal.ONE),
            Triple(Dimension.Area, Dimension.Length, false) to (METRE to BigDecimal.ONE),
            Triple(Dimension.Volume, Dimension.Area, false) to (METRE to BigDecimal("0.001")),
            Triple(Dimension.Volume, Dimension.Length, false) to (SQUARE_METRE to BigDecimal("0.001")),
        )
    }
}

private fun Percent.fraction(): BigDecimal = number.divide(BigDecimal(100), MATH)
