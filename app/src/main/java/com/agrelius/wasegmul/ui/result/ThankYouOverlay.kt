package com.agrelius.wasegmul.ui.result

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class LeafParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var rotationSpeed: Float,
    var size: Float,
    var alpha: Float,
    val color: Color
)

@Composable
fun ThankYouOverlay(
    xpEarned: Int,
    co2PreventedGrams: Double,
    waterSavedMl: Double,
    onDismiss: () -> Unit
) {
    var phase by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(true) }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "overlay_alpha",
        finishedListener = { if (!visible) onDismiss() }
    )

    val iconScale by animateFloatAsState(
        targetValue = when (phase) {
            0 -> 0f
            1 -> 1.3f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "icon_scale"
    )

    val glowRadius by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "glow_radius"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "glow_pulse")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse_alpha"
    )

    // Leaf particles state
    val particles = remember {
        List(30) {
            LeafParticle(
                x = 0.5f,
                y = 0.4f,
                vx = (Random.nextFloat() - 0.5f) * 0.015f,
                vy = (Random.nextFloat() - 0.3f) * 0.012f,
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 8f,
                size = Random.nextFloat() * 12f + 6f,
                alpha = Random.nextFloat() * 0.5f + 0.5f,
                color = listOf(
                    Color(0xFF00FF94),
                    Color(0xFF2ECC71),
                    Color(0xFFA8C69F),
                    Color(0xFF32CD32),
                    Color(0xFFFFD700),
                    Color(0xFFFFFFFF)
                ).random()
            )
        }.toMutableList()
    }

    // Animation sequencing
    LaunchedEffect(Unit) {
        delay(100)
        phase = 1 // Glow + icon
        delay(800)
        phase = 2 // Text appears
        delay(1200)
        phase = 3 // Stats appear
        delay(1200)
        visible = false // Start fade out
    }

    if (overlayAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(overlayAlpha)
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    visible = false
                },
            contentAlignment = Alignment.Center
        ) {
            // Radial glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerX = size.width / 2
                val centerY = size.height * 0.38f
                val maxRadius = size.width * 0.6f * glowRadius

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00FF94).copy(alpha = 0.25f * glowPulse),
                            Color(0xFF00FF94).copy(alpha = 0.08f * glowPulse),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = maxRadius
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius
                )
            }

            // Leaf particles
            if (phase >= 2) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    particles.forEach { p ->
                        p.x += p.vx
                        p.y += p.vy
                        p.vy += 0.0002f // gravity
                        p.rotation += p.rotationSpeed
                        p.alpha = (p.alpha - 0.003f).coerceAtLeast(0f)

                        if (p.alpha > 0f) {
                            rotate(p.rotation, pivot = Offset(p.x * size.width, p.y * size.height)) {
                                drawOval(
                                    color = p.color.copy(alpha = p.alpha),
                                    topLeft = Offset(
                                        p.x * size.width - p.size / 2,
                                        p.y * size.height - p.size / 3
                                    ),
                                    size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Spacer(modifier = Modifier.height(80.dp))

                // Globe/Leaf icon
                Box(
                    modifier = Modifier.scale(iconScale),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\uD83C\uDF0D",
                        fontSize = 72.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // "Thank You!" text
                AnimatedVisibility(
                    visible = phase >= 2,
                    enter = fadeIn(tween(600)) + scaleIn(
                        tween(600),
                        initialScale = 0.5f
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Thank You!",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00FF94),
                            textAlign = TextAlign.Center,
                            letterSpacing = 4.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "You just saved the planet a little more",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 24.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Stats pills
                AnimatedVisibility(
                    visible = phase >= 3,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { 40 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // XP earned
                        if (xpEarned > 0) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color(0xFF00FF94).copy(alpha = 0.15f),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "+$xpEarned XP",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF00FF94)
                                )
                            }
                        }

                        // Eco stat pills row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (co2PreventedGrams > 0.1) {
                                EcoStatPill(
                                    label = "CO\u2082",
                                    value = "-${formatGrams(co2PreventedGrams)}",
                                    color = Color(0xFF2ECC71)
                                )
                            }
                            if (waterSavedMl > 0.1) {
                                EcoStatPill(
                                    label = "Water",
                                    value = "+${formatMl(waterSavedMl)}",
                                    color = Color(0xFF3498DB)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))

                AnimatedVisibility(
                    visible = phase >= 3,
                    enter = fadeIn(tween(800, delayMillis = 300))
                ) {
                    Text(
                        text = "Tap anywhere to continue",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EcoStatPill(
    label: String,
    value: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .background(
                color.copy(alpha = 0.12f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private fun formatGrams(grams: Double): String {
    return if (grams >= 1000) {
        "${"%.2f".format(java.util.Locale.US, grams / 1000)}kg"
    } else {
        "${"%.1f".format(java.util.Locale.US, grams)}g"
    }
}

private fun formatMl(ml: Double): String {
    return if (ml >= 1000) {
        "${"%.1f".format(java.util.Locale.US, ml / 1000)}L"
    } else {
        "${"%.0f".format(java.util.Locale.US, ml)}mL"
    }
}
