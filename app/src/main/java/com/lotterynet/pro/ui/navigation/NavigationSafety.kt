package com.lotterynet.pro.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.permissions.RoleCapability
import com.lotterynet.pro.core.permissions.canRolePerform
import com.lotterynet.pro.ui.admin.AdminAlertsActivity
import com.lotterynet.pro.ui.admin.AdminAuditActivity
import com.lotterynet.pro.ui.admin.AdminConfigActivity
import com.lotterynet.pro.ui.admin.AdminDashboardActivity
import com.lotterynet.pro.ui.admin.AdminLimitsActivity
import com.lotterynet.pro.ui.admin.AdminLotteryMonitorActivity
import com.lotterynet.pro.ui.admin.AdminMonitorActivity
import com.lotterynet.pro.ui.admin.AdminWinnersActivity
import com.lotterynet.pro.ui.finance.FinanceActivity
import com.lotterynet.pro.ui.login.LoginActivity
import com.lotterynet.pro.ui.master.MasterCreateBankActivity
import com.lotterynet.pro.ui.master.MasterDashboardActivity
import com.lotterynet.pro.ui.printer.PrinterActivity
import com.lotterynet.pro.ui.report.OperationalReportActivity
import com.lotterynet.pro.ui.recharge.RecargasActivity
import com.lotterynet.pro.ui.results.ResultsActivity
import com.lotterynet.pro.ui.sales.SalesActivity
import com.lotterynet.pro.ui.shell.ShellActivity
import com.lotterynet.pro.ui.tickets.TicketLookupActivity
import com.lotterynet.pro.ui.tickets.TicketOfficialActivity
import com.lotterynet.pro.ui.tickets.TicketSummaryActivity
import com.lotterynet.pro.ui.users.UserAccountsActivity
import java.util.Locale

enum class NativeDestination(
    val activityClassName: String,
) {
    LOGIN(LoginActivity::class.java.name),
    SHELL_MENU(ShellActivity::class.java.name),
    SALES(SalesActivity::class.java.name),
    TICKET_SUMMARY(TicketSummaryActivity::class.java.name),
    TICKET_LOOKUP(TicketLookupActivity::class.java.name),
    TICKET_OFFICIAL(TicketOfficialActivity::class.java.name),
    FINANCE(FinanceActivity::class.java.name),
    OPERATIONAL_REPORT(OperationalReportActivity::class.java.name),
    RESULTS(ResultsActivity::class.java.name),
    RECHARGE(RecargasActivity::class.java.name),
    PRINTER(PrinterActivity::class.java.name),
    ADMIN_WINNERS(AdminWinnersActivity::class.java.name),
    USER_ACCOUNTS(UserAccountsActivity::class.java.name),
    ADMIN_LIMITS(AdminLimitsActivity::class.java.name),
    ADMIN_MONITOR(AdminMonitorActivity::class.java.name),
    ADMIN_LOTTERY_MONITOR(AdminLotteryMonitorActivity::class.java.name),
    ADMIN_ALERTS(AdminAlertsActivity::class.java.name),
    ADMIN_CONFIG(AdminConfigActivity::class.java.name),
    ADMIN_AUDIT(AdminAuditActivity::class.java.name),
    ADMIN_DASHBOARD(AdminDashboardActivity::class.java.name),
    MASTER_DASHBOARD(MasterDashboardActivity::class.java.name),
    MASTER_CREATE_BANK(MasterCreateBankActivity::class.java.name),
}

fun allowedNativeDestinations(role: UserRole): Set<NativeDestination> {
    if (role == UserRole.UNKNOWN) return setOf(NativeDestination.LOGIN)
    return buildSet {
        add(NativeDestination.SHELL_MENU)
        if (canRolePerform(role, RoleCapability.SELL_TICKETS)) add(NativeDestination.SALES)
        if (canRolePerform(role, RoleCapability.VIEW_TICKETS)) {
            add(NativeDestination.TICKET_SUMMARY)
            add(NativeDestination.TICKET_OFFICIAL)
        }
        if (
            canRolePerform(role, RoleCapability.DUPLICATE_TICKETS) ||
            canRolePerform(role, RoleCapability.PAY_TICKETS) ||
            canRolePerform(role, RoleCapability.DELETE_TICKETS) ||
            canRolePerform(role, RoleCapability.DELETE_OWN_RECENT_TICKET)
        ) {
            add(NativeDestination.TICKET_LOOKUP)
        }
        if (canRolePerform(role, RoleCapability.VIEW_FINANCE)) add(NativeDestination.FINANCE)
        if (canRolePerform(role, RoleCapability.VIEW_REPORTS)) add(NativeDestination.OPERATIONAL_REPORT)
        if (canRolePerform(role, RoleCapability.VIEW_RESULTS)) add(NativeDestination.RESULTS)
        if (canRolePerform(role, RoleCapability.SELL_RECHARGES)) add(NativeDestination.RECHARGE)
        if (canRolePerform(role, RoleCapability.PRINT)) add(NativeDestination.PRINTER)
        if (canRolePerform(role, RoleCapability.MANAGE_USERS)) add(NativeDestination.USER_ACCOUNTS)
        if (canRolePerform(role, RoleCapability.MANAGE_LIMITS)) add(NativeDestination.ADMIN_LIMITS)
        if (canRolePerform(role, RoleCapability.MONITOR_OPERATIONS)) {
            add(NativeDestination.ADMIN_MONITOR)
            add(NativeDestination.ADMIN_LOTTERY_MONITOR)
        }
        if (canRolePerform(role, RoleCapability.MANAGE_SYSTEM)) {
            add(NativeDestination.ADMIN_WINNERS)
            add(NativeDestination.ADMIN_ALERTS)
            add(NativeDestination.ADMIN_CONFIG)
            add(NativeDestination.ADMIN_DASHBOARD)
        }
        if (canRolePerform(role, RoleCapability.VIEW_AUDIT)) add(NativeDestination.ADMIN_AUDIT)
        if (canRolePerform(role, RoleCapability.MANAGE_BANKS)) {
            add(NativeDestination.MASTER_DASHBOARD)
            add(NativeDestination.MASTER_CREATE_BANK)
        }
    }
}

fun canOpenNativeDestination(role: UserRole, destination: NativeDestination): Boolean {
    return destination in allowedNativeDestinations(role)
}

fun resolveBlockedDestinationFallback(
    role: UserRole,
    attemptedDestination: NativeDestination,
): NativeDestination {
    if (canOpenNativeDestination(role, attemptedDestination)) return attemptedDestination
    return when (role) {
        UserRole.MASTER -> NativeDestination.MASTER_DASHBOARD
        UserRole.ADMIN, UserRole.SUPERVISOR, UserRole.CASHIER -> NativeDestination.SHELL_MENU
        UserRole.UNKNOWN -> NativeDestination.LOGIN
    }
}

fun resolveNativeDestinationByActivity(activityClassName: String): NativeDestination? {
    return NativeDestination.entries.firstOrNull { it.activityClassName == activityClassName }
}

fun normalizeTicketLookupMode(raw: String?): String {
    return when (raw?.trim()?.lowercase(Locale.US)) {
        "pagar" -> "pagar"
        "anular" -> "anular"
        "duplicar", "buscar", "lookup", "ticket_lookup" -> "duplicar"
        else -> "duplicar"
    }
}

fun normalizeTicketLookupModeForRole(role: UserRole, raw: String?): String {
    val normalized = normalizeTicketLookupMode(raw)
    return if (role == UserRole.SUPERVISOR && normalized != "duplicar") {
        "duplicar"
    } else {
        normalized
    }
}

fun safeNativeHomeActivityClassName(role: UserRole): String {
    return when (resolveNativeHomeRoute(role)) {
        NativeHomeRoute.SALES -> NativeDestination.SALES.activityClassName
        NativeHomeRoute.SHELL_MENU -> NativeDestination.SHELL_MENU.activityClassName
        NativeHomeRoute.MASTER_DASHBOARD -> NativeDestination.MASTER_DASHBOARD.activityClassName
        NativeHomeRoute.LOGIN -> NativeDestination.LOGIN.activityClassName
    }
}

fun safeNativeDestinationIntent(
    context: Context,
    role: UserRole,
    destination: NativeDestination,
    ticketLookupMode: String? = null,
): Intent {
    val resolved = resolveBlockedDestinationFallback(role, destination)
    return intentForDestination(context, resolved).apply {
        if (resolved == NativeDestination.SHELL_MENU) {
            putExtra(ShellActivity.EXTRA_FORCE_MENU, true)
        }
        if (resolved == NativeDestination.TICKET_LOOKUP) {
            putExtra(TicketLookupActivity.EXTRA_MODE, normalizeTicketLookupModeForRole(role, ticketLookupMode))
        }
    }
}

fun startSafeNativeDestination(
    context: Context,
    role: UserRole,
    destination: NativeDestination,
    ticketLookupMode: String? = null,
) {
    runCatching {
        context.startActivity(safeNativeDestinationIntent(context, role, destination, ticketLookupMode))
    }.onFailure { throwable ->
        NativeCrashReporter(context).recordHandled("native_navigation:${destination.name}", throwable)
        context.startActivity(intentForDestination(context, NativeDestination.LOGIN))
    }
}

fun redirectIfNativeDestinationBlocked(
    activity: Activity,
    role: UserRole?,
    destination: NativeDestination,
): Boolean {
    if (role == null) {
        activity.startActivity(intentForDestination(activity, NativeDestination.LOGIN).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        activity.finish()
        return true
    }
    if (canOpenNativeDestination(role, destination)) return false
    activity.startActivity(safeNativeDestinationIntent(activity, role, destination))
    activity.finish()
    return true
}

fun intentForDestination(context: Context, destination: NativeDestination): Intent {
    return when (destination) {
        NativeDestination.LOGIN -> Intent(context, LoginActivity::class.java)
        NativeDestination.SHELL_MENU -> Intent(context, ShellActivity::class.java)
        NativeDestination.SALES -> Intent(context, SalesActivity::class.java)
        NativeDestination.TICKET_SUMMARY -> Intent(context, TicketSummaryActivity::class.java)
        NativeDestination.TICKET_LOOKUP -> Intent(context, TicketLookupActivity::class.java)
        NativeDestination.TICKET_OFFICIAL -> Intent(context, TicketOfficialActivity::class.java)
        NativeDestination.FINANCE -> Intent(context, FinanceActivity::class.java)
        NativeDestination.OPERATIONAL_REPORT -> Intent(context, OperationalReportActivity::class.java)
        NativeDestination.RESULTS -> Intent(context, ResultsActivity::class.java)
        NativeDestination.RECHARGE -> Intent(context, RecargasActivity::class.java)
        NativeDestination.PRINTER -> Intent(context, PrinterActivity::class.java)
        NativeDestination.ADMIN_WINNERS -> Intent(context, AdminWinnersActivity::class.java)
        NativeDestination.USER_ACCOUNTS -> Intent(context, UserAccountsActivity::class.java)
        NativeDestination.ADMIN_LIMITS -> Intent(context, AdminLimitsActivity::class.java)
        NativeDestination.ADMIN_MONITOR -> Intent(context, AdminMonitorActivity::class.java)
        NativeDestination.ADMIN_LOTTERY_MONITOR -> Intent(context, AdminLotteryMonitorActivity::class.java)
        NativeDestination.ADMIN_ALERTS -> Intent(context, AdminAlertsActivity::class.java)
        NativeDestination.ADMIN_CONFIG -> Intent(context, AdminConfigActivity::class.java)
        NativeDestination.ADMIN_AUDIT -> Intent(context, AdminAuditActivity::class.java)
        NativeDestination.ADMIN_DASHBOARD -> Intent(context, AdminDashboardActivity::class.java)
        NativeDestination.MASTER_DASHBOARD -> Intent(context, MasterDashboardActivity::class.java)
        NativeDestination.MASTER_CREATE_BANK -> Intent(context, MasterCreateBankActivity::class.java)
    }
}
