package dev.freeranger.range.lift.data

import kotlin.math.roundToInt

/**
 * Rep maxes by Epley's formula: what [kg] for [reps] says could be lifted for any other number of
 * reps. The same formula as [WorkoutSet.estimatedMax], so the calculator agrees with the records.
 */
object RepMax {
    /** Rep counts the calculator lists; past about a dozen the estimates stop meaning much. */
    val REPS = 1..12

    /** The most [kg] for [reps] says could be lifted once. */
    fun oneRepMax(kg: Double, reps: Int): Double = if (reps <= 1) kg else kg * (1 + reps / 30.0)

    /** What could be lifted for [reps], from a one-rep max of [max]. */
    fun weightFor(max: Double, reps: Int): Double = if (reps <= 1) max else max / (1 + reps / 30.0)

    /** [kg] rounded to the nearest half kilo, as close as a calculator's answer is worth. */
    fun rounded(kg: Double): Double = (kg * 2).roundToInt() / 2.0
}
