package com.lotterynet.pro.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundCatchUpPolicyTest {
    @Test
    fun `first foreground with empty local state refreshes tickets results and realtime`() {
        val policy = ForegroundCatchUpPolicy(OperationalSyncThrottle(minIntervalMs = 10_000L))

        val decision = policy.decide(
            ForegroundCatchUpInput(
                ownerKey = "ADM-1",
                dateKey = "2026-06-02",
                hasLocalTickets = false,
                hasLocalResults = false,
                ticketStampChanged = false,
                resultsStampChanged = false,
                realtimeConnected = false,
                nowMs = 100_000L,
            ),
        )

        assertTrue(decision.shouldRun)
        assertTrue(decision.refreshTickets)
        assertTrue(decision.refreshResults)
        assertTrue(decision.reconnectRealtime)
    }

    @Test
    fun `foreground catches up changed server stamps even when local cache exists`() {
        val policy = ForegroundCatchUpPolicy(OperationalSyncThrottle(minIntervalMs = 10_000L))

        val decision = policy.decide(
            ForegroundCatchUpInput(
                ownerKey = "ADM-1",
                dateKey = "2026-06-02",
                hasLocalTickets = true,
                hasLocalResults = true,
                ticketStampChanged = true,
                resultsStampChanged = true,
                realtimeConnected = true,
                nowMs = 100_000L,
            ),
        )

        assertTrue(decision.shouldRun)
        assertTrue(decision.refreshTickets)
        assertTrue(decision.refreshResults)
        assertFalse(decision.reconnectRealtime)
    }

    @Test
    fun `foreground reconnects realtime without downloading fresh local data`() {
        val policy = ForegroundCatchUpPolicy(OperationalSyncThrottle(minIntervalMs = 10_000L))

        val decision = policy.decide(
            ForegroundCatchUpInput(
                ownerKey = "ADM-1",
                dateKey = "2026-06-02",
                hasLocalTickets = true,
                hasLocalResults = true,
                ticketStampChanged = false,
                resultsStampChanged = false,
                realtimeConnected = false,
                nowMs = 100_000L,
            ),
        )

        assertTrue(decision.shouldRun)
        assertFalse(decision.refreshTickets)
        assertFalse(decision.refreshResults)
        assertTrue(decision.reconnectRealtime)
    }

    @Test
    fun `foreground catch-up is throttled to avoid server spam`() {
        val policy = ForegroundCatchUpPolicy(OperationalSyncThrottle(minIntervalMs = 10_000L))

        assertTrue(
            policy.decide(
                ForegroundCatchUpInput(
                    ownerKey = "ADM-1",
                    dateKey = "2026-06-02",
                    hasLocalTickets = false,
                    hasLocalResults = false,
                    ticketStampChanged = false,
                    resultsStampChanged = false,
                    realtimeConnected = false,
                    nowMs = 100_000L,
                ),
            ).shouldRun,
        )

        assertFalse(
            policy.decide(
                ForegroundCatchUpInput(
                    ownerKey = "ADM-1",
                    dateKey = "2026-06-02",
                    hasLocalTickets = false,
                    hasLocalResults = false,
                    ticketStampChanged = true,
                    resultsStampChanged = true,
                    realtimeConnected = false,
                    nowMs = 105_000L,
                ),
            ).shouldRun,
        )
    }

    @Test
    fun `forced foreground catch-up bypasses throttle for manual recovery`() {
        val policy = ForegroundCatchUpPolicy(OperationalSyncThrottle(minIntervalMs = 10_000L))

        assertTrue(
            policy.decide(
                ForegroundCatchUpInput(
                    ownerKey = "ADM-1",
                    dateKey = "2026-06-02",
                    hasLocalTickets = true,
                    hasLocalResults = true,
                    ticketStampChanged = false,
                    resultsStampChanged = false,
                    realtimeConnected = true,
                    nowMs = 100_000L,
                    force = true,
                ),
            ).shouldRun,
        )
        assertTrue(
            policy.decide(
                ForegroundCatchUpInput(
                    ownerKey = "ADM-1",
                    dateKey = "2026-06-02",
                    hasLocalTickets = true,
                    hasLocalResults = true,
                    ticketStampChanged = false,
                    resultsStampChanged = false,
                    realtimeConnected = true,
                    nowMs = 101_000L,
                    force = true,
                ),
            ).shouldRun,
        )
    }
}
