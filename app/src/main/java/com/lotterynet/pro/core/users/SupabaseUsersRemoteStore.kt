package com.lotterynet.pro.core.users

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.master.MasterRechargeFundServerReceipt
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import org.json.JSONArray
import org.json.JSONObject

class SupabaseUsersRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
    private val renderClient: RenderUsersRemoteClient = RenderUsersRemoteClient(),
    private val bearerTokenProvider: () -> String? = { null },
) : UsersRemoteStore {
    fun refreshUsersPayload(forceRemote: Boolean = false): String? {
        if (forceRemote) {
            clearUsersPayloadMemoryCache()
        }
        return fetchUsersPayload()
    }

    override fun fetchUsersPayload(): String? {
        readUsersPayloadMemoryCache()?.let { return it }
        return coalescedUsersPayloadFetch {
            resolveUsersPayloadFetch(
                fetchLegacy = {
                    val value = edgeClient.invoke(
                        "lotterynet-users-state",
                        buildUsersStateFunctionPayload("fetch", null),
                    ).opt("payload") ?: return@resolveUsersPayloadFetch null
                    when (value) {
                        is JSONObject -> value.toString()
                        is String -> value
                        else -> value.toString()
                    }
                },
                fetchRender = { renderClient.fetchUsersPayload() },
            )
        }
    }

    override fun upsertUsersPayload(payloadJson: String) {
        upsertUsersPayload(payloadJson, emptySet())
    }

    fun upsertUsersPayload(payloadJson: String, commissionOverrideKeys: Set<String>) {
        persistUsersPayload(
            saveLegacy = {
                edgeClient.invokeAuthenticated(
                    "lotterynet-users-state",
                    buildUsersStateFunctionPayload("upsert", payloadJson, commissionOverrideKeys),
                    bearerTokenProvider(),
                )
            },
            saveRender = { renderClient.upsertUsersPayload(payloadJson) },
        )
        cacheUsersPayloadMemory(payloadJson)
    }

    fun updateMasterRechargeFund(
        accountId: String,
        enabled: Boolean,
        amount: Double,
    ): MasterRechargeFundServerReceipt {
        val response = edgeClient.invokeAuthenticated(
            "lotterynet-users-state",
            buildMasterRechargeFundPayload(accountId, enabled, amount),
            bearerTokenProvider(),
        )
        if (!response.optBoolean("confirmed", false)) {
            throw IllegalStateException(response.optString("message").ifBlank { "El servidor no confirmó el fondo." })
        }
        clearUsersPayloadMemoryCache()
        return MasterRechargeFundServerReceipt(
            requestedAmount = response.getDouble("requestedAmount"),
            persistedAmount = response.getDouble("persistedAmount"),
            updatedAt = response.optString("updatedAt").ifBlank { null },
        )
    }

    fun updateRechargeBalance(
        accountId: String,
        amount: Double,
    ): MasterRechargeFundServerReceipt {
        val response = edgeClient.invokeAuthenticated(
            "lotterynet-users-state",
            buildRechargeBalancePayload(accountId, amount),
            bearerTokenProvider(),
        )
        if (!response.optBoolean("confirmed", false)) {
            throw IllegalStateException(response.optString("message").ifBlank { "El servidor no confirmó el saldo." })
        }
        clearUsersPayloadMemoryCache()
        return MasterRechargeFundServerReceipt(
            requestedAmount = response.getDouble("requestedAmount"),
            persistedAmount = response.getDouble("persistedAmount"),
            updatedAt = response.optString("updatedAt").ifBlank { null },
        )
    }

    companion object {
        const val USERS_SCOPE = "global"
    }
}

class RenderUsersRemoteClient(
    private val baseUrl: String = "https://didactic-guacamole.onrender.com",
    private val connectTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 12000,
    private val requestSender: ((String, String, String?, Int, Int) -> Pair<Int, String>)? = null,
) {
    fun fetchUsersPayload(): String? {
        val (code, body) = send("GET", "/users-state", null)
        if (code !in 200..299 || body.isBlank()) return null
        val payload = JSONObject(body).opt("payload") ?: return null
        return when (payload) {
            is JSONObject -> payload.toString()
            is String -> payload
            else -> payload.toString()
        }
    }

    fun upsertUsersPayload(payloadJson: String) {
        val body = JSONObject().put("payload", JSONObject(payloadJson)).toString()
        val (code, responseBody) = send("POST", "/users-state", body)
        if (code !in 200..299) {
            throw IllegalStateException(responseBody.ifBlank { "No se pudo guardar usuarios en Render." })
        }
    }

    private fun send(method: String, path: String, body: String?): Pair<Int, String> {
        requestSender?.let { return it(method, path, body, connectTimeoutMs, readTimeoutMs) }
        val connection = (URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = body != null
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            code to responseBody
        } finally {
            connection.disconnect()
        }
    }
}

internal fun resolveUsersPayloadFetch(
    fetchLegacy: () -> String?,
    fetchRender: () -> String?,
): String? {
    readUsersPayloadMemoryCache()?.let { return it }
    fetchLegacy()?.let {
        cacheUsersPayloadMemory(it)
        return it
    }
    fetchRender()?.let {
        cacheUsersPayloadMemory(it)
        return it
    }
    return null
}

private const val USERS_PAYLOAD_CACHE_TTL_MS = 60_000L
private var usersPayloadMemoryCacheEntry: Pair<String, Long>? = null

internal fun clearUsersPayloadMemoryCache() {
    usersPayloadMemoryCacheEntry = null
}

internal fun cacheUsersPayloadMemory(payloadJson: String) {
    usersPayloadMemoryCacheEntry = payloadJson to System.currentTimeMillis()
}

internal fun readUsersPayloadMemoryCache(nowMs: Long = System.currentTimeMillis()): String? {
    val entry = usersPayloadMemoryCacheEntry ?: return null
    return if (nowMs - entry.second <= USERS_PAYLOAD_CACHE_TTL_MS) entry.first else null.also {
        usersPayloadMemoryCacheEntry = null
    }
}

internal fun persistUsersPayload(
    saveLegacy: () -> Unit,
    saveRender: () -> Unit,
) {
    val failures = mutableListOf<Throwable>()
    val saved = listOf(saveLegacy, saveRender)
        .count { attemptUsersPayloadSave(it, failures) }
    if (saved == 0) {
        throw IllegalStateException("No se pudo guardar usuarios.", failures.lastOrNull())
    }
}

private fun attemptUsersPayloadSave(
    action: () -> Unit,
    failures: MutableList<Throwable>,
): Boolean {
    return try {
        action()
        true
    } catch (error: Throwable) {
        failures += error
        false
    }
}

internal fun buildUsersStateFunctionPayload(
    action: String,
    payloadJson: String?,
    commissionOverrideKeys: Set<String> = emptySet(),
): JSONObject {
    return JSONObject().apply {
        put("action", action)
        if (!payloadJson.isNullOrBlank()) {
            put("payload", JSONObject(payloadJson))
        }
        val normalizedCommissionOverrideKeys = commissionOverrideKeys
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalizedCommissionOverrideKeys.isNotEmpty()) {
            put("commissionOverrideKeys", JSONArray().apply {
                normalizedCommissionOverrideKeys.forEach { put(it) }
            })
        }
    }
}

internal fun buildMasterRechargeFundPayload(
    accountId: String,
    enabled: Boolean,
    amount: Double,
): JSONObject {
    return JSONObject()
        .put("action", "update-recharge-fund")
        .put("accountId", accountId.trim())
        .put("enabled", enabled)
        .put("amount", amount)
}

internal fun buildRechargeBalancePayload(
    accountId: String,
    amount: Double,
): JSONObject {
    return JSONObject()
        .put("action", "update-recharge-balance")
        .put("accountId", accountId.trim())
        .put("amount", amount)
}

internal fun shouldFailUsersPayloadSave(edgeSaved: Boolean): Boolean = !edgeSaved

private val usersPayloadInFlightRequests = ConcurrentHashMap<String, FutureTask<String?>>()

private fun coalescedUsersPayloadFetch(
    block: () -> String?,
): String? {
    while (true) {
        val existing = usersPayloadInFlightRequests[USERS_PAYLOAD_FETCH_REQUEST_KEY]
        if (existing != null) return existing.get()

        val task = FutureTask { block() }
        val previous = usersPayloadInFlightRequests.putIfAbsent(USERS_PAYLOAD_FETCH_REQUEST_KEY, task)
        if (previous == null) {
            try {
                task.run()
                return task.get()
            } finally {
                usersPayloadInFlightRequests.remove(USERS_PAYLOAD_FETCH_REQUEST_KEY, task)
            }
        }
    }
}

private const val USERS_PAYLOAD_FETCH_REQUEST_KEY = "lotterynet-users-state|fetch"
