package com.lotterynet.pro.ui.shell

import android.content.Context
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.finance.FinancePeriodPreset
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.finance.OperationalReportActorFilter
import com.lotterynet.pro.core.finance.RemoteOperationalReportRepository
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.operations.filterCashiersForSession
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository

internal fun buildShellDashboardStateKey(session: ActiveSession, dayKey: String): String {
    val actorKey = session.userId.ifBlank { session.username }
    return "${session.role.name}:$actorKey:$dayKey"
}

internal fun buildShellDashboardSnapshot(
    context: Context,
    session: ActiveSession,
    dayKey: String,
): ShellDashboardSnapshot {
    val appContext = context.applicationContext
    val salesRepositoryForShell = LocalSalesRepository(appContext)
    val usersRepositoryForShell = LocalUsersRepository(appContext)
    val financeRepository = LocalFinanceRepository(
        salesRepository = salesRepositoryForShell,
        rechargeRepository = LocalRechargeRepository(appContext),
        usersRepository = usersRepositoryForShell,
    )
    val localFinanceSummary = financeRepository.getScopedPeriodReport(
        scope = financeRepository.resolveScope(session),
        preset = FinancePeriodPreset.DAY,
        anchorDayKey = dayKey,
    ).summary
    val financeSummary = loadShellAuthoritativeFinanceSummary(
        context = appContext,
        session = session,
        dayKey = dayKey,
        fallback = localFinanceSummary,
    )
    val scopedTodayTickets = filterTicketsForOperationalScope(
        session = session,
        tickets = salesRepositoryForShell.getTicketsForDay(dayKey),
        cashiers = usersRepositoryForShell.getCashiers(),
    )
    val assignedCashiersCount = if (session.role == UserRole.SUPERVISOR) {
        filterCashiersForSession(session, usersRepositoryForShell.getCashiers()).count { it.active }
    } else {
        0
    }
    val recentTickets = scopedTodayTickets
        .sortedByDescending { it.createdAtEpochMs }
        .take(6)
    val visibleSalesTotal = if (session.role == UserRole.CASHIER) {
        scopedTodayTickets
            .filterNot { it.status.equals("voided", true) || it.status.equals("invalid", true) }
            .sumOf { it.total }
    } else {
        financeSummary.ventas
    }
    val visiblePendingTotal = if (session.role == UserRole.CASHIER) {
        scopedTodayTickets
            .filter { it.status.equals("winner", true) }
            .sumOf { it.totalPrize.coerceAtLeast(0.0) }
    } else {
        financeSummary.premiosPendientes
    }
    return ShellDashboardSnapshot(
        recentTickets = recentTickets,
        salesTotal = visibleSalesTotal,
        cashTotal = resolveShellCashTotalForRole(
            role = session.role,
            visibleSalesTotal = visibleSalesTotal,
            scopedCajaDisponible = financeSummary.cajaDisponible,
        ),
        pendingTotal = visiblePendingTotal,
        assignedCashiersCount = assignedCashiersCount,
    )
}

private fun loadShellAuthoritativeFinanceSummary(
    context: Context,
    session: ActiveSession,
    dayKey: String,
    fallback: FinanceSummary,
): FinanceSummary {
    val appContext = context.applicationContext
    val tokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(appContext))
    return runCatching {
        val range = LocalFinanceRepository(
            salesRepository = LocalSalesRepository(appContext),
            rechargeRepository = LocalRechargeRepository(appContext),
            usersRepository = LocalUsersRepository(appContext),
        ).resolveRange(
            preset = FinancePeriodPreset.DAY,
            anchorDayKey = dayKey,
        )
        RemoteOperationalReportRepository(
            bearerTokenProvider = { tokenProvider.freshAccessToken() },
            bearerTokenRefresher = { tokenProvider.forceFreshAccessToken() },
        ).getReport(
            session = session,
            filter = OperationalReportActorFilter.All,
            preset = FinancePeriodPreset.DAY,
            range = range,
        ).summary
    }.getOrNull() ?: fallback
}
