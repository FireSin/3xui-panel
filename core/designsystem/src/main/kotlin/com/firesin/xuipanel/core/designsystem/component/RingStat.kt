package com.firesin.xuipanel.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import kotlin.math.min

private const val StartAngleDeg = -90f
private val RingStrokeWidth = 5.dp
private val RingSize = 64.dp

/**
 * Circular ring progress stat widget as per Direction A design.
 *
 * @param label  upper label (e.g. "CPU")
 * @param value  large value string (e.g. "23.4")
 * @param unit   unit suffix (e.g. "%")
 * @param progress fraction 0f..1f
 * @param sub    small sub-text below value (e.g. "0.45 · 0.51 · 0.48")
 */
@Composable
fun RingStat(
    label: String,
    value: String,
    unit: String,
    progress: Float,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.outline

    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(RingSize)) {
                val strokePx = RingStrokeWidth.toPx()
                val radius = (min(size.width, size.height) - strokePx) / 2f
                val topLeft = Offset(
                    x = (size.width - radius * 2) / 2f,
                    y = (size.height - radius * 2) / 2f,
                )
                val arcSize = Size(radius * 2, radius * 2)
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = StartAngleDeg,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx),
                )
                // Progress
                drawArc(
                    color = accentColor,
                    startAngle = StartAngleDeg,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.5).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = MonoFontFamily,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RingStatPreview() {
    RingStat(
        label = "CPU",
        value = "23.4",
        unit = "%",
        progress = 0.234f,
        sub = "0.45 · 0.51 · 0.48",
    )
}
