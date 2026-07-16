package com.agrelius.wasegmul.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class AppThemeMode {
    DARK, LIGHT, COLOUR
}

data class GlassColors(
    val surface: Color,
    val border: Color
)

val LocalGlassColors = staticCompositionLocalOf {
    GlassColors(surface = DarkGlassSurface, border = DarkGlassBorder)
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldVibrant,
    onPrimary = Color.Black,
    primaryContainer = ForestGreen,
    onPrimaryContainer = TextPrimary,
    secondary = SageGreen,
    onSecondary = Color.Black,
    secondaryContainer = ForestGreen.copy(alpha = 0.3f),
    onSecondaryContainer = TextPrimary,
    tertiary = ForestGreen,
    onTertiary = Color.White,
    tertiaryContainer = ForestGreen.copy(alpha = 0.2f),
    onTertiaryContainer = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = SurfaceGray,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGray.copy(alpha = 0.7f),
    onSurfaceVariant = TextSecondary,
    error = LowConfidence,
    onError = Color.White,
    outline = DarkGlassBorder,
    outlineVariant = DarkGlassBorder.copy(alpha = 0.3f),
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1B8A4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8E6C3),
    onPrimaryContainer = Color(0xFF0D3319),
    secondary = Color(0xFF4E6353),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD0E8D4),
    onSecondaryContainer = Color(0xFF0B1F12),
    tertiary = Color(0xFF3E6348),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8DBC6),
    onTertiaryContainer = Color(0xFF0B2013),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = Color(0xFFE8EDE9),
    onSurfaceVariant = Color(0xFF414942),
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFFC0C9C2),
    outlineVariant = Color(0xFFDFE5E0),
    inverseSurface = LightText,
    inverseOnSurface = LightBackground
)

private val ColourColorScheme = darkColorScheme(
    primary = GrassGreenLustrous,
    onPrimary = Color.Black,
    secondary = SkyBlueDeep,
    onSecondary = Color.White,
    tertiary = OliveLight,
    surface = OliveDark,
    onSurface = Color.White,
    background = MossSecondary,
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

    val glassColors = when(themeMode) {
        AppThemeMode.DARK -> GlassColors(DarkGlassSurface, DarkGlassBorder)
        AppThemeMode.LIGHT -> GlassColors(LightGlassSurface, LightGlassBorder)
        AppThemeMode.COLOUR -> GlassColors(DarkGlassSurface, DarkGlassBorder)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()

                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = themeMode == AppThemeMode.LIGHT
                controller.isAppearanceLightNavigationBars = themeMode == AppThemeMode.LIGHT
            }
        }
    }

    CompositionLocalProvider(LocalGlassColors provides glassColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
