package com.agrelius.wasegmul.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.agrelius.wasegmul.EcoImpactCalculator
import com.agrelius.wasegmul.data.WasteDatabase
import com.agrelius.wasegmul.gamification.GamificationManager
import com.agrelius.wasegmul.utils.SettingsManager
import kotlinx.coroutines.flow.first
import java.util.Calendar

class DailyImpactWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "daily_eco_impact"
        private const val NOTIFICATION_ID = 1001
    }

    override suspend fun doWork(): Result {
        return try {
            val db = WasteDatabase.getDatabase(applicationContext)
            val dao = db.wasteDao()
            val settingsManager = SettingsManager(applicationContext)

            // Get all history for total stats
            val allRecords = dao.getAllForExport()
            val totalScans = allRecords.size

            // Filter today's scans
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayRecords = allRecords.filter { it.timestamp >= todayStart }
            val todayScans = todayRecords.size

            // Calculate today's eco impact
            val todayCommonRecords = todayRecords.map { record ->
                com.agrelius.wasegmul.WasteRecord(
                    id = record.id,
                    category = record.category,
                    subclass = record.subclass,
                    confidence = record.confidence,
                    estimatedWeight = record.estimatedWeight,
                    featureVector = record.featureVector,
                    imagePath = record.imagePath,
                    feedback = record.feedback,
                    correctedSubclass = record.correctedSubclass,
                    topPredictions = record.topPredictions,
                    timestamp = record.timestamp
                )
            }

            val todayMetrics = EcoImpactCalculator.calculate(todayCommonRecords)

            // Get gamification level
            val totalXp = settingsManager.totalXp.first()
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
        } catch (e: Exception) {
            android.util.Log.e("DailyImpactWorker", "Failed to generate daily report", e)
            Result.retry()
        }
    }
}
