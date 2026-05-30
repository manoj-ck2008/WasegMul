package com.agrelius.wasegmul.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import com.agrelius.wasegmul.ui.theme.*

@Composable
fun OrganicBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "organic")
    
    val windSway by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        // Diverse palette usage
        val leafColors = listOf(ForestGreen, OliveDeep, MossEarthy, SageGreen)
        val random = java.util.Random(1337)
        
        repeat(12) { i ->
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val scale = 0.5f + random.nextFloat() * 1.5f
            val color = leafColors[i % leafColors.size].copy(alpha = 0.08f)
            
            rotate(degrees = (i * 30f) + windSway, pivot = Offset(x, y)) {
                drawLeaf(Offset(x, y), scale, color)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLeaf(
    center: Offset,
    scale: Float,
    color: androidx.compose.ui.graphics.Color
) {
    val w = 40f * scale
    val h = 70f * scale
    val path = Path().apply {
        moveTo(center.x, center.y - h/2)
        quadraticTo(center.x + w, center.y, center.x, center.y + h/2)
        quadraticTo(center.x - w, center.y, center.x, center.y - h/2)
    }
    drawPath(path, color)
}
