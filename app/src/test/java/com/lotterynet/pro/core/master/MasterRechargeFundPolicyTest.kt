package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class MasterRechargeFundPolicyTest {
    private val account = UserAccount(
        id = "adm-1",
        user = "admin01",
        role = UserRole.ADMIN,
        rechargesEnabled = true,
        rechargesAssignedBalance = 10_000.0,
        rechargesBalance = 8_575.0,
    )

    @Test
    fun `snapshot keeps assigned and available separate`() {
        val snapshot = masterRechargeFundSnapshot(account)

        assertEquals(10_000.0, snapshot.assigned, 0.0)
        assertEquals(8_575.0, snapshot.available, 0.0)
        assertEquals(1_425.0, snapshot.consumed, 0.0)
    }

    @Test
    fun `replace fund intentionally resets available to new assigned amount`() {
        val updated = replaceMasterRechargeFund(account, enabled = true, amount = 20_000.0)

        assertEquals(20_000.0, updated.rechargesAssignedBalance, 0.0)
        assertEquals(20_000.0, updated.rechargesBalance, 0.0)
    }

    @Test
    fun `add balance preserves assigned fund and increases available only`() {
        val updated = addMasterRechargeBalance(account, amount = 1_500.0)

        assertEquals(10_000.0, updated.rechargesAssignedBalance, 0.0)
        assertEquals(10_075.0, updated.rechargesBalance, 0.0)
    }
}
