package com.lotterynet.pro.core.remote

import com.lotterynet.pro.core.config.SupabaseConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SupabaseEdgeException(
    val userMessage: String,
    technicalMessage: String,
    val reason: SupabaseEdgeFailureReason = SupabaseEdgeFailureReason.REMOTE_ERROR,
) : IllegalStateException(technicalMessage)

enum class SupabaseEdgeFailureReason {
    AUTH_REQUIRED,
    REMOTE_ERROR,
}

class SupabaseEdgeClient(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 35_000,
    private val callTimeoutMs: Int = connectTimeoutMs + readTimeoutMs,
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(callTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .connectTimeout(connectTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
        .build()

    fun invoke(functionSlug: String, payload: JSONObject, bearerToken: String? = null): JSONObject {
        return postJson(functionPath(functionSlug), payload, bearerToken)
    }

    fun invokeAuthenticated(functionSlug: String, payload: JSONObject, bearerToken: String?): JSONObject {
        val token = requireSupabaseUserJwt(bearerToken)
        return postJson(functionPath(functionSlug), payload, token)
    }

    internal fun functionPath(functionSlug: String): String {
        val cleanSlug = functionSlug.trim().trim('/')
        require(cleanSlug.isNotBlank()) { "Edge Function requerida." }
        return "functions/v1/$cleanSlug"
    }

    private fun postJson(path: String, payload: JSONObject, bearerToken: String?): JSONObject {
        val requestUrl = "${baseUrl.trimEnd('/')}/$path"
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(requestUrl)
            .post(body)
            .header("Accept", "application/json")
            .header("apikey", apiKey)
            .header("Authorization", authorizationHeader(apiKey, bearerToken))
            .build()
        val response = client.newCall(request).execute()
        val bodyString = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val message = extractEdgeErrorMessage(bodyString).ifBlank { "Servidor no disponible." }
            throw SupabaseEdgeException(
                userMessage = presentSupabaseEdgeMessage(message),
                technicalMessage = message,
            )
        }
        return JSONObject(bodyString.ifBlank { "{}" })
    }
}

internal fun authorizationHeader(apiKey: String, bearerToken: String?): String {
    return "Bearer ${bearerToken?.takeIf { it.isNotBlank() } ?: apiKey}"
}

internal fun requireSupabaseUserJwt(bearerToken: String?): String {
    val token = bearerToken.orEmpty().trim()
    if (!isLikelySupabaseJwt(token)) {
        throw SupabaseEdgeException(
            userMessage = "Sesion del servidor requerida. Inicia sesion con internet para continuar.",
            technicalMessage = "Missing Supabase Auth JWT for server-first operation.",
            reason = SupabaseEdgeFailureReason.AUTH_REQUIRED,
        )
    }
    return token
}

internal fun isSupabaseAuthRequired(error: Throwable?): Boolean {
    return (error as? SupabaseEdgeException)?.reason == SupabaseEdgeFailureReason.AUTH_REQUIRED
}

internal fun isLikelySupabaseJwt(token: String): Boolean {
    val parts = token.split('.')
    return parts.size == 3 && parts.all { it.isNotBlank() } && token.startsWith("eyJ")
}

internal fun isSupabaseEdgeTimeout(message: String?): Boolean {
    val normalized = message.orEmpty().lowercase()
    return normalized.contains("statement timeout") ||
        normalized.contains("canceling statement due to") ||
        normalized.contains("read timed out") ||
        normalized.contains("timeout")
}

internal fun presentSupabaseEdgeMessage(message: String?): String {
    val clean = message.orEmpty().trim()
    if (isSupabaseEdgeTimeout(clean)) {
        return "El servidor tardo demasiado validando la operacion. Intenta de nuevo."
    }
    return clean.takeIf { it.isNotBlank() }
        ?: "Sin conexion al servidor, no se puede ejecutar esta opcion."
}

internal fun extractEdgeErrorMessage(body: String): String {
    return runCatching {
        JSONObject(body).let { json ->
            json.optString("message")
                .ifBlank { json.optString("details") }
                .ifBlank { json.optString("hint") }
                .ifBlank { json.optString("error") }
        }
    }.getOrNull().orEmpty()
}
