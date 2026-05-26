package com.lotterynet.pro.core.render

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.ResultShareRow
import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RenderCacheKeysTest {
    @Test
    fun `ticket render key is stable for same ticket content`() {
        val ticket = TicketRecord(
            id = "T-1",
            serial = "S-1",
            status = "active",
            total = 50.0,
            plays = listOf(PlayItem(number = "12", playType = "Q", amount = 50.0)),
        )

        assertEquals(
            ticketRenderCacheKey(ticket, bancaName = "Banca", logoUri = "logo"),
            ticketRenderCacheKey(ticket, bancaName = "Banca", logoUri = "logo"),
        )
    }

    @Test
    fun `ticket render key changes when status changes`() {
        val active = TicketRecord(id = "T-1", status = "active")
        val voided = TicketRecord(id = "T-1", status = "voided")

        assertNotEquals(
            ticketRenderCacheKey(active, bancaName = "Banca", logoUri = ""),
            ticketRenderCacheKey(voided, bancaName = "Banca", logoUri = ""),
        )
    }

    @Test
    fun `results render key changes when numbers change`() {
        val a = listOf(ResultShareRow("Anguila", "01", "02", "03"))
        val b = listOf(ResultShareRow("Anguila", "01", "02", "04"))

        assertNotEquals(
            resultsRenderCacheKey("25-04-2026", a, pageIndex = 0),
            resultsRenderCacheKey("25-04-2026", b, pageIndex = 0),
        )
    }

    @Test
    fun `results render key changes when logo changes`() {
        val a = listOf(ResultShareRow("Anguila", "01", "02", "03", logoAssetPath = "lot-logos/1.png"))
        val b = listOf(ResultShareRow("Anguila", "01", "02", "03", logoAssetPath = "lot-logos/2.png"))

        assertNotEquals(
            resultsRenderCacheKey("25-04-2026", a, pageIndex = 0),
            resultsRenderCacheKey("25-04-2026", b, pageIndex = 0),
        )
    }
}
