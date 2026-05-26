package com.lotterynet.pro.core.sync

const val OWNER_HYDRATE_MIN_INTERVAL_MS: Long = 30_000L
const val RESULTS_HYDRATE_MIN_INTERVAL_MS: Long = 60_000L

class SyncPermit internal constructor(
    val key: String,
    val startedAtMs: Long,
    val force: Boolean,
) {
    val ownerKey: String
        get() = key
}

class SyncGovernor(
    private val minOwnerHydrateIntervalMs: Long = OWNER_HYDRATE_MIN_INTERVAL_MS,
    private val minResultsHydrateIntervalMs: Long = RESULTS_HYDRATE_MIN_INTERVAL_MS,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private val ownerHydratesInFlight = mutableSetOf<String>()
    private val ownerHydratesLastFinishedMs = mutableMapOf<String, Long>()
    private val resultHydratesInFlight = mutableSetOf<String>()
    private val resultHydratesLastFinishedMs = mutableMapOf<String, Long>()

    fun tryStartOwnerHydrate(ownerKey: String, force: Boolean = false): SyncPermit? {
        val key = normalizeOwnerKey(ownerKey)
        if (key.isBlank()) return null
        val nowMs = nowEpochMs()
        synchronized(lock) {
            if (key in ownerHydratesInFlight) return null
            val lastFinished = ownerHydratesLastFinishedMs[key]
            if (!force && lastFinished != null && nowMs - lastFinished < minOwnerHydrateIntervalMs) {
                return null
            }
            ownerHydratesInFlight += key
            return SyncPermit(key = key, startedAtMs = nowMs, force = force)
        }
    }

    fun finishOwnerHydrate(permit: SyncPermit, finishedAtMs: Long = nowEpochMs()) {
        finishOwnerHydrate(permit.ownerKey, finishedAtMs)
    }

    fun finishOwnerHydrate(ownerKey: String, finishedAtMs: Long = nowEpochMs()) {
        val key = normalizeOwnerKey(ownerKey)
        if (key.isBlank()) return
        synchronized(lock) {
            ownerHydratesInFlight -= key
            ownerHydratesLastFinishedMs[key] = finishedAtMs
        }
    }

    fun tryStartResultsHydrate(dateKey: String, force: Boolean = false): SyncPermit? {
        val key = normalizeDateKey(dateKey)
        if (key.isBlank()) return null
        val nowMs = nowEpochMs()
        synchronized(lock) {
            if (key in resultHydratesInFlight) return null
            val lastFinished = resultHydratesLastFinishedMs[key]
            if (!force && lastFinished != null && nowMs - lastFinished < minResultsHydrateIntervalMs) {
                return null
            }
            resultHydratesInFlight += key
            return SyncPermit(key = key, startedAtMs = nowMs, force = force)
        }
    }

    fun finishResultsHydrate(permit: SyncPermit, finishedAtMs: Long = nowEpochMs()) {
        finishResultsHydrate(permit.key, finishedAtMs)
    }

    fun finishResultsHydrate(dateKey: String, finishedAtMs: Long = nowEpochMs()) {
        val key = normalizeDateKey(dateKey)
        if (key.isBlank()) return
        synchronized(lock) {
            resultHydratesInFlight -= key
            resultHydratesLastFinishedMs[key] = finishedAtMs
        }
    }

    internal fun clearForTests() {
        synchronized(lock) {
            ownerHydratesInFlight.clear()
            ownerHydratesLastFinishedMs.clear()
            resultHydratesInFlight.clear()
            resultHydratesLastFinishedMs.clear()
        }
    }

    private fun normalizeOwnerKey(ownerKey: String): String = ownerKey.trim().lowercase()
    private fun normalizeDateKey(dateKey: String): String = dateKey.trim().lowercase()

    companion object {
        val shared = SyncGovernor()
    }
}
