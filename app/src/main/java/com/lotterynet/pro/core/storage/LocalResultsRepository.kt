package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.repository.ResultsRepository
import org.json.JSONArray
import org.json.JSONObject

class LocalResultsRepository(
    context: Context,
) : ResultsRepository {
    private val prefs = context.getSharedPreferences(ResultsStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    override fun getResultsForDate(date: String): List<LotteryResult> {
        val payload = prefs.getString(ResultsStorageKeys.RESULTS_PREFIX + date, null) ?: return emptyList()
        return try {
            val rows = JSONArray(payload)
            buildList(rows.length()) {
                for (index in 0 until rows.length()) {
                    val item = rows.optJSONObject(index) ?: continue
                    add(
                        LotteryResult(
                            lotteryId = item.optString("lotteryId").ifBlank {
                                item.optString("id")
                            },
                            lotteryName = item.optString("lotteryName").ifBlank {
                                item.optString("name")
                            }.ifBlank { null },
                            date = item.optString("date").ifBlank { date },
                            first = item.optString("first").ifBlank { null },
                            second = item.optString("second").ifBlank { null },
                            third = item.optString("third").ifBlank { null },
                            pick3 = item.optString("pick3").ifBlank { null },
                            pick4 = item.optString("pick4").ifBlank { null },
                            source = item.optString("source").ifBlank { null },
                            status = item.optString("status").ifBlank { null },
                            fetchedAtEpochMs = item.optLong("fetchedAtEpochMs", System.currentTimeMillis()),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun saveResultsForDate(date: String, results: List<LotteryResult>) {
        val payload = JSONArray().apply {
            results.forEach { row ->
                put(
                    JSONObject().apply {
                        put("lotteryId", row.lotteryId)
                        put("lotteryName", row.lotteryName)
                        put("date", row.date)
                        put("first", row.first)
                        put("second", row.second)
                        put("third", row.third)
                        put("pick3", row.pick3)
                        put("pick4", row.pick4)
                        put("source", row.source)
                        put("status", row.status)
                        put("fetchedAtEpochMs", row.fetchedAtEpochMs)
                    },
                )
            }
        }.toString()
        prefs.edit {
            putString(ResultsStorageKeys.RESULTS_PREFIX + date, payload)
        }
    }

    override fun clearResultsForDate(date: String) {
        prefs.edit {
            remove(ResultsStorageKeys.RESULTS_PREFIX + date)
        }
    }
}
