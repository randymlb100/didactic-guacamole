package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSalesRepositoryContractsTest {

    @Test
    fun `admin delegated cashier ticket belongs to selected cashier not admin actor`() {
        val delegatedTicket = TicketRecord(
            id = "delegated-sale",
            sellerId = "CAJ-1",
            sellerUser = "cajero01",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.CASHIER,
            total = 500.0,
        )

        assertTrue(matchesSalesActorTicket(delegatedTicket, "CAJ-1"))
        assertTrue(matchesSalesActorTicket(delegatedTicket, "cajero01"))
        assertFalse(matchesSalesActorTicket(delegatedTicket, "ADM-1"))
        assertFalse(matchesSalesActorTicket(delegatedTicket, "admin01"))
    }

    @Test
    fun `admin ticket still belongs to admin actor`() {
        val adminTicket = TicketRecord(
            id = "admin-sale",
            sellerId = "ADM-1",
            sellerUser = "admin01",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.ADMIN,
            total = 300.0,
        )

        assertTrue(matchesSalesActorTicket(adminTicket, "ADM-1"))
        assertTrue(matchesSalesActorTicket(adminTicket, "admin01"))
    }

    @Test
    fun `server snapshot removes local cashier ticket deleted by admin`() {
        val deletedFromServer = TicketRecord(
            id = "cashier-sale-deleted",
            sellerId = "CAJ-1",
            sellerUser = "cajero01",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.CASHIER,
            total = 400.0,
        )
        val stillOnServer = TicketRecord(
            id = "cashier-sale-active",
            sellerId = "CAJ-1",
            sellerUser = "cajero01",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.CASHIER,
            total = 60.0,
        )
        val otherAdminTicket = TicketRecord(
            id = "other-admin-sale",
            sellerId = "CAJ-9",
            sellerUser = "otro",
            adminId = "ADM-9",
            adminUser = "admin09",
            role = UserRole.CASHIER,
            total = 20.0,
        )

        val reconciled = reconcileScopedImportedTickets(
            existing = listOf(deletedFromServer, stillOnServer, otherAdminTicket),
            ownerKey = "ADM-1",
            imported = listOf(stillOnServer.copy(total = 65.0)),
            deletedIds = emptySet(),
        )

        assertFalse(reconciled.any { it.id == "cashier-sale-deleted" })
        assertTrue(reconciled.any { it.id == "cashier-sale-active" && it.total == 65.0 })
        assertTrue(reconciled.any { it.id == "other-admin-sale" })
    }

    @Test
    fun `empty server snapshot clears scoped cashier tickets but preserves other admins`() {
        val scopedTicket = TicketRecord(
            id = "cashier-sale-deleted",
            sellerId = "CAJ-1",
            sellerUser = "cajero01",
            adminId = "ADM-1",
            adminUser = "admin01",
            role = UserRole.CASHIER,
            total = 400.0,
        )
        val otherAdminTicket = TicketRecord(
            id = "other-admin-sale",
            sellerId = "CAJ-9",
            sellerUser = "otro",
            adminId = "ADM-9",
            adminUser = "admin09",
            role = UserRole.CASHIER,
            total = 20.0,
        )

        val reconciled = reconcileScopedImportedTickets(
            existing = listOf(scopedTicket, otherAdminTicket),
            ownerKey = "ADM-1",
            imported = emptyList(),
            deletedIds = emptySet(),
        )

        assertEquals(listOf("other-admin-sale"), reconciled.map { it.id })
    }

    @Test
    fun `server snapshot clears stale local ticket when admin id and username are aliases`() {
        val staleLocalTicket = TicketRecord(
            id = "stale-local-qa",
            sellerId = "CASH-1",
            sellerUser = "cajero01",
            adminId = "podero02",
            adminUser = "podero02",
            role = UserRole.CASHIER,
            total = 4.0,
        )
        val remoteTicket = TicketRecord(
            id = "remote-real-sale",
            sellerId = "ADM-C5FFB0",
            sellerUser = "podero02",
            adminId = "ADM-C5FFB0",
            adminUser = "podero02",
            role = UserRole.ADMIN,
            total = 1.0,
        )

        val reconciled = reconcileScopedImportedTickets(
            existing = listOf(staleLocalTicket),
            ownerKey = "ADM-C5FFB0",
            imported = listOf(remoteTicket),
            deletedIds = emptySet(),
        )

        assertEquals(listOf("remote-real-sale"), reconciled.map { it.id })
    }

    @Test
    fun `local persistence rejects money tickets without valid plays`() {
        val emptyMoneyTicket = TicketRecord(
            id = "bad-empty",
            total = 16.0,
            status = "active",
            plays = emptyList(),
        )
        val blankLotteryTicket = TicketRecord(
            id = "bad-lottery",
            total = 2.0,
            status = "active",
            plays = listOf(
                PlayItem(number = "25", playType = "Q", amount = 2.0, lotteryId = "", lotteryName = ""),
            ),
        )
        val validTicket = TicketRecord(
            id = "valid",
            total = 2.0,
            status = "active",
            plays = listOf(
                PlayItem(number = "25", playType = "Q", amount = 2.0, lotteryId = "1", lotteryName = "La Primera"),
            ),
        )

        assertFalse(emptyMoneyTicket.isSafeToPersistLocally())
        assertFalse(blankLotteryTicket.isSafeToPersistLocally())
        assertTrue(validTicket.isSafeToPersistLocally())
    }
}
