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
        val THEME_KEY = stringPreferencesKey("theme")
    }

    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_KEY] ?: "DARK" }

    suspend fun setThemeMode(value: String) { context.dataStore.edit { it[THEME_KEY] = value } }
}
