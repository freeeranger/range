package dev.freeranger.range.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import dev.freeranger.range.ui.icons.RangeIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A round checkbox, like Google Tasks', that fills with the app's accent. Material has no round
 * or multi-step checkbox, so this is one of the few custom components.
 *
 * For things done in several steps (a task due at 8:00 and 20:00), pass [steps]: the ring splits
 * into that many arcs, [stepsDone] of them light up, and it fills once every step is done.
 * Every step forward gets the same success moment as a full tick: the control pops, a ring
 * bursts outward, and a partial step flashes a check before settling on its lit arc.
 * Disabled checkboxes are drawn faint, for items that can't be ticked off yet.
 */
@Composable
fun RangeCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 1,
    stepsDone: Int = if (checked) steps else 0,
) {
    val haptics = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.let { if (enabled) it else it.copy(alpha = 0.3f) }

    val fill by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "fill",
    )
    // A little overshoot, so a newly lit arc snaps into place.
    val progress by animateFloatAsState(stepsDone.toFloat(), spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), label = "progress")

    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    var lastSteps by remember { mutableIntStateOf(stepsDone) }
    var lastChecked by remember { mutableStateOf(checked) }

    LaunchedEffect(stepsDone, checked) {
        val forward = stepsDone > lastSteps || (checked && !lastChecked)
        val partial = forward && !checked
        lastSteps = stepsDone
        lastChecked = checked
        if (!forward) return@LaunchedEffect
        launch {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
        if (partial) {
            launch {
                flash.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                delay(450)
                flash.animateTo(0f, tween(250))
            }
        }
        pop.snapTo(0.82f)
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
    }

    val state = when {
        checked -> ToggleableState.On
        stepsDone > 0 -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .triStateToggleable(state = state, enabled = enabled, role = Role.Checkbox) {
                haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onCheckedChange(!checked)
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        // The burst: a ring that grows out from the checkbox and fades.
        Canvas(Modifier.fillMaxSize()) {
            val t = burst.value
            if (t < 1f) {
                drawCircle(
                    color = accent.copy(alpha = 0.5f * (1f - t)),
                    radius = 11.dp.toPx() + t * 13.dp.toPx(),
                    style = Stroke(width = (1f - t) * 4.dp.toPx()),
                )
            }
        }
        Box(
            Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val segments = steps.coerceAtLeast(1)
                // Gaps only between segments; a single step is one unbroken ring.
                val gap = if (segments > 1) 28f else 0f
                val sweep = 360f / segments - gap
                val width = 2.dp.toPx()
                val inset = width / 2
                for (i in 0 until segments) {
                    val lit = (progress - i).coerceIn(0f, 1f)
                    drawArc(
                        color = lerp(idle, accent, lit),
                        startAngle = -90f + gap / 2 + i * (sweep + gap),
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - width, size.height - width),
                        style = Stroke(width = width + lit * 0.5.dp.toPx(), cap = if (segments > 1) StrokeCap.Round else StrokeCap.Butt),
                    )
                }
            }
            // A partial step's check: pops in inside the ring, then fades away.
            Icon(
                RangeIcons.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        alpha = flash.value.coerceIn(0f, 1f)
                        scaleX = 0.5f + 0.5f * flash.value
                        scaleY = 0.5f + 0.5f * flash.value
                    },
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = fill
                        scaleY = fill
                        alpha = fill.coerceIn(0f, 1f)
                    }
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(RangeIcons.Check, contentDescription = null, tint = onAccent, modifier = Modifier.size(14.dp))
            }
        }
    }
}
