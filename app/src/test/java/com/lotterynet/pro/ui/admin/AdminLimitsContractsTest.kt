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
}
