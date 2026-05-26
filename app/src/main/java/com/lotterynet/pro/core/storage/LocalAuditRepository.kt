package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.AuditEntry
import org.json.JSONArray
import org.json.JSONObject

class LocalAuditRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(AuditStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getEntries(): List<AuditEntry> {
        val raw = prefs.getString(AuditStorageKeys.AUDIT_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        AuditEntry(
                            timestampLabel = item.optString("ts"),
                            user = item.optString("user"),
                            role = item.optString("role"),
                            action = item.optString("accion"),
                            detail = item.optString("detalle"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveEntries(entries: List<AuditEntry>) {
        prefs.edit {
            putString(
                AuditStorageKeys.AUDIT_KEY,
                JSONArray(entries.map(::toJson)).toString(),
            )
        }
    }

    fun exportPayload(): String {
        return prefs.getString(AuditStorageKeys.AUDIT_KEY, null) ?: "[]"
    }

    fun cachePayload(payloadJson: String?) {
        val payload = payloadJson?.takeIf { it.isNotBlank() } ?: return
        runCatching {
            JSONArray(payload)
        }.onSuccess {
            prefs.edit { putString(AuditStorageKeys.AUDIT_KEY, payload) }
        }
    }

    private fun toJson(entry: AuditEntry): JSONObject {
        return JSONObject().apply {
            put("ts", entry.timestampLabel)
            put("user", entry.user)
            put("role", entry.role)
            put("accion", entry.action)
            put("detalle", entry.detail)
        }
    }
}
