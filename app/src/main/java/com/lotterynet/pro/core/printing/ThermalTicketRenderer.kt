package com.lotterynet.pro.core.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.lotterynet.pro.core.delivery.TicketDeliveryPolicy
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.export.TicketSecurity
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import com.lotterynet.pro.core.model.SportsbookTicketRecord
import com.lotterynet.pro.core.model.SportsbookTicketStatus
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.effectiveDrawDateKey
import com.lotterynet.pro.core.model.formatPlayDisplayNumber
import com.lotterynet.pro.core.model.isPaidTicketStatus
import java.util.Locale
import java.text.Normalizer

internal enum class ThermalLineStyle {
    TITLE,
    CENTER,
    LOTTERY,
    BOLD,
    PLAY_TYPE,
    PLAY_NUMBER,
    PLAY_AMOUNT,
    SECURITY,
    QR,
    TOTAL,
    FOOTER,
    NORMAL,
}

enum class TicketPrintMark {
    ORIGINAL,
    COPIA,
    NONE,
}

internal fun formatThermalLotteryName(raw: String): String {
    val normalized = Normalizer.normalize(raw.trim().ifBlank { "Loteria" }, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .uppercase(Locale.getDefault())
        .replace("LOTERIA ", "")
        .replace(" LOTTERY", "")
        .replace(" LOTERIA", "")
        .replace(Regex("\\s+"), " ")
        .trim()
    val period = when {
        Regex("\\b(AM|MANANA|DIA)\\b").containsMatchIn(normalized) -> "AM"
        Regex("\\b(PM|TARDE|NOCHE)\\b").containsMatchIn(normalized) -> "PM"
        "11:30" in normalized -> "AM"
        "6:30" in normalized || "18:30" in normalized -> "PM"
        else -> null
    }
    val base = normalized
        .replace(Regex("\\b(MANANA|DIA|TARDE|NOCHE)\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (base.contains("HAITI BOLET")) {
        val time = Regex("(\\d{1,2}:\\d{2})").find(base)?.value
        return listOfNotNull("HAITI BOLET", time, period).joinToString(" ").trim()
    }
    return listOfNotNull(base, period).joinToString(" ").trim().take(30)
}

internal data class ThermalStyledLine(
    val text: String,
    val style: ThermalLineStyle,
    val scale: String = "normal",
    val payload: String = text,
)

internal object ThermalLineStyling {
    const val POSITIONED_ROW_SEPARATOR = "\u001F"

    private const val TITLE = "[[TITLE]]"
    private const val CENTER = "[[CENTER]]"
    private const val LOTTERY = "[[LOTTERY]]"
    private const val BOLD = "[[BOLD]]"
    private const val PLAY_TYPE = "[[PLAY_TYPE]]"
    private const val PLAY_NUMBER = "[[PLAY_NUMBER]]"
    private const val PLAY_AMOUNT = "[[PLAY_AMOUNT]]"
    private const val SECURITY = "[[SECURITY]]"
    private const val QR = "[[QR]]"
    private const val TOTAL = "[[TOTAL]]"
    private const val FOOTER = "[[FOOTER]]"

    fun title(text: String, scale: String = "large"): String = marker(TITLE, scale, text)
    fun center(text: String): String = "$CENTER$text"
    fun lottery(text: String, scale: String = "normal"): String = marker(LOTTERY, scale, text)
    fun bold(text: String, scale: String = "normal"): String = marker(BOLD, scale, text)
    fun playType(text: String, scale: String = "normal"): String = marker(PLAY_TYPE, scale, text)
    fun playNumber(text: String, scale: String = "normal"): String = marker(PLAY_NUMBER, scale, text)
    fun playMoneyRow(label: String, amount: String, width: Int, scale: String = "normal"): String {
        return positionedMoneyMarker(PLAY_NUMBER, scale, label, amount, width)
    }
    fun playAmount(text: String, scale: String = "normal"): String = marker(PLAY_AMOUNT, scale, text)
    fun security(text: String, scale: String = "normal"): String = marker(SECURITY, scale, text)
    fun qr(text: String): String = "$QR$text"
    fun total(text: String, scale: String = "large"): String = marker(TOTAL, scale, text)
    fun totalMoneyRow(label: String, amount: String, width: Int, scale: String = "large"): String {
        return positionedMoneyMarker(TOTAL, scale, label, amount, width)
    }
    fun footer(text: String): String = "$FOOTER$text"

    fun parse(raw: String): ThermalStyledLine {
        parseScaled(raw, TITLE, ThermalLineStyle.TITLE)?.let { return it }
        parseScaled(raw, CENTER, ThermalLineStyle.CENTER)?.let { return it }
        parseScaled(raw, LOTTERY, ThermalLineStyle.LOTTERY)?.let { return it }
        parseScaled(raw, BOLD, ThermalLineStyle.BOLD)?.let { return it }
        parseScaled(raw, PLAY_TYPE, ThermalLineStyle.PLAY_TYPE)?.let { return it }
        parseScaled(raw, PLAY_NUMBER, ThermalLineStyle.PLAY_NUMBER)?.let { return it }
        parseScaled(raw, PLAY_AMOUNT, ThermalLineStyle.PLAY_AMOUNT)?.let { return it }
        parseScaled(raw, SECURITY, ThermalLineStyle.SECURITY)?.let { return it }
        parseScaled(raw, TOTAL, ThermalLineStyle.TOTAL)?.let { return it }
        parseScaled(raw, FOOTER, ThermalLineStyle.FOOTER)?.let { return it }
        return when {
            raw.startsWith(TITLE) -> styledLine(raw.removePrefix(TITLE), ThermalLineStyle.TITLE, "large")
            raw.startsWith(CENTER) -> styledLine(raw.removePrefix(CENTER), ThermalLineStyle.CENTER)
            raw.startsWith(LOTTERY) -> styledLine(raw.removePrefix(LOTTERY), ThermalLineStyle.LOTTERY)
            raw.startsWith(BOLD) -> styledLine(raw.removePrefix(BOLD), ThermalLineStyle.BOLD)
            raw.startsWith(PLAY_TYPE) -> styledLine(raw.removePrefix(PLAY_TYPE), ThermalLineStyle.PLAY_TYPE)
            raw.startsWith(PLAY_NUMBER) -> styledLine(raw.removePrefix(PLAY_NUMBER), ThermalLineStyle.PLAY_NUMBER)
            raw.startsWith(PLAY_AMOUNT) -> styledLine(raw.removePrefix(PLAY_AMOUNT), ThermalLineStyle.PLAY_AMOUNT)
            raw.startsWith(SECURITY) -> styledLine(raw.removePrefix(SECURITY), ThermalLineStyle.SECURITY)
            raw.startsWith(QR) -> ThermalStyledLine("QR", ThermalLineStyle.QR, payload = raw.removePrefix(QR))
            raw.startsWith(TOTAL) -> styledLine(raw.removePrefix(TOTAL), ThermalLineStyle.TOTAL, "large")
            raw.startsWith(FOOTER) -> styledLine(raw.removePrefix(FOOTER), ThermalLineStyle.FOOTER)
            else -> ThermalStyledLine(raw, ThermalLineStyle.NORMAL)
        }
    }

    data class PositionedMoneyRow(
        val label: String,
        val amount: String,
        val width: Int,
    )

    fun positionedMoneyRow(styled: ThermalStyledLine): PositionedMoneyRow? {
        if (styled.style != ThermalLineStyle.PLAY_NUMBER && styled.style != ThermalLineStyle.TOTAL) return null
        return parsePositionedMoneyPayload(styled.payload)
    }

    private fun marker(prefix: String, scale: String, text: String): String {
        val safeScale = when (scale) {
            "compact", "normal", "tall", "large" -> scale
            else -> "normal"
        }
        return if (safeScale == "normal") "$prefix$text" else "${prefix.removeSuffix("]]")}|$safeScale]]$text"
    }

    private fun positionedMoneyMarker(
        prefix: String,
        scale: String,
        label: String,
        amount: String,
        width: Int,
    ): String {
        val safeWidth = width.coerceIn(12, 80)
        val payload = listOf(label, safeWidth.toString(), amount)
            .joinToString(POSITIONED_ROW_SEPARATOR)
        return marker(prefix, scale, payload)
    }

    private fun parseScaled(
        raw: String,
        prefix: String,
        style: ThermalLineStyle,
    ): ThermalStyledLine? {
        val scaledPrefix = prefix.removeSuffix("]]") + "|"
        if (!raw.startsWith(scaledPrefix)) return null
        val markerEnd = raw.indexOf("]]")
        if (markerEnd < 0) return null
        val scale = raw.substring(scaledPrefix.length, markerEnd).let {
            when (it) {
                "compact", "normal", "tall", "large" -> it
                else -> "normal"
            }
        }
        return styledLine(raw.substring(markerEnd + 2), style, scale)
    }

    private fun styledLine(
        payload: String,
        style: ThermalLineStyle,
        scale: String = "normal",
    ): ThermalStyledLine {
        val visibleText = parsePositionedMoneyPayload(payload)?.let(::alignPositionedMoneyRow) ?: payload
        return ThermalStyledLine(visibleText, style, scale, payload)
    }

    private fun parsePositionedMoneyPayload(payload: String): PositionedMoneyRow? {
        val parts = payload.split(POSITIONED_ROW_SEPARATOR)
        if (parts.size != 3) return null
        val width = parts[1].toIntOrNull()?.coerceIn(12, 80) ?: return null
        if (parts[0].isBlank() || parts[2].isBlank()) return null
        return PositionedMoneyRow(label = parts[0], width = width, amount = parts[2])
    }

    private fun alignPositionedMoneyRow(row: PositionedMoneyRow): String {
        val amount = row.amount.take(row.width)
        val labelLimit = (row.width - amount.length - 1).coerceAtLeast(1)
        val label = row.label.take(labelLimit)
        val gap = (row.width - label.length - amount.length).coerceAtLeast(1)
        return label + " ".repeat(gap) + amount
    }
}

class ThermalTicketRenderer {
    fun renderCompactShareBitmap(
        ticket: TicketRecord,
        bancaName: String,
        drawTimesByLottery: Map<String, String> = emptyMap(),
    ): Bitmap {
        val shareWidth = resolveCompactShareLineWidth(ticket)
        val prefs = ThermalPrinterPrefs(
            paperWidth = "80",
            widthMode = if (shareWidth > 42) "custom" else "wide",
            customChars = shareWidth.toString(),
            headerScale = "compact",
            serialScale = "compact",
            lotteryScale = "compact",
            playTypeScale = "compact",
            playNumberScale = "compact",
            amountScale = "compact",
            securityScale = "compact",
            totalScale = "compact",
            previewZoom = "90",
            showOriginal = true,
            showSecurity = true,
            showFooter = false,
        )
        return renderTextBitmap(
            renderCompactShareText(ticket, bancaName, drawTimesByLottery),
            prefs,
            drawTopRule = false,
            inkColor = Color.BLACK,
        )
    }

    internal fun renderCompactShareText(
        ticket: TicketRecord,
        bancaName: String,
        drawTimesByLottery: Map<String, String> = emptyMap(),
    ): String {
        val width = resolveCompactShareLineWidth(ticket)
        val useAbbreviatedLotteryNames = width > 42
        val lines = mutableListOf<String>()
        val serial = ticket.serial?.takeIf { it.isNotBlank() } ?: ticket.id
        val sellerLabel = ticket.sellerUser?.takeIf { it.isNotBlank() }
            ?: ticket.adminUser?.takeIf { it.isNotBlank() }
        val groupedPlays = ticket.plays.groupBy { play ->
            val primary = formatThermalLotteryName(play.lotteryName.orEmpty().ifBlank { "Loteria" })
            val secondary = play.secondaryLotteryName.orEmpty().trim()
            val primaryLabel = compactShareLotteryName(primary, useAbbreviatedLotteryNames)
            val secondaryLabel = secondary
                .takeIf { it.isNotBlank() }
                ?.let { compactShareLotteryName(formatThermalLotteryName(it), useAbbreviatedLotteryNames) }
            if (secondaryLabel != null) "$primaryLabel / $secondaryLabel" else primaryLabel
        }
        val groupSubtotals = groupedPlays.mapValues { (_, plays) -> plays.sumOf { it.amount } }
        val showLotterySubtotals = groupSubtotals.size > 1

        lines += center(bancaName.uppercase(Locale.getDefault()).take(width), width)
        lines += center("ORIGINAL - ACTIVO", width)
        lines += divider(width, "minimal")
        lines += ThermalLineStyling.bold(serial.take(width), "compact")
        lines += "${NativeBitmapExport.formatDateForTicket(ticket.createdAtEpochMs)}  ${NativeBitmapExport.formatTimeForTicket(ticket.createdAtEpochMs)}"
        sellerLabel?.let { lines += "VENDEDOR: ${it.take(width - 10)}" }
        val drawLabel = NativeBitmapExport
            .formatDrawDateForTicket(ticket.effectiveDrawDateKey(), resolveThermalTicketDrawTimeLabel(ticket, drawTimesByLottery))
            .uppercase(Locale.getDefault())
        thermalWrapWords("SORTEO $drawLabel", width).forEach { lines += ThermalLineStyling.bold(it, "compact") }
        lines += divider(width, "minimal")

        groupedPlays.forEach { (lotteryName, plays) ->
            lines += ThermalLineStyling.lottery(lotteryName.take(width), "compact")
            lines += "${plays.size} JUGADAS"
            lines += compactThreeColumnHeader(width)
            val cellWidth = (width - 2) / 3
            plays.chunked(3).forEach { row ->
                lines += row.map { play -> compactPlayCell(play, cellWidth) }
                    .joinToString(" ")
                    .padEnd(width)
            }
            if (showLotterySubtotals) {
                val subtotalText = "SUBTOTAL ${lotteryName.take(12)}: ${formatThermalMoney(groupSubtotals.getValue(lotteryName))}"
                lines += ThermalLineStyling.bold(center(subtotalText, width), "compact")
                lines += divider(width, "minimal")
            }
        }

        if (showLotterySubtotals) {
            lines += ThermalLineStyling.bold("RESUMEN", "compact")
            groupSubtotals.forEach { (lotteryName, subtotal) ->
                lines += ThermalLineStyling.playMoneyRow(
                    lotteryName.take((width * 0.62f).toInt()),
                    formatThermalMoney(subtotal),
                    width,
                    "compact",
                )
            }
            lines += divider(width, "minimal")
        } else {
            lines += divider(width, "minimal")
        }

        val totalToPlay = ticket.total.takeIf { it > 0.0 } ?: ticket.plays.sumOf { it.amount }
        lines += ThermalLineStyling.bold(alignMoney("JUGADAS", ticket.plays.size.toDouble(), width), "compact")
        lines += ThermalLineStyling.bold(alignMoney("TOTAL A JUGAR", totalToPlay, width), "compact")
        lines += divider(width, "minimal")

        val securityCode = TicketSecurity.resolveSecurityCode(ticket, bancaName)
        if (securityCode.isNotBlank()) {
            lines += ThermalLineStyling.bold(center("VERIFICACION: $securityCode", width), "compact")
        }

        lines += ThermalLineStyling.qr(buildTicketQrPayload(ticket, securityCode))
        lines += ""
        lines += ""
        lines += ""
        lines += ""
        lines += ""
        lines += ""
        return lines.joinToString("\n")
            .normalizeThermalText()
    }

    fun renderTicketChunks(
        ticket: TicketRecord,
        bancaName: String,
        prefs: ThermalPrinterPrefs,
        drawTimesByLottery: Map<String, String> = emptyMap(),
        printMark: TicketPrintMark = if (prefs.showOriginal) TicketPrintMark.ORIGINAL else TicketPrintMark.NONE,
    ): List<String> {
        val estimatedLines = renderTicket(
            ticket = ticket,
            bancaName = bancaName,
            prefs = prefs,
            drawTimesByLottery = drawTimesByLottery,
            printMark = printMark,
        ).lineSequence().count()
        if (ticket.plays.size <= TicketDeliveryPolicy.MAX_SINGLE_IMAGE_PLAYS && estimatedLines <= 160) {
            return listOf(
                renderTicket(
                    ticket = ticket,
                    bancaName = bancaName,
                    prefs = prefs,
                    drawTimesByLottery = drawTimesByLottery,
                    printMark = printMark,
                ),
            )
        }
        val pages = TicketDeliveryPolicy.buildPages(ticket)
        if (pages.isEmpty()) {
            return listOf(
                renderTicket(
                    ticket = ticket,
                    bancaName = bancaName,
                    prefs = prefs,
                    drawTimesByLottery = drawTimesByLottery,
                    printMark = printMark,
                ),
            )
        }
        return pages.map { page ->
            val pageTotal = page.plays.sumOf { it.amount }
            val pageTicket = ticket.copy(
                serial = listOfNotNull(ticket.serial, "P${page.index + 1}-${page.totalPages}")
                    .joinToString("-"),
                plays = page.plays,
                subtotal = pageTotal,
                total = pageTotal,
                winningDetails = if (page.index == pages.lastIndex) ticket.winningDetails else emptyList(),
                totalPrize = if (page.index == pages.lastIndex) ticket.totalPrize else 0.0,
            )
            renderTicket(
                ticket = pageTicket,
                bancaName = bancaName,
                prefs = prefs,
                drawTimesByLottery = drawTimesByLottery,
                printMark = printMark,
            )
        }
    }

    fun renderTicket(
        ticket: TicketRecord,
        bancaName: String,
        prefs: ThermalPrinterPrefs,
        drawTimesByLottery: Map<String, String> = emptyMap(),
        printMark: TicketPrintMark = if (prefs.showOriginal) TicketPrintMark.ORIGINAL else TicketPrintMark.NONE,
    ): String {
        val width = resolveLineWidth(prefs)
        val lines = mutableListOf<String>()
        val securityCode = if (prefs.showSecurity) TicketSecurity.resolveSecurityCode(ticket, bancaName) else ""
        val totalLabel = when (printMark) {
            TicketPrintMark.ORIGINAL -> "ORIGINAL"
            TicketPrintMark.COPIA -> "COPIA"
            TicketPrintMark.NONE -> null
        }
        val sellerLabel = ticket.sellerUser?.takeIf { it.isNotBlank() }
            ?: ticket.adminUser?.takeIf { it.isNotBlank() }
        val statusLabel = if (isPaidTicketStatus(ticket.status)) {
            "PAGADO"
        } else {
            when (ticket.status.lowercase(Locale.getDefault())) {
                "winner" -> "GANADOR"
                "voided", "invalid" -> "ANULADO"
                else -> "ACTIVO"
            }
        }

        lines += ThermalLineStyling.title(bancaName.uppercase(Locale.getDefault()), narrowPresenceScale(prefs.headerScale))
        lines += ThermalLineStyling.center(listOfNotNull(totalLabel, statusLabel).joinToString(" · "))
        lines += divider(width, prefs.separator)
        lines += ThermalLineStyling.bold(ticket.serial ?: ticket.id, prefs.serialScale)
        if (prefs.showDateTime) {
            lines += "${NativeBitmapExport.formatDateForTicket(ticket.createdAtEpochMs)}  ${NativeBitmapExport.formatTimeForTicket(ticket.createdAtEpochMs)}"
        }
        val validDrawLabel = NativeBitmapExport
            .formatDrawDateForTicket(ticket.effectiveDrawDateKey(), resolveThermalTicketDrawTimeLabel(ticket, drawTimesByLottery))
            .uppercase(Locale.getDefault())
        lines += ThermalLineStyling.bold("TICKET VALIDO", "compact")
        lines += ThermalLineStyling.bold("PARA EL SORTEO", "compact")
        thermalWrapWords(validDrawLabel, width).forEach { line ->
            lines += ThermalLineStyling.bold(line, "compact")
        }
        sellerLabel?.let { lines += padRight("VENDEDOR:", 10) + it.take(width - 10) }
        lines += divider(width, prefs.separator)

        ticket.plays.groupBy { play ->
            val primary = formatThermalLotteryName(play.lotteryName.orEmpty().ifBlank { "Loteria" })
            val secondary = play.secondaryLotteryName.orEmpty().trim()
            if (secondary.isNotBlank()) "$primary / ${formatThermalLotteryName(secondary)}" else primary
        }.forEach { (lotteryName, plays) ->
            lines += ThermalLineStyling.lottery(lotteryName, strongScale(prefs.lotteryScale))
            if (prefs.showDrawTime) {
                drawTimesByLottery[lotteryName.substringBefore(" / ")]?.takeIf { it.isNotBlank() }?.let {
                    lines += "Juega $it"
                }
            }
            lines += tableHeader(width)
            plays.forEach { play ->
                lines += renderPlayLines(
                    typeText = playTypeText(play.playType, prefs.typeLabelMode),
                    number = formatThermalPlayNumber(play.number, play.playType),
                    amount = play.amount,
                    width = width,
                    prefs = prefs,
                    compact = plays.size >= 20,
                )
            }
            lines += ThermalLineStyling.playMoneyRow("MONTO LOTERIA", formatThermalMoney(plays.sumOf { it.amount }), width)
            lines += divider(width, "minimal")
        }

        val winnerDetails = ticket.winningDetails
            .filter { detail ->
                detail.lotteryName.isNotBlank() ||
                detail.playedNumber.isNotBlank() ||
                    detail.resultNumber.isNotBlank() ||
                    detail.payoutAmount > 0.0
            }
        if (winnerDetails.isNotEmpty()) {
            lines += ThermalLineStyling.bold("PREMIOS DEL TICKET", "compact")
            winnerDetails
                .groupBy { detail ->
                    listOf(
                        detail.lotteryName.ifBlank { "Loteria" },
                        detail.resultNumber.ifBlank { "-" },
                    ).joinToString("|")
                }
                .forEach { (_, details) ->
                    val first = details.first()
                    val groupPrize = details.sumOf { it.payoutAmount }
                    thermalWrapWords(formatThermalLotteryName(first.lotteryName), width).forEach { line ->
                        lines += ThermalLineStyling.lottery(line, "compact")
                    }
                    val resultLine = "RESULTADO ${first.resultNumber.ifBlank { "-" }}"
                    lines += if (details.size > 1) {
                        ThermalLineStyling.playMoneyRow(resultLine, formatThermalMoney(groupPrize), width)
                    } else {
                        resultLine
                    }
                    details.forEach { detail ->
                        lines += "ACIERTO ${winningHitLabel(detail.hitPosition).uppercase(Locale.US)}"
                        lines += renderPlayLines(
                            typeText = playTypeText(detail.playType, prefs.typeLabelMode),
                            number = formatThermalPlayNumber(detail.playedNumber, detail.playType),
                            amount = detail.amount,
                            width = width,
                            prefs = prefs,
                            compact = true,
                        )
                        lines += ThermalLineStyling.playMoneyRow("PREMIO", formatThermalMoney(detail.payoutAmount), width)
                    }
                }
            if (ticket.winningDetails.size > winnerDetails.size) {
                lines += "...${winnerDetails.size} de ${ticket.winningDetails.size} premios"
            }
            lines += divider(width, "minimal")
        }

        if (winnerDetails.isNotEmpty()) {
            lines += ThermalLineStyling.totalMoneyRow(
                label = "PREMIO",
                amount = formatThermalMoney(ticket.totalPrize.takeIf { it > 0.0 } ?: winnerDetails.sumOf { it.payoutAmount }),
                width = width,
                scale = stableTotalScale(prefs.totalScale),
            )
        }
        lines += ThermalLineStyling.totalMoneyRow(
            label = if (winnerDetails.isNotEmpty()) "JUGADO" else "TOTAL",
            amount = formatThermalMoney(ticket.total),
            width = width,
            scale = stableTotalScale(prefs.totalScale),
        )
        if (securityCode.isNotBlank()) {
            lines += ThermalLineStyling.security("CODIGO: $securityCode", prefs.securityScale)
        }
        lines += ThermalLineStyling.footer("Gracias por su jugada")
        lines += ThermalLineStyling.qr(buildTicketQrPayload(ticket, securityCode))
        lines += ""
        lines += ""
        lines += ""
        lines += ""
        lines += ""
        return lines.joinToString("\n")
            .normalizeThermalText()
    }

    fun renderPayoutReceipt(
        ticket: TicketRecord,
        bancaName: String,
        prefs: ThermalPrinterPrefs,
    ): String {
        val width = resolveLineWidth(prefs)
        val serial = ticket.serial?.takeIf { it.isNotBlank() } ?: ticket.id
        val prize = ticket.totalPrize.takeIf { it > 0.0 }
        val sellerLabel = ticket.sellerUser?.takeIf { it.isNotBlank() }
            ?: ticket.adminUser?.takeIf { it.isNotBlank() }
        val lines = mutableListOf<String>()

        lines += ThermalLineStyling.title(bancaName.uppercase(Locale.getDefault()), narrowPresenceScale(prefs.headerScale))
        lines += ThermalLineStyling.center("COMPROBANTE DE PAGO")
        lines += divider(width, prefs.separator)
        lines += ThermalLineStyling.bold("TICKET: $serial", prefs.serialScale)
        lines += ThermalLineStyling.bold(
            prize?.let { alignMoney("PAGO:", it, width) }
                ?: "${padRight("PAGO:", 8)}PENDIENTE",
        )
        sellerLabel?.let { lines += padRight("CAJERO:", 8) + it.take(width - 8) }
        lines += "${NativeBitmapExport.formatDateForTicket(System.currentTimeMillis())}  ${NativeBitmapExport.formatTimeForTicket(System.currentTimeMillis())}"
        lines += divider(width, "minimal")
        lines += ThermalLineStyling.footer("Premio pagado")
        lines += ""
        lines += ""
        lines += ""
        return lines.joinToString("\n")
            .normalizeThermalText()
    }

    fun renderSportsbookTicket(
        ticket: SportsbookTicketRecord,
        bancaName: String,
        prefs: ThermalPrinterPrefs,
        printMark: TicketPrintMark = if (prefs.showOriginal) TicketPrintMark.ORIGINAL else TicketPrintMark.NONE,
    ): String {
        val width = resolveLineWidth(prefs)
        val lines = mutableListOf<String>()
        val statusLabel = when (ticket.status) {
            SportsbookTicketStatus.PAID -> "PAGADO"
            SportsbookTicketStatus.WON -> "GANADOR"
            SportsbookTicketStatus.LOST -> "PERDIDO"
            SportsbookTicketStatus.VOID -> "ANULADO"
            else -> "ACTIVO"
        }
        val markLabel = when (printMark) {
            TicketPrintMark.ORIGINAL -> "ORIGINAL"
            TicketPrintMark.COPIA -> "COPIA"
            TicketPrintMark.NONE -> null
        }

        lines += ThermalLineStyling.title(bancaName.uppercase(Locale.getDefault()), narrowPresenceScale(prefs.headerScale))
        lines += ThermalLineStyling.center(listOfNotNull("DEPORTE", markLabel, statusLabel).joinToString(" · "))
        lines += divider(width, prefs.separator)
        lines += ThermalLineStyling.bold(ticket.ticketCode, prefs.serialScale)
        if (prefs.showDateTime && ticket.soldAtEpochMs > 0L) {
            lines += "${NativeBitmapExport.formatDateForTicket(ticket.soldAtEpochMs)}  ${NativeBitmapExport.formatTimeForTicket(ticket.soldAtEpochMs)}"
        }
        ticket.sellerUsername.takeIf { it.isNotBlank() }?.let {
            lines += padRight("VENDEDOR:", 10) + it.take(width - 10)
        }
        lines += divider(width, prefs.separator)
        lines += ThermalLineStyling.bold("SELECCIONES", "compact")
        val legs = ticket.legs.ifEmpty {
            listOf(
                com.lotterynet.pro.core.model.SportsbookTicketLegRecord(
                    eventLabel = "Ticket deportivo",
                    marketTitle = ticket.ticketType,
                    selectionLabel = "Sin detalle",
                    decimalOdds = ticket.decimalOdds,
                    status = ticket.status,
                ),
            )
        }
        legs.forEachIndexed { index, leg ->
            if (index > 0) lines += divider(width, "minimal")
            thermalWrapWords(leg.eventLabel.ifBlank { "Evento" }.uppercase(Locale.US), width).forEach { line ->
                lines += ThermalLineStyling.lottery(line, "compact")
            }
            thermalWrapWords("${leg.marketTitle}: ${leg.selectionLabel}", width).forEach { line ->
                lines += ThermalLineStyling.bold(line, "compact")
            }
            lines += ThermalLineStyling.playMoneyRow("CUOTA", "%.2f".format(Locale.US, leg.decimalOdds), width, "compact")
        }
        lines += divider(width, prefs.separator)
        lines += ThermalLineStyling.playMoneyRow("TIPO", ticket.ticketType.uppercase(Locale.US), width)
        lines += ThermalLineStyling.playMoneyRow("CUOTA TOTAL", "%.2f".format(Locale.US, ticket.decimalOdds), width)
        lines += ThermalLineStyling.totalMoneyRow("APOSTADO", formatThermalMoney(ticket.stake), width, stableTotalScale(prefs.totalScale))
        lines += ThermalLineStyling.totalMoneyRow("PAGO POSIBLE", formatThermalMoney(ticket.potentialPayout), width, stableTotalScale(prefs.totalScale))
        lines += ThermalLineStyling.footer("Validar antes de pagar")
        lines += ""
        lines += ""
        lines += ""
        return lines.joinToString("\n").normalizeThermalText()
    }

    private fun buildTicketQrPayload(ticket: TicketRecord, securityCode: String): String {
        val serial = ticket.serial?.takeIf { it.isNotBlank() } ?: ticket.id
        val code = securityCode.ifBlank { ticket.securityCode.orEmpty().ifBlank { ticket.id } }
        return listOf(
            "LN",
            serial,
            code,
            formatThermalMoney(ticket.total),
            ticket.createdAtEpochMs.toString(),
        ).joinToString("|")
    }

    private fun winningHitLabel(raw: String): String {
        return when (raw.trim().lowercase(Locale.US)) {
            "1" -> "primera"
            "2" -> "segunda"
            "3" -> "tercera"
            "1-2" -> "pale 1-2"
            "1-3" -> "pale 1-3"
            "2-3" -> "pale 2-3"
            "sp" -> "super pale"
            "back" -> "ultimo par"
            "" -> "ganadora"
            else -> raw
        }
    }

    private fun resolveThermalTicketDrawTimeLabel(
        ticket: TicketRecord,
        drawTimesByLottery: Map<String, String>,
    ): String? {
        val times = ticket.plays.mapNotNull { play ->
            drawTimesByLottery[play.lotteryName.orEmpty()]?.takeIf { it.isNotBlank() }
        }.distinct()
        return when (times.size) {
            0 -> null
            1 -> times.first()
            else -> "varios sorteos"
        }
    }

    fun renderPreview(bancaName: String, prefs: ThermalPrinterPrefs): String {
        val ticket = TicketRecord(
            id = "native-preview",
            serial = "NAT-12345678",
            securityCode = "SEC908",
            plays = listOf(
                com.lotterynet.pro.core.model.PlayItem(number = "12", playType = "Q", amount = 25.0, lotteryName = "Loto Real"),
                com.lotterynet.pro.core.model.PlayItem(number = "34-56", playType = "P", amount = 50.0, lotteryName = "Loto Real"),
                com.lotterynet.pro.core.model.PlayItem(number = "789", playType = "P3", amount = 35.0, lotteryName = "New York Tarde"),
            ),
            total = 110.0,
        )
        return renderTicket(
            ticket = ticket,
            bancaName = bancaName,
            prefs = prefs,
            drawTimesByLottery = mapOf("Loto Real" to "08:30 PM", "New York Tarde" to "02:30 PM"),
        )
    }

    fun renderTicketBitmap(
        ticket: TicketRecord,
        bancaName: String,
        prefs: ThermalPrinterPrefs,
        drawTimesByLottery: Map<String, String> = emptyMap(),
    ): Bitmap {
        return renderTextBitmap(renderTicket(ticket, bancaName, prefs, drawTimesByLottery), prefs)
    }

    fun renderPreviewBitmap(
        bancaName: String,
        prefs: ThermalPrinterPrefs,
    ): Bitmap {
        return renderTextBitmap(renderPreview(bancaName, prefs), prefs)
    }

    fun renderTextBitmapForExternal(
        content: String,
        prefs: ThermalPrinterPrefs,
    ): Bitmap {
        return renderTextBitmap(content, prefs)
    }

    fun renderFinanceSummary(
        bancaName: String,
        dayKey: String,
        summary: FinanceSummary,
        prefs: ThermalPrinterPrefs,
        actorLabel: String? = null,
        turnoLabel: String? = null,
        turnoDiff: Double? = null,
        cashEntered: Double? = null,
    ): String {
        val width = resolveLineWidth(prefs)
        val lines = mutableListOf<String>()
        lines += center(bancaName.uppercase(Locale.getDefault()), width)
        lines += center("CUADRE", width)
        lines += divider(width, prefs.separator)
        lines += padRight("FECHA:", 10) + dayKey
        actorLabel?.takeIf { it.isNotBlank() }?.let {
            lines += padRight("ACTOR:", 10) + it
        }
        turnoLabel?.takeIf { it.isNotBlank() }?.let {
            lines += padRight("TURNO:", 10) + it
        }
        lines += divider(width, prefs.separator)
        lines += alignMoney("VENTAS", summary.ventas, width)
        lines += alignMoney("RECARGAS", summary.recargas, width)
        lines += alignMoney("COMISION", summary.comision, width)
        lines += alignMoney("PREMIOS PAG", summary.premiosPagados, width)
        lines += alignMoney("PREM PEND", summary.premiosPendientes, width)
        lines += alignMoney("CAJA DISP", summary.cajaDisponible, width)
        lines += alignMoney("NETO PROY", summary.netoProyectado, width)
        lines += divider(width, "minimal")
        lines += "TICKETS ${summary.ticketsCount}  ACT ${summary.activos}  PAG ${summary.pagados}"
        lines += "GAN ${summary.ganadores}  ANU ${summary.anuladosCount}  INV ${summary.invalidosCount}"
        if (summary.fueraDeFinanzaCount > 0 || summary.fueraDeFinanzaMonto > 0.0) {
            lines += "FUERA FIN ${summary.fueraDeFinanzaCount}"
            lines += alignMoney("FUERA MONTO", summary.fueraDeFinanzaMonto, width)
        }
        cashEntered?.let { lines += alignMoney("EFECTIVO", it, width) }
        turnoDiff?.let { lines += alignMoney("DIF TURNO", it, width) }
        if (summary.alertas.isNotEmpty()) {
            lines += divider(width, prefs.separator)
            lines += "ALERTAS"
            summary.alertas.take(4).forEach { alert ->
                lines += "- ${alert.label}: ${alert.text}".take(width)
            }
        }
        if (prefs.showFooter) {
            lines += divider(width, prefs.separator)
            lines += center("LOTTERYNET PRO", width)
        }
        return lines.joinToString("\n").trimEnd()
    }

    fun renderFinanceBitmap(
        bancaName: String,
        dayKey: String,
        summary: FinanceSummary,
        prefs: ThermalPrinterPrefs,
        actorLabel: String? = null,
        turnoLabel: String? = null,
        turnoDiff: Double? = null,
        cashEntered: Double? = null,
    ): Bitmap {
        return renderTextBitmap(
            renderFinanceSummary(
                bancaName = bancaName,
                dayKey = dayKey,
                summary = summary,
                prefs = prefs,
                actorLabel = actorLabel,
                turnoLabel = turnoLabel,
                turnoDiff = turnoDiff,
                cashEntered = cashEntered,
            ),
            prefs,
        )
    }

    private fun resolveLineWidth(prefs: ThermalPrinterPrefs): Int {
        return when (prefs.widthMode) {
            "narrow" -> 24
            "wide" -> 42
            "custom" -> prefs.customChars.toIntOrNull()?.coerceIn(24, 60) ?: 32
            else -> if (prefs.paperWidth == "80") 42 else 26
        }
    }

    private fun divider(width: Int, separator: String): String {
        val char = when (separator) {
            "minimal" -> '.'
            else -> '-'
        }
        val size = if (separator == "short") (width * 0.7f).toInt() else width
        return char.toString().repeat(size.coerceAtLeast(8))
    }

    private fun thermalWrapWords(text: String, width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(12)
        val lines = mutableListOf<String>()
        var current = ""
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            val next = if (current.isBlank()) word else "$current $word"
            if (next.length <= safeWidth) {
                current = next
            } else {
                if (current.isNotBlank()) lines += current
                current = word
            }
        }
        if (current.isNotBlank()) lines += current
        return lines
    }

    private fun center(text: String, width: Int): String {
        if (text.length >= width) return text
        val left = (width - text.length) / 2
        return " ".repeat(left) + text
    }

    private fun padRight(text: String, width: Int): String {
        return text.padEnd(width, ' ')
    }

    private fun alignMoney(label: String, amount: Double, width: Int): String {
        val amountText = formatThermalMoney(amount)
        val gap = (width - label.length - amountText.length).coerceAtLeast(1)
        return label + " ".repeat(gap) + amountText
    }

    private fun formatThermalMoney(amount: Double): String {
        return com.lotterynet.pro.core.format.formatWholeAmount(amount)
    }

    private fun renderPlayLines(
        typeText: String,
        number: String,
        amount: Double,
        width: Int,
        prefs: ThermalPrinterPrefs,
        compact: Boolean = false,
    ): List<String> {
        val playLabel = listOf(typeText.trim(), number.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val amountText = formatThermalMoney(amount)
        val playScale = if (compact) "normal" else stablePlayNumberScale(prefs.playNumberScale)
        val singleLinePlayWidth = (width - amountText.length - 1).coerceAtLeast(1)

        if (playLabel.length <= singleLinePlayWidth) {
            return listOf(
                ThermalLineStyling.playMoneyRow(
                    label = playLabel,
                    amount = amountText,
                    width = width,
                    scale = playScale,
                ),
            )
        }

        return wrapPlayLabelForThermal(typeText.trim(), number.trim(), width)
            .map { ThermalLineStyling.playNumber(it, playScale) } +
            ThermalLineStyling.playAmount(alignMoney("MONTO", amount, width))
    }

    private fun wrapPlayLabelForThermal(
        typeText: String,
        number: String,
        width: Int,
    ): List<String> {
        if (number.isBlank()) return listOf(typeText.take(width))
        val prefix = typeText.take(width).let { if (it.isBlank()) "" else "$it " }
        val firstChunkSize = (width - prefix.length).coerceAtLeast(1)
        val chunks = mutableListOf<String>()
        var remaining = number
        chunks += prefix + remaining.take(firstChunkSize)
        remaining = remaining.drop(firstChunkSize)
        val nextChunkSize = width.coerceAtLeast(1)
        while (remaining.isNotBlank()) {
            chunks += remaining.take(nextChunkSize)
            remaining = remaining.drop(nextChunkSize)
        }
        return chunks
    }

    private fun strongScale(scale: String): String {
        return if (scale == "compact" || scale == "normal") "large" else scale
    }

    private fun narrowPresenceScale(scale: String): String {
        return if (scale == "compact" || scale == "normal" || scale == "large") "tall" else scale
    }

    private fun stableTotalScale(scale: String): String {
        // Tall keeps TOTAL prominent without doubling line width and wrapping the amount on 58mm paper.
        return if (scale == "compact" || scale == "normal" || scale == "large") "tall" else scale
    }

    private fun stablePlayNumberScale(scale: String): String {
        // Tall makes jugadas bigger without double-width wrapping on 58mm paper.
        return if (scale == "compact" || scale == "normal" || scale == "large") "tall" else scale
    }

    private fun tableHeader(width: Int): String {
        return ThermalLineStyling.playMoneyRow(
            label = "JUGADA",
            amount = "MONTO",
            width = width,
        )
    }

    private fun compactThreeColumnHeader(width: Int): String {
        val cellWidth = (width - 2) / 3
        return List(3) { compactHeaderCell(cellWidth) }.joinToString(" ")
    }

    private fun compactPlayCell(play: com.lotterynet.pro.core.model.PlayItem, width: Int): String {
        val amount = formatThermalMoney(play.amount).take(width)
        val label = listOf(
            playTypeText(play.playType, "short"),
            formatThermalPlayNumber(play.number, play.playType),
        ).joinToString(" ").trim()
        val amountWidth = amount.length.coerceAtLeast(4).coerceAtMost((width / 2).coerceAtLeast(4))
        val labelWidth = (width - amountWidth - 1).coerceAtLeast(1)
        val safeLabel = label.take(labelWidth).padEnd(labelWidth)
        return "$safeLabel ${amount.padStart(amountWidth)}".take(width).padEnd(width)
    }

    private fun compactHeaderCell(width: Int): String {
        val amountWidth = 5.coerceAtMost((width / 2).coerceAtLeast(4))
        val labelWidth = (width - amountWidth - 1).coerceAtLeast(1)
        return "JUGADA".take(labelWidth).padEnd(labelWidth) + " " + "MONTO".take(amountWidth).padStart(amountWidth)
    }

    private fun resolveCompactShareLineWidth(ticket: TicketRecord): Int {
        val lotteryCount = ticket.plays
            .map { play ->
                listOf(play.lotteryName, play.secondaryLotteryName)
                    .filterNotNull()
                    .joinToString("/")
                    .ifBlank { "Loteria" }
            }
            .distinct()
            .size
        return when {
            ticket.plays.size >= 100 || lotteryCount >= 6 -> 60
            ticket.plays.size >= 70 || lotteryCount >= 4 -> 52
            else -> 42
        }
    }

    private fun compactShareLotteryName(name: String, abbreviated: Boolean): String {
        if (!abbreviated) return name
        val normalized = name.uppercase(Locale.US).replace(Regex("\\s+"), " ").trim()
        val time = Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(AM|PM)\b""").find(normalized)?.let { match ->
            val hour = match.groupValues[1]
            val minute = match.groupValues[2].takeIf { it.isNotBlank() }
            val suffix = match.groupValues[3]
            if (minute == null || minute == "00") "$hour$suffix" else "$hour:$minute$suffix"
        } ?: when {
            Regex("\\b(AM|DIA|MANANA)\\b").containsMatchIn(normalized) -> "AM"
            Regex("\\b(PM|TARDE|NOCHE)\\b").containsMatchIn(normalized) -> "PM"
            else -> null
        }
        val prefix = when {
            "LOTEKA" in normalized -> "LK"
            "LEIDSA" in normalized -> "LEIDSA"
            "NACIONAL" in normalized -> "LN"
            "ANGUIL" in normalized -> "ANG"
            "KING" in normalized -> "KING"
            "PRIMERA" in normalized -> "PRI"
            "LA SUERTE" in normalized -> "SUERTE"
            "REAL" in normalized -> "REAL"
            "NEW YORK" in normalized -> "NY"
            "FLORIDA" in normalized -> "FL"
            else -> normalized.split(" ").filter { it.isNotBlank() }.take(2).joinToString(" ") { it.take(4) }
        }
        return listOfNotNull(prefix, time).joinToString(" ").take(24)
    }

    private fun formatThermalPlayNumber(number: String, playType: String): String {
        val normalizedPlayType = playType.uppercase(Locale.getDefault())
        if (normalizedPlayType in setOf("P3", "P3BOX", "P4", "P4BOX")) {
            val cleaned = number.filter(Char::isDigit).ifBlank { number }
            val suffix = if (normalizedPlayType.endsWith("BOX")) "B" else "S"
            return if (cleaned.endsWith(suffix, ignoreCase = true)) cleaned else cleaned + suffix
        }
        if (normalizedPlayType != "T") {
            return formatPlayDisplayNumber(number, playType)
        }
        if (number.contains("/")) return number
        if (number.contains("-")) return number.split("-").filter { it.isNotBlank() }.joinToString("/")
        val cleaned = number.filter(Char::isDigit)
        if (cleaned.length < 3) return number
        return cleaned.chunked(2).take(3).joinToString("/")
    }

    private fun renderTextBitmap(
        content: String,
        prefs: ThermalPrinterPrefs,
        drawTopRule: Boolean = true,
        inkColor: Int = Color.parseColor("#111827"),
    ): Bitmap {
        val textSize = when (prefs.previewZoom) {
            "130" -> 32f
            "115" -> 29f
            "90" -> 23f
            else -> 27f
        }
        val padding = 28f
        val typeface = when (prefs.fontFamily) {
            "courier" -> Typeface.MONOSPACE
            "jetbrains" -> Typeface.MONOSPACE
            else -> Typeface.MONOSPACE
        }
        val styledLines = content.lineSequence()
            .toList()
            .ifEmpty { listOf("") }
            .map(ThermalLineStyling::parse)
        val width = when {
            prefs.widthMode == "custom" -> {
                val chars = prefs.customChars.toIntOrNull()?.coerceIn(24, 80) ?: 42
                (chars * 15 + padding.toInt() * 2).coerceAtLeast(if (prefs.paperWidth == "80") 760 else 560)
            }
            prefs.paperWidth == "80" -> 760
            else -> 560
        }
        val height = (
            padding * 2 + styledLines.sumOf { styledLineHeight(it.style, it.scale, textSize).toDouble() }
        ).toInt().coerceAtLeast(220)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            if (drawTopRule) {
                val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E5E7EB") }
                canvas.drawRect(0f, 0f, width.toFloat(), 10f, dividerPaint)
            }
            var y = padding
            styledLines.forEach { line ->
                val paint = paintForStyle(line.style, line.scale, textSize, typeface, inkColor)
                val lineHeight = styledLineHeight(line.style, line.scale, textSize)
                if (line.style == ThermalLineStyle.QR) {
                    val qrSize = (lineHeight * 0.78f).toInt().coerceAtLeast(96)
                    val qr = buildQrBitmap(line.payload, qrSize)
                    val left = (width - qrSize) / 2f
                    canvas.drawBitmap(
                        qr,
                        null,
                        RectF(left, y + 8f, left + qrSize, y + 8f + qrSize),
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = false },
                    )
                    y += lineHeight
                } else {
                    y += lineHeight * 0.8f
                    val x = when (line.style) {
                        ThermalLineStyle.TITLE,
                        ThermalLineStyle.CENTER,
                        ThermalLineStyle.FOOTER -> width / 2f
                        else -> padding
                    }
                    canvas.drawText(line.text, x, y, paint)
                    y += lineHeight * 0.35f
                }
            }
        }
    }

    private fun buildQrBitmap(content: String, size: Int): Bitmap {
        val matrix = MultiFormatWriter().encode(
            content.ifBlank { "LN" },
            BarcodeFormat.QR_CODE,
            size,
            size,
        )
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        }
    }

    private fun paintForStyle(
        style: ThermalLineStyle,
        scale: String,
        baseTextSize: Float,
        typeface: Typeface,
        inkColor: Int = Color.parseColor("#111827"),
    ): Paint {
        val size = resolveTextSize(style, scale, baseTextSize)
        val align = when (style) {
            ThermalLineStyle.TITLE,
            ThermalLineStyle.CENTER,
            ThermalLineStyle.FOOTER,
            ThermalLineStyle.QR -> Paint.Align.CENTER
            else -> Paint.Align.LEFT
        }
        val styledTypeface = when (style) {
            ThermalLineStyle.TITLE,
            ThermalLineStyle.LOTTERY,
            ThermalLineStyle.BOLD,
            ThermalLineStyle.PLAY_TYPE,
            ThermalLineStyle.PLAY_NUMBER,
            ThermalLineStyle.PLAY_AMOUNT,
            ThermalLineStyle.SECURITY,
            ThermalLineStyle.QR,
            ThermalLineStyle.TOTAL -> Typeface.create(typeface, Typeface.BOLD)
            else -> typeface
        }
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColor
            textSize = size
            textAlign = align
            this.typeface = styledTypeface
        }
    }

    private fun styledLineHeight(style: ThermalLineStyle, scale: String, baseTextSize: Float): Float {
        val base = resolveTextSize(style, scale, baseTextSize) * 1.35f
        return if (style == ThermalLineStyle.QR) 148f else base
    }

    private fun resolveTextSize(style: ThermalLineStyle, scale: String, baseTextSize: Float): Float {
        val styleBoost = when (style) {
            ThermalLineStyle.TITLE -> 4f
            ThermalLineStyle.TOTAL -> 4f
            ThermalLineStyle.LOTTERY -> 2f
            ThermalLineStyle.BOLD,
            ThermalLineStyle.PLAY_TYPE,
            ThermalLineStyle.PLAY_NUMBER,
            ThermalLineStyle.PLAY_AMOUNT,
            ThermalLineStyle.SECURITY,
            ThermalLineStyle.QR -> 1f
            ThermalLineStyle.FOOTER -> -2f
            else -> 0f
        }
        val scaleBoost = when (scale) {
            "large" -> 5f
            "tall" -> 3f
            "compact" -> -3f
            else -> 0f
        }
        return (baseTextSize + styleBoost + scaleBoost).coerceAtLeast(18f)
    }

    private fun playTypeText(playType: String, labelMode: String): String {
        val label = playTypeLabel(playType)
        return when (labelMode) {
            "full" -> label
            "double" -> label.take(2).uppercase(Locale.getDefault())
            else -> playType.uppercase(Locale.getDefault())
        }
    }

    private fun playTypeLabel(playType: String): String {
        return when (playType.uppercase(Locale.getDefault())) {
            "Q" -> "Quiniela"
            "P" -> "Pale"
            "T" -> "Tripleta"
            "SP" -> "Super Pale"
            "P3" -> "Pick 3"
            "P4" -> "Pick 4"
            else -> playType
        }
    }

    private fun String.normalizeThermalText(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('·', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace('ñ', 'n')
            .replace('Ñ', 'N')
    }
}
