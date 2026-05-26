package com.lotterynet.pro.core.model

data class SavedLogin(
    val username: String,
    val password: String = "",
    val remember: Boolean = true,
)

data class ActiveSession(
    val role: UserRole,
    val userId: String,
    val username: String,
    val adminId: String? = null,
    val adminUser: String? = null,
    val banca: String? = null,
    val territory: String? = null,
    val authUserId: String? = null,
    val authAccessToken: String? = null,
    val authRefreshToken: String? = null,
    val authExpiresAtEpochSeconds: Long? = null,
    val startedAtEpochMs: Long = System.currentTimeMillis(),
)

data class SessionSnapshot(
    val activeSession: ActiveSession? = null,
    val currentScreen: String? = null,
    val turnoStartEpochMs: Long? = null,
    val lastSyncEpochMs: Long? = null,
    val isOnline: Boolean = true,
)
