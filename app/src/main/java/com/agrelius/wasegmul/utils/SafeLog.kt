package com.agrelius.wasegmul.utils

import android.util.Log

/**
 * Logcat facade that never throws on JVM local unit tests (§3 low-note / test fix).
 *
 * `android.util.Log` is a stub on the JVM (`"Method w/d in android.util.Log not mocked"`)
 * and this project has no Robolectric/MockK plus `isReturnDefaultValues` cannot be
 * enabled (build files are owned by another batch). Every data-layer mapper/write-guard
 * logs coercions, so a bare `Log.w` crashes `WasteRepositoryTest`/`MappersTest`.
 *
 * Each call is wrapped in `runCatching`: on-device it logs normally; under unit tests
 * the stub exception is swallowed (and returned for assertions if needed).
 */
object SafeLog {
    fun w(tag: String, message: String): Throwable? =
        runCatching { Log.w(tag, message) }.exceptionOrNull()

    fun d(tag: String, message: String): Throwable? =
        runCatching { Log.d(tag, message) }.exceptionOrNull()

    fun i(tag: String, message: String): Throwable? =
        runCatching { Log.i(tag, message) }.exceptionOrNull()

    fun e(tag: String, message: String, cause: Throwable? = null): Throwable? =
        runCatching { Log.e(tag, message, cause) }.exceptionOrNull()
}
