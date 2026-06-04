package com.lotterynet.pro.core.render

import com.lotterynet.pro.core.model.ResultShareRow
import com.lotterynet.pro.core.model.TicketRecord
import java.security.MessageDigest

fun ticketRenderCacheKey(ticket: TicketRecord, bancaName: String, logoUri: String): String {
    val raw = buildString {
        append("ticket|")
        append(ticket.id).append('|')
        append(ticket.serial.orEmpty()).append('|')
        append(ticket.securityCode.orEmpty()).append('|')
        append(ticket.status).append('|')
        append(ticket.total).append('|')
        append(ticket.totalPrize).append('|')
        append(bancaName).append('|')
        append(logoUri).append('|')
        ticket.plays.forEach { play ->
            append(play.lotteryId.orEmpty()).append(':')
            append(play.lotteryName.orEmpty()).append(':')
            append(play.playType).append(':')
            append(play.number).append(':')
            append(play.amount).append(';')
        }
        ticket.winningDetails.forEach { detail ->
            append("win:")
            append(detail.lotteryName).append(':')
            append(detail.playType).append(':')
            append(detail.playedNumber).append(':')
            append(detail.resultNumber).append(':')
            append(detail.hitPosition).append(':')
            append(detail.payoutAmount).append(';')
        }
    }
    return "ticket-${sha256Short(raw)}"
}

fun resultsRenderCacheKey(
    date: String,
    rows: List<ResultShareRow>,
    pageIndex: Int,
    template: String = "default",
): String {
    val raw = buildString {
        append("results|").append(date).append('|').append(pageIndex).append('|').append(template).append('|')
        rows.forEach { row ->
            append(row.displayName).append(':')
            append(row.drawTimeLabel.orEmpty()).append(':')
            append(row.logoAssetPath.orEmpty()).append(':')
            append(row.first).append(',')
            append(row.second).append(',')
            append(row.third).append(',')
            append(row.pick3.orEmpty()).append(',')
            append(row.pick4.orEmpty()).append(';')
        }
    }
    return "results-${sha256Short(raw)}"
}

private fun sha256Short(raw: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.take(16).joinToString("") { byte -> "%02x".format(byte) }
}
