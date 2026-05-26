package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

interface SupabaseAuthRefresher {
    fun refreshSession(refreshToken: String): SupabaseAuthBridgeSession
}

class SupabaseAuthBridgeClient(
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(SupabaseConfig.URL, SupabaseConfig.KEY),
    private val httpClient: OkHttpClient = OkHttpClient(),
) : SupabaseAuthRefresher {
    fun legacyLogin(account: UserAccount, password: String): SupabaseAuthBridgeSession {
        val response = edgeClient.invoke(
            "auth-legacy-login",
            buildLegacyAuthLoginPayload(account, password),
        )
        return SupabaseAuthBridgeSession(
            authUserId = response.optString("authUserId").takeIf { it.isNotBlank() },
            accessToken = response.optString("accessToken").takeIf { it.isNotBlank() },
            refreshToken = response.optString("refreshToken").takeIf { it.isNotBlank() },
            expiresAtEpochSeconds = response.takeIf { it.has("expiresAt") }?.optLong("expiresAt")?.takeIf { it > 0L },
        )
    }

    override fun refreshSession(refreshToken: String): SupabaseAuthBridgeSession {
        val cleanRefreshToken = refreshToken.trim()
        require(cleanRefreshToken.isNotBlank()) { "No hay refresh token para renovar la sesion." }
        val body = JSONObject()
            .put("refresh_token", cleanRefreshToken)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${SupabaseConfig.URL.trimEnd('/')}/auth/v1/token?grant_type=refresh_token")
            .header("apikey", SupabaseConfig.KEY)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(parseSupabaseAuthError(rawBody) ?: "No se pudo renovar la sesion.")
            }
            val json = JSONObject(rawBody)
            val user = json.optJSONObject("user")
            return SupabaseAuthBridgeSession(
                authUserId = user?.optString("id")?.takeIf { it.isNotBlank() },
                accessToken = json.optString("access_token").takeIf { it.isNotBlank() },
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: cleanRefreshToken,
                expiresAtEpochSeconds = json.takeIf { it.has("expires_at") }?.optLong("expires_at")?.takeIf { it > 0L },
            )
        }
    }
}

data class SupabaseAuthBridgeSession(
    val authUserId: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresAtEpochSeconds: Long?,
)

internal fun buildLegacyAuthLoginPayload(account: UserAccount, password: String): JSONObject {
    return JSONObject().apply {
        put("username", account.user)
        put("legacyId", account.id)
        put("roleHint", account.role.name.lowercase())
        put("password", password)
    }
}

private fun parseSupabaseAuthError(rawBody: String): String? {
    return runCatching {
        val json = JSONObject(rawBody)
        json.optString("msg")
            .ifBlank { json.optString("message") }
            .ifBlank { json.optString("error_description") }
            .takeIf { it.isNotBlank() }
    }.getOrNull()
}
