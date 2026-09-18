package com.agrelius.wasegmul.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agrelius.wasegmul.MainActivity
import com.agrelius.wasegmul.R

object NotificationHelper {

    const val CHANNEL_ID_DAILY_IMPACT = "eco_daily_report"
    private const val CHANNEL_NAME = "Daily Eco Impact"
    private const val CHANNEL_DESCRIPTION = "Your daily environmental impact summary from WasegMul"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_DAILY_IMPACT,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = 0xFF00FF94.toInt()
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun buildDailyImpactNotification(
        context: Context,
        todayScans: Int,
        todayCo2Kg: Double,
        todayWaterL: Double,
        todayEnergyKwh: Double,
        totalScans: Int,
        levelName: String,
        levelEmoji: String
    ): NotificationCompat.Builder {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (todayScans > 0) {
            "\uD83C\uDF3F Your Daily Eco Impact"
        } else {
            "\uD83C\uDF31 Keep Going, $levelName!"
        }

        val shortBody = if (todayScans > 0) {
            "Today: $todayScans scan${if (todayScans != 1) "s" else ""}, " +
                "${"%.2f".format(java.util.Locale.US, todayCo2Kg)}kg CO\u2082 diverted. " +
                "You're a $levelEmoji $levelName!"
        } else {
            "You haven't scanned any waste today. Scan just one item to make a difference! " +
                "You've classified $totalScans items so far."
        }

        val expandedBody = if (todayScans > 0) {
            buildString {
                appendLine("\uD83D\uDCCA Today's Environmental Impact")
                appendLine("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501")
                appendLine("\u267B\uFE0F Items classified: $todayScans")
                appendLine("\uD83C\uDF0D CO\u2082 diverted: ${"%.3f".format(java.util.Locale.US, todayCo2Kg)} kg")
                appendLine("\uD83D\uDCA7 Water conserved: ${"%.1f".format(java.util.Locale.US, todayWaterL)} L")
                appendLine("\u26A1 Energy saved: ${"%.2f".format(java.util.Locale.US, todayEnergyKwh)} kWh")
                appendLine()
                appendLine("$levelEmoji Current rank: $levelName")
                appendLine("\uD83C\uDF1F Total lifetime scans: $totalScans")
                appendLine()
                append("Every item you properly sort makes a real difference. Thank you for protecting our planet!")
            }
        } else {
            buildString {
                appendLine("$levelEmoji You're currently a $levelName")
                appendLine("\uD83C\uDF1F Lifetime scans: $totalScans")
                appendLine()
                append("Open WasegMul and scan just one item today. Even a single properly sorted item helps!")
            }
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_DAILY_IMPACT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(shortBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(0xFF00FF94.toInt())
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }
}
