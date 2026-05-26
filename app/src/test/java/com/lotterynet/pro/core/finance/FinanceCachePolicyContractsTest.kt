package com.lotterynet.pro.core.finance

import com.lotterynet.pro.core.sync.SyncFreshnessRecord
import com.lotterynet.pro.core.sync.SyncFreshnessState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceCachePolicyContractsTest {
    @Test
    fun `past day with local data does not need server`() {
        val decision = resolveFinanceRemoteRefreshDecision(
            hasLocalData = true,
            forceRemote = false,
            selectedDayKey = "2026-04-29",
            todayDayKey = "2026-04-30",
            freshnessRecord = null,
            nowEpochMs = 120_000L,
        )

        assertFalse(decision.shouldRefreshRemote)
    }

    @Test
    fun `today refreshes after freshness interval`() {
        val decision = resolveFinanceRemoteRefreshDecision(
            hasLocalData = true,
            forceRemote = false,
            selectedDayKey = "2026-04-30",
            todayDayKey = "2026-04-30",
            freshnessRecord = SyncFreshnessRecord(
                state = SyncFreshnessState.SERVER_UPDATED,
                updatedAtEpochMs = 0L,
            ),
            nowEpochMs = FINANCE_TODAY_REFRESH_WINDOW_MS + 1L,
        )

        assertTrue(decision.shouldRefreshRemote)
    }

    @Test
    fun `manual refresh ignores local finance cache`() {
        val decision = resolveFinanceRemoteRefreshDecision(
            hasLocalData = true,
            forceRemote = true,
            selectedDayKey = "2026-04-29",
            todayDayKey = "2026-04-30",
            freshnessRecord = null,
            nowEpochMs = 120_000L,
        )

        assertTrue(decision.shouldRefreshRemote)
    }
}
