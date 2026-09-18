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
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.ui.theme.EmeraldVibrant
import com.agrelius.wasegmul.ui.theme.SageGreen
import kotlin.math.cos
import kotlin.math.sin

private data class FallingPetal(
    val startX: Float,
    val startY: Float,
    val fallSpeed: Float,
    val rotationSpeed: Float,
    val scale: Float,
    val color: Color,
    val xWavePhase: Float,
    val xWaveAmplitude: Float,
    val delayFraction: Float,
    val leafType: Int,
    val initialRotation: Float
)

@Composable
fun FallingPetals(
    modifier: Modifier = Modifier,
    count: Int = 18,
    enabled: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val powerManager = remember {
        context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    }
    val app = remember(context) {
        context.applicationContext as? com.agrelius.wasegmul.WasegMulApp
    }
    val reduceAnimations by app?.settingsManager?.reduceAnimations?.collectAsState(initial = false)
        ?: remember { mutableStateOf(false) }
    val isPowerSave = powerManager?.isPowerSaveMode == true
    // Battery-saver, reduced-motion, or caller disables decoration: render
    // nothing (zero GPU cost).
    if (!enabled || isPowerSave || reduceAnimations) return

    val renderCount = if (count > 12) 12 else count
    val infiniteTransition = rememberInfiniteTransition(label = "petals")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing)
        ),
        label = "time"
    )

    val petals = remember(renderCount) {
        (0 until renderCount).map { i ->
            FallingPetal(
                startX = (i * 0.053f + (i % 7) * 0.11f) % 1f,
                startY = -0.1f - (i * 0.06f),
                fallSpeed = 0.04f + (i % 4) * 0.015f,
                rotationSpeed = 0.15f + (i % 3) * 0.1f,
                scale = 0.7f + (i % 5) * 0.25f,
                color = when (i % 3) {
                    0 -> EmeraldVibrant.copy(alpha = 0.18f)
                    1 -> SageGreen.copy(alpha = 0.14f)
                    else -> EmeraldVibrant.copy(alpha = 0.10f)
                },
                xWavePhase = i * 1.2f,
                xWaveAmplitude = 0.015f + (i % 4) * 0.008f,
                delayFraction = (i * 0.04f) % 0.6f,
                leafType = i % 3,
                initialRotation = (i * 47f) % 360f
            )
        }
    }

    val petalPath = remember { Path() }

    // Honor the caller's modifier (previously ignored in favor of fillMaxSize).
    Canvas(modifier = modifier.fillMaxSize()) {
        val baseHeight = 12.dp.toPx()
        val baseWidth = 8.dp.toPx()

        petals.forEach { petal ->
            val adjustedTime = (time - petal.delayFraction).coerceIn(0f, 1f)
            if (adjustedTime <= 0f) return@forEach

            val fadeIn = (adjustedTime / 0.15f).coerceIn(0f, 1f)
            val fadeOut = if (adjustedTime > 0.85f) (1f - adjustedTime) / 0.15f else 1f
            val alpha = fadeIn * fadeOut

            val rawY = petal.startY + adjustedTime * petal.fallSpeed * 30f
            val currentY = ((rawY % 1.4f) - 0.2f) * size.height

            val xDrift = sin(adjustedTime * 6.28f * 2f + petal.xWavePhase) * petal.xWaveAmplitude
            val windGust = cos(adjustedTime * 6.28f * 0.7f + petal.xWavePhase * 0.5f) * 0.008f
            val rawX = petal.startX + xDrift + windGust * adjustedTime
            val currentX = (((rawX % 1f) + 1f) % 1f) * size.width

            val currentRotation = petal.initialRotation + adjustedTime * petal.rotationSpeed * 360f

            val leafHeight = baseHeight * petal.scale
            val leafWidth = baseWidth * petal.scale

            petalPath.reset()
            when (petal.leafType) {
                0 -> {
                    petalPath.moveTo(currentX, currentY - leafHeight)
                    petalPath.quadraticTo(currentX + leafWidth, currentY, currentX, currentY + leafHeight)
                    petalPath.quadraticTo(currentX - leafWidth, currentY, currentX, currentY - leafHeight)
                }
                1 -> {
                    petalPath.moveTo(currentX, currentY - leafHeight * 0.8f)
                    petalPath.cubicTo(
                        currentX + leafWidth * 1.2f, currentY - leafHeight * 0.3f,
                        currentX + leafWidth * 0.8f, currentY + leafHeight * 0.5f,
                        currentX, currentY + leafHeight
                    )
                    petalPath.cubicTo(
                        currentX - leafWidth * 0.8f, currentY + leafHeight * 0.5f,
                        currentX - leafWidth * 1.2f, currentY - leafHeight * 0.3f,
                        currentX, currentY - leafHeight * 0.8f
                    )
                }
                else -> {
                    petalPath.moveTo(currentX, currentY - leafHeight * 0.6f)
                    petalPath.quadraticTo(currentX + leafWidth * 0.7f, currentY, currentX, currentY + leafHeight * 0.6f)
                    petalPath.quadraticTo(currentX - leafWidth * 0.7f, currentY, currentX, currentY - leafHeight * 0.6f)
                }
            }

            rotate(currentRotation, pivot = Offset(x = currentX, y = currentY)) {
                drawPath(
                    path = petalPath,
                    color = petal.color.copy(alpha = petal.color.alpha * alpha)
                )
            }
        }
    }
}
