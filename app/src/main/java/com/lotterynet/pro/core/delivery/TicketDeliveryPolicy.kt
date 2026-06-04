package com.lotterynet.pro.core.delivery

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import java.util.Locale

enum class TicketDeliveryMode {
    SINGLE_IMAGE,
    PAGED_IMAGES,
    TEXT_SUMMARY_FALLBACK,
}

data class TicketDeliveryPage(
    val index: Int,
    val totalPages: Int,
    val plays: List<PlayItem>,
    val label: String,
)

data class TicketDeliveryDecision(
    val mode: TicketDeliveryMode,
    val estimatedHeightPx: Int,
    val playCount: Int,
    val lotteryCount: Int,
)

object TicketDeliveryPolicy {
    const val MAX_SINGLE_IMAGE_HEIGHT_PX = 3600
    const val MAX_SINGLE_IMAGE_PLAYS = 60
    const val MAX_LOTTERIES_FOR_SINGLE_IMAGE = 4
    const val MAX_PLAYS_PER_LARGE_PAGE = 60
    const val EXTREME_PLAY_COUNT = 220

    fun resolveDecision(
        ticket: TicketRecord,
        estimatedHeightPx: Int,
    ): TicketDeliveryDecision {
        val lotteryCount = groupPlaysByLottery(ticket.plays).size
        val playCount = ticket.plays.size
        val mode = when {
            playCount <= MAX_SINGLE_IMAGE_PLAYS &&
                lotteryCount <= MAX_LOTTERIES_FOR_SINGLE_IMAGE &&
                estimatedHeightPx <= MAX_SINGLE_IMAGE_HEIGHT_PX -> TicketDeliveryMode.SINGLE_IMAGE
            playCount >= EXTREME_PLAY_COUNT -> TicketDeliveryMode.TEXT_SUMMARY_FALLBACK
            else -> TicketDeliveryMode.PAGED_IMAGES
        }
        return TicketDeliveryDecision(
            mode = mode,
            estimatedHeightPx = estimatedHeightPx,
            playCount = playCount,
            lotteryCount = lotteryCount,
        )
    }

    fun buildPages(ticket: TicketRecord): List<TicketDeliveryPage> {
        val chunks = mutableListOf<Pair<String, List<PlayItem>>>()
        groupPlaysByLottery(ticket.plays).forEach { (lotteryName, plays) ->
            if (plays.size <= MAX_PLAYS_PER_LARGE_PAGE) {
                chunks += lotteryName to plays
            } else {
                plays.chunked(MAX_PLAYS_PER_LARGE_PAGE).forEachIndexed { index, chunk ->
                    chunks += "$lotteryName ${index + 1}/${plays.chunked(MAX_PLAYS_PER_LARGE_PAGE).size}" to chunk
                }
            }
        }
        if (chunks.isEmpty()) return emptyList()
        val total = chunks.size
        return chunks.mapIndexed { index, (label, plays) ->
            TicketDeliveryPage(
                index = index,
                totalPages = total,
                plays = plays,
                label = label,
            )
        }
    }

    fun shouldRenderPreviewBitmap(ticket: TicketRecord, estimatedHeightPx: Int): Boolean {
        return resolveDecision(ticket, estimatedHeightPx).mode == TicketDeliveryMode.SINGLE_IMAGE
    }

    private fun groupPlaysByLottery(plays: List<PlayItem>): Map<String, List<PlayItem>> {
        return plays.groupBy { play ->
            val primary = play.lotteryName.orEmpty().ifBlank { "Loteria" }
            val secondary = play.secondaryLotteryName.orEmpty().trim()
            if (secondary.isNotBlank()) "$primary / $secondary" else primary
        }.toList()
            .sortedBy { (name, _) -> name.lowercase(Locale.US) }
            .toMap()
    }
}
