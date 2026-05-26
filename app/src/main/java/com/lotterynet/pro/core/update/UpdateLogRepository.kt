package com.lotterynet.pro.core.update

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

class UpdateLogRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lotterynet_ota_logs", Context.MODE_PRIVATE)

    fun append(event: String, message: String? = null, targetVersionCode: Int? = null) {
        val next = JSONArray(prefs.getString(KEY_LOGS, "[]"))
        next.put(JSONObject().apply {
            put("event", event)
            put("message", message)
            put("targetVersionCode", targetVersionCode)
            put("createdAt", System.currentTimeMillis())
        })
        val trimmed = JSONArray()
        val start = (next.length() - MAX_LOGS).coerceAtLeast(0)
        for (index in start until next.length()) {
            trimmed.put(next.getJSONObject(index))
        }
        prefs.edit { putString(KEY_LOGS, trimmed.toString()) }
    }

    fun all(): JSONArray = JSONArray(prefs.getString(KEY_LOGS, "[]"))

    companion object {
        private const val KEY_LOGS = "logs"
        private const val MAX_LOGS = 80
    }
}
