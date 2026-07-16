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

    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = SinEasing()),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    var leafPathCache by remember { mutableStateOf<Path?>(null) }

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // BACKGROUND NEURAL FABRIC — hexagonal shield
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(sizePx / 2, sizePx / 2)

            // Outer glow ring
            drawCircle(
                brush = Brush.radialGradient(
                    0f to EmeraldVibrant.copy(alpha = 0.06f),
                    0.7f to EmeraldVibrant.copy(alpha = 0.02f),
                    1f to Color.Transparent,
                    center = center,
                    radius = sizePx * 0.48f
                ),
                radius = sizePx * 0.48f,
                alpha = glowPulse
            )

            // Hexagonal neural filaments
            for (i in 0..5) {
                val angle = i * 60f + (rotation * 0.3f)
                val rad = Math.toRadians(angle.toDouble())
                val outerDist = sizePx * 0.46f
                val x = center.x + (outerDist * cos(rad)).toFloat()
                val y = center.y + (outerDist * sin(rad)).toFloat()

                drawLine(
                    color = EmeraldVibrant.copy(alpha = 0.10f),
                    start = center,
                    end = Offset(x, y),
                    strokeWidth = 1.dp.toPx()
                )

                // Small node at filament tip
                drawCircle(
                    color = EmeraldVibrant.copy(alpha = 0.25f * nodePulse),
                    radius = 1.5.dp.toPx(),
                    center = Offset(x, y)
                )
            }

            // Cross-filaments connecting adjacent nodes
            for (i in 0..5) {
                val a1 = i * 60f + (rotation * 0.3f)
                val a2 = (i + 1) * 60f + (rotation * 0.3f)
                val r1 = Math.toRadians(a1.toDouble())
                val r2 = Math.toRadians(a2.toDouble())
                val d = sizePx * 0.46f
                drawLine(
                    color = MintAccent.copy(alpha = 0.05f),
                    start = Offset(center.x + (d * cos(r1)).toFloat(), center.y + (d * sin(r1)).toFloat()),
                    end = Offset(center.x + (d * cos(r2)).toFloat(), center.y + (d * sin(r2)).toFloat()),
                    strokeWidth = 0.8.dp.toPx()
                )
            }
        }

        // THE "BIO-CORE" — Neural Leaf
        Canvas(modifier = Modifier.size(size * 0.65f).rotate(if (animate) rotation * 0.1f else 0f)) {
            val w = sizePx * 0.65f
            val h = sizePx * 0.65f

            val leafPath = leafPathCache ?: Path().also { leafPathCache = it }
            leafPath.reset()
            leafPath.apply {
                moveTo(w * 0.5f, 0f)
                quadraticTo(w * 0.95f, h * 0.3f, w * 0.95f, h * 0.65f)
                quadraticTo(w * 0.95f, h * 0.95f, w * 0.5f, h)
                quadraticTo(w * 0.05f, h * 0.95f, w * 0.05f, h * 0.65f)
                quadraticTo(w * 0.05f, h * 0.3f, w * 0.5f, 0f)
            }

            // Leaf fill — richer gradient
            drawPath(
                path = leafPath,
                brush = Brush.linearGradient(
                    colors = listOf(MintAccent, EmeraldVibrant, DeepCharcoal.copy(alpha = 0.3f)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                alpha = 0.92f
            )

            // Leaf outline — subtle border
            drawPath(
                path = leafPath,
                color = Color.White.copy(alpha = 0.12f),
                style = Stroke(width = 0.8.dp.toPx())
            )

            // Central vein — pulsing data flow
            val pulseY = h * energyFlow
            drawLine(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.4f to Color.White.copy(alpha = 0.7f),
                    0.5f to EmeraldVibrant.copy(alpha = 0.9f),
                    0.6f to Color.White.copy(alpha = 0.7f),
                    1f to Color.Transparent,
                    startY = pulseY - 40f,
                    endY = pulseY + 40f
                ),
                start = Offset(w * 0.5f, max(0f, pulseY - 60f)),
                end = Offset(w * 0.5f, min(h, pulseY + 60f)),
                strokeWidth = 2.dp.toPx()
            )

            // Side veins — subtle branching
            for (i in 1..3) {
                val vy = h * (i * 0.25f)
                val spread = w * 0.18f * (1f - i * 0.15f)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(w * 0.5f, vy),
                    end = Offset(w * 0.5f + spread, vy + h * 0.08f),
                    strokeWidth = 0.6.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(w * 0.5f, vy),
                    end = Offset(w * 0.5f - spread, vy + h * 0.08f),
                    strokeWidth = 0.6.dp.toPx()
                )
            }
        }

        // ORBITING NEURAL NODES — 5 nodes for richer network feel
        for (i in 0..4) {
            val angle = i * 72f - (rotation * 0.6f)
            val dist = (sizePx / 2.3f)
            val rad = Math.toRadians(angle.toDouble())
            val x = (dist * cos(rad)).toFloat()
            val y = (dist * sin(rad)).toFloat()

            Canvas(modifier = Modifier.offset(
                x = with(density) { x.toDp() },
                y = with(density) { y.toDp() }
            )) {
                // Outer ring
                drawCircle(
                    color = EmeraldVibrant,
                    radius = 9.dp.toPx() * nodePulse,
                    style = Stroke(1.dp.toPx()),
                    alpha = (1f - nodePulse) * 0.35f
                )
                // Inner core
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    alpha = nodePulse
                )
                // Glow
                drawCircle(
                    color = MintAccent,
                    radius = 5.dp.toPx(),
                    alpha = nodePulse * 0.2f
                )
            }
        }
    }
}

/** Smooth sine easing for organic pulsing. */
private class SinEasing : Easing {
    override fun transform(fraction: Float): Float =
        ((sin(fraction * PI) / PI) + 0.5).toFloat().coerceIn(0f, 1f)
}
