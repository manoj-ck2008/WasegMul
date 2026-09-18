package com.agrelius.wasegmul.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agrelius.wasegmul.utils.SettingsManager
import com.agrelius.wasegmul.utils.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsManager: SettingsManager
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)

    val dynamicColor: StateFlow<Boolean> = settingsManager.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val confidenceThreshold: StateFlow<Float> = settingsManager.confidenceThreshold
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.50f)

    val hapticsEnabled: StateFlow<Boolean> = settingsManager.hapticsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { settingsManager.setThemeMode(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { settingsManager.setDynamicColor(value) }
    }

    fun setConfidenceThreshold(value: Float) {
        // Clamp to the supported slider range; the slider also coerces, this is
        // the ViewModel-side guarantee (no per-pixel DataStore hammering: the
        // UI only calls this from onValueChangeFinished).
        viewModelScope.launch {
            settingsManager.setConfidenceThreshold(value.coerceIn(0.20f, 0.90f))
        }
    }

    fun setHapticsEnabled(value: Boolean) {
        viewModelScope.launch { settingsManager.setHapticsEnabled(value) }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsManager.setThemeMode(ThemeMode.DARK)
            settingsManager.setDynamicColor(false)
            settingsManager.setConfidenceThreshold(0.50f)
            settingsManager.setHapticsEnabled(true)
        }
    }


    class Factory(
        private val settingsManager: SettingsManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(settingsManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
