package com.lotterynet.pro.core.results

import android.util.Log
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.repository.ResultsRepository
import org.json.JSONArray

class LegacyResultsCacheBridge(
    private val resultsRepository: ResultsRepository,
    private val logger: (String, Throwable) -> Unit,
) {
    fun cacheResultsPayload(dateKey: String?, payloadJson: String?) {
        val safeDate = dateKey?.trim()?.takeIf { it.isNotBlank() } ?: return
        val payload = payloadJson?.takeIf { it.isNotBlank() } ?: return
        try {
            val rows = JSONArray(payload)
            val mapped = buildList(rows.length()) {
                for (index in 0 until rows.length()) {
                    val item = rows.optJSONObject(index) ?: continue
                    val lotteryId = item.optString("id").trim()
                    if (lotteryId.isBlank()) continue
                    val normalizedNumber = item.optString("number").trim()
                    val parts = normalizedNumber.split("-").map { it.trim() }
                    add(
                        LotteryResult(
                            lotteryId = lotteryId,
                            lotteryName = item.optString("name").ifBlank { null },
                            date = item.optString("date").ifBlank { safeDate },
                            first = item.optString("first").ifBlank { parts.getOrNull(0).orEmpty() }.ifBlank { null },
                            second = item.optString("second").ifBlank { parts.getOrNull(1).orEmpty() }.ifBlank { null },
                            third = item.optString("third").ifBlank { parts.getOrNull(2).orEmpty() }.ifBlank { null },
                            pick3 = item.optString("pick3").ifBlank { null },
                            pick4 = item.optString("pick4").ifBlank { null },
                            source = "web-cache",
                        ),
                    )
                }
            }
            resultsRepository.saveResultsForDate(safeDate, mapped)
        } catch (e: Exception) {
            logger("cacheResultsPayload failed", e)
        }
    }

    companion object {
        fun androidLogger(tag: String): (String, Throwable) -> Unit = { message, error ->
            Log.w(tag, message, error)
        }
    }
}
