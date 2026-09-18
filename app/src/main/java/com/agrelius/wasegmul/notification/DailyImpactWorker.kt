package com.agrelius.wasegmul.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.agrelius.wasegmul.EcoImpactCalculator
import com.agrelius.wasegmul.data.WasteDatabase
import com.agrelius.wasegmul.data.toCommon
import com.agrelius.wasegmul.gamification.GamificationManager
import com.agrelius.wasegmul.utils.SafeLog
import com.agrelius.wasegmul.utils.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.Calendar

class DailyImpactWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "daily_eco_impact"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "DailyImpactWorker"

        /**
         * Upper bound for the DataStore read. `totalXp.first()` with no timeout can
         * stall the worker past its 10-minute execution window on a wedged DataStore.
         */
        const val PREFS_TIMEOUT_MS = 10_000L
    }

    override suspend fun doWork(): Result {
        return try {
            val db = WasteDatabase.getDatabase(applicationContext)
            val dao = db.wasteDao()
            val settingsManager = SettingsManager(applicationContext)

            // Targeted queries: avoid loading the full history table into memory.
            val totalScans = dao.getCount()

            // DST note: Calendar midnight is wall-clock midnight — on a DST transition
            // day "today" is 23/25 h long. Acceptable for a daily nudge (off-by-an-hour
            // boundary once a year), not for billing-grade accounting.
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayEntities = dao.getRecordsSince(todayStart)
            val todayScans = todayEntities.size

            // Single mapper (Mappers.toCommon): the hand-rolled field copy previously
            // here drifted from the canonical mapping on every model change.
            val todayCommonRecords = todayEntities.map { it.toCommon() }

            val todayMetrics = EcoImpactCalculator.calculate(todayCommonRecords)

            // Get gamification level
            val totalXp = try {
                withTimeout(PREFS_TIMEOUT_MS) { settingsManager.totalXp.first() }
            } catch (e: TimeoutCancellationException) {
                SafeLog.w(TAG, "totalXp read timed out; defaulting to 0 for this run")
                0
            }
            val gamState = GamificationManager.computeState(totalXp)

            // Build and show notification
            if (NotificationHelper.hasNotificationPermission(applicationContext)) {
                val notification = NotificationHelper.buildDailyImpactNotification(
                    context = applicationContext,
                    todayScans = todayScans,
                    todayCo2Kg = todayMetrics.co2PreventedKg,
                    todayWaterL = todayMetrics.waterSavedLiters,
                    todayEnergyKwh = todayMetrics.energySavedKwh,
                    totalScans = totalScans,
                    levelName = gamState.currentLevel.name,
                    levelEmoji = gamState.currentLevel.iconEmoji
                ).build()

                NotificationManagerCompat.from(applicationContext)
                    .notify(NOTIFICATION_ID, notification)
            }

            Result.success()
        } catch (e: CancellationException) {
            // Structured-concurrency contract: never convert cancellation to retry/failure.
            throw e
        } catch (e: IOException) {
            // Transient (DB/DataStore IO): backoff + retry per WorkManager policy.
            SafeLog.e(TAG, "Transient IO failure; retrying daily report", e)
            Result.retry()
        } catch (e: Exception) {
            // Permanent (corrupt DB, proto failure, code bug): retrying forever drains
            // battery for a report that will never succeed. Fail loudly instead.
            SafeLog.e(TAG, "Non-retryable failure generating daily report", e)
            Result.failure()
        }
    }
}
