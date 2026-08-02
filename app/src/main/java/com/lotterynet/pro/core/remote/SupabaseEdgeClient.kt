package com.lotterynet.pro.core.remote

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.BuildConfig
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
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
    private val client = SupabaseDns.okHttpBuilder()
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
            .header("User-Agent", "LotteryNetAndroid/${BuildConfig.VERSION_NAME}")
            .header("X-Lotterynet-Client", "android")
            .header("X-Lotterynet-Client-Version", BuildConfig.VERSION_NAME)
            .header("X-Lotterynet-Build-Variant", if (BuildConfig.DEBUG) "debug" else "release")
            .header("apikey", apiKey)
            .header("Authorization", authorizationHeader(apiKey, bearerToken))
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (error: IOException) {
            SupabaseConfig.logRuntimeFailure("SupabaseEdgeClient", requestUrl, error)
            throw SupabaseEdgeException(
                userMessage = presentSupabaseTransportMessage(error),
                technicalMessage = error.message ?: error::class.java.simpleName,
            )
        }
        val contentType = response.header("Content-Type").orEmpty()
        val bodyString = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val message = presentSupabaseHttpFailureMessage(bodyString, contentType)
            throw SupabaseEdgeException(
                userMessage = presentSupabaseEdgeMessage(message),
                technicalMessage = message,
                reason = resolveSupabaseEdgeFailureReason(response.code, message),
            )
        }
        return JSONObject(bodyString.ifBlank { "{}" })
    }
}

internal object SupabaseDns {
    private val fallbackDns: Dns by lazy {
        DnsOverHttps.Builder()
            .client(OkHttpClient.Builder().build())
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1"),
            )
            .includeIPv6(false)
            .build()
    }

    val systemThenHttps: Dns = Dns { hostname ->
        try {
            Dns.SYSTEM.lookup(hostname)
        } catch (systemError: UnknownHostException) {
            try {
                fallbackDns.lookup(hostname)
            } catch (fallbackError: UnknownHostException) {
                systemError.addSuppressed(fallbackError)
                throw systemError
            }
        }
    }

    fun okHttpBuilder(): OkHttpClient.Builder = OkHttpClient.Builder().dns(systemThenHttps)
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

internal fun resolveSupabaseEdgeFailureReason(statusCode: Int, message: String?): SupabaseEdgeFailureReason {
    val normalized = message.orEmpty().lowercase()
    val authMessage = normalized.contains("sesion invalida") ||
        normalized.contains("session invalid") ||
        normalized.contains("invalid jwt") ||
        normalized.contains("jwt expired") ||
        normalized.contains("missing authorization") ||
        normalized.contains("auth session missing") ||
        normalized.contains("user not found")
    return if ((statusCode == 401 || statusCode == 403) && authMessage) {
        SupabaseEdgeFailureReason.AUTH_REQUIRED
    } else {
        SupabaseEdgeFailureReason.REMOTE_ERROR
    }
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

internal fun presentSupabaseHttpFailureMessage(body: String, contentType: String?): String {
    val normalizedBody = body.lowercase()
    val normalizedType = contentType.orEmpty().lowercase()
    if (normalizedType.contains("text/html") ||
        normalizedBody.contains("<!doctype html") ||
        normalizedBody.contains("cloudflare") ||
        normalizedBody.contains("error 1001") ||
        normalizedBody.contains("dns resolution error")
    ) {
        return "Supabase no respondio correctamente. Revisa la red e intenta de nuevo."
    }
    return extractEdgeErrorMessage(body).ifBlank { "Servidor no disponible." }
}

internal fun presentSupabaseTransportMessage(error: Throwable): String {
    return when (error) {
        is UnknownHostException -> "No se pudo encontrar el servidor. Revisa internet o cambia el DNS/red del equipo."
        else -> presentSupabaseEdgeMessage(error.message)
    }
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
