package com.lotterynet.pro.core.notification

import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.ActiveSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinningTicketNotifierContractsTest {

    @Test
    fun `admin and cashier can be prompted for winner notifications`() {
        assertTrue(WinningTicketNotifier.canRequestNotificationsForRole(UserRole.ADMIN))
        assertTrue(WinningTicketNotifier.canRequestNotificationsForRole(UserRole.CASHIER))
        assertFalse(WinningTicketNotifier.canRequestNotificationsForRole(UserRole.MASTER))
    }

    @Test
    fun `new pending winner should notify with seller ticket and prize detail`() {
        val winner = TicketRecord(
            id = "ticket-1",
            serial = "NAT-1234",
            sellerUser = "cajero01",
            status = "winner",
            totalPrize = 850.0,
            plays = listOf(
                PlayItem(
                    lotteryName = "La Primera",
                    playType = "Q",
                    number = "12",
                    amount = 25.0,
                ),
            ),
        )

        assertTrue(WinningTicketNotifier.shouldNotifyWinningTicket(previous = null, current = winner))
        assertEquals("Ticket ganador - cajero01", WinningTicketNotifier.winningTicketTitle(winner))
        assertEquals(
            listOf(
                "Vendedor: cajero01",
                "Ticket: NAT-1234",
                "Premio: $ 850",
                "Loteria: La Primera",
                "Jugada: Q 12 $ 25",
            ).joinToString("\n"),
            WinningTicketNotifier.winningTicketMessage(winner),
        )
    }

    @Test
    fun `winner notification opens ticket in pay mode`() {
        assertEquals("pagar", WinningTicketNotifier.WINNING_TICKET_ACTION_MODE)
    }

    @Test
    fun `admin receives winners from all cashiers in their network`() {
        val admin = ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin01",
        )

        assertTrue(
            WinningTicketNotifier.shouldNotifyTicketForActiveSession(
                admin,
                TicketRecord(
                    id = "ticket-1",
                    adminId = "admin-1",
                    sellerUser = "cajero02",
                    status = "winner",
                    totalPrize = 500.0,
                ),
            ),
        )
        assertFalse(
            WinningTicketNotifier.shouldNotifyTicketForActiveSession(
                admin,
                TicketRecord(
                    id = "ticket-2",
                    adminId = "admin-2",
                    sellerUser = "cajero99",
                    status = "winner",
                    totalPrize = 500.0,
                ),
            ),
        )
    }

    @Test
    fun `cashier receives only winners from active profile`() {
        val cashier = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-1",
            username = "cajero01",
            adminId = "admin-1",
        )

        assertTrue(
            WinningTicketNotifier.shouldNotifyTicketForActiveSession(
                cashier,
                TicketRecord(id = "own", sellerId = "cashier-1", status = "winner", totalPrize = 500.0),
            ),
        )
        assertFalse(
            WinningTicketNotifier.shouldNotifyTicketForActiveSession(
                cashier,
                TicketRecord(id = "other", sellerUser = "cajero02", status = "winner", totalPrize = 500.0),
            ),
        )
    }

    @Test
    fun `same pending winner does not notify twice`() {
        val previous = TicketRecord(id = "ticket-1", status = "winner", totalPrize = 850.0)
        val current = previous.copy()

        assertFalse(WinningTicketNotifier.shouldNotifyWinningTicket(previous, current))
    }

    @Test
    fun `paid voided and invalid winners do not notify`() {
        assertFalse(WinningTicketNotifier.isPendingWinningTicket(TicketRecord(id = "paid", status = "paid", totalPrize = 850.0)))
        assertFalse(WinningTicketNotifier.isPendingWinningTicket(TicketRecord(id = "void", status = "voided", totalPrize = 850.0)))
        assertFalse(WinningTicketNotifier.isPendingWinningTicket(TicketRecord(id = "invalid", status = "invalid", totalPrize = 850.0)))
    }
}
