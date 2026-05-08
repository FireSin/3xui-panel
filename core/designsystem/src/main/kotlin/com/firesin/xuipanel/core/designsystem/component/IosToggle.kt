package com.firesin.xuipanel.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val TrackWidth = 51.dp
private val TrackHeight = 31.dp
private val KnobSize = 27.dp
private val KnobPadding = 2.dp
private val TrackShape = RoundedCornerShape(16.dp)
private const val AnimDurationMs = 200

@Composable
fun IosToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - KnobSize - KnobPadding else KnobPadding,
        animationSpec = tween(AnimDurationMs),
        label = "knob",
    )
    val trackColor = if (checked) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outline

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .width(TrackWidth)
            .height(TrackHeight)
            .background(
                color = if (enabled) trackColor else trackColor.copy(alpha = 0.4f),
                shape = TrackShape,
            )
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(KnobSize)
                .padding(KnobPadding / 2)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .background(Color.White, shape = CircleShape),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IosToggleOnPreview() {
    IosToggle(checked = true, onCheckedChange = {})
}

@Preview(showBackground = true)
@Composable
private fun IosToggleOffPreview() {
    IosToggle(checked = false, onCheckedChange = {})
}
