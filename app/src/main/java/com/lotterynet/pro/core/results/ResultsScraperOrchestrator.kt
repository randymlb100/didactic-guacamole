package com.lotterynet.pro.core.results

import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.repository.ResultsRepository
import com.lotterynet.pro.core.sync.SyncFreshnessKey
import com.lotterynet.pro.core.sync.SyncFreshnessRecord
import com.lotterynet.pro.core.sync.SyncFreshnessRepository
import com.lotterynet.pro.core.sync.SyncFreshnessState
import com.lotterynet.pro.core.sync.SyncGovernor
import com.lotterynet.pro.core.sync.canUseCachedSyncState
import com.lotterynet.pro.core.sync.resolveCacheAwareSyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ResultsScraperOrchestrator(
    private val remoteStore: ResultsRemoteStore,
    private val localResultsRepository: ResultsRepository,
    private val freshnessRepository: SyncFreshnessRepository? = null,
    private val freshnessKeyFactory: ((String) -> SyncFreshnessKey)? = null,
    private val expectedResultIdsProvider: ((String) -> Set<String>)? = null,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val todayDateProvider: () -> String = ::resultsTodayDateKey,
    private val syncGovernor: SyncGovernor = SyncGovernor(),
) {
    fun refreshDateFromRealtime(date: String): ResultsRefreshResult {
        return refreshDate(
            date = date,
            forceRemote = true,
            allowLive = false,
        )
    }

    fun refreshDate(
        date: String,
        forceRemote: Boolean = false,
        allowLive: Boolean = false,
    ): ResultsRefreshResult {
        val local = localResultsRepository.getResultsForDate(date)
            .filter { result -> resultBelongsToDate(result, date) }
        val expectedResultIds = expectedResultIdsProvider?.invoke(date)
            .orEmpty()
            .mapTo(linkedSetOf(), PickResultIdentityResolver::canonicalKeyForExpectedId)
        val hasRecoverableNoDrawLocal = date == todayDateProvider() && local.any(::isNoDrawResult)
        val expectedLocalComplete = (expectedResultIds.isEmpty() ||
            hasCompleteExpectedLocalResults(local, expectedResultIds)) && !hasRecoverableNoDrawLocal
        if (!forceRemote && expectedResultIds.isNotEmpty() && expectedLocalComplete) {
            return ResultsRefreshResult(
                date = date,
                source = "local",
                results = local,
                updated = false,
                message = "Resultados locales completos",
            )
        }
        val hasPickLocal = local.any(::isPickResult)
        val hasClassicLocal = local.any { !isPickResult(it) }
        val completeLocal = local.isNotEmpty() &&
            expectedLocalComplete &&
            local.all(::isCompletePublishedResult) &&
            (!hasClassicLocal || !isMissingTrackedRemoteResult(local)) &&
            !hasRecoverableNoDrawLocal
        val freshnessKey = freshnessKeyFactory?.invoke(date)
        val freshnessRecord = freshnessKey?.let { freshnessRepository?.getRecord(it) }
        val nowMs = nowEpochMs()
        val missingFreshnessRecord = freshnessKey != null && freshnessRecord == null
        val cacheDecision = resolveCacheAwareSyncState(
            hasLocalData = local.isNotEmpty(),
            forceRemote = forceRemote,
            localComplete = completeLocal,
            freshnessRecord = freshnessRecord ?: if (hasPickLocal || missingFreshnessRecord) {
                SyncFreshnessRecord(SyncFreshnessState.NEEDS_SERVER, nowMs - RESULTS_TODAY_REFRESH_WINDOW_MS - 1)
            } else {
                null
            },
            nowEpochMs = nowMs,
            staleAfterMs = if (freshnessKey == null && completeLocal && !hasPickLocal) {
                Long.MAX_VALUE
            } else {
                RESULTS_TODAY_REFRESH_WINDOW_MS
            },
        )
        if (canUseCachedSyncState(cacheDecision)) {
            return ResultsRefreshResult(
                date = date,
                source = "local",
                results = local,
                updated = false,
                message = "Datos locales listos",
            )
        }
        val needsRemoteCompletion = !expectedLocalComplete ||
            local.any(::isIncompleteClassicResult) ||
            (hasClassicLocal && isMissingTrackedRemoteResult(local)) ||
            hasRecoverableNoDrawLocal
        if (local.isNotEmpty() && !forceRemote && !needsRemoteCompletion && completeLocal && !hasPickLocal && freshnessKey == null) {
            freshnessKey?.let { freshnessRepository?.mark(it, SyncFreshnessState.LOCAL_READY, nowEpochMs()) }
            return ResultsRefreshResult(
                date = date,
                source = "local",
                results = local,
                updated = false,
                message = "Datos locales listos",
            )
        }

        val remotePermit = syncGovernor.tryStartResultsHydrate(date, force = forceRemote)
        if (remotePermit == null) {
            return ResultsRefreshResult(
                date = date,
                source = "local",
                results = local,
                updated = false,
                message = "Sync de resultados ya reciente",
            )
        }
        val remote = try {
            remoteStore.fetchResultsForDate(
                date = date,
                forceLive = shouldForceLiveResultsFetch(
                    selectedDate = date,
                    today = todayDateProvider(),
                    allowLive = allowLive,
                    needsRemoteCompletion = needsRemoteCompletion,
                ),
            )
        } finally {
            syncGovernor.finishResultsHydrate(remotePermit, nowEpochMs())
        }
        if (remote.isNotEmpty()) {
            val merged = mergeResults(local = local, remote = remote)
            localResultsRepository.saveResultsForDate(date, merged)
            freshnessKey?.let { freshnessRepository?.mark(it, SyncFreshnessState.SERVER_UPDATED, nowEpochMs()) }
            return ResultsRefreshResult(
                date = date,
                source = if (local.isNotEmpty()) "local+supabase" else "supabase",
                results = merged,
                updated = true,
            )
        }

        if (local.isNotEmpty()) {
            freshnessKey?.let { freshnessRepository?.mark(it, SyncFreshnessState.SERVER_FAILED_USING_CACHE, nowEpochMs()) }
        }
        return ResultsRefreshResult(
            date = date,
            source = if (forceRemote) "supabase" else "local",
            results = local,
            updated = false,
            message = "No se encontraron resultados remotos para la fecha solicitada.",
        )
    }

    private fun mergeResults(
        local: List<LotteryResult>,
        remote: List<LotteryResult>,
    ): List<LotteryResult> {
        val merged = linkedMapOf<String, LotteryResult>()
        (local + remote).forEach { result ->
            val key = PickResultIdentityResolver.canonicalKeyForResult(result)
            val current = merged[key]
            merged[key] = when {
                current == null -> result
                isNoDrawResult(result) && !hasPublishedNumbers(current) -> result
                classicPrizeCount(result) > classicPrizeCount(current) -> result
                classicPrizeCount(result) < classicPrizeCount(current) -> current
                hasPublishedNumbers(result) && !hasPublishedNumbers(current) -> result
                !hasPublishedNumbers(result) && hasPublishedNumbers(current) -> current
                result.fetchedAtEpochMs >= current.fetchedAtEpochMs -> result
                else -> current
            }
        }
        return merged.values.toList()
    }

    private fun hasPublishedNumbers(result: LotteryResult): Boolean {
        return !result.first.isNullOrBlank() ||
            !result.second.isNullOrBlank() ||
            !result.third.isNullOrBlank() ||
            !result.pick3.isNullOrBlank() ||
            !result.pick4.isNullOrBlank()
    }

    private fun isIncompleteClassicResult(result: LotteryResult): Boolean {
        if (isPickResult(result)) return false
        val count = classicPrizeCount(result)
        return count in 1..2
    }

    private fun isCompletePublishedResult(result: LotteryResult): Boolean {
        if (isNoDrawResult(result)) return true
        if (!hasPublishedNumbers(result)) return false
        if (isPickResult(result)) return true
        return classicPrizeCount(result) >= 3
    }

    private fun isPickResult(result: LotteryResult): Boolean {
        return !result.pick3.isNullOrBlank() || !result.pick4.isNullOrBlank()
    }

    private fun hasCompleteExpectedLocalResults(
        local: List<LotteryResult>,
        expectedResultIds: Set<String>,
    ): Boolean {
        val byId = local.associateBy(PickResultIdentityResolver::canonicalKeyForResult)
        return expectedResultIds.all { id ->
            byId[id]?.let(::isCompletePublishedResult) == true
        }
    }

    companion object {
        const val RESULTS_TODAY_REFRESH_WINDOW_MS = 15 * 60_000L
        private val TRACKED_REMOTE_RESULT_IDS = setOf("23", "24", "27", "28")
    }

    private fun isMissingTrackedRemoteResult(results: List<LotteryResult>): Boolean {
        if (results.isEmpty()) return false
        val availableIds = results.map { it.lotteryId }.toSet()
        return TRACKED_REMOTE_RESULT_IDS.any { it !in availableIds }
    }
}

internal fun shouldForceLiveResultsFetch(
    selectedDate: String,
    today: String,
    allowLive: Boolean,
    needsRemoteCompletion: Boolean,
): Boolean {
    return selectedDate == today && allowLive && needsRemoteCompletion
}

private fun resultsTodayDateKey(): String {
    return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date())
}

fun classicPrizeCount(result: LotteryResult): Int {
    return listOf(result.first, result.second, result.third).count { !it.isNullOrBlank() }
}

fun isNoDrawResult(result: LotteryResult): Boolean {
    return result.status.equals(RESULT_STATUS_NO_DRAW, ignoreCase = true)
}

data class ResultsRefreshResult(
    val date: String,
    val source: String,
    val results: List<LotteryResult>,
    val updated: Boolean,
    val message: String? = null,
)
