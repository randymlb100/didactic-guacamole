package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.SyncStatus

interface SyncRepository {
    suspend fun preloadAdminState(adminId: String): SyncStatus
    suspend fun refreshLive(ownerId: String): SyncStatus
    suspend fun pushCritical(ownerId: String): SyncStatus
}
