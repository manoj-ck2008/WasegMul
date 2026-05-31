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
        val IMAGE_SHARING_KEY = booleanPreferencesKey("image_sharing")
    }

    val volume: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[VOLUME_KEY] ?: 0.5f
    }

    val themeMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_KEY] ?: "DARK"
    }

    val isImageSharingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IMAGE_SHARING_KEY] ?: false
    }

    suspend fun setVolume(value: Float) {
        context.dataStore.edit { preferences ->
            preferences[VOLUME_KEY] = value
        }
    }

    suspend fun setThemeMode(value: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = value
        }
    }

    suspend fun setImageSharing(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IMAGE_SHARING_KEY] = enabled
        }
    }
}
