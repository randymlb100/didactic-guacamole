package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.WinningPlayDetail
import org.json.JSONArray
import org.json.JSONObject

class SalesDayTicketCache(
    private val maxEntries: Int = 14,
) {
    private data class Entry(
        val rawHash: Int,
        val tickets: List<TicketRecord>,
    )

    private val entries = object : LinkedHashMap<String, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean {
            return size > maxEntries
        }
    }

    @Synchronized
    fun getOrParse(dayKey: String, raw: String?): List<TicketRecord> {
        val normalizedRaw = raw.orEmpty()
        val rawHash = normalizedRaw.hashCode()
        entries[dayKey]?.takeIf { it.rawHash == rawHash }?.let { return it.tickets }
        val parsed = parseTickets(normalizedRaw)
        entries[dayKey] = Entry(rawHash = rawHash, tickets = parsed)
        return parsed
    }

    @Synchronized
    fun invalidate(dayKey: String) {
        entries.remove(dayKey)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun parseTickets(raw: String): List<TicketRecord> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(ticketFromJson(item))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun ticketFromJson(json: JSONObject): TicketRecord {
        val playsArray = json.optJSONArray("plays") ?: JSONArray()
        val winningDetailsArray = json.optJSONArray("winningDetails") ?: JSONArray()
        return TicketRecord(
            id = json.optString("id"),
            serial = json.optString("serial").takeIf { it.isNotBlank() },
            securityCode = json.optString("securityCode").takeIf { it.isNotBlank() },
            sellerId = json.optString("sellerId").takeIf { it.isNotBlank() },
            sellerUser = json.optString("sellerUser").takeIf { it.isNotBlank() },
            adminId = json.optString("adminId").takeIf { it.isNotBlank() },
            adminUser = json.optString("adminUser").takeIf { it.isNotBlank() },
            role = runCatching { UserRole.valueOf(json.optString("role")) }.getOrDefault(UserRole.UNKNOWN),
            createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis()),
            plays = buildList {
                for (index in 0 until playsArray.length()) {
                    val item = playsArray.optJSONObject(index) ?: continue
                    add(
                        PlayItem(
                            number = item.optString("number"),
                            playType = item.optString("playType"),
                            amount = item.optDouble("amount", 0.0),
                            lotteryId = item.optString("lotteryId").takeIf { it.isNotBlank() },
                            lotteryName = item.optString("lotteryName").takeIf { it.isNotBlank() },
                            secondaryLotteryId = item.optString("secondaryLotteryId").takeIf { it.isNotBlank() },
                            secondaryLotteryName = item.optString("secondaryLotteryName").takeIf { it.isNotBlank() },
                            straightDigits = item.optString("straightDigits").takeIf { it.isNotBlank() },
                            boxDigits = item.optString("boxDigits").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            },
            subtotal = json.optDouble("subtotal", 0.0),
            discount = json.optDouble("discount", 0.0),
            total = json.optDouble("total", 0.0),
            totalPrize = json.optDouble("totalPrize", 0.0),
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
            status = json.optString("status", "active"),
            note = json.optString("note").takeIf { it.isNotBlank() },
        )
    }
}
