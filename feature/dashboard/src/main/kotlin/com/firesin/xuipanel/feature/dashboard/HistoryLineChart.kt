package com.firesin.xuipanel.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto

private const val CHART_HEIGHT_DP = 140
private const val LINE_WIDTH_DP = 1.5f
private const val FILL_ALPHA = 0.12f

/**
 * Lightweight Canvas line chart for the dashboard history section. Renders the value series
 * scaled to the chart bounds, plus a soft area fill under the line.
 */
@Composable
fun HistoryLineChart(
    points: List<ServerHistoryPointDto>,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.dashboard_history_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = FILL_ALPHA)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp),
    ) {
        val w = size.width
        val h = size.height
        val values = points.map { it.v }
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).coerceAtLeast(EPS)

        val xStep = if (points.size == 1) 0f else w / (points.size - 1)
        val xy = points.mapIndexed { i, p ->
            val nx = i * xStep
            val ny = h - ((p.v - minV) / range * h).toFloat()
            Offset(nx, ny)
        }

        // Baseline (bottom edge)
        drawLine(
            color = baselineColor,
            start = Offset(0f, h),
            end = Offset(w, h),
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

private const val EPS = 1e-9
