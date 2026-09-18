package com.agrelius.wasegmul.ui.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.R
import com.agrelius.wasegmul.ui.components.AppLogo
import com.agrelius.wasegmul.ui.components.VisionImagery
import com.agrelius.wasegmul.ui.components.FallingPetals
import com.agrelius.wasegmul.ui.components.rememberReduceMotion
import com.agrelius.wasegmul.utils.EcoThoughts
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // Rotation-safe: stage/navigation/thought survive configuration change
    // (no restart, no replay, no reshuffled quote).
    var stage by rememberSaveable { mutableIntStateOf(0) }
    val currentThought = rememberSaveable { EcoThoughts.getRandom() }
    var navigated by rememberSaveable { mutableStateOf(false) }
    val reduceMotion = rememberReduceMotion()

    val fullTitle = stringResource(R.string.app_name)
    var displayedTitle by rememberSaveable { mutableStateOf("") }

    fun go() {
        if (!navigated) {
            navigated = true
            onTimeout()
        }
    }

    val visionAlpha by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = tween(1200),
        label = "vision_alpha"
    )

    val logoScale by animateFloatAsState(
        targetValue = when(stage) {
            0 -> 0.0f
            1 -> 1.0f
            else -> 0.85f
        },
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow, dampingRatio = Spring.DampingRatioHighBouncy),
        label = "logo_scale"
    )

    // Clamped offset so compact/landscape screens never clip the logo.
    val logoOffset by animateDpAsState(
        targetValue = when(stage) {
            0 -> 0.dp
            1 -> 0.dp
            else -> (-48).dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "logo_offset"
    )

    LaunchedEffect(Unit) {
        if (navigated) return@LaunchedEffect
        // Total forced delay stays under ~2s; Skip is always visible.
        delay(250)
        stage = 1 // Logo emerges
        if (!reduceMotion) {
            fullTitle.forEachIndexed { index, _ ->
                displayedTitle = fullTitle.take(index + 1)
                delay(35)
            }
        } else {
            displayedTitle = fullTitle
        }
        delay(200)
        stage = 2 // Vision & Petals Bloom
        delay(900)
        go()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Theme-aware scrim (was forced black cutting into light Home).
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Organic Vision Layer
        VisionImagery(alpha = visionAlpha)

        // Fluid Nature Elements
        if (stage >= 2) {
            FallingPetals()
        }

        // Scrollable + compact-safe column (small screens no longer clip).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp)
        ) {
            // Central Neural Logo
            Box(
                modifier = Modifier
                    .offset(y = logoOffset)
                    .scale(logoScale)
                    .alpha(if (stage >= 1) 1f else 0f),
                contentAlignment = Alignment.Center
            ) {
                AppLogo(size = 160.dp, animate = !reduceMotion)

                if (stage >= 2 && !reduceMotion) {
                    CircularRsAnimation()
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Cinematic Title
            Box(modifier = Modifier.heightIn(min = 96.dp), contentAlignment = Alignment.TopCenter) {
                if (displayedTitle.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = displayedTitle,
                            style = MaterialTheme.typography.displaySmall.copy(
                                letterSpacing = 8.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        AnimatedVisibility(
                            visible = stage >= 2,
                            enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { 40 }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.home_subtitle),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.alpha(0.8f)
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                Text(
                                    text = "\"$currentThought\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    lineHeight = 28.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Visible Skip control (Role.Button via Button, labelled).
            Button(
                onClick = { go() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(stringResource(R.string.splash_skip))
            }
        }
    }
}

@Composable
fun CircularRsAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "rs")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
        label = "rotation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.size(240.dp).rotate(rotation)) {
        val strokeWidth = 2.dp.toPx()
        for (i in 0..2) {
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to primaryColor.copy(alpha = 0f),
                    0.5f to primaryColor.copy(alpha = 0.3f),
                    1.0f to primaryColor.copy(alpha = 0f)
                ),
                startAngle = i * 120f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
