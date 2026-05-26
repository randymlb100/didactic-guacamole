package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.repository.SessionRepository

class SupabaseSessionTokenProvider(
    private val sessionRepository: SessionRepository,
    private val authRefresher: SupabaseAuthRefresher = SupabaseAuthBridgeClient(),
    private val legacyAuthLogin: ((UserAccount, String) -> SupabaseAuthBridgeSession)? = defaultLegacyAuthLogin(authRefresher),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    fun freshSessionOrCurrent(refreshBeforeSeconds: Long = DEFAULT_REFRESH_BEFORE_SECONDS): ActiveSession? {
        val current = sessionRepository.getActiveSession() ?: return null
        if (hasUsableCurrentJwt(current, nowEpochSeconds(), refreshBeforeSeconds)) {
            return current
        }
        return synchronized(refreshLock) {
            val latest = sessionRepository.getActiveSession() ?: return@synchronized current
            if (hasUsableCurrentJwt(latest, nowEpochSeconds(), refreshBeforeSeconds)) {
                return@synchronized latest
            }
            refreshWithToken(latest)
                ?: refreshWithSavedLogin(latest)
        }
    }

    fun freshAccessToken(refreshBeforeSeconds: Long = DEFAULT_REFRESH_BEFORE_SECONDS): String? {
        return freshSessionOrCurrent(refreshBeforeSeconds)?.authAccessToken
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
        }
    }

    companion object {
        private const val DEFAULT_REFRESH_BEFORE_SECONDS = 10L * 60L
        private val refreshLock = Any()
    }
}

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
    val expiresAt = session.authExpiresAtEpochSeconds ?: return true
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
