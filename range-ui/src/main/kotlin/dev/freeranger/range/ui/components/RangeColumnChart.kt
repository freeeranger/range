package dev.freeranger.range.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import kotlin.math.ceil
import kotlin.math.max

/**
 * A bar per period for a count, like workouts each week, drawn with Vico in the app's colours and
 * type. The bars are oldest first and fill the width; the scale up the side is whole numbers from
 * zero. Pressing a bar shows its [markerLabel]; [xLabel] names the bars along the bottom. Screen
 * readers hear [description].
 */
@Composable
fun RangeColumnChart(
    values: List<Int>,
    xLabel: (Int) -> String,
    markerLabel: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val axisText = rememberTextComponent(
        style = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant, fontFeatureSettings = "tnum"),
        margins = Insets(4.dp),
    )
    val hairline = rememberLineComponent(fill = Fill(colors.outlineVariant), thickness = 1.dp)
    // Whole steps from zero, about four of them however high the bars go.
    val top = max(values.maxOrNull() ?: 0, 1)
    val step = ceil(top / 4.0).toInt().coerceAtLeast(1)
    val maxY = (ceil(top / step.toDouble()) * step).toDouble()
    val currentXLabel by rememberUpdatedState(xLabel)
    val currentMarkerLabel by rememberUpdatedState(markerLabel)

    val bar = rememberLineComponent(
        fill = Fill(colors.primary),
        thickness = 14.dp,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
    )
    val marker = rememberDefaultCartesianMarker(
        label = rememberTextComponent(
            style = MaterialTheme.typography.labelLarge.copy(color = colors.inverseOnSurface),
            padding = Insets(horizontal = 8.dp, vertical = 4.dp),
            margins = Insets(bottom = 4.dp),
            background = rememberShapeComponent(fill = Fill(colors.inverseSurface), shape = RoundedCornerShape(8.dp)),
        ),
        valueFormatter = remember { DefaultCartesianMarker.ValueFormatter { _, targets -> currentMarkerLabel(targets.first().x.toInt()) } },
        indicator = null,
        guideline = null,
    )
    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(bar),
            columnCollectionSpacing = 8.dp,
            rangeProvider = remember(maxY) { CartesianLayerRangeProvider.fixed(minY = 0.0, maxY = maxY) },
        ),
        startAxis = VerticalAxis.rememberStart(
            line = null,
            label = axisText,
            tick = null,
            guideline = hairline,
            valueFormatter = remember { CartesianValueFormatter { _, value, _ -> value.toInt().toString() } },
            itemPlacer = remember(step) { VerticalAxis.ItemPlacer.step({ step.toDouble() }) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            line = hairline,
            label = axisText,
            tick = null,
            guideline = null,
            valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentXLabel(value.toInt()) } },
            // About four labels across, whatever the number of bars.
            itemPlacer = remember(values.size) { HorizontalAxis.ItemPlacer.aligned(spacing = { max(1, (values.size + 3) / 4) }) },
        ),
        marker = marker,
    )
    val model = remember(values) { CartesianChartModel(ColumnCartesianLayerModel.build { series(values.indices.toList(), values) }) }

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
