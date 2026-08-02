package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class NativeTicketSyncContractsTest {

    @Test
    fun `native ticket converts to web compatible payload and parses back`() {
        val ticket = TicketRecord(
            id = "native-1",
            serial = "NAT-001",
            securityCode = "SEC1",
            sellerId = "cashier-1",
            sellerUser = "cajero",
            adminId = "admin-1",
            adminUser = "admin",
            role = UserRole.CASHIER,
            createdAtEpochMs = 1_776_000_000_000,
            plays = listOf(
                PlayItem(
                    number = "164",
                    playType = "Q",
                    amount = 25.0,
                    lotteryId = "anguila-am",
                    lotteryName = "Anguila Mañana",
                ),
            ),
            total = 25.0,
            status = "active",
        )

        val payload = JSONArray().put(ticketRecordToWebCompatibleJson(ticket, "Banca Central")).toString()
        val parsed = parseWebTicketsPayload(payload)

        assertEquals(1, parsed.size)
        assertEquals("native-1", parsed.first().id)
        assertEquals("admin-1", parsed.first().adminId)
        assertEquals("cashier-1", parsed.first().sellerId)
        assertEquals("164", parsed.first().plays.first().number)
        assertEquals("Q", parsed.first().plays.first().playType)
        assertEquals(25.0, parsed.first().total, 0.0)
    }

    @Test
    fun `merge prefers imported server version by ticket id`() {
        val local = TicketRecord(id = "same", total = 10.0, status = "active")
        val imported = TicketRecord(id = "same", total = 10.0, status = "voided")

        val merged = mergeTicketsPreferImported(listOf(local), listOf(imported))

        assertEquals(1, merged.size)
        assertEquals("voided", merged.first().status)
        assertTrue(merged.first().createdAtEpochMs > 0)
    }

    @Test
    fun `deleted ticket tombstones prevent server import from restoring ticket`() {
        val deleted = TicketRecord(id = "deleted", total = 10.0, status = "voided")
        val active = TicketRecord(id = "active", total = 15.0, status = "active")

        val filtered = filterDeletedTickets(listOf(deleted, active), setOf("deleted"))

        assertEquals(listOf("active"), filtered.map { it.id })
    }

    @Test
    fun `local deleted tombstone cannot hide server winner ticket`() {
        val winner = TicketRecord(id = "winner", total = 149.0, totalPrize = 76.0, status = "winner")
        val active = TicketRecord(id = "active", total = 15.0, status = "active")

        val filtered = filterDeletedTickets(listOf(winner, active), setOf("winner"))
        val visible = filterServerVisibleTickets(listOf(winner, active), deletedIds = setOf("winner"))

        assertEquals(listOf("winner", "active"), filtered.map { it.id })
        assertEquals(listOf("winner", "active"), visible.map { it.id })
    }

    @Test
    fun `server sync payload removes deleted status tickets instead of publishing tombstones`() {
        val deleted = TicketRecord(id = "deleted", total = 10.0, status = "deleted")
        val active = TicketRecord(id = "active", total = 15.0, status = "active")
        val voided = TicketRecord(id = "voided", total = 20.0, status = "voided")

        val visible = filterServerVisibleTickets(listOf(deleted, active, voided), deletedIds = setOf("deleted"))

        assertEquals(listOf("active", "voided"), visible.map { it.id }.sorted())
    }

    @Test
    fun `ticket lottery identity helper recognizes id-only and rejects blank tickets`() {
        val malformed = TicketRecord(
            id = "malformed",
            status = "active",
            total = 10.0,
            plays = listOf(
                PlayItem(number = "22", playType = "Q", amount = 10.0),
            ),
        )
        val validByIdOnly = malformed.copy(
            id = "valid-by-id",
            plays = listOf(
                PlayItem(number = "22", playType = "Q", amount = 10.0, lotteryId = "anguila"),
            ),
        )

        assertFalse(malformed.hasMeaningfulLotteryIdentity())
        assertTrue(validByIdOnly.hasMeaningfulLotteryIdentity())
    }

    @Test
    fun `server ticket metadata without items is not materialized as an empty ticket`() {
        val payload = JSONObject()
            .put(
                "tickets",
                JSONArray().put(
                    JSONObject()
                        .put("id", "d05811eb-8777-43a3-8f28-8009421fe096")
                        .put("client_request_id", "native-1780750776220")
                        .put("ticket_code", "LN-46FAB9-BC35FF")
                        .put("admin_key", "ADM-163C38")
                        .put("cashier_key", "CAJ-AF4874")
                        .put("status", "PERDEDOR")
                        .put("total_amount", 46.0)
                        .put("server_created_at", "2026-06-06T12:59:37.099938Z")
                        .put("draw_date_real", "2026-06-06"),
                ),
            )
            .put("items", JSONArray())

        val parsed = parseTicketDeltaPayload(payload.toString(), ownerKey = "CAJ-AF4874")

        assertTrue(parsed.isEmpty())
    }
}
