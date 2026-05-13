package com.firesin.xuipanel.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultHeight = 40.dp
private const val LINE_WIDTH_DP = 1.5f

/**
 * A compact line chart that renders a sequence of values as a single mint stroke
 * over a faded gradient fill. Intended for hero-card trend visualisations.
 *
 * Values are positioned uniformly along the x axis. y is normalised to the
 * largest value in the series; if the series contains fewer than two points
 * or all values are zero, nothing is drawn.
 */
@Composable
fun Sparkline(
    values: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = DefaultHeight,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (values.size < 2) return@Box
        val maxV = values.max()
        if (maxV <= 0L) return@Box

        val fillBrush = Brush.verticalGradient(
            listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0f)),
        )

        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val stepX = if (values.size > 1) w / (values.size - 1) else 0f

            val linePath = Path()
            val fillPath = Path()
            values.forEachIndexed { i, v ->
                val x = i * stepX
                val y = h - (v.toFloat() / maxV.toFloat()) * h
                if (i == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(w, h)
            fillPath.close()

            drawPath(path = fillPath, brush = fillBrush)
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = LINE_WIDTH_DP * density),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SparklinePreview() {
    Sparkline(values = listOf(10L, 24L, 18L, 33L, 41L, 28L, 52L))
}

@Preview(showBackground = true)
@Composable
private fun SparklineEmptyPreview() {
    Sparkline(values = emptyList())
}
