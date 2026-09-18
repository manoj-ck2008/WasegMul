package com.agrelius.wasegmul.ui.components

import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Central gate for decorative animation.
 *
 * Returns true when motion should be reduced: battery-saver is on, or the
 * user disabled animations in Settings. Decorative layers (OrganicBackground,
 * FallingPetals, AppLogo, GlassCard blur) must consult this and render a
 * static frame instead of animating.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val app = remember(context) {
        context.applicationContext as? com.agrelius.wasegmul.WasegMulApp
    }
    val powerManager = remember(context) {
        context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
    }
    val isPowerSave = powerManager?.isPowerSaveMode == true
    val reduceAnimations by app?.settingsManager?.reduceAnimations?.collectAsState(initial = false)
        ?: remember { androidx.compose.runtime.mutableStateOf(false) }
    return isPowerSave || reduceAnimations
}
