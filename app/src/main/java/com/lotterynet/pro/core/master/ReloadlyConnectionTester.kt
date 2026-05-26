package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.storage.ReloadlySettings
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ReloadlyProbeResult(
    val ok: Boolean,
    val message: String,
)

class ReloadlyConnectionTester {
    fun probe(settings: ReloadlySettings): ReloadlyProbeResult {
        val clientId = settings.clientId.trim()
        val clientSecret = settings.clientSecret.trim()
        if (clientId.isBlank() || clientSecret.isBlank()) {
            return ReloadlyProbeResult(false, "Faltan credenciales de Reloadly.")
        }

        return runCatching {
            val token = requestToken(clientId, clientSecret)
            val payload = authorizedGet(token, "$RLDLY_BASE/accounts/balance")
            val balance = payload.optDouble("balance", 0.0)
            val currency = payload.optString("currencyCode").ifBlank { "USD" }
            ReloadlyProbeResult(true, "Conectado · Saldo ${com.lotterynet.pro.core.format.formatWholeAmount(balance)} $currency")
        }.getOrElse { error ->
            ReloadlyProbeResult(false, error.message ?: "No se pudo verificar Reloadly.")
        }
    }

    private fun requestToken(clientId: String, clientSecret: String): String {
        val payload = JSONObject().apply {
            put("client_id", clientId)
            put("client_secret", clientSecret)
            put("grant_type", "client_credentials")
            put("audience", RLDLY_BASE)
        }
        val response = postJson(RLDLY_AUTH_URL, payload.toString(), "application/json")
        return response.optString("access_token").takeIf { it.isNotBlank() }
            ?: error("Reloadly no devolvió access_token.")
    }

    private fun authorizedGet(token: String, url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/com.reloadly.topups-v1+json")
        }
        return connection.readJson()
    }

    private fun postJson(url: String, body: String, accept: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", accept)
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body)
        }
        return connection.readJson()
    }

    private fun HttpURLConnection.readJson(): JSONObject {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("HTTP $code ${body.take(120)}".trim())
        return if (body.isBlank()) JSONObject() else JSONObject(body)
    }

    companion object {
        private const val RLDLY_AUTH_URL = "https://auth.reloadly.com/oauth/token"
        private const val RLDLY_BASE = "https://topups.reloadly.com"
    }
}
