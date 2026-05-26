package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONObject

private const val MASTER_REMOTE_CACHE_TTL_MS = 30_000L

class SupabaseMasterConfigRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
) : MasterConfigRemoteStore {
    fun refreshValue(key: String): Any? = fetchValue(key)

    override fun probeAccess() {
        edgeClient.invoke("get-master-config", JSONObject().put("action", "probe"))
    }

    override fun fetchValue(key: String): Any? {
        readMasterValueMemoryCache(key)?.let { return it }
        return edgeClient.invoke(
            "get-master-config",
            JSONObject()
                .put("action", "fetch")
                .put("key", key),
        ).opt("payload").also {
            cacheMasterValueMemory(key, it)
        }
    }

    override fun fetchUpdatedAt(key: String): String? {
        readMasterUpdatedAtMemoryCache(key)?.let { return it }
        return edgeClient.invoke(
            "get-master-config",
            JSONObject()
                .put("action", "updated-at")
                .put("key", key),
        ).optString("updatedAt").ifBlank { null }.also {
            cacheMasterUpdatedAtMemory(key, it)
        }
    }

    override fun upsertJsonValue(key: String, rawJsonValue: String) {
        edgeClient.invoke(
            "update-master-config",
            JSONObject()
                .put("key", key)
                .put("payload", JSONObject("{\"value\":$rawJsonValue}").opt("value")),
        )
        clearMasterMemoryCache(key)
    }
}

private var masterValueMemoryCache = mutableMapOf<String, Pair<Any?, Long>>()
private var masterUpdatedAtMemoryCache = mutableMapOf<String, Pair<String?, Long>>()

internal fun clearMasterMemoryCache(key: String? = null) {
    if (key == null) {
        masterValueMemoryCache = mutableMapOf()
        masterUpdatedAtMemoryCache = mutableMapOf()
    } else {
        masterValueMemoryCache.remove(key)
        masterUpdatedAtMemoryCache.remove(key)
    }
}

internal fun cacheMasterValueMemory(key: String, value: Any?, nowMs: Long = System.currentTimeMillis()) {
    masterValueMemoryCache[key] = value to nowMs
}

internal fun readMasterValueMemoryCache(key: String, nowMs: Long = System.currentTimeMillis()): Any? {
    val entry = masterValueMemoryCache[key] ?: return null
    return if (nowMs - entry.second <= MASTER_REMOTE_CACHE_TTL_MS) {
        entry.first
    } else {
        masterValueMemoryCache.remove(key)
        null
    }
}

internal fun cacheMasterUpdatedAtMemory(key: String, value: String?, nowMs: Long = System.currentTimeMillis()) {
    masterUpdatedAtMemoryCache[key] = value to nowMs
}

internal fun readMasterUpdatedAtMemoryCache(key: String, nowMs: Long = System.currentTimeMillis()): String? {
    val entry = masterUpdatedAtMemoryCache[key] ?: return null
    return if (nowMs - entry.second <= MASTER_REMOTE_CACHE_TTL_MS) {
        entry.first
    } else {
        masterUpdatedAtMemoryCache.remove(key)
        null
    }
}
