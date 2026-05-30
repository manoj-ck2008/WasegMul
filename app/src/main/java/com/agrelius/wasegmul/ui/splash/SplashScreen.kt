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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.agrelius.wasegmul.WasegMulApp
import com.agrelius.wasegmul.ui.components.AppLogo
import com.agrelius.wasegmul.utils.EcoThoughts
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    val soundManager = (context.applicationContext as WasegMulApp).soundManager
    var stage by remember { mutableIntStateOf(0) }
    val currentThought = remember { EcoThoughts.getRandom() }
    
    val logoScale by animateFloatAsState(
        targetValue = when(stage) {
            0 -> 1.2f
            1 -> 0.8f
            else -> 0.6f
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "logo_scale"
    )

    val logoOffset by animateDpAsState(
        targetValue = when(stage) {
            0 -> 0.dp
            1 -> (-100).dp
            else -> (-140).dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "logo_offset"
    )

    LaunchedEffect(Unit) {
        delay(1000)
        stage = 1 // Name appears
        soundManager.playTick()
        delay(1200)
        stage = 2 // Full form appears
        soundManager.playSuccess()
        delay(3000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Futuristic Particle System
        val infiniteTransition = rememberInfiniteTransition(label = "stars")
        val alphaAnim by infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
            label = "pulse"
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val random = java.util.Random(100)
            repeat(60) {
                val x = random.nextFloat() * size.width
                val y = random.nextFloat() * size.height
                val r = random.nextFloat() * 1.5.dp.toPx()
                drawCircle(
                    color = Color.White.copy(alpha = alphaAnim * random.nextFloat()),
                    radius = r,
                    center = Offset(x, y)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .offset(y = logoOffset)
                    .scale(logoScale),
                contentAlignment = Alignment.Center
            ) {
                AppLogo(size = 180.dp)
                
                // The 3 arrows (Simplified 3 Rs Circular Path) appearing in stage 2
                if (stage >= 2) {
                    Canvas(modifier = Modifier.size(240.dp)) {
                        val strokeWidth = 2.dp.toPx()
                        for (i in 0..2) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    0.0f to Color(0xFF2ECC71).copy(alpha = 0f),
                                    0.5f to Color(0xFF2ECC71).copy(alpha = 0.4f),
                                    1.0f to Color(0xFF2ECC71).copy(alpha = 0f)
                                ),
                                startAngle = i * 120f + 10f,
                                sweepAngle = 100f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }
            
            AnimatedVisibility(
                visible = stage >= 1,
                enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { 20 },
                modifier = Modifier.offset(y = (-80).dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WasegMul",
                        style = MaterialTheme.typography.displaySmall.copy(
                            letterSpacing = 6.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AnimatedVisibility(
                        visible = stage >= 2,
                        enter = fadeIn(tween(1000)) + slideInVertically(tween(1000)) { 20 }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Waste Segregation Multi-model AI",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.alpha(0.9f)
                            )
                            
                            Spacer(modifier = Modifier.height(64.dp))
                            
                            Text(
                                text = "\"$currentThought\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
