package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.isPaidStatus
import com.lotterynet.pro.core.model.normalizedPrizeTableConfig
import java.text.Normalizer
import java.util.Locale

data class PrizeValidationOutcome(
    val ticket: TicketRecord,
    val totalPrize: Double,
    val matchCount: Int,
    val didValidate: Boolean,
)

class PrizeValidationEngine(
    private val catalogRepository: StaticLotteryCatalogRepository = StaticLotteryCatalogRepository(),
) {
    fun validate(
        ticket: TicketRecord,
        results: List<LotteryResult>,
        prizeConfig: PrizeTableConfig,
    ): PrizeValidationOutcome {
        if (ticket.status.equals("voided", true) || ticket.status.equals("invalid", true) || results.isEmpty()) {
            return PrizeValidationOutcome(ticket, ticket.totalPrize, 0, false)
        }

        val safePrizeConfig = prizeConfig.normalizedPrizeTableConfig()
        var relevantRowsFound = false
        var totalPrize = 0.0
        var matchCount = 0

        ticket.plays.forEach { play ->
            val resolution = resolvePrizes(play, results, safePrizeConfig)
            relevantRowsFound = relevantRowsFound || resolution.relevantRowsFound
            totalPrize += resolution.totalPrize
            matchCount += resolution.matchCount
        }

        if (!relevantRowsFound) {
            return PrizeValidationOutcome(ticket, ticket.totalPrize, 0, false)
        }

        val normalizedTicket = ticket.copy(
            totalPrize = totalPrize,
            status = when {
                totalPrize > 0.0 && ticket.isPaidStatus() -> "paid"
                totalPrize > 0.0 -> "winner"
                else -> "active"
            },
        )
        return PrizeValidationOutcome(
            ticket = normalizedTicket,
            totalPrize = totalPrize,
            matchCount = matchCount,
            didValidate = true,
        )
    }

    private fun resolvePrizes(
        play: PlayItem,
        results: List<LotteryResult>,
        prizeConfig: PrizeTableConfig,
    ): PlayPrizeResolution {
        if (play.playType.equals("SP", true)) {
            val primary = findResultRow(results, play.lotteryId, play.lotteryName)
            val secondary = findResultRow(results, play.secondaryLotteryId, play.secondaryLotteryName)
            if (primary == null && secondary == null) return PlayPrizeResolution()
            if (primary == null || secondary == null) return PlayPrizeResolution(relevantRowsFound = true)

            val parts = splitDigits(play.number, 2)
            if (parts.size != 2) return PlayPrizeResolution(relevantRowsFound = true)
            val direct = primary.first.orEmpty() == parts[0] && secondary.first.orEmpty() == parts[1]
            val reverse = primary.first.orEmpty() == parts[1] && secondary.first.orEmpty() == parts[0]
            return if (direct || reverse) {
                PlayPrizeResolution(totalPrize = play.amount * prizeConfig.superPale, matchCount = 1, relevantRowsFound = true)
            } else {
                PlayPrizeResolution(relevantRowsFound = true)
            }
        }

        val result = findResultRow(results, play.lotteryId, play.lotteryName) ?: return PlayPrizeResolution()
        val digits = digitsOnly(play.number)
        return when (play.playType.uppercase(Locale.ROOT)) {
            "Q" -> resolveQuiniela(play.amount, digits, result, prizeConfig)
            "P" -> resolvePale(play.amount, digits, result, prizeConfig)
            "T" -> resolveTripleta(play.amount, digits, result, prizeConfig)
            "P3" -> resolveExact(play.amount, digits, result.pick3 ?: drawTriple(result), prizeConfig.pick3Straight)
            "P4" -> resolveExact(play.amount, digits, result.pick4 ?: drawQuad(result), prizeConfig.pick4Straight)
            "P3BOX" -> resolveBox(play.amount, digits, result.pick3 ?: drawTriple(result), "P3BOX", prizeConfig)
            "P4BOX" -> resolveBox(play.amount, digits, result.pick4 ?: drawQuad(result), "P4BOX", prizeConfig)
            "P3B" -> resolveBackPair(play.amount, digits, result.pick3 ?: drawTriple(result), prizeConfig.pick3BackPair)
            "P4B" -> resolveBackPair(play.amount, digits, result.pick4 ?: drawQuad(result), prizeConfig.pick4BackPair)
            else -> PlayPrizeResolution(relevantRowsFound = true)
        }
    }

    private fun resolveQuiniela(
        amount: Double,
        digits: String,
        result: LotteryResult,
        prizeConfig: PrizeTableConfig,
    ): PlayPrizeResolution {
        val multiplier = when (digits) {
            result.first -> prizeConfig.q1.toDouble()
            result.second -> prizeConfig.q2.toDouble()
            result.third -> prizeConfig.q3.toDouble()
            else -> null
        }
        return if (multiplier != null) {
            PlayPrizeResolution(amount * multiplier, 1, true)
        } else {
            PlayPrizeResolution(relevantRowsFound = true)
        }
    }

    private fun resolvePale(
        amount: Double,
        digits: String,
        result: LotteryResult,
        prizeConfig: PrizeTableConfig,
    ): PlayPrizeResolution {
        val parts = splitDigits(digits, 2)
        if (parts.size != 2 || parts[0] == parts[1]) return PlayPrizeResolution(relevantRowsFound = true)
        val first = result.first.orEmpty()
        val second = result.second.orEmpty()
        val third = result.third.orEmpty()
        val matchedPayout = when {
            containsBoth(parts, first, second) -> prizeConfig.pale12
            containsBoth(parts, first, third) -> prizeConfig.pale13
            containsBoth(parts, second, third) -> prizeConfig.pale23
            else -> null
        }
        return if (matchedPayout != null) {
            PlayPrizeResolution(amount * matchedPayout, 1, true)
        } else {
            PlayPrizeResolution(relevantRowsFound = true)
        }
    }

    private fun resolveTripleta(
        amount: Double,
        digits: String,
        result: LotteryResult,
        prizeConfig: PrizeTableConfig,
    ): PlayPrizeResolution {
        val parts = splitDigits(digits, 2)
        val drawn = listOfNotNull(result.first, result.second, result.third)
        val matched = if (parts.size == 3 && drawn.size == 3) drawn.count(parts::contains) else 0
        return when (matched) {
            3 -> PlayPrizeResolution(amount * prizeConfig.tripleta3, 1, true)
            2 -> PlayPrizeResolution(amount * prizeConfig.tripleta2, 1, true)
            else -> PlayPrizeResolution(relevantRowsFound = true)
        }
    }

    private fun containsBoth(parts: List<String>, a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return parts.contains(a) && parts.contains(b)
    }

    private fun resolveExact(amount: Double, digits: String, drawn: String, multiplier: Int): PlayPrizeResolution {
        return if (digits.isNotBlank() && digits == digitsOnly(drawn)) {
            PlayPrizeResolution(amount * multiplier, 1, true)
        } else {
            PlayPrizeResolution(relevantRowsFound = drawn.isNotBlank())
        }
    }

    private fun resolveBox(
        amount: Double,
        digits: String,
        drawn: String,
        playType: String,
        prizeConfig: PrizeTableConfig,
    ): PlayPrizeResolution {
        val multiplier = when (playType) {
            "P3BOX" -> when (pickBoxWay(digits)) {
                3 -> prizeConfig.pick3Box3
                6 -> prizeConfig.pick3Box6
                else -> 0
            }
            else -> when (pickBoxWay(digits)) {
                4 -> prizeConfig.pick4Box4
                6 -> prizeConfig.pick4Box6
                12 -> prizeConfig.pick4Box12
                24 -> prizeConfig.pick4Box24
                else -> 0
            }
        }
        return if (multiplier > 0 && isPermutationMatch(drawn, digits)) {
            PlayPrizeResolution(amount * multiplier, 1, true)
        } else {
            PlayPrizeResolution(relevantRowsFound = drawn.isNotBlank())
        }
    }

    private fun resolveBackPair(amount: Double, digits: String, drawn: String, multiplier: Int): PlayPrizeResolution {
        val normalized = digitsOnly(drawn)
        return if (digits.length == 2 && normalized.takeLast(2) == digits) {
            PlayPrizeResolution(amount * multiplier, 1, true)
        } else {
            PlayPrizeResolution(relevantRowsFound = normalized.isNotBlank())
        }
    }

    private fun findResultRow(
        results: List<LotteryResult>,
        lotteryId: String?,
        lotteryName: String?,
    ): LotteryResult? {
        val safeName = lotteryName.orEmpty()
        val normalizedName = normalizeLooseText(safeName)
        val catalogLottery = lotteryId?.let(catalogRepository::getLotteryById)
            ?: safeName.takeIf { it.isNotBlank() }?.let(catalogRepository::getLotteryByName)
        return results.firstOrNull { row ->
            val rowCatalogLottery = row.lotteryId.takeIf { it.isNotBlank() }?.let(catalogRepository::getLotteryById)
                ?: row.lotteryName?.takeIf { it.isNotBlank() }?.let(catalogRepository::getLotteryByName)
            row.lotteryId == lotteryId ||
                (catalogLottery != null && row.lotteryId == catalogLottery.id) ||
                (catalogLottery != null && rowCatalogLottery?.id == catalogLottery.id) ||
                normalizeLooseText(row.lotteryName.orEmpty()) == normalizedName
        }
    }

    private fun drawTriple(result: LotteryResult): String =
        listOfNotNull(result.first, result.second, result.third).joinToString("")

    private fun drawQuad(result: LotteryResult): String =
        digitsOnly(result.pick4 ?: result.pick3 ?: drawTriple(result))

    private fun splitDigits(raw: String, chunk: Int): List<String> {
        val normalized = digitsOnly(raw)
        if (chunk <= 0 || normalized.length % chunk != 0) return emptyList()
        return normalized.chunked(chunk)
    }

    private fun digitsOnly(raw: String?): String = raw.orEmpty().filter(Char::isDigit)

    private fun isPermutationMatch(drawn: String, number: String): Boolean {
        val a = digitsOnly(drawn).toCharArray().sorted().joinToString("")
        val b = digitsOnly(number).toCharArray().sorted().joinToString("")
        return a.isNotBlank() && a == b
    }

    private fun pickBoxWay(number: String): Int {
        val digits = digitsOnly(number)
        if (digits.length != 3 && digits.length != 4) return 0
        val freq = digits.groupingBy { it }.eachCount().values.sortedDescending()
        return if (digits.length == 3) {
            when (freq.firstOrNull()) {
                2 -> 3
                1 -> 6
                else -> 0
            }
        } else {
            when {
                freq.firstOrNull() == 3 -> 4
                freq.size == 2 && freq[0] == 2 && freq[1] == 2 -> 6
                freq.firstOrNull() == 2 -> 12
                freq.firstOrNull() == 1 -> 24
                else -> 0
            }
        }
    }

    private fun normalizeLooseText(value: String): String {
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return normalized.lowercase(Locale.ROOT).trim()
    }
}

private data class PlayPrizeResolution(
    val totalPrize: Double = 0.0,
    val matchCount: Int = 0,
    val relevantRowsFound: Boolean = false,
)
