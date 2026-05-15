package com.firesin.xuipanel.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firesin.xuipanel.core.designsystem.theme.MonoFontFamily
import com.firesin.xuipanel.core.designsystem.theme.XuiPanelTheme

/** Health of a panel, mirrored from the design's `Dot` statuses. */
enum class PanelStatus { Up, Warn, Down, Offline }

@Composable
fun PanelStatus.color(): Color = when (this) {
    PanelStatus.Up -> MaterialTheme.colorScheme.primary
    PanelStatus.Warn -> MaterialTheme.colorScheme.tertiary
    PanelStatus.Down -> MaterialTheme.colorScheme.error
    PanelStatus.Offline -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Pill-shaped button in the top bar of the three panel-dependent sections
 * (Главная / Подключения / Ноды). Tap opens [PanelSwitcherSheet].
 *
 * Visual reference: `bugs/design/3xui/project/components/shared.jsx` → `PanelChip`.
 */
@Composable
fun PanelChip(
    name: String,
    host: String,
    status: PanelStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(PaddingValues(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)),
    ) {
        StatusDot(status = status)
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(weight = 1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "· $host",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MonoFontFamily,
                fontSize = 11.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(weight = 1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun StatusDot(status: PanelStatus, modifier: Modifier = Modifier) {
    val color = status.color()
    val ring = if (status == PanelStatus.Up) color.copy(alpha = 0.18f) else Color.Transparent
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(ring, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
    }
}

@Preview
@Composable
private fun PanelChipPreview() {
    XuiPanelTheme {
        PanelChip(
            name = "Stockholm Edge",
            host = "panel.northwind.io",
            status = PanelStatus.Up,
            onClick = {},
        )
    }
}
