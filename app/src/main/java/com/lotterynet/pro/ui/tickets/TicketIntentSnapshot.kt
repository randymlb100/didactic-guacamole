package com.lotterynet.pro.ui.tickets

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
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
        snapshot.isOfficialPayRelevant() && !refreshed.isOfficialPayRelevant() -> snapshot
        refreshed.isOfficialPayRelevant() && snapshot.isOfficialPayRelevant() && refreshed.totalPrize > snapshot.totalPrize -> refreshed
        else -> snapshot
    }
}

internal fun TicketRecord.isOfficialPayRelevant(): Boolean {
    return isPaidStatus() || isPendingWinnerStatus() || totalPrize > 0.0
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
