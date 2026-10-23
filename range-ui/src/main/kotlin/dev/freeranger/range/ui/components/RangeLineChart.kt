package dev.freeranger.range.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * A line through one value per point in time, like an exercise's best set each workout, drawn
 * with Vico in the app's colours and type. The points are evenly spaced, oldest first, and the
 * whole series fits the width. Pressing the chart shows a point's [markerLabel]; [xLabel] names
 * the points along the bottom and [valueLabel] the values up the side. Screen readers hear
 * [description].
 */
@Composable
fun RangeLineChart(
    values: List<Double>,
    xLabel: (Int) -> String,
    valueLabel: (Double) -> String,
    markerLabel: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
    /** The surface behind the chart, ringing each dot so it stands off the line. */
    surface: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    val axisText = rememberTextComponent(
        style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, fontFeatureSettings = "tnum"),
        margins = Insets(4.dp),
    )
    val hairline = rememberLineComponent(fill = Fill(colors.outlineVariant), thickness = 1.dp)
    val scale = remember(values) { NiceScale.from(values.minOrNull() ?: 0.0, values.maxOrNull() ?: 0.0) }
    val currentXLabel by rememberUpdatedState(xLabel)
    val currentValueLabel by rememberUpdatedState(valueLabel)
    val currentMarkerLabel by rememberUpdatedState(markerLabel)

    // Every dot while there are few enough to tell apart; past that, only the line.
    val dot = rememberShapeComponent(fill = Fill(accent), shape = CircleShape, strokeFill = Fill(surface), strokeThickness = 2.dp)
    val line = LineCartesianLayer.rememberLine(
        fill = remember(accent) { LineCartesianLayer.LineFill.single(Fill(accent)) },
        stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp, cap = StrokeCap.Round),
        areaFill = remember(accent) {
            LineCartesianLayer.AreaFill.single(Fill(Brush.verticalGradient(listOf(accent.copy(alpha = 0.2f), accent.copy(alpha = 0f)))))
        },
        pointProvider = if (values.size <= 24) LineCartesianLayer.PointProvider.single(LineCartesianLayer.Point(dot, 10.dp)) else null,
    )
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            style = MaterialTheme.typography.labelLarge.copy(color = colors.inverseOnSurface),
            padding = Insets(horizontal = 8.dp, vertical = 4.dp),
            margins = Insets(bottom = 4.dp),
            background = rememberShapeComponent(fill = Fill(colors.inverseSurface), shape = RoundedCornerShape(8.dp)),
        ),
        valueFormatter = remember { DefaultCartesianMarker.ValueFormatter { _, targets -> currentMarkerLabel(targets.first().x.toInt()) } },
        indicator = { ShapeComponent(fill = Fill(it), shape = CircleShape, strokeFill = Fill(surface), strokeThickness = 2.dp) },
        indicatorSize = 12.dp,
        guideline = rememberLineComponent(fill = Fill(colors.outline), thickness = 1.dp),
    )
    val chart = rememberCartesianChart(
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(line),
            rangeProvider = remember(scale) { CartesianLayerRangeProvider.fixed(minY = scale.min, maxY = scale.max) },
        ),
        startAxis = VerticalAxis.rememberStart(
            line = null,
            label = axisText,
            tick = null,
            guideline = hairline,
            valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentValueLabel(value) } },
            itemPlacer = remember(scale) { VerticalAxis.ItemPlacer.step({ scale.step }) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            line = hairline,
            label = axisText,
            tick = null,
            guideline = null,
            valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentXLabel(value.toInt()) } },
            // About four dates across, whatever the number of points.
            itemPlacer = remember(values.size) { HorizontalAxis.ItemPlacer.aligned(spacing = { max(1, (values.size + 3) / 4) }) },
        ),
        marker = marker,
    )
    val model = remember(values) { CartesianChartModel(LineCartesianLayerModel.build { series(values.indices.toList(), values) }) }

    CartesianChartHost(
        chart = chart,
        model = model,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
    )
}

/** Round-numbered ticks around a range of values, with a little air so no point sits on an edge. */
internal class NiceScale(val min: Double, val max: Double, val step: Double) {
    companion object {
        fun from(low: Double, high: Double): NiceScale {
            val span = (high - low).takeIf { it > 0 } ?: max(abs(high) * 0.1, 1.0)
            val pad = span * 0.1
            val step = niceStep((span + 2 * pad) / 4)
            val min = max(0.0, floor((low - pad) / step) * step)
            return NiceScale(min, max(ceil((high + pad) / step) * step, min + step), step)
        }

        private fun niceStep(raw: Double): Double {
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
