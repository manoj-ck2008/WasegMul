package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.gamification.EcoLevel
import kotlinx.coroutines.delay
import kotlin.random.Random

private val EmeraldVibrant = Color(0xFF00FF94)

@Composable
fun LevelUpOverlay(
    newLevel: EcoLevel,
    xpEarned: Int,
    onDismiss: () -> Unit
) {
    var animationPhase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        animationPhase = 1
        delay(1000)
        animationPhase = 2
        delay(1000)
        animationPhase = 3
        delay(1000)
        animationPhase = 4
        delay(1000)
        onDismiss()
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (animationPhase in 1..3) 0.8f else 0f,
        animationSpec = tween(500),
        label = "overlayAlpha"
    )

    val iconScale by animateFloatAsState(
        targetValue = when (animationPhase) {
            0 -> 0f
            1, 2, 3 -> 1.0f
            else -> 0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "iconScale"
    )

    val textScale by animateFloatAsState(
        targetValue = if (animationPhase >= 2 && animationPhase <= 3) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "textScale"
    )

    val detailsAlpha by animateFloatAsState(
        targetValue = if (animationPhase == 3) 1f else 0f,
        animationSpec = tween(500),
        label = "detailsAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = overlayAlpha))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        if (animationPhase >= 2 && animationPhase <= 3) {
            ConfettiEffect()
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = newLevel.iconEmoji,
                fontSize = 120.sp,
                modifier = Modifier.scale(iconScale)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "LEVEL UP!",
                color = EmeraldVibrant,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.scale(textScale)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(detailsAlpha)
            ) {
                Text(
                    text = newLevel.name,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "+$xpEarned XP",
                    color = EmeraldVibrant,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Thank you for protecting our planet",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun ConfettiEffect() {
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { tick = it }
        }
    }

    val particles = remember {
        List(40) {
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * 0.5f + 0.25f,
                vx = (Random.nextFloat() - 0.5f) * 0.02f,
                vy = -Random.nextFloat() * 0.05f - 0.02f,
                color = listOf(EmeraldVibrant, Color(0xFFFFD700), Color.White, Color.Green).random(),
                size = Random.nextFloat() * 20f + 10f
            )
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        particles.forEach { particle ->
            particle.x += particle.vx
            particle.y += particle.vy
            particle.vy += 0.001f // gravity

            drawCircle(
                color = particle.color,
                radius = particle.size,
                center = Offset(particle.x * width, particle.y * height)
            )
        }
    }
}

class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float
)
