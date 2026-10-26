package dev.freeranger.range.calc.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.sign

/** What an equation in one unknown comes to. */
sealed interface Solutions {
    /** The real values of the unknown that make it true, smallest first, and where known, each written exactly. */
    data class Roots(val values: List<BigDecimal>, val exact: List<Exact?> = values.map { null }) : Solutions
    /** True whatever the unknown is: "2x = x + x". */
    data object Any : Solutions
    /** No real value makes it true: "x² = −1". */
    data object None : Solutions
}

/**
 * Finds where [f] is zero. Lines and parabolas are solved exactly; anything else is searched
 * for every sign change and touch of zero between −1 000 000 and 1 000 000, and each found
 * root is narrowed down to about ten decimals.
 */
class Solver(
    /** The equation's left side minus its right side, or null where it isn't defined (like 1/0). */
    private val f: (BigDecimal) -> BigDecimal?,
) {
    fun solve(): Solutions = exact() ?: numeric()

    /** A line or a parabola, told apart by how evenly it changes over a handful of points. */
    private fun exact(): Solutions? {
        val xs = listOf("-3", "-2", "-1", "-0.5", "0", "0.5", "1", "2", "3").map(::BigDecimal)
        val ys = xs.map { f(it) ?: return null }
        val c = ys[4]
        val a = ys[6].add(ys[2]).divide(TWO, MATH).subtract(c)
        val b = ys[6].subtract(ys[2]).divide(TWO, MATH)
        // Does a x² + b x + c match every point?
        val fits = xs.indices.all { i -> close(a.multiply(xs[i].pow(2)).add(b.multiply(xs[i])).add(c, MATH), ys[i]) }
        if (!fits) return null
        // Whole numbers and fractions in, an exact answer out: x² − 5x + 3 = 0 is (5 ± √13)/2.
        val fractions = listOf(a, b, c).map { Fraction.near(it, 1_000_000) }
        if (fractions.all { it != null }) exactRoots(fractions[0]!!, fractions[1]!!, fractions[2]!!)?.let { return it }
        if (isZero(a)) {
            if (isZero(b)) return if (isZero(c)) Solutions.Any else Solutions.None
            return roots(listOf(c.negate().divide(b, MATH)))
        }
        val discriminant = b.pow(2).subtract(FOUR.multiply(a).multiply(c), MATH)
        if (discriminant.signum() < 0 && !isZero(discriminant)) return Solutions.None
        if (isZero(discriminant)) return roots(listOf(b.negate().divide(TWO.multiply(a), MATH)))
        val root = discriminant.sqrt(MATH)
        val twoA = TWO.multiply(a)
        return roots(listOf(b.negate().subtract(root).divide(twoA, MATH), b.negate().add(root).divide(twoA, MATH)))
    }

    /** a x² + b x + c = 0 with fractions for a, b and c, solved without rounding. */
    private fun exactRoots(a: Fraction, b: Fraction, c: Fraction): Solutions? {
        val zero = Fraction.ZERO
        if (a == zero) {
            if (b == zero) return if (c == zero) Solutions.Any else Solutions.None
            val x = -c / b
            return Solutions.Roots(listOf(x.toBigDecimal()), listOf(Exact(x)))
        }
        val two = Fraction.of(2)
        val discriminant = b * b - Fraction.of(4) * a * c
        if (discriminant.num.signum() < 0) return Solutions.None
        val middle = -b / (two * a)
        if (discriminant == zero) return Solutions.Roots(listOf(middle.toBigDecimal()), listOf(Exact(middle)))
        val root = Exact.root(discriminant)
        val spread = (if (root.surd == zero) root.rational else root.surd) / (two * a)
        val half = if (spread.num.signum() < 0) -spread else spread
        val exact = if (root.surd == zero) {
            listOf(Exact(middle - half), Exact(middle + half))
        } else {
            listOf(Exact(middle, -half, root.radicand), Exact(middle, half, root.radicand))
        }
        val values = exact.map { it.rational.toBigDecimal().add(it.surd.toBigDecimal().multiply(BigDecimal(it.radicand).sqrt(MATH), MATH), MATH) }
        return Solutions.Roots(values.map { it.round(MathContext(30)) }, exact)
    }

    private fun numeric(): Solutions {
        val g = { x: Double -> runCatching { f(BigDecimal(x))?.toDouble() }.getOrNull()?.takeIf { it.isFinite() } }
        val xs = grid()
        val ys = xs.map(g)
        val found = mutableListOf<Double>()
        for (i in 0 until xs.size - 1) {
            val (y0, y1) = ys[i] to ys[i + 1]
            if (y0 == null || y1 == null) continue
            when {
                y0 == 0.0 -> found += xs[i]
                y0.sign != y1.sign && y1 != 0.0 -> bisect(g, xs[i], xs[i + 1])?.let { found += it }
            }
            // A touch of zero without crossing it: x² = 0, (x − 1)² = 0.
            if (i > 0) {
                val before = ys[i - 1]
                if (before != null && abs(y0) < abs(before) && abs(y0) < abs(y1) && y0.sign == y1.sign && y0.sign == before.sign) {
                    lowest(g, xs[i - 1], xs[i + 1])?.let { found += it }
                }
            }
        }
        val distinct = found.sorted().fold(mutableListOf<Double>()) { acc, x ->
            if (acc.isEmpty() || abs(acc.last() - x) > 1e-7 * maxOf(1.0, abs(x))) acc += x
            acc
        }
        return if (distinct.isEmpty()) Solutions.None else roots(distinct.take(MAX_ROOTS).map(::BigDecimal))
    }

    /** Points close together near zero, where most answers are, and further apart going out. */
    private fun grid(): List<Double> {
        val near = (-2000..2000).map { it * 0.01 }
        val far = (1..300).map { 20.0 * Math.pow(50_000.0, it / 300.0) }
        return (far.reversed().map { -it } + near + far).distinct()
    }

    private fun bisect(g: (Double) -> Double?, from: Double, to: Double): Double? {
        var (a, b) = from to to
        var ga = g(a) ?: return null
        repeat(200) {
            val mid = (a + b) / 2
            val gm = g(mid) ?: return null
            if (gm == 0.0) return mid
            if (gm.sign == ga.sign) {
                a = mid
                ga = gm
            } else {
                b = mid
            }
        }
        val x = (a + b) / 2
        // A jump across zero, as 1/x makes at 0, isn't a root.
        val y = g(x) ?: return null
        return x.takeIf { abs(y) < 1e-6 * maxOf(1.0, abs(x)) }
    }

    /** Where |g| is lowest between [from] and [to], if it gets to zero there. */
    private fun lowest(g: (Double) -> Double?, from: Double, to: Double): Double? {
        val ratio = (Math.sqrt(5.0) - 1) / 2
        var (a, b) = from to to
        repeat(200) {
            val c = b - ratio * (b - a)
            val d = a + ratio * (b - a)
            val gc = g(c)?.let(::abs) ?: return null
            val gd = g(d)?.let(::abs) ?: return null
            if (gc < gd) b = d else a = c
        }
        val x = (a + b) / 2
        val y = g(x) ?: return null
        return x.takeIf { abs(y) < 1e-9 }
    }

    /** Roots tidied: whole numbers where they're within a hair of one, smallest first. */
    private fun roots(values: List<BigDecimal>): Solutions.Roots = Solutions.Roots(
        values.map { x ->
            val rounded = x.setScale(0, RoundingMode.HALF_UP)
            if (x.subtract(rounded).abs() < BigDecimal("1E-9")) rounded else x.round(MathContext(15))
        }.sorted().distinct(),
    )

    private fun isZero(x: BigDecimal) = x.abs() < EPSILON
    private fun close(a: BigDecimal, b: BigDecimal) = a.subtract(b).abs() <= EPSILON.max(b.abs().multiply(BigDecimal("1E-20")))

    private companion object {
        val TWO = BigDecimal(2)
        val FOUR = BigDecimal(4)
        val EPSILON = BigDecimal("1E-20")
        const val MAX_ROOTS = 8
    }
}
