package com.agrelius.wasegmul.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.repository.WasteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: WasteRepository) : ViewModel() {
    
    val recentHistory: StateFlow<List<WasteRecord>> = repository.recentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHistory: StateFlow<List<WasteRecord>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory() {
        viewModelScope.launch {
            repository.clear()
        }
    }

    fun updateFeedback(recordId: Long, feedback: String) {
        viewModelScope.launch {
            repository.updateFeedback(recordId, feedback)
        }
    }

    fun updateCorrection(recordId: Long, correction: String) {
        viewModelScope.launch {
            repository.updateCorrection(recordId, correction)
        }
    }

    class Factory(private val repository: WasteRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
