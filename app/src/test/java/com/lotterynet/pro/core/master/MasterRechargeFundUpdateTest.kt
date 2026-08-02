package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterRechargeFundUpdateTest {
    private val original = UserAccount(
        id = "adm-1",
        user = "dueno01",
        role = UserRole.ADMIN,
        rechargesEnabled = true,
        rechargesAssignedBalance = 2_000.0,
        rechargesBalance = 1_250.0,
        updatedAtEpochMs = 10L,
    )

    @Test
    fun `server confirms exact 4037 amount`() {
        val writes = mutableListOf<UserAccount>()
        val coordinator = MasterRechargeFundUpdateCoordinator(
            writeLocal = { account -> writes.add(account) },
            writeRemote = { _, _, amount ->
                MasterRechargeFundServerReceipt(
                    requestedAmount = amount,
                    persistedAmount = 4_037.0,
                    updatedAt = "2026-06-18T12:00:00Z",
                )
            },
        )

        val result = coordinator.update(original, enabled = true, amount = 4_037.0)

        assertTrue(result is MasterRechargeFundUpdateResult.Confirmed)
        assertEquals(4_037.0, (result as MasterRechargeFundUpdateResult.Confirmed).account.rechargesBalance, 0.0)
        assertEquals(listOf(4_037.0), writes.map { it.rechargesBalance })
    }

    @Test
    fun `server mismatch restores complete previous account`() {
        val writes = mutableListOf<UserAccount>()
        val coordinator = MasterRechargeFundUpdateCoordinator(
            writeLocal = { account -> writes.add(account) },
            writeRemote = { _, _, amount ->
                MasterRechargeFundServerReceipt(
                    requestedAmount = amount,
                    persistedAmount = 4_036.99,
                    updatedAt = "2026-06-18T12:00:00Z",
                )
            },
        )

        val result = coordinator.update(original, enabled = true, amount = 4_037.0)

        assertTrue(result is MasterRechargeFundUpdateResult.Rejected)
        assertSame(original, (result as MasterRechargeFundUpdateResult.Rejected).restoredAccount)
        assertEquals(listOf(4_037.0, 1_250.0), writes.map { it.rechargesBalance })
    }

    @Test
    fun `server failure rolls back optimistic local account`() {
        val writes = mutableListOf<UserAccount>()
        val coordinator = MasterRechargeFundUpdateCoordinator(
            writeLocal = { account -> writes.add(account) },
            writeRemote = { _, _, _ -> error("network down") },
        )

        val result = coordinator.update(original, enabled = false, amount = 4_037.0)

        assertTrue(result is MasterRechargeFundUpdateResult.RolledBack)
        assertSame(original, (result as MasterRechargeFundUpdateResult.RolledBack).restoredAccount)
        assertEquals(listOf(4_037.0, 1_250.0), writes.map { it.rechargesBalance })
    }

    @Test
    fun `adding balance preserves assigned fund`() {
        val writes = mutableListOf<UserAccount>()
        val coordinator = MasterRechargeBalanceUpdateCoordinator(
            writeLocal = { account -> writes.add(account) },
            writeRemote = { _, amount ->
                MasterRechargeFundServerReceipt(
                    requestedAmount = amount,
                    persistedAmount = amount,
                    updatedAt = "2026-07-16T12:00:00Z",
                )
            },
        )

        val result = coordinator.add(original, amount = 750.0)

        assertTrue(result is MasterRechargeFundUpdateResult.Confirmed)
        val updated = (result as MasterRechargeFundUpdateResult.Confirmed).account
        assertEquals(2_000.0, updated.rechargesAssignedBalance, 0.0)
        assertEquals(2_000.0, updated.rechargesBalance, 0.0)
        assertEquals(listOf(2_000.0), writes.map { it.rechargesBalance })
    }
}
