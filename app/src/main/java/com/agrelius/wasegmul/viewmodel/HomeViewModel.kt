package com.agrelius.wasegmul.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agrelius.wasegmul.WasteRecord
import com.agrelius.wasegmul.repository.WasteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: WasteRepository) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val recentHistory: StateFlow<List<WasteRecord>> = repository.recentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHistory: StateFlow<List<WasteRecord>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearHistory(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.clear()
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear history", e)
                _error.value = "Failed to clear history"
            }
        }
    }

    fun deleteRecord(id: Long, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.deleteById(id)
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete record $id", e)
                _error.value = "Failed to delete record"
            }
        }
    }

    fun updateFeedback(recordId: Long, feedback: String) {
        viewModelScope.launch {
            try {
                repository.updateFeedback(recordId, feedback)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update feedback", e)
                _error.value = "Failed to update feedback"
            }
        }
    }

    fun updateCorrection(recordId: Long, correction: String) {
        viewModelScope.launch {
            try {
                repository.updateCorrectionWithCategory(recordId, correction)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update correction", e)
                _error.value = "Failed to update correction"
            }
        }
    }

    fun clearError() {
        _error.value = null
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

    companion object {
        private const val TAG = "HomeVM"
    }
}
