package com.agrelius.wasegmul.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.ui.draw.blur
import androidx.compose.ui.tooling.preview.Preview
import com.agrelius.wasegmul.ui.theme.DarkGlassHighlight
import com.agrelius.wasegmul.ui.theme.LocalGlassColors
import com.agrelius.wasegmul.ui.theme.WasegMulTheme

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassColors = LocalGlassColors.current
    val bgBrush = remember(glassColors) {
        Brush.verticalGradient(
            colors = listOf(
                glassColors.surface,
                glassColors.surface.copy(alpha = glassColors.surface.alpha * 0.4f),
                Color.Transparent
            )
        )
    }
    val borderBrush = remember(glassColors) {
        Brush.verticalGradient(
            colors = listOf(
                glassColors.border,
                glassColors.border.copy(alpha = 0.03f),
                glassColors.border.copy(alpha = 0.08f)
            )
        )
    }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val blurModifier = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.blur(16.dp)
        } else {
            Modifier
        }
    }

    Box(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = glassColors.border.copy(alpha = 0.15f))
            .clip(shape)
            .border(
                width = 1.dp,
                brush = borderBrush,
                shape = shape
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(blurModifier)
                .background(bgBrush)
        )
        Column(modifier = Modifier.padding(20.dp)) {
            content()
        }
    }
}

@Preview(name = "GlassCard Preview", showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun GlassCardPreview() {
    WasegMulTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            GlassCard {
                Text(text = "GlassCard Title", color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "GlassCard content with frosted glass styling.", color = Color.LightGray)
            }
        }
    }
}
