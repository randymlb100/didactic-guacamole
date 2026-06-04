package com.lotterynet.pro.ui.tickets

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.WinningPlayDetail
import com.lotterynet.pro.core.model.isPaidStatus
import com.lotterynet.pro.core.model.isPendingWinnerStatus
import org.json.JSONArray
import org.json.JSONObject

internal fun resolveInitialOfficialTicket(
    snapshot: TicketRecord?,
    refreshed: TicketRecord?,
): TicketRecord? {
    snapshot ?: return refreshed
    refreshed ?: return snapshot
    return when {
        refreshed.isOfficialPayRelevant() && !snapshot.isOfficialPayRelevant() -> refreshed
        snapshot.isOfficialPayRelevant() && !refreshed.isOfficialPayRelevant() -> {
            snapshot.withBetterOfficialDisplayDetailsFrom(refreshed)
        }
        refreshed.isOfficialPayRelevant() && snapshot.isOfficialPayRelevant() && refreshed.totalPrize > snapshot.totalPrize -> refreshed
        refreshed.hasBetterOfficialDisplayDetailsThan(snapshot) -> refreshed
        else -> snapshot
    }
}

internal fun findOfficialTicketCandidate(
    tickets: List<TicketRecord>,
    ticketId: String,
    snapshot: TicketRecord?,
): TicketRecord? {
    val targetIds = buildSet {
        ticketId.trim().takeIf { it.isNotBlank() }?.let(::add)
        snapshot?.id?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
    }
    val targetSerials = buildSet {
        ticketId.trim().takeIf { it.isNotBlank() }?.let(::add)
        snapshot?.serial?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
    }
    return tickets.firstOrNull { ticket ->
        targetIds.any { target -> ticket.id.equals(target, ignoreCase = true) }
    } ?: tickets.firstOrNull { ticket ->
        ticket.serial?.let { serial ->
            targetSerials.any { target -> serial.equals(target, ignoreCase = true) }
        } == true
    }
}

internal fun TicketRecord.isOfficialPayRelevant(): Boolean {
    return isPaidStatus() || isPendingWinnerStatus() || totalPrize > 0.0
}

private fun TicketRecord.withBetterOfficialDisplayDetailsFrom(other: TicketRecord): TicketRecord {
    return if (other.hasBetterOfficialDisplayDetailsThan(this)) {
        copy(plays = other.plays)
    } else {
        this
    }
}

private fun TicketRecord.hasBetterOfficialDisplayDetailsThan(other: TicketRecord): Boolean {
    val validPlayCount = officialValidPlayCount()
    val otherValidPlayCount = other.officialValidPlayCount()
    if (validPlayCount > otherValidPlayCount) return true
    if (validPlayCount < otherValidPlayCount) return false
    val lotteryNameCount = plays.count { !it.lotteryName.isNullOrBlank() }
    val otherLotteryNameCount = other.plays.count { !it.lotteryName.isNullOrBlank() }
    if (lotteryNameCount > otherLotteryNameCount) return true
    if (lotteryNameCount < otherLotteryNameCount) return false
    val winningCount = winningDetails.count { it.playedNumber.isNotBlank() && it.payoutAmount > 0.0 }
    val otherWinningCount = other.winningDetails.count { it.playedNumber.isNotBlank() && it.payoutAmount > 0.0 }
    return winningCount > otherWinningCount
}

private fun TicketRecord.officialValidPlayCount(): Int {
    return plays.count { play ->
        play.number.isNotBlank() &&
            play.playType.isNotBlank() &&
            !play.lotteryName.isNullOrBlank() &&
            play.amount > 0.0
    }
}

internal fun encodeTicketRecordSnapshot(ticket: TicketRecord): String {
    return JSONObject().apply {
        put("id", ticket.id)
        put("serial", ticket.serial)
        put("securityCode", ticket.securityCode)
        put("sellerId", ticket.sellerId)
        put("sellerUser", ticket.sellerUser)
        put("adminId", ticket.adminId)
        put("adminUser", ticket.adminUser)
        put("role", ticket.role.name)
        put("createdAtEpochMs", ticket.createdAtEpochMs)
        put("drawDateKey", ticket.drawDateKey)
        put("subtotal", ticket.subtotal)
        put("discount", ticket.discount)
        put("total", ticket.total)
        put("totalPrize", ticket.totalPrize)
        put(
            "winningDetails",
            JSONArray(ticket.winningDetails.map { detail ->
                JSONObject().apply {
                    put("lotteryName", detail.lotteryName)
                    put("playType", detail.playType)
                    put("playedNumber", detail.playedNumber)
                    put("resultNumber", detail.resultNumber)
                    put("hitPosition", detail.hitPosition)
                    put("amount", detail.amount)
                    put("payoutAmount", detail.payoutAmount)
                }
            }),
        )
        put("status", ticket.status)
        put("note", ticket.note)
        put(
            "plays",
            JSONArray(ticket.plays.map { play ->
                JSONObject().apply {
                    put("number", play.number)
                    put("playType", play.playType)
                    put("amount", play.amount)
                    put("lotteryId", play.lotteryId)
                    put("lotteryName", play.lotteryName)
                    put("secondaryLotteryId", play.secondaryLotteryId)
                    put("secondaryLotteryName", play.secondaryLotteryName)
                    put("straightDigits", play.straightDigits)
                    put("boxDigits", play.boxDigits)
                }
            }),
        )
    }.toString()
}

internal fun decodeTicketRecordSnapshot(payload: String?): TicketRecord? {
    val raw = payload?.trim().orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        val json = JSONObject(raw)
        val playsArray = json.optJSONArray("plays") ?: JSONArray()
        val winningDetailsArray = json.optJSONArray("winningDetails") ?: JSONArray()
        TicketRecord(
            id = json.optString("id"),
            serial = json.optString("serial").takeIf { it.isNotBlank() },
            securityCode = json.optString("securityCode").takeIf { it.isNotBlank() },
            sellerId = json.optString("sellerId").takeIf { it.isNotBlank() },
            sellerUser = json.optString("sellerUser").takeIf { it.isNotBlank() },
            adminId = json.optString("adminId").takeIf { it.isNotBlank() },
            adminUser = json.optString("adminUser").takeIf { it.isNotBlank() },
            role = runCatching { UserRole.valueOf(json.optString("role")) }.getOrDefault(UserRole.UNKNOWN),
            createdAtEpochMs = json.optLong("createdAtEpochMs"),
            drawDateKey = json.optString("drawDateKey").takeIf { it.isNotBlank() },
            subtotal = json.optDouble("subtotal"),
            discount = json.optDouble("discount"),
            total = json.optDouble("total"),
            totalPrize = json.optDouble("totalPrize"),
            winningDetails = buildList {
                for (index in 0 until winningDetailsArray.length()) {
                    val item = winningDetailsArray.optJSONObject(index) ?: continue
                    add(
                        WinningPlayDetail(
                            lotteryName = item.optString("lotteryName"),
                            playType = item.optString("playType"),
                            playedNumber = item.optString("playedNumber"),
                            resultNumber = item.optString("resultNumber"),
                            hitPosition = item.optString("hitPosition"),
                            amount = item.optDouble("amount", 0.0),
                            payoutAmount = item.optDouble("payoutAmount", 0.0),
                        ),
                    )
                }
            },
            status = json.optString("status"),
            note = json.optString("note").takeIf { it.isNotBlank() },
            plays = buildList {
                for (index in 0 until playsArray.length()) {
                    val play = playsArray.optJSONObject(index) ?: continue
                    add(
                        PlayItem(
                            number = play.optString("number"),
                            playType = play.optString("playType"),
                            amount = play.optDouble("amount"),
                            lotteryId = play.optString("lotteryId").takeIf { it.isNotBlank() },
                            lotteryName = play.optString("lotteryName").takeIf { it.isNotBlank() },
                            secondaryLotteryId = play.optString("secondaryLotteryId").takeIf { it.isNotBlank() },
                            secondaryLotteryName = play.optString("secondaryLotteryName").takeIf { it.isNotBlank() },
                            straightDigits = play.optString("straightDigits").takeIf { it.isNotBlank() },
                            boxDigits = play.optString("boxDigits").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            },
        )
    }.getOrNull()
}
