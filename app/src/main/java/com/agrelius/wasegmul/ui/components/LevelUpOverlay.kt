package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.gamification.EcoLevel
import kotlinx.coroutines.delay

/** Immutable confetti parameters; positions are a pure function of time. */
private data class ConfettiParams(
    val x0: Float,
    val fallSpeed: Float,
    val swayPhase: Float,
    val swayAmp: Float,
    val colorIndex: Int,
    val radiusDp: Float
)

@Composable
fun LevelUpOverlay(
    newLevel: EcoLevel,
    xpEarned: Int,
    onDismiss: () -> Unit,
    // Timed auto-dismiss is optional and pauses once the user interacts.
    autoDismiss: Boolean = true
) {
    var animationPhase by rememberSaveable { mutableIntStateOf(0) }
    var userInteracted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animationPhase = 1
        delay(1000)
        animationPhase = 2
        delay(1000)
        animationPhase = 3
        if (autoDismiss) {
            delay(2000)
            if (!userInteracted) onDismiss()
        }
    }

    val overlayAlpha by animateFloatAsState(
        targetValue = if (animationPhase in 1..3) 1f else 0f,
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

    fun dismissByUser() {
        userInteracted = true
        onDismiss()
    }

    if (overlayAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(overlayAlpha)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            if (animationPhase >= 2 && animationPhase <= 3) {
                ConfettiEffect()
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
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = newLevel.iconEmoji,
                        fontSize = 96.sp,
                        modifier = Modifier.scale(iconScale)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = stringResource(R.string.gamification_level_up),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.scale(textScale)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.alpha(detailsAlpha)
                    ) {
                        Text(
                            text = newLevel.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.gamification_xp_earned, xpEarned),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.gamification_congrats),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
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
}

@Composable
fun ConfettiEffect() {
    // Stateless time driver: the draw pass is a pure function of `time`,
    // so nothing is mutated during draw (no withFrameMillis mutation loop).
    val infiniteTransition = rememberInfiniteTransition(label = "confetti_time")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val particles = remember {
        List(36) { i ->
            ConfettiParams(
                x0 = (i * 0.137f) % 1f,
                fallSpeed = 0.35f + (i % 5) * 0.09f,
                swayPhase = i * 0.9f,
                swayAmp = 0.02f + (i % 3) * 0.012f,
                colorIndex = i % 4,
                radiusDp = 3f + (i % 4) * 1.5f
            )
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val error = MaterialTheme.colorScheme.error
    val palette = remember(primary, secondary, tertiary, error) {
        listOf(primary, secondary, tertiary, error)
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val yFrac = (time * p.fallSpeed) % 1f
            val xBase = p.x0 + kotlin.math.sin(time * 6.28f + p.swayPhase) * p.swayAmp
            val xFrac = ((xBase % 1f) + 1f) % 1f
            // Density-aware radius (dp, not px constants).
            val radiusPx = p.radiusDp.dp.toPx()
            drawCircle(
                color = palette[p.colorIndex],
                radius = radiusPx,
                center = Offset(xFrac * size.width, yFrac * size.height)
            )
        }
    }
}
