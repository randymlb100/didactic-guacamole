package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.WinningPlayDetail
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
        val winningDetails = mutableListOf<WinningPlayDetail>()

        ticket.plays.forEach { play ->
            val resolution = resolvePrizes(play, results, safePrizeConfig)
            relevantRowsFound = relevantRowsFound || resolution.relevantRowsFound
            totalPrize += resolution.totalPrize
            matchCount += resolution.matchCount
            resolution.detail?.let(winningDetails::add)
        }

        if (!relevantRowsFound) {
            return PrizeValidationOutcome(ticket, ticket.totalPrize, 0, false)
        }

        val normalizedTicket = ticket.copy(
            totalPrize = totalPrize,
            winningDetails = winningDetails,
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
                val payout = play.amount * prizeConfig.superPale
                PlayPrizeResolution(
                    totalPrize = payout,
                    matchCount = 1,
                    relevantRowsFound = true,
                    detail = winningDetail(play, primary, "SP", payout),
                )
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
            "P3" -> resolveExact(play, digits, result, result.pick3 ?: drawTriple(result), prizeConfig.pick3Straight)
            "P4" -> resolveExact(play, digits, result, result.pick4 ?: drawQuad(result), prizeConfig.pick4Straight)
            "P3BOX" -> resolveBox(play, digits, result, result.pick3 ?: drawTriple(result), "P3BOX", prizeConfig)
            "P4BOX" -> resolveBox(play, digits, result, result.pick4 ?: drawQuad(result), "P4BOX", prizeConfig)
            "P3B" -> resolveBackPair(play, digits, result, result.pick3 ?: drawTriple(result), prizeConfig.pick3BackPair)
            "P4B" -> resolveBackPair(play, digits, result, result.pick4 ?: drawQuad(result), prizeConfig.pick4BackPair)
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
            result.first -> "1" to prizeConfig.q1.toDouble()
            result.second -> "2" to prizeConfig.q2.toDouble()
            result.third -> "3" to prizeConfig.q3.toDouble()
            else -> null
        }
        return if (multiplier != null) {
            val (hitPosition, value) = multiplier
            val payout = amount * value
            PlayPrizeResolution(payout, 1, true, winningDetail(amount, digits, "Q", result, hitPosition, payout))
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
            containsBoth(parts, first, second) -> "1-2" to prizeConfig.pale12
            containsBoth(parts, first, third) -> "1-3" to prizeConfig.pale13
            containsBoth(parts, second, third) -> "2-3" to prizeConfig.pale23
            else -> null
        }
        return if (matchedPayout != null) {
            val (hitPosition, value) = matchedPayout
            val payout = amount * value
            PlayPrizeResolution(payout, 1, true, winningDetail(amount, digits, "P", result, hitPosition, payout))
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
            3 -> {
                val payout = amount * prizeConfig.tripleta3
                PlayPrizeResolution(payout, 1, true, winningDetail(amount, digits, "T", result, "3", payout))
            }
            2 -> {
                val payout = amount * prizeConfig.tripleta2
                PlayPrizeResolution(payout, 1, true, winningDetail(amount, digits, "T", result, "2", payout))
            }
            else -> PlayPrizeResolution(relevantRowsFound = true)
        }
    }

    private fun containsBoth(parts: List<String>, a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return parts.contains(a) && parts.contains(b)
    }

    private fun resolveExact(
        play: PlayItem,
        digits: String,
        result: LotteryResult,
        drawn: String,
        multiplier: Int,
    ): PlayPrizeResolution {
        return if (digits.isNotBlank() && digits == digitsOnly(drawn)) {
            val payout = play.amount * multiplier
            PlayPrizeResolution(
                totalPrize = payout,
                matchCount = 1,
                relevantRowsFound = true,
                detail = winningDetail(play.amount, digits, play.playType, result, "straight", payout).copy(resultNumber = drawn),
            )
        } else {
            PlayPrizeResolution(relevantRowsFound = drawn.isNotBlank())
        }
    }

    private fun resolveBox(
        play: PlayItem,
        digits: String,
        result: LotteryResult,
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
            val payout = play.amount * multiplier
            PlayPrizeResolution(
                totalPrize = payout,
                matchCount = 1,
                relevantRowsFound = true,
                detail = winningDetail(play.amount, digits, play.playType, result, playType.removePrefix("P").lowercase(Locale.ROOT), payout)
                    .copy(resultNumber = drawn),
            )
        } else {
            PlayPrizeResolution(relevantRowsFound = drawn.isNotBlank())
        }
    }

    private fun resolveBackPair(
        play: PlayItem,
        digits: String,
        result: LotteryResult,
        drawn: String,
        multiplier: Int,
    ): PlayPrizeResolution {
        val normalized = digitsOnly(drawn)
        return if (digits.length == 2 && normalized.takeLast(2) == digits) {
            val payout = play.amount * multiplier
            PlayPrizeResolution(
                totalPrize = payout,
                matchCount = 1,
                relevantRowsFound = true,
                detail = winningDetail(play.amount, digits, play.playType, result, "back", payout).copy(resultNumber = drawn),
            )
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

    private fun winningDetail(
        play: PlayItem,
        result: LotteryResult,
        hitPosition: String,
        payout: Double,
    ): WinningPlayDetail {
        return winningDetail(
            amount = play.amount,
            digits = play.number,
            playType = play.playType,
            result = result,
            hitPosition = hitPosition,
            payout = payout,
            fallbackLotteryName = play.lotteryName,
        )
    }

    private fun winningDetail(
        amount: Double,
        digits: String,
        playType: String,
        result: LotteryResult,
        hitPosition: String,
        payout: Double,
        fallbackLotteryName: String? = null,
    ): WinningPlayDetail {
        return WinningPlayDetail(
            lotteryName = result.lotteryName?.takeIf { it.isNotBlank() } ?: fallbackLotteryName.orEmpty(),
            playType = playType,
            playedNumber = digits,
            resultNumber = listOfNotNull(result.first, result.second, result.third)
                .takeIf { it.isNotEmpty() }
                ?.joinToString("-")
                ?: result.pick4
                ?: result.pick3
                ?: "",
            hitPosition = hitPosition,
            amount = amount,
            payoutAmount = payout,
        )
    }

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
    val detail: WinningPlayDetail? = null,
)
