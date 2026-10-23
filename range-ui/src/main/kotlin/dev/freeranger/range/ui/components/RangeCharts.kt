package dev.freeranger.range.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * Charts drawn by hand, since Material has none and chart libraries bring their own look. They
 * follow one set of marks: thin columns with 4dp rounded ends on a square baseline, 2dp lines
 * with a 10% wash under them, 8dp dots with a ring in the card's colour, hairline gridlines,
 * and text in text colours, never the data's. Each plots a single series in the accent, so the
 * card's title names it and no legend is needed. Tapping picks a point; the caller shows its
 * value, so selection is hoisted.
 */

private val ChartHeight = 168.dp
private val ColumnMaxWidth = 24.dp
private val AxisGap = 8.dp

/**
 * One column per period, like weekly volume. [selected] is drawn in the accent and the rest in a
 * lighter step of it; tapping a column selects it. [xLabel] labels the columns that should be
 * (null skips one), and [description] is what screen readers hear instead.
 */
@Composable
fun RangeColumnChart(
    values: List<Double>,
    selected: Int,
    onSelect: (Int) -> Unit,
    xLabel: (Int) -> String?,
    valueLabel: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, fontFeatureSettings = "tnum")
    val scale = remember(values) { NiceScale.from(0.0, values.maxOrNull() ?: 0.0, startAtZero = true) }
    val tickLabels = scale.ticks.map { measurer.measure(valueLabel(it), axisStyle) }
    val xLabels = values.indices.map { i -> xLabel(i)?.let { measurer.measure(it, axisStyle) } }
    val select by rememberUpdatedState(onSelect)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = description }
            .pointerInput(values.size) {
                detectTapGestures { offset ->
                    val left = (tickLabels.maxOfOrNull { it.size.width } ?: 0) + AxisGap.toPx()
                    val band = (size.width - left) / values.size
                    if (offset.x >= left) select(((offset.x - left) / band).toInt().coerceIn(values.indices))
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val left = (tickLabels.maxOfOrNull { it.size.width } ?: 0) + AxisGap.toPx()
        val bottom = size.height - (xLabels.firstNotNullOfOrNull { it }?.size?.height ?: 0) - AxisGap.toPx()
        val top = tickLabels.first().size.height / 2f
        val plot = Plot(left, top, size.width, bottom)
        drawGrid(plot, scale, tickLabels, colors.outlineVariant)

        val band = plot.width / values.size
        val barWidth = minOf(ColumnMaxWidth.toPx(), band * 0.6f)
        val radius = CornerRadius(4.dp.toPx())
        values.forEachIndexed { i, value ->
            val center = left + band * (i + 0.5f)
            val height = (plot.height * (value / scale.max)).toFloat()
            if (height > 0f) {
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = center - barWidth / 2, top = bottom - height, right = center + barWidth / 2, bottom = bottom,
                            topLeftCornerRadius = radius, topRightCornerRadius = radius,
                        ),
                    )
                }
                drawPath(path, if (i == selected) colors.primary else colors.primary.copy(alpha = 0.35f))
            }
            xLabels[i]?.let { drawCentered(it, center, bottom + AxisGap.toPx()) }
        }
    }
}

/**
 * A line through values over time, like an exercise's estimated one-rep max. Points sit at their
 * [x] (e.g. epoch days), so gaps between sessions show as gaps. Tap or drag to pick a point.
 * [ringColor] is the surface behind the chart, ringing each dot so it stands off the line.
 */
@Composable
fun RangeLineChart(
    x: List<Double>,
    y: List<Double>,
    selected: Int,
    onSelect: (Int) -> Unit,
    xLabel: (Double) -> String,
    valueLabel: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, fontFeatureSettings = "tnum")
    val scale = remember(y) { NiceScale.from(y.minOrNull() ?: 0.0, y.maxOrNull() ?: 0.0, startAtZero = false) }
    val tickLabels = scale.ticks.map { measurer.measure(valueLabel(it), axisStyle) }
    val firstLabel = x.firstOrNull()?.let { measurer.measure(xLabel(it), axisStyle) }
    val lastLabel = x.lastOrNull()?.takeIf { x.size > 1 }?.let { measurer.measure(xLabel(it), axisStyle) }
    val select by rememberUpdatedState(onSelect)
    val dot = 4.dp
    val ring = 2.dp

    fun DrawScope.geometry(): Pair<Plot, (Int) -> Offset> {
        val left = (tickLabels.maxOfOrNull { it.size.width } ?: 0) + (AxisGap + dot + ring).toPx()
        val bottom = size.height - (firstLabel?.size?.height ?: 0) - AxisGap.toPx()
        val plot = Plot(left, tickLabels.first().size.height / 2f, size.width - (dot + ring).toPx(), bottom)
        val minX = x.minOrNull() ?: 0.0
        val spanX = (x.maxOrNull() ?: 0.0) - minX
        val at = { i: Int ->
            val px = if (spanX == 0.0) plot.left + plot.width / 2 else plot.left + (plot.width * ((x[i] - minX) / spanX)).toFloat()
            Offset(px, plot.bottom - (plot.height * ((y[i] - scale.min) / (scale.max - scale.min))).toFloat())
        }
        return plot to at
    }

    /** The point closest to [px] across, in a chart [width] wide. */
    fun Density.nearest(px: Float, width: Float): Int {
        val left = (tickLabels.maxOfOrNull { it.size.width } ?: 0) + (AxisGap + dot + ring).toPx()
        val right = width - (dot + ring).toPx()
        val minX = x.minOrNull() ?: 0.0
        val spanX = (x.maxOrNull() ?: 0.0) - minX
        if (spanX == 0.0) return x.lastIndex
        val value = minX + spanX * ((px - left) / (right - left)).coerceIn(0f, 1f)
        return x.indices.minBy { abs(x[it] - value) }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .semantics { contentDescription = description }
            .pointerInput(x) { detectTapGestures { select(nearest(it.x, size.width.toFloat())) } }
            .pointerInput(x) { detectHorizontalDragGestures { change, _ -> select(nearest(change.position.x, size.width.toFloat())) } },
    ) {
        if (x.isEmpty()) return@Canvas
        val (plot, at) = geometry()
        drawGrid(plot, scale, tickLabels, colors.outlineVariant)

        val points = x.indices.map(at)
        if (points.size > 1) {
            val line = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, plot.bottom)
                lineTo(points.first().x, plot.bottom)
                close()
            }
            drawPath(area, colors.primary.copy(alpha = 0.1f))
            drawPath(line, colors.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        points.getOrNull(selected)?.let {
            drawLine(colors.outline, Offset(it.x, plot.top), Offset(it.x, plot.bottom), strokeWidth = 1.dp.toPx())
        }
        // Every dot while there are few enough to tell apart; past that, only the picked one.
        points.forEachIndexed { i, point ->
            if (i == selected || points.size <= 16) {
                val radius = (if (i == selected) dot + 1.dp else dot).toPx()
                drawCircle(ringColor, radius + ring.toPx(), point)
                drawCircle(colors.primary, radius, point)
            }
        }
        firstLabel?.let { drawCentered(it, points.first().x, plot.bottom + AxisGap.toPx()) }
        lastLabel?.let { drawCentered(it, points.last().x, plot.bottom + AxisGap.toPx()) }
    }
}

/**
 * Horizontal bars from a shared left edge, each with its value at the tip: amounts compared
 * across named things, like sets per muscle group.
 */
@Composable
fun RangeBarList(
    items: List<Pair<String, Double>>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 88.dp,
) {
    val colors = MaterialTheme.colorScheme
    val most = items.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.width(labelWidth),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    // Leaves room for the value at the end of the longest bar.
                    val fraction = (value / most).toFloat() * 0.8f
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(12.dp)
                                .background(colors.primary, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                        )
                    }
                    Text(
                        valueLabel(value),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = if (value > 0) colors.onSurface else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Where the data goes inside a chart, in pixels. */
private class Plot(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}

/** Three or four round-numbered ticks covering a range of values: 0 / 50 / 100 / 150. */
internal class NiceScale(val min: Double, val max: Double, val ticks: List<Double>) {
    companion object {
        fun from(low: Double, high: Double, startAtZero: Boolean): NiceScale {
            val span = (high - low).takeIf { it > 0 } ?: max(abs(high) * 0.1, 1.0)
            // A free-floating range gets a little air above and below, so no point sits on an edge.
            val pad = if (startAtZero) 0.0 else span * 0.1
            val step = if (startAtZero) niceStep(max(high, span), 3) else niceStep(span + 2 * pad, 4)
            val min = if (startAtZero) 0.0 else max(0.0, floor((low - pad) / step) * step)
            val top = max(ceil((high + pad) / step) * step, min + step)
            val count = ((top - min) / step).roundToInt()
            return NiceScale(min, top, (0..count).map { min + it * step })
        }

        private fun niceStep(range: Double, ticks: Int): Double {
            val raw = range / ticks
            val magnitude = 10.0.pow(floor(log10(raw)))
            val nice = when (raw / magnitude) {
                in 0.0..1.0 -> 1.0
                in 1.0..2.0 -> 2.0
                in 2.0..2.5 -> 2.5
                in 2.5..5.0 -> 5.0
                else -> 10.0
            }
            return nice * magnitude
        }
    }
}

/** Hairline gridlines at each tick, labelled on the left. */
private fun DrawScope.drawGrid(plot: Plot, scale: NiceScale, labels: List<TextLayoutResult>, color: Color) {
    scale.ticks.forEachIndexed { i, tick ->
        val y = plot.bottom - (plot.height * ((tick - scale.min) / (scale.max - scale.min))).toFloat()
        drawLine(color, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
        val label = labels[i]
        drawText(label, topLeft = Offset(plot.left - AxisGap.toPx() - label.size.width, y - label.size.height / 2f))
    }
}

/** Text centred under [x], kept inside the chart. */
private fun DrawScope.drawCentered(label: TextLayoutResult, x: Float, top: Float) {
    val left = (x - label.size.width / 2f).coerceIn(0f, max(0f, size.width - label.size.width))
    drawText(label, topLeft = Offset(left, top))
}
