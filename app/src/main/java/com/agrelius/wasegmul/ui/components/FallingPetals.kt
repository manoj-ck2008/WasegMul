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

data class Petal(
    var x: Float,
    var y: Float,
    var speed: Float,
    var angle: Float,
    var rotationSpeed: Float,
    var color: Color
)

@Composable
fun FallingPetals() {
    val infiniteTransition = rememberInfiniteTransition(label = "petals")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(20000, easing = LinearEasing)),
        label = "time"
    )

    val petals = remember {
        List(20) {
            Petal(
                x = (0..1000).random().toFloat() / 1000f,
                y = (0..1000).random().toFloat() / 1000f,
                speed = 0.001f + (0..1000).random().toFloat() / 500000f,
                angle = (0..360).random().toFloat(),
                rotationSpeed = (1..5).random().toFloat(),
                color = if ((0..1).random() == 0) EmeraldVibrant.copy(alpha = 0.15f) else SageGreen.copy(alpha = 0.15f)
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        petals.forEach { petal ->
            val currentY = ((petal.y + time * petal.speed) % 1.0f) * size.height
            val currentX = ((petal.x + sin(time * 0.01f + petal.x) * 0.05f) % 1.0f) * size.width
            val currentRotation = petal.angle + time * petal.rotationSpeed

            rotate(currentRotation, pivot = Offset(currentX, currentY)) {
                drawPath(
                    path = Path().apply {
                        moveTo(currentX, currentY - 15f)
                        quadraticTo(currentX + 10f, currentY, currentX, currentY + 15f)
                        quadraticTo(currentX - 10f, currentY, currentX, currentY - 15f)
                    },
                    color = petal.color
                )
            }
        }
    }
}
