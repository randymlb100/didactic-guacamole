package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.RechargeRecord
import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeOperationalHydrationTest {

    @Test
    fun `web tickets payload maps legacy sales into native ticket records`() {
        val payload = """
            [
              {
                "id": "WEB-1",
                "serial": "SER-1",
                "securityCode": "SC-1",
                "adminId": "ADM-1",
                "vendedorId": "CAJ-9",
                "vendedorNombre": "Randy",
                "vendedorRol": "cashier",
                "createdAtMs": 1714151800000,
                "total": 125.0,
                "status": "active",
                "items": [
                  {
                    "type": "Q",
                    "nums": "12",
                    "amt": 125.0,
                    "lotId": "king-tarde",
                    "lotName": "King Tarde"
                  }
                ]
              }
            ]
        """.trimIndent()

        val result = parseWebTicketsPayload(payload)

        assertEquals(1, result.size)
        assertEquals("WEB-1", result.first().id)
        assertEquals("SER-1", result.first().serial)
        assertEquals("ADM-1", result.first().adminId)
        assertEquals("CAJ-9", result.first().sellerId)
        assertEquals("Randy", result.first().sellerUser)
        assertEquals(125.0, result.first().total, 0.0)
        assertEquals(1, result.first().plays.size)
        assertEquals("12", result.first().plays.first().number)
        assertEquals("King Tarde", result.first().plays.first().lotteryName)
    }

    @Test
    fun `ticket merge keeps imported version for matching ids`() {
        val existing = listOf(
            TicketRecord(id = "A", total = 10.0, createdAtEpochMs = 100L),
            TicketRecord(id = "B", total = 20.0, createdAtEpochMs = 200L),
        )
        val imported = listOf(
            TicketRecord(id = "B", total = 99.0, createdAtEpochMs = 200L),
            TicketRecord(id = "C", total = 30.0, createdAtEpochMs = 300L),
        )

        val merged = mergeTicketsPreferImported(existing, imported)

        assertEquals(listOf("C", "B", "A"), merged.map { it.id })
        assertEquals(99.0, merged.first { it.id == "B" }.total, 0.0)
    }

    @Test
    fun `ticket merge does not replace a local winner with stale active imported copy`() {
        val existing = listOf(
            TicketRecord(id = "WIN-1", totalPrize = 600.0, status = "winner", createdAtEpochMs = 200L),
        )
        val imported = listOf(
            TicketRecord(id = "WIN-1", totalPrize = 0.0, status = "active", createdAtEpochMs = 200L),
        )

        val merged = mergeTicketsPreferImported(existing, imported)

        assertEquals("winner", merged.single().status)
        assertEquals(600.0, merged.single().totalPrize, 0.0)
    }

    @Test
    fun `ticket merge keeps paid imported copy over local pending winner`() {
        val existing = listOf(
            TicketRecord(id = "WIN-2", totalPrize = 600.0, status = "winner", createdAtEpochMs = 200L),
        )
        val imported = listOf(
            TicketRecord(id = "WIN-2", totalPrize = 600.0, status = "paid", createdAtEpochMs = 200L),
        )

        val merged = mergeTicketsPreferImported(existing, imported)

        assertEquals("paid", merged.single().status)
        assertEquals(600.0, merged.single().totalPrize, 0.0)
    }

    @Test
    fun `ticket merge keeps local paid payout over stale active server copy`() {
        val existing = listOf(
            TicketRecord(id = "WIN-3", total = 20.0, totalPrize = 120.0, status = "paid", createdAtEpochMs = 200L),
        )
        val imported = listOf(
            TicketRecord(id = "WIN-3", total = 20.0, totalPrize = 0.0, status = "active", createdAtEpochMs = 200L),
        )

        val merged = mergeTicketsPreferImported(existing, imported)

        assertEquals("paid", merged.single().status)
        assertEquals(120.0, merged.single().totalPrize, 0.0)
    }

    @Test
    fun `ticket merge treats spanish paid status as terminal payout`() {
        val existing = listOf(
            TicketRecord(id = "WIN-4", total = 20.0, totalPrize = 120.0, status = "winner", createdAtEpochMs = 200L),
        )
        val imported = listOf(
            TicketRecord(id = "WIN-4", total = 20.0, totalPrize = 0.0, status = "pagado", createdAtEpochMs = 200L),
        )

        val merged = mergeTicketsPreferImported(existing, imported)

        assertEquals("pagado", merged.single().status)
        assertEquals(120.0, merged.single().totalPrize, 0.0)
    }

    @Test
    fun `ticket merge does not revive a voided ticket with stale active winner import`() {
        val existing = listOf(
            TicketRecord(id = "VOID-1", totalPrize = 0.0, status = "voided", createdAtEpochMs = 200L),
        )
        val imported = listOf(
            TicketRecord(id = "VOID-1", totalPrize = 600.0, status = "active", createdAtEpochMs = 200L),
        )

        val merged = mergeTicketsPreferImported(existing, imported)

        assertEquals("voided", merged.single().status)
        assertEquals(0.0, merged.single().totalPrize, 0.0)
    }

    @Test
    fun `ticket merge lets a deleted tombstone override an active server ticket`() {
        val existing = listOf(
            TicketRecord(id = "DEL-1", total = 50.0, totalPrize = 600.0, status = "active", createdAtEpochMs = 200L),
        )
        val imported = listOf(
            TicketRecord(id = "DEL-1", total = 50.0, totalPrize = 0.0, status = "deleted", createdAtEpochMs = 200L),
        )

        val merged = mergeTicketsPreferImported(existing, imported)

        assertEquals("deleted", merged.single().status)
        assertEquals(0.0, merged.single().totalPrize, 0.0)
    }

    @Test
    fun `deleted ticket status survives web sync round trip`() {
        val ticket = TicketRecord(id = "DEL-2", status = "deleted", total = 25.0, createdAtEpochMs = 200L)

        val payload = "[${ticketRecordToWebCompatibleJson(ticket, "Banca Central")}]"
        val parsed = parseWebTicketsPayload(payload)

        assertEquals("deleted", parsed.single().status)
    }

    @Test
    fun `web sync payload publishes total premio alias for paid tickets`() {
        val ticket = TicketRecord(id = "PAID-1", status = "paid", total = 25.0, totalPrize = 750.0, createdAtEpochMs = 200L)

        val json = ticketRecordToWebCompatibleJson(ticket, "Banca Central")
        val parsed = parseWebTicketsPayload("[${json}]")

        assertEquals(750.0, json.getDouble("totalPremio"), 0.0)
        assertEquals(750.0, parsed.single().totalPrize, 0.0)
    }

    @Test
    fun `ticket sync owner matches legacy admin user and seller fields`() {
        val paidByAdminUser = TicketRecord(
            id = "PAID-LEGACY",
            adminId = "",
            adminUser = "admin01",
            sellerId = "cajero01",
            sellerUser = "Caja 01",
            status = "paid",
            totalPrize = 300.0,
        )
        val json = ticketRecordToWebCompatibleJson(paidByAdminUser, "Banca Central")

        assertTrue(matchesNativeTicketSyncOwner(paidByAdminUser, "admin01"))
        assertTrue(matchesNativeTicketSyncOwner(paidByAdminUser, "cajero01"))
        assertTrue(matchesNativeTicketSyncOwner(paidByAdminUser, "Caja 01"))
        assertTrue(matchesNativeTicketSyncOwner(json, "admin01"))
        assertTrue(matchesNativeTicketSyncOwner(json, "cajero01"))
        assertTrue(matchesNativeTicketSyncOwner(json, "Caja 01"))
    }

    @Test
    fun `remote ticket payload keeps deleted ids outside visible tickets`() {
        val visible = TicketRecord(id = "ACTIVE-1", status = "active", total = 25.0, createdAtEpochMs = 300L)
        val payload = buildWebTicketRemotePayload(
            tickets = listOf(visible),
            deletedIds = setOf("DELETED-1"),
            banca = "Banca Central",
        )

        val parsed = parseWebTicketRemotePayload(payload)

        assertEquals(listOf("ACTIVE-1"), parsed.tickets.map { it.id })
        assertEquals(setOf("DELETED-1"), parsed.deletedIds)
    }

    @Test
    fun `legacy ticket array payload remains supported`() {
        val payload = "[${ticketRecordToWebCompatibleJson(TicketRecord(id = "LEGACY-1"), "Banca Central")}]"

        val parsed = parseWebTicketRemotePayload(payload)

        assertEquals(listOf("LEGACY-1"), parsed.tickets.map { it.id })
        assertTrue(parsed.deletedIds.isEmpty())
    }

    @Test
    fun `web recharge payload resolves legacy recargas into native records`() {
        val payload = """
            [
              {
                "id": "REC-1",
                "prov": "claro",
                "phone": "8095550000",
                "amt": 200.0,
                "userId": "CAJ-1",
                "userName": "Caja 1",
                "adminId": "ADM-1",
                "date": "23-04-2026",
                "time": "03:28 PM"
              }
            ]
        """.trimIndent()

        val result = parseWebRechargesPayload(payload)

        assertEquals(1, result.size)
        assertEquals("REC-1", result.first().id)
        assertEquals("claro", result.first().providerName)
        assertEquals("8095550000", result.first().phoneNumber)
        assertEquals(200.0, result.first().amount, 0.0)
        assertEquals("ADM-1", result.first().adminId)
        assertTrue(result.first().createdAtEpochMs > 0L)
    }

    @Test
    fun `recharge merge keeps imported record for matching ids`() {
        val existing = listOf(
            RechargeRecord(id = "R-1", amount = 25.0, createdAtEpochMs = 100L),
        )
        val imported = listOf(
            RechargeRecord(id = "R-1", amount = 50.0, createdAtEpochMs = 100L),
            RechargeRecord(id = "R-2", amount = 75.0, createdAtEpochMs = 200L),
        )

        val merged = mergeRechargesPreferImported(existing, imported)

        assertEquals(listOf("R-2", "R-1"), merged.map { it.id })
        assertEquals(50.0, merged.first { it.id == "R-1" }.amount, 0.0)
    }
}
