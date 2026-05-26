package com.lotterynet.pro.core.printing

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import com.lotterynet.pro.core.model.TicketRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalTicketRendererTest {

    @Test
    fun `thermal renderer outputs styled premium hierarchy`() {
        val ticket = TicketRecord(
            id = "ticket-1",
            serial = "ABC-12345",
            drawDateKey = "2026-05-09",
            plays = listOf(
                PlayItem(number = "12", playType = "Q", amount = 25.0, lotteryName = "Loto Real"),
                PlayItem(number = "345", playType = "P3", amount = 40.0, lotteryName = "New York Tarde"),
            ),
            total = 65.0,
            sellerUser = "Randy",
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Mi Banca",
            prefs = ThermalPrinterPrefs(showSecurity = true, showOriginal = true),
            drawTimesByLottery = mapOf("Loto Real" to "08:30 PM"),
        )

        assertTrue(output.contains("[[TITLE|tall]]MI BANCA"))
        assertTrue(output.contains("[[CENTER]]ORIGINAL"))
        assertTrue(output.contains("[[BOLD]]ABC-12345"))
        assertTrue(output.contains("TICKET VALIDO"))
        assertTrue(output.contains("PARA EL SORTEO"))
        assertTrue(output.contains("DE"))
        assertTrue(output.contains("2026"))
        assertTrue(output.contains("08:30 PM"))
        assertTrue(output.contains("[[LOTTERY|large]]LOTO REAL"))
        assertTrue(output.contains("[[TOTAL|tall]]TOTAL"))
        assertTrue(output.contains("CODIGO:"))
    }

    @Test
    fun `thermal payout receipt prints short paid proof with ticket id and prize`() {
        val ticket = TicketRecord(
            id = "ticket-ganador-1",
            serial = "ABC-900",
            status = "paid",
            total = 35.0,
            totalPrize = 850.0,
            sellerUser = "caja1",
        )

        val output = ThermalTicketRenderer().renderPayoutReceipt(
            ticket = ticket,
            bancaName = "Mi Banca",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )
        val visibleLines = output.lines().map { ThermalLineStyling.parse(it).text }

        assertTrue(visibleLines.contains("COMPROBANTE DE PAGO"))
        assertTrue(visibleLines.any { it.contains("ABC-900") })
        assertTrue(visibleLines.any { it.contains("PAGO:") && it.contains("850") })
        assertFalse(visibleLines.any { it.contains("JUGADAS") })
    }

    @Test
    fun `thermal payout receipt does not print sale total as payout`() {
        val ticket = TicketRecord(
            id = "ticket-ganador-2",
            serial = "ABC-139",
            status = "paid",
            total = 139.0,
            totalPrize = 0.0,
            sellerUser = "bancay06",
        )

        val output = ThermalTicketRenderer().renderPayoutReceipt(
            ticket = ticket,
            bancaName = "Banca juan",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )
        val visibleLines = output.lines().map { ThermalLineStyling.parse(it).text }

        assertFalse(visibleLines.any { it.contains("PAGO:") && it.contains("139") })
        assertTrue(visibleLines.any { it.contains("PAGO:") && it.contains("PENDIENTE") })
    }

    @Test
    fun `thermal renderer groups super pale with both lotteries and table header`() {
        val ticket = TicketRecord(
            id = "ticket-2",
            serial = "SP-900",
            plays = listOf(
                PlayItem(
                    number = "1225",
                    playType = "SP",
                    amount = 100.0,
                    lotteryName = "Anguila Tarde",
                    secondaryLotteryName = "King Lottery Noche",
                ),
            ),
            total = 100.0,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(showOriginal = true),
        )

        assertTrue(output.contains("[[LOTTERY|large]]ANGUILA PM / KING PM"))
        assertTrue(output.lines().any { line ->
            ThermalLineStyling.parse(line).text == "JUGADA               MONTO"
        })
        assertTrue(output.contains("SP 12/25"))
        assertTrue(output.contains("[[TOTAL|tall]]TOTAL"))
    }

    @Test
    fun `thermal renderer keeps play amount in one row on narrow paper even when scales are large`() {
        val ticket = TicketRecord(
            id = "ticket-3",
            serial = "LG-100",
            plays = listOf(
                PlayItem(number = "164", playType = "Q", amount = 25.0, lotteryName = "Anguila Mañana"),
            ),
            total = 25.0,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(
                playNumberScale = "large",
                amountScale = "large",
                typeLabelMode = "full",
            ),
        )
        val lines = output.lines()

        val visibleLines = lines.map { ThermalLineStyling.parse(it).text }

        assertTrue(visibleLines.any { it.startsWith("Quiniela 164") && it.endsWith("25") && it.length <= 26 })
        assertFalse(visibleLines.any { it.trim() == "MONTO" })
        assertFalse(lines.any { it.startsWith("[[PLAY_NUMBER|large]]Quiniela 164") })
        assertFalse(lines.any { it.startsWith("[[PLAY_AMOUNT|large]]MONTO") })
    }

    @Test
    fun `thermal renderer strips accents before printer text to avoid mojibake`() {
        val ticket = TicketRecord(
            id = "ticket-4",
            serial = "MX-100",
            plays = listOf(
                PlayItem(number = "02", playType = "Q", amount = 200.0, lotteryName = "Anguila Mañana"),
            ),
            total = 200.0,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(showOriginal = true),
        )

        assertTrue(output.contains("ANGUILA AM"))
        assertFalse(output.contains("TICKET TERMICO"))
        assertFalse(output.contains("MAÑANA"))
        assertFalse(output.contains("TÉRMICO"))
        assertFalse(output.contains(" · "))
    }

    @Test
    fun `thermal lottery names are shortened for narrow paper`() {
        assertEquals("ANGUILA AM", formatThermalLotteryName("Anguila Mañana"))
        assertEquals("ANGUILA PM", formatThermalLotteryName("Anguila Noche"))
        assertEquals("HAITI BOLET 11:30 AM", formatThermalLotteryName("Haiti Bolet 11:30 AM"))
        assertEquals("HAITI BOLET 6:30 PM", formatThermalLotteryName("Haiti Bolet 6:30 PM"))
    }

    @Test
    fun `thermal ticket keeps monto inside 58mm width and only hides zero cents`() {
        val ticket = TicketRecord(
            id = "ticket-5",
            serial = "NAT-12345678",
            plays = listOf(
                PlayItem(number = "12", playType = "Q", amount = 25.0, lotteryName = "Loto Real"),
                PlayItem(number = "34-56", playType = "P", amount = 50.5, lotteryName = "Loto Real"),
            ),
            total = 75.5,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )
        val visibleLines = output.lines().map { line ->
            ThermalLineStyling.parse(line).text
        }

        assertFalse(output.contains("TICKET TERMICO"))
        assertTrue(visibleLines.any { it == "JUGADA               MONTO" })
        assertTrue(visibleLines.any { it.endsWith("25") })
        assertTrue(visibleLines.any { it.endsWith("50.50") })
        assertFalse(output.contains("25.00"))
        assertTrue(output.contains("50.50"))
        assertTrue(
            output.lines()
                .filterNot { it.startsWith("[[QR]]") }
                .map { ThermalLineStyling.parse(it).text }
                .all { it.length <= 26 },
        )
    }

    @Test
    fun `thermal ticket does not truncate long play numbers and keeps amount aligned on narrow paper`() {
        val longNumber = "123456789012345678901234567890"
        val ticket = TicketRecord(
            id = "ticket-long-number",
            serial = "NAT-LONG-NUM",
            plays = listOf(
                PlayItem(number = longNumber, playType = "Q", amount = 89.0, lotteryName = "Nacional"),
            ),
            total = 89.0,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )
        val styledLines = output.lines().map(ThermalLineStyling::parse)
        val visibleLines = styledLines.map { it.text }
        val printedDigits = visibleLines
            .filter { it.startsWith("Q ") || it.all(Char::isDigit) }
            .joinToString("") { it.filter(Char::isDigit) }
        val amountLine = styledLines.single { it.style == ThermalLineStyle.PLAY_AMOUNT }
        val totalLine = styledLines.single { it.style == ThermalLineStyle.TOTAL }

        assertEquals(longNumber, printedDigits)
        assertEquals("tall", styledLines.first { it.text.startsWith("Q ") }.scale)
        assertTrue(amountLine.text.startsWith("MONTO"))
        assertTrue(amountLine.text.endsWith("89"))
        assertTrue(totalLine.text.startsWith("TOTAL"))
        assertTrue(totalLine.text.endsWith("89"))
        assertEquals(amountLine.text.length - 2, totalLine.text.length - 2)
        assertTrue(visibleLines.filterNot { it == "QR" }.all { it.length <= 26 })
    }

    @Test
    fun `thermal ticket can mark copies and includes qr payload`() {
        val ticket = TicketRecord(
            id = "ticket-copy",
            serial = "NAT-900",
            securityCode = "SEC900",
            plays = listOf(
                PlayItem(number = "789", playType = "P3", amount = 35.0, lotteryName = "New York Tarde"),
            ),
            total = 35.0,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
            printMark = TicketPrintMark.COPIA,
        )
        val visibleLines = output.lines().map { ThermalLineStyling.parse(it).text }

        assertTrue(visibleLines.any { it.contains("COPIA") })
        assertTrue(output.contains("[[QR]]"))
        assertTrue(output.contains("NAT-900"))
        assertFalse(output.contains("TERMICO"))
        assertFalse(visibleLines.any { it.trim() == "MONTO" })
    }

    @Test
    fun `thermal ticket prints pick suffix from play type while stored number stays clean`() {
        val ticket = TicketRecord(
            id = "ticket-pick-suffix",
            serial = "NAT-PICK",
            plays = listOf(
                PlayItem(number = "852", playType = "P3", amount = 5.0, lotteryName = "Texas Pick 3"),
                PlayItem(number = "852", playType = "P3BOX", amount = 5.0, lotteryName = "Texas Pick 3"),
            ),
            total = 10.0,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )
        val visibleLines = output.lines().map { ThermalLineStyling.parse(it).text }

        assertTrue(visibleLines.any { it.startsWith("P3 852S") && it.endsWith("5") })
        assertTrue(visibleLines.any { it.startsWith("P3BOX 852B") && it.endsWith("5") })
        assertFalse(visibleLines.any { it.contains("852SS") || it.contains("852BB") })
    }

    @Test
    fun `thermal qr parser keeps payload for scannable preview`() {
        val payload = "LN|NAT-900|SEC900|35|1777072400000"

        val parsed = ThermalLineStyling.parse("[[QR]]$payload")

        assertEquals(ThermalLineStyle.QR, parsed.style)
        assertEquals("QR", parsed.text)
        assertEquals(payload, parsed.payload)
    }

    @Test
    fun `thermal ticket keeps all mixed play amounts visible on 58mm`() {
        val ticket = TicketRecord(
            id = "ticket-mixed",
            serial = "NAT-901",
            plays = listOf(
                PlayItem(number = "12", playType = "Q", amount = 25.0, lotteryName = "Loto Real"),
                PlayItem(number = "34-56", playType = "P", amount = 50.0, lotteryName = "Loto Real"),
                PlayItem(number = "789", playType = "P3", amount = 35.0, lotteryName = "New York Tarde"),
            ),
            total = 110.0,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(paperWidth = "58", amountScale = "large", playNumberScale = "large"),
        )
        val visibleLines = output.lines().map { ThermalLineStyling.parse(it).text }

        assertTrue(visibleLines.any { it.contains("25") })
        assertTrue(visibleLines.any { it.contains("50") })
        assertTrue(visibleLines.any { it.contains("35") })
        assertFalse(visibleLines.any { it.trim() == "MONTO" })
    }

    @Test
    fun `thermal ticket prints strong organized ticket with cut space after qr`() {
        val ticket = TicketRecord(
            id = "ticket-presence",
            serial = "NAT-777",
            plays = listOf(
                PlayItem(number = "26", playType = "Q", amount = 100.0, lotteryName = "Nacional"),
                PlayItem(number = "89", playType = "Q", amount = 50.0, lotteryName = "Nacional"),
            ),
            total = 150.0,
            sellerUser = "Caja1",
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )
        val lines = output.lines()
        val visibleLines = lines.map { ThermalLineStyling.parse(it).text }

        assertTrue(lines.contains("[[TITLE|tall]]BANCA CENTRAL"))
        assertTrue(lines.contains("[[LOTTERY|large]]NACIONAL"))
        assertTrue(lines.any { it.startsWith("[[PLAY_NUMBER|tall]]Q 26") })
        assertTrue(lines.any { it.startsWith("[[PLAY_NUMBER|tall]]Q 89") })
        assertTrue(visibleLines.any { it.startsWith("Q 26") && it.endsWith("100") && it.length <= 26 })
        assertTrue(visibleLines.any { it.startsWith("Q 89") && it.endsWith("50") && it.length <= 26 })
        assertTrue(lines.any { it.startsWith("[[TOTAL|tall]]TOTAL") && it.endsWith("150") })
        assertFalse(visibleLines.any { it.trim() == "MONTO" })
        assertTrue(visibleLines.filter { it.startsWith("Q ") }.all { it.length <= 26 })
        val qrIndex = lines.indexOfFirst { it.startsWith("[[QR]]") }
        assertTrue(qrIndex >= 0)
        assertTrue(lines.drop(qrIndex + 1).take(5).all { it.isBlank() })
    }

    @Test
    fun `thermal ticket sends short play and total as positioned money rows`() {
        val ticket = TicketRecord(
            id = "ticket-positioned-money",
            serial = "NAT-41882100",
            plays = listOf(
                PlayItem(number = "14-15", playType = "P", amount = 3.0, lotteryName = "Quiniela Leidsa"),
                PlayItem(number = "51", playType = "Q", amount = 5.0, lotteryName = "Quiniela Leidsa"),
            ),
            total = 8.0,
            sellerUser = "nicola01",
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Yuniel",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
            printMark = TicketPrintMark.COPIA,
        )
        val playRow = output.lines().first { it.startsWith("[[PLAY_NUMBER|tall]]P 14/15") }
        val totalRow = output.lines().single { it.startsWith("[[TOTAL|tall]]TOTAL") }

        assertTrue(playRow.contains(ThermalLineStyling.POSITIONED_ROW_SEPARATOR))
        assertTrue(totalRow.contains(ThermalLineStyling.POSITIONED_ROW_SEPARATOR))
        assertFalse(playRow.contains("                 3"))
        assertFalse(totalRow.contains("                 8"))
        assertTrue(ThermalLineStyling.parse(playRow).text.startsWith("P 14/15"))
        assertTrue(ThermalLineStyling.parse(playRow).text.endsWith("3"))
        assertEquals(26, ThermalLineStyling.parse(playRow).text.length)
        assertTrue(ThermalLineStyling.parse(totalRow).text.startsWith("TOTAL"))
        assertTrue(ThermalLineStyling.parse(totalRow).text.endsWith("8"))
        assertEquals(26, ThermalLineStyling.parse(totalRow).text.length)
    }

    @Test
    fun `thermal ticket prints tripleta as separated pairs and positions monto header`() {
        val ticket = TicketRecord(
            id = "ticket-tripleta",
            serial = "NAT-TRI",
            plays = listOf(
                PlayItem(number = "788965", playType = "T", amount = 10.0, lotteryName = "Quiniela Leidsa"),
            ),
            total = 10.0,
            sellerUser = "nicola01",
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Yuniel",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )
        val headerRow = output.lines().first { ThermalLineStyling.parse(it).text.contains("JUGADA") }
        val tripletaRow = output.lines().first { it.startsWith("[[PLAY_NUMBER|tall]]T ") }

        assertTrue(headerRow.contains(ThermalLineStyling.POSITIONED_ROW_SEPARATOR))
        assertTrue(ThermalLineStyling.parse(headerRow).text.endsWith("MONTO"))
        assertTrue(ThermalLineStyling.parse(tripletaRow).text.startsWith("T 78/89/65"))
        assertFalse(output.contains("T 788965"))
    }

    @Test
    fun `thermal ticket with many lotteries still reaches total and qr`() {
        val ticket = TicketRecord(
            id = "ticket-many-lotteries",
            serial = "NAT-MULTI",
            plays = listOf(
                PlayItem(number = "28", playType = "Q", amount = 85.0, lotteryName = "Anguila Mañana"),
                PlayItem(number = "12", playType = "Q", amount = 85.0, lotteryName = "Anguila Tarde"),
                PlayItem(number = "98", playType = "Q", amount = 85.0, lotteryName = "Haiti Bolet 11:30 AM"),
                PlayItem(number = "88", playType = "Q", amount = 130.0, lotteryName = "Haiti Bolet 6:30 PM"),
                PlayItem(number = "25", playType = "Q", amount = 130.0, lotteryName = "New York Noche"),
            ),
            total = 515.0,
            sellerUser = "Caja1",
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Yuniel SRL",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )
        val lines = output.lines()
        val visibleLines = lines.map { ThermalLineStyling.parse(it).text }

        assertTrue(visibleLines.contains("ANGUILA AM"))
        assertTrue(visibleLines.contains("ANGUILA PM"))
        assertTrue(visibleLines.contains("HAITI BOLET 11:30 AM"))
        assertTrue(visibleLines.contains("HAITI BOLET 6:30 PM"))
        assertTrue(lines.any { it.startsWith("[[TITLE|tall]]BANCA YUNIEL SRL") })
        assertFalse(lines.any { it.startsWith("[[TITLE|large]]BANCA YUNIEL SRL") })
        assertTrue(lines.any { it.startsWith("[[TOTAL|tall]]TOTAL") && it.endsWith("515") })
        assertTrue(lines.indexOfFirst { it.startsWith("[[QR]]") } > lines.indexOfFirst { it.startsWith("[[TOTAL|tall]]TOTAL") })
        val qrIndex = lines.indexOfFirst { it.startsWith("[[QR]]") }
        assertTrue(lines.drop(qrIndex + 1).take(5).all { it.isBlank() })
        assertTrue(
            lines.filterNot { it.startsWith("[[QR]]") }
                .map { ThermalLineStyling.parse(it).text }
                .all { it.length <= 26 },
        )
    }

    @Test
    fun `thermal ticket with many plays uses normal play scale to avoid excessive paper height`() {
        val ticket = TicketRecord(
            id = "ticket-many-plays",
            serial = "NAT-LONG",
            plays = (1..30).map { index ->
                PlayItem(
                    number = index.toString().padStart(2, '0'),
                    playType = "Q",
                    amount = 10.0,
                    lotteryName = "Nacional",
                )
            },
            total = 300.0,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Central",
            prefs = ThermalPrinterPrefs(paperWidth = "58"),
        )

        assertTrue(output.lines().filter { it.contains("Q ") }.all { it.startsWith("[[PLAY_NUMBER]]") })
        assertTrue(output.lines().any { it.startsWith("[[TOTAL|tall]]TOTAL") && it.endsWith("300") })
        assertTrue(output.lines().any { it.startsWith("[[QR]]") })
    }

    @Test
    fun `thermal styling parses component scale markers`() {
        val styled = ThermalLineStyling.parse("[[PLAY_AMOUNT|large]]MONTO 25.00")

        assertEquals(ThermalLineStyle.PLAY_AMOUNT, styled.style)
        assertEquals("large", styled.scale)
        assertEquals("MONTO 25.00", styled.text)
    }

    @Test
    fun `thermal styling parses scaled center markers for printer output`() {
        val styled = ThermalLineStyling.parse("[[CENTER|normal]]Prueba de impresora")

        assertEquals(ThermalLineStyle.CENTER, styled.style)
        assertEquals("normal", styled.scale)
        assertEquals("Prueba de impresora", styled.text)
    }

    @Test
    fun `thermal ticket total uses tall total line to keep amount prominent on same row`() {
        val ticket = TicketRecord(
            id = "ticket-total-align",
            serial = "NAT-72602060",
            plays = listOf(
                PlayItem(number = "8963", playType = "SP", amount = 85.0, lotteryName = "Loteka", secondaryLotteryName = "King PM"),
                PlayItem(number = "7841", playType = "SP", amount = 85.0, lotteryName = "Loteka", secondaryLotteryName = "King PM"),
                PlayItem(number = "8966", playType = "SP", amount = 85.0, lotteryName = "Loteka", secondaryLotteryName = "King PM"),
            ),
            total = 255.0,
        )

        val output = ThermalTicketRenderer().renderTicket(
            ticket = ticket,
            bancaName = "Banca Yuniel",
            prefs = ThermalPrinterPrefs(paperWidth = "58", totalScale = "large"),
        )
        val totalLine = output.lines().single { it.contains("TOTAL") }
        val visible = ThermalLineStyling.parse(totalLine)

        assertEquals(ThermalLineStyle.TOTAL, visible.style)
        assertEquals("tall", visible.scale)
        assertTrue(visible.text.startsWith("TOTAL"))
        assertTrue(visible.text.endsWith("255"))
        assertTrue(visible.text.length <= 26)
    }
}
