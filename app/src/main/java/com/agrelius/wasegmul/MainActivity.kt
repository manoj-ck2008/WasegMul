package com.agrelius.wasegmul

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
        val app = applicationContext as WasegMulApp
        setContent {
            val themeMode by app.settingsManager.themeMode.collectAsState(initial = ThemeMode.DARK)
            val appThemeMode = when(themeMode) {
                ThemeMode.LIGHT -> AppThemeMode.LIGHT
                ThemeMode.DARK -> AppThemeMode.DARK
                ThemeMode.SYSTEM -> AppThemeMode.DARK
            }

            WasegMulTheme(themeMode = appThemeMode) {
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
