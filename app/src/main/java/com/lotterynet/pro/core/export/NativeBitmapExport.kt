package com.lotterynet.pro.core.export

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.print.PrintHelper
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.lotterynet.pro.core.catalog.LotteryLogoBitmapLoader
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.delivery.TicketDeliveryMode
import com.lotterynet.pro.core.delivery.TicketDeliveryPolicy
import com.lotterynet.pro.core.finance.FinanceAlertTone
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.FinancePeriodReport
import com.lotterynet.pro.core.finance.operationalReportCommissionPercent
import com.lotterynet.pro.core.finance.resolveOperationalReportNet
import com.lotterynet.pro.core.model.ResultShareRow
import com.lotterynet.pro.core.model.ResultsSharePayload
import com.lotterynet.pro.core.model.SportsbookTicketRecord
import com.lotterynet.pro.core.model.SportsbookTicketStatus
import com.lotterynet.pro.core.model.TicketQrPayload
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.WinningPlayDetail
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.effectiveDrawDateKey
import com.lotterynet.pro.core.model.formatPlayDisplayNumber
import com.lotterynet.pro.core.model.isPaidTicketStatus
import com.lotterynet.pro.core.perf.PosPerformanceBudget
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object NativeBitmapExport {
    data class ShareTargetPolicy(
        val chooserTitle: String,
        val forceWhatsAppPackage: Boolean,
    )

    fun resolveShareTargetPolicy(whatsappOnly: Boolean): ShareTargetPolicy {
        return ShareTargetPolicy(
            chooserTitle = if (whatsappOnly) "Elige donde compartir" else "Compartir",
            forceWhatsAppPackage = false,
        )
    }

private const val RESULTS_ROWS_PER_PAGE = 4
private const val RESULTS_POSTER_MAX_PAGES = 5
private const val PICK_DENSE_ROWS_PER_PAGE = 16
private const val RESULTS_WHATSAPP_BALL_GREEN = -15293622

internal enum class ResultNumbersMode {
    PRIMARY,
    PICK_ONLY,
    MIXED,
    EMPTY,
}

internal data class ResultRowLayout(
    val mode: ResultNumbersMode,
    val nameMaxWidth: Float,
    val numbersStartX: Float,
    val drawPrimaryBalls: Boolean,
)

internal data class PickResultBallLayout(
    val startX: Float,
    val endX: Float,
    val radius: Float,
    val spacing: Float,
)

internal data class ResultsWhatsAppBitmapSpec(
    val width: Int,
    val height: Int,
)

internal data class ResultsWhatsAppRowLayout(
    val rowHeight: Int,
    val logoLeft: Float,
    val logoTopOffset: Float,
    val logoWidth: Float,
    val logoHeight: Float,
    val contentLeft: Float,
    val nameY: Float,
    val metaY: Float,
    val primaryCenterY: Float,
    val pickStartY: Float,
)

internal enum class ResultsShareImageTemplate {
    EMPTY,
    LOTTERY_POSTER,
    LOTTERY_LIST,
    PICK3_DENSE_LIST,
    PICK4_DENSE_LIST,
}

internal data class ResultsShareImagePage(
    val template: ResultsShareImageTemplate,
    val rows: List<ResultShareRow>,
)

internal data class BitmapExportSpec(
    val width: Int,
)

internal data class BitmapExportSize(
    val width: Int,
    val height: Int,
)

internal fun resolveBitmapExportSpec(
    isLowRamDevice: Boolean,
    requestedWidth: Int,
): BitmapExportSpec {
    val maxWidth = if (isLowRamDevice) {
        PosPerformanceBudget.LOW_RAM_BITMAP_MAX_WIDTH_PX
    } else {
        requestedWidth
    }
    return BitmapExportSpec(width = requestedWidth.coerceAtMost(maxWidth))
}

internal fun resolveScaledBitmapExportSize(
    isLowRamDevice: Boolean,
    sourceWidth: Int,
    sourceHeight: Int,
): BitmapExportSize {
    if (sourceWidth <= 0 || sourceHeight <= 0) {
        return BitmapExportSize(width = sourceWidth.coerceAtLeast(0), height = sourceHeight.coerceAtLeast(0))
    }
    val targetWidth = resolveBitmapExportSpec(
        isLowRamDevice = isLowRamDevice,
        requestedWidth = sourceWidth,
    ).width
    if (targetWidth >= sourceWidth) {
        return BitmapExportSize(width = sourceWidth, height = sourceHeight)
    }
    val scale = targetWidth.toDouble() / sourceWidth.toDouble()
    return BitmapExportSize(
        width = targetWidth,
        height = kotlin.math.max(1, kotlin.math.round(sourceHeight * scale).toInt()),
    )
}

data class LotteryResultsViewData(
    val nombreLoteria: String,
    val fecha: String,
    val hora: String,
    val primerPremio: String,
    val segundoPremio: String,
    val tercerPremio: String,
    val logoAssetPath: String? = null,
    val cuartoPremio: String? = null,
    val prizeLabels: List<String> = listOf("1ER PREMIO", "2DO PREMIO", "3ER PREMIO"),
)

fun resolveLotteryResultsViewData(payload: ResultsSharePayload): LotteryResultsViewData? {
    val rowsWithPrimary = payload.rows.filter { row ->
        row.first.isNotBlank() || row.second.isNotBlank() || row.third.isNotBlank()
    }
    val row = if (rowsWithPrimary.isNotEmpty()) {
        rowsWithPrimary.firstOrNull { row ->
            row.displayName.lowercase(Locale.US).contains("nacional")
        } ?: rowsWithPrimary.firstOrNull()
    } else {
        null
    }
    return row?.let {
        val pickDigits = pickResultDigits(it)
        if (rowsWithPrimary.isEmpty() && pickDigits.isNotEmpty()) {
            LotteryResultsViewData(
                nombreLoteria = it.displayName.ifBlank { payload.bancaName.ifBlank { "Loteria" } },
                fecha = payload.dateLabel,
                hora = it.drawTimeLabel.orEmpty(),
                primerPremio = pickDigits.getOrElse(0) { "" },
                segundoPremio = pickDigits.getOrElse(1) { "" },
                tercerPremio = pickDigits.getOrElse(2) { "" },
                logoAssetPath = it.logoAssetPath,
                cuartoPremio = pickDigits.getOrNull(3),
                prizeLabels = pickDigits.indices.map { index -> "DIGITO ${index + 1}" },
            )
        } else {
            LotteryResultsViewData(
                nombreLoteria = it.displayName.ifBlank { payload.bancaName.ifBlank { "Loteria" } },
                fecha = payload.dateLabel,
                hora = it.drawTimeLabel.orEmpty(),
                primerPremio = it.first,
                segundoPremio = it.second,
                tercerPremio = it.third,
                logoAssetPath = it.logoAssetPath,
            )
        }
    }
}

internal fun shouldRenderLotteryResultsPoster(payload: ResultsSharePayload): Boolean {
    val primaryRows = payload.rows.filter(::isPrimaryLotteryResultRow)
    val pickRows = payload.rows.filter { row -> !isPrimaryLotteryResultRow(row) && isPickShareRow(row) }
    return primaryRows.size == 1 && pickRows.isEmpty()
}

internal fun isPrimaryLotteryResultRow(row: ResultShareRow): Boolean {
    return row.first.isNotBlank() || row.second.isNotBlank() || row.third.isNotBlank()
}

internal fun isPickOnlyResultRow(row: ResultShareRow): Boolean {
    return !isPrimaryLotteryResultRow(row) && isPickShareRow(row)
}

private fun hasAnyResultForPoster(row: ResultShareRow): Boolean {
    return row.first.isNotBlank() || row.second.isNotBlank() || row.third.isNotBlank()
}

private fun hasPickResults(row: ResultShareRow): Boolean {
    return row.pick3.orEmpty().any(Char::isDigit) || row.pick4.orEmpty().any(Char::isDigit)
}

private fun pickResultDigits(row: ResultShareRow): List<String> {
    return (row.pick4 ?: row.pick3).orEmpty().filter(Char::isDigit).map { it.toString() }
}

internal fun resolveResultsListPageSize(rowCount: Int): Int {
    return RESULTS_ROWS_PER_PAGE
}

internal fun chunkResultsRowsForPoster(rows: List<ResultShareRow>): List<List<ResultShareRow>> {
    if (rows.isEmpty()) return emptyList()
    if (rows.size <= RESULTS_ROWS_PER_PAGE) return listOf(rows)
    val pageCount = kotlin.math.min(RESULTS_POSTER_MAX_PAGES, rows.size)
    val baseSize = rows.size / pageCount
    val remainder = rows.size % pageCount
    val chunks = mutableListOf<List<ResultShareRow>>()
    var start = 0
    repeat(pageCount) { index ->
        val size = baseSize + if (index < remainder) 1 else 0
        val end = (start + size).coerceAtMost(rows.size)
        if (start < end) chunks += rows.subList(start, end)
        start = end
    }
    return chunks
}

internal fun chunkPickResultsRowsForDenseList(rows: List<ResultShareRow>): List<List<ResultShareRow>> {
    return rows.filter(::isPickShareRow)
        .sortedWith(compareBy<ResultShareRow>({ parseShareDrawTimeMinutes(it.drawTimeLabel) }, { it.displayName.lowercase(Locale.US) }))
        .chunked(PICK_DENSE_ROWS_PER_PAGE)
}

internal fun resolveResultsShareImagePages(payload: ResultsSharePayload): List<ResultsShareImagePage> {
    val publishedRows = payload.rows.filter(::shouldIncludeResultInSharePages)
    if (publishedRows.isEmpty()) {
        return listOf(ResultsShareImagePage(ResultsShareImageTemplate.EMPTY, emptyList()))
    }
    if (publishedRows.size == 1 && isPrimaryLotteryResultRow(publishedRows.single())) {
        return listOf(ResultsShareImagePage(ResultsShareImageTemplate.LOTTERY_POSTER, publishedRows))
    }

    val primaryRows = publishedRows.filter(::isPrimaryLotteryResultRow)
    val pick3Rows = publishedRows.filter(::isPick3ShareRow)
    val pick4Rows = publishedRows.filter(::isPick4ShareRow)
    val mixedValueRows = publishedRows.filter { row ->
        hasPickResults(row) && isPrimaryLotteryResultRow(row)
    }
    val pages = mutableListOf<ResultsShareImagePage>()

    if (primaryRows.isNotEmpty() || mixedValueRows.isNotEmpty()) {
        val lotteryRows = (primaryRows + mixedValueRows).distinctBy { row ->
            listOf(row.displayName, row.drawTimeLabel.orEmpty(), row.first, row.second, row.third, row.pick3.orEmpty(), row.pick4.orEmpty()).joinToString("|")
        }
        chunkResultsRowsForPoster(lotteryRows).forEach { chunk ->
            pages += ResultsShareImagePage(
                template = if (chunk.size == 1) ResultsShareImageTemplate.LOTTERY_POSTER else ResultsShareImageTemplate.LOTTERY_LIST,
                rows = chunk,
            )
        }
    }

    chunkPickResultsRowsForDenseList(pick3Rows).forEach { chunk ->
        pages += ResultsShareImagePage(ResultsShareImageTemplate.PICK3_DENSE_LIST, chunk)
    }
    chunkPickResultsRowsForDenseList(pick4Rows).forEach { chunk ->
        pages += ResultsShareImagePage(ResultsShareImageTemplate.PICK4_DENSE_LIST, chunk)
    }

    return pages.ifEmpty {
        listOf(ResultsShareImagePage(ResultsShareImageTemplate.EMPTY, emptyList()))
    }
}

internal fun resolveCashierResultsShareImagePages(payload: ResultsSharePayload): List<ResultsShareImagePage> {
    val publishedRows = payload.rows.filter(::shouldIncludeResultInSharePages)
    if (publishedRows.isEmpty()) {
        return listOf(ResultsShareImagePage(ResultsShareImageTemplate.EMPTY, emptyList()))
    }

    val primaryRows = publishedRows.filter { row ->
        isPrimaryLotteryResultRow(row) || (hasPickResults(row) && isPrimaryLotteryResultRow(row))
    }
    val pick3Rows = publishedRows.filter(::isPick3ShareRow)
    val pick4Rows = publishedRows.filter(::isPick4ShareRow)
    val pages = mutableListOf<ResultsShareImagePage>()

    primaryRows.chunked(RESULTS_ROWS_PER_PAGE).forEach { chunk ->
        pages += ResultsShareImagePage(ResultsShareImageTemplate.LOTTERY_LIST, chunk)
    }
    chunkPickResultsRowsForDenseList(pick3Rows).forEach { chunk ->
        pages += ResultsShareImagePage(ResultsShareImageTemplate.PICK3_DENSE_LIST, chunk)
    }
    chunkPickResultsRowsForDenseList(pick4Rows).forEach { chunk ->
        pages += ResultsShareImagePage(ResultsShareImageTemplate.PICK4_DENSE_LIST, chunk)
    }

    return pages.ifEmpty {
        listOf(ResultsShareImagePage(ResultsShareImageTemplate.EMPTY, emptyList()))
    }
}

private fun shouldIncludeResultInSharePages(row: ResultShareRow): Boolean {
    return hasAnyResultForPoster(row) || isPickShareRow(row)
}

private fun isPickShareRow(row: ResultShareRow): Boolean {
    return hasPickResults(row) || isPick3ShareRow(row) || isPick4ShareRow(row)
}

private fun isPick3ShareRow(row: ResultShareRow): Boolean {
    if (row.pick3.orEmpty().any(Char::isDigit)) return true
    val name = row.displayName.filter(Char::isLetterOrDigit).lowercase(Locale.US)
    return "pick3" in name || "daily3" in name || "numbers" in name
}

private fun isPick4ShareRow(row: ResultShareRow): Boolean {
    if (row.pick4.orEmpty().any(Char::isDigit)) return true
    val name = row.displayName.filter(Char::isLetterOrDigit).lowercase(Locale.US)
    return "pick4" in name || "daily4" in name || "cash4" in name || "win4" in name
}

private fun parseShareDrawTimeMinutes(raw: String?): Int {
    val text = raw.orEmpty().trim().uppercase(Locale.US)
    val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM))?""").find(text) ?: return Int.MAX_VALUE
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    val meridiem = match.groupValues.getOrNull(3).orEmpty()
    if (meridiem == "AM" && hour == 12) hour = 0
    if (meridiem == "PM" && hour < 12) hour += 12
    return hour * 60 + minute
}

internal fun resultPrizeLabels(count: Int): List<String> {
    return (0 until count.coerceAtLeast(0)).map { index ->
        when (index) {
            0 -> "🥇"
            1 -> "🥈"
            2 -> "🥉"
            3 -> "🏅"
            else -> (index + 1).toString()
        }
    }
}

internal fun resolvePickResultBallLayout(count: Int): PickResultBallLayout {
    val radius = 42f
    val spacing = 92f
    val endX = 1432f
    val safeCount = count.coerceAtLeast(1)
    return PickResultBallLayout(
        startX = endX - ((safeCount - 1) * spacing),
        endX = endX,
        radius = radius,
        spacing = spacing,
    )
}

internal fun resolveResultsHeaderHighlight(dateLabel: String, todayLabel: String = currentDominicanDateLabel()): String {
    val selected = parseDominicanDateLabel(dateLabel)
    val today = parseDominicanDateLabel(todayLabel)
    val relative = if (selected != null && today != null) {
        val days = ((today.time - selected.time) / 86_400_000L).toInt()
        when (days) {
            0 -> "hoy"
            1 -> "ayer"
            2 -> "anteayer"
            else -> dateLabel
        }
    } else {
        dateLabel
    }
    return "Numeros ganadores · Sorteo de $relative"
}

internal fun resolveResultRowLayout(row: ResultShareRow): ResultRowLayout {
    val hasPrimary = row.first.isNotBlank() || row.second.isNotBlank() || row.third.isNotBlank()
    val hasPick = row.pick3.orEmpty().any(Char::isDigit) || row.pick4.orEmpty().any(Char::isDigit)
    val mode = when {
        hasPrimary && hasPick -> ResultNumbersMode.MIXED
        hasPrimary -> ResultNumbersMode.PRIMARY
        hasPick -> ResultNumbersMode.PICK_ONLY
        else -> ResultNumbersMode.EMPTY
    }
    return when (mode) {
        ResultNumbersMode.PICK_ONLY -> ResultRowLayout(
            mode = mode,
            nameMaxWidth = 780f,
            numbersStartX = 1110f,
            drawPrimaryBalls = false,
        )
        ResultNumbersMode.MIXED -> ResultRowLayout(
            mode = mode,
            nameMaxWidth = 650f,
            numbersStartX = 1120f,
            drawPrimaryBalls = true,
        )
        ResultNumbersMode.PRIMARY -> ResultRowLayout(
            mode = mode,
            nameMaxWidth = 820f,
            numbersStartX = 1190f,
            drawPrimaryBalls = true,
        )
        ResultNumbersMode.EMPTY -> ResultRowLayout(
            mode = mode,
            nameMaxWidth = 930f,
            numbersStartX = 1190f,
            drawPrimaryBalls = false,
        )
    }
}

    data class ExportActionResult(
        val success: Boolean,
        val message: String,
    )

    private data class TicketGroupLayout(
        val lotteryName: String,
        val lotteryLogoAssetPath: String?,
        val plays: List<PlayItem>,
        val columns: List<List<PlayItem>>,
        val rows: Int,
        val density: OfficialTicketBitmapDensity,
        val height: Int,
    )

    internal data class OfficialTicketWinnerGroup(
        val lotteryName: String,
        val resultNumber: String,
        val details: List<WinningPlayDetail>,
        val totalPayout: Double,
    )

    internal fun shouldShowWinnerGroupSubtotal(group: OfficialTicketWinnerGroup): Boolean {
        return group.details.size > 1
    }

    internal data class OfficialTicketSecurityLayout(
        val boxTopY: Float,
        val boxBottomY: Float,
        val labelBaselineY: Float,
        val valueBaselineY: Float,
        val valueTextSize: Float,
    )

    internal data class OfficialTicketBitmapPlayVisual(
        val ballRadiusPx: Float,
        val textSizePx: Float,
        val rowHeightPx: Int,
        val spacingPx: Float,
    )

    internal data class OfficialTicketBitmapDensity(
        val compact: Boolean,
        val rowHeightPx: Int,
        val playTextSizePx: Float,
        val amountTextSizePx: Float,
        val headerTextSizePx: Float,
    )

    internal data class OfficialTicketLotteryHeaderLayout(
        val heightPx: Int,
        val logoSizePx: Float,
        val playColumnHeader: String,
        val amountColumnHeader: String,
    )

    internal data class OfficialTicketHeaderIdentity(
        val primaryText: String,
        val secondaryText: String,
        val logoWidthPx: Float,
        val logoHeightPx: Float,
    )

    internal data class BrandLogoTarget(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun width(): Float = right - left
        fun height(): Float = bottom - top
        fun toRectF(): RectF = RectF(left, top, right, bottom)
    }

    internal fun resolveOfficialTicketHeaderIdentity(
        bancaName: String,
        hasLogo: Boolean,
    ): OfficialTicketHeaderIdentity {
        return OfficialTicketHeaderIdentity(
            primaryText = bancaName.ifBlank { "LotteryNet" },
            secondaryText = if (hasLogo) "TICKET OFICIAL" else "BANCA DE LOTERIA",
            logoWidthPx = if (hasLogo) 420f else 168f,
            logoHeightPx = if (hasLogo) 190f else 168f,
        )
    }

    internal fun resolveBrandLogoTarget(
        bitmapWidth: Int,
        bitmapHeight: Int,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ): BrandLogoTarget {
        val safeWidth = bitmapWidth.coerceAtLeast(1)
        val safeHeight = bitmapHeight.coerceAtLeast(1)
        val scale = kotlin.math.max(width / safeWidth, height / safeHeight)
        val drawWidth = safeWidth * scale
        val drawHeight = safeHeight * scale
        return BrandLogoTarget(
            left + (width - drawWidth) / 2f,
            top + (height - drawHeight) / 2f,
            left + (width + drawWidth) / 2f,
            top + (height + drawHeight) / 2f,
        )
    }

    internal fun resolveOfficialTicketSecurityLayout(top: Float): OfficialTicketSecurityLayout {
        return OfficialTicketSecurityLayout(
            boxTopY = top,
            boxBottomY = top + 112f,
            labelBaselineY = top + 28f,
            valueBaselineY = top + 78f,
            valueTextSize = 50f,
        )
    }

    internal fun resolveOfficialTicketBitmapPlayVisual(
        partCount: Int,
        hasLongPart: Boolean,
    ): OfficialTicketBitmapPlayVisual {
        val radius = when {
            hasLongPart -> 24f
            partCount <= 2 -> 28f
            else -> 26f
        }
        return OfficialTicketBitmapPlayVisual(
            ballRadiusPx = radius,
            textSizePx = if (hasLongPart) 26f else 32f,
            rowHeightPx = 96,
            spacingPx = radius * 2f + 8f,
        )
    }

    internal fun resolveOfficialTicketBitmapDensity(
        totalPlayCount: Int,
        groupCount: Int,
        maxGroupPlayCount: Int,
    ): OfficialTicketBitmapDensity {
        val compact = totalPlayCount >= 24 || groupCount >= 4 || maxGroupPlayCount >= 16
        return when {
            !compact -> OfficialTicketBitmapDensity(
                compact = false,
                rowHeightPx = resolveOfficialTicketBitmapPlayVisual(partCount = 2, hasLongPart = false).rowHeightPx,
                playTextSizePx = 36f,
                amountTextSizePx = 36f,
                headerTextSizePx = 18f,
            )
            totalPlayCount >= 120 -> OfficialTicketBitmapDensity(
                compact = true,
                rowHeightPx = 38,
                playTextSizePx = 26f,
                amountTextSizePx = 25f,
                headerTextSizePx = 24f,
            )
            totalPlayCount >= 72 -> OfficialTicketBitmapDensity(
                compact = true,
                rowHeightPx = 42,
                playTextSizePx = 28f,
                amountTextSizePx = 27f,
                headerTextSizePx = 25f,
            )
            else -> OfficialTicketBitmapDensity(
                compact = true,
                rowHeightPx = 50,
                playTextSizePx = 32f,
                amountTextSizePx = 30f,
                headerTextSizePx = 27f,
            )
        }
    }

    internal fun resolveOfficialTicketLotteryHeaderLayout(): OfficialTicketLotteryHeaderLayout {
        return OfficialTicketLotteryHeaderLayout(
            heightPx = 76,
            logoSizePx = 58f,
            playColumnHeader = "Jugada",
            amountColumnHeader = "Monto",
        )
    }

    internal fun sortOfficialTicketPlays(plays: List<PlayItem>): List<PlayItem> {
        return plays.sortedWith(
            compareBy<PlayItem> { officialPlayTypeOrder(it.playType) }
                .thenBy { it.number.filter(Char::isDigit) }
                .thenBy { it.number },
        )
    }

    private fun officialPlayTypeOrder(playType: String): Int {
        return when (playType.uppercase(Locale.US)) {
            "Q" -> 0
            "P" -> 1
            "SP" -> 2
            "T" -> 3
            else -> 4
        }
    }

    fun renderOfficialTicketBitmap(
        context: Context? = null,
        ticket: TicketRecord,
        bancaName: String,
        securityCode: String = "",
        bancaLogoUri: String? = null,
    ): Bitmap {
        val width = 1260
        val headerHeight = 342
        val serialBandHeight = 132
        val securityBandHeight = if (securityCode.isNotBlank()) 142 else 0
        val totalBarHeight = 198
        val qrSectionHeight = 284
        val footerHeight = 200
        val catalog = StaticLotteryCatalogRepository()
        val grouped = ticket.plays.groupBy { play ->
            val primary = play.lotteryName.orEmpty().ifBlank { "Lotería" }
            val secondary = play.secondaryLotteryName.orEmpty().trim()
            if (secondary.isNotBlank()) "$primary | $secondary" else primary
        }
        val density = resolveOfficialTicketBitmapDensity(
            totalPlayCount = ticket.plays.size,
            groupCount = grouped.size,
            maxGroupPlayCount = grouped.values.maxOfOrNull { it.size } ?: 0,
        )
        val lotteryHeaderLayout = resolveOfficialTicketLotteryHeaderLayout()
        val groupEntries = grouped.entries.map { entry ->
            val sortedPlays = sortOfficialTicketPlays(entry.value)
            val columns = splitTicketColumns(sortedPlays)
            val rows = columns.maxOfOrNull { it.size } ?: 0
            val primaryName = entry.value.firstOrNull()?.lotteryName.orEmpty().ifBlank { "Lotería" }
            TicketGroupLayout(
                lotteryName = entry.key.replace(" | ", " · "),
                lotteryLogoAssetPath = catalog.getLotteryByName(primaryName)?.logoAssetPath,
                plays = sortedPlays,
                columns = columns,
                rows = rows,
                density = density,
                height = lotteryHeaderLayout.heightPx + 44 + (rows * density.rowHeightPx),
            )
        }
        val groupsHeight = groupEntries.sumOf { it.height } + (kotlin.math.max(0, groupEntries.size - 1) * 24)
        val winnerGroups = groupOfficialTicketWinnerDetails(ticket)
        val winnerDetailsHeight = if (winnerGroups.isNotEmpty()) {
            92 + winnerGroups.sumOf { group -> 78 + (group.details.size * 96) }
        } else {
            0
        }
        val height = headerHeight + serialBandHeight + securityBandHeight + groupsHeight + winnerDetailsHeight + totalBarHeight + qrSectionHeight + footerHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val navy = Color.parseColor("#071A44")
        val navyDeep = Color.parseColor("#13367A")
        val navySoft = Color.parseColor("#16355E")
        val gold = Color.parseColor("#F59E0B")
        val goldSoft = Color.parseColor("#C99612")
        val goldPale = Color.parseColor("#F8DF8C")
        val green = Color.parseColor("#16A34A")
        val greenDeep = Color.parseColor("#14532D")
        val greenSoft = Color.parseColor("#DCFCE7")
        val outline = Color.parseColor("#E2E8F0")
        val surfaceAlt = Color.parseColor("#F8FAFC")
        val muted = Color.parseColor("#64748B")
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 34f
            isFakeBoldText = true
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 24f
            isFakeBoldText = true
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = outline
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                width.toFloat(),
                headerHeight.toFloat(),
                navyDeep,
                greenDeep,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), headerPaint)
        fill.color = gold
        canvas.drawRect(0f, 0f, width.toFloat(), 18f, fill)
        canvas.drawRect(0f, headerHeight - 12f, width.toFloat(), headerHeight.toFloat(), fill)
        val brandLogo = if (context != null && !bancaLogoUri.isNullOrBlank()) {
            loadBitmapFromUri(context, bancaLogoUri)
        } else {
            null
        }
        val headerIdentity = resolveOfficialTicketHeaderIdentity(bancaName, brandLogo != null)
        if (brandLogo != null) {
            drawBrandLogo(canvas, brandLogo, 54f, 42f, headerIdentity.logoWidthPx, headerIdentity.logoHeightPx, goldPale)
        } else {
            drawHeaderMedallion(canvas, 48f, 48f, headerIdentity.logoWidthPx, goldSoft, navy)
        }
        fill.color = green
        canvas.drawCircle(1136f, 84f, 18f, fill)
        canvas.drawCircle(1188f, 84f, 18f, fill)
        if (brandLogo == null) {
            small.color = goldSoft
            canvas.drawText(headerIdentity.secondaryText, 246f, 98f, small)
            body.color = Color.WHITE
            body.textSize = if (headerIdentity.primaryText.length > 16) 68f else 82f
            drawTextFitted(canvas, headerIdentity.primaryText, 246f, 180f, body, 620f)
        } else {
            small.color = goldSoft
            small.textAlign = Paint.Align.LEFT
            canvas.drawText(headerIdentity.secondaryText, 520f, 96f, small)
            body.color = Color.WHITE
            body.textSize = if (headerIdentity.primaryText.length > 18) 44f else 54f
            drawTextFitted(canvas, headerIdentity.primaryText, 520f, 168f, body, 360f)
        }
        val headerInfo = listOf(ticket.adminUser, ticket.sellerUser).filter { !it.isNullOrBlank() }.joinToString("  ·  ")
        if (headerInfo.isNotBlank()) {
            small.color = Color.argb(212, Color.red(goldPale), Color.green(goldPale), Color.blue(goldPale))
            canvas.drawText(headerInfo, if (brandLogo == null) 246f else 520f, 224f, small)
        }
        drawHeaderBadge(canvas, width - 462f, 146f, 408f, 96f, Color.argb(34, Color.red(gold), Color.green(gold), Color.blue(gold)), goldPale, "ORIGINAL", Color.argb(120, Color.red(goldPale), Color.green(goldPale), Color.blue(goldPale)))
        drawTicketStatusBanner(
            canvas = canvas,
            width = width.toFloat(),
            ticket = ticket,
        )

        var top = headerHeight.toFloat()
        fill.color = Color.parseColor("#F9FBFF")
        canvas.drawRect(0f, top, width.toFloat(), top + serialBandHeight, fill)
        fill.color = gold
        canvas.drawRect(0f, top, 12f, top + serialBandHeight, fill)
        small.color = muted
        small.textAlign = Paint.Align.LEFT
        small.textSize = 22f
        canvas.drawText("NUMERO DE SERIE", 42f, top + 50f, small)
        mono.color = navyDeep
        mono.textSize = 66f
        mono.textAlign = Paint.Align.LEFT
        canvas.drawText((ticket.serial ?: ticket.id).sanitizeTicketId(), 42f, top + 122f, mono)
        small.textAlign = Paint.Align.RIGHT
        canvas.drawText("FECHA / HORA", width - 42f, top + 50f, small)
        mono.textSize = 38f
        mono.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatDateForTicket(ticket.createdAtEpochMs), width - 42f, top + 92f, mono)
        mono.color = navySoft
        mono.textSize = 32f
        canvas.drawText(formatTimeForTicket(ticket.createdAtEpochMs), width - 42f, top + 134f, mono)
        canvas.drawLine(0f, top + serialBandHeight, width.toFloat(), top + serialBandHeight, stroke)
        top += serialBandHeight
        if (securityCode.isNotBlank()) {
            drawInfoBox(canvas, 42f, top + 18f, width - 42f, top + 118f, "Codigo de seguridad", securityCode, body, small, stroke)
            top += securityBandHeight
        }

        groupEntries.forEachIndexed { index, group ->
            if (index > 0) {
                fill.color = Color.parseColor("#F1F5F9")
                canvas.drawRect(0f, top, width.toFloat(), top + 24f, fill)
                top += 24f
            }
            val lotColor = lotAccentColor(group.lotteryName)
            fill.color = lotColor
            canvas.drawRect(0f, top, width.toFloat(), top + lotteryHeaderLayout.heightPx, fill)
            fill.color = gold
            canvas.drawRect(0f, top, width.toFloat(), top + 6f, fill)
            val logoBitmap = if (context != null && !group.lotteryLogoAssetPath.isNullOrBlank()) {
                LotteryLogoBitmapLoader.load(context, group.lotteryLogoAssetPath)
            } else {
                null
            }
            if (logoBitmap != null) {
                drawLogoThumb(canvas, logoBitmap, 34f, top + 10f, lotteryHeaderLayout.logoSizePx)
            } else {
                drawLotteryBadge(canvas, 34f, top + 10f, lotteryHeaderLayout.logoSizePx, group.lotteryName.take(2), lotColor)
            }
            body.color = Color.WHITE
            body.textSize = 36f
            canvas.drawText(group.lotteryName, 110f, top + 43f, body)
            small.color = Color.argb(218, 255, 255, 255)
            small.textAlign = Paint.Align.LEFT
            small.textSize = 20f
            canvas.drawText("JUGADAS ${group.plays.size}", 110f, top + 66f, small)
            top += lotteryHeaderLayout.heightPx

            val columnGap = 24f
            val columnWidth = if (group.columns.size > 1) (width - 84f - columnGap) / 2f else width - 84f
            group.columns.forEachIndexed { columnIndex, column ->
                val columnLeft = 42f + (columnIndex * (columnWidth + columnGap))
                fill.color = Color.parseColor("#FBFDFF")
                canvas.drawRoundRect(RectF(columnLeft, top + 6f, columnLeft + columnWidth, top + 44f), 12f, 12f, fill)
                small.color = Color.parseColor("#334155")
                small.textAlign = Paint.Align.LEFT
                small.textSize = group.density.headerTextSizePx
                canvas.drawText(lotteryHeaderLayout.playColumnHeader, columnLeft + 24f, top + 31f, small)
                small.textAlign = Paint.Align.RIGHT
                canvas.drawText(lotteryHeaderLayout.amountColumnHeader, columnLeft + columnWidth - 24f, top + 31f, small)

                column.forEachIndexed { rowIndex, play ->
                    val rowHeight = group.density.rowHeightPx.toFloat()
                    val rowTop = top + 44f + (rowIndex * rowHeight)
                    if (rowIndex % 2 == 0) {
                        fill.color = surfaceAlt
                        canvas.drawRect(columnLeft, rowTop, columnLeft + columnWidth, rowTop + rowHeight, fill)
                    }
                    if (rowIndex > 0) {
                        canvas.drawLine(columnLeft + 12f, rowTop, columnLeft + columnWidth - 12f, rowTop, stroke)
                    }
                    if (group.density.compact) {
                        mono.color = navy
                        mono.textSize = group.density.playTextSizePx
                        mono.textAlign = Paint.Align.LEFT
                        canvas.drawText(
                            "${play.playType.uppercase(Locale.US)} ${formatPlayDisplayNumber(play.number, play.playType)}",
                            columnLeft + 24f,
                            rowTop + (rowHeight * 0.72f),
                            mono,
                        )
                    } else {
                        drawPlayVisual(canvas, play.number, play.playType, columnLeft + 30f, rowTop + 50f, lotColor)
                        small.color = Color.parseColor("#475569")
                        small.textAlign = Paint.Align.LEFT
                        small.textSize = 18f
                        canvas.drawText(playTypeLabel(play.playType), columnLeft + 30f, rowTop + 88f, small)
                        small.color = muted
                        small.textAlign = Paint.Align.RIGHT
                        canvas.drawText("$", columnLeft + columnWidth - 24f, rowTop + 34f, small)
                    }
                    mono.color = navy
                    mono.textSize = group.density.amountTextSizePx
                    mono.textAlign = Paint.Align.RIGHT
                    canvas.drawText(formatMoney(play.amount), columnLeft + columnWidth - 24f, rowTop + (rowHeight * 0.72f), mono)
                }
            }
            top += group.height - lotteryHeaderLayout.heightPx
        }

        if (winnerGroups.isNotEmpty()) {
            fill.color = Color.parseColor("#ECFDF5")
            canvas.drawRect(0f, top, width.toFloat(), top + winnerDetailsHeight, fill)
            fill.color = greenDeep
            canvas.drawRect(0f, top, width.toFloat(), top + 72f, fill)
            fill.color = gold
            canvas.drawRect(0f, top, width.toFloat(), top + 6f, fill)
            body.color = Color.WHITE
            body.textSize = 34f
            body.textAlign = Paint.Align.LEFT
            canvas.drawText("PREMIOS DEL TICKET", 42f, top + 48f, body)
            body.textAlign = Paint.Align.RIGHT
            body.textSize = 32f
            canvas.drawText("$ ${formatMoney(winnerPrizeTotalAmount(ticket))}", width - 42f, top + 40f, body)
            small.color = Color.argb(220, 255, 255, 255)
            small.textAlign = Paint.Align.RIGHT
            small.textSize = 22f
            canvas.drawText(winnerDetailsMeta(ticket), width - 42f, top + 64f, small)
            top += 92f

            winnerGroups.forEachIndexed { groupIndex, group ->
                val groupTop = top
                fill.color = if (groupIndex % 2 == 0) Color.WHITE else Color.parseColor("#F8FAFC")
                canvas.drawRect(42f, groupTop, width - 42f, groupTop + 66f, fill)
                fill.color = green
                canvas.drawRoundRect(RectF(58f, groupTop + 16f, 78f, groupTop + 36f), 10f, 10f, fill)
                body.color = navy
                body.textSize = 28f
                body.textAlign = Paint.Align.LEFT
                drawTextFitted(canvas, group.lotteryName, 92f, groupTop + 32f, body, 520f)
                small.color = muted
                small.textSize = 18f
                small.textAlign = Paint.Align.LEFT
                canvas.drawText("Resultado ${group.resultNumber.ifBlank { "-" }}", 92f, groupTop + 58f, small)
                if (shouldShowWinnerGroupSubtotal(group)) {
                    mono.color = greenDeep
                    mono.textAlign = Paint.Align.RIGHT
                    mono.textSize = 34f
                    canvas.drawText("$ ${formatMoney(group.totalPayout)}", width - 64f, groupTop + 44f, mono)
                }
                top += 78f

                group.details.forEachIndexed { detailIndex, detail ->
                    val rowTop = top + (detailIndex * 96f)
                    fill.color = if (detailIndex % 2 == 0) Color.parseColor("#FBFEFC") else Color.WHITE
                    canvas.drawRect(64f, rowTop, width - 64f, rowTop + 84f, fill)
                    canvas.drawLine(76f, rowTop + 84f, width - 76f, rowTop + 84f, stroke)
                    small.color = muted
                    small.textSize = 18f
                    small.textAlign = Paint.Align.LEFT
                    canvas.drawText("Tipo", 88f, rowTop + 28f, small)
                    canvas.drawText("Jugado", 248f, rowTop + 28f, small)
                    canvas.drawText("Acierto", 456f, rowTop + 28f, small)
                    canvas.drawText("Apuesta", 672f, rowTop + 28f, small)
                    canvas.drawText("Premio", 850f, rowTop + 28f, small)
                    mono.color = navy
                    mono.textAlign = Paint.Align.LEFT
                    mono.textSize = 25f
                    canvas.drawText(playTypeLabel(detail.playType), 88f, rowTop + 66f, mono)
                    drawTextFitted(
                        canvas,
                        formatPlayDisplayNumber(detail.playedNumber, detail.playType),
                        248f,
                        rowTop + 66f,
                        mono,
                        180f,
                    )
                    drawTextFitted(canvas, winnerHitLabel(detail.hitPosition), 456f, rowTop + 66f, mono, 190f)
                    mono.textAlign = Paint.Align.RIGHT
                    canvas.drawText("$ ${formatMoney(detail.amount)}", 790f, rowTop + 66f, mono)
                    mono.color = greenDeep
                    canvas.drawText("$ ${formatMoney(detail.payoutAmount)}", width - 88f, rowTop + 66f, mono)
                }
                top += group.details.size * 96f
            }
        }

        val totalTop = top + 18f
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                totalTop,
                width.toFloat(),
                totalTop + totalBarHeight,
                navy,
                greenDeep,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, totalTop, width.toFloat(), totalTop + totalBarHeight, totalPaint)
        fill.color = gold
        canvas.drawRect(0f, totalTop, width.toFloat(), totalTop + 14f, fill)
        fill.color = Color.argb(24, Color.red(goldPale), Color.green(goldPale), Color.blue(goldPale))
        canvas.drawRoundRect(RectF(34f, totalTop + 38f, 312f, totalTop + 106f), 34f, 34f, fill)
        small.color = Color.parseColor("#F8DF8C")
        small.textAlign = Paint.Align.LEFT
        small.textSize = 26f
        canvas.drawText("TOTAL A JUGAR", 58f, totalTop + 82f, small)
        mono.color = gold
        mono.textAlign = Paint.Align.RIGHT
        mono.textSize = 94f
        canvas.drawText("$ ${formatMoney(ticket.total)}", width - 36f, totalTop + 144f, mono)
        top = totalTop + totalBarHeight

        drawQrBlock(
            canvas = canvas,
            left = 42f,
            top = top,
            size = 196,
            payload = TicketQrPayload(
                id = ticket.id,
                banca = bancaName,
                lots = ticket.plays.mapNotNull { it.lotteryName }.distinct().joinToString(" / "),
                date = formatDateForTicket(ticket.createdAtEpochMs),
                total = ticket.total,
                securityCode = securityCode,
                plays = ticket.plays.map {
                    com.lotterynet.pro.core.model.TicketQrPlayPayload(
                        type = it.playType,
                        number = it.number,
                        amount = it.amount,
                        lotteryName = it.lotteryName.orEmpty(),
                    )
                },
            ),
            outline = outline,
            navy = navy,
            muted = muted,
        )
        top += qrSectionHeight

        fill.color = navy
        canvas.drawRect(0f, top, width.toFloat(), top + footerHeight, fill)
        fill.color = gold
        canvas.drawRect(0f, top, width.toFloat(), top + 6f, fill)
        body.color = Color.parseColor("#CBD5E1")
        body.textSize = 26f
        body.textAlign = Paint.Align.CENTER
        val validityText = "Ticket valido para el sorteo ${formatDrawDateForTicket(ticket.effectiveDrawDateKey(), resolveTicketDrawTimeLabel(ticket))}"
        canvas.drawText(validityText, width / 2f, top + 46f, body)
        canvas.drawText("Presentar para cobrar premio", width / 2f, top + 82f, body)
        body.color = goldPale
        body.textSize = 23f
        val footerMeta = listOfNotNull(bancaName.takeIf { it.isNotBlank() }, ticket.sellerUser?.takeIf { it.isNotBlank() }).joinToString(" · ")
        canvas.drawText(footerMeta.ifBlank { "LotteryNet" }, width / 2f, top + 120f, body)
        if (securityCode.isNotBlank()) {
            body.color = Color.WHITE
            body.textSize = 22f
            canvas.drawText("Verificacion: $securityCode", width / 2f, top + 154f, body)
        }
        return bitmap
    }

    fun renderOfficialTicketBitmaps(
        context: Context? = null,
        ticket: TicketRecord,
        bancaName: String,
        securityCode: String = "",
        bancaLogoUri: String? = null,
    ): List<Bitmap> {
        val estimatedHeight = estimateOfficialTicketBitmapHeight(ticket, securityCode)
        val decision = TicketDeliveryPolicy.resolveDecision(ticket, estimatedHeight)
        if (decision.mode == TicketDeliveryMode.SINGLE_IMAGE || ticket.plays.isEmpty()) {
            return listOf(
                renderOfficialTicketBitmap(
                    context = context,
                    ticket = ticket,
                    bancaName = bancaName,
                    securityCode = securityCode,
                    bancaLogoUri = bancaLogoUri,
                ),
            )
        }
        val pages = TicketDeliveryPolicy.buildPages(ticket)
        if (pages.isEmpty()) {
            return listOf(
                renderOfficialTicketBitmap(
                    context = context,
                    ticket = ticket,
                    bancaName = bancaName,
                    securityCode = securityCode,
                    bancaLogoUri = bancaLogoUri,
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
            renderOfficialTicketBitmap(
                context = context,
                ticket = pageTicket,
                bancaName = bancaName,
                securityCode = securityCode,
                bancaLogoUri = bancaLogoUri,
            )
        }
    }

    internal fun estimateOfficialTicketBitmapHeight(
        ticket: TicketRecord,
        securityCode: String = "",
    ): Int {
        val headerHeight = 342
        val serialBandHeight = 132
        val securityBandHeight = if (securityCode.isNotBlank()) 142 else 0
        val totalBarHeight = 198
        val qrSectionHeight = 284
        val footerHeight = 200
        val groups = ticket.plays.groupBy { play ->
            val primary = play.lotteryName.orEmpty().ifBlank { "Lotería" }
            val secondary = play.secondaryLotteryName.orEmpty().trim()
            if (secondary.isNotBlank()) "$primary | $secondary" else primary
        }
        val density = resolveOfficialTicketBitmapDensity(
            totalPlayCount = ticket.plays.size,
            groupCount = groups.size,
            maxGroupPlayCount = groups.values.maxOfOrNull { it.size } ?: 0,
        )
        val lotteryHeaderLayout = resolveOfficialTicketLotteryHeaderLayout()
        val groupsHeight = groups.values.sumOf { plays ->
            val columns = splitTicketColumns(sortOfficialTicketPlays(plays))
            val rows = columns.maxOfOrNull { it.size } ?: 0
            lotteryHeaderLayout.heightPx + 44 + (rows * density.rowHeightPx)
        } + (kotlin.math.max(0, groups.size - 1) * 24)
        val winnerGroups = groupOfficialTicketWinnerDetails(ticket)
        val winnerDetailsHeight = if (winnerGroups.isNotEmpty()) {
            92 + winnerGroups.sumOf { group -> 78 + (group.details.size * 96) }
        } else {
            0
        }
        return headerHeight + serialBandHeight + securityBandHeight + groupsHeight + winnerDetailsHeight + totalBarHeight + qrSectionHeight + footerHeight
    }

    fun renderResultsBitmap(payload: ResultsSharePayload, context: Context? = null): Bitmap {
        return renderResultsBitmaps(payload, context).first()
    }

    fun renderResultsBitmaps(payload: ResultsSharePayload, context: Context? = null): List<Bitmap> {
        val pages = resolveResultsShareImagePages(payload)
        return pages.mapIndexed { index, page ->
            renderResultsShareImagePage(
                payload = payload.copy(rows = page.rows),
                page = page,
                pageIndex = index,
                pageCount = pages.size,
                context = context,
            )
        }
    }

    fun renderResultadosWhatsAppBitmap(payload: ResultsSharePayload, context: Context? = null): Bitmap {
        return renderResultsBitmap(payload, context)
    }

    internal fun renderResultsShareImagePage(
        payload: ResultsSharePayload,
        page: ResultsShareImagePage,
        pageIndex: Int,
        pageCount: Int,
        context: Context? = null,
    ): Bitmap {
        return when (page.template) {
            ResultsShareImageTemplate.EMPTY -> renderEmptyResultsBitmap(payload, context)
            ResultsShareImageTemplate.LOTTERY_POSTER -> {
                resolveLotteryResultsViewData(payload.copy(rows = page.rows))?.let { poster ->
                    renderLotteryResultsViewBitmap(poster, context)
                } ?: renderEmptyResultsBitmap(payload, context)
            }
            ResultsShareImageTemplate.LOTTERY_LIST -> renderResultsPageBitmap(
                payload = payload.copy(rows = page.rows),
                pageIndex = pageIndex,
                pageCount = pageCount,
                context = context,
            )
            ResultsShareImageTemplate.PICK3_DENSE_LIST -> renderPickDenseResultsBitmap(
                payload = payload.copy(rows = page.rows),
                pickLabel = "Pick 3",
                pageIndex = pageIndex,
                pageCount = pageCount,
                context = context,
            )
            ResultsShareImageTemplate.PICK4_DENSE_LIST -> renderPickDenseResultsBitmap(
                payload = payload.copy(rows = page.rows),
                pickLabel = "Pick 4",
                pageIndex = pageIndex,
                pageCount = pageCount,
                context = context,
            )
        }
    }

    internal fun resolveResultadosWhatsAppBitmapSpec(payload: ResultsSharePayload): ResultsWhatsAppBitmapSpec {
        val pages = resolveResultsShareImagePages(payload)
        if (pages.size == 1 && pages.first().template in setOf(ResultsShareImageTemplate.PICK3_DENSE_LIST, ResultsShareImageTemplate.PICK4_DENSE_LIST)) {
            return resolvePickDenseResultsBitmapSpec(pages.first().rows.size)
        }
        if (pages.size == 1 && pages.first().template == ResultsShareImageTemplate.LOTTERY_POSTER) {
            return ResultsWhatsAppBitmapSpec(width = 1600, height = 1600)
        }
        val width = 1600
        val headerHeight = 272
        val footerHeight = 112
        val rowHeight = resolveResultsWhatsAppRowLayout().rowHeight
        val gap = 22
        val contentHeight = headerHeight + 32 + (payload.rows.size * (rowHeight + gap)) + footerHeight
        return ResultsWhatsAppBitmapSpec(
            width = width,
            height = kotlin.math.max(1600, contentHeight),
        )
    }

    fun renderSportsbookTicketBitmap(
        ticket: SportsbookTicketRecord,
        bancaName: String,
    ): Bitmap {
        val width = 1080
        val headerHeight = 260
        val ticketBandHeight = 132
        val legHeight = 126
        val legsHeight = (ticket.legs.size.coerceAtLeast(1) * legHeight) + 96
        val totalHeight = 176
        val footerHeight = 148
        val height = headerHeight + ticketBandHeight + legsHeight + totalHeight + footerHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val navy = Color.parseColor("#071A44")
        val blue = Color.parseColor("#164EAD")
        val green = Color.parseColor("#16A34A")
        val gold = Color.parseColor("#F59E0B")
        val surface = Color.parseColor("#F8FAFC")
        val outline = Color.parseColor("#E2E8F0")
        val muted = Color.parseColor("#64748B")
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = outline
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 34f
            isFakeBoldText = true
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 24f
            isFakeBoldText = true
        }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                width.toFloat(),
                headerHeight.toFloat(),
                blue,
                Color.parseColor("#14532D"),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), headerPaint)
        fill.color = gold
        canvas.drawRect(0f, 0f, width.toFloat(), 14f, fill)
        canvas.drawRect(0f, headerHeight - 10f, width.toFloat(), headerHeight.toFloat(), fill)
        fill.color = Color.argb(34, 255, 255, 255)
        canvas.drawRoundRect(RectF(44f, 46f, 150f, 152f), 28f, 28f, fill)
        body.color = Color.WHITE
        body.textSize = 52f
        canvas.drawText("D", 82f, 116f, body)
        small.color = Color.parseColor("#F8DF8C")
        canvas.drawText("APUESTA DEPORTIVA", 184f, 80f, small)
        body.textSize = if (bancaName.length > 18) 44f else 56f
        drawTextFitted(canvas, bancaName.ifBlank { "Deportes" }, 184f, 148f, body, 590f)
        drawSportsbookStatusBadge(canvas, width - 264f, 58f, ticket.status, green, gold, body)

        var top = headerHeight.toFloat()
        fill.color = surface
        canvas.drawRect(0f, top, width.toFloat(), top + ticketBandHeight, fill)
        small.color = muted
        small.textSize = 22f
        small.textAlign = Paint.Align.LEFT
        canvas.drawText("NUMERO DE TICKET", 38f, top + 46f, small)
        mono.color = blue
        mono.textSize = 46f
        mono.textAlign = Paint.Align.LEFT
        canvas.drawText(ticket.ticketCode.take(24), 38f, top + 104f, mono)
        small.textAlign = Paint.Align.RIGHT
        canvas.drawText("VENDEDOR", width - 38f, top + 46f, small)
        body.color = navy
        body.textSize = 28f
        body.textAlign = Paint.Align.RIGHT
        drawTextFitted(canvas, ticket.sellerUsername.ifBlank { ticket.bancaName }, width - 38f, top + 102f, body, 340f)
        canvas.drawLine(0f, top + ticketBandHeight, width.toFloat(), top + ticketBandHeight, stroke)
        top += ticketBandHeight

        body.textAlign = Paint.Align.LEFT
        body.color = navy
        body.textSize = 34f
        canvas.drawText("Selecciones", 38f, top + 58f, body)
        small.textAlign = Paint.Align.RIGHT
        small.color = muted
        canvas.drawText("${ticket.legs.size} total", width - 38f, top + 58f, small)
        top += 86f
        val visibleLegs = ticket.legs.ifEmpty {
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
        visibleLegs.forEachIndexed { index, leg ->
            val rowTop = top + (index * legHeight)
            fill.color = if (index % 2 == 0) Color.WHITE else surface
            canvas.drawRoundRect(RectF(38f, rowTop, width - 38f, rowTop + legHeight - 12f), 18f, 18f, fill)
            canvas.drawRoundRect(RectF(38f, rowTop, width - 38f, rowTop + legHeight - 12f), 18f, 18f, stroke)
            body.color = navy
            body.textSize = 30f
            body.textAlign = Paint.Align.LEFT
            drawTextFitted(canvas, leg.eventLabel.ifBlank { "Evento" }, 64f, rowTop + 42f, body, 620f)
            small.color = muted
            small.textSize = 23f
            canvas.drawText("${leg.marketTitle} · ${leg.selectionLabel}", 64f, rowTop + 86f, small)
            mono.color = green
            mono.textSize = 34f
            mono.textAlign = Paint.Align.RIGHT
            canvas.drawText("%.2f".format(Locale.US, leg.decimalOdds), width - 64f, rowTop + 70f, mono)
        }
        top += visibleLegs.size * legHeight

        fill.color = Color.parseColor("#0B2A4A")
        canvas.drawRect(0f, top, width.toFloat(), top + totalHeight, fill)
        small.textAlign = Paint.Align.LEFT
        small.color = Color.parseColor("#F8DF8C")
        small.textSize = 24f
        canvas.drawText("MONTO APOSTADO", 42f, top + 56f, small)
        body.color = Color.WHITE
        body.textSize = 42f
        canvas.drawText("$ ${formatMoney(ticket.stake)}", 42f, top + 118f, body)
        small.textAlign = Paint.Align.RIGHT
        canvas.drawText("PAGO POSIBLE", width - 42f, top + 56f, small)
        mono.color = gold
        mono.textSize = 58f
        mono.textAlign = Paint.Align.RIGHT
        canvas.drawText("$ ${formatMoney(ticket.potentialPayout)}", width - 42f, top + 122f, mono)
        top += totalHeight

        fill.color = Color.parseColor("#071A44")
        canvas.drawRect(0f, top, width.toFloat(), top + footerHeight, fill)
        small.color = Color.WHITE
        small.textAlign = Paint.Align.CENTER
        small.textSize = 24f
        canvas.drawText(
            "Ticket deportivo separado de loteria · Validar antes de pagar",
            width / 2f,
            top + 56f,
            small,
        )
        small.color = Color.parseColor("#F8DF8C")
        canvas.drawText("${ticket.bancaName.ifBlank { bancaName }} · ${ticket.ticketType.uppercase(Locale.US)}", width / 2f, top + 96f, small)
        return bitmap
    }

    private fun drawSportsbookStatusBadge(
        canvas: Canvas,
        left: Float,
        top: Float,
        status: SportsbookTicketStatus,
        green: Int,
        gold: Int,
        paint: Paint,
    ) {
        val color = when (status) {
            SportsbookTicketStatus.WON, SportsbookTicketStatus.PAID -> green
            SportsbookTicketStatus.LOST, SportsbookTicketStatus.VOID -> Color.parseColor("#DC2626")
            else -> gold
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(left, top, left + 220f, top + 64f), 22f, 22f, fill)
        paint.color = Color.WHITE
        paint.textSize = 27f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(status.wireValue.uppercase(Locale.US), left + 110f, top + 42f, paint)
    }

    internal fun resolveResultsWhatsAppRowLayout(): ResultsWhatsAppRowLayout {
        return ResultsWhatsAppRowLayout(
            rowHeight = 238,
            logoLeft = 58f,
            logoTopOffset = 42f,
            logoWidth = 240f,
            logoHeight = 144f,
            contentLeft = 344f,
            nameY = 76f,
            metaY = 124f,
            primaryCenterY = 124f,
            pickStartY = 166f,
        )
    }

    internal fun resolveResultsWhatsAppBallColor(): Int = RESULTS_WHATSAPP_BALL_GREEN

    private fun resolveResultsWhatsAppBallShadowColor(): Int = Color.argb(150, 22, 163, 74)

    internal fun financeReportImageSegmentLabels(): List<String> = listOf("Resumen", "Transparencia", "Detalle", "Compartir")

    internal fun financeReportImageSectionLabels(periodReport: Boolean): List<String> {
        return if (periodReport) {
            listOf("Resumen compacto", "Transparencia", "Desglose por dia")
        } else {
            listOf("Resumen compacto", "Clasificacion", "Alertas", "Cierre")
        }
    }

    internal fun financeReportImageResultLabel(value: Double): String {
        return when {
            value > 0.0 -> "Beneficio"
            value < 0.0 -> "Pérdida"
            else -> "Neutro"
        }
    }

    fun renderLotteryResultsViewBitmap(
        data: LotteryResultsViewData,
        context: Context? = null,
    ): Bitmap {
        val width = 1600
        val height = 1600
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(
                    Color.parseColor("#020617"),
                    Color.parseColor("#071A44"),
                    Color.parseColor("#030712"),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        drawPosterGlow(canvas, 230f, 250f, 380f, Color.parseColor("#1D4ED8"), 82)
        drawPosterGlow(canvas, 1290f, 220f, 430f, Color.parseColor("#F59E0B"), 64)
        drawPosterGlow(canvas, 1320f, 1120f, 420f, Color.parseColor("#DC2626"), 58)

        val logoBitmap = context?.let { LotteryLogoBitmapLoader.load(it, data.logoAssetPath) }
        if (logoBitmap != null) {
            drawBrandLogo(canvas, logoBitmap, 66f, 70f, 284f, 218f, Color.argb(112, 245, 158, 11))
        } else {
            drawPosterFallbackLogo(canvas, 86f, 84f, 178f, "LN")
        }

        text.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD_ITALIC)
        text.textSize = 86f
        text.color = Color.WHITE
        text.setShadowLayer(14f, 0f, 8f, Color.argb(190, 0, 0, 0))
        drawTextFitted(canvas, "RESULTADOS", 430f, 150f, text, 1040f)
        drawPosterGoldText(canvas, data.nombreLoteria.uppercase(Locale.US), 430f, 262f, 1030f, 96f)
        text.clearShadowLayer()

        drawPosterInfoBar(canvas, data.fecha, data.hora)

        val cardTop = 690f
        val prizeNumbers = listOfNotNull(
            data.primerPremio,
            data.segundoPremio,
            data.tercerPremio,
            data.cuartoPremio,
        ).filter { it.isNotBlank() }
        val prizeCount = prizeNumbers.size.coerceAtLeast(1)
        val cardWidth = if (prizeCount >= 4) 320f else 430f
        val cardHeight = 520f
        val gap = if (prizeCount >= 4) 34f else 52f
        val left = 104f
        val prizeColors = listOf(
            Color.parseColor("#F59E0B") to Color.parseColor("#3B2100"),
            Color.parseColor("#38BDF8") to Color.parseColor("#082F49"),
            Color.parseColor("#F97316") to Color.parseColor("#450A0A"),
            Color.parseColor("#22C55E") to Color.parseColor("#052E16"),
        )
        prizeNumbers.forEachIndexed { index, number ->
            val colors = prizeColors[index.coerceAtMost(prizeColors.lastIndex)]
            drawPosterPrizeCard(
                canvas = canvas,
                left = left + ((cardWidth + gap) * index),
                top = cardTop,
                width = cardWidth,
                height = cardHeight,
                rank = (index + 1).toString(),
                label = data.prizeLabels.getOrElse(index) { "PREMIO ${index + 1}" },
                number = number,
                mainColor = colors.first,
                darkColor = colors.second,
            )
        }

        fill.shader = android.graphics.LinearGradient(
            0f,
            1378f,
            width.toFloat(),
            1378f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.parseColor("#F59E0B"),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.5f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(110f, 1374f, width - 110f, 1384f, fill)
        fill.shader = null
        text.typeface = android.graphics.Typeface.DEFAULT_BOLD
        text.textAlign = Paint.Align.CENTER
        text.textSize = 48f
        text.color = Color.parseColor("#F8D568")
        text.setShadowLayer(9f, 0f, 4f, Color.argb(180, 245, 158, 11))
        canvas.drawText("RESULTADOS OFICIALES", width / 2f, 1448f, text)
        text.clearShadowLayer()
        text.textSize = 30f
        text.color = Color.parseColor("#CBD5E1")
        canvas.drawText("${data.nombreLoteria} · ${data.fecha}", width / 2f, 1518f, text)

        stroke.color = Color.argb(72, 245, 158, 11)
        stroke.strokeWidth = 5f
        canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 42f, 42f, stroke)
        return bitmap
    }

    internal fun resolveResultsHeaderLogoUri(payload: ResultsSharePayload): String? {
        return payload.bancaLogoUri.trim().takeIf { it.isNotBlank() }
    }

    fun renderFinanceBitmap(
        bancaName: String,
        dayKey: String,
        summary: FinanceSummary,
        actorLabel: String? = null,
        turnoLabel: String? = null,
        turnoDiff: Double? = null,
        cashEntered: Double? = null,
    ): Bitmap {
        val width = 1400
        val headerHeight = 248
        val heroHeight = 248
        val summaryHeight = 474
        val classifyHeight = 254
        val alertsHeight = kotlin.math.max(176, 110 + (summary.alertas.size * 78))
        val closeHeight = if (turnoLabel != null) 246 else 0
        val footerHeight = 100
        val height = headerHeight + heroHeight + summaryHeight + classifyHeight + alertsHeight + closeHeight + footerHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F3F6FB"))

        val navy = Color.parseColor("#0F172A")
        val blue = Color.parseColor("#1D4ED8")
        val green = Color.parseColor("#166534")
        val amber = Color.parseColor("#B45309")
        val violet = Color.parseColor("#4338CA")
        val red = Color.parseColor("#B91C1C")
        val muted = Color.parseColor("#64748B")
        val outline = Color.parseColor("#DBE4EE")
        val white = Color.WHITE

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = outline
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 64f
            isFakeBoldText = true
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(214, 255, 255, 255)
            textSize = 28f
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 30f
            isFakeBoldText = true
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 24f
        }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 40f
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#123A66") }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), headerPaint)
        drawHeaderMedallion(canvas, 38f, 34f, 92f, white, Color.parseColor("#123A66"))
        canvas.drawText("Cuadre de Caja", 154f, 92f, title)
        title.textSize = 38f
        canvas.drawText(bancaName, 154f, 148f, title)
        canvas.drawText(dayKey, 154f, 196f, subtitle)
        drawReportStatusPill(canvas, width - 352f, 46f, "Sincronizado", green)
        actorLabel?.takeIf { it.isNotBlank() }?.let {
            drawHeaderBadge(canvas, width - 352f, 118f, 296f, 54f, Color.argb(42, 255, 255, 255), white, it, Color.argb(58, 255, 255, 255))
        }
        drawReportSegmentBar(canvas, 28f, headerHeight - 34f, width - 56f, financeReportImageSegmentLabels().take(3), selectedIndex = 0)

        val heroTop = headerHeight + 22f
        val netLabel = financeReportImageResultLabel(summary.netoProyectado)
        drawFinanceMetricCard(canvas, 28f, heroTop, 320f, 196f, "Ventas", formatMoney(summary.ventas), green)
        drawFinanceMetricCard(canvas, 362f, heroTop, 320f, 196f, "Caja disp.", formatMoney(summary.cajaDisponible), violet)
        drawFinanceMetricCard(canvas, 696f, heroTop, 320f, 196f, "Premios pagados", formatMoney(summary.premiosPagados), amber)
        drawFinanceMetricCard(
            canvas,
            1030f,
            heroTop,
            342f,
            196f,
            netLabel,
            signedAmount(summary.netoProyectado),
            if (summary.netoProyectado >= 0) green else red,
        )

        val summaryTop = heroTop + heroHeight
        drawSectionCard(canvas, 28f, summaryTop, width - 28f, summaryTop + summaryHeight, "Resumen compacto", stroke)
        drawFinanceRow(canvas, 56f, summaryTop + 88f, "Tickets", summary.ticketsCount.toString(), body, mono)
        drawFinanceRow(canvas, 56f, summaryTop + 144f, "Promedio", "$ ${formatMoney(summary.avgTicket)}", body, mono)
        drawFinanceRow(canvas, 56f, summaryTop + 200f, "Activos", summary.activos.toString(), body, mono)
        drawFinanceRow(canvas, 56f, summaryTop + 256f, "Ganadores", summary.ganadores.toString(), body, mono)
        drawFinanceRow(canvas, 56f, summaryTop + 312f, "Pagados", summary.pagados.toString(), body, mono)
        drawFinanceRow(canvas, 56f, summaryTop + 368f, "Recargas", "$ ${formatMoney(summary.recargas)}", body, mono)
        drawFinanceRow(canvas, 720f, summaryTop + 88f, "Comision", "$ ${formatMoney(summary.comision)}", body, mono)
        drawFinanceRow(canvas, 720f, summaryTop + 144f, "Pendientes", "$ ${formatMoney(summary.premiosPendientes)}", body, mono)
        drawFinanceRow(canvas, 720f, summaryTop + 200f, "Anulados", "${summary.anuladosCount} · $ ${formatMoney(summary.anuladosMonto)}", body, mono)
        drawFinanceRow(canvas, 720f, summaryTop + 256f, "Invalidos", "${summary.invalidosCount} · $ ${formatMoney(summary.invalidosMonto)}", body, mono)
        drawFinanceRow(canvas, 720f, summaryTop + 312f, "Fuera de finanza", "${summary.fueraDeFinanzaCount} · $ ${formatMoney(summary.fueraDeFinanzaMonto)}", body, mono)
        drawFinanceRow(canvas, 720f, summaryTop + 368f, "Caja disponible", "$ ${formatMoney(summary.cajaDisponible)}", body, mono)

        var top = summaryTop + summaryHeight + 20f
        drawSectionCard(canvas, 28f, top, width - 28f, top + classifyHeight, "Clasificacion de tickets", stroke)
        drawFinanceRow(canvas, 56f, top + 88f, "Validos", summary.ticketsCount.toString(), body, mono)
        drawFinanceRow(canvas, 56f, top + 144f, "Ganadores", summary.ganadores.toString(), body, mono)
        drawFinanceRow(canvas, 720f, top + 88f, "Pagados", summary.pagados.toString(), body, mono)
        drawFinanceRow(canvas, 720f, top + 144f, "Nulos + invalidos", "${summary.fueraDeFinanzaCount}", body, mono)
        top += classifyHeight + 20f

        drawSectionCard(canvas, 28f, top, width - 28f, top + alertsHeight, "Alertas", stroke)
        if (summary.alertas.isEmpty()) {
            small.color = muted
            canvas.drawText("Sin alertas operativas en este corte.", 56f, top + 92f, small)
        } else {
            var alertTop = top + 52f
            summary.alertas.forEach { alert ->
                val tone = when (alert.tone) {
                    FinanceAlertTone.DANGER -> red
                    FinanceAlertTone.WARNING -> amber
                    FinanceAlertTone.NOTICE -> Color.parseColor("#92400E")
                }
                fill.color = Color.argb(26, Color.red(tone), Color.green(tone), Color.blue(tone))
                val rect = RectF(50f, alertTop, width - 50f, alertTop + 58f)
                canvas.drawRoundRect(rect, 16f, 16f, fill)
                body.color = tone
                body.textSize = 24f
                canvas.drawText(alert.label, 68f, alertTop + 36f, body)
                small.color = navy
                small.textSize = 22f
                canvas.drawText(alert.text, 240f, alertTop + 36f, small)
                alertTop += 72f
            }
        }
        top += alertsHeight + 20f

        if (turnoLabel != null) {
            drawSectionCard(canvas, 28f, top, width - 28f, top + closeHeight, "Cierre de turno", stroke)
            drawFinanceRow(canvas, 56f, top + 88f, "Turno", turnoLabel, body, mono)
            cashEntered?.let {
                drawFinanceRow(canvas, 56f, top + 144f, "Efectivo digitado", "$ ${formatMoney(it)}", body, mono)
            }
            turnoDiff?.let {
                drawFinanceRow(canvas, 720f, top + 88f, "Diferencia", signedAmount(it), body, mono)
            }
            top += closeHeight + 20f
        }

        fill.color = navy
        canvas.drawRect(0f, height - footerHeight.toFloat(), width.toFloat(), height.toFloat(), fill)
        small.color = Color.parseColor("#CBD5E1")
        small.textAlign = Paint.Align.CENTER
        canvas.drawText("Cuadre generado en Kotlin · LotteryNet Pro", width / 2f, height - 38f, small)
        return bitmap
    }

    fun renderFinancePeriodBitmap(
        bancaName: String,
        report: FinancePeriodReport,
        actorLabel: String? = null,
    ): Bitmap {
        val visibleRows = report.rows.take(10)
        val width = 1400
        val headerHeight = 230
        val heroHeight = 228
        val transparencyHeight = 212
        val rowsHeight = 132 + (visibleRows.size * 96)
        val footerHeight = 108
        val height = headerHeight + heroHeight + transparencyHeight + rowsHeight + footerHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F3F6FB"))

        val navy = Color.parseColor("#0F172A")
        val blue = Color.parseColor("#1747D1")
        val green = Color.parseColor("#166534")
        val amber = Color.parseColor("#B45309")
        val violet = Color.parseColor("#4338CA")
        val red = Color.parseColor("#B91C1C")
        val outline = Color.parseColor("#DBE4EE")
        val white = Color.WHITE
        fun resultColor(value: Double): Int = when {
            value > 0.0 -> green
            value < 0.0 -> red
            else -> navy
        }

        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = outline
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 58f
            isFakeBoldText = true
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(214, 255, 255, 255)
            textSize = 28f
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 28f
            isFakeBoldText = true
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 22f
        }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 30f
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#123A66") }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), headerPaint)
        drawHeaderMedallion(canvas, 38f, 34f, 92f, white, Color.parseColor("#123A66"))
        canvas.drawText("Reporte por Periodo", 154f, 88f, title)
        title.textSize = 34f
        canvas.drawText("${report.fromDayKey} a ${report.toDayKey}", 154f, 140f, title)
        canvas.drawText(bancaName, 154f, 184f, subtitle)
        drawReportStatusPill(canvas, width - 352f, 42f, "Actualizado", green)
        actorLabel?.takeIf { it.isNotBlank() }?.let {
            drawHeaderBadge(canvas, width - 352f, 112f, 296f, 54f, Color.argb(42, 255, 255, 255), white, it, Color.argb(58, 255, 255, 255))
        }
        drawReportSegmentBar(canvas, 28f, headerHeight - 34f, width - 56f, financeReportImageSegmentLabels().take(3), selectedIndex = 1)

        val heroTop = headerHeight + 20f
        val periodNet = resolveOperationalReportNet(report.summary)
        drawFinanceMetricCard(canvas, 28f, heroTop, 320f, 180f, "Ventas", formatMoney(report.summary.ventas), green)
        drawFinanceMetricCard(canvas, 362f, heroTop, 320f, 180f, "Caja", formatMoney(report.summary.cajaDisponible), violet)
        drawFinanceMetricCard(canvas, 696f, heroTop, 320f, 180f, "Recargas", formatMoney(report.summary.recargas), amber)
        drawFinanceMetricCard(canvas, 1030f, heroTop, 342f, 180f, financeReportImageResultLabel(periodNet), signedAmount(periodNet), resultColor(periodNet))

        val transparencyTop = heroTop + heroHeight
        drawSectionCard(canvas, 28f, transparencyTop, width - 28f, transparencyTop + transparencyHeight - 20f, "Transparencia", stroke)
        val scope = actorLabel?.takeIf { it.isNotBlank() } ?: report.scope.actorDisplay ?: "Global"
        drawFinanceRow(canvas, 56f, transparencyTop + 82f, "Alcance", scope, body, mono)
        drawFinanceRow(canvas, 56f, transparencyTop + 138f, financeReportImageResultLabel(periodNet), "$ ${formatMoney(periodNet)}", body, mono, resultColor(periodNet))
        drawFinanceRow(canvas, 720f, transparencyTop + 82f, "Comision vendedor", "$ ${formatMoney(report.summary.comision)} (${operationalReportCommissionPercent(report.summary)})", body, mono, red)
        drawFinanceRow(canvas, 720f, transparencyTop + 138f, "Premios pagados", "$ ${formatMoney(report.summary.premiosPagados)}", body, mono, amber)
        small.color = amber
        small.textSize = 22f
        canvas.drawText("Premios pendientes: $ ${formatMoney(report.summary.premiosPendientes)}", 56f, transparencyTop + 182f, small)
        small.color = red
        canvas.drawText("Anulados/invalidos fuera de caja: $ ${formatMoney(report.summary.fueraDeFinanzaMonto)}", 720f, transparencyTop + 182f, small)

        val tableTop = transparencyTop + transparencyHeight
        drawSectionCard(canvas, 28f, tableTop, width - 28f, tableTop + rowsHeight, "Desglose por dia", stroke)
        small.color = Color.parseColor("#475569")
        canvas.drawText("DIA", 58f, tableTop + 86f, small)
        canvas.drawText("VENTAS", 360f, tableTop + 86f, small)
        canvas.drawText("PREMIOS", 620f, tableTop + 86f, small)
        canvas.drawText("RESULTADO", 900f, tableTop + 86f, small)
        canvas.drawText("TICKETS", 1290f, tableTop + 86f, small)
        visibleRows.forEachIndexed { index, row ->
            val y = tableTop + 138f + (index * 96f)
            val rowNet = resolveOperationalReportNet(row.summary)
            if (index % 2 == 0) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
                canvas.drawRoundRect(RectF(44f, y - 44f, width - 44f, y + 28f), 14f, 14f, fill)
            }
            body.textAlign = Paint.Align.LEFT
            mono.textAlign = Paint.Align.LEFT
            canvas.drawText(row.dayKey, 58f, y, body)
            mono.color = green
            canvas.drawText(formatMoney(row.summary.ventas), 360f, y, mono)
            mono.color = amber
            canvas.drawText(formatMoney(row.summary.premiosPagados), 620f, y, mono)
            mono.color = resultColor(rowNet)
            canvas.drawText(signedAmount(rowNet), 900f, y, mono)
            mono.color = navy
            mono.textAlign = Paint.Align.RIGHT
            canvas.drawText(row.summary.ticketsCount.toString(), width - 70f, y, mono)
        }
        val footerTop = tableTop + rowsHeight
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy }
        canvas.drawRect(0f, footerTop, width.toFloat(), footerTop + footerHeight, fill)
        body.color = Color.parseColor("#CBD5E1")
        body.textAlign = Paint.Align.CENTER
        body.textSize = 24f
        val overflow = if (report.rows.size > visibleRows.size) " · +${report.rows.size - visibleRows.size} dias mas" else ""
        canvas.drawText("${financeReportImageResultLabel(periodNet)} ${formatMoney(periodNet)} · Comision ${formatMoney(report.summary.comision)} (${operationalReportCommissionPercent(report.summary)}) · Premios ${formatMoney(report.summary.premiosPagados)}$overflow", width / 2f, footerTop + 62f, body)
        return bitmap
    }

    fun renderCashierDetailBitmap(
        bancaName: String,
        dayKey: String,
        actorLabel: String,
        actorUser: String,
        summary: FinanceSummary,
        tickets: List<TicketRecord>,
    ): Bitmap {
        val width = 1400
        val headerHeight = 214
        val metricsHeight = 214
        val summaryHeight = 258
        val visibleTickets = tickets.take(10)
        val ticketsHeight = kotlin.math.max(220, 128 + (visibleTickets.size * 82))
        val footerHeight = 96
        val height = headerHeight + metricsHeight + summaryHeight + ticketsHeight + footerHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F4F1EA"))

        val ink = Color.parseColor("#1F1A17")
        val coffee = Color.parseColor("#5B4634")
        val amber = Color.parseColor("#A35A1F")
        val sand = Color.parseColor("#E9DFC9")
        val surface = Color.WHITE
        val outline = Color.parseColor("#D7C9B6")
        val muted = Color.parseColor("#776A5C")
        val green = Color.parseColor("#2F6B3B")
        val red = Color.parseColor("#8C2F26")

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = outline
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f
            isFakeBoldText = true
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(218, 255, 244, 230)
            textSize = 24f
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 26f
            isFakeBoldText = true
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 22f
        }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 30f
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                width.toFloat(),
                headerHeight.toFloat(),
                Color.parseColor("#6E4E2E"),
                Color.parseColor("#2E2119"),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), headerPaint)
        fill.color = sand
        canvas.drawRect(0f, 0f, width.toFloat(), 12f, fill)
        fill.color = amber
        canvas.drawRect(0f, headerHeight - 10f, width.toFloat(), headerHeight.toFloat(), fill)
        title.textSize = 54f
        canvas.drawText("Detalle por cajero", 42f, 82f, title)
        title.textSize = if (actorLabel.length > 18) 42f else 48f
        canvas.drawText(actorLabel, 42f, 142f, title)
        subtitle.textSize = 24f
        canvas.drawText("${actorUser.ifBlank { "sin usuario" }} · $bancaName · $dayKey", 42f, 182f, subtitle)
        drawHeaderBadge(canvas, width - 306f, 46f, 264f, 58f, Color.argb(38, 255, 255, 255), Color.WHITE, "${summary.ticketsCount} tickets", Color.argb(52, 255, 255, 255))
        drawHeaderBadge(canvas, width - 306f, 118f, 264f, 58f, Color.argb(38, 255, 255, 255), Color.WHITE, miniMoney(summary.cajaDisponible), Color.argb(52, 255, 255, 255))

        val metricsTop = headerHeight + 18f
        drawFinanceMetricCard(canvas, 28f, metricsTop, 320f, 168f, "Ventas", miniMoney(summary.ventas), amber)
        drawFinanceMetricCard(canvas, 362f, metricsTop, 320f, 168f, "Recargas", miniMoney(summary.recargas), coffee)
        drawFinanceMetricCard(canvas, 696f, metricsTop, 320f, 168f, "Caja", miniMoney(summary.cajaDisponible), green)
        drawFinanceMetricCard(canvas, 1030f, metricsTop, 342f, 168f, "Pendiente", miniMoney(summary.premiosPendientes), red)

        val summaryTop = metricsTop + metricsHeight
        drawSectionCard(canvas, 28f, summaryTop, width - 28f, summaryTop + summaryHeight, "Resumen operativo", stroke)
        drawFinanceRow(canvas, 56f, summaryTop + 88f, "Tickets", summary.ticketsCount.toString(), body, mono)
        drawFinanceRow(canvas, 56f, summaryTop + 142f, "Pagados", summary.pagados.toString(), body, mono)
        drawFinanceRow(canvas, 56f, summaryTop + 196f, "Anulados", summary.anuladosCount.toString(), body, mono)
        drawFinanceRow(canvas, 720f, summaryTop + 88f, "Promedio", miniMoney(summary.avgTicket), body, mono)
        drawFinanceRow(canvas, 720f, summaryTop + 142f, "Comisión", miniMoney(summary.comision), body, mono)
        drawFinanceRow(canvas, 720f, summaryTop + 196f, "Neto proyectado", miniMoney(summary.netoProyectado), body, mono)

        val ticketsTop = summaryTop + summaryHeight + 18f
        drawSectionCard(canvas, 28f, ticketsTop, width - 28f, ticketsTop + ticketsHeight, "Tickets del día", stroke)
        if (visibleTickets.isEmpty()) {
            body.color = muted
            body.textSize = 28f
            canvas.drawText("Sin tickets registrados para este cajero hoy.", 56f, ticketsTop + 128f, body)
        } else {
            val headerY = ticketsTop + 88f
            small.color = muted
            small.textAlign = Paint.Align.LEFT
            canvas.drawText("Serial", 56f, headerY, small)
            canvas.drawText("Hora", 412f, headerY, small)
            canvas.drawText("Estado", 612f, headerY, small)
            small.textAlign = Paint.Align.RIGHT
            canvas.drawText("Monto", width - 56f, headerY, small)
            visibleTickets.forEachIndexed { index, ticket ->
                val rowTop = ticketsTop + 108f + (index * 82f)
                if (index % 2 == 0) {
                    fill.color = surface
                    canvas.drawRoundRect(RectF(42f, rowTop - 34f, width - 42f, rowTop + 26f), 16f, 16f, fill)
                }
                val status = ticket.status.lowercase(Locale.getDefault())
                val statusColor = if (isPaidTicketStatus(status)) {
                    green
                } else {
                    when (status) {
                        "winner" -> amber
                        "voided", "invalid" -> red
                        else -> amber
                    }
                }
                body.color = ink
                body.textAlign = Paint.Align.LEFT
                body.textSize = 24f
                canvas.drawText(ticket.serial ?: ticket.id, 56f, rowTop, body)
                small.color = coffee
                small.textAlign = Paint.Align.LEFT
                canvas.drawText(formatTimeForTicket(ticket.createdAtEpochMs), 412f, rowTop, small)
                drawBadge(canvas, 586f, rowTop - 28f, cashierTicketState(status), statusColor, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = statusColor
                    textSize = 18f
                    isFakeBoldText = true
                })
                mono.color = ink
                mono.textAlign = Paint.Align.RIGHT
                mono.textSize = 26f
                canvas.drawText(miniMoney(ticket.total), width - 56f, rowTop, mono)
            }
            if (tickets.size > visibleTickets.size) {
                small.color = muted
                small.textAlign = Paint.Align.RIGHT
                canvas.drawText("+${tickets.size - visibleTickets.size} ticket(s) más", width - 56f, ticketsTop + ticketsHeight - 32f, small)
            }
        }

        val footerTop = ticketsTop + ticketsHeight
        fill.color = coffee
        canvas.drawRect(0f, footerTop, width.toFloat(), footerTop + footerHeight, fill)
        small.color = Color.parseColor("#F6EEDF")
        small.textAlign = Paint.Align.CENTER
        small.textSize = 22f
        canvas.drawText("Salida operativa nativa: imprimir, WhatsApp, compartir y guardar.", width / 2f, footerTop + 40f, small)
        canvas.drawText("LotteryNet Android · admin operativo local", width / 2f, footerTop + 72f, small)
        return bitmap
    }

    fun renderAdminMonitorBitmap(
        bancaName: String,
        dayKey: String,
        bancaSummary: FinanceSummary,
        rows: List<String>,
    ): Bitmap {
        val visibleRows = rows.take(10)
        val width = 1400
        val headerHeight = 230
        val heroHeight = 220
        val rowsHeight = 150 + (visibleRows.size * 104)
        val footerHeight = 108
        val height = headerHeight + heroHeight + rowsHeight + footerHeight
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F3F6FB"))

        val navy = Color.parseColor("#0F172A")
        val blue = Color.parseColor("#1747D1")
        val green = Color.parseColor("#166534")
        val amber = Color.parseColor("#B45309")
        val outline = Color.parseColor("#DBE4EE")
        val white = Color.WHITE
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = outline
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 58f
            isFakeBoldText = true
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(214, 255, 255, 255)
            textSize = 28f
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 26f
            isFakeBoldText = true
        }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                0f,
                headerHeight.toFloat(),
                Color.parseColor("#1747D1"),
                Color.parseColor("#0D2FA1"),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), headerPaint)
        drawHeaderMedallion(canvas, 38f, 34f, 92f, white, Color.parseColor("#1747D1"))
        canvas.drawText("Monitor Admin", 154f, 88f, title)
        title.textSize = 34f
        canvas.drawText(bancaName, 154f, 140f, title)
        canvas.drawText(dayKey, 154f, 184f, subtitle)
        drawHeaderBadge(canvas, width - 338f, 46f, 296f, 58f, Color.argb(34, 255, 255, 255), white, "${rows.size} cajeros", Color.argb(48, 255, 255, 255))
        drawHeaderBadge(canvas, width - 338f, 118f, 296f, 58f, Color.argb(34, 255, 255, 255), white, "Ordenado por cajero", Color.argb(48, 255, 255, 255))

        val heroTop = headerHeight + 20f
        drawFinanceMetricCard(canvas, 28f, heroTop, 320f, 172f, "Ventas banca", formatMoney(bancaSummary.ventas), green)
        drawFinanceMetricCard(canvas, 362f, heroTop, 320f, 172f, "Caja banca", formatMoney(bancaSummary.cajaDisponible), navy)
        drawFinanceMetricCard(canvas, 696f, heroTop, 320f, 172f, "Recargas", formatMoney(bancaSummary.recargas), amber)
        drawFinanceMetricCard(canvas, 1030f, heroTop, 342f, 172f, "Tickets", bancaSummary.ticketsCount.toString(), navy)

        val tableTop = heroTop + heroHeight
        drawSectionCard(canvas, 28f, tableTop, width - 28f, tableTop + rowsHeight, "Cajeros ordenados", stroke)
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 21f
            isFakeBoldText = true
        }
        small.textAlign = Paint.Align.LEFT
        canvas.drawText("Cajero", 58f, tableTop + 92f, small)
        canvas.drawText("Estado", 472f, tableTop + 92f, small)
        canvas.drawText("Ventas", 650f, tableTop + 92f, small)
        canvas.drawText("Caja", 868f, tableTop + 92f, small)
        canvas.drawText("Resultado", 1084f, tableTop + 92f, small)
        visibleRows.forEachIndexed { index, row ->
            val parts = row.split(" · ")
            val cashier = parts.getOrNull(0).orEmpty().ifBlank { row }
            val status = parts.getOrNull(1).orEmpty().ifBlank { "-" }
            val sales = parts.getOrNull(2)?.removePrefix("Ventas ") ?: "-"
            val cash = parts.getOrNull(3)?.removePrefix("Caja ") ?: "-"
            val result = parts.getOrNull(4) ?: "-"
            val y = tableTop + 142f + (index * 104f)
            if (index % 2 == 0) {
                val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
                canvas.drawRoundRect(RectF(44f, y - 44f, width - 44f, y + 42f), 16f, 16f, fill)
            }
            body.textAlign = Paint.Align.LEFT
            body.textSize = 25f
            body.color = navy
            drawTextFitted(canvas, cashier, 58f, y - 10f, body, 370f)
            small.color = Color.parseColor("#64748B")
            small.textSize = 18f
            drawTextFitted(canvas, row.substringAfter(" · ", "").substringBefore(" · Ventas"), 58f, y + 22f, small, 370f)
            drawBadge(
                canvas,
                462f,
                y - 38f,
                status,
                if (status.equals("Activo", ignoreCase = true)) green else amber,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (status.equals("Activo", ignoreCase = true)) green else amber
                    textSize = 18f
                    isFakeBoldText = true
                },
            )
            mono.textAlign = Paint.Align.LEFT
            mono.textSize = 24f
            mono.color = green
            drawTextFitted(canvas, sales, 650f, y, mono, 190f)
            mono.color = navy
            drawTextFitted(canvas, cash, 868f, y, mono, 190f)
            mono.color = if (result.contains("Perd", ignoreCase = true) || result.contains("-", ignoreCase = true)) Color.parseColor("#B91C1C") else green
            drawTextFitted(canvas, result, 1084f, y, mono, 250f)
        }
        val footerTop = tableTop + rowsHeight
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy }
        canvas.drawRect(0f, footerTop, width.toFloat(), footerTop + footerHeight, fill)
        body.color = amber
        body.textAlign = Paint.Align.CENTER
        body.textSize = 24f
        val overflow = if (rows.size > visibleRows.size) " · +${rows.size - visibleRows.size} cajeros más" else ""
        canvas.drawText("Premios pendientes ${miniMoney(bancaSummary.premiosPendientes)} · snapshot operativo$overflow", width / 2f, footerTop + 62f, body)
        return bitmap
    }

    private fun renderResultsPageBitmap(
        payload: ResultsSharePayload,
        pageIndex: Int,
        pageCount: Int,
        context: Context? = null,
    ): Bitmap {
        val width = 1600
        val headerHeight = 272
        val footerHeight = 112
        val rowLayout = resolveResultsWhatsAppRowLayout()
        val rowHeight = rowLayout.rowHeight
        val gap = 22
        val contentHeight = headerHeight + 32 + (payload.rows.size * (rowHeight + gap)) + footerHeight
        val height = kotlin.math.max(1600, contentHeight)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                intArrayOf(
                    Color.parseColor("#020617"),
                    Color.parseColor("#071A44"),
                    Color.parseColor("#030712"),
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
        drawPosterGlow(canvas, 220f, 170f, 360f, Color.parseColor("#F59E0B"), 68)
        drawPosterGlow(canvas, width - 180f, 380f, 440f, Color.parseColor("#1D4ED8"), 58)
        drawPosterGlow(canvas, width - 220f, height - 240f, 420f, Color.parseColor("#DC2626"), 48)

        val outline = Color.argb(118, 248, 213, 104)
        val slate = Color.WHITE
        val muted = Color.parseColor("#CBD5E1")
        val white = Color.WHITE

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = outline
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 68f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(214, 255, 255, 255)
            textSize = 28f
            textAlign = Paint.Align.LEFT
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slate
            textSize = 42f
            isFakeBoldText = true
        }
        val ball = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = resolveResultsWhatsAppBallColor()
            setShadowLayer(10f, 0f, 0f, resolveResultsWhatsAppBallShadowColor())
        }
        val ballText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = white
            textSize = 42f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val prizeLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 22f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3F2B00")
            textSize = 24f
            isFakeBoldText = true
        }

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                width.toFloat(),
                headerHeight.toFloat(),
                intArrayOf(
                    Color.parseColor("#020617"),
                    Color.parseColor("#12306A"),
                    Color.parseColor("#3B2100"),
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), headerPaint)
        val brandLogo = resolveResultsHeaderLogoUri(payload)?.let { logoUri ->
            context?.let { loadBitmapFromUri(it, logoUri) }
        }
        if (brandLogo != null) {
            drawBrandLogo(canvas, brandLogo, 36f, 34f, 104f, 104f, Color.argb(90, 255, 255, 255))
        }
        val headerTextX = if (brandLogo != null) 164f else 44f
        drawGoldenHeaderTitle(canvas, resolveResultsHeaderHighlight(payload.dateLabel), headerTextX, 88f)
        title.textSize = 40f
        title.color = white
        canvas.drawText(payload.bancaName, headerTextX, 150f, title)
        canvas.drawText(payload.dateLabel, headerTextX, 202f, subtitle)
        if (pageCount > 1) {
            drawHeaderBadge(
                canvas = canvas,
                left = width - 320f,
                top = 36f,
                width = 248f,
                height = 64f,
                background = Color.argb(36, 255, 255, 255),
                textColor = white,
                label = "PAGINA ${pageIndex + 1} DE $pageCount",
                outlineColor = Color.argb(54, 255, 255, 255),
            )
        }

        var top = headerHeight + 32f
        payload.rows.forEach { row ->
            val accent = runCatching { Color.parseColor(row.accentColor ?: "#6366F1") }.getOrElse { Color.parseColor("#6366F1") }
            val localLogoAssetPath = row.logoAssetPath
                ?: StaticLotteryCatalogRepository().getLotteryByName(row.displayName)?.logoAssetPath
            val logoBitmap = context?.let { LotteryLogoBitmapLoader.load(it, localLogoAssetPath) }
            val layout = resolveResultRowLayout(row)
            val hasPrimaryResults = layout.drawPrimaryBalls
            val pick3Digits = row.pick3.orEmpty().filter(Char::isDigit).map { it.toString() }
            val pick4Digits = row.pick4.orEmpty().filter(Char::isDigit).map { it.toString() }
            val card = RectF(28f, top, width - 28f, top + rowHeight)
            fill.shader = android.graphics.LinearGradient(
                card.left,
                card.top,
                card.right,
                card.bottom,
                intArrayOf(
                    Color.argb(224, 15, 23, 42),
                    Color.argb(202, 2, 6, 23),
                ),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(card, 28f, 28f, fill)
            fill.shader = null
            canvas.drawRoundRect(card, 28f, 28f, stroke)
            fill.color = accent
            canvas.drawRoundRect(RectF(28f, top, 40f, top + rowHeight), 12f, 12f, fill)
            if (logoBitmap != null) {
                drawBrandLogo(
                    canvas = canvas,
                    bitmap = logoBitmap,
                    left = rowLayout.logoLeft,
                    top = top + rowLayout.logoTopOffset,
                    width = rowLayout.logoWidth,
                    height = rowLayout.logoHeight,
                    borderColor = Color.argb(86, 255, 255, 255),
                )
            } else {
                drawLotteryBadge(canvas, rowLayout.logoLeft, top + rowLayout.logoTopOffset + 22f, 100f, row.displayName.take(2), accent)
            }
            body.textAlign = Paint.Align.LEFT
            val contentWidth = width - rowLayout.contentLeft - 484f
            drawTextFitted(canvas, row.displayName, rowLayout.contentLeft, top + rowLayout.nameY, body, contentWidth)
            val metaLine = buildResultsMetaLine(row)
            subtitle.color = if (metaLine.contains("Pendiente")) Color.parseColor("#FDE68A") else muted
            subtitle.textAlign = Paint.Align.LEFT
            subtitle.textSize = 24f
            drawTextFitted(canvas, metaLine, rowLayout.contentLeft, top + rowLayout.metaY, subtitle, contentWidth)

            if (hasPrimaryResults) {
                drawResultBallsWithLabels(
                    canvas = canvas,
                    values = listOf(row.first, row.second, row.third),
                    startX = layout.numbersStartX,
                    centerY = top + rowLayout.primaryCenterY,
                    radius = 44f,
                    spacing = 106f,
                    activePaint = ball,
                    inactivePaint = fill,
                    textPaint = ballText,
                    labelPaint = prizeLabel,
                    mutedColor = muted,
                )
            }

            if (pick3Digits.isNotEmpty() || pick4Digits.isNotEmpty()) {
                val pickLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = muted
                    textSize = 24f
                    isFakeBoldText = true
                    textAlign = Paint.Align.LEFT
                }
                var rowTop = if (hasPrimaryResults) top + rowLayout.pickStartY else top + rowLayout.primaryCenterY
                listOf("Pick 3" to pick3Digits, "Pick 4" to pick4Digits).forEach { (label, digits) ->
                    if (digits.isEmpty()) return@forEach
                    val pickLayout = resolvePickResultBallLayout(digits.size)
                    if (hasPrimaryResults) {
                        canvas.drawText(label, rowLayout.contentLeft, rowTop + 12f, pickLabelPaint)
                    }
                    ballText.textSize = 40f
                    drawResultBallsWithLabels(
                        canvas = canvas,
                        values = digits,
                        startX = pickLayout.startX,
                        centerY = rowTop,
                        radius = pickLayout.radius,
                        spacing = pickLayout.spacing,
                        activePaint = ball,
                        inactivePaint = fill,
                        textPaint = ballText,
                        labelPaint = prizeLabel,
                        mutedColor = muted,
                    )
                    rowTop += 68f
                }
                ballText.textSize = 42f
            }
            top += rowHeight + gap
        }
        fill.color = Color.argb(210, 2, 6, 23)
        canvas.drawRect(0f, height - footerHeight.toFloat(), width.toFloat(), height.toFloat(), fill)
        canvas.drawLine(0f, height - footerHeight.toFloat(), width.toFloat(), height - footerHeight.toFloat(), stroke)
        subtitle.color = Color.parseColor("#CBD5E1")
        subtitle.textAlign = Paint.Align.LEFT
        subtitle.textSize = 22f
        canvas.drawText("${payload.bancaName} · Resultados oficiales HD", 36f, height - 36f, subtitle)
        if (pageCount > 1) {
            subtitle.textAlign = Paint.Align.RIGHT
            canvas.drawText("${payload.rows.size} loterias en esta pagina", width - 36f, height - 36f, subtitle)
        }
        return bitmap
    }

    private fun resolvePickDenseResultsBitmapSpec(rowCount: Int): ResultsWhatsAppBitmapSpec {
        val width = 1600
        val headerHeight = 236
        val tableHeaderHeight = 78
        val rowHeight = 82
        val footerHeight = 96
        val contentHeight = headerHeight + tableHeaderHeight + (rowCount.coerceAtLeast(1) * rowHeight) + footerHeight + 54
        return ResultsWhatsAppBitmapSpec(width = width, height = kotlin.math.max(1120, contentHeight))
    }

    private fun renderPickDenseResultsBitmap(
        payload: ResultsSharePayload,
        pickLabel: String,
        pageIndex: Int,
        pageCount: Int,
        context: Context? = null,
    ): Bitmap {
        val rows = payload.rows.filter(::isPickShareRow)
        val spec = resolvePickDenseResultsBitmapSpec(rows.size)
        val width = spec.width
        val height = spec.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F3F6FB"))

        val navy = Color.parseColor("#0F172A")
        val blue = Color.parseColor("#1747D1")
        val green = Color.parseColor("#166534")
        val amber = Color.parseColor("#B45309")
        val pickTone = if (pickLabel == "Pick 3") green else amber
        val border = Color.parseColor("#DBE4EE")
        val muted = Color.parseColor("#64748B")
        val panel = Color.WHITE
        val panelAlt = Color.parseColor("#F8FAFC")

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = border
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 58f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(216, 255, 255, 255)
            textSize = 28f
            textAlign = Paint.Align.LEFT
        }
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 28f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 34f
            typeface = android.graphics.Typeface.MONOSPACE
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        fill.color = Color.parseColor("#123A66")
        canvas.drawRect(0f, 0f, width.toFloat(), 236f, fill)
        val brandLogo = resolveResultsHeaderLogoUri(payload)?.let { logoUri ->
            context?.let { loadBitmapFromUri(it, logoUri) }
        }
        if (brandLogo != null) {
            drawBrandLogo(canvas, brandLogo, 42f, 44f, 112f, 112f, Color.argb(80, 255, 255, 255))
        } else {
            drawHeaderMedallion(canvas, 42f, 44f, 112f, Color.WHITE, Color.parseColor("#123A66"))
        }
        canvas.drawText("Resultados $pickLabel", 180f, 92f, title)
        title.textSize = 34f
        canvas.drawText(payload.bancaName, 180f, 146f, title)
        canvas.drawText(payload.dateLabel, 180f, 192f, subtitle)
        drawHeaderBadge(
            canvas = canvas,
            left = width - 344f,
            top = 48f,
            width = 292f,
            height = 64f,
            background = Color.argb(40, 255, 255, 255),
            textColor = Color.WHITE,
            label = "Página ${pageIndex + 1} de $pageCount",
            outlineColor = Color.argb(54, 255, 255, 255),
        )
        drawHeaderBadge(
            canvas = canvas,
            left = width - 344f,
            top = 128f,
            width = 292f,
            height = 54f,
            background = Color.argb(34, 34, 197, 94),
            textColor = Color.WHITE,
            label = "${rows.size} sorteos",
            outlineColor = Color.argb(54, 255, 255, 255),
        )

        val tableLeft = 34f
        val tableRight = width - 34f
        val top = 266f
        fill.color = panel
        val tableRect = RectF(tableLeft, top, tableRight, height - 116f)
        canvas.drawRoundRect(tableRect, 28f, 28f, fill)
        canvas.drawRoundRect(tableRect, 28f, 28f, stroke)

        val headerY = top + 58f
        header.color = Color.parseColor("#475569")
        canvas.drawText("LOTERÍA", 132f, headerY, header)
        canvas.drawText("HORA", 828f, headerY, header)
        header.textAlign = Paint.Align.CENTER
        canvas.drawText("PICK", 1210f, headerY, header)
        header.textAlign = Paint.Align.LEFT
        fill.color = border
        canvas.drawRect(tableLeft + 22f, top + 76f, tableRight - 22f, top + 78f, fill)

        val rowHeight = 82f
        rows.forEachIndexed { index, row ->
            val rowTop = top + 92f + (index * rowHeight)
            fill.color = if (index % 2 == 0) panelAlt else panel
            canvas.drawRoundRect(RectF(tableLeft + 18f, rowTop - 8f, tableRight - 18f, rowTop + 66f), 16f, 16f, fill)

            val localLogoAssetPath = row.logoAssetPath
                ?: StaticLotteryCatalogRepository().getLotteryByName(row.displayName)?.logoAssetPath
            val logoBitmap = context?.let { LotteryLogoBitmapLoader.load(it, localLogoAssetPath) }
            if (logoBitmap != null) {
                drawLogoThumb(canvas, logoBitmap, 56f, rowTop + 3f, 52f)
            } else {
                drawLotteryBadge(canvas, 56f, rowTop + 5f, 50f, row.displayName.take(2), blue)
            }

            body.textSize = 28f
            body.color = navy
            body.textAlign = Paint.Align.LEFT
            drawTextFitted(canvas, row.displayName, 132f, rowTop + 38f, body, 710f)
            body.textSize = 23f
            body.color = muted
            drawTextFitted(canvas, row.drawTimeLabel.orEmpty().ifBlank { "--" }, 828f, rowTop + 38f, body, 156f)

            val value = if (pickLabel == "Pick 3") row.pick3.orEmpty() else row.pick4.orEmpty()
            drawPickDenseNumberCell(
                canvas = canvas,
                left = 1118f,
                top = rowTop + 6f,
                width = 184f,
                value = value,
                emptyLabel = row.stateLabel.orEmpty().ifBlank { "Sin resultado" },
                tone = pickTone,
                textPaint = mono,
            )
        }

        fill.color = navy
        canvas.drawRect(0f, height - 96f, width.toFloat(), height.toFloat(), fill)
        subtitle.color = Color.parseColor("#CBD5E1")
        subtitle.textAlign = Paint.Align.LEFT
        subtitle.textSize = 23f
        canvas.drawText("${payload.bancaName} · $pickLabel", 42f, height - 38f, subtitle)
        subtitle.textAlign = Paint.Align.RIGHT
        canvas.drawText("LotteryNet", width - 42f, height - 38f, subtitle)
        return bitmap
    }

    private fun drawPickDenseNumberCell(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        value: String,
        emptyLabel: String,
        tone: Int,
        textPaint: Paint,
    ) {
        val rect = RectF(left, top, left + width, top + 54f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (value.any(Char::isDigit)) {
                Color.argb(28, Color.red(tone), Color.green(tone), Color.blue(tone))
            } else {
                Color.parseColor("#F1F5F9")
            }
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = if (value.any(Char::isDigit)) tone else Color.parseColor("#CBD5E1")
        }
        canvas.drawRoundRect(rect, 16f, 16f, fill)
        canvas.drawRoundRect(rect, 16f, 16f, stroke)
        val hasDigits = value.any(Char::isDigit)
        textPaint.color = if (hasDigits) tone else Color.parseColor("#64748B")
        textPaint.textSize = if (hasDigits) 32f else 20f
        canvas.drawText(formatPickDenseDigits(value, emptyLabel), rect.centerX(), top + if (hasDigits) 37f else 34f, textPaint)
    }

    private fun formatPickDenseDigits(value: String, emptyLabel: String): String {
        val digits = value.filter(Char::isDigit)
        if (digits.isNotBlank()) return digits
        return when {
            emptyLabel.contains("No hubo", ignoreCase = true) -> "No hubo"
            emptyLabel.contains("Pendiente", ignoreCase = true) -> "Pendiente"
            emptyLabel.contains("Esperando", ignoreCase = true) -> "Esperando"
            else -> "Sin resultado"
        }
    }

    private fun drawPosterGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                cx,
                cy,
                radius,
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun drawPosterFallbackLogo(canvas: Canvas, left: Float, top: Float, size: Float, label: String) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                left,
                top,
                left + size,
                top + size,
                Color.parseColor("#12306A"),
                Color.parseColor("#06142F"),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = Color.parseColor("#F8D568")
        }
        val rect = RectF(left, top, left + size, top + size)
        canvas.drawRoundRect(rect, 40f, 40f, fill)
        canvas.drawRoundRect(rect, 40f, 40f, stroke)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8D568")
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = 62f
        }
        canvas.drawText(label, rect.centerX(), rect.centerY() + 22f, text)
    }

    private fun drawPosterGoldText(
        canvas: Canvas,
        label: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        initialSize: Float,
    ) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 0, 0, 0)
            textSize = initialSize
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = initialSize
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        while (gold.textSize > 46f && gold.measureText(label) > maxWidth) {
            gold.textSize -= 3f
            shadow.textSize = gold.textSize
        }
        gold.shader = android.graphics.LinearGradient(
            x,
            y - gold.textSize,
            x,
            y + 12f,
            intArrayOf(
                Color.parseColor("#FFF8B8"),
                Color.parseColor("#FACC15"),
                Color.parseColor("#B45309"),
            ),
            null,
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawText(label, x + 5f, y + 8f, shadow)
        canvas.drawText(label, x, y, gold)
    }

    private fun drawPosterInfoBar(canvas: Canvas, fecha: String, hora: String) {
        val rect = RectF(292f, 394f, 1308f, 548f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(176, 2, 6, 23) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            shader = android.graphics.LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#38BDF8"), Color.parseColor("#DC2626")),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(rect, 74f, 74f, fill)
        canvas.drawRoundRect(rect, 74f, 74f, stroke)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 32f
            isFakeBoldText = true
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FDE68A")
            textSize = 42f
            isFakeBoldText = true
        }
        canvas.drawText("FECHA:", 420f, 452f, label)
        drawTextFitted(canvas, fecha.uppercase(Locale.US), 420f, 506f, value, 388f)
        canvas.drawLine(856f, 418f, 856f, 524f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 255, 255, 255)
            strokeWidth = 3f
        })
        canvas.drawText("SORTEO:", 916f, 452f, label)
        drawTextFitted(canvas, hora.ifBlank { "--" }, 916f, 506f, value, 290f)
    }

    private fun drawPosterPrizeCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        rank: String,
        label: String,
        number: String,
        mainColor: Int,
        darkColor: Int,
    ) {
        val rect = RectF(left, top, left + width, top + height)
        drawPosterGlow(canvas, rect.centerX(), rect.bottom - 24f, width * 0.7f, mainColor, 90)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(Color.argb(218, Color.red(darkColor), Color.green(darkColor), Color.blue(darkColor)), Color.parseColor("#020617")),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            color = mainColor
            setShadowLayer(20f, 0f, 0f, mainColor)
        }
        canvas.drawRoundRect(rect, 48f, 48f, fill)
        canvas.drawRoundRect(rect, 48f, 48f, stroke)
        val inner = RectF(left + 34f, top + 150f, left + width - 34f, top + height - 54f)
        val innerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.argb(210, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor))
        }
        canvas.drawRoundRect(inner, 42f, 42f, innerStroke)
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.RadialGradient(
                rect.centerX() - 20f,
                top + 52f,
                126f,
                Color.WHITE,
                mainColor,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(rect.centerX(), top + 40f, 88f, badge)
        canvas.drawCircle(rect.centerX(), top + 40f, 88f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.parseColor("#FFF7AD")
        })
        val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827")
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = 86f
        }
        canvas.drawText(rank, rect.centerX(), top + 72f, rankPaint)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = 46f
            setShadowLayer(10f, 0f, 4f, Color.BLACK)
        }
        canvas.drawText(label, rect.centerX(), top + 238f, labelPaint)
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = 184f
            setShadowLayer(12f, 0f, 8f, Color.BLACK)
            shader = android.graphics.LinearGradient(
                0f,
                top + 304f,
                0f,
                top + 488f,
                Color.WHITE,
                mainColor,
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawText(number.ifBlank { "--" }, rect.centerX(), top + 438f, numberPaint)
    }

    private fun drawLogoThumb(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        size: Float,
    ) {
        val dest = RectF(left, top, left + size, top + size)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRoundRect(dest, 18f, 18f, fill)
        canvas.drawBitmap(bitmap, null, dest, null)
        canvas.drawRoundRect(dest, 18f, 18f, stroke)
    }

    private fun drawGoldenHeaderTitle(canvas: Canvas, label: String, x: Float, y: Float) {
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 63, 43, 0)
            textSize = 46f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                x,
                y - 48f,
                x,
                y + 8f,
                Color.parseColor("#FFF4A3"),
                Color.parseColor("#F8B400"),
                android.graphics.Shader.TileMode.CLAMP,
            )
            textSize = 46f
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(label, x + 3f, y + 4f, shadow)
        canvas.drawText(label, x, y, gold)
    }

    private fun currentDominicanDateLabel(): String {
        return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date())
    }

    private fun parseDominicanDateLabel(value: String): Date? {
        return runCatching {
            SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
            }.parse(value.trim())
        }.getOrNull()
    }

    private fun renderEmptyResultsBitmap(payload: ResultsSharePayload, context: Context? = null): Bitmap {
        val width = 1600
        val height = 760
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#EFF4FB"))
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                0f,
                0f,
                0f,
                220f,
                Color.parseColor("#1747D1"),
                Color.parseColor("#0D2FA1"),
                android.graphics.Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 220f, head)
        val brandLogo = resolveResultsHeaderLogoUri(payload)?.let { logoUri ->
            context?.let { loadBitmapFromUri(it, logoUri) }
        }
        if (brandLogo != null) {
            drawBrandLogo(canvas, brandLogo, 46f, 52f, 128f, 116f, Color.argb(90, 255, 255, 255))
        }
        val navy = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 66f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val muted = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(payload.bancaName, width / 2f, 120f, navy)
        canvas.drawText("Resultados ${payload.dateLabel}", width / 2f, 304f, muted)
        navy.color = Color.parseColor("#0F172A")
        navy.textSize = 54f
        canvas.drawText("Sin resultados disponibles", width / 2f, 424f, navy)
        muted.textSize = 30f
        canvas.drawText("Cuando se publiquen, el export compartido saldra con esta misma plantilla.", width / 2f, 478f, muted)
        return bitmap
    }

    fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        title: String,
        text: String = "",
        whatsappOnly: Boolean = false,
    ): ExportActionResult {
        val uri = writeBitmapToCache(context, bitmap, fileName)
            ?: return ExportActionResult(false, "No se pudo preparar la imagen del ticket")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = android.content.ClipData.newUri(context.contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserTitle = if (whatsappOnly) resolveShareTargetPolicy(true).chooserTitle else title
        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching {
                context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ExportActionResult(true, if (whatsappOnly) "Elige donde compartir" else "Abriendo compartir")
            }.getOrElse {
                ExportActionResult(false, "No se pudo abrir compartir")
            }
        } else {
            ExportActionResult(false, "No hay apps disponibles para compartir")
        }
    }

    fun shareText(
        context: Context,
        title: String,
        text: String,
        whatsappOnly: Boolean = false,
    ): ExportActionResult {
        if (text.isBlank()) return ExportActionResult(false, "No hay resultados para compartir")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooserTitle = if (whatsappOnly) resolveShareTargetPolicy(true).chooserTitle else title
        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching {
                context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ExportActionResult(true, if (whatsappOnly) "Elige donde compartir" else "Abriendo compartir")
            }.getOrElse {
                ExportActionResult(false, "No se pudo abrir compartir")
            }
        } else {
            ExportActionResult(false, "No hay apps disponibles para compartir")
        }
    }

    fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
        return writeBitmapToCache(context, bitmap, "resultados-whatsapp.png")
            ?: throw IllegalStateException("No se pudo guardar la imagen temporal.")
    }

    fun shareResultadosWhatsApp(context: Context, bitmap: Bitmap): ExportActionResult {
        val uri = runCatching { saveBitmapToCache(context, bitmap) }.getOrNull()
            ?: return ExportActionResult(false, "No se pudo preparar la imagen de resultados")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(context.contentResolver, "resultados-whatsapp.png", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(intent, resolveShareTargetPolicy(true).chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching {
                context.startActivity(chooserIntent)
                ExportActionResult(true, "Elige donde compartir")
            }.getOrElse {
                ExportActionResult(false, "No se pudo abrir compartir")
            }
        } else {
            ExportActionResult(false, "No hay apps disponibles para compartir")
        }
    }

    fun shareBitmaps(
        context: Context,
        bitmaps: List<Bitmap>,
        fileNames: List<String>,
        title: String,
        text: String = "",
        whatsappOnly: Boolean = false,
    ): ExportActionResult {
        if (bitmaps.isEmpty()) return ExportActionResult(false, "No hay imagenes para compartir")
        if (bitmaps.size == 1) {
            return shareBitmap(context, bitmaps.first(), fileNames.firstOrNull() ?: "imagen.png", title, text, whatsappOnly)
        }
        val uris = bitmaps.mapIndexedNotNull { index, bitmap ->
            writeBitmapToCache(context, bitmap, fileNames.getOrNull(index) ?: "imagen-${index + 1}.png")
        }
        if (uris.isEmpty()) return ExportActionResult(false, "No se pudo preparar el lote de imagenes")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserTitle = if (whatsappOnly) resolveShareTargetPolicy(true).chooserTitle else title
        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching {
                context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ExportActionResult(true, if (whatsappOnly) "Elige donde compartir" else "Abriendo compartir")
            }.getOrElse {
                ExportActionResult(false, "No se pudo abrir compartir")
            }
        } else {
            ExportActionResult(false, "No hay apps disponibles para compartir")
        }
    }

    fun shareImageUris(
        context: Context,
        uris: List<Uri>,
        title: String,
        text: String = "",
        whatsappOnly: Boolean = false,
    ): ExportActionResult {
        if (uris.isEmpty()) return ExportActionResult(false, "No hay imagenes para compartir")
        val intent = (if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/png"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }).apply {
            putExtra(Intent.EXTRA_SUBJECT, title)
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            val clip = android.content.ClipData.newUri(context.contentResolver, title, uris.first())
            uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserTitle = if (whatsappOnly) resolveShareTargetPolicy(true).chooserTitle else title
        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching {
                context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ExportActionResult(true, if (whatsappOnly) "Elige donde compartir" else "Abriendo compartir")
            }.getOrElse {
                ExportActionResult(false, "No se pudo abrir compartir")
            }
        } else {
            ExportActionResult(false, "No hay apps disponibles para compartir")
        }
    }

    fun saveBitmapToDownloads(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
    ): Boolean {
        val exportBitmap = prepareBitmapForExport(context, bitmap)
        val safeName = if (fileName.lowercase(Locale.getDefault()).endsWith(".png")) fileName else "$fileName.png"
        val bytes = java.io.ByteArrayOutputStream().use { out ->
            exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LotteryNet")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                true
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LotteryNet").apply { mkdirs() }
                FileOutputStream(File(downloadsDir, safeName)).use { it.write(bytes) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun saveBitmapsToDownloads(
        context: Context,
        bitmaps: List<Bitmap>,
        fileNames: List<String>,
    ): Boolean {
        if (bitmaps.isEmpty()) return false
        return bitmaps.mapIndexed { index, bitmap ->
            saveBitmapToDownloads(context, bitmap, fileNames.getOrNull(index) ?: "imagen-${index + 1}.png")
        }.all { it }
    }

    fun printBitmap(
        context: Context,
        bitmap: Bitmap,
        jobName: String,
    ): Boolean {
        return runCatching {
            PrintHelper(context).apply {
                scaleMode = PrintHelper.SCALE_MODE_FIT
                colorMode = PrintHelper.COLOR_MODE_COLOR
                orientation = PrintHelper.ORIENTATION_PORTRAIT
            }.printBitmap(jobName, bitmap)
        }.isSuccess
    }

    fun printBitmaps(
        context: Context,
        bitmaps: List<Bitmap>,
        jobName: String,
    ): Boolean {
        if (bitmaps.isEmpty()) return false
        val printable = if (bitmaps.size == 1) bitmaps.first() else combineVertical(bitmaps)
        return printBitmap(context, printable, jobName)
    }

    private fun writeBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return try {
            val exportBitmap = prepareBitmapForExport(context, bitmap)
            val dir = File(context.cacheDir, "shared_exports").apply { mkdirs() }
            val safeName = if (fileName.lowercase(Locale.getDefault()).endsWith(".png")) fileName else "$fileName.png"
            val file = File(dir, safeName)
            FileOutputStream(file).use { output ->
                exportBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    private fun prepareBitmapForExport(context: Context, bitmap: Bitmap): Bitmap {
        val isLowRamDevice = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        val size = resolveScaledBitmapExportSize(
            isLowRamDevice = isLowRamDevice,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
        )
        if (size.width == bitmap.width && size.height == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, size.width, size.height, true)
    }

    private fun combineVertical(bitmaps: List<Bitmap>): Bitmap {
        val width = bitmaps.maxOf { it.width }
        val height = bitmaps.sumOf { it.height }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { merged ->
            val canvas = Canvas(merged)
            canvas.drawColor(Color.WHITE)
            var top = 0f
            bitmaps.forEach { bitmap ->
                val left = ((width - bitmap.width) / 2f).coerceAtLeast(0f)
                canvas.drawBitmap(bitmap, left, top, null)
                top += bitmap.height.toFloat()
            }
        }
    }

    private fun splitTicketColumns(plays: List<PlayItem>): List<List<PlayItem>> {
        if (plays.size <= 4) return listOf(plays)
        val midpoint = kotlin.math.ceil(plays.size / 2.0).toInt()
        return listOf(plays.take(midpoint), plays.drop(midpoint))
    }

    private fun winnerHitLabel(raw: String): String {
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

    internal fun officialTicketWinnerDetails(ticket: TicketRecord): List<WinningPlayDetail> {
        return ticket.winningDetails
            .filter { detail ->
                detail.lotteryName.isNotBlank() ||
                    detail.playedNumber.isNotBlank() ||
                    detail.resultNumber.isNotBlank() ||
                    detail.payoutAmount > 0.0
            }
    }

    internal fun groupOfficialTicketWinnerDetails(ticket: TicketRecord): List<OfficialTicketWinnerGroup> {
        return officialTicketWinnerDetails(ticket)
            .groupBy { detail ->
                listOf(
                    detail.lotteryName.ifBlank { "Loteria" },
                    detail.resultNumber.ifBlank { "-" },
                ).joinToString("|")
            }
            .map { (_, details) ->
                val first = details.first()
                OfficialTicketWinnerGroup(
                    lotteryName = first.lotteryName.ifBlank { "Loteria" },
                    resultNumber = first.resultNumber.ifBlank { "-" },
                    details = details,
                    totalPayout = details.sumOf { it.payoutAmount },
                )
            }
    }

    internal fun winnerDetailsMeta(ticket: TicketRecord): String {
        val visible = officialTicketWinnerDetails(ticket).size
        val total = ticket.winningDetails.size
        return if (total > visible) "$visible de $total premios" else "$visible premios"
    }

    internal fun winnerPrizeTotalAmount(ticket: TicketRecord): Double {
        return ticket.totalPrize.takeIf { it > 0.0 }
            ?: officialTicketWinnerDetails(ticket).sumOf { it.payoutAmount }
    }

    internal fun winnerPrizeTotalMeta(ticket: TicketRecord): String {
        return "$ ${formatMoney(winnerPrizeTotalAmount(ticket))} total"
    }

    private fun loadBitmapFromUri(context: Context, uriString: String): Bitmap? {
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use(BitmapFactory::decodeStream)
        }.getOrNull()
    }

    private fun drawBrandLogo(
        canvas: Canvas,
        bitmap: Bitmap,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        borderColor: Int,
    ) {
        val box = RectF(left, top, left + width, top + height)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(28, 255, 255, 255) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = borderColor
        }
        canvas.drawRoundRect(box, 28f, 28f, fill)
        canvas.drawRoundRect(box, 28f, 28f, stroke)
        val target = resolveBrandLogoTarget(bitmap.width, bitmap.height, left, top, width, height).toRectF()
        val saveCount = canvas.save()
        canvas.clipRect(box)
        canvas.drawBitmap(bitmap, null, target, Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
        canvas.restoreToCount(saveCount)
    }

    private fun drawResultBallsWithLabels(
        canvas: Canvas,
        values: List<String>,
        startX: Float,
        centerY: Float,
        radius: Float,
        spacing: Float,
        activePaint: Paint,
        inactivePaint: Paint,
        textPaint: Paint,
        labelPaint: Paint,
        mutedColor: Int,
    ) {
        val originalTextColor = textPaint.color
        resultPrizeLabels(values.size).forEachIndexed { index, label ->
            val cx = startX + (index * spacing)
            canvas.drawText(label, cx, centerY - radius - 10f, labelPaint)
        }
        values.forEachIndexed { index, raw ->
            val cx = startX + (index * spacing)
            val value = raw.takeIf { it.isNotBlank() } ?: "-"
            if (raw.isNotBlank()) {
                canvas.drawCircle(cx, centerY, radius, activePaint)
                textPaint.color = originalTextColor
            } else {
                inactivePaint.color = Color.parseColor("#E2E8F0")
                canvas.drawCircle(cx, centerY, radius, inactivePaint)
                textPaint.color = mutedColor
            }
            canvas.drawText(value, cx, centerY + (radius * 0.34f), textPaint)
        }
        textPaint.color = originalTextColor
    }

    private fun drawTextFitted(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
        maxWidth: Float,
    ) {
        if (paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, y, paint)
            return
        }
        var fitted = text
        while (fitted.length > 4 && paint.measureText("$fitted...") > maxWidth) {
            fitted = fitted.dropLast(1)
        }
        canvas.drawText("${fitted.trimEnd()}...", x, y, paint)
    }

    private fun drawHeaderMedallion(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        gold: Int,
        navy: Int,
        label: String = "B",
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = if (label.length > 1) size * 0.28f else size * 0.38f
        }
        fill.color = gold
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, fill)
        fill.color = navy
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2.25f, fill)
        canvas.drawText(label, left + size / 2f, top + size / 2f + (size * 0.14f), text)
    }

    private fun drawHeaderBadge(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        background: Int,
        textColor: Int,
        label: String,
        outlineColor: Int? = null,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), 24f, 24f, fill)
        outlineColor?.let {
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = it
            }
            canvas.drawRoundRect(RectF(left, top, left + width, top + height), 24f, 24f, stroke)
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = if (height > 70f) 34f else 24f
        }
        canvas.drawText(label, left + width / 2f, top + height / 2f + 12f, text)
    }

    private fun drawLotteryBadge(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        label: String,
        accentColor: Int,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#16A34A") }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            textSize = size * 0.34f
        }
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, fill)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.parseColor("#DCFCE7")
        }
        canvas.drawCircle(left + size / 2f, top + size / 2f, size / 2f, stroke)
        canvas.drawText(label.uppercase(Locale.getDefault()), left + size / 2f, top + size / 2f + (size * 0.12f), text)
    }

    private fun drawPlayVisual(
        canvas: Canvas,
        playNumber: String,
        playType: String,
        left: Float,
        centerY: Float,
        accentColor: Int,
    ) {
        val parts = getPlayParts(playNumber, playType)
        if (parts.isEmpty()) {
            val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#16A34A")
                textSize = 36f
                isFakeBoldText = true
                typeface = android.graphics.Typeface.MONOSPACE
            }
            canvas.drawText(playNumber, left, centerY + 12f, mono)
            return
        }
        val ticketBallGreen = Color.parseColor("#16A34A")
        val ticketBallRing = Color.parseColor("#DCFCE7")
        val visual = resolveOfficialTicketBitmapPlayVisual(
            partCount = parts.size,
            hasLongPart = parts.any { it.length > 2 },
        )
        val radius = visual.ballRadiusPx
        val spacing = visual.spacingPx
        parts.forEachIndexed { index, part ->
            val cx = left + radius + (index * spacing)
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ticketBallGreen }
            canvas.drawCircle(cx, centerY, radius, fill)
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = ticketBallRing
            }
            canvas.drawCircle(cx, centerY, radius, stroke)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = if (part.length > 2) visual.textSizePx.coerceAtMost(36f) else visual.textSizePx
            }
            canvas.drawText(part, cx, centerY + 14f, text)
        }
    }

    private fun drawInfoBox(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        label: String,
        value: String,
        body: Paint,
        small: Paint,
        stroke: Paint,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
        canvas.drawRoundRect(RectF(left, top, right, bottom), 22f, 22f, fill)
        canvas.drawRoundRect(RectF(left, top, right, bottom), 22f, 22f, stroke)
        val layout = resolveOfficialTicketSecurityLayout(top)
        small.color = Color.parseColor("#64748B")
        small.textAlign = Paint.Align.LEFT
        small.textSize = 20f
        canvas.drawText(label.uppercase(Locale.getDefault()), left + 24f, layout.labelBaselineY, small)
        body.color = Color.parseColor("#0F172A")
        body.textAlign = Paint.Align.LEFT
        body.textSize = layout.valueTextSize
        body.typeface = android.graphics.Typeface.MONOSPACE
        body.isFakeBoldText = true
        canvas.drawText(value, left + 24f, layout.valueBaselineY, body)
    }

    private fun drawTicketStatusBanner(
        canvas: Canvas,
        width: Float,
        ticket: TicketRecord,
    ) {
        val normalized = ticket.status.lowercase(Locale.getDefault())
        val label = if (isPaidTicketStatus(normalized)) {
            "PAGADO"
        } else {
            when (normalized) {
            "winner" -> "GANADOR"
            "voided", "invalid" -> "ANULADO"
            else -> "ACTIVO"
            }
        }
        val fillColor = if (isPaidTicketStatus(normalized)) {
            Color.parseColor("#166534")
        } else {
            when (normalized) {
            "winner" -> Color.parseColor("#B45309")
            "voided", "invalid" -> Color.parseColor("#991B1B")
            else -> Color.parseColor("#16A34A")
            }
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val left = width - 246f
        val top = 34f
        val right = width - 34f
        val bottom = 92f
        canvas.drawRoundRect(RectF(left, top, right, bottom), 18f, 18f, fill)
        canvas.drawText(label, (left + right) / 2f, top + 39f, text)
    }

    private fun drawBadge(
        canvas: Canvas,
        left: Float,
        top: Float,
        text: String,
        color: Int,
        textPaint: Paint,
    ) {
        val width = 128f
        val height = 38f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(56, Color.red(color), Color.green(color), Color.blue(color)) }
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), 16f, 16f, fill)
        canvas.drawText(text, left + 18f, top + 26f, textPaint)
    }

    private fun drawFinanceMetricCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        label: String,
        value: String,
        accent: Int,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#DBE4EE")
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 22f
            isFakeBoldText = true
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 44f
            isFakeBoldText = true
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val rect = RectF(left, top, left + width, top + height)
        canvas.drawRoundRect(rect, 24f, 24f, fill)
        canvas.drawRoundRect(rect, 24f, 24f, stroke)
        fill.color = accent
        canvas.drawRoundRect(RectF(left, top, left + 12f, top + height), 12f, 12f, fill)
        canvas.drawText(label.uppercase(Locale.getDefault()), left + 28f, top + 52f, labelPaint)
        canvas.drawText(value, left + 28f, top + 128f, valuePaint)
    }

    private fun drawReportStatusPill(
        canvas: Canvas,
        left: Float,
        top: Float,
        label: String,
        accent: Int,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 236, 253, 245)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 25f
            isFakeBoldText = true
        }
        val rect = RectF(left, top, left + 296f, top + 54f)
        canvas.drawRoundRect(rect, 20f, 20f, fill)
        canvas.drawCircle(left + 30f, top + 27f, 7f, dot)
        canvas.drawText(label, left + 50f, top + 36f, text)
    }

    private fun drawReportSegmentBar(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        labels: List<String>,
        selectedIndex: Int,
    ) {
        if (labels.isEmpty()) return
        val gap = 10f
        val itemWidth = (width - (gap * (labels.size - 1))) / labels.size
        labels.forEachIndexed { index, label ->
            val itemLeft = left + (index * (itemWidth + gap))
            val rect = RectF(itemLeft, top, itemLeft + itemWidth, top + 48f)
            val selected = index == selectedIndex
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (selected) Color.WHITE else Color.argb(36, 255, 255, 255)
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.argb(72, 255, 255, 255)
            }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (selected) Color.parseColor("#123A66") else Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = 22f
                isFakeBoldText = true
            }
            canvas.drawRoundRect(rect, 16f, 16f, fill)
            canvas.drawRoundRect(rect, 16f, 16f, stroke)
            canvas.drawText(label, rect.centerX(), top + 32f, text)
        }
    }

    private fun drawSectionCard(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        title: String,
        stroke: Paint,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 30f
            isFakeBoldText = true
        }
        canvas.drawRoundRect(RectF(left, top, right, bottom), 24f, 24f, fill)
        canvas.drawRoundRect(RectF(left, top, right, bottom), 24f, 24f, stroke)
        canvas.drawText(title, left + 24f, top + 34f, titlePaint)
    }

    private fun drawFinanceRow(
        canvas: Canvas,
        left: Float,
        baseline: Float,
        label: String,
        value: String,
        body: Paint,
        mono: Paint,
        valueColor: Int = Color.parseColor("#0F172A"),
    ) {
        body.color = Color.parseColor("#475569")
        body.textSize = 24f
        body.textAlign = Paint.Align.LEFT
        mono.color = valueColor
        mono.textAlign = Paint.Align.RIGHT
        mono.textSize = if (value.length > 20) 24f else 28f
        canvas.drawText(label, left, baseline, body)
        canvas.drawText(value, left + 580f, baseline, mono)
    }

    private fun lotAccentColor(seed: String): Int {
        val palette = listOf("#1747D1", "#0D9488", "#16A34A", "#EA580C", "#7C3AED", "#CA8A04")
        val index = (seed.lowercase(Locale.getDefault()).hashCode() and Int.MAX_VALUE) % palette.size
        return Color.parseColor(palette[index])
    }

    private fun getPlayParts(number: String, playType: String): List<String> {
        val cleaned = number.replace("-", "").replace("/", "").trim()
        return when {
            playType.startsWith("P3", ignoreCase = true) || playType.startsWith("P4", ignoreCase = true) ->
                cleaned.toCharArray().map { it.toString() }.filter { it.isNotBlank() }
            playType.equals("P", ignoreCase = true) || playType.equals("SP", ignoreCase = true) -> {
                val formatted = formatPlayDisplayNumber(number, playType).replace("/", "-")
                formatted.split('-').map { it.trim() }.filter { it.isNotBlank() }
            }
            playType.equals("T", ignoreCase = true) -> {
                val formatted = formatTripletaDisplayNumber(number)
                formatted.split('-').map { it.trim() }.filter { it.isNotBlank() }
            }
            number.contains("-") -> number.split('-').map { it.trim() }.filter { it.isNotBlank() }
            else -> listOf(number.trim()).filter { it.isNotBlank() }
        }
    }

    private fun formatTripletaDisplayNumber(number: String): String {
        if (number.isBlank()) return number
        if (number.contains("-")) return number
        val cleaned = number.filter(Char::isDigit)
        return when {
            cleaned.length >= 6 -> listOf(
                cleaned.take(2),
                cleaned.drop(2).take(2),
                cleaned.drop(4).take(2),
            ).joinToString("-")
            cleaned.length == 4 -> listOf(
                cleaned.take(2),
                cleaned.drop(2).take(2),
            ).joinToString("-")
            else -> number
        }
    }

    private fun playTypeLabel(playType: String): String {
        return when (playType.uppercase(Locale.getDefault())) {
            "Q" -> "Quiniela"
            "P" -> "Pale"
            "SP" -> "Super Pale"
            "T" -> "Tripleta"
            "P3" -> "Pick 3 Straight"
            "P3BOX" -> "Pick 3 Box"
            "P3B" -> "Pick 3 Back Pair"
            "P4" -> "Pick 4 Straight"
            "P4BOX" -> "Pick 4 Box"
            "P4B" -> "Pick 4 Back Pair"
            else -> playType
        }
    }

    private fun formatMoney(amount: Double): String {
        return com.lotterynet.pro.core.format.formatWholeAmount(amount)
    }

    private fun signedAmount(amount: Double): String {
        return com.lotterynet.pro.core.format.formatSignedWholeMoney(amount)
    }

    private fun miniMoney(amount: Double): String = "$ ${formatMoney(amount)}"

    private fun cashierTicketState(status: String): String {
        if (isPaidTicketStatus(status)) return "Pagado"
        return when (status) {
            "winner" -> "Ganador"
            "voided", "invalid" -> "Anulado"
            else -> "Activo"
        }
    }

    internal fun formatDateForTicket(epochMs: Long): String {
        val format = SimpleDateFormat("dd-MM-yyyy", Locale.US)
        format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        return format.format(Date(epochMs))
    }

    internal fun formatDrawDateForTicket(dayKey: String, drawTimeLabel: String? = null): String {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }
        val date = runCatching { input.parse(dayKey) }.getOrNull() ?: return dayKey
        val output = SimpleDateFormat("EEEE d 'de' MMMM yyyy", Locale.forLanguageTag("es-DO")).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }
        val dateLabel = output.format(date)
        val time = drawTimeLabel?.takeIf { it.isNotBlank() }
        return if (time == null) dateLabel else "$dateLabel - $time"
    }

    private fun resolveTicketDrawTimeLabel(ticket: TicketRecord): String? {
        val catalog = runCatching { StaticLotteryCatalogRepository().getAllLotteries() }.getOrDefault(emptyList())
        val times = ticket.plays.mapNotNull { play ->
            val lottery = catalog.firstOrNull { it.id == play.lotteryId } ?: catalog.firstOrNull {
                it.name.equals(play.lotteryName.orEmpty(), ignoreCase = true)
            }
            lottery?.baseDrawTime
        }.distinct()
        return when (times.size) {
            0 -> null
            1 -> times.first()
            else -> "varios sorteos"
        }
    }

    internal fun formatTimeForTicket(epochMs: Long): String {
        val format = SimpleDateFormat("hh:mm a", Locale.US)
        format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        return format.format(Date(epochMs))
    }

    private fun buildResultsMetaLine(row: ResultShareRow): String {
        val extras = buildList {
            row.source?.takeIf { it.isNotBlank() }?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
            row.drawTimeLabel?.takeIf { it.isNotBlank() }?.let { add("Sorteo $it") }
            row.stateLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (row.pick3.isNullOrBlank() && row.pick4.isNullOrBlank() && row.first.isBlank() && row.second.isBlank() && row.third.isBlank()) {
                add("Pendiente por publicar")
            }
        }
        return extras.joinToString(" · ").ifBlank { "Publicacion local" }
    }

    private fun drawQrBlock(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Int,
        payload: TicketQrPayload,
        outline: Int,
        navy: Int,
        muted: Int,
    ) {
        val qrBitmap = buildQrBitmap(JSONObject().apply {
            put("version", payload.version)
            put("id", payload.id)
            put("banca", payload.banca)
            put("lots", payload.lots)
            put("date", payload.date)
            put("total", payload.total)
            put("securityCode", payload.securityCode)
            put("plays", payload.plays.map {
                JSONObject().apply {
                    put("type", it.type)
                    put("number", it.number)
                    put("amount", it.amount)
                    put("lotteryName", it.lotteryName)
                }
            })
        }.toString(), size)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F8FAFC") }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = outline
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navy
            textSize = 28f
            isFakeBoldText = true
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = muted
            textSize = 24f
        }
        val verificationWidth = 920f
        val box = RectF(left, top, left + verificationWidth, top + size + 120f)
        canvas.drawRoundRect(box, 24f, 24f, fill)
        canvas.drawRoundRect(box, 24f, 24f, stroke)
        canvas.drawBitmap(qrBitmap, left + 24f, top + 24f, null)
        canvas.drawText("VERIFICACION", left + size + 68f, top + 64f, body)
        canvas.drawText("Ticket: ${payload.id.sanitizeTicketId()}", left + size + 68f, top + 114f, small)
        canvas.drawText("Fecha: ${payload.date}", left + size + 68f, top + 154f, small)
        canvas.drawText("Total: ${formatMoney(payload.total)}", left + size + 68f, top + 194f, small)
        canvas.drawText("Loterias: ${payload.lots.ifBlank { "N/A" }}", left + size + 68f, top + 234f, small)
        canvas.drawText("Jugadas: ${payload.plays.size}", left + size + 68f, top + 274f, small)
        payload.securityCode.takeIf { it.isNotBlank() }?.let {
            body.color = Color.parseColor("#92400E")
            body.textSize = 26f
            canvas.drawText("Codigo: $it", left + size + 68f, top + 316f, body)
        }
    }

    private fun buildQrBitmap(content: String, size: Int): Bitmap {
        val matrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun String.sanitizeTicketId(): String {
        return replace(Regex("[^0-9A-Za-z]"), "").ifBlank { this }
    }
}
