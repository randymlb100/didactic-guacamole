package com.lotterynet.pro.core.finance

data class OperationalReportRequestSnapshot(
    val requestId: Long,
    val preset: FinancePeriodPreset,
    val fromDayKey: String?,
    val toDayKey: String?,
    val selectedFilter: OperationalReportActorFilter,
    val forceRemote: Boolean,
)

fun shouldFetchOperationalReportEndpoint(
    refreshDecision: FinanceRemoteRefreshDecision,
    hasLocalReport: Boolean,
): Boolean {
    // A local projection or an old decision can never replace the authoritative report.
    return true
}

fun isOperationalReportRequestCurrent(
    activeRequestId: Long,
    completedRequestId: Long,
): Boolean = activeRequestId == completedRequestId

/**
 * Historical manual ranges are already authoritative in the report endpoint.
 * Synchronizing the complete current session first only delays that read.
 */
fun shouldSynchronizeOperationalReportDependencies(
    request: OperationalReportRequestSnapshot,
    todayDayKey: String,
): Boolean {
    if (request.forceRemote) return true
    val selectedToDayKey = request.toDayKey ?: return true
    return selectedToDayKey >= todayDayKey
}
