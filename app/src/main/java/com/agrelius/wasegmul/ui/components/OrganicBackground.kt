package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private val leafColors = listOf(ForestGreen, OliveDeep, MossEarthy, SageGreen)

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

    val blob1X by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1x"
    )
    val blob1Y by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(28000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1y"
    )
    val blob2X by infiniteTransition.animateFloat(
        initialValue = 0.80f,
        targetValue = 0.20f,
        animationSpec = infiniteRepeatable(
            animation = tween(19000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2x"
    )
    val blob2Y by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2y"
    )
    val blob3X by infiniteTransition.animateFloat(
        initialValue = 0.50f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob3x"
    )
    val blob3Y by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob3y"
    )

    val leafPath = remember { Path() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val blobRadius = size.minDimension * 0.38f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    EmeraldVibrant.copy(alpha = 0.07f),
                    Color.Transparent
                ),
                center = Offset(blob1X * size.width, blob1Y * size.height),
                radius = blobRadius
            ),
            radius = blobRadius,
            center = Offset(blob1X * size.width, blob1Y * size.height)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SageGreen.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(blob2X * size.width, blob2Y * size.height),
                radius = blobRadius * 0.75f
            ),
            radius = blobRadius * 0.75f,
            center = Offset(blob2X * size.width, blob2Y * size.height)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    EmeraldVibrant.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = Offset(blob3X * size.width, blob3Y * size.height),
                radius = blobRadius * 0.6f
            ),
            radius = blobRadius * 0.6f,
            center = Offset(blob3X * size.width, blob3Y * size.height)
        )

        repeat(12) { i ->
            val (fx, fy) = leafPositions[i]
            val x = fx * size.width
            val y = fy * size.height
            val scale = leafScales[i]
            val color = leafColors[i % leafColors.size].copy(alpha = 0.05f)

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
