package dev.freeranger.range.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A round checkbox with a number inside, like a workout's set number that's tapped once the set is
 * done: a ring around the number, filling with the accent when ticked, with [RangeCheckbox]'s pop
 * and burst. Material has no numbered checkbox.
 */
@Composable
fun RangeNumberCheck(
    number: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val container by animateColorAsState(if (checked) accent else accent.copy(alpha = 0f), label = "numberContainer")
    val ring by animateColorAsState(if (checked) accent else colors.outline, label = "numberRing")
    val content by animateColorAsState(if (checked) colors.onPrimary else colors.onSurface, label = "numberContent")

    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var lastChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        val forward = checked && !lastChecked
        lastChecked = checked
        if (!forward) return@LaunchedEffect
        launch {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
        pop.snapTo(0.82f)
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .toggleable(value = checked, role = Role.Checkbox) {
                haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onCheckedChange(it)
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = burst.value
            if (t < 1f) {
                drawCircle(
                    color = accent.copy(alpha = 0.5f * (1f - t)),
                    radius = 15.dp.toPx() + t * 9.dp.toPx(),
                    style = Stroke(width = (1f - t) * 4.dp.toPx()),
                )
            }
        }
        Box(
            Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .background(container, CircleShape)
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = content,
            )
        }
    }
}
