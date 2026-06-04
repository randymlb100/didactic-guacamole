package com.lotterynet.pro.ui.admin

import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.WinningPlayDetail
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

    @Test
    fun `winner section refreshes stale winner details with configured admin payout`() {
        val ticket = TicketRecord(
            id = "ticket-stale-winner",
            status = "winner",
            totalPrize = 6_000.0,
            winningDetails = listOf(
                WinningPlayDetail(
                    lotteryName = "Loteka",
                    playType = "Q",
                    playedNumber = "88",
                    resultNumber = "88-28-33",
                    hitPosition = "1",
                    amount = 100.0,
                    payoutAmount = 6_000.0,
                ),
            ),
            plays = listOf(
                PlayItem(
                    playType = "Q",
                    number = "88",
                    amount = 100.0,
                    lotteryId = "loteka",
                    lotteryName = "Loteka",
                ),
            ),
        )
        val result = LotteryResult(
            lotteryId = "loteka",
            lotteryName = "Loteka",
            date = "2026-05-29",
            first = "88",
            second = "28",
            third = "33",
        )

        val winners = buildAdminWinnerTickets(
            tickets = listOf(ticket),
            results = listOf(result),
            prizeConfigResolver = { PrizeTableConfig(q1 = 72) },
        )

        assertEquals(1, winners.size)
        assertEquals(7_200.0, winners.single().totalPrize, 0.0)
        assertEquals(7_200.0, winners.single().winningDetails.single().payoutAmount, 0.0)
    }

    @Test
    fun `winner section refreshes cashier tickets with cashier payout table`() {
        val ticket = TicketRecord(
            id = "ticket-cashier-winner",
            status = "winner",
            adminId = "admin-1",
            adminUser = "ramonc3",
            sellerUser = "bancay04",
            totalPrize = 6_000.0,
            winningDetails = listOf(
                WinningPlayDetail(
                    lotteryName = "Loteka",
                    playType = "Q",
                    playedNumber = "88",
                    resultNumber = "88-28-33",
                    hitPosition = "1",
                    amount = 100.0,
                    payoutAmount = 6_000.0,
                ),
            ),
            plays = listOf(
                PlayItem(
                    playType = "Q",
                    number = "88",
                    amount = 100.0,
                    lotteryId = "loteka",
                    lotteryName = "Loteka",
                ),
            ),
        )
        val result = LotteryResult(
            lotteryId = "loteka",
            lotteryName = "Loteka",
            date = "2026-05-29",
            first = "88",
            second = "28",
            third = "33",
        )

        val winners = buildAdminWinnerTickets(
            tickets = listOf(ticket),
            results = listOf(result),
            prizeConfigResolver = { current ->
                if (current.sellerUser == "bancay04") PrizeTableConfig(q1 = 72) else PrizeTableConfig(q1 = 60)
            },
        )

        assertEquals(1, winners.size)
        assertEquals(7_200.0, winners.single().totalPrize, 0.0)
        assertEquals(7_200.0, winners.single().winningDetails.single().payoutAmount, 0.0)
    }

    @Test
    fun `winner section validates each ticket with its own draw date results`() {
        val oldTicket = TicketRecord(
            id = "old-ticket",
            status = "active",
            drawDateKey = "2026-05-30",
            plays = listOf(
                PlayItem(
                    playType = "Q",
                    number = "82",
                    amount = 4.0,
                    lotteryId = "la-suerte-tarde",
                    lotteryName = "La Suerte Tarde",
                ),
            ),
        )
        val currentTicket = TicketRecord(
            id = "current-ticket",
            status = "active",
            drawDateKey = "2026-05-31",
            plays = listOf(
                PlayItem(
                    playType = "Q",
                    number = "82",
                    amount = 4.0,
                    lotteryId = "la-suerte-tarde",
                    lotteryName = "La Suerte Tarde",
                ),
            ),
        )
        val oldResult = LotteryResult(
            lotteryId = "la-suerte-tarde",
            lotteryName = "La Suerte Tarde",
            date = "2026-05-30",
            first = "77",
            second = "82",
            third = "44",
        )
        val currentResult = LotteryResult(
            lotteryId = "la-suerte-tarde",
            lotteryName = "La Suerte Tarde",
            date = "2026-05-31",
            first = "11",
            second = "22",
            third = "33",
        )

        val winners = buildAdminWinnerTicketsByDrawDate(
            tickets = listOf(oldTicket, currentTicket),
            resultsByDate = mapOf(
                "2026-05-30" to listOf(oldResult),
                "2026-05-31" to listOf(currentResult),
            ),
            prizeConfig = PrizeTableConfig(q2 = 12),
        )

        assertEquals(listOf("old-ticket"), winners.map { it.id })
        assertEquals("winner", winners.single().status)
        assertEquals(48.0, winners.single().totalPrize, 0.0)
    }

    @Test
    fun `winner section foreground catch-up refreshes when ticket stamp changes`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "ramonc3",
            adminId = "ADM-1",
            adminUser = "ramonc3",
        )
        val input = resolveAdminWinnersForegroundCatchUpInput(
            session = session,
            tickets = listOf(TicketRecord(id = "winner-1", drawDateKey = "2026-06-02")),
            dayKey = "2026-06-02",
            lastRemoteUpdatedAt = "2026-06-02T10:00:00Z",
            remoteUpdatedAt = "2026-06-02T10:05:00Z",
            realtimeConfigured = true,
            hasRealtimeSubscription = true,
            nowEpochMs = 1_780_376_400_000L,
        )

        assertEquals("ADM-1", input.ownerKey)
        assertEquals("2026-06-02", input.dateKey)
        assertTrue(input.hasLocalTickets)
        assertTrue(input.ticketStampChanged)
        assertTrue(input.realtimeConnected)
    }

    @Test
    fun `winner section foreground catch-up reconnects realtime after background`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "ramonc3",
            adminId = "ADM-1",
            adminUser = "ramonc3",
        )
        val input = resolveAdminWinnersForegroundCatchUpInput(
            session = session,
            tickets = listOf(TicketRecord(id = "winner-1", drawDateKey = "2026-06-02")),
            dayKey = "2026-06-02",
            lastRemoteUpdatedAt = "2026-06-02T10:00:00Z",
            remoteUpdatedAt = "2026-06-02T10:00:00Z",
            realtimeConfigured = true,
            hasRealtimeSubscription = false,
            nowEpochMs = 1_780_376_400_000L,
        )

        assertFalse(input.ticketStampChanged)
        assertFalse(input.realtimeConnected)
    }
}
