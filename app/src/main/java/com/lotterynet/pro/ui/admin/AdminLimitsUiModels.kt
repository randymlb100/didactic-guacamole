package com.lotterynet.pro.ui.admin

import com.lotterynet.pro.core.storage.AdminOperationalLimits
import com.lotterynet.pro.core.storage.CashierSalesLimitInputs
import com.lotterynet.pro.core.storage.RechargeLimitSettings

internal enum class AdminLimitsDestination {
    OVERVIEW,
    POOL,
    CASHIERS,
    ADMIN_SELF,
    CASH_AND_RECHARGES,
    POS,
}

internal enum class AdminLimitsTone {
    PRIMARY,
    SUCCESS,
    WARNING,
    NEUTRAL,
}

internal data class AdminLimitsOverviewItem(
    val destination: AdminLimitsDestination,
    val title: String,
    val summary: String,
    val scopeLabel: String,
    val effectiveValue: String,
    val tone: AdminLimitsTone,
)

internal fun adminLimitsOverviewItems(
    adminLimits: AdminOperationalLimits,
    rechargeLimits: RechargeLimitSettings,
    poolLimits: CashierSalesLimitInputs,
    cashierLimits: CashierSalesLimitInputs,
    adminSelfLimits: CashierSalesLimitInputs?,
    posModeEnabled: Boolean,
): List<AdminLimitsOverviewItem> = listOf(
    AdminLimitsOverviewItem(
        destination = AdminLimitsDestination.POOL,
        title = "Pool de banca",
        summary = "Compartido por lotería, número y jugada.",
        scopeLabel = "GLOBAL",
        effectiveValue = "${summarizeConfiguredLimitCount(poolLimits)} reglas activas",
        tone = AdminLimitsTone.SUCCESS,
    ),
    AdminLimitsOverviewItem(
        destination = AdminLimitsDestination.CASHIERS,
        title = "Límites de cajeros",
        summary = "Venta diaria y límites por jugada para cada usuario.",
        scopeLabel = "POR USUARIO",
        effectiveValue = if (cashierLimits.daySale > 0.0) moneyLimitText(cashierLimits.daySale) else "Sin tope diario",
        tone = AdminLimitsTone.PRIMARY,
    ),
    AdminLimitsOverviewItem(
        destination = AdminLimitsDestination.ADMIN_SELF,
        title = "Límite propio del admin",
        summary = "Solo aplica cuando el admin realiza ventas.",
        scopeLabel = "SOLO ADMIN",
        effectiveValue = adminSelfLimits?.daySale?.takeIf { it > 0.0 }?.let(::moneyLimitText) ?: "Sin tope propio",
        tone = AdminLimitsTone.PRIMARY,
    ),
    AdminLimitsOverviewItem(
        destination = AdminLimitsDestination.CASH_AND_RECHARGES,
        title = "Cobros y recargas",
        summary = "Pagos de premios y fondos; no limita ventas.",
        scopeLabel = "OPERACIÓN",
        effectiveValue = "Pago ${adminLimits.cashierPayoutLimit.takeIf { it > 0.0 }?.let(::moneyLimitText) ?: "sin tope"} · Recarga ${rechargeLimits.globalPerTx.takeIf { it > 0.0 }?.let(::moneyLimitText) ?: "sin tope"}",
        tone = AdminLimitsTone.WARNING,
    ),
    AdminLimitsOverviewItem(
        destination = AdminLimitsDestination.POS,
        title = "Modo POS",
        summary = "Solo compacta la interfaz; no cambia límites.",
        scopeLabel = "INTERFAZ",
        effectiveValue = if (posModeEnabled) "Activo" else "Inactivo",
        tone = AdminLimitsTone.NEUTRAL,
    ),
)

private fun summarizeConfiguredLimitCount(limits: CashierSalesLimitInputs): Int {
    return listOf(
        limits.quiniela,
        limits.pale,
        limits.superPale,
        limits.tripleta,
        limits.pick3Straight,
        limits.pick3Box,
        limits.pick4Straight,
        limits.pick4Box,
    ).count { it > 0.0 }
}

private fun moneyLimitText(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)
