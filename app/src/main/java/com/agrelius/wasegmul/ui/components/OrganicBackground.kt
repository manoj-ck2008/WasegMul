package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import com.agrelius.wasegmul.ui.theme.*

private val leafPositions = listOf(
    0.12f to 0.18f,
    0.73f to 0.07f,
    0.35f to 0.55f,
    0.89f to 0.33f,
    0.50f to 0.80f,
    0.18f to 0.62f,
    0.65f to 0.91f,
    0.42f to 0.25f,
    0.95f to 0.72f,
    0.28f to 0.40f,
    0.80f to 0.15f,
    0.55f to 0.68f
)

private val leafScales = listOf(1.2f, 0.8f, 1.5f, 0.6f, 1.0f, 1.3f, 0.7f, 1.1f, 0.9f, 1.4f, 0.5f, 1.6f)

@Composable
fun OrganicBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "organic")

    val windSway by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    val leafPath = remember { Path() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val leafColors = listOf(ForestGreen, OliveDeep, MossEarthy, SageGreen)

        repeat(12) { i ->
            val (fx, fy) = leafPositions[i]
            val x = fx * size.width
            val y = fy * size.height
            val scale = leafScales[i]
            val color = leafColors[i % leafColors.size].copy(alpha = 0.08f)

            val w = 40f * scale
            val h = 70f * scale
            leafPath.reset()
            leafPath.moveTo(x, y - h / 2)
            leafPath.quadraticTo(x + w, y, x, y + h / 2)
            leafPath.quadraticTo(x - w, y, x, y - h / 2)

            rotate(degrees = (i * 30f) + windSway, pivot = Offset(x, y)) {
                drawPath(leafPath, color)
            }
        }
    }
}
