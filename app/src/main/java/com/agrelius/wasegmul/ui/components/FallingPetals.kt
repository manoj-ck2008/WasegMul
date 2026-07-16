package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import com.agrelius.wasegmul.ui.theme.EmeraldVibrant
import com.agrelius.wasegmul.ui.theme.SageGreen
import kotlin.math.sin

private data class PetalData(
    val startX: Float,
    val startY: Float,
    val speed: Float,
    val angle: Float,
    val rotationSpeed: Float,
    val color: Color,
    val xWavePhase: Float,
    val xWaveAmplitude: Float
)

@Composable
fun FallingPetals() {
    val infiniteTransition = rememberInfiniteTransition(label = "petals")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing)
        ),
        label = "time"
    )

    val petals = remember {
        List(20) { i ->
            PetalData(
                startX = (i * 137.508f % 1f),
                startY = (i * 73.137f % 1f),
                speed = 0.003f + (i * 0.0004f),
                angle = (i * 47f) % 360f,
                rotationSpeed = 0.5f + (i % 5) * 0.3f,
                color = if (i % 2 == 0) EmeraldVibrant.copy(alpha = 0.12f) else SageGreen.copy(alpha = 0.10f),
                xWavePhase = i * 0.7f,
                xWaveAmplitude = 0.02f + (i % 3) * 0.015f
            )
        }
    }

    val petalPath = remember { Path() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        petals.forEach { petal ->
            val rawY = (petal.startY + time * petal.speed)
            val currentY = (rawY % 1f) * size.height
            val xWave = sin(time * 0.8f + petal.xWavePhase) * petal.xWaveAmplitude
            val currentX = ((petal.startX + xWave) % 1f) * size.width
            val currentRotation = petal.angle + time * petal.rotationSpeed * 30f

            petalPath.reset()
            petalPath.moveTo(currentX, currentY - 15f)
            petalPath.quadraticTo(currentX + 10f, currentY, currentX, currentY + 15f)
            petalPath.quadraticTo(currentX - 10f, currentY, currentX, currentY - 15f)

            rotate(currentRotation, pivot = Offset(x = currentX, y = currentY)) {
                drawPath(
                    path = petalPath,
                    color = petal.color
                )
            }
        }
    }
}
