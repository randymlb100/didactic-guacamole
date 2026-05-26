package com.lotterynet.pro.core.notification

import android.annotation.SuppressLint
import android.Manifest
import android.app.Activity
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
import com.lotterynet.pro.core.format.formatWholeMoney
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.isPaidStatus

object WinningTicketNotifier {
    private const val CHANNEL_ID = "lotterynet_winning_tickets"
    private const val CHANNEL_NAME = "Premios"
    private const val PREFS_NAME = "winning_ticket_notifications"
    private const val REQUEST_CODE = 9307
    const val WINNING_TICKET_ACTION_MODE = "pagar"

    fun requestPermissionIfNeeded(activity: Activity, role: UserRole) {
        if (!canRequestNotificationsForRole(role)) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE,
        )
    }

    fun notifyIfNewPendingWinner(
        context: Context,
        previous: TicketRecord?,
        current: TicketRecord,
        activeSession: ActiveSession?,
    ) {
        if (!shouldNotifyTicketForActiveSession(activeSession, current)) return
        if (!shouldNotifyWinningTicket(previous, current)) return
        if (!hasNotificationPermission(context)) return

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = notificationKey(current)
        if (prefs.getBoolean(key, false)) return

        ensureChannel(appContext)
        val contentIntent = PendingIntent.getActivity(
            appContext,
            current.id.hashCode(),
            buildWinningTicketIntent(appContext, current),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(winningTicketTitle(current))
            .setContentText(winningTicketMessage(current))
            .setStyle(NotificationCompat.BigTextStyle().bigText(winningTicketMessage(current)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            notifyWinningTicket(appContext, current.id.hashCode(), notification)
            prefs.edit().putBoolean(key, true).apply()
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyWinningTicket(context: Context, notificationId: Int, notification: android.app.Notification) {
        if (!hasNotificationPermission(context)) return
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    internal fun canRequestNotificationsForRole(role: UserRole): Boolean {
        return role == UserRole.ADMIN || role == UserRole.CASHIER
    }

    internal fun shouldNotifyWinningTicket(previous: TicketRecord?, current: TicketRecord): Boolean {
        if (!isPendingWinningTicket(current)) return false
        val previousPrize = previous?.let(::pendingPrizeAmount) ?: 0.0
        val currentPrize = pendingPrizeAmount(current)
        return previous == null ||
            !isPendingWinningTicket(previous) ||
            previousPrize != currentPrize
    }

    internal fun isPendingWinningTicket(ticket: TicketRecord): Boolean {
        if (ticket.isPaidStatus()) return false
        if (ticket.status.equals("voided", true) || ticket.status.equals("nulled", true)) return false
        if (ticket.status.equals("invalid", true)) return false
        return ticket.status.equals("winner", true) || ticket.totalPrize > 0.0
    }

    internal fun shouldNotifyTicketForActiveSession(session: ActiveSession?, ticket: TicketRecord): Boolean {
        val active = session ?: return false
        return when (active.role) {
            UserRole.ADMIN -> ticket.adminId.equals(active.userId, true) ||
                ticket.adminUser.equals(active.username, true)
            UserRole.CASHIER -> ticket.sellerId.equals(active.userId, true) ||
                ticket.sellerUser.equals(active.username, true)
            else -> false
        }
    }

    internal fun winningTicketMessage(ticket: TicketRecord): String {
        return listOf(
            "Vendedor: ${winnerSellerLabel(ticket)}",
            "Ticket: ${winnerTicketCode(ticket)}",
            "Premio: ${formatWholeMoney(pendingPrizeAmount(ticket))}",
            "Loteria: ${winnerLotteryLabel(ticket)}",
            "Jugada: ${winnerPlayLabel(ticket)}",
        ).joinToString("\n")
    }

    internal fun winningTicketTitle(ticket: TicketRecord): String {
        return "Ticket ganador - ${winnerSellerLabel(ticket)}"
    }

    private fun pendingPrizeAmount(ticket: TicketRecord): Double {
        return ticket.totalPrize.coerceAtLeast(0.0)
    }

    private fun winnerSellerLabel(ticket: TicketRecord): String {
        return ticket.sellerUser?.takeIf { it.isNotBlank() }
            ?: ticket.sellerId?.takeIf { it.isNotBlank() }
            ?: ticket.adminUser?.takeIf { it.isNotBlank() }
            ?: "Sin vendedor"
    }

    private fun winnerTicketCode(ticket: TicketRecord): String {
        return ticket.serial?.takeIf { it.isNotBlank() } ?: ticket.id
    }

    private fun winnerLotteryLabel(ticket: TicketRecord): String {
        return ticket.plays
            .mapNotNull { play -> play.lotteryName?.takeIf { it.isNotBlank() } }
            .distinct()
            .joinToString(", ")
            .ifBlank { "Sin loteria" }
    }

    private fun winnerPlayLabel(ticket: TicketRecord): String {
        return ticket.plays
            .take(4)
            .joinToString(", ") { play ->
                "${play.playType} ${play.number} ${formatWholeMoney(play.amount)}"
            }
            .ifBlank { "Sin jugada" }
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
                description = "Avisos de tickets ganadores"
            },
        )
    }

    private fun notificationKey(ticket: TicketRecord): String {
        return "${ticket.id}:${pendingPrizeAmount(ticket)}"
    }

    private fun buildWinningTicketIntent(context: Context, ticket: TicketRecord): Intent {
        return Intent().setClassName(
            context.packageName,
            "com.lotterynet.pro.ui.tickets.TicketOfficialActivity",
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ticket_id", ticket.id)
            putExtra("ticket_epoch", ticket.createdAtEpochMs)
            putExtra("ticket_action_mode", WINNING_TICKET_ACTION_MODE)
        }
    }
}
