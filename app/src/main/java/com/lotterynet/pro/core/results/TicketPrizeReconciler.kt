package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.isPaidStatus
import com.lotterynet.pro.core.storage.LocalPrizeConfigRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class TicketReconcileSummary(
    val scanned: Int = 0,
    val updated: Int = 0,
    val winners: Int = 0,
    val paid: Int = 0,
)

class TicketPrizeReconciler(
    private val salesRepository: LocalSalesRepository,
    private val prizeRepository: LocalPrizeConfigRepository,
    private val validationEngine: PrizeValidationEngine = PrizeValidationEngine(),
    private val prizeConfigResolver: ((TicketRecord) -> PrizeTableConfig)? = null,
    private val onTicketUpdated: ((TicketRecord) -> Unit)? = null,
) {
    fun reconcileTicketsForDate(
        dateKey: String,
        results: List<LotteryResult>,
    ): TicketReconcileSummary {
        if (results.isEmpty()) return TicketReconcileSummary()
        val canonicalDayKey = resultsDateToTicketDayKey(dateKey)
        val tickets = salesRepository.getTicketsForDay(canonicalDayKey)
        if (tickets.isEmpty()) return TicketReconcileSummary()

        var updated = 0
        var winners = 0
        var paid = 0

        tickets.forEach { ticket ->
            val prizeConfig = prizeConfigResolver?.invoke(ticket) ?: prizeRepository.getConfig()
            val outcome = validationEngine.validate(ticket, results, prizeConfig)
            val normalized = outcome.ticket
            if (normalized.status.equals("winner", true)) winners += 1
            if (normalized.isPaidStatus()) paid += 1
            if (outcome.didValidate && normalized != ticket) {
                salesRepository.replaceTicket(normalized)
                onTicketUpdated?.invoke(normalized)
                updated += 1
            }
        }

        return TicketReconcileSummary(
            scanned = tickets.size,
            updated = updated,
            winners = winners,
            paid = paid,
        )
    }

    fun resultsDateToTicketDayKey(raw: String): String {
        val aliases = dateAliases(raw)
        return aliases.firstOrNull { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) } ?: raw
    }

    fun dateAliases(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        val zone = TimeZone.getTimeZone("America/Santo_Domingo")
        val formats = listOf("yyyy-MM-dd", "dd-MM-yyyy")
        val parsed = formats.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = zone
                    isLenient = false
                }.parse(trimmed)
            }.getOrNull()
        } ?: return listOf(trimmed)

        return listOf("yyyy-MM-dd", "dd-MM-yyyy").map { pattern ->
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = zone
            }.format(Date(parsed.time))
        }.distinct()
    }
}
