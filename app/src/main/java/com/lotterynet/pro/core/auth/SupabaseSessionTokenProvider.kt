package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.repository.SessionRepository
import java.util.Base64
import org.json.JSONObject

class SupabaseSessionTokenProvider(
    private val sessionRepository: SessionRepository,
    private val authRefresher: SupabaseAuthRefresher = SupabaseAuthBridgeClient(),
    private val legacyAuthLogin: ((UserAccount, String) -> SupabaseAuthBridgeSession)? = defaultLegacyAuthLogin(authRefresher),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    @Volatile
    private var cachedFreshSession: CachedFreshSession? = null

    fun freshSessionOrCurrent(refreshBeforeSeconds: Long = DEFAULT_REFRESH_BEFORE_SECONDS): ActiveSession? {
        cachedFreshSessionOrNull(refreshBeforeSeconds)?.let { return it }
        val current = sessionRepository.getActiveSession() ?: return null
        if (hasUsableCurrentJwt(current, nowEpochSeconds(), refreshBeforeSeconds)) {
            cacheFreshSession(current)
            return current
        }
        return synchronized(refreshLock) {
            val latest = sessionRepository.getActiveSession() ?: return@synchronized current
            cachedFreshSessionOrNull(refreshBeforeSeconds)?.let { return@synchronized it }
            if (hasUsableCurrentJwt(latest, nowEpochSeconds(), refreshBeforeSeconds)) {
                cacheFreshSession(latest)
                return@synchronized latest
            }
            refreshWithToken(latest)
                ?: refreshWithSavedLogin(latest)
                ?: latest.takeIf { it.authAccessToken?.isNotBlank() == true }
        }
    }

    fun freshAccessToken(refreshBeforeSeconds: Long = DEFAULT_REFRESH_BEFORE_SECONDS): String? {
        return freshSessionOrCurrent(refreshBeforeSeconds)?.authAccessToken
    }

    fun forceFreshSession(): ActiveSession? {
        val current = sessionRepository.getActiveSession() ?: return null
        return synchronized(refreshLock) {
            val latest = sessionRepository.getActiveSession() ?: return@synchronized current
            refreshWithToken(latest)
                ?: refreshWithSavedLogin(latest)
                ?: latest.takeIf { it.authAccessToken?.isNotBlank() == true }?.also { cached ->
                    if (hasUsableCurrentJwt(cached, nowEpochSeconds(), DEFAULT_REFRESH_BEFORE_SECONDS)) {
                        cacheFreshSession(cached)
                    }
                }
        }
    }

    fun forceFreshAccessToken(): String? {
        return forceFreshSession()?.authAccessToken
    }

    private fun ActiveSession.withSupabaseAuth(auth: SupabaseAuthBridgeSession): ActiveSession {
        return copy(
            authUserId = auth.authUserId ?: authUserId,
            authAccessToken = auth.accessToken ?: authAccessToken,
            authRefreshToken = auth.refreshToken ?: authRefreshToken,
            authExpiresAtEpochSeconds = auth.expiresAtEpochSeconds ?: authExpiresAtEpochSeconds,
        )
    }

    private fun refreshWithToken(session: ActiveSession): ActiveSession? {
        val refreshToken = session.authRefreshToken?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            session.withSupabaseAuth(authRefresher.refreshSession(refreshToken))
        }.getOrNull()?.also { refreshed ->
            sessionRepository.saveActiveSession(refreshed)
            if (hasUsableCurrentJwt(refreshed, nowEpochSeconds(), DEFAULT_REFRESH_BEFORE_SECONDS)) {
                cacheFreshSession(refreshed)
            }
        }
    }

    private fun refreshWithSavedLogin(session: ActiveSession): ActiveSession? {
        val saved = sessionRepository.getSavedLogin() ?: return null
        if (!saved.username.equals(session.username, ignoreCase = true)) return null
        val password = saved.password.takeIf { it.isNotBlank() } ?: return null
        val login = legacyAuthLogin ?: return null
        return runCatching {
            session.withSupabaseAuth(
                login(
                    UserAccount(
                        id = session.userId,
                        user = session.username,
                        role = session.role,
                        adminId = session.adminId,
                        adminUser = session.adminUser,
                        banca = session.banca,
                        territory = session.territory,
                    ),
                    password,
                ),
            )
        }.getOrNull()?.also { refreshed ->
            sessionRepository.saveActiveSession(refreshed)
            if (hasUsableCurrentJwt(refreshed, nowEpochSeconds(), DEFAULT_REFRESH_BEFORE_SECONDS)) {
                cacheFreshSession(refreshed)
            }
        }
    }

    private fun cachedFreshSessionOrNull(refreshBeforeSeconds: Long): ActiveSession? {
        val cached = cachedFreshSession ?: return null
        val nowMs = nowMs()
        if (nowMs - cached.cachedAtMs > FRESH_SESSION_CACHE_TTL_MS) return null
        return if (hasUsableCurrentJwt(cached.session, nowEpochSeconds(), refreshBeforeSeconds)) {
            cached.session
        } else {
            null
        }
    }

    private fun cacheFreshSession(session: ActiveSession) {
        cachedFreshSession = CachedFreshSession(session = session, cachedAtMs = nowMs())
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    companion object {
        private const val DEFAULT_REFRESH_BEFORE_SECONDS = 10L * 60L
        private const val FRESH_SESSION_CACHE_TTL_MS = 8_000L
        private val refreshLock = Any()
    }
}

private data class CachedFreshSession(
    val session: ActiveSession,
    val cachedAtMs: Long,
)

private fun defaultLegacyAuthLogin(
    authRefresher: SupabaseAuthRefresher,
): ((UserAccount, String) -> SupabaseAuthBridgeSession)? {
    val client = authRefresher as? SupabaseAuthBridgeClient ?: return null
    return { account, password -> client.legacyLogin(account, password) }
}

internal fun hasUsableCurrentJwt(
    session: ActiveSession,
    nowEpochSeconds: Long,
    refreshBeforeSeconds: Long,
): Boolean {
    val accessToken = session.authAccessToken?.takeIf { it.isNotBlank() } ?: return false
    val expiresAt = session.authExpiresAtEpochSeconds ?: jwtExpiresAtEpochSeconds(accessToken) ?: return false
    return accessToken.isNotBlank() && expiresAt - nowEpochSeconds > refreshBeforeSeconds
}

internal fun shouldRefreshSupabaseSession(
    session: ActiveSession,
    nowEpochSeconds: Long,
    refreshBeforeSeconds: Long,
): Boolean {
    val accessToken = session.authAccessToken?.takeIf { it.isNotBlank() } ?: return false
    val expiresAt = session.authExpiresAtEpochSeconds ?: return false
    return accessToken.isNotBlank() && expiresAt - nowEpochSeconds <= refreshBeforeSeconds
}

internal fun jwtExpiresAtEpochSeconds(accessToken: String): Long? {
    val payload = accessToken.split('.').getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
    return runCatching {
        val padded = payload.padEnd(payload.length + ((4 - payload.length % 4) % 4), '=')
        val decoded = String(Base64.getUrlDecoder().decode(padded), Charsets.UTF_8)
        JSONObject(decoded).optLong("exp").takeIf { it > 0L }
    }.getOrNull()
}
