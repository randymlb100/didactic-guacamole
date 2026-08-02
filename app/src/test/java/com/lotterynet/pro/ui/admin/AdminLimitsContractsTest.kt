package com.lotterynet.pro.ui.admin

import com.lotterynet.pro.core.storage.AdminOperationalLimits
import com.lotterynet.pro.core.storage.CashierSalesLimitInputs
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminLimitsContractsTest {

    @Test
    fun `global cashier payout limit is published inside cashier sales limits`() {
        val resolved = resolveDefaultSalesLimitsForServer(
            salesLimits = CashierSalesLimitInputs(payout = 0.0, quiniela = 250.0),
            adminLimits = AdminOperationalLimits(cashierPayoutLimit = 1200.0),
        )

        assertEquals(1200.0, resolved.payout, 0.001)
        assertEquals(250.0, resolved.quiniela, 0.001)
    }

    @Test
    fun `specific cashier payout limit wins over global payout fallback`() {
        val resolved = resolveDefaultSalesLimitsForServer(
            salesLimits = CashierSalesLimitInputs(payout = 700.0),
            adminLimits = AdminOperationalLimits(cashierPayoutLimit = 1200.0),
        )

        assertEquals(700.0, resolved.payout, 0.001)
    }

    @Test
    fun `cashier daily sales limit copy explains it is money the cashier can sell`() {
        val contract = resolveCashierSalesLimitVisibilityContract(
            CashierSalesLimitInputs(daySale = 10_000.0),
        )

        assertEquals("Límite de venta de cajeros", contract.title)
        assertEquals("Dinero máximo que cada cajero puede vender por día", contract.daySaleLabel)
        assertEquals("$ 10,000", contract.currentDaySaleValue)
        assertEquals("0 deja al cajero sin tope diario de venta.", contract.daySaleHelp)
    }

    @Test
    fun `admin self limits are separate from cashier defaults`() {
        val contract = resolveAdminLimitScopeContract(
            selectedScope = AdminLimitScope.ADMIN_SELF,
            adminHasSelfLimits = false,
            cashierDefaultsEnabled = true,
        )

        assertEquals(AdminLimitScope.ADMIN_SELF, contract.selectedScope)
        assertEquals("Mis límites", contract.title)
        assertEquals("Admin vende sin tope si está vacío", contract.emptyStateCopy)
        assertEquals(true, contract.adminSalesUnlimitedWhenEmpty)
        assertEquals(false, contract.cashierDefaultsAffectAdmin)
        assertEquals(listOf("Propio", "Global", "Por cajero"), contract.scopeLabels)
    }

    @Test
    fun `pos mode accepts only the protected password`() {
        assertEquals(true, verifyPosModePassword("123"))
        assertEquals(false, verifyPosModePassword(""))
        assertEquals(false, verifyPosModePassword("0123"))
        assertEquals(false, verifyPosModePassword("1234"))
    }

    @Test
    fun `pos mode action keeps a simple system label`() {
        assertEquals("Modo POS", posModeActionLabel())
    }

    @Test
    fun `limits screen groups sections into visible admin tabs`() {
        assertEquals(
            listOf("Resumen", "Pool de banca", "Límites de cajeros", "Límite propio del admin", "Cobros y recargas", "Modo POS"),
            adminLimitsSectionOptions().map { it.label },
        )
    }

    @Test
    fun `limits overview keeps pool cashier admin cash and pos scopes separate`() {
        val items = adminLimitsOverviewItems(
            adminLimits = AdminOperationalLimits(cashierPayoutLimit = 1_000.0),
            rechargeLimits = com.lotterynet.pro.core.storage.RechargeLimitSettings(globalPerTx = 500.0),
            poolLimits = CashierSalesLimitInputs(quiniela = 10_000.0),
            cashierLimits = CashierSalesLimitInputs(daySale = 2_000.0),
            adminSelfLimits = CashierSalesLimitInputs(daySale = 3_000.0),
            posModeEnabled = true,
        )

        assertEquals(
            listOf(
                AdminLimitsDestination.POOL,
                AdminLimitsDestination.CASHIERS,
                AdminLimitsDestination.ADMIN_SELF,
                AdminLimitsDestination.CASH_AND_RECHARGES,
                AdminLimitsDestination.POS,
            ),
            items.map { it.destination },
        )
        assertEquals("$ 2,000", items[1].effectiveValue)
        assertEquals("Activo", items.last().effectiveValue)
    }
}
