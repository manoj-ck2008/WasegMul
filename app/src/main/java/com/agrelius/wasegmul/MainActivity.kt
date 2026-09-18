package com.agrelius.wasegmul

import android.content.Intent
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
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /**
     * Warm deep-link events. The activity is `singleTask`, so a `wasegmul://…`
     * link fired while the app is alive arrives via [onNewIntent], not onCreate —
     * without this the NavHost (which only consumes the launch intent) would
     * ignore it and appear to do nothing.
     */
    private val deepLinkEvents = MutableStateFlow<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            deepLinkEvents.value = intent
        }
    }

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
                    AppNavigation(deepLinkEvents = deepLinkEvents)
                }
            }
        }
    }
}
