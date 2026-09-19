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

import com.agrelius.wasegmul.gamification.EcoLevel

object NotificationHelper {

    const val CHANNEL_ID_DAILY_IMPACT = "eco_daily_report"
    private const val CHANNEL_NAME = "Daily Eco Impact"
    private const val CHANNEL_DESCRIPTION = "Your daily environmental impact summary from WasegMul"

    const val CHANNEL_ID_LEVEL_UP = "eco_level_up"
    private const val CHANNEL_NAME_LEVEL_UP = "Level Up & Achievements"
    private const val CHANNEL_DESC_LEVEL_UP = "Notifications when you reach a new Eco Level in WasegMul"
    const val NOTIFICATION_ID_LEVEL_UP = 1002

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dailyChannel = NotificationChannel(
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

            val levelUpChannel = NotificationChannel(
                CHANNEL_ID_LEVEL_UP,
                CHANNEL_NAME_LEVEL_UP,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC_LEVEL_UP
                enableLights(true)
                lightColor = 0xFFFFD700.toInt()
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(dailyChannel)
            manager?.createNotificationChannel(levelUpChannel)
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

    fun buildLevelUpNotification(
        context: Context,
        newLevel: EcoLevel,
        xpEarned: Int
    ): NotificationCompat.Builder {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "\uD83C\uDF89 LEVEL UP: You are now an ${newLevel.name}!"
        val shortBody = "${newLevel.iconEmoji} Fantastic work! You reached Level ${newLevel.level}: ${newLevel.name} with +${xpEarned} XP!"
        val expandedBody = buildString {
            appendLine("${newLevel.iconEmoji} CONGRATULATIONS!")
            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            appendLine("You reached Level ${newLevel.level}: ${newLevel.name}!")
            if (xpEarned > 0) {
                appendLine("⚡ XP Earned: +$xpEarned XP")
            }
            appendLine("🌱 Minimum XP: ${newLevel.xpRequired} XP")
            appendLine()
            appendLine("Every item you classify helps keep waste out of landfills and protects our planet's future. Keep up the extraordinary work!")
        }

        return NotificationCompat.Builder(context, CHANNEL_ID_LEVEL_UP)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(shortBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(0xFFFFD700.toInt())
    }

    fun showLevelUpNotification(
        context: Context,
        newLevel: EcoLevel,
        xpEarned: Int
    ) {
        try {
            if (hasNotificationPermission(context)) {
                val notification = buildLevelUpNotification(context, newLevel, xpEarned).build()
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_LEVEL_UP, notification)
            }
        } catch (e: Exception) {
            android.util.Log.w("NotificationHelper", "Could not show level up notification", e)
        }
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
