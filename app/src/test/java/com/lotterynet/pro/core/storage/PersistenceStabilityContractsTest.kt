package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.DeletedTicketRef
import com.lotterynet.pro.core.model.RechargeRecord
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistenceStabilityContractsTest {

    @Test
    fun `ticket persistence ordering stays stable across equal payloads`() {
        val first = TicketRecord(id = "b", createdAtEpochMs = 20L)
        val second = TicketRecord(id = "a", createdAtEpochMs = 10L)
        val third = TicketRecord(id = "c", createdAtEpochMs = 10L)

        val ordered = orderTicketsForStableStorage(listOf(first, second, third))

        assertEquals(listOf("a", "c", "b"), ordered.map { it.id })
    }

    @Test
    fun `deleted ticket refs persistence ordering stays stable`() {
        val refs = listOf(
            DeletedTicketRef(id = "z", dayKey = "2026-07-09", deletedAtEpochMs = 90L, role = UserRole.CASHIER),
            DeletedTicketRef(id = "a", dayKey = "2026-07-08", deletedAtEpochMs = 10L, role = UserRole.ADMIN),
            DeletedTicketRef(id = "b", dayKey = "2026-07-08", deletedAtEpochMs = 10L, role = UserRole.ADMIN),
        )

        val ordered = orderDeletedTicketRefsForStableStorage(refs)

        assertEquals(listOf("a", "b", "z"), ordered.map { it.id })
    }

    @Test
    fun `recharge persistence ordering stays stable across equal payloads`() {
        val first = RechargeRecord(id = "x", createdAtEpochMs = 30L)
        val second = RechargeRecord(id = "a", createdAtEpochMs = 30L)
        val third = RechargeRecord(id = "b", createdAtEpochMs = 10L)

        val ordered = orderRechargesForStableStorage(listOf(first, second, third))

        assertEquals(listOf("b", "a", "x"), ordered.map { it.id })
    }

    @Test
    fun `deleted ticket ids persistence ordering trims blanks and sorts`() {
        val ordered = orderDeletedTicketIdListForStableStorage(listOf("  b ", "", "a", "   ", "c"))

        assertEquals(listOf("a", "b", "c"), ordered)
    }
}
