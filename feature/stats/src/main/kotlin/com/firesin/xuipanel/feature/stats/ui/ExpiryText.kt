package com.firesin.xuipanel.feature.stats.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.firesin.xuipanel.core.common.util.ExpiryLabel
import com.firesin.xuipanel.core.common.util.classifyExpiry
import com.firesin.xuipanel.feature.stats.R

@Composable
fun ExpiryText(
    expiryMs: Long,
    modifier: Modifier = Modifier,
) {
    val label = classifyExpiry(expiryMs)
    val (text, color) = when (label) {
        is ExpiryLabel.Never -> Pair(
            stringResource(R.string.stats_expiry_never),
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is ExpiryLabel.ExpiresIn -> {
            val days = label.days
            val daysStr = pluralStringResource(R.plurals.stats_expiry_in_days, days, days)
            val prefix = stringResource(R.string.stats_expiry_in_days_prefix)
            Pair(
                "$prefix $daysStr",
                if (days <= 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is ExpiryLabel.ExpiredAgo -> {
            val days = label.days
            val daysStr = pluralStringResource(R.plurals.stats_expiry_ago_days, days, days)
            val prefix = stringResource(R.string.stats_expiry_ago_days_prefix)
            val suffix = stringResource(R.string.stats_expiry_ago_days_suffix)
            Pair(
                "$prefix $daysStr $suffix",
                MaterialTheme.colorScheme.error,
            )
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = modifier,
    )
}
