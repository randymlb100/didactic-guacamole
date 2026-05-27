package com.lotterynet.pro.ui.admin

import com.lotterynet.pro.core.storage.CashierSalesLimitInputs

internal data class AdminLimitSection(
    val label: String,
    val description: String,
)

internal data class CashierSalesLimitVisibilityContract(
    val title: String,
    val meta: String,
    val description: String,
    val currentDaySaleLabel: String,
    val currentDaySaleValue: String,
    val daySaleLabel: String,
    val daySaleHelp: String,
)

internal enum class AdminLimitScope {
    ADMIN_SELF,
    CASHIER_DEFAULTS,
    CASHIER_SPECIFIC,
}

internal data class AdminLimitScopeContract(
    val selectedScope: AdminLimitScope,
    val title: String,
    val emptyStateCopy: String,
    val adminSalesUnlimitedWhenEmpty: Boolean,
    val cashierDefaultsAffectAdmin: Boolean,
    val scopeLabels: List<String>,
)

internal fun adminLimitSections(): List<AdminLimitSection> = listOf(
    AdminLimitSection("Mis límites de venta", "Topes propios del admin"),
    AdminLimitSection("Límite de venta de cajeros", "Dinero diario y topes por jugada"),
    AdminLimitSection("Pagos", "Control de cobro por cajero"),
    AdminLimitSection("Recargas", "Topes globales y master"),
    AdminLimitSection("Sistema", "Modo compacto para POS"),
)

internal fun adminSalesLimitFieldLabels(): List<String> = listOf(
    "Quiniela",
    "Pale",
    "Super Pale",
    "Tripleta",
    "Pick 3 Straight",
    "Pick 3 Box",
    "Pick 4 Straight",
    "Pick 4 Box",
)

internal fun recommendedCashierSalesLimits(): CashierSalesLimitInputs = CashierSalesLimitInputs()

internal fun resolveAdminLimitScopeContract(
    selectedScope: AdminLimitScope,
    adminHasSelfLimits: Boolean,
    cashierDefaultsEnabled: Boolean,
): AdminLimitScopeContract {
    val title = when (selectedScope) {
        AdminLimitScope.ADMIN_SELF -> "Mis límites"
        AdminLimitScope.CASHIER_DEFAULTS -> "Todos los cajeros"
        AdminLimitScope.CASHIER_SPECIFIC -> "Por cajero"
    }
    return AdminLimitScopeContract(
        selectedScope = selectedScope,
        title = title,
        emptyStateCopy = if (!adminHasSelfLimits && selectedScope == AdminLimitScope.ADMIN_SELF) {
            "Admin vende sin tope si está vacío"
        } else {
            "Límites activos"
        },
        adminSalesUnlimitedWhenEmpty = !adminHasSelfLimits,
        cashierDefaultsAffectAdmin = false,
        scopeLabels = listOf("Mis límites", "Todos los cajeros", "Por cajero"),
    )
}

internal fun resolveCashierSalesLimitVisibilityContract(
    salesLimits: CashierSalesLimitInputs,
): CashierSalesLimitVisibilityContract {
    return CashierSalesLimitVisibilityContract(
        title = "Límite de venta de cajeros",
        meta = "Dinero por día",
        description = "Este es el dinero máximo que puede vender cada cajero en un día. Los límites por jugada quedan debajo.",
        currentDaySaleLabel = "Venta diaria actual",
        currentDaySaleValue = if (salesLimits.daySale > 0.0) {
            com.lotterynet.pro.core.format.formatWholeMoney(salesLimits.daySale)
        } else {
            "Sin tope"
        },
        daySaleLabel = "Dinero máximo que cada cajero puede vender por día",
        daySaleHelp = "0 deja al cajero sin tope diario de venta.",
    )
}
