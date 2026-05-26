package com.lotterynet.pro.core.finance

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.operations.filterCashiersForSession
import com.lotterynet.pro.core.operations.cashierSortLabel
import com.lotterynet.pro.core.operations.naturalCashierNumber
import com.lotterynet.pro.core.operations.sortCashierAccountsNatural
import java.util.Locale

enum class OperationalReportHealth {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
}

enum class OperationalReportSyncStatus {
    UPDATED,
    CACHED_COPY,
    SERVER_FAILED,
}

enum class OperationalReportManualTarget {
    FROM,
    TO,
}

data class OperationalReportManualRange(
    val fromDayKey: String,
    val toDayKey: String,
)

sealed class OperationalReportActorFilter(
    open val key: String,
    open val label: String,
) {
    data object All : OperationalReportActorFilter("all", "Todos")
    data object Admin : OperationalReportActorFilter("admin", "Admin")
    data class Supervisor(
        val supervisorKey: String,
        val display: String,
    ) : OperationalReportActorFilter("supervisor:$supervisorKey", display)
    data class Cashier(
        val actorKey: String,
        val display: String,
    ) : OperationalReportActorFilter("cashier:$actorKey", display)
}

data class OperationalReportRemoteLoadResult(
    val remoteSucceeded: Boolean,
    val cacheAvailable: Boolean,
)

data class OperationalReportTrendPoint(
    val label: String,
    val summary: FinanceSummary,
    val net: Double = resolveOperationalReportNet(summary),
)

data class OperationalReportViewState(
    val periodLabel: String,
    val filter: OperationalReportActorFilter,
    val syncStatus: OperationalReportSyncStatus,
    val summary: FinanceSummary,
    val trend: List<OperationalReportTrendPoint>,
    val actorRows: List<FinanceActorPeriodRow>,
)

fun resolveOperationalReportNet(summary: FinanceSummary): Double {
    return summary.ventas + summary.recargas - summary.comision - summary.supervisorComision - summary.premiosPagados - summary.premiosPendientes
}

fun resolveOperationalReportHealth(summary: FinanceSummary): OperationalReportHealth {
    val net = resolveOperationalReportNet(summary)
    return when {
        net > 0.0 -> OperationalReportHealth.POSITIVE
        net < 0.0 -> OperationalReportHealth.NEGATIVE
        else -> OperationalReportHealth.NEUTRAL
    }
}

fun resolveOperationalReportSyncStatus(result: OperationalReportRemoteLoadResult): OperationalReportSyncStatus {
    return when {
        result.remoteSucceeded -> OperationalReportSyncStatus.UPDATED
        result.cacheAvailable -> OperationalReportSyncStatus.CACHED_COPY
        else -> OperationalReportSyncStatus.SERVER_FAILED
    }
}

fun resolveOperationalReportSyncLabel(status: OperationalReportSyncStatus): String {
    return when (status) {
        OperationalReportSyncStatus.UPDATED -> "Actualizado"
        OperationalReportSyncStatus.CACHED_COPY -> "Sin conexión usando última copia"
        OperationalReportSyncStatus.SERVER_FAILED -> "No se pudo cargar servidor"
    }
}

fun updateOperationalReportManualRange(
    fromDayKey: String,
    toDayKey: String,
    selectedDayKey: String,
    target: OperationalReportManualTarget,
): OperationalReportManualRange {
    val selected = selectedDayKey.takeIf { it.isNotBlank() } ?: fromDayKey
    val candidate = when (target) {
        OperationalReportManualTarget.FROM -> OperationalReportManualRange(selected, toDayKey)
        OperationalReportManualTarget.TO -> OperationalReportManualRange(fromDayKey, selected)
    }
    return if (candidate.fromDayKey <= candidate.toDayKey) {
        candidate
    } else {
        OperationalReportManualRange(candidate.toDayKey, candidate.fromDayKey)
    }
}

fun buildOperationalReportActorFilters(
    session: ActiveSession,
    cashiers: List<UserAccount>,
    supervisors: List<UserAccount> = emptyList(),
): List<OperationalReportActorFilter> {
    if (session.role != UserRole.ADMIN && session.role != UserRole.SUPERVISOR) return emptyList()
    return buildList {
        add(OperationalReportActorFilter.All)
        if (session.role == UserRole.ADMIN) {
            add(OperationalReportActorFilter.Admin)
            sortOperationalAccountsNatural(supervisors.filter { supervisor ->
                supervisor.active && matchesSupervisorForAdmin(session, supervisor)
            }).forEach { supervisor ->
                add(OperationalReportActorFilter.Supervisor(supervisor.id, supervisor.displayName ?: supervisor.user))
            }
        }
        sortCashierAccountsNatural(filterCashiersForSession(session, cashiers))
            .filter { it.active }
            .forEach { cashier ->
                add(OperationalReportActorFilter.Cashier(cashier.id, cashier.displayName ?: cashier.user))
            }
    }
}

private fun sortOperationalAccountsNatural(accounts: List<UserAccount>): List<UserAccount> {
    return accounts.sortedWith(
        compareBy<UserAccount> { naturalCashierNumber(it) == null }
            .thenBy { naturalCashierNumber(it) ?: Int.MAX_VALUE }
            .thenBy { cashierSortLabel(it).lowercase(Locale.US) }
            .thenBy { it.user.lowercase(Locale.US) },
    )
}

private fun matchesSupervisorForAdmin(session: ActiveSession, supervisor: UserAccount): Boolean {
    return (!session.userId.isBlank() && session.userId.equals(supervisor.adminId, ignoreCase = true)) ||
        session.username.equals(supervisor.adminUser, ignoreCase = true) ||
        (!session.banca.isNullOrBlank() && session.banca.equals(supervisor.banca, ignoreCase = true))
}

fun buildOperationalReportViewState(
    repository: LocalFinanceRepository,
    session: ActiveSession,
    preset: FinancePeriodPreset,
    anchorDayKey: String,
    fromDayKey: String?,
    toDayKey: String?,
    filter: OperationalReportActorFilter,
    syncStatus: OperationalReportSyncStatus,
): OperationalReportViewState {
    val baseScope = repository.resolveScope(session)
    val report = when (filter) {
        OperationalReportActorFilter.All -> repository.getScopedPeriodReport(
            scope = baseScope,
            preset = preset,
            anchorDayKey = anchorDayKey,
            fromDayKey = fromDayKey,
            toDayKey = toDayKey,
        )
        OperationalReportActorFilter.Admin -> repository.getScopedPeriodReport(
            scope = FinanceScope(
                type = FinanceScopeType.ACTOR,
                adminId = session.userId,
                adminUser = session.username,
                bancaName = session.banca,
                actorKey = session.userId,
                actorDisplay = session.username,
            ),
            preset = preset,
            anchorDayKey = anchorDayKey,
            fromDayKey = fromDayKey,
            toDayKey = toDayKey,
        )
        is OperationalReportActorFilter.Supervisor -> repository.getScopedPeriodReport(
            scope = repository.resolveSupervisorScope(
                adminSession = session,
                supervisorKey = filter.supervisorKey,
                supervisorDisplay = filter.display,
            ),
            preset = preset,
            anchorDayKey = anchorDayKey,
            fromDayKey = fromDayKey,
            toDayKey = toDayKey,
        )
        is OperationalReportActorFilter.Cashier -> repository.getScopedPeriodReport(
            scope = FinanceScope(
                type = FinanceScopeType.ACTOR,
                adminId = session.userId,
                adminUser = session.username,
                bancaName = session.banca,
                actorKey = filter.actorKey,
                actorDisplay = filter.display,
            ),
            preset = preset,
            anchorDayKey = anchorDayKey,
            fromDayKey = fromDayKey,
            toDayKey = toDayKey,
        )
    }
    return OperationalReportViewState(
        periodLabel = report.range.label,
        filter = filter,
        syncStatus = syncStatus,
        summary = report.summary,
        trend = report.rows
            .sortedBy { it.dayKey }
            .map { row -> OperationalReportTrendPoint(row.dayKey, row.summary) },
        actorRows = if (filter == OperationalReportActorFilter.All) {
            report.actorRows.sortedByDescending { resolveOperationalReportNet(it.summary) }
        } else {
            emptyList()
        },
    )
}

fun buildOperationalReportShareText(
    bancaName: String,
    periodLabel: String,
    filter: OperationalReportActorFilter,
    syncStatus: OperationalReportSyncStatus,
    summary: FinanceSummary,
    isSupervisorCommissionReport: Boolean = false,
): String {
    return buildString {
        appendLine(if (isSupervisorCommissionReport) "Reporte supervisor - $bancaName" else "Reporte - $bancaName")
        appendLine("Periodo: $periodLabel")
        appendLine("Operador: ${filter.label}")
        appendLine("Estado: ${resolveOperationalReportSyncLabel(syncStatus)}")
        appendLine()
        appendLine("Ingresos")
        appendLine("Venta: ${operationalReportMoney(summary.ventas)}")
        appendLine("Recarga: ${operationalReportMoney(summary.recargas)}")
        appendLine()
        appendLine("Salidas")
        appendLine("Comisión: ${operationalReportMoney(summary.comision)} (${operationalReportCommissionPercent(summary)})")
        if (summary.supervisorComision > 0.0 && !isSupervisorCommissionReport) {
            appendLine("Comisión supervisor: ${operationalReportMoney(summary.supervisorComision)}")
        }
        appendLine("Premios pagados: ${operationalReportMoney(summary.premiosPagados)}")
        appendLine("Premios pendientes: ${operationalReportMoney(summary.premiosPendientes)}")
        appendLine()
        appendLine("Resultado")
        appendLine("Caja: ${operationalReportMoney(summary.cajaDisponible)}")
        if (isSupervisorCommissionReport) {
            appendLine("Comisión supervisión: ${operationalReportMoney(summary.supervisorComision)}")
        } else {
            appendLine("Beneficio: ${operationalReportMoney(resolveOperationalReportNet(summary))}")
        }
        appendLine("Tickets: ${summary.ticketsCount}")
        appendLine("Activos: ${summary.activos}")
        appendLine("Anulados/Borrados: ${summary.anuladosCount + summary.borradosCount}")
    }.trim()
}

fun operationalReportMoney(value: Double): String {
    return com.lotterynet.pro.core.format.formatWholeMoney(value)
}

fun operationalReportCommissionPercent(summary: FinanceSummary): String {
    if (summary.ventas <= 0.0) return "0%"
    val percent = (summary.comision / summary.ventas) * 100.0
    val rounded = kotlin.math.round(percent)
    return if (kotlin.math.abs(percent - rounded) < 0.05) {
        "%,.0f%%".format(Locale.US, rounded)
    } else {
        "%,.1f%%".format(Locale.US, percent)
    }
}
