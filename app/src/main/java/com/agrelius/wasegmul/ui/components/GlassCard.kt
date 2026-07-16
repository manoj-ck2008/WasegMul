package com.agrelius.wasegmul.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.ui.theme.DarkGlassHighlight
import com.agrelius.wasegmul.ui.theme.LocalGlassColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassColors = LocalGlassColors.current
    val bgBrush = remember(glassColors) {
        Brush.verticalGradient(
            colors = listOf(
                glassColors.surface,
                glassColors.surface.copy(alpha = glassColors.surface.alpha * 0.4f),
                Color.Transparent
            )
        )
    }
    val borderBrush = remember(glassColors) {
        Brush.verticalGradient(
            colors = listOf(
                glassColors.border,
                glassColors.border.copy(alpha = 0.03f),
                glassColors.border.copy(alpha = 0.08f)
            )
        )
    }
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(cornerRadius), ambientColor = glassColors.border.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgBrush)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(20.dp)
    ) {
        Column {
            content()
        }
    }
}
