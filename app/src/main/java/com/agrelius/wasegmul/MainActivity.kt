package com.agrelius.wasegmul

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.agrelius.wasegmul.navigation.AppNavigation
import com.agrelius.wasegmul.ui.theme.AppThemeMode
import com.agrelius.wasegmul.ui.theme.WasegMulTheme
import com.agrelius.wasegmul.utils.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as? WasegMulApp
            ?: error("Application class must be WasegMulApp: check AndroidManifest android:name")
        setContent {
            val themeMode by app.settingsManager.themeMode.collectAsState(initial = null)
            val dynamicColor by app.settingsManager.dynamicColor.collectAsState(initial = false)
            // null = loading prefs; fall back to system to avoid dark-flash for LIGHT users.
            val systemDark = isSystemInDarkTheme()
            val effective = themeMode ?: if (systemDark) ThemeMode.DARK else ThemeMode.SYSTEM
            val appThemeMode = when(effective) {
                ThemeMode.LIGHT -> AppThemeMode.LIGHT
                ThemeMode.DARK -> AppThemeMode.DARK
                ThemeMode.COLOUR -> AppThemeMode.COLOUR
                ThemeMode.SYSTEM -> if (systemDark) AppThemeMode.DARK else AppThemeMode.LIGHT
            }

            WasegMulTheme(themeMode = appThemeMode, dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
