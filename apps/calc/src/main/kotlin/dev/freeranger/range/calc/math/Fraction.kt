package dev.freeranger.range.calc.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** A fraction in lowest terms, the sign on the top: 7/12, −1/3. */
data class Fraction(val num: BigInteger, val den: BigInteger) {
    val isWhole: Boolean get() = den == BigInteger.ONE

    fun toBigDecimal(): BigDecimal = BigDecimal(num).divide(BigDecimal(den), MATH)

    operator fun plus(o: Fraction) = of(num * o.den + o.num * den, den * o.den)
    operator fun minus(o: Fraction) = of(num * o.den - o.num * den, den * o.den)
    operator fun times(o: Fraction) = of(num * o.num, den * o.den)
    operator fun div(o: Fraction) = of(num * o.den, den * o.num)
    operator fun unaryMinus() = Fraction(-num, den)

    companion object {
        val ZERO = Fraction(BigInteger.ZERO, BigInteger.ONE)

        fun of(num: BigInteger, den: BigInteger): Fraction {
            require(den.signum() != 0)
            val g = num.gcd(den).takeIf { it.signum() != 0 } ?: BigInteger.ONE
            val sign = den.signum().toBigInteger()
            return Fraction(num / g * sign, (den / g).abs())
        }

        fun of(n: Long) = Fraction(BigInteger.valueOf(n), BigInteger.ONE)

        /**
         * The simplest fraction [x] is, if its bottom is at most [maxDen]: 0,333… is 1/3. Numbers
         * worked out with 34 digits come back to the fraction they started as; √2 and π have none.
         */
        fun near(x: BigDecimal, maxDen: Long = 10_000): Fraction? {
            // A decimal that ends is exactly a fraction, of ten's powers.
            val stripped = x.stripTrailingZeros()
            if (stripped.scale() <= 0) return Fraction(stripped.toBigIntegerExact(), BigInteger.ONE)
            // Continued fractions: the best fractions with small bottoms, one after another.
            var (h0, h1) = BigInteger.ZERO to BigInteger.ONE
            var (k0, k1) = BigInteger.ONE to BigInteger.ZERO
            var rest = x
            val limit = BigInteger.valueOf(maxDen)
            val tolerance = BigDecimal("1E-25").max(x.abs().multiply(BigDecimal("1E-25")))
            repeat(60) {
                val a = rest.setScale(0, java.math.RoundingMode.FLOOR).toBigInteger()
                val h2 = a * h1 + h0
                val k2 = a * k1 + k0
                if (k2 > limit) return null
                val candidate = of(h2, k2)
                if (candidate.toBigDecimal().subtract(x).abs() <= tolerance) return candidate
                val fraction = rest.subtract(BigDecimal(a))
                if (fraction.signum() == 0) return candidate
                rest = BigDecimal.ONE.divide(fraction, MathContext(60))
                h0 = h1; h1 = h2; k0 = k1; k1 = k2
            }
            return null
        }
    }
}

/**
 * A number written exactly: [rational], plus [surd] times √[radicand] when there's a root in it,
 * as a quadratic's solutions often have: 5/2 − √13/2.
 */
data class Exact(val rational: Fraction, val surd: Fraction = Fraction.ZERO, val radicand: BigInteger = BigInteger.ONE) {
    companion object {
        /** √(p/q) as k√m / q·…, with the square factors taken out from under the root. */
        fun root(of: Fraction): Exact {
            // √(p/q) = √(p·q) / q
            val pq = of.num * of.den
            var outside = BigInteger.ONE
            var inside = pq
            var factor = BigInteger.TWO
            while (factor * factor <= inside && factor < BigInteger.valueOf(100_000)) {
                val square = factor * factor
                while (inside.mod(square).signum() == 0) {
                    inside /= square
                    outside *= factor
                }
                factor += BigInteger.ONE
            }
            val coefficient = Fraction.of(outside, of.den)
            return if (inside == BigInteger.ONE) Exact(coefficient) else Exact(Fraction.ZERO, coefficient, inside)
        }
    }
}
