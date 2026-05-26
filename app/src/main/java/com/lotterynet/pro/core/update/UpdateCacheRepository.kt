package com.lotterynet.pro.core.update

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

class UpdateCacheRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lotterynet_ota_cache", Context.MODE_PRIVATE)

    fun getCachedUpdate(): OtaUpdateInfo? {
        val raw = prefs.getString(KEY_UPDATE, null) ?: return null
        return runCatching { OtaUpdateInfo.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun saveCachedUpdate(info: OtaUpdateInfo) {
        prefs.edit { putString(KEY_UPDATE, info.toJson().toString()) }
    }

    fun clearCachedUpdate() {
        prefs.edit { remove(KEY_UPDATE) }
    }

    fun getLastCheckEpochMs(): Long = prefs.getLong(KEY_LAST_CHECK, 0L)

    fun markChecked(nowEpochMs: Long = System.currentTimeMillis()) {
        prefs.edit { putLong(KEY_LAST_CHECK, nowEpochMs) }
    }

    fun shouldSkipNetworkCheck(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val cached = getCachedUpdate() ?: return false
        val ttlMs = cached.cacheTtlSeconds.coerceAtLeast(60L) * 1000L
        return nowEpochMs - getLastCheckEpochMs() in 0 until ttlMs
    }

    companion object {
        private const val KEY_UPDATE = "cached_update"
        private const val KEY_LAST_CHECK = "last_check_epoch_ms"
    }
}
