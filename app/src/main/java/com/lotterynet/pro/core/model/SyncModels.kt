package com.lotterynet.pro.core.model

data class PresenceState(
    val role: UserRole,
    val user: String,
    val adminId: String? = null,
    val online: Boolean,
    val lastSeenAtEpochMs: Long = System.currentTimeMillis(),
)

data class SyncStatus(
    val ok: Boolean,
    val reason: String? = null,
    val updated: Int = 0,
    val lastSyncEpochMs: Long = System.currentTimeMillis(),
)
