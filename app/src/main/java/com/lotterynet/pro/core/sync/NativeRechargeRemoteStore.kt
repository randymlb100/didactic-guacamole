package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.RechargeRecord
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONArray
import org.json.JSONObject

class NativeRechargeRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
) {
    fun fetchRecharges(ownerKey: String): List<RechargeRecord> {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return emptyList()
        val payload = edgeClient.invoke(
            "recharge-history-state",
            JSONObject()
                .put("action", "fetch")
                .put("ownerKey", key),
        ).opt("payload") ?: return emptyList()
        return parseWebRechargesPayload(payload.toRawJsonString())
    }

    fun upsertRecharges(ownerKey: String, recharges: List<RechargeRecord>) {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return
        val payload = JSONArray(recharges.map(::rechargeRecordToWebCompatibleJson)).toString()
        edgeClient.invoke(
            "recharge-history-state",
            JSONObject()
                .put("action", "upsert")
                .put("ownerKey", key)
                .put("payload", JSONArray(payload)),
        )
    }

    private fun Any.toRawJsonString(): String {
        return when (this) {
            is JSONArray -> toString()
            is JSONObject -> toString()
            is String -> this
            else -> toString()
        }
    }
}
