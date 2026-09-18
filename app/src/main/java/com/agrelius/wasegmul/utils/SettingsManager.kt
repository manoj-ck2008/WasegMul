package com.agrelius.wasegmul.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

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

/**
 * App preferences (theme, confidence threshold, XP, daily counters).
 *
 * ### `java.time` / desugar note (§3.41) — RESOLVED
 * [LocalDate]/[Clock] require API 26+ at runtime. `minSdk` is 29 and
 * `isCoreLibraryDesugaringEnabled = true` + `desugar_jdk_libs` are set in
 * `app/build.gradle.kts`, so ART desugaring backports `java.time` to API 29–25
 * devices. Do not remove that flag without replacing [LocalDate]/[Clock] usage.
 * (Three-arg `LocalDate` use is injectable via [clock] so unit tests control "today".)
 *
 * ### Threshold single-source (§3.41)
 * [DEFAULT_CONFIDENCE_THRESHOLD] is the ONE default for the user-facing confidence
 * slider. The YOLO detector's constructor default (`0.45f`) is a model-side fallback
 * only — call sites must push this DataStore value into
 * `YoloDetector.setConfidenceThreshold()` (and the ML Arbitrator path) on collect.
 */
class SettingsManager(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val appContext = context.applicationContext
    companion object {
        /**
         * Single default for the confidence slider (§3.41). Changing it changes the
         * first-run experience everywhere the threshold is consumed.
         */
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.50f

        /** Slider bounds: below 0.20 the arbitrator abstains on everything; above 0.90 it trusts noise. */
        const val MIN_CONFIDENCE_THRESHOLD = 0.20f
        const val MAX_CONFIDENCE_THRESHOLD = 0.90f

        private val THEME_KEY = stringPreferencesKey("theme")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
        private val CONFIDENCE_THRESHOLD_KEY = floatPreferencesKey("confidence_threshold")
        private val HAPTICS_KEY = booleanPreferencesKey("haptics_enabled")
        private val REDUCE_ANIMATIONS_KEY = booleanPreferencesKey("reduce_animations")
        private val TOTAL_XP_KEY = intPreferencesKey("total_xp")
        private val DAILY_SCAN_COUNT_KEY = intPreferencesKey("daily_scan_count")
        private val LAST_SCAN_DATE_KEY = stringPreferencesKey("last_scan_date")
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
        .map { it[CONFIDENCE_THRESHOLD_KEY] ?: DEFAULT_CONFIDENCE_THRESHOLD }

    val hapticsEnabled: Flow<Boolean> = appContext.dataStore.data
        .catch { e ->
            if (e is java.io.IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[HAPTICS_KEY] ?: true }

    val reduceAnimations: Flow<Boolean> = appContext.dataStore.data
        .catch { e ->
            if (e is java.io.IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[REDUCE_ANIMATIONS_KEY] ?: false }

    suspend fun setThemeMode(value: ThemeMode) { appContext.dataStore.edit { it[THEME_KEY] = value.key } }
    suspend fun setDynamicColor(value: Boolean) { appContext.dataStore.edit { it[DYNAMIC_COLOR_KEY] = value } }
    suspend fun setConfidenceThreshold(value: Float) { appContext.dataStore.edit { it[CONFIDENCE_THRESHOLD_KEY] = value.coerceIn(MIN_CONFIDENCE_THRESHOLD, MAX_CONFIDENCE_THRESHOLD) } }
    suspend fun setHapticsEnabled(value: Boolean) { appContext.dataStore.edit { it[HAPTICS_KEY] = value } }
    suspend fun setReduceAnimations(value: Boolean) { appContext.dataStore.edit { it[REDUCE_ANIMATIONS_KEY] = value } }

    val totalXp: Flow<Int> = appContext.dataStore.data
        .catch { e ->
            if (e is java.io.IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[TOTAL_XP_KEY] ?: 0 }

    val dailyScanCount: Flow<Int> = appContext.dataStore.data
        .catch { e ->
            if (e is java.io.IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val today = LocalDate.now(clock).toString()
            val lastDate = prefs[LAST_SCAN_DATE_KEY] ?: ""
            if (lastDate == today) prefs[DAILY_SCAN_COUNT_KEY] ?: 0 else 0
        }

    /** Negative XP is rejected (clamped to 0) — totals must never go below zero. */
    suspend fun setTotalXp(xp: Int) {
        appContext.dataStore.edit { it[TOTAL_XP_KEY] = xp.coerceAtLeast(0) }
    }

    suspend fun incrementDailyScanCount(): Int {
        var currentCount = 0
        appContext.dataStore.edit { prefs ->
            val today = LocalDate.now(clock).toString()
            val lastDate = prefs[LAST_SCAN_DATE_KEY] ?: ""
            currentCount = if (lastDate == today) {
                (prefs[DAILY_SCAN_COUNT_KEY] ?: 0) + 1
            } else {
                1
            }
            prefs[LAST_SCAN_DATE_KEY] = today
            prefs[DAILY_SCAN_COUNT_KEY] = currentCount
        }
        return currentCount
    }
}

