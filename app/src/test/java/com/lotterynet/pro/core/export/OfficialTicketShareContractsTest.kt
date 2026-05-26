package com.lotterynet.pro.core.export

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialTicketShareContractsTest {

    @Test
    fun `official ticket preview keeps premium whatsapp styling`() {
        val repository = StaticExportTemplateRepository()
        val html = repository.buildOfficialTicketPreviewHtml(
            ticket = sampleTicket(),
            bancaName = "LotteryNet",
            securityCode = "ABC123",
        )

        assertTrue(html.contains("background:#16a34a"))
        assertTrue(html.contains("color:#f8df8c"))
        assertTrue(html.contains("font-weight:900"))
        assertTrue(html.contains("ticket-summary"))
        assertTrue(html.contains("$ 125"))
        assertTrue(html.contains("ticket-security"))
    }

    @Test
    fun `whatsapp share envelope sends only the ticket image`() {
        val repository = StaticExportTemplateRepository()

        val envelope = repository.buildTicketWhatsAppShare(
            ticket = sampleTicket(),
            bancaName = "LotteryNet",
        )

        assertEquals("", envelope.text)
        assertEquals("ticket-TK-12345.png", envelope.fileName)
    }

    @Test
    fun `official ticket sorts plays by quiniela pale and tripleta`() {
        val sorted = NativeBitmapExport.sortOfficialTicketPlays(
            listOf(
                play("T", "65-22-33"),
                play("P", "89-65"),
                play("Q", "21"),
                play("SP", "12-34"),
                play("Q", "98"),
            ),
        )

        assertEquals(listOf("Q", "Q", "P", "SP", "T"), sorted.map { it.playType })
    }

    @Test
    fun `official ticket security layout separates label and value`() {
        val layout = NativeBitmapExport.resolveOfficialTicketSecurityLayout(top = 100f)

        assertTrue(layout.valueBaselineY - layout.labelBaselineY >= 50f)
        assertTrue(layout.valueTextSize <= 56f)
        assertTrue(layout.boxBottomY - layout.valueBaselineY >= 28f)
    }

    @Test
    fun `official ticket bitmap play numbers render larger`() {
        val layout = NativeBitmapExport.resolveOfficialTicketBitmapPlayVisual(
            partCount = 2,
            hasLongPart = false,
        )

        assertTrue(layout.ballRadiusPx >= 28f)
        assertTrue(layout.textSizePx >= 32f)
        assertTrue(layout.rowHeightPx in 90..110)
    }

    @Test
    fun `official ticket lottery header shows stronger logo and table columns`() {
        val layout = NativeBitmapExport.resolveOfficialTicketLotteryHeaderLayout()

        assertTrue(layout.logoSizePx >= 58f)
        assertTrue(layout.heightPx >= 76)
        assertEquals("Jugada", layout.playColumnHeader)
        assertEquals("Monto", layout.amountColumnHeader)
    }

    @Test
    fun `official ticket bitmap stays compact enough for whatsapp with many lotteries`() {
        val ticket = sampleTicket().copy(
            total = 1_250.0,
            plays = (1..12).map { index ->
                PlayItem(
                    lotteryId = "lot-$index",
                    lotteryName = "Loteria $index",
                    playType = "Q",
                    number = index.toString().padStart(2, '0'),
                    amount = 100.0,
                )
            },
        )

        val height = NativeBitmapExport.estimateOfficialTicketBitmapHeight(
            ticket = ticket,
            securityCode = "ABC123",
        )

        assertTrue("height=$height", height <= 3_900)
    }

    @Test
    fun `official ticket bitmap switches to dense table when one lottery has many plays`() {
        val density = NativeBitmapExport.resolveOfficialTicketBitmapDensity(
            totalPlayCount = 47,
            groupCount = 1,
            maxGroupPlayCount = 47,
        )

        assertTrue(density.compact)
        assertTrue(density.rowHeightPx <= 50)
        assertTrue(density.playTextSizePx >= 30f)
    }

    @Test
    fun `official ticket estimate uses dense rows for a long single lottery ticket`() {
        val ticket = sampleTicket().copy(
            total = 470.0,
            plays = (1..47).map { index ->
                PlayItem(
                    lotteryId = "loteka",
                    lotteryName = "Loteka",
                    playType = if (index % 5 == 0) "P" else "Q",
                    number = if (index % 5 == 0) "${index.toString().padStart(2, '0')}-69" else index.toString().padStart(2, '0'),
                    amount = 10.0,
                )
            },
        )

        val height = NativeBitmapExport.estimateOfficialTicketBitmapHeight(ticket)

        assertTrue("height=$height", height <= 2_600)
    }

    @Test
    fun `official ticket header keeps banca name visible when logo exists`() {
        val identity = NativeBitmapExport.resolveOfficialTicketHeaderIdentity(
            bancaName = "Banca La Fe",
            hasLogo = true,
        )

        assertEquals("Banca La Fe", identity.primaryText)
        assertEquals("TICKET OFICIAL", identity.secondaryText)
        assertTrue(identity.logoWidthPx >= 420f)
    }

    @Test
    fun `official ticket brand logo fills its box instead of shrinking`() {
        val target = NativeBitmapExport.resolveBrandLogoTarget(
            bitmapWidth = 120,
            bitmapHeight = 60,
            left = 0f,
            top = 0f,
            width = 420f,
            height = 190f,
        )

        assertTrue(target.width() >= 420f)
        assertTrue(target.height() >= 190f)
    }

    private fun sampleTicket(): TicketRecord {
        return TicketRecord(
            id = "TK-12345",
            serial = "12345",
            createdAtEpochMs = 1_713_600_000_000,
            total = 125.0,
            status = "active",
            plays = listOf(
                PlayItem(
                    lotteryId = "anguila",
                    lotteryName = "Anguila",
                    playType = "Q",
                    number = "25",
                    amount = 65.0,
                ),
                PlayItem(
                    lotteryId = "real",
                    lotteryName = "Real",
                    playType = "P",
                    number = "12-34",
                    amount = 60.0,
                ),
            ),
        )
    }

    private fun play(type: String, number: String): PlayItem {
        return PlayItem(
            lotteryId = "anguila",
            lotteryName = "Anguila",
            playType = type,
            number = number,
            amount = 65.0,
        )
    }
}
