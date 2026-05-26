package com.lotterynet.pro.core.export

import com.lotterynet.pro.core.model.ResultShareRow
import com.lotterynet.pro.core.model.ResultsSharePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsShareLayoutContractsTest {

    @Test
    fun `compact result layout reserves text and right aligned balls for pick games`() {
        val layout = NativeBitmapExport.resolveResultRowLayout(
            ResultShareRow(
                displayName = "NJ Pick 4 Noche",
                first = "",
                second = "",
                third = "",
                pick4 = "1706",
            ),
        )

        assertEquals(NativeBitmapExport.ResultNumbersMode.PICK_ONLY, layout.mode)
        assertTrue(layout.numbersStartX >= 1070f)
        assertTrue(layout.nameMaxWidth > 760f)
        assertFalse(layout.drawPrimaryBalls)
    }

    @Test
    fun `pick result rows use the same ball size and centered rail for pick3 and pick4`() {
        val pick3 = NativeBitmapExport.resolvePickResultBallLayout(3)
        val pick4 = NativeBitmapExport.resolvePickResultBallLayout(4)

        assertEquals(pick3.radius, pick4.radius, 0.01f)
        assertEquals(42f, pick3.radius, 0.01f)
        assertEquals(pick3.spacing, pick4.spacing, 0.01f)
        assertTrue(pick3.startX > pick4.startX)
        assertEquals(pick3.endX, pick4.endX, 0.01f)
    }

    @Test
    fun `result prize labels use medal icons and fourth place medal`() {
        assertEquals(listOf("🥇", "🥈", "🥉"), NativeBitmapExport.resultPrizeLabels(3))
        assertEquals(listOf("🥇", "🥈", "🥉", "🏅"), NativeBitmapExport.resultPrizeLabels(4))
    }

    @Test
    fun `results header uses admin business logo when available`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "24-04-2026",
            bancaLogoUri = "content://lotterynet/logo-central.png",
        )

        assertEquals("content://lotterynet/logo-central.png", NativeBitmapExport.resolveResultsHeaderLogoUri(payload))
    }

    @Test
    fun `results header has no app logo fallback when admin logo is missing`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "24-04-2026",
            bancaLogoUri = "",
        )

        assertEquals(null, NativeBitmapExport.resolveResultsHeaderLogoUri(payload))
    }

    @Test
    fun `results header copy uses golden winning numbers wording with relative selected date`() {
        assertEquals(
            "Numeros ganadores · Sorteo de hoy",
            NativeBitmapExport.resolveResultsHeaderHighlight("25-04-2026", "25-04-2026"),
        )
        assertEquals(
            "Numeros ganadores · Sorteo de ayer",
            NativeBitmapExport.resolveResultsHeaderHighlight("24-04-2026", "25-04-2026"),
        )
        assertEquals(
            "Numeros ganadores · Sorteo de anteayer",
            NativeBitmapExport.resolveResultsHeaderHighlight("23-04-2026", "25-04-2026"),
        )
    }

    @Test
    fun `lottery poster data prefers national lottery and keeps its logo asset`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-05-2024",
            rows = listOf(
                ResultShareRow(
                    displayName = "Anguila Mañana",
                    first = "34",
                    second = "58",
                    third = "70",
                    logoAssetPath = "lot-logos/1.png",
                    drawTimeLabel = "10:00 AM",
                ),
                ResultShareRow(
                    displayName = "Lotería Nacional",
                    first = "53",
                    second = "94",
                    third = "23",
                    logoAssetPath = "lot-logos/13.png",
                    drawTimeLabel = "8:00 PM",
                ),
            ),
        )

        val data = NativeBitmapExport.resolveLotteryResultsViewData(payload)

        assertEquals("Lotería Nacional", data?.nombreLoteria)
        assertEquals("25-05-2024", data?.fecha)
        assertEquals("8:00 PM", data?.hora)
        assertEquals("53", data?.primerPremio)
        assertEquals("94", data?.segundoPremio)
        assertEquals("23", data?.tercerPremio)
        assertEquals("lot-logos/13.png", data?.logoAssetPath)
    }

    @Test
    fun `lottery poster data falls back to first row with primary results`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-05-2024",
            rows = listOf(
                ResultShareRow(displayName = "NJ Pick 3", first = "", second = "", third = "", pick3 = "071"),
                ResultShareRow(
                    displayName = "Anguila Mañana",
                    first = "34",
                    second = "58",
                    third = "70",
                    logoAssetPath = "lot-logos/1.png",
                    drawTimeLabel = "10:00 AM",
                ),
            ),
        )

        val data = NativeBitmapExport.resolveLotteryResultsViewData(payload)

        assertEquals("Anguila Mañana", data?.nombreLoteria)
        assertEquals("lot-logos/1.png", data?.logoAssetPath)
    }

    @Test
    fun `results export uses single poster only for one primary lottery`() {
        val single = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-05-2024",
            rows = listOf(ResultShareRow(displayName = "Lotería Nacional", first = "53", second = "94", third = "23")),
        )
        val multiple = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-05-2024",
            rows = listOf(
                ResultShareRow(displayName = "Lotería Nacional", first = "53", second = "94", third = "23"),
                ResultShareRow(displayName = "Anguila Mañana", first = "34", second = "58", third = "70"),
            ),
        )

        assertTrue(NativeBitmapExport.shouldRenderLotteryResultsPoster(single))
        assertFalse(NativeBitmapExport.shouldRenderLotteryResultsPoster(multiple))
    }

    @Test
    fun `single pick result does not use the large lottery poster`() {
        val pick3 = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-05-2024",
            rows = listOf(ResultShareRow(displayName = "NJ Pick 3 Día", first = "", second = "", third = "", pick3 = "8-7-3")),
        )
        val pick4 = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-05-2024",
            rows = listOf(ResultShareRow(displayName = "NJ Pick 4 Noche", first = "", second = "", third = "", pick4 = "1-7-0-6")),
        )

        assertFalse(NativeBitmapExport.shouldRenderLotteryResultsPoster(pick3))
        assertFalse(NativeBitmapExport.shouldRenderLotteryResultsPoster(pick4))
    }

    @Test
    fun `single pick result resolves to dense pick page instead of poster data`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-05-2024",
            rows = listOf(ResultShareRow(displayName = "NJ Pick 4 Noche", first = "", second = "", third = "", pick4 = "1-7-0-6")),
        )

        val pages = NativeBitmapExport.resolveResultsShareImagePages(payload)

        assertNull(NativeBitmapExport.resolveLotteryResultsViewData(payload))
        assertEquals(listOf(NativeBitmapExport.ResultsShareImageTemplate.PICK4_DENSE_LIST), pages.map { it.template })
    }

    @Test
    fun `multi lottery results export splits into five poster pages when many rows are shared`() {
        val rows = (1..12).map { index ->
            ResultShareRow(
                displayName = "Lotería $index",
                first = index.toString().padStart(2, '0'),
                second = (index + 10).toString(),
                third = (index + 20).toString(),
            )
        }

        val chunks = NativeBitmapExport.chunkResultsRowsForPoster(rows)

        assertEquals(5, chunks.size)
        assertEquals(rows.size, chunks.sumOf { it.size })
        assertTrue(chunks.all { it.size <= 3 })
    }

    @Test
    fun `multi lottery whatsapp capture uses one tall png instead of multiple pages`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-04-2026",
            rows = (1..8).map { index ->
                ResultShareRow(
                    displayName = "Lotería $index",
                    first = index.toString().padStart(2, '0'),
                    second = (index + 10).toString(),
                    third = (index + 20).toString(),
                )
            },
        )

        val spec = NativeBitmapExport.resolveResultadosWhatsAppBitmapSpec(payload)

        assertEquals(1600, spec.width)
        assertTrue(spec.height > 1600)
    }

    @Test
    fun `single lottery whatsapp result keeps existing poster template`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-04-2026",
            rows = listOf(ResultShareRow("Lotería Nacional", "53", "94", "23")),
        )

        val pages = NativeBitmapExport.resolveResultsShareImagePages(payload)

        assertEquals(1, pages.size)
        assertEquals(NativeBitmapExport.ResultsShareImageTemplate.LOTTERY_POSTER, pages.single().template)
    }

    @Test
    fun `cashier whatsapp result never uses large lottery poster`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-04-2026",
            rows = listOf(ResultShareRow("Lotería Nacional", "53", "94", "23")),
        )

        val pages = NativeBitmapExport.resolveCashierResultsShareImagePages(payload)

        assertEquals(1, pages.size)
        assertEquals(NativeBitmapExport.ResultsShareImageTemplate.LOTTERY_LIST, pages.single().template)
    }

    @Test
    fun `pick whatsapp result uses dense list pages when there are many pick rows`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-04-2026",
            rows = (1..34).map { index ->
                ResultShareRow(
                    displayName = "Estado $index Pick 4",
                    first = "",
                    second = "",
                    third = "",
                    pick4 = index.toString().padStart(4, '0'),
                )
            },
        )

        val pages = NativeBitmapExport.resolveResultsShareImagePages(payload)

        assertEquals(listOf(16, 16, 2), pages.map { it.rows.size })
        assertTrue(pages.all { it.template == NativeBitmapExport.ResultsShareImageTemplate.PICK4_DENSE_LIST })
    }

    @Test
    fun `pick whatsapp result separates pick three and pick four pages`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-04-2026",
            rows = listOf(
                ResultShareRow("Texas Pick 3 Morning", "", "", "", pick3 = "1-2-3", drawTimeLabel = "11:00 AM"),
                ResultShareRow("Texas Daily 4 Morning", "", "", "", pick4 = "1-2-3-4", drawTimeLabel = "11:00 AM"),
                ResultShareRow("California Daily 4 Day", "", "", "", stateLabel = "Sin resultado", drawTimeLabel = "7:00 PM"),
            ),
        )

        val pages = NativeBitmapExport.resolveResultsShareImagePages(payload)

        assertEquals(
            listOf(
                NativeBitmapExport.ResultsShareImageTemplate.PICK3_DENSE_LIST,
                NativeBitmapExport.ResultsShareImageTemplate.PICK4_DENSE_LIST,
            ),
            pages.map { it.template },
        )
        assertEquals(listOf("Texas Pick 3 Morning"), pages[0].rows.map { it.displayName })
        assertEquals(listOf("Texas Daily 4 Morning", "California Daily 4 Day"), pages[1].rows.map { it.displayName })
    }

    @Test
    fun `mixed whatsapp result separates lottery page from pick dense pages`() {
        val payload = ResultsSharePayload(
            bancaName = "Banca Central",
            dateLabel = "25-04-2026",
            rows = listOf(
                ResultShareRow("Lotería Nacional", "53", "94", "23"),
                ResultShareRow("Florida Pick 3", "", "", "", pick3 = "1-2-3"),
                ResultShareRow("Florida Pick 4", "", "", "", pick4 = "1-2-3-4"),
            ),
        )

        val pages = NativeBitmapExport.resolveResultsShareImagePages(payload)

        assertEquals(
            listOf(
                NativeBitmapExport.ResultsShareImageTemplate.LOTTERY_POSTER,
                NativeBitmapExport.ResultsShareImageTemplate.PICK3_DENSE_LIST,
                NativeBitmapExport.ResultsShareImageTemplate.PICK4_DENSE_LIST,
            ),
            pages.map { it.template },
        )
    }
}
