package com.lotterynet.pro.ui.tickets

import com.journeyapps.barcodescanner.ScanOptions
import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketLookupContractsTest {
    @Test
    fun `ticket qr scanner stays portrait locked`() {
        val contract = resolveTicketQrScannerContract()

        assertTrue(contract.orientationLocked)
        assertEquals(QrCaptureActivity::class.java.name, contract.captureActivityClassName)
        assertEquals(
            listOf(ScanOptions.QR_CODE, ScanOptions.CODE_128, ScanOptions.CODE_39),
            contract.formats,
        )
    }

    @Test
    fun `ticket qr json scan searches by ticket id`() {
        val query = resolveTicketLookupQueryFromScan(
            """{"version":2,"id":"TICKET-123","banca":"Banca","total":50}""",
        )

        assertEquals("TICKET-123", query)
    }

    @Test
    fun `ticket lookup auto scan extra is stable for venta qr entry`() {
        assertEquals("ticket_lookup_auto_scan", TicketLookupActivity.EXTRA_AUTO_SCAN)
    }

    @Test
    fun `venta qr entry offers pay and duplicate scanner choices`() {
        val contract = com.lotterynet.pro.ui.sales.resolveVentaQrLookupActionContract()
        val choices = com.lotterynet.pro.ui.sales.resolveVentaQrLookupChoices()

        assertEquals("buscar", contract.lookupMode)
        assertEquals(listOf("pagar", "duplicar"), choices.map { it.lookupMode })
        assertTrue(choices.all { it.autoScan })
    }

    @Test
    fun `ticket qr url scan searches by serial`() {
        val query = resolveTicketLookupQueryFromScan("https://lotterynet/ticket?serial=ABC-999")

        assertEquals("ABC-999", query)
    }

    @Test
    fun `ticket thermal qr scan searches by serial`() {
        val query = resolveTicketLookupQueryFromScan("LN|NAT-900|SEC900|35|1777072400000")

        assertEquals("NAT-900", query)
    }

    @Test
    fun `qr scan auto opens only one exact ticket match`() {
        val ticket = TicketRecord(id = "native-1", serial = "NAT-123", securityCode = "SEC123")
        val other = TicketRecord(id = "native-2", serial = "NAT-456", securityCode = "SEC456")

        assertEquals(ticket, resolveAutoOpenScannedTicket("NAT-123", listOf(ticket, other)))
        assertEquals(ticket, resolveAutoOpenScannedTicket("SEC123", listOf(ticket, other)))
        assertNull(resolveAutoOpenScannedTicket("NAT", listOf(ticket, other)))
    }

    @Test
    fun `lookup modes keep clear operational titles`() {
        assertEquals("Duplicar ticket", LookupMode.from("duplicar").title)
        assertEquals("Cobro de ticket", LookupMode.from("pagar").title)
        assertEquals("Eliminar ticket", LookupMode.from("anular").title)
    }

    @Test
    fun `duplicate lookup row exposes direct duplicate next to open`() {
        assertEquals(
            listOf(TicketLookupRowAction.OPEN, TicketLookupRowAction.DUPLICATE),
            resolveTicketLookupRowActions(LookupMode.DUPLICATE),
        )
    }

    @Test
    fun `duplicate lookup duplicate action opens official duplicate options`() {
        assertEquals("duplicar", resolveTicketLookupDuplicateActionOfficialModeKey())
    }

    @Test
    fun `duplicate lookup open sends official duplicate mode`() {
        assertEquals("duplicar", LookupMode.DUPLICATE.officialModeKey)
    }

    @Test
    fun `lookup row labels active prize as pending payout`() {
        val ticket = TicketRecord(id = "win", status = "active", total = 25.0, totalPrize = 850.0)

        assertEquals("Pendiente pago", lookupStatusLabel(ticket))
        assertEquals("$ 850", lookupAmountLabel(ticket))
    }

    @Test
    fun `lookup row labels legacy paid prize as paid`() {
        val ticket = TicketRecord(id = "paid-legacy", status = "pagado", total = 25.0, totalPrize = 850.0)

        assertEquals("Pagado", lookupStatusLabel(ticket))
        assertEquals("$ 25", lookupAmountLabel(ticket))
    }

    @Test
    fun `bulk pay only includes unpaid winning tickets`() {
        val pending = TicketRecord(id = "winner", status = "winner", total = 25.0, totalPrize = 850.0)
        val paid = TicketRecord(id = "paid", status = "paid", total = 25.0, totalPrize = 850.0)
        val voided = TicketRecord(id = "voided", status = "voided", total = 25.0, totalPrize = 850.0)
        val active = TicketRecord(id = "active", status = "active", total = 25.0, totalPrize = 0.0)

        assertTrue(isLookupBulkPayableTicket(pending))
        assertFalse(isLookupBulkPayableTicket(paid))
        assertFalse(isLookupBulkPayableTicket(voided))
        assertFalse(isLookupBulkPayableTicket(active))
    }

    @Test
    fun `invalid lookup mode falls back to search without repeating ticket`() {
        assertEquals(LookupMode.SEARCH, LookupMode.from("modo-raro"))
        assertEquals(LookupMode.SEARCH, LookupMode.from(""))
        assertEquals(LookupMode.SEARCH, LookupMode.from(null))
    }
}
