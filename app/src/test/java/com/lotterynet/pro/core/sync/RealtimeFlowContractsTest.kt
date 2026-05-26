package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.master.cacheMasterUpdatedAtMemory
import com.lotterynet.pro.core.master.cacheMasterValueMemory
import com.lotterynet.pro.core.master.clearMasterMemoryCache
import com.lotterynet.pro.core.master.readMasterUpdatedAtMemoryCache
import com.lotterynet.pro.core.master.readMasterValueMemoryCache
import com.lotterynet.pro.core.perf.PosPerformanceBudget
import com.lotterynet.pro.core.results.shouldForceLiveResultsFetch
import com.lotterynet.pro.ui.sales.CASHIER_LIMIT_PULL_INTERVAL_MS
import com.lotterynet.pro.ui.sales.SALES_EXPOSURE_REFRESH_INTERVAL_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeFlowContractsTest {
    @Test
    fun `remote master cache and ticket stamp caches expire locally before another network hit`() {
        clearMasterMemoryCache()
        clearTicketUpdatedAtMemoryCache()

        cacheMasterValueMemory("cashier_limits:admin-1", "{}", nowMs = 1_000L)
        cacheMasterUpdatedAtMemory("cashier_limits:admin-1", "2026-05-14T00:00:00Z", nowMs = 1_000L)
        cacheTicketUpdatedAt("admin-1", "2026-05-14T00:00:00Z", nowMs = 1_000L)

        assertEquals("{}", readMasterValueMemoryCache("cashier_limits:admin-1", nowMs = 5_000L))
        assertEquals("2026-05-14T00:00:00Z", readMasterUpdatedAtMemoryCache("cashier_limits:admin-1", nowMs = 5_000L))
        assertEquals("2026-05-14T00:00:00Z", readTicketUpdatedAtCache("admin-1", nowMs = 5_000L))
        assertEquals(null, readMasterValueMemoryCache("cashier_limits:admin-1", nowMs = 40_000L))
        assertEquals(null, readMasterUpdatedAtMemoryCache("cashier_limits:admin-1", nowMs = 40_000L))
        assertEquals(null, readTicketUpdatedAtCache("admin-1", nowMs = 70_000L))
    }

    @Test
    fun `empty remote ticket stamp is cached to prevent repeated updated-at calls`() {
        clearTicketUpdatedAtMemoryCache()

        cacheTicketUpdatedAt("admin-empty", null, nowMs = 1_000L)

        val entry = readTicketUpdatedAtCacheEntry("admin-empty", nowMs = 5_000L)
        assertNotNull(entry)
        assertEquals(null, entry?.updatedAt)
        assertEquals(null, readTicketUpdatedAtCacheEntry("admin-empty", nowMs = 70_000L))
    }

    @Test
    fun `manual ticket sync force still hydrates remote for live operations`() {
        assertTrue(
            shouldHydrateOperationalRemote(
                lastRemoteUpdatedAt = "2026-04-30T10:00:00Z",
                remoteUpdatedAt = "2026-04-30T10:00:00Z",
                force = true,
            ),
        )
    }

    @Test
    fun `ticket sync skips only when remote stamp did not change and force is false`() {
        assertFalse(
            shouldHydrateOperationalRemote(
                lastRemoteUpdatedAt = "2026-04-30T10:00:00Z",
                remoteUpdatedAt = "2026-04-30T10:00:00Z",
                force = false,
            ),
        )
    }

    @Test
    fun `cashier limits remain scoped by owner`() {
        assertEquals("cashier_limits:admin-1", cashierLimitRemoteKey("admin-1"))
        assertEquals("cashier_limits:admin-2", cashierLimitRemoteKey("admin-2"))
    }

    @Test
    fun `cashier owner key resolves to admin for multi banca limits and tickets`() {
        val session = ActiveSession(
            userId = "cashier-1",
            username = "cajero01",
            role = UserRole.CASHIER,
            banca = "Banca A",
            adminId = "admin-1",
            adminUser = "admin",
        )

        assertEquals("admin-1", resolveOperationalOwnerKey(session))
    }

    @Test
    fun `sales live sync fallback intervals protect server when realtime is unavailable`() {
        assertTrue(SALES_EXPOSURE_REFRESH_INTERVAL_MS >= 60_000L)
        assertTrue(CASHIER_LIMIT_PULL_INTERVAL_MS >= 60_000L)
        assertTrue(PosPerformanceBudget.SYNC_RESUME_THROTTLE_MS <= 10_000L)
    }

    @Test
    fun `results realtime signal refreshes snapshot but not historical live scrape`() {
        assertFalse(
            shouldForceLiveResultsFetch(
                selectedDate = "12-05-2026",
                today = "13-05-2026",
                allowLive = true,
                needsRemoteCompletion = true,
            ),
        )
    }
}
