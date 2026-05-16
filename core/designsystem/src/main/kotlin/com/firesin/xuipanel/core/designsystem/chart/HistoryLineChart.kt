package com.firesin.xuipanel.core.designsystem.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private const val CHART_HEIGHT_DP = 140
private const val LINE_WIDTH_DP = 1.5f
private const val FILL_ALPHA = 0.12f
private const val GRID_LINES = 4
private const val LABEL_FONT_SP = 10f
private const val LABEL_GUTTER_DP = 44
private const val EPS = 1e-9

/**
 * Lightweight Canvas line chart. Renders the value series scaled to the chart bounds,
 * plus a soft area fill under the line. When [valueFormatter] is provided, also draws
 * a 4-line horizontal grid with formatted Y-axis labels on the left.
 *
 * @param values         raw numeric series (needs at least 2 points to draw a line)
 * @param noDataLabel    text shown when [values] has fewer than 2 points
 * @param valueFormatter optional formatter for grid value labels; no grid drawn when null
 */
@Composable
fun HistoryLineChart(
    values: List<Double>,
    noDataLabel: String,
    modifier: Modifier = Modifier,
    valueFormatter: ((Double) -> String)? = null,
) {
    if (values.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = noDataLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = FILL_ALPHA)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val gridColor = baselineColor.copy(alpha = 0.6f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp),
    ) {
        val gutter = if (valueFormatter != null) LABEL_GUTTER_DP.dp.toPx() else 0f
        val plotLeft = gutter
        val plotRight = size.width
        val plotW = plotRight - plotLeft
        val h = size.height
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).coerceAtLeast(EPS)

        // Gridlines + Y-axis labels
        if (valueFormatter != null) {
            val labelPaint = android.graphics.Paint().apply {
                color = labelArgb
                textSize = LABEL_FONT_SP.dp.toPx()
                isAntiAlias = true
                typeface = android.graphics.Typeface.MONOSPACE
            }
            for (i in 0..GRID_LINES) {
                val frac = i.toFloat() / GRID_LINES
                val y = h * frac
                drawLine(
                    color = gridColor,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 0.5.dp.toPx(),
                )
                val value = maxV - frac * (maxV - minV)
                val label = valueFormatter(value)
                val ty = (y + labelPaint.textSize / 2).coerceIn(
                    labelPaint.textSize,
                    h,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    /* x = */ 0f,
                    /* y = */ ty,
                    labelPaint,
                )
            }
        }

        val xStep = if (values.size == 1) 0f else plotW / (values.size - 1)
        val xy = values.mapIndexed { i, v ->
            val nx = plotLeft + i * xStep
            val ny = h - ((v - minV) / range * h).toFloat()
            Offset(nx, ny)
        }

        // Baseline (bottom edge)
        drawLine(
            color = baselineColor,
            start = Offset(plotLeft, h),
            end = Offset(plotRight, h),
            strokeWidth = 0.5.dp.toPx(),
        )

        // Area fill under the line
        val fill = Path().apply {
            moveTo(xy.first().x, h)
            xy.forEach { lineTo(it.x, it.y) }
            lineTo(xy.last().x, h)
            close()
        }
        drawPath(path = fill, color = fillColor)

        // Line
        val line = Path().apply {
            moveTo(xy.first().x, xy.first().y)
            xy.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = LINE_WIDTH_DP.dp.toPx()),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HistoryLineChartPreview() {
    HistoryLineChart(
        values = listOf(10.0, 20.0, 15.0, 30.0, 25.0, 40.0, 35.0),
        noDataLabel = "Нет данных",
        valueFormatter = { "%.1f".format(it) },
    )
}

@Preview(showBackground = true, name = "No data")
@Composable
private fun HistoryLineChartEmptyPreview() {
    HistoryLineChart(
        values = emptyList(),
        noDataLabel = "Нет данных",
    )
}
