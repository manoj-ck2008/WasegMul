package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppLogo(
    size: Dp = 100.dp,
    animate: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer animated ring
        Canvas(modifier = Modifier
            .fillMaxSize()
            .rotate(if (animate) rotation else 0f)) {
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        SustainabilityGreen.copy(alpha = 0.5f),
                        SustainabilityGreen,
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 4.dp.toPx())
            )
        }

        // Inner Glow
        Box(
            modifier = Modifier
                .fillMaxSize(0.7f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SustainabilityGreen.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Core Icon
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            modifier = Modifier
                .size(size * 0.4f)
                .rotate(if (animate) -rotation * 0.5f else 0f),
            tint = SustainabilityGreen
        )
        
        Icon(
            imageVector = Icons.Default.Recycling,
            contentDescription = null,
            modifier = Modifier
                .size(size * 0.25f)
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-4).dp),
            tint = MintAccent
        )
    }
}

// Add these to Color.kt if not there, but SustainabilityGreen is there.
// I'll use the ones from the theme.
private val SustainabilityGreen = Color(0xFF2ECC71)
private val MintAccent = Color(0xFF82E0AA)
