package com.lotterynet.pro.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketRefreshGovernorTest {
    @Test
    fun `refresh governor key collapses canonical owner aliases but keeps request type and auth scope distinct`() {
        val snapshotAuth = ticketRefreshGovernorKey(
            ownerKey = " ADM-C5FFB0 ",
            requestType = "snapshot",
            authScope = "auth",
        )
        val snapshotAnon = ticketRefreshGovernorKey(
            ownerKey = "adm-c5ffb0",
            requestType = "snapshot",
            authScope = "anon",
        )
        val updatedAtAuth = ticketRefreshGovernorKey(
            ownerKey = "adm-c5ffb0",
            requestType = "updated-at",
            authScope = "auth",
        )

        assertEquals(snapshotAuth, ticketRefreshGovernorKey("adm-c5ffb0", "snapshot", "auth"))
        assertEquals(snapshotAuth, ticketRefreshGovernorKey("ADM-C5FFB0", "snapshot", "auth"))
        assertNotEquals(snapshotAuth, snapshotAnon)
        assertNotEquals(snapshotAuth, updatedAtAuth)
    }

    @Test
    fun `governor reuses the same key inside the cool down window`() {
        val governor = TicketRefreshGovernor(requestCooldownMs = 10_000L)

        assertFalse(governor.shouldReuse("admin-1|get-ticket-list", nowMs = 1_000L))
        assertTrue(governor.shouldReuse("admin-1|get-ticket-list", nowMs = 5_000L))
        assertFalse(governor.shouldReuse("admin-1|get-ticket-list", nowMs = 12_001L))
    }

    @Test
    fun `governor does not reuse blank keys`() {
        val governor = TicketRefreshGovernor(requestCooldownMs = 10_000L)

        assertFalse(governor.shouldReuse(" "))
    }
}
