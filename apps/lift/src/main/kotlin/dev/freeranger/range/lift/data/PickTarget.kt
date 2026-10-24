package dev.freeranger.range.lift.data

import kotlinx.serialization.Serializable

/** Where exercises picked from the library go: into a workout or template, or in place of one of its exercises. */
@Serializable
sealed interface PickTarget {
    /** Takes one exercise, in place of another, rather than adding any number. */
    val swaps: Boolean get() = this is SwapInWorkout || this is SwapInTemplate

    @Serializable
    data class IntoWorkout(val workoutId: Long) : PickTarget

    @Serializable
    data class IntoTemplate(val templateId: Long) : PickTarget

    /** In place of the workout's exercise [entryId]. */
    @Serializable
    data class SwapInWorkout(val entryId: Long) : PickTarget

    /** In place of the template's exercise [entryId]. */
    @Serializable
    data class SwapInTemplate(val entryId: Long) : PickTarget
}
