package com.agrelius.wasegmul

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    /** App-owned scope for fire-and-forget background work (pre-warm only). */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        scheduleDailyImpactNotification()
        prewarmModels()
    }

    /**
     * Warms the two EfficientNet interpreters off the critical path (§3.42): without
     * this the first classification pays the full mmap + init stall. Best-effort and
     * failure-silent — a corrupt asset must not crash launch; the Classify screen
     * surfaces init errors with its own retry UI when actually needed.
     */
    private fun prewarmModels() {
        applicationScope.launch(Dispatchers.IO) {
            runCatching { modelManager.ensureInitialized() }
        }
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

        // REPLACE (not KEEP) re-anchors the 8 PM fire time on every process start:
        // under Doze a KEEP-enqueued periodic drifts day-over-day and never comes back.
        // Linear backoff bounds the retry storm if the worker hits transient IO.
        val dailyWork = PeriodicWorkRequestBuilder<DailyImpactWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .addTag(DailyImpactWorker.WORK_NAME)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            DailyImpactWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            dailyWork
        )
    }
}
