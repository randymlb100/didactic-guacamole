package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.PickPlayMode
import com.lotterynet.pro.core.model.SaleDraft
import com.lotterynet.pro.core.model.SaleDraftSnapshot
import com.lotterynet.pro.core.model.SaleStagedRow
import org.json.JSONArray
import org.json.JSONObject

class LocalSaleDraftRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(SalesStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun load(session: ActiveSession?): SaleDraftSnapshot? {
        val key = getDraftStorageKey(session)
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val rows = json.optJSONArray("rows") ?: JSONArray()
            SaleDraftSnapshot(
                draft = SaleDraft(
                    selectedLotteryIds = buildList {
                        val selected = json.optJSONArray("selectedLotteryIds") ?: JSONArray()
                        for (index in 0 until selected.length()) {
                            add(selected.optString(index))
                        }
                    },
                    secondaryLotteryId = json.optString("secondaryLotteryId").takeIf { it.isNotBlank() },
                    numberInput = json.optString("numberInput"),
                    amountInput = json.optString("amountInput"),
                    classicMode = json.optString("classicMode", "Q"),
                    pickMode = runCatching { PickPlayMode.valueOf(json.optString("pickMode", PickPlayMode.STRAIGHT.name)) }
                        .getOrDefault(PickPlayMode.STRAIGHT),
                    superPaleEnabled = json.optBoolean("superPaleEnabled", false),
                ),
                stagedRows = buildList {
                    for (index in 0 until rows.length()) {
                        val item = rows.optJSONObject(index) ?: continue
                        add(
                            SaleStagedRow(
                                id = item.optString("id"),
                                lotteryId = item.optString("lotteryId"),
                                lotteryName = item.optString("lotteryName"),
                                secondaryLotteryId = item.optString("secondaryLotteryId").takeIf { it.isNotBlank() },
                                secondaryLotteryName = item.optString("secondaryLotteryName").takeIf { it.isNotBlank() },
                                playType = item.optString("playType"),
                                label = item.optString("label"),
                                number = item.optString("number"),
                                displayNumber = item.optString("displayNumber"),
                                amount = item.optDouble("amount", 0.0),
                            ),
                        )
                    }
                },
                savedAtEpochMs = json.optLong("savedAtEpochMs", System.currentTimeMillis()),
            )
        }.getOrNull()
    }

    fun save(session: ActiveSession?, snapshot: SaleDraftSnapshot?) {
        val key = getDraftStorageKey(session)
        prefs.edit {
            if (snapshot == null || !hasDraft(snapshot)) {
                remove(key)
                return@edit
            }
            putString(
                key,
                JSONObject().apply {
                    put(
                        "selectedLotteryIds",
                        JSONArray(snapshot.draft.selectedLotteryIds),
                    )
                    put("secondaryLotteryId", snapshot.draft.secondaryLotteryId)
                    put("numberInput", snapshot.draft.numberInput)
                    put("amountInput", snapshot.draft.amountInput)
                    put("classicMode", snapshot.draft.classicMode)
                    put("pickMode", snapshot.draft.pickMode.name)
                    put("superPaleEnabled", snapshot.draft.superPaleEnabled)
                    put(
                        "rows",
                        JSONArray(snapshot.stagedRows.map { row ->
                            JSONObject().apply {
                                put("id", row.id)
                                put("lotteryId", row.lotteryId)
                                put("lotteryName", row.lotteryName)
                                put("secondaryLotteryId", row.secondaryLotteryId)
                                put("secondaryLotteryName", row.secondaryLotteryName)
                                put("playType", row.playType)
                                put("label", row.label)
                                put("number", row.number)
                                put("displayNumber", row.displayNumber)
                                put("amount", row.amount)
                            }
                        }),
                    )
                    put("savedAtEpochMs", snapshot.savedAtEpochMs)
                }.toString(),
            )
        }
    }

    fun clear(session: ActiveSession?) {
        prefs.edit { remove(getDraftStorageKey(session)) }
    }

    private fun getDraftStorageKey(session: ActiveSession?): String {
        val owner = session?.adminId ?: session?.userId ?: session?.username ?: "guest"
        return "bv_sale_draft_native_$owner"
    }

    private fun hasDraft(snapshot: SaleDraftSnapshot): Boolean {
        return snapshot.draft.selectedLotteryIds.isNotEmpty() ||
            snapshot.draft.numberInput.isNotBlank() ||
            snapshot.draft.amountInput.isNotBlank() ||
            snapshot.stagedRows.isNotEmpty()
    }
}
