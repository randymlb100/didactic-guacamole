package com.lotterynet.pro.core.finance

import com.lotterynet.pro.core.sync.SyncFreshnessRecord
import com.lotterynet.pro.core.sync.SyncFreshnessState
import com.lotterynet.pro.core.sync.resolveCacheAwareSyncState

data class FinanceRemoteRefreshDecision(
    val shouldRefreshRemote: Boolean,
    val initialMessage: String,
)

fun resolveFinanceRemoteRefreshDecision(
    hasLocalData: Boolean,
    forceRemote: Boolean,
    selectedDayKey: String,
    todayDayKey: String,
    freshnessRecord: SyncFreshnessRecord?,
    nowEpochMs: Long,
    staleTodayMs: Long = FINANCE_TODAY_REFRESH_WINDOW_MS,
): FinanceRemoteRefreshDecision {
    if (forceRemote) {
        return FinanceRemoteRefreshDecision(
            shouldRefreshRemote = true,
            initialMessage = "Cargando desde servidor...",
        )
    }
    val state = resolveCacheAwareSyncState(
        hasLocalData = hasLocalData,
        forceRemote = false,
        localComplete = true,
        freshnessRecord = freshnessRecord,
        nowEpochMs = nowEpochMs,
        staleAfterMs = if (selectedDayKey == todayDayKey) staleTodayMs else Long.MAX_VALUE,
    )
    return if (state == SyncFreshnessState.NEEDS_SERVER) {
        FinanceRemoteRefreshDecision(
            shouldRefreshRemote = true,
            initialMessage = "Actualizando servidor...",
        )
    } else {
        FinanceRemoteRefreshDecision(
            shouldRefreshRemote = false,
            initialMessage = "Datos locales listos",
        )
    }
}

const val FINANCE_TODAY_REFRESH_WINDOW_MS: Long = 60_000L
