package com.agrelius.wasegmul.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
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
    surface = DarkSurfaceGreen,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariantGreen,
    onSurfaceVariant = TextSecondary,
    surfaceTint = EmeraldVibrant,
    error = LowConfidence,
    onError = Color.White,
    errorContainer = Color(0xFF5C1512),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = DarkGlassBorder,
    outlineVariant = DarkGlassBorder.copy(alpha = 0.3f),
    inverseSurface = TextPrimary,
    inverseOnSurface = DarkBackground,
    inversePrimary = ForestGreen
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
    surfaceTint = Color(0xFF1B8A4A),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFC0C9C2),
    outlineVariant = Color(0xFFDFE5E0),
    inverseSurface = LightText,
    inverseOnSurface = LightBackground,
    inversePrimary = Color(0xFFA8E6C3)
)

private val ColourColorScheme = darkColorScheme(
    // Nature Vivid: grass primary / sky secondary / olive tertiary on deep
    // moss surfaces. Every slot is set explicitly so nothing falls back to
    // the default purple and text roles stay readable in both... (dark-only
    // theme: onPrimary is near-black for the luminous grass green).
    primary = GrassGreenLustrous,
    onPrimary = Color(0xFF0A140A),
    primaryContainer = Color(0xFF1E4D1E),
    onPrimaryContainer = Color(0xFFDFF2D8),
    secondary = SkyBlueDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF12394F),
    onSecondaryContainer = Color(0xFFDCE9F5),
    tertiary = OliveLight,
    onTertiary = Color(0xFF1A2410),
    tertiaryContainer = Color(0xFF4A5230),
    onTertiaryContainer = Color(0xFFF0F2DF),
    background = Color(0xFF0E150C),
    onBackground = Color(0xFFEDF4E4),
    surface = Color(0xFF14210F),
    onSurface = Color(0xFFEDF4E4),
    surfaceVariant = Color(0xFF22301A),
    onSurfaceVariant = Color(0xFFC9D2B8),
    surfaceTint = GrassGreenLustrous,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF7A8F5A),
    outlineVariant = Color(0xFF2C3A24),
    inverseSurface = Color(0xFFEDF4E4),
    inverseOnSurface = Color(0xFF14210F),
    inversePrimary = Color(0xFF1E5B1E),
    scrim = Color.Black
)

/**
 * Swatch triple (primary / secondary / tertiary) per theme for the Settings
 * theme picker. Single source so the picker can never drift from the scheme.
 */
fun appThemeSwatches(mode: AppThemeMode): List<Color> = when (mode) {
    AppThemeMode.DARK -> listOf(EmeraldVibrant, SageGreen, ForestGreen)
    AppThemeMode.LIGHT -> listOf(Color(0xFF1B8A4A), Color(0xFF4E6353), Color(0xFF3E6348))
    AppThemeMode.COLOUR -> listOf(GrassGreenLustrous, SkyBlueDeep, OliveLight)
}

@Composable
fun WasegMulTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (themeMode == AppThemeMode.LIGHT) {
                dynamicLightColorScheme(context)
            } else {
                dynamicDarkColorScheme(context)
            }
        }
        themeMode == AppThemeMode.DARK -> DarkColorScheme
        themeMode == AppThemeMode.LIGHT -> LightColorScheme
        themeMode == AppThemeMode.COLOUR -> ColourColorScheme
        else -> DarkColorScheme
    }

    val glassColors = when(themeMode) {
        AppThemeMode.DARK -> GlassColors(DarkGlassSurface, DarkGlassBorder)
        AppThemeMode.LIGHT -> GlassColors(LightGlassSurface, LightGlassBorder)
        AppThemeMode.COLOUR -> GlassColors(ColourGlassSurface, ColourGlassBorder)
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

@Preview(name = "Dark Theme", showBackground = true)
@Composable
private fun DarkThemePreview() {
    WasegMulTheme(themeMode = AppThemeMode.DARK) {
        Surface {
            Text("Dark Theme Preview", modifier = androidx.compose.ui.Modifier.padding(16.dp))
        }
    }
}

@Preview(name = "Light Theme", showBackground = true)
@Composable
private fun LightThemePreview() {
    WasegMulTheme(themeMode = AppThemeMode.LIGHT) {
        Surface {
            Text("Light Theme Preview", modifier = androidx.compose.ui.Modifier.padding(16.dp))
        }
    }
}

