package com.agrelius.wasegmul.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GradientActionButton(
    text: String,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    hapticsEnabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    val gradient = remember(containerColor) {
        Brush.horizontalGradient(
            colors = listOf(
                containerColor,
                containerColor.copy(alpha = 0.8f)
            )
        )
    }
    val disabledGradient = remember(containerColor) {
        Brush.horizontalGradient(
            colors = listOf(
                containerColor.copy(alpha = 0.38f),
                containerColor.copy(alpha = 0.2f)
            )
        )
    }

    Button(
        onClick = {
            // Single tap confirmation. (No dedicated tap constant exists on
            // this Compose version; LongPress is the supported key.)
            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            // Gradient on the OUTER modifier (under the ripple) so the touch
            // ripple stays visible; the inner box is transparent.
            .background(
                brush = if (enabled) gradient else disabledGradient,
                shape = RoundedCornerShape(16.dp)
            ),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        // Meaningful action icons need a description; fall
                        // back to the button text when none is provided.
                        contentDescription = iconContentDescription ?: text,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
