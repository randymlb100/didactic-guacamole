package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import org.json.JSONObject

private const val MASTER_REMOTE_CACHE_TTL_MS = 300_000L

class SupabaseMasterConfigRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
    private val bearerTokenProvider: (() -> String?)? = null,
) : MasterConfigRemoteStore {
    fun refreshValue(key: String): Any? {
        clearMasterMemoryCache(key)
        return fetchValue(key)
    }

    override fun probeAccess() {
        invokeMasterConfig(JSONObject().put("action", "probe"))
    }

    override fun fetchValue(key: String): Any? {
        readMasterValueMemoryCache(key)?.let { return it }
        return invokeMasterConfig(
            JSONObject()
                .put("action", "fetch")
                .put("key", key),
        ).opt("payload").also {
            cacheMasterValueMemory(key, it)
        }
    }

    override fun fetchUpdatedAt(key: String): String? {
        readMasterUpdatedAtMemoryCache(key)?.let { return it }
        return invokeMasterConfig(
            JSONObject()
                .put("action", "updated-at")
                .put("key", key),
        ).optString("updatedAt").ifBlank { null }.also {
            cacheMasterUpdatedAtMemory(key, it)
        }
    }

    override fun upsertJsonValue(key: String, rawJsonValue: String) {
        edgeClient.invokeAuthenticated(
            "update-master-config",
            JSONObject()
                .put("key", key)
                .put("payload", JSONObject("{\"value\":$rawJsonValue}").opt("value")),
            bearerTokenProvider?.invoke(),
        )
        clearMasterMemoryCache(key)
    }

    private fun invokeMasterConfig(payload: JSONObject): JSONObject {
        val bearerToken = bearerTokenProvider?.invoke()?.takeIf { it.isNotBlank() }
        return coalescedMasterConfig(
            requestKey = buildMasterRequestKey(payload, bearerToken),
        ) {
            if (bearerToken == null) {
                edgeClient.invoke("get-master-config", payload)
            } else {
                edgeClient.invokeAuthenticated("get-master-config", payload, bearerToken)
            }
        }
    }
}

private var masterValueMemoryCache = mutableMapOf<String, Pair<Any?, Long>>()
private var masterUpdatedAtMemoryCache = mutableMapOf<String, Pair<String?, Long>>()
private val masterConfigInFlightRequests = ConcurrentHashMap<String, FutureTask<JSONObject>>()

private fun buildMasterRequestKey(payload: JSONObject, bearerToken: String?): String {
    return listOf(
        "get-master-config",
        authScopeKey(bearerToken),
        payload.toString(),
    ).joinToString("|")
}

private fun authScopeKey(bearerToken: String?): String {
    return if (bearerToken.isNullOrBlank()) "anon" else "auth"
}

private fun coalescedMasterConfig(
    requestKey: String,
    block: () -> JSONObject,
): JSONObject {
    while (true) {
        val existing = masterConfigInFlightRequests[requestKey]
        if (existing != null) return existing.get()

        val task = FutureTask { block() }
        val previous = masterConfigInFlightRequests.putIfAbsent(requestKey, task)
        if (previous == null) {
            try {
                task.run()
                return task.get()
            } finally {
                masterConfigInFlightRequests.remove(requestKey, task)
            }
        }
    }
}

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
