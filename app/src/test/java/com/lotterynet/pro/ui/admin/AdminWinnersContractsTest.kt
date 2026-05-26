package com.lotterynet.pro.ui.admin

import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminWinnersContractsTest {

    @Test
    fun `admin only can open winners section`() {
        assertFalse(canOpenWinnersForRole(UserRole.CASHIER))
        assertTrue(canOpenWinnersForRole(UserRole.ADMIN))
        assertFalse(canOpenWinnersForRole(UserRole.MASTER))
    }

    @Test
    fun `winner list includes detected prize before winner status`() {
        assertTrue(isWinnerListTicket(TicketRecord(id = "prize", status = "active", totalPrize = 500.0)))
        assertFalse(isWinnerListTicket(TicketRecord(id = "paid-voided", status = "voided", totalPrize = 500.0)))
    }

    @Test
    fun `winner list treats legacy paid statuses as paid`() {
        val tickets = listOf(
            TicketRecord(id = "pending", status = "winner", totalPrize = 500.0),
            TicketRecord(id = "paid", status = "cobrado", totalPrize = 300.0),
        )

        assertTrue(isWinnerListTicket(tickets[1]))
        assertEquals(listOf("pending"), filterPendingWinnerTickets(tickets).map { it.id })
        assertEquals(listOf("paid"), filterPaidWinnerTickets(tickets).map { it.id })
    }

    @Test
    fun `winner list treats spanish server winner status as pending`() {
        val tickets = listOf(TicketRecord(id = "spanish", status = "GANADOR", totalPrize = 500.0))

        assertTrue(isWinnerListTicket(tickets.single()))
        assertEquals(listOf("spanish"), filterPendingWinnerTickets(tickets).map { it.id })
    }

    @Test
    fun `winner section validates active tickets against local results before filtering`() {
        val ticket = TicketRecord(
            id = "ticket-1",
            status = "active",
            plays = listOf(
                PlayItem(
                    playType = "Q",
                    number = "12",
                    amount = 10.0,
                    lotteryId = "lot-1",
                    lotteryName = "Loteria",
                ),
            ),
        )
        val result = LotteryResult(
            lotteryId = "lot-1",
            lotteryName = "Loteria",
            date = "2026-05-05",
            first = "12",
            second = "34",
            third = "56",
        )

        val winners = buildAdminWinnerTickets(
            tickets = listOf(ticket),
            results = listOf(result),
            prizeConfig = PrizeTableConfig(q1 = 60),
        )

        assertEquals(1, winners.size)
        assertEquals("winner", winners.single().status)
        assertEquals(600.0, winners.single().totalPrize, 0.0)
    }
}
