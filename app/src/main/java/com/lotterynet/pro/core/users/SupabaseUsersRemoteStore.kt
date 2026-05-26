package com.lotterynet.pro.core.users

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

class SupabaseUsersRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
    private val renderClient: RenderUsersRemoteClient = RenderUsersRemoteClient(),
    private val stateClient: SupabaseUsersStateClient = SupabaseUsersStateClient(baseUrl, apiKey),
    private val bearerTokenProvider: () -> String? = { null },
) : UsersRemoteStore {
    fun refreshUsersPayload(): String? = fetchUsersPayload()

    override fun fetchUsersPayload(): String? {
        return resolveUsersPayloadFetch(
            fetchDirect = { stateClient.fetchUsersPayload() },
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

    override fun upsertUsersPayload(payloadJson: String) {
        persistUsersPayload(
            saveDirect = { stateClient.upsertUsersPayload(payloadJson) },
            saveLegacy = {
                edgeClient.invokeAuthenticated(
                    "lotterynet-users-state",
                    buildUsersStateFunctionPayload("upsert", payloadJson),
                    bearerTokenProvider(),
                )
            },
            saveRender = { renderClient.upsertUsersPayload(payloadJson) },
        )
        cacheUsersPayloadMemory(payloadJson)
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

class SupabaseUsersStateClient(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val connectTimeoutMs: Int = 5000,
    private val readTimeoutMs: Int = 5000,
    private val requestFetcher: ((String, String, String?, Int, Int) -> Pair<Int, String>)? = null,
) {
    fun fetchUsersPayload(): String? {
        val encodedScope = URLEncoder.encode(SupabaseUsersRemoteStore.USERS_SCOPE, "UTF-8")
        val path = "/rest/v1/lotterynet_users_state?scope=eq.$encodedScope&select=payload"
        val (code, body) = send("GET", path, null)
        if (code !in 200..299 || body.isBlank()) return null
        val rows = JSONArray(body)
        val value = rows.optJSONObject(0)?.opt("payload") ?: return null
        return when (value) {
            is JSONObject -> value.toString()
            is String -> value
            else -> value.toString()
        }
    }

    fun upsertUsersPayload(payloadJson: String) {
        val payload = JSONObject(payloadJson)
        val row = JSONObject()
            .put("scope", SupabaseUsersRemoteStore.USERS_SCOPE)
            .put("payload", payload)
            .put("updated_at", Instant.now().toString())
        val (code, body) = send(
            method = "POST",
            path = "/rest/v1/lotterynet_users_state?on_conflict=scope",
            body = row.toString(),
        )
        if (code !in 200..299) {
            throw IllegalStateException(
                body.ifBlank { "No se pudo guardar usuarios en Supabase." }
            )
        }
    }

    private fun send(method: String, path: String, body: String?): Pair<Int, String> {
        requestFetcher?.let { return it(method, path, body, connectTimeoutMs, readTimeoutMs) }
        val requestUrl = "${baseUrl.trimEnd('/')}$path"
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            doOutput = body != null
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal")
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
    fetchDirect: () -> String?,
    fetchRender: () -> String?,
): String? {
    readUsersPayloadMemoryCache()?.let { return it }
    fetchDirect()?.let {
        cacheUsersPayloadMemory(it)
        return it
    }
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
    saveDirect: () -> Unit,
    saveRender: () -> Unit,
) {
    val failures = mutableListOf<Throwable>()
    val saved = listOf(saveDirect, saveLegacy, saveRender)
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

internal fun buildUsersStateFunctionPayload(action: String, payloadJson: String?): JSONObject {
    return JSONObject().apply {
        put("action", action)
        if (!payloadJson.isNullOrBlank()) {
            put("payload", JSONObject(payloadJson))
        }
    }
}

internal fun shouldFailUsersPayloadSave(edgeSaved: Boolean): Boolean = !edgeSaved
