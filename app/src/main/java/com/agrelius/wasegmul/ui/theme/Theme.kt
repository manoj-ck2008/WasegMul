package com.agrelius.wasegmul.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    DARK, LIGHT, COLOUR
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldVibrant,
    secondary = SageGreen,
    surface = SurfaceGray, // Darker surface
    background = DarkBackground, // Extremely dark background
    onPrimary = Color.Black,
    onSurface = TextPrimary,
    onBackground = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF27AE60),
    secondary = Color(0xFF8E8E93),
    surface = LightSurface,
    background = LightBackground,
    onSurface = LightText,
    onBackground = LightText
)

private val ColourColorScheme = darkColorScheme(
    primary = GrassGreenLustrous,
    secondary = SkyBlueDeep,
    tertiary = OliveLight,
    surface = OliveDark,
    background = MossSecondary,
    onSurface = Color.White,
    onBackground = Color.White,
    inverseSurface = EarthBrown
)

@Composable
fun WasegMulTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when(themeMode) {
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.COLOUR -> ColourColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // True edge-to-edge
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = themeMode == AppThemeMode.LIGHT
                controller.isAppearanceLightNavigationBars = themeMode == AppThemeMode.LIGHT
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
