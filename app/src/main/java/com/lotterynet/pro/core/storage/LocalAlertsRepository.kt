package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.SystemAlert
import org.json.JSONArray
import org.json.JSONObject

class LocalAlertsRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(AlertStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getAlerts(): List<SystemAlert> {
        val raw = prefs.getString(AlertStorageKeys.ALERTS_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        SystemAlert(
                            id = item.opt("id")?.toString().orEmpty(),
                            timestampLabel = item.optString("ts"),
                            type = item.optString("tipo"),
                            message = item.optString("msg"),
                            level = item.optString("nivel", "info"),
                            read = item.optBoolean("leida", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveAlerts(alerts: List<SystemAlert>) {
        prefs.edit {
            putString(
                AlertStorageKeys.ALERTS_KEY,
                JSONArray(alerts.map(::toJson)).toString(),
            )
        }
    }

    fun exportPayload(): String {
        return prefs.getString(AlertStorageKeys.ALERTS_KEY, null) ?: "[]"
    }

    fun cachePayload(payloadJson: String?) {
        val payload = payloadJson?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            JSONArray(payload)
        }.onSuccess {
            prefs.edit { putString(AlertStorageKeys.ALERTS_KEY, payload) }
        }
    }

    fun markRead(alertId: String) {
        val updated = getAlerts().map { alert ->
            if (alert.id == alertId) alert.copy(read = true) else alert
        }
        saveAlerts(updated)
    }

    private fun toJson(alert: SystemAlert): JSONObject {
        return JSONObject().apply {
            put("id", alert.id)
            put("ts", alert.timestampLabel)
            put("tipo", alert.type)
            put("msg", alert.message)
            put("nivel", alert.level)
            put("leida", alert.read)
        }
    }
}
