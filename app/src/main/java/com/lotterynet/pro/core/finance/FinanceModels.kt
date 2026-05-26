package com.lotterynet.pro.core.finance

enum class FinanceAlertTone {
    DANGER,
    WARNING,
    NOTICE,
}

data class FinanceAlert(
    val label: String,
    val text: String,
    val tone: FinanceAlertTone,
)

data class FinanceSummary(
    val ventas: Double = 0.0,
    val ticketsCount: Int = 0,
    val activos: Int = 0,
    val ganadores: Int = 0,
    val pagados: Int = 0,
    val anuladosCount: Int = 0,
    val anuladosMonto: Double = 0.0,
    val invalidosCount: Int = 0,
    val invalidosMonto: Double = 0.0,
    val borradosCount: Int = 0,
    val borradosMonto: Double = 0.0,
    val fueraDeFinanzaCount: Int = 0,
    val fueraDeFinanzaMonto: Double = 0.0,
    val premiosPagados: Double = 0.0,
    val premiosPendientes: Double = 0.0,
    val recargas: Double = 0.0,
    val comision: Double = 0.0,
    val supervisorComision: Double = 0.0,
    val cajaDisponible: Double = 0.0,
    val avgTicket: Double = 0.0,
    val alertas: List<FinanceAlert> = emptyList(),
) {
    val tickets: Int
        get() = ticketsCount

    val anulados: Int
        get() = anuladosCount

    val netoProyectado: Double
        get() = cajaDisponible
}

data class CashierFinanceSummary(
    val actorKey: String,
    val actorDisplay: String,
    val summary: FinanceSummary,
)

data class TurnoFinanceSummary(
    val actorKey: String,
    val actorDisplay: String,
    val startedAtEpochMs: Long,
    val summary: FinanceSummary,
)

enum class FinanceScopeType {
    BANK,
    ACTOR,
}

data class FinanceScope(
    val type: FinanceScopeType,
    val adminId: String? = null,
    val adminUser: String? = null,
    val bancaName: String? = null,
    val actorKey: String? = null,
    val actorDisplay: String? = null,
    val allowedActorKeys: Set<String> = emptySet(),
    val supervisorId: String? = null,
    val supervisorUser: String? = null,
)

enum class FinancePeriodPreset {
    DAY,
    WEEK,
    QUINCENA,
    MONTH,
    CALENDAR,
}

data class FinanceResolvedRange(
    val preset: FinancePeriodPreset,
    val anchorDayKey: String,
    val fromDayKey: String,
    val toDayKey: String,
    val label: String,
)

data class FinancePeriodRow(
    val dayKey: String,
    val summary: FinanceSummary,
)

data class FinanceActorPeriodRow(
    val actorKey: String,
    val actorDisplay: String,
    val summary: FinanceSummary,
)

data class FinancePeriodReport(
    val scope: FinanceScope,
    val preset: FinancePeriodPreset,
    val range: FinanceResolvedRange,
    val fromDayKey: String,
    val toDayKey: String,
    val summary: FinanceSummary,
    val rows: List<FinancePeriodRow>,
    val actorRows: List<FinanceActorPeriodRow> = emptyList(),
)

data class FinanceHistoryEntry(
    val id: String,
    val createdAtEpochMs: Long,
    val dayKey: String,
    val recordType: String,
    val scopeType: String,
    val targetId: String,
    val targetName: String,
    val periodLabel: String,
    val summary: FinanceSummary,
    val closeCash: Double? = null,
    val closeDiff: Double? = null,
)
