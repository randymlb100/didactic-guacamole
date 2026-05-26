package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

class LocalPresenceRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PresenceStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getPayload(): JSONObject {
        val raw = prefs.getString(PresenceStorageKeys.PRESENCE_KEY, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    fun exportPayload(): String {
        return prefs.getString(PresenceStorageKeys.PRESENCE_KEY, null) ?: "{}"
    }

    fun cachePayload(payloadJson: String?) {
        val payload = payloadJson?.takeIf { it.isNotBlank() } ?: return
        runCatching { JSONObject(payload) }.onSuccess {
            prefs.edit { putString(PresenceStorageKeys.PRESENCE_KEY, payload) }
        }
    }
}
