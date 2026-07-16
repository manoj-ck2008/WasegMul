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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val glassGradient = remember(surfaceVariant) {
        Brush.verticalGradient(
            colors = listOf(
                surfaceVariant.copy(alpha = 0.5f),
                surfaceVariant.copy(alpha = 0.2f)
            )
        )
    }
    val borderColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(glassGradient)
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = 0.1f),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(24.dp)
    ) {
        content()
    }
}
