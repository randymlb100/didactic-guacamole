package com.lotterynet.pro.core.delivery

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketDeliveryPolicyTest {
    @Test
    fun `large ticket pages keep small lotteries together`() {
        val ticket = TicketRecord(
            id = "large-ticket",
            plays = (1..8).flatMap { lotteryIndex ->
                (1..10).map { playIndex ->
                    PlayItem(
                        number = playIndex.toString().padStart(2, '0'),
                        playType = "Q",
                        amount = 10.0,
                        lotteryName = "Loteria $lotteryIndex",
                    )
                }
            },
        )

        val pages = TicketDeliveryPolicy.buildPages(ticket)

        assertEquals(8, pages.size)
        assertTrue(pages.all { page -> page.plays.map { it.lotteryName }.distinct().size == 1 })
    }

    @Test
    fun `one huge lottery is split inside same lottery`() {
        val ticket = TicketRecord(
            id = "huge-single-lottery",
            plays = (1..130).map { playIndex ->
                PlayItem(
                    number = playIndex.toString().padStart(2, '0'),
                    playType = "Q",
                    amount = 10.0,
                    lotteryName = "Loteka",
                )
            },
        )

        val pages = TicketDeliveryPolicy.buildPages(ticket)

        assertEquals(3, pages.size)
        assertTrue(pages.all { page -> page.plays.all { it.lotteryName == "Loteka" } })
        assertTrue(pages.all { page -> page.plays.size <= TicketDeliveryPolicy.MAX_PLAYS_PER_LARGE_PAGE })
    }
}
