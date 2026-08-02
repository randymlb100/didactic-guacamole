package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SavedLogin
import com.lotterynet.pro.core.model.SessionSnapshot
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.SessionRepository
import java.util.Base64
import org.junit.Assert.assertEquals
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
    fun `fresh access token reuses cached healthy session`() {
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = "cached-token",
                refreshToken = "refresh-token",
                expiresAt = 2_000L,
            ),
        )
        val provider = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = FakeAuthRefresher(),
            nowEpochSeconds = { 1_000L },
        )

        val first = provider.freshAccessToken()
        val second = provider.freshAccessToken()

        assertEquals("cached-token", first)
        assertEquals("cached-token", second)
        assertEquals(1, repository.getActiveSessionCalls)
    }

    @Test
    fun `fresh access token preserves current token when refresh fails`() {
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

        assertEquals("expired-token", token)
        assertEquals("expired-token", repository.getActiveSession()?.authAccessToken)
    }

    @Test
    fun `force fresh access token preserves current token when refresh fails`() {
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = "forced-current-token",
                refreshToken = "refresh-token",
                expiresAt = 1_100L,
            ),
        )

        val token = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = FakeAuthRefresher(error = IllegalStateException("refresh rejected")),
            nowEpochSeconds = { 1_095L },
        ).forceFreshAccessToken()

        assertEquals("forced-current-token", token)
        assertEquals("forced-current-token", repository.getActiveSession()?.authAccessToken)
    }

    @Test
    fun `fresh access token preserves current token when refresh token is missing`() {
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

        assertEquals("expiring-token", token)
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

    @Test
    fun `fresh access token reads jwt exp when saved expiry is missing`() {
        val jwt = jwtWithExp(2_000L)
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = jwt,
                refreshToken = "refresh-token",
                expiresAt = 0L,
            ).copy(authExpiresAtEpochSeconds = null),
        )

        val token = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = FakeAuthRefresher(),
            nowEpochSeconds = { 1_000L },
        ).freshAccessToken()

        assertEquals(jwt, token)
    }

    @Test
    fun `fresh access token refreshes expired jwt when saved expiry is missing`() {
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = jwtWithExp(1_000L),
                refreshToken = "refresh-token",
                expiresAt = 0L,
            ).copy(authExpiresAtEpochSeconds = null),
        )
        val refresher = FakeAuthRefresher(
            session = SupabaseAuthBridgeSession(
                authUserId = "auth-1",
                accessToken = "renewed-token",
                refreshToken = "renewed-refresh",
                expiresAtEpochSeconds = 3_000L,
            ),
        )

        val token = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = refresher,
            nowEpochSeconds = { 1_100L },
        ).freshAccessToken()

        assertEquals("renewed-token", token)
        assertEquals(1, refresher.refreshCalls)
    }

    @Test
    fun `force fresh access token refreshes even when current jwt looks usable`() {
        val repository = FakeSessionRepository(
            active = activeSession(
                accessToken = "old-token",
                refreshToken = "refresh-token",
                expiresAt = 9_999L,
            ),
        )
        val refresher = FakeAuthRefresher(
            session = SupabaseAuthBridgeSession(
                authUserId = "auth-1",
                accessToken = "forced-token",
                refreshToken = "forced-refresh",
                expiresAtEpochSeconds = 12_000L,
            ),
        )

        val token = SupabaseSessionTokenProvider(
            sessionRepository = repository,
            authRefresher = refresher,
            nowEpochSeconds = { 1_000L },
        ).forceFreshAccessToken()

        assertEquals("forced-token", token)
        assertEquals("forced-token", repository.getActiveSession()?.authAccessToken)
        assertEquals(1, refresher.refreshCalls)
    }

    private class FakeAuthRefresher(
        private val session: SupabaseAuthBridgeSession? = null,
        private val error: Throwable? = null,
    ) : SupabaseAuthRefresher {
        var refreshCalls: Int = 0
            private set

        override fun refreshSession(refreshToken: String): SupabaseAuthBridgeSession {
            refreshCalls += 1
            error?.let { throw it }
            return checkNotNull(session) { "No refresh expected." }
        }
    }

    private class FakeSessionRepository(
        private var active: ActiveSession?,
        private val saved: SavedLogin? = null,
    ) : SessionRepository {
        var getActiveSessionCalls: Int = 0
            private set

        override fun getSavedLogin(): SavedLogin? = saved
        override fun saveSavedLogin(savedLogin: SavedLogin?) = Unit
        override fun getActiveSession(): ActiveSession? {
            getActiveSessionCalls += 1
            return active
        }
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

    private fun jwtWithExp(exp: Long): String {
        val header = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("""{"alg":"HS256"}""".toByteArray(Charsets.UTF_8))
        val payload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("""{"exp":$exp}""".toByteArray(Charsets.UTF_8))
        return "$header.$payload.signature"
    }
}
