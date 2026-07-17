package com.agrelius.wasegmul.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.ui.theme.LocalGlassColors

@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassColors = LocalGlassColors.current
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val glassGradient = remember(surfaceVariant, glassColors) {
        Brush.verticalGradient(
            colors = listOf(
                glassColors.surface,
                surfaceVariant.copy(alpha = 0.15f),
                Color.Transparent
            )
        )
    }
    val borderBrush = remember(glassColors) {
        Brush.verticalGradient(
            colors = listOf(
                glassColors.border,
                glassColors.border.copy(alpha = 0.03f),
                glassColors.border.copy(alpha = 0.1f)
            )
        )
    }
    val shape = remember { RoundedCornerShape(28.dp) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape, ambientColor = glassColors.border.copy(alpha = 0.1f))
            .clip(shape)
            .background(glassGradient)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            )
            .padding(24.dp)
    ) {
        content()
    }
}
