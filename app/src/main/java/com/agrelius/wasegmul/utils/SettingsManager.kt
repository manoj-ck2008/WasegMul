package com.agrelius.wasegmul.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode(val key: String) {
    DARK("DARK"),
    LIGHT("LIGHT"),
    COLOUR("COLOUR"),
    SYSTEM("SYSTEM");

    companion object {
        fun fromKey(key: String): ThemeMode = entries.find { it.key == key } ?: DARK
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(context: Context) {
    private val appContext = context.applicationContext
    companion object {
        private val THEME_KEY = stringPreferencesKey("theme")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val CONFIDENCE_THRESHOLD_KEY = floatPreferencesKey("confidence_threshold")
        private val HAPTICS_KEY = booleanPreferencesKey("haptics_enabled")
    }

    val themeMode: Flow<ThemeMode> = appContext.dataStore.data
        .catch { e ->
            if (e is java.io.IOException) emit(emptyPreferences()) else throw e
        }
        .map { ThemeMode.fromKey(it[THEME_KEY] ?: ThemeMode.DARK.key) }

    val dynamicColor: Flow<Boolean> = appContext.dataStore.data
        .catch { e ->
            if (e is java.io.IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[DYNAMIC_COLOR_KEY] ?: false }

    val confidenceThreshold: Flow<Float> = appContext.dataStore.data
        .catch { e ->
            if (e is java.io.IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[CONFIDENCE_THRESHOLD_KEY] ?: 0.50f }

    val hapticsEnabled: Flow<Boolean> = appContext.dataStore.data
        .catch { e ->
            if (e is java.io.IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[HAPTICS_KEY] ?: true }

    suspend fun setThemeMode(value: ThemeMode) { appContext.dataStore.edit { it[THEME_KEY] = value.key } }
    suspend fun setDynamicColor(value: Boolean) { appContext.dataStore.edit { it[DYNAMIC_COLOR_KEY] = value } }
    suspend fun setConfidenceThreshold(value: Float) { appContext.dataStore.edit { it[CONFIDENCE_THRESHOLD_KEY] = value.coerceIn(0.1f, 0.95f) } }
    suspend fun setHapticsEnabled(value: Boolean) { appContext.dataStore.edit { it[HAPTICS_KEY] = value } }
}

