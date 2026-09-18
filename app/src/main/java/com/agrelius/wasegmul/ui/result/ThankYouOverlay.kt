package com.agrelius.wasegmul.ui.result

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.R
import kotlinx.coroutines.delay
import kotlin.math.sin

/** Immutable leaf parameters; positions are a pure function of [progress]. */
private data class LeafParams(
    val x0: Float,
    val y0: Float,
    val driftX: Float,
    val fallDistance: Float,
    val rotation0: Float,
    val rotationSpeed: Float,
    val size: Float,
    val colorIndex: Int
)

@Composable
fun ThankYouOverlay(
    xpEarned: Int,
    co2PreventedGrams: Double,
    waterSavedMl: Double,
    onDismiss: () -> Unit,
    // Timed auto-dismiss is optional and pauses once the user interacts
    // (WCAG timed-content): the visible Continue button always works.
    autoDismiss: Boolean = true
) {
    var phase by rememberSaveable { mutableIntStateOf(0) }
    var visible by rememberSaveable { mutableStateOf(true) }
    var userInteracted by rememberSaveable { mutableStateOf(false) }

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

    // Stateless particles: immutable params + one-shot progress drive the
    // draw pass. Nothing is mutated inside drawScope (no mutation-in-draw).
    val leafParams = remember {
        List(24) { i ->
            LeafParams(
                x0 = (i * 0.041f + (i % 5) * 0.17f) % 1f,
                y0 = 0.30f + (i % 7) * 0.02f,
                driftX = sin(i * 1.7f) * 0.08f,
                fallDistance = 0.25f + (i % 4) * 0.08f,
                rotation0 = (i * 47f) % 360f,
                rotationSpeed = 40f + (i % 3) * 30f,
                size = 6f + (i % 4) * 3f,
                colorIndex = i % 4
            )
        }
    }
    val particleProgress by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(3500, easing = LinearEasing),
        label = "particle_progress"
    )

    // Animation sequencing (rotation-safe via Saveable phase).
    LaunchedEffect(Unit) {
        delay(100)
        phase = 1 // Glow + icon
        delay(800)
        phase = 2 // Text appears
        delay(1200)
        phase = 3 // Stats appear
        if (autoDismiss) {
            delay(2500)
            // Pause the timed dismiss once the user has taken over.
            if (!userInteracted) visible = false
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val leafColors = remember(primary, secondary, tertiary) {
        listOf(primary, secondary, tertiary, primary)
    }

    fun dismissByUser() {
        userInteracted = true
        visible = false
    }

    if (overlayAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(overlayAlpha)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)),
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
                            primary.copy(alpha = 0.25f * glowPulse),
                            primary.copy(alpha = 0.08f * glowPulse),
                            Color.Transparent
                        ),
                        center = Offset(centerX, centerY),
                        radius = maxRadius
                    ),
                    center = Offset(centerX, centerY),
                    radius = maxRadius
                )
            }

            // Leaf particles (pure function of progress — no state mutation).
            if (phase >= 2 && particleProgress > 0f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fade = (1f - particleProgress).coerceIn(0f, 1f)
                    leafParams.forEach { p ->
                        val x = (p.x0 + p.driftX * particleProgress) * size.width
                        val y = (p.y0 + p.fallDistance * particleProgress) * size.height
                        val rotation = p.rotation0 + p.rotationSpeed * particleProgress
                        rotate(rotation, pivot = Offset(x, y)) {
                            drawOval(
                                color = leafColors[p.colorIndex].copy(alpha = 0.7f * fade + 0.1f),
                                topLeft = Offset(x - p.size / 2, y - p.size / 3),
                                size = androidx.compose.ui.geometry.Size(p.size, p.size * 0.6f)
                            )
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 28.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Globe/Leaf icon
                    Box(
                        modifier = Modifier.scale(iconScale),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

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
                                text = stringResource(R.string.overlay_thank_you),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                letterSpacing = 4.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.overlay_saved_planet),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                fontStyle = FontStyle.Italic,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

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
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 24.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.gamification_xp_earned, xpEarned),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
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
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (waterSavedMl > 0.1) {
                                    EcoStatPill(
                                        label = stringResource(R.string.impact_metric_water),
                                        value = "+${formatMl(waterSavedMl)}",
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Visible dismiss control (Role.Button via Button).
                    Button(
                        onClick = { dismissByUser() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.overlay_continue),
                            fontWeight = FontWeight.Bold
                        )
                    }
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
        "${"%.2f".format(java.util.Locale.ROOT, grams / 1000)}kg"
    } else {
        "${"%.1f".format(java.util.Locale.ROOT, grams)}g"
    }
}

private fun formatMl(ml: Double): String {
    return if (ml >= 1000) {
        "${"%.1f".format(java.util.Locale.ROOT, ml / 1000)}L"
    } else {
        "${"%.0f".format(java.util.Locale.ROOT, ml)}mL"
    }
}
