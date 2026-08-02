package com.lotterynet.pro.ui.master

import com.lotterynet.pro.core.servicesgames.ServicesGamesModule
import com.lotterynet.pro.core.storage.MasterServicesGamesSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterModuleAccessContractsTest {

    @Test
    fun `disabling all cashiers clears only that admin individual permissions`() {
        val initial = MasterServicesGamesSettings(
            module = ServicesGamesModule.SERVICES,
            enabled = true,
            cashierAdminKeys = setOf("admin-a", "admin-b"),
            allowedCashierKeys = setOf("cashier-a1", "cashier-a2", "cashier-b1"),
        )

        val updated = updateCashierAdminScope(
            settings = initial,
            adminKey = "admin-a",
            cashierKeysForAdmin = setOf("cashier-a1", "cashier-a2"),
            enabled = false,
        )

        assertFalse("admin-a" in updated.cashierAdminKeys)
        assertTrue("admin-b" in updated.cashierAdminKeys)
        assertEquals(setOf("cashier-b1"), updated.allowedCashierKeys)
    }

    @Test
    fun `enabling all cashiers preserves individual permissions`() {
        val initial = MasterServicesGamesSettings(
            module = ServicesGamesModule.VIDEO_GAMES,
            allowedCashierKeys = setOf("cashier-b1"),
        )

        val updated = updateCashierAdminScope(
            settings = initial,
            adminKey = "admin-a",
            cashierKeysForAdmin = setOf("cashier-a1"),
            enabled = true,
        )

        assertTrue("admin-a" in updated.cashierAdminKeys)
        assertEquals(setOf("cashier-b1"), updated.allowedCashierKeys)
    }
}
