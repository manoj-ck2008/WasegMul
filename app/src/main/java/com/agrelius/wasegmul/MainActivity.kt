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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext as WasegMulApp
        setContent {
            val themeStr by app.settingsManager.themeMode.collectAsState(initial = "DARK")
            val themeMode = when(themeStr) {
                "LIGHT" -> AppThemeMode.LIGHT
                "COLOUR" -> AppThemeMode.COLOUR
                else -> AppThemeMode.DARK
            }

            WasegMulTheme(themeMode = themeMode) {
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
