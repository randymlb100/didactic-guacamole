package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Test

class PrizeValidationEngineTest {
    private val engine = PrizeValidationEngine()
    private val result = LotteryResult(
        lotteryId = "lot-1",
        lotteryName = "Loteria",
        date = "2026-04-30",
        first = "12",
        second = "34",
        third = "56",
    )

    @Test
    fun `pale uses configured position payout for first and third`() {
        val ticket = ticketWith(playType = "P", number = "5612", amount = 2.0)
        val config = PrizeTableConfig(pale12 = 100, pale13 = 80, pale23 = 60)

        val outcome = engine.validate(ticket, listOf(result), config)

        assertEquals("winner", outcome.ticket.status)
        assertEquals(160.0, outcome.totalPrize, 0.0)
    }

    @Test
    fun `default pale payout pays one thousand per peso`() {
        val ticket = ticketWith(playType = "P", number = "1234", amount = 5.0)

        val outcome = engine.validate(ticket, listOf(result), PrizeTableConfig())

        assertEquals("winner", outcome.ticket.status)
        assertEquals(5000.0, outcome.totalPrize, 0.0)
    }

    @Test
    fun `legacy pale payout is normalized before calculating prize`() {
        val ticket = ticketWith(playType = "P", number = "1234", amount = 5.0)
        val legacyConfig = PrizeTableConfig(pale = 100000, pale12 = 100000, pale13 = 100000, pale23 = 100000)

        val outcome = engine.validate(ticket, listOf(result), legacyConfig)

        assertEquals("winner", outcome.ticket.status)
        assertEquals(5000.0, outcome.totalPrize, 0.0)
    }

    @Test
    fun `tripleta pays two number match when all three are not present`() {
        val ticket = ticketWith(playType = "T", number = "123499", amount = 3.0)
        val config = PrizeTableConfig(tripleta3 = 200, tripleta2 = 25)

        val outcome = engine.validate(ticket, listOf(result), config)

        assertEquals("winner", outcome.ticket.status)
        assertEquals(75.0, outcome.totalPrize, 0.0)
    }

    @Test
    fun `automatic validation matches primera morning alias to primera dia ticket`() {
        val ticket = TicketRecord(
            id = "primera-banca01",
            sellerUser = "banca01",
            plays = listOf(
                PlayItem(
                    number = "32",
                    playType = "Q",
                    amount = 10.0,
                    lotteryId = "1",
                    lotteryName = "La Primera Día",
                ),
            ),
        )
        val morningResult = LotteryResult(
            lotteryId = "",
            lotteryName = "La Primera Mañana",
            date = "2026-05-19",
            first = "11",
            second = "22",
            third = "32",
        )

        val outcome = engine.validate(ticket, listOf(morningResult), PrizeTableConfig(q3 = 60))

        assertEquals("winner", outcome.ticket.status)
        assertEquals(600.0, outcome.totalPrize, 0.0)
    }

    @Test
    fun `automatic validation matches pick3 draw name alias to sold pick ticket`() {
        val ticket = TicketRecord(
            id = "pick3-banca01",
            sellerUser = "banca01",
            plays = listOf(
                PlayItem(
                    number = "256",
                    playType = "P3",
                    amount = 5.0,
                    lotteryId = "US-P3-FL-PICK-3-MIDDAY",
                    lotteryName = "Florida Pick 3",
                ),
            ),
        )
        val result = LotteryResult(
            lotteryId = "",
            lotteryName = "Florida Pick 3 Midday Draw",
            date = "2026-05-19",
            pick3 = "256",
        )

        val outcome = engine.validate(ticket, listOf(result), PrizeTableConfig(pick3Straight = 600))

        assertEquals("winner", outcome.ticket.status)
        assertEquals(3000.0, outcome.totalPrize, 0.0)
    }

    @Test
    fun `automatic validation matches pick4 numbers alias to sold pick ticket`() {
        val ticket = TicketRecord(
            id = "pick4-banca01",
            sellerUser = "banca01",
            plays = listOf(
                PlayItem(
                    number = "5423",
                    playType = "P4",
                    amount = 2.0,
                    lotteryId = "US-P4-NY-WIN-4-MIDDAY",
                    lotteryName = "New York Win 4 Midday",
                ),
            ),
        )
        val result = LotteryResult(
            lotteryId = "",
            lotteryName = "New York Win 4 Midday Draw",
            date = "2026-05-19",
            pick4 = "5423",
        )

        val outcome = engine.validate(ticket, listOf(result), PrizeTableConfig(pick4Straight = 5000))

        assertEquals("winner", outcome.ticket.status)
        assertEquals(10000.0, outcome.totalPrize, 0.0)
    }

    private fun ticketWith(playType: String, number: String, amount: Double): TicketRecord =
        TicketRecord(
            id = "T-$playType-$number",
            plays = listOf(
                PlayItem(
                    number = number,
                    playType = playType,
                    amount = amount,
                    lotteryId = "lot-1",
                    lotteryName = "Loteria",
                ),
            ),
        )
}
