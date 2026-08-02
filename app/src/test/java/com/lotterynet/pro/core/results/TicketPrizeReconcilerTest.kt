package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.sync.resolveTicketSyncOwnerKey
import com.lotterynet.pro.core.sync.resolveTicketSyncOwnerKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class TicketPrizeReconcilerTest {
    @Test
    fun `batch flush owner keys collapse duplicate identities and prefer the strongest owner identity`() {
        val tickets = listOf(
            TicketRecord(
                id = "t-1",
                sellerId = "seller-1",
                sellerUser = "cashier-a",
                adminId = "admin-1",
                adminUser = "admin-one",
                role = UserRole.CASHIER,
            ),
            TicketRecord(
                id = "t-2",
                sellerId = "seller-1",
                sellerUser = "cashier-a",
                adminId = "admin-1",
                adminUser = "admin-one",
                role = UserRole.CASHIER,
            ),
            TicketRecord(
                id = "t-3",
                sellerId = "seller-2",
                sellerUser = "cashier-b",
                role = UserRole.CASHIER,
            ),
        )

        assertEquals("admin-1", resolveTicketSyncOwnerKey(tickets.first()))
        assertEquals(setOf("admin-1", "seller-2"), resolveTicketSyncOwnerKeys(tickets))
    }
}
