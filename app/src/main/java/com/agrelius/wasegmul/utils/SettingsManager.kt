package com.agrelius.wasegmul.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        val VOLUME_KEY = floatPreferencesKey("volume")
        val THEME_KEY = stringPreferencesKey("theme")
    }

    val volume: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[VOLUME_KEY] ?: 0.5f
    }

    suspend fun setVolume(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[VOLUME_KEY] = value
        }
    }
}
