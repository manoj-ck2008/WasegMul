package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.agrelius.wasegmul.ui.theme.*
import kotlin.math.*

@Composable
fun AppLogo(
    size: Dp = 100.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val energyFlow by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "energy"
    )

    val nodePulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "node_pulse"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // BACKGROUND NEURAL FABRIC
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(sizePx / 2, sizePx / 2)
            
            // Hexagonal "Neural Shield" Glow
            for (i in 0..5) {
                val angle = i * 60f + (rotation * 0.3f)
                val rad = Math.toRadians(angle.toDouble())
                val outerDist = sizePx * 0.48f
                val x = center.x + (outerDist * cos(rad)).toFloat()
                val y = center.y + (outerDist * sin(rad)).toFloat()
                
                // Connecting filaments
                drawLine(
                    color = EmeraldVibrant.copy(alpha = 0.08f),
                    start = center,
                    end = Offset(x, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // THE "BIO-CORE" - Earth as a Neural Leaf
        Canvas(modifier = Modifier.size(size * 0.65f).rotate(if (animate) rotation * 0.1f else 0f)) {
            val w = sizePx * 0.65f
            val h = sizePx * 0.65f
            
            val leafPath = Path().apply {
                moveTo(w * 0.5f, 0f)
                quadraticTo(w * 0.95f, h * 0.3f, w * 0.95f, h * 0.65f)
                quadraticTo(w * 0.95f, h * 0.95f, w * 0.5f, h)
                quadraticTo(w * 0.05f, h * 0.95f, w * 0.05f, h * 0.65f)
                quadraticTo(w * 0.05f, h * 0.3f, w * 0.5f, 0f)
            }
            
            drawPath(
                path = leafPath,
                brush = Brush.linearGradient(
                    colors = listOf(EmeraldVibrant, MintAccent),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                alpha = 0.9f
            )
            
            // Central Neural "Vein" (Flowing data)
            val pulseY = h * energyFlow
            drawLine(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.6f),
                    1f to Color.Transparent,
                    startY = pulseY - 30f,
                    endY = pulseY + 30f
                ),
                start = Offset(w * 0.5f, max(0f, pulseY - 50f)),
                end = Offset(w * 0.5f, min(h, pulseY + 50f)),
                strokeWidth = 2.dp.toPx()
            )
        }

        // SYNCED NEURAL NODES
        for (i in 0..2) {
            val angle = i * 120f - (rotation * 0.6f)
            val dist = (sizePx / 2.3f)
            val rad = Math.toRadians(angle.toDouble())
            val x = (dist * cos(rad)).toFloat()
            val y = (dist * sin(rad)).toFloat()
            
            Canvas(modifier = Modifier.offset(
                x = with(density) { x.toDp() },
                y = with(density) { y.toDp() }
            )) {
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    alpha = nodePulse
                )
                drawCircle(
                    color = EmeraldVibrant,
                    radius = 8.dp.toPx() * nodePulse,
                    style = Stroke(1.dp.toPx()),
                    alpha = (1f - nodePulse) * 0.4f
                )
            }
        }
    }
}
