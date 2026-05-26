package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SavedLogin
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.NativeAuthRepository
import com.lotterynet.pro.core.repository.SessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.sync.NativeUsersBootstrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class NativeSessionAuthRepository(
    private val authenticator: LocalAuthenticator,
    private val sessionRepository: SessionRepository,
    private val usersRepository: LocalUsersRepository,
    private val refreshRemoteUsers: (() -> Boolean)? = null,
    private val authBridgeClient: SupabaseAuthBridgeClient = SupabaseAuthBridgeClient(),
    private val authBridgeTimeoutMs: Long = 12_000L,
) : NativeAuthRepository {

    override fun authenticate(username: String, password: String, remember: Boolean): ActiveSession? {
        val account = authenticator.authenticate(username, password)
            ?: authenticateAfterRemoteRefresh(username, password)
            ?: return null
        val authBridgeSession = createSupabaseAuthSession(account, password)
        if (requiresSupabaseServerSession(account) && !hasUsableSupabaseAuthSession(authBridgeSession)) {
            return null
        }
        sessionRepository.saveSavedLogin(buildSavedLoginForSuccessfulAuth(username, password, remember))
        val session = ActiveSession(
            role = account.role,
            userId = account.id,
            username = account.user,
            adminId = account.adminId,
            adminUser = account.adminUser,
            banca = account.banca,
            territory = account.territory,
            authUserId = authBridgeSession?.authUserId,
            authAccessToken = authBridgeSession?.accessToken,
            authRefreshToken = authBridgeSession?.refreshToken,
            authExpiresAtEpochSeconds = authBridgeSession?.expiresAtEpochSeconds,
        )
        sessionRepository.saveActiveSession(session)
        usersRepository.touchLastSeen(account.id)
        return session
    }

    private fun authenticateAfterRemoteRefresh(username: String, password: String): UserAccount? {
        if (!shouldRetryLoginWithRemoteUsers(username)) return null
        val refreshed = refreshRemoteUsers?.invoke()
            ?: runBlocking {
                withContext(Dispatchers.IO) {
                    NativeUsersBootstrapper(usersRepository).bootstrap(forceRemoteRefresh = true).ok
                }
            }
        if (!refreshed) return null
        return authenticator.authenticate(username, password)
    }

    private fun createSupabaseAuthSession(account: UserAccount, password: String): SupabaseAuthBridgeSession? {
        return attemptSupabaseAuthSession(
            timeoutMs = authBridgeTimeoutMs,
            request = {
                runBlocking {
                    withTimeoutOrNull(authBridgeTimeoutMs) {
                        withContext(Dispatchers.IO) {
                            authBridgeClient.legacyLogin(account, password)
                        }
                    }
                }
            },
        )
    }
}

internal fun shouldRetryLoginWithRemoteUsers(username: String): Boolean {
    val normalized = username.trim()
    return normalized.isNotBlank() && !normalized.equals("master", ignoreCase = true)
}

internal fun attemptSupabaseAuthSession(
    timeoutMs: Long,
    request: () -> SupabaseAuthBridgeSession?,
): SupabaseAuthBridgeSession? {
    if (timeoutMs <= 0L) return null
    return runCatching { request() }.getOrNull()
}

internal fun requiresSupabaseServerSession(account: UserAccount): Boolean {
    return account.role != UserRole.MASTER && !account.user.equals("master", ignoreCase = true)
}

internal fun hasUsableSupabaseAuthSession(session: SupabaseAuthBridgeSession?): Boolean {
    return !session?.accessToken.isNullOrBlank()
}

internal fun buildSavedLoginForSuccessfulAuth(
    username: String,
    password: String,
    remember: Boolean,
): SavedLogin? {
    if (!remember) return null
    return SavedLogin(
        username = username.trim(),
        password = password,
        remember = true,
    )
}
