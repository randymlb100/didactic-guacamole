package com.lotterynet.pro.core.notification

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lotterynet.pro.R
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryCloseDecision

data class LotteryClosingAlert(
    val lotteryId: String,
    val lotteryName: String,
    val minutesRemaining: Int,
)

object LotteryClosingNotifier {
    private const val CHANNEL_ID = "lotterynet_lottery_closing"
    private const val CHANNEL_NAME = "Cierres"
    private const val PREFS_NAME = "lottery_closing_notifications"
    private const val NOTIFICATION_ID = 7210
    const val EXTRA_PRESELECT_LOTTERY_ID = "preselect_lottery_id"

    fun notifyIfNeeded(
        context: Context,
        dateKey: String,
        alerts: List<LotteryClosingAlert>,
    ) {
        if (alerts.isEmpty()) return
        if (!hasNotificationPermission(context)) return

        val appContext = context.applicationContext
        val key = lotteryClosingNotificationKey(dateKey, alerts)
        if (key.isBlank()) return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(key, false)) return

        ensureChannel(appContext)
        val intent = Intent().setClassName(
            appContext.packageName,
            "com.lotterynet.pro.ui.sales.SalesActivity",
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PRESELECT_LOTTERY_ID, topClosingLotteryId(alerts))
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val message = lotteryClosingMessage(alerts)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(lotteryClosingTitle(alerts))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            notifyClosingAlert(appContext, notification)
            prefs.edit().putBoolean(key, true).apply()
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyClosingAlert(context: Context, notification: android.app.Notification) {
        if (!hasNotificationPermission(context)) return
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos de loterias cerca de cerrar"
            },
        )
    }
}

fun buildLotteryClosingAlerts(
    lotteries: List<LotteryCatalogItem>,
    decisionsByLotteryId: Map<String, LotteryCloseDecision>,
    thresholdMinutes: Int = 10,
): List<LotteryClosingAlert> {
    return lotteries.mapNotNull { lottery ->
        val decision = decisionsByLotteryId[lottery.id] ?: return@mapNotNull null
        if (decision.isClosed) return@mapNotNull null
        val minutes = parseLotteryClosingMinutes(decision.reason) ?: return@mapNotNull null
        if (minutes !in 0..thresholdMinutes) return@mapNotNull null
        LotteryClosingAlert(
            lotteryId = lottery.id,
            lotteryName = lottery.name,
            minutesRemaining = minutes,
        )
    }.sortedWith(compareBy<LotteryClosingAlert> { it.minutesRemaining }.thenBy { it.lotteryName })
}

fun lotteryClosingTitle(alerts: List<LotteryClosingAlert>): String {
    return if (alerts.size == 1) {
        "Loteria cerca de cerrar"
    } else {
        "${alerts.size} loterias cerca de cerrar"
    }
}

fun lotteryClosingMessage(alerts: List<LotteryClosingAlert>): String {
    return alerts.joinToString("; ") { alert ->
        "${alert.lotteryName} ${alert.minutesRemaining} min"
    }
}

fun topClosingLotteryId(alerts: List<LotteryClosingAlert>): String? {
    return alerts.firstOrNull()?.lotteryId
}

fun lotteryClosingNotificationKey(dateKey: String, alerts: List<LotteryClosingAlert>): String {
    if (dateKey.isBlank() || alerts.isEmpty()) return ""
    val ids = alerts.map { it.lotteryId }.sorted().joinToString(",")
    return "$dateKey:$ids"
}

private fun parseLotteryClosingMinutes(reason: String?): Int? {
    val text = reason?.lowercase() ?: return null
    val match = Regex("""(\d+)\s*min""").find(text) ?: return null
    return match.groupValues.getOrNull(1)?.toIntOrNull()
}
