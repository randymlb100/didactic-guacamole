package com.lotterynet.pro.core.realtime

import org.junit.Assert.assertEquals
import org.junit.Test

class LotterynetRealtimeOrchestratorTest {
    @Test
    fun `users-state update triggers a single remote users refresh`() {
        var refreshCalls = 0
        val orchestrator = LotterynetRealtimeOrchestrator(
            onUsersChanged = { refreshCalls += 1 },
        )

        orchestrator.onEvent(
            LotterynetRealtimeEvent(
                type = LotterynetRealtimeEventType.UPDATE,
                table = "lotterynet_users_state",
                filterValue = "global",
                payloadJson = """{"scope":"global"}""",
            ),
        )

        assertEquals(1, refreshCalls)
    }

    @Test
    fun `duplicate realtime updates for same key are coalesced`() {
        var calls = 0
        val orchestrator = LotterynetRealtimeOrchestrator(
            onUsersChanged = { calls += 1 },
            nowMs = { 1_000L },
        )

        repeat(3) {
            orchestrator.onEvent(
                LotterynetRealtimeEvent(
                    type = LotterynetRealtimeEventType.UPDATE,
                    table = "lotterynet_users_state",
                    filterValue = "global",
                    payloadJson = """{"scope":"global"}""",
                ),
            )
        }

        assertEquals(1, calls)
    }

    @Test
    fun `result draw update triggers result refresh`() {
        var calls = 0
        var dayKey = ""
        val orchestrator = LotterynetRealtimeOrchestrator(
            onResultsCacheChanged = {
                calls += 1
                dayKey = it
            },
        )

        orchestrator.onEvent(
            LotterynetRealtimeEvent(
                type = LotterynetRealtimeEventType.UPDATE,
                table = "result_draws",
                filterValue = "29-05-2026",
                payloadJson = """{"result_day_key":"29-05-2026"}""",
            ),
        )

        assertEquals(1, calls)
        assertEquals("29-05-2026", dayKey)
    }
}
