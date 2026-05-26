package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SavedLogin
import com.lotterynet.pro.core.model.SessionSnapshot
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.SessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupabaseSessionTokenProviderTest {
    @Test
    fun `fresh access token returns current token when it is not near expiry`() {
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = "current-token",
                refreshToken = "refresh-token",
                expiresAt = 2_000L,
            ),
        )

        val token = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = FakeAuthRefresher(),
            nowEpochSeconds = { 1_000L },
        ).freshAccessToken()

        assertEquals("current-token", token)
    }

    @Test
    fun `fresh access token does not reuse stale jwt when refresh fails`() {
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = "expired-token",
                refreshToken = "refresh-token",
                expiresAt = 1_100L,
            ),
        )

        val token = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = FakeAuthRefresher(error = IllegalStateException("refresh rejected")),
            nowEpochSeconds = { 1_095L },
        ).freshAccessToken()

        assertNull(token)
        assertEquals("expired-token", repository.getActiveSession()?.authAccessToken)
    }

    @Test
    fun `fresh access token does not reuse expiring jwt when refresh token is missing`() {
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = "expiring-token",
                refreshToken = "",
                expiresAt = 1_100L,
            ),
        )

        val token = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = FakeAuthRefresher(),
            nowEpochSeconds = { 1_095L },
        ).freshAccessToken()

        assertNull(token)
    }

    @Test
    fun `fresh access token creates jwt silently from saved login when current session has no server token`() {
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = "",
                refreshToken = "",
                expiresAt = 0L,
            ),
            saved = SavedLogin(username = "admin", password = "clave123", remember = true),
        )

        val token = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = FakeAuthRefresher(),
            legacyAuthLogin = { account, password ->
                assertEquals("admin", account.user)
                assertEquals("clave123", password)
                SupabaseAuthBridgeSession(
                    authUserId = "auth-legacy",
                    accessToken = "legacy-token",
                    refreshToken = "legacy-refresh",
                    expiresAtEpochSeconds = 4_000L,
                )
            },
            nowEpochSeconds = { 1_095L },
        ).freshAccessToken()

        assertEquals("legacy-token", token)
        assertEquals("legacy-token", repository.getActiveSession()?.authAccessToken)
        assertEquals("legacy-refresh", repository.getActiveSession()?.authRefreshToken)
    }

    @Test
    fun `fresh access token saves refreshed jwt when refresh succeeds`() {
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = "old-token",
                refreshToken = "refresh-token",
                expiresAt = 1_100L,
            ),
        )

        val token = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = FakeAuthRefresher(
                session = SupabaseAuthBridgeSession(
                    authUserId = "auth-1",
                    accessToken = "new-token",
                    refreshToken = "new-refresh",
                    expiresAtEpochSeconds = 3_000L,
                ),
            ),
            nowEpochSeconds = { 1_095L },
        ).freshAccessToken()

        assertEquals("new-token", token)
        assertEquals("new-token", repository.getActiveSession()?.authAccessToken)
        assertEquals("new-refresh", repository.getActiveSession()?.authRefreshToken)
    }

    private class FakeAuthRefresher(
        private val session: SupabaseAuthBridgeSession? = null,
        private val error: Throwable? = null,
    ) : SupabaseAuthRefresher {
        override fun refreshSession(refreshToken: String): SupabaseAuthBridgeSession {
            error?.let { throw it }
            return checkNotNull(session) { "No refresh expected." }
        }
    }

    private class FakeSessionRepository(
        private var active: ActiveSession?,
        private val saved: SavedLogin? = null,
    ) : SessionRepository {
        override fun getSavedLogin(): SavedLogin? = saved
        override fun saveSavedLogin(savedLogin: SavedLogin?) = Unit
        override fun getActiveSession(): ActiveSession? = active
        override fun saveActiveSession(activeSession: ActiveSession?) {
            active = activeSession
        }
        override fun getSessionSnapshot(): SessionSnapshot? = null
        override fun saveSessionSnapshot(snapshot: SessionSnapshot?) = Unit
        override fun clearSession() {
            active = null
        }
    }

    private fun activeSession(
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
    ): ActiveSession {
        return ActiveSession(
            role = UserRole.ADMIN,
            userId = "admin-1",
            username = "admin",
            authAccessToken = accessToken,
            authRefreshToken = refreshToken,
            authExpiresAtEpochSeconds = expiresAt,
        )
    }
}
