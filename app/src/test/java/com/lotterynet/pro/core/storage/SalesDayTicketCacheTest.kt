package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class SalesDayTicketCacheTest {
    @Test
    fun `same raw json returns same parsed snapshot`() {
        val cache = SalesDayTicketCache()
        val raw = """[{"id":"T-1","status":"active","total":10.0}]"""

        val first = cache.getOrParse("2026-04-26", raw)
        val second = cache.getOrParse("2026-04-26", raw)

        assertSame(first, second)
        assertEquals("T-1", first.single().id)
    }

    @Test
    fun `changed raw json invalidates parsed snapshot`() {
        val cache = SalesDayTicketCache()
        val first = cache.getOrParse("2026-04-26", """[{"id":"T-1"}]""")
        val second = cache.getOrParse("2026-04-26", """[{"id":"T-2"}]""")

        assertNotSame(first, second)
        assertEquals("T-2", second.single().id)
    }

    @Test
    fun `manual invalidation clears one day only`() {
        val cache = SalesDayTicketCache()
        val raw = """[{"id":"T-1"}]"""
        val first = cache.getOrParse("2026-04-26", raw)

        cache.invalidate("2026-04-26")
        val second = cache.getOrParse("2026-04-26", raw)

        assertNotSame(first, second)
    }

    @Test
    fun `stored ticket reads hide locally deleted ids`() {
        val tickets = listOf(
            TicketRecord(id = "deleted", status = "active"),
            TicketRecord(id = "active", status = "active"),
        )

        val visible = filterDeletedStoredTickets(tickets, setOf("deleted"))

        assertEquals(listOf("active"), visible.map { it.id })
    }
}
