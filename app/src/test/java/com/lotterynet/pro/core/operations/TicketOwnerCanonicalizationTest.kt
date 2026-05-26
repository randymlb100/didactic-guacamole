package com.lotterynet.pro.core.operations

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class TicketOwnerCanonicalizationTest {

    @Test
    fun `paid admin ticket uses canonical admin id for server finance`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "nicola01",
            username = "nicola01",
            banca = "Banca yuniel",
        )
        val admin = UserAccount(
            id = "ADM-163C38",
            user = "nicola01",
            role = UserRole.ADMIN,
            banca = "Banca yuniel",
        )
        val ticket = TicketRecord(
            id = "winner-1",
            sellerId = "nicola01",
            sellerUser = "nicola01",
            adminId = "nicola01",
            adminUser = "nicola01",
            role = UserRole.ADMIN,
            total = 50.0,
            totalPrize = 500.0,
            status = "paid",
        )

        val canonical = canonicalizeTicketOwnerForSession(ticket, session, listOf(admin))

        assertEquals("ADM-163C38", canonical.adminId)
        assertEquals("nicola01", canonical.adminUser)
        assertEquals("ADM-163C38", canonical.sellerId)
        assertEquals("nicola01", canonical.sellerUser)
        assertEquals(UserRole.ADMIN, canonical.role)
    }

    @Test
    fun `paid cashier ticket keeps cashier but uses canonical admin id`() {
        val session = ActiveSession(
            role = UserRole.ADMIN,
            userId = "nicola01",
            username = "nicola01",
            banca = "Banca yuniel",
        )
        val admin = UserAccount(id = "ADM-163C38", user = "nicola01", role = UserRole.ADMIN)
        val cashier = UserAccount(
            id = "CAJ-9426F8",
            user = "bancay01",
            role = UserRole.CASHIER,
            adminId = "ADM-163C38",
            adminUser = "nicola01",
        )
        val ticket = TicketRecord(
            id = "winner-2",
            sellerId = "bancay01",
            sellerUser = "bancay01",
            adminId = "nicola01",
            adminUser = "nicola01",
            role = UserRole.CASHIER,
            total = 50.0,
            totalPrize = 500.0,
            status = "paid",
        )

        val canonical = canonicalizeTicketOwnerForSession(ticket, session, listOf(admin, cashier))

        assertEquals("ADM-163C38", canonical.adminId)
        assertEquals("nicola01", canonical.adminUser)
        assertEquals("CAJ-9426F8", canonical.sellerId)
        assertEquals("bancay01", canonical.sellerUser)
        assertEquals(UserRole.CASHIER, canonical.role)
    }
}
