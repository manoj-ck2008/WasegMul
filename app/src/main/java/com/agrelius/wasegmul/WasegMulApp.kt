package com.agrelius.wasegmul

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.agrelius.wasegmul.data.WasteDatabase
import com.agrelius.wasegmul.network.OpenFoodFactsApi
import com.agrelius.wasegmul.notification.DailyImpactWorker
import com.agrelius.wasegmul.notification.NotificationHelper
import com.agrelius.wasegmul.repository.BarcodeRepository
import com.agrelius.wasegmul.repository.WasteRepository
import com.agrelius.wasegmul.utils.ConnectivityChecker
import com.agrelius.wasegmul.utils.SettingsManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WasegMulApp : Application() {
    private val database by lazy { WasteDatabase.getDatabase(this) }
    val repository by lazy { WasteRepository(database.wasteDao()) }
    val barcodeRepository by lazy {
        BarcodeRepository(
            dao = database.barcodeProductDao(),
            api = OpenFoodFactsApi(),
            isOnline = { ConnectivityChecker.isOnline(this) },
            context = this
        )
    }
    val modelManager by lazy { com.agrelius.wasegmul.ml.ModelManager(this) }
    val settingsManager by lazy { SettingsManager(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        scheduleDailyImpactNotification()
    }

    private fun scheduleDailyImpactNotification() {
        // Calculate delay to 8:00 PM today (or tomorrow if already past 8 PM)
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelayMs = target.timeInMillis - now.timeInMillis

        val dailyWork = PeriodicWorkRequestBuilder<DailyImpactWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .addTag(DailyImpactWorker.WORK_NAME)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyImpactWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWork
        )
    }
}
