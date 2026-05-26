package com.lotterynet.pro.core.operations

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationalScopeContractsTest {

    @Test
    fun `cashier sees only own tickets and never admin tickets from same banca`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "cashier-1",
            username = "cajero1",
            adminId = "admin-1",
            adminUser = "admin1",
            banca = "Banca Central",
        )
        val ownTicket = TicketRecord(
            id = "own",
            sellerId = "cashier-1",
            sellerUser = "cajero1",
            adminId = "admin-1",
            adminUser = "admin1",
            role = UserRole.CASHIER,
        )
        val adminTicket = ownTicket.copy(
            id = "admin",
            sellerId = "admin-1",
            sellerUser = "admin1",
            role = UserRole.ADMIN,
        )

        val visible = filterTicketsForOperationalScope(session, listOf(adminTicket, ownTicket))

        assertEquals(listOf("own"), visible.map { it.id })
    }

    @Test
    fun `supervisor sees only tickets from assigned cashiers`() {
        val session = ActiveSession(
            role = UserRole.SUPERVISOR,
            userId = "SUP-1",
            username = "sup01",
            adminId = "ADM-1",
            adminUser = "admin01",
            banca = "Banca Central",
        )
        val assignedCashier = UserAccount(
            id = "CAJ-1",
            user = "cajero01",
            role = UserRole.CASHIER,
            adminId = "ADM-1",
            adminUser = "admin01",
            supervisorIds = listOf("SUP-1"),
            supervisorUsers = listOf("sup01"),
        )
        val otherCashier = assignedCashier.copy(
            id = "CAJ-2",
            user = "cajero02",
            supervisorIds = emptyList(),
            supervisorUsers = emptyList(),
        )
        val assignedTicket = TicketRecord(
            id = "assigned",
            sellerId = "CAJ-1",
            sellerUser = "cajero01",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.CASHIER,
        )
        val otherTicket = assignedTicket.copy(id = "other", sellerId = "CAJ-2", sellerUser = "cajero02")
        val adminTicket = assignedTicket.copy(id = "admin", sellerId = "ADM-1", sellerUser = "admin01", role = UserRole.ADMIN)

        val visible = filterTicketsForOperationalScope(
            session = session,
            tickets = listOf(otherTicket, adminTicket, assignedTicket),
            cashiers = listOf(assignedCashier, otherCashier),
        )

        assertEquals(listOf("assigned"), visible.map { it.id })
    }

    @Test
    fun `admin monitor accepts ticket seller display name when server omits cashier id`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "ADM-1",
            username = "admin01",
            banca = "Banca Central",
        )
        val cashier = UserAccount(
            id = "CAJ-1",
            user = "banca01",
            displayName = "Cajero 1 - Banca yuniel",
            role = UserRole.CASHIER,
            adminId = "ADM-1",
            adminUser = "admin01",
            banca = "Banca Central",
        )
        val serverTicket = TicketRecord(
            id = "server-ticket",
            sellerId = null,
            sellerUser = "Cajero 1 - Banca yuniel",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.CASHIER,
        )

        val visible = filterTicketsForOperationalScope(
            session = session,
            tickets = listOf(serverTicket),
            cashiers = listOf(cashier),
        )

        assertEquals(listOf("server-ticket"), visible.map { it.id })
    }

    @Test
    fun `cashier sees own winning ticket when server sends seller display name`() {
        val session = ActiveSession(
            role = UserRole.CASHIER,
            userId = "CAJ-6",
            username = "bancay06",
            adminId = "ADM-1",
            adminUser = "nicola01",
            banca = "Banca juan",
        )
        val cashier = UserAccount(
            id = "CAJ-6",
            user = "bancay06",
            displayName = "Cajero 6 - Banca juan",
            role = UserRole.CASHIER,
            adminId = "ADM-1",
            adminUser = "nicola01",
            banca = "Banca juan",
        )
        val serverWinner = TicketRecord(
            id = "LN-E8E25B-E01626",
            sellerId = null,
            sellerUser = "Cajero 6 - Banca juan",
            adminId = "ADM-1",
            adminUser = "nicola01",
            role = UserRole.CASHIER,
            status = "winner",
            totalPrize = 139.0,
        )

        val visible = filterTicketsForOperationalScope(
            session = session,
            tickets = listOf(serverWinner),
            cashiers = listOf(cashier),
        )

        assertEquals(listOf("LN-E8E25B-E01626"), visible.map { it.id })
    }

    @Test
    fun `admin monitor includes cashiers assigned by session admin id`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "auth-user-id",
            username = "nicola01",
            adminId = "ADM-C5FFB0",
            adminUser = "nicola01",
            banca = "Banca Yuniel",
        )
        val assigned = UserAccount(
            id = "CAJ-1",
            user = "banca01",
            role = UserRole.CASHIER,
            adminId = "ADM-C5FFB0",
            adminUser = "nicola01",
            banca = "Banca Yuniel",
        )
        val other = assigned.copy(id = "CAJ-2", user = "otra", adminId = "ADM-OTHER", adminUser = "otro-admin", banca = "Otra")

        val visible = filterCashiersForSession(session, listOf(other, assigned))

        assertEquals(listOf("banca01"), visible.map { it.user })
    }
}
