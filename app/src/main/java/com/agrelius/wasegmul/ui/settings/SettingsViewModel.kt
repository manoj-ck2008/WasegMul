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

    fun setVolume(value: Float) {
        viewModelScope.launch {
            settingsManager.setVolume(value)
            soundManager.updateVolume()
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
