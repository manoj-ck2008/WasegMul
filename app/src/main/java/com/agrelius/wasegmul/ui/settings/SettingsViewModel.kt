package com.agrelius.wasegmul.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agrelius.wasegmul.utils.SettingsManager
import com.agrelius.wasegmul.utils.SoundManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsManager: SettingsManager,
    private val soundManager: SoundManager
) : ViewModel() {

    val volume: StateFlow<Float> = settingsManager.volume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)

    val themeMode: StateFlow<String> = settingsManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DARK")

    val isImageSharingEnabled: StateFlow<Boolean> = settingsManager.isImageSharingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setVolume(value: Float) {
        viewModelScope.launch {
            settingsManager.setVolume(value)
            soundManager.updateVolume()
            // playTick removed as per request
        }
    }

    fun setThemeMode(value: String) {
        viewModelScope.launch {
            settingsManager.setThemeMode(value)
        }
    }

    fun setImageSharing(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setImageSharing(enabled)
            soundManager.playTick()
        }
    }

    class Factory(
        private val settingsManager: SettingsManager,
        private val soundManager: SoundManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsManager, soundManager) as T
        }
    }
}
