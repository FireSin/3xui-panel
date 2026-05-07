package com.firesin.xuipanel.feature.stats.chart

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.firesin.xuipanel.core.common.util.prettyBytes
import com.firesin.xuipanel.core.data.repository.DailyPoint
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme
import com.firesin.xuipanel.feature.stats.R
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.component.LineComponent
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DAY_LABEL_KEY = ExtraStore.Key<List<String>>()
private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM").withZone(ZoneOffset.UTC)

@Composable
fun InboundTrafficChart(
    points: List<DailyPoint>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.stats_chart_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(points) {
        val labels = points.map { pt ->
            DATE_FORMATTER.format(Instant.ofEpochMilli(pt.dayEpoch))
        }
        modelProducer.runTransaction {
            columnSeries {
                series(points.map { it.up })
                series(points.map { it.down })
            }
            extras { store -> store[DAY_LABEL_KEY] = labels }
        }
    }

    val upColumn = remember(primaryColor) {
        LineComponent(fill = fill(primaryColor), thicknessDp = COLUMN_THICKNESS_DP)
    }
    val downColumn = remember(tertiaryColor) {
        LineComponent(fill = fill(tertiaryColor), thicknessDp = COLUMN_THICKNESS_DP)
    }

    val yFormatter = remember {
        object : CartesianValueFormatter {
            override fun format(
                context: CartesianMeasuringContext,
                value: Double,
                verticalAxisPosition: Axis.Position.Vertical?,
            ): CharSequence = prettyBytes(value.toLong())
        }
    }

    val xFormatter = remember {
        object : CartesianValueFormatter {
            override fun format(
                context: CartesianMeasuringContext,
                value: Double,
                verticalAxisPosition: Axis.Position.Vertical?,
            ): CharSequence =
                context.model.extraStore.getOrNull(DAY_LABEL_KEY)
                    ?.getOrNull(value.toInt())
                    ?: ""
        }
    }

    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(upColumn, downColumn),
        ),
        startAxis = VerticalAxis.rememberStart(valueFormatter = yFormatter),
        bottomAxis = HorizontalAxis.rememberBottom(
            valueFormatter = xFormatter,
            itemPlacer = HorizontalAxis.ItemPlacer.segmented(),
        ),
    )

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp)
            .padding(horizontal = 4.dp),
        zoomState = rememberVicoZoomState(zoomEnabled = false),
    )
}

private const val CHART_HEIGHT_DP = 180
private const val COLUMN_THICKNESS_DP = 8f

@Preview(showBackground = true)
@Composable
private fun InboundTrafficChartPreview() {
    XuiPanelTheme {
        val msPerDay = 86_400_000L
        val today = (System.currentTimeMillis() / msPerDay) * msPerDay
        InboundTrafficChart(
            points = listOf(
                DailyPoint(today - 2 * msPerDay, 10_000_000L, 50_000_000L),
                DailyPoint(today - msPerDay, 20_000_000L, 80_000_000L),
                DailyPoint(today, 5_000_000L, 30_000_000L),
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InboundTrafficChartEmptyPreview() {
    XuiPanelTheme {
        InboundTrafficChart(points = emptyList())
    }
}
