package com.lotterynet.pro.core.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LotterynetRealtimeSubscriptionTest {
    @Test
    fun `realtime module exposes required logical channels`() {
        val names = listOf(
            LotterynetRealtimeSubscription.usersGlobal().channelName,
            LotterynetRealtimeSubscription.masterKey("owner").channelName,
            LotterynetRealtimeSubscription.ticketOwner("admin-1").channelName,
            LotterynetRealtimeSubscription.resultsDraws("13-05-2026").channelName,
        )
        assertTrue(names.contains("users-global"))
        assertTrue(names.contains("tickets-admin-1"))
    }

    @Test
    fun `ticket owner subscription targets tickets table with owner filter`() {
        val subscription = LotterynetRealtimeSubscription.ticketOwner("admin-1")
        assertEquals("public", subscription.schema)
        assertEquals("lotterynet_tickets_by_owner", subscription.table)
        assertEquals("owner_key=eq.admin-1", subscription.filter)
    }

    @Test
    fun `results subscription targets normalized result draws by day`() {
        val subscription = LotterynetRealtimeSubscription.resultsDraws("29-05-2026")

        assertEquals("public", subscription.schema)
        assertEquals("result_draws", subscription.table)
        assertEquals("result_day_key=eq.29-05-2026", subscription.filter)
    }
}
