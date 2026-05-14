package com.firesin.xuipanel.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.firesin.xuipanel.core.designsystem.chart.HistoryLineChart
import com.firesin.xuipanel.core.xui.dto.ServerHistoryPointDto

/**
 * Dashboard-local wrapper that keeps the old call-site signature
 * ([ServerHistoryPointDto] list) while delegating rendering to the
 * shared [com.firesin.xuipanel.core.designsystem.chart.HistoryLineChart].
 */
@Composable
fun HistoryLineChart(
    points: List<ServerHistoryPointDto>,
    modifier: Modifier = Modifier,
) {
    HistoryLineChart(
        values = points.map { it.v },
        noDataLabel = stringResource(R.string.dashboard_history_no_data),
        modifier = modifier,
    )
}
