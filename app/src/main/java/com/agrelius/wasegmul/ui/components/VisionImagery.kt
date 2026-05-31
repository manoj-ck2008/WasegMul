package com.agrelius.wasegmul.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.agrelius.wasegmul.ui.theme.EmeraldVibrant
import com.agrelius.wasegmul.ui.theme.ForestGreen
import com.agrelius.wasegmul.ui.theme.MossEarthy

@Composable
fun VisionImagery(alpha: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Deep organic gradients
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    ForestGreen.copy(alpha = 0.2f * alpha),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.8f, size.height * 0.2f),
                radius = size.width
            )
        )
        
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    MossEarthy.copy(alpha = 0.15f * alpha),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.2f, size.height * 0.8f),
                radius = size.width
            )
        )
    }
}
