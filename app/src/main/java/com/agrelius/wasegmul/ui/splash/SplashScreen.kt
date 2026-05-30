package com.agrelius.wasegmul.ui.splash

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agrelius.wasegmul.ui.components.AppLogo
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var stage by remember { mutableStateOf(0) }
    
    val logoScale by animateFloatAsState(
        targetValue = when(stage) {
            0 -> 1f
            1 -> 0.7f
            else -> 0.5f
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "logo_scale"
    )

    val logoOffset by animateDpAsState(
        targetValue = when(stage) {
            0 -> 0.dp
            1 -> (-80).dp
            else -> (-120).dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "logo_offset"
    )

    LaunchedEffect(Unit) {
        delay(500)
        stage = 1 // Name appears
        delay(800)
        stage = 2 // Full form appears
        delay(2000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .offset(y = logoOffset)
                    .scale(logoScale)
            ) {
                AppLogo(size = 150.dp)
            }
            
            AnimatedVisibility(
                visible = stage >= 1,
                enter = fadeIn(tween(600)) + expandVertically(tween(600)),
                modifier = Modifier.offset(y = (-40).dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WasegMul",
                        style = MaterialTheme.typography.displaySmall.copy(
                            letterSpacing = 8.sp // Extra wide futuristic spacing
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    AnimatedVisibility(
                        visible = stage >= 2,
                        enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { it / 2 }
                    ) {
                        Text(
                            text = "Waste Segregation Multi-model AI",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alpha(0.8f)
                        )
                    }
                }
            }
        }
    }
}
