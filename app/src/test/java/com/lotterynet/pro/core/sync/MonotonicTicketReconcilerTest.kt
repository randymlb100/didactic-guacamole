package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class MonotonicTicketReconcilerTest {
    @Test
    fun `partial response cannot shrink existing ticket set`() {
        val existing = (1..73).map(::ticket)
        val remote = existing.take(36)

        val result = reconcileMonotonicTickets(
            existing = existing,
            remote = remote,
            deletedIds = emptySet(),
            completeScope = false,
        )

        assertEquals(73, result.size)
    }

    @Test
    fun `explicit tombstone removes matching ticket`() {
        val existing = listOf(ticket(1), ticket(2))

        val result = reconcileMonotonicTickets(
            existing = existing,
            remote = emptyList(),
            deletedIds = setOf(ticket(2).id),
            completeScope = false,
        )

        assertEquals(listOf(ticket(1).id), result.map(TicketRecord::id))
    }

    @Test
    fun `complete scope may replace missing existing tickets`() {
        val existing = listOf(ticket(1), ticket(2))
        val remote = listOf(ticket(2))

        val result = reconcileMonotonicTickets(
            existing = existing,
            remote = remote,
            deletedIds = emptySet(),
            completeScope = true,
        )

        assertEquals(listOf(ticket(2).id), result.map(TicketRecord::id))
    }

    private fun ticket(number: Int): TicketRecord {
        return TicketRecord(
            id = "ticket-$number",
            serial = "serial-$number",
            createdAtEpochMs = number.toLong(),
        )
    }
}
