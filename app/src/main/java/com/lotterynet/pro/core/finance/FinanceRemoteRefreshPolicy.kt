package com.lotterynet.pro.core.finance

import com.lotterynet.pro.core.sync.SyncFreshnessRecord

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
    // Financial reports are authoritative on the server for every selected range.
    // The local copy is used only while the request is in flight or if it fails.
    return FinanceRemoteRefreshDecision(
        shouldRefreshRemote = true,
        initialMessage = if (forceRemote) "Cargando desde servidor..." else "Consultando servidor...",
    )
}

const val FINANCE_TODAY_REFRESH_WINDOW_MS: Long = 60_000L
