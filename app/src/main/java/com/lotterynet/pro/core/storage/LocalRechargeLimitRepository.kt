package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

data class RechargeLimitSettings(
    val globalPerTx: Double = 0.0,
    val masterPerTx: Double = 0.0,
)

fun decodeRechargeLimitSettingsPayload(payload: Any?): RechargeLimitSettings? {
    val root = when (payload) {
        is JSONObject -> payload
        is String -> runCatching { JSONObject(payload) }.getOrNull()
        else -> null
    } ?: return null
    val global = root.optDouble("globalPerTx", Double.NaN)
    val master = root.optDouble("masterPerTx", Double.NaN)
    val legacyMaster = root.optDouble("recarga", Double.NaN)
    if (!global.isFinite() && !master.isFinite() && !legacyMaster.isFinite()) return null
    return RechargeLimitSettings(
        globalPerTx = if (global.isFinite()) global.coerceAtLeast(0.0) else 0.0,
        masterPerTx = when {
            master.isFinite() -> master.coerceAtLeast(0.0)
            legacyMaster.isFinite() -> legacyMaster.coerceAtLeast(0.0)
            else -> 0.0
        },
    )
}

class LocalRechargeLimitRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(RechargeLimitStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): RechargeLimitSettings {
        return RechargeLimitSettings(
            globalPerTx = prefs.getFloat(RechargeLimitStorageKeys.GLOBAL_LIMIT_KEY, 0f).toDouble().coerceAtLeast(0.0),
            masterPerTx = prefs.getFloat(RechargeLimitStorageKeys.MASTER_LIMIT_KEY, 0f).toDouble().coerceAtLeast(0.0),
        )
    }

    fun saveSettings(settings: RechargeLimitSettings) {
        prefs.edit {
            putFloat(RechargeLimitStorageKeys.GLOBAL_LIMIT_KEY, settings.globalPerTx.toFloat())
            putFloat(RechargeLimitStorageKeys.MASTER_LIMIT_KEY, settings.masterPerTx.toFloat())
        }
    }

    fun exportLegacyPayload(): String {
        val settings = getSettings()
        return JSONObject().apply {
            put("recarga", settings.masterPerTx)
        }.toString()
    }

    fun cacheLegacyPayload(payload: Any?) {
        val decoded = decodeRechargeLimitSettingsPayload(payload) ?: return
        val current = getSettings()
        saveSettings(
            current.copy(
                globalPerTx = if (decoded.globalPerTx > 0.0) decoded.globalPerTx else current.globalPerTx,
                masterPerTx = if (decoded.masterPerTx > 0.0) decoded.masterPerTx else current.masterPerTx,
            ),
        )
    }

    fun cacheRemotePayload(payload: Any?) {
        val decoded = decodeRechargeLimitSettingsPayload(payload) ?: return
        saveSettings(decoded)
    }
}
