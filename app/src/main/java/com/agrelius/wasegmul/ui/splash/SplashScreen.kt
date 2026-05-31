package com.agrelius.wasegmul.ui.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.WasegMulApp
import com.agrelius.wasegmul.ui.components.AppLogo
import com.agrelius.wasegmul.ui.components.VisionImagery
import com.agrelius.wasegmul.ui.components.FallingPetals
import com.agrelius.wasegmul.utils.EcoThoughts
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WasegMulApp
    val soundManager = app.soundManager
    
    var stage by remember { mutableIntStateOf(0) }
    val currentThought = remember { EcoThoughts.getRandom() }
    
    val fullTitle = "WasegMul"
    var displayedTitle by remember { mutableStateOf("") }
    
    val visionAlpha by animateFloatAsState(
        targetValue = if (stage >= 2) 1f else 0f,
        animationSpec = tween(4000),
        label = "vision_alpha"
    )

    val logoScale by animateFloatAsState(
        targetValue = when(stage) {
            0 -> 0.0f
            1 -> 1.0f
            else -> 0.7f
        },
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow, dampingRatio = Spring.DampingRatioHighBouncy),
        label = "logo_scale"
    )

    val logoOffset by animateDpAsState(
        targetValue = when(stage) {
            0 -> 0.dp
            1 -> 0.dp
            else -> (-140).dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "logo_offset"
    )

    LaunchedEffect(Unit) {
        soundManager.playStartupMusic()
        delay(1500)
        stage = 1 // Logo emerges from darkness
        
        delay(1200)
        // Cinematic Fluid Title Writing
        fullTitle.forEachIndexed { index, _ ->
            displayedTitle = fullTitle.take(index + 1)
            delay(250)
        }
        
        delay(800)
        stage = 2 // Vision & Petals Bloom
        
        delay(5000)
        soundManager.stopMusic()
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Organic Vision Layer
        VisionImagery(alpha = visionAlpha)
        
        // Fluid Nature Elements
        if (stage >= 2) {
            FallingPetals()
        }

        // Center aligned column for content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
        ) {
            // Central Neural Logo
            Box(
                modifier = Modifier
                    .offset(y = logoOffset)
                    .scale(logoScale)
                    .alpha(if (stage >= 1) 1f else 0f),
                contentAlignment = Alignment.Center
            ) {
                AppLogo(size = 200.dp)
                
                if (stage >= 2) {
                    CircularRsAnimation()
                }
            }
            
            // Fixed spacer to maintain layout during animations
            Spacer(modifier = Modifier.height(100.dp))

            // Cinematic Title
            Box(modifier = Modifier.height(120.dp), contentAlignment = Alignment.TopCenter) {
                if (displayedTitle.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = displayedTitle,
                            style = MaterialTheme.typography.displaySmall.copy(
                                letterSpacing = 12.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        AnimatedVisibility(
                            visible = stage >= 2,
                            enter = fadeIn(tween(2000)) + slideInVertically(tween(2000)) { 40 }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Waste Segregation Multi-model AI",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.alpha(0.8f)
                                )
                                
                                Spacer(modifier = Modifier.height(80.dp))
                                
                                Text(
                                    text = "\"$currentThought\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    lineHeight = 28.sp
                                )
                            }
                        }
                    }
                }
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

    Canvas(modifier = Modifier.size(280.dp).rotate(rotation)) {
        val strokeWidth = 2.dp.toPx()
        for (i in 0..2) {
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to Color(0xFF2ECC71).copy(alpha = 0f),
                    0.5f to Color(0xFF2ECC71).copy(alpha = 0.3f),
                    1.0f to Color(0xFF2ECC71).copy(alpha = 0f)
                ),
                startAngle = i * 120f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}
