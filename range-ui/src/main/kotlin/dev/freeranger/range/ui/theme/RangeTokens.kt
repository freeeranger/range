package dev.freeranger.range.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.dp

/** Material's 4dp spacing grid, named like Tailwind's (s4 = 16dp). */
object RangeSpace {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s6 = 24.dp
    val s8 = 32.dp

    /** Side margin for screen content, matching Material list items. */
    val gutter = s4
}

/**
 * Page transitions in the style of Android's own: the new page slides in from the edge while
 * the old one drifts a little the other way and dims. Pass these to Navigation 3's NavDisplay.
 */
object RangeTransitions {
    /** Material's emphasized easing: quick to start, long gentle settle. */
    private val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private const val DURATION = 400

    /** How far the page underneath moves, as a fraction of the width. */
    private const val PARALLAX = 4
    private const val DIM = 0.4f

    /** A pager sliding to a page picked elsewhere, with the same timing as a page transition. */
    val pageSlide: AnimationSpec<Float> = tween(DURATION, easing = emphasized)

    fun forward(): ContentTransform = ContentTransform(
        targetContentEnter = slideInHorizontally(tween(DURATION, easing = emphasized)) { it },
        initialContentExit = slideOutHorizontally(tween(DURATION, easing = emphasized)) { -it / PARALLAX } +
            fadeOut(tween(DURATION, easing = emphasized), targetAlpha = DIM),
    )

    fun back(): ContentTransform = back(emphasized)

    /** The swipe-back gesture: linear, so the page tracks the finger exactly. */
    fun predictiveBack(): ContentTransform = back(LinearEasing)

    private fun back(easing: Easing) = ContentTransform(
        targetContentEnter = slideInHorizontally(tween(DURATION, easing = easing)) { -it / PARALLAX } +
            fadeIn(tween(DURATION, easing = easing), initialAlpha = DIM),
        initialContentExit = slideOutHorizontally(tween(DURATION, easing = easing)) { it },
        // Keep the page that's leaving on top as it slides away.
        targetContentZIndex = -1f,
    )
}
