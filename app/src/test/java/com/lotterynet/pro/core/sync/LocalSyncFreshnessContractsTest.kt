package com.lotterynet.pro.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSyncFreshnessContractsTest {
    @Test
    fun `freshness key includes type owner banca and date`() {
        val bancaA = buildSyncFreshnessStorageKey(
            buildSyncFreshnessKey(
                type = SyncFreshnessType.FINANCE_DAY,
                ownerKey = "admin-1",
                banca = "Banca A",
                dateKey = "2026-04-30",
            ),
        )
        val bancaB = buildSyncFreshnessStorageKey(
            buildSyncFreshnessKey(
                type = SyncFreshnessType.FINANCE_DAY,
                ownerKey = "admin-1",
                banca = "Banca B",
                dateKey = "2026-04-30",
            ),
        )

        assertEquals("finance_day:admin-1:banca_a:2026-04-30", bancaA)
        assertEquals("finance_day:admin-1:banca_b:2026-04-30", bancaB)
        assertFalse(bancaA == bancaB)
    }

    @Test
    fun `cache decision forces server when manual refresh or local is missing`() {
        assertEquals(
            SyncFreshnessState.NEEDS_SERVER,
            resolveCacheAwareSyncState(
                hasLocalData = true,
                forceRemote = true,
                localComplete = true,
                freshnessRecord = null,
                nowEpochMs = 10_000L,
                staleAfterMs = 60_000L,
            ),
        )
        assertEquals(
            SyncFreshnessState.NEEDS_SERVER,
            resolveCacheAwareSyncState(
                hasLocalData = false,
                forceRemote = false,
                localComplete = true,
                freshnessRecord = null,
                nowEpochMs = 10_000L,
                staleAfterMs = 60_000L,
            ),
        )
    }

    @Test
    fun `cached sync states are reusable only when local is ready or updated`() {
        assertTrue(canUseCachedSyncState(SyncFreshnessState.LOCAL_READY))
        assertTrue(canUseCachedSyncState(SyncFreshnessState.SERVER_UPDATED))
        assertFalse(canUseCachedSyncState(SyncFreshnessState.NEEDS_SERVER))
        assertFalse(canUseCachedSyncState(SyncFreshnessState.SERVER_FAILED_USING_CACHE))
    }
}
