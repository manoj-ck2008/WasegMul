package com.agrelius.wasegmul.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val key: String) {
    DARK("DARK"),
    LIGHT("LIGHT"),
    SYSTEM("SYSTEM");

    companion object {
        fun fromKey(key: String): ThemeMode = entries.find { it.key == key } ?: DARK
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {
    companion object {
        val THEME_KEY = stringPreferencesKey("theme")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.fromKey(it[THEME_KEY] ?: ThemeMode.DARK.key) }

    suspend fun setThemeMode(value: ThemeMode) { context.dataStore.edit { it[THEME_KEY] = value.key } }
}
