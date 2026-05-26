package com.lotterynet.pro.core.sync

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

enum class SyncFreshnessType {
    RESULTS,
    FINANCE_DAY,
    REPORT_PERIOD,
}

enum class SyncFreshnessState {
    LOCAL_READY,
    NEEDS_SERVER,
    SERVER_UPDATED,
    SERVER_FAILED_USING_CACHE,
}

data class SyncFreshnessKey(
    val type: SyncFreshnessType,
    val ownerKey: String,
    val banca: String?,
    val dateKey: String,
)

data class SyncFreshnessRecord(
    val state: SyncFreshnessState,
    val updatedAtEpochMs: Long,
)

interface SyncFreshnessRepository {
    fun getRecord(key: SyncFreshnessKey): SyncFreshnessRecord?
    fun mark(key: SyncFreshnessKey, state: SyncFreshnessState, nowEpochMs: Long = System.currentTimeMillis())
}

class LocalSyncFreshnessRepository(
    context: Context,
) : SyncFreshnessRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getRecord(key: SyncFreshnessKey): SyncFreshnessRecord? {
        val storageKey = buildSyncFreshnessStorageKey(key)
        val stateName = prefs.getString("$storageKey:state", null) ?: return null
        val state = runCatching { SyncFreshnessState.valueOf(stateName) }.getOrNull() ?: return null
        return SyncFreshnessRecord(
            state = state,
            updatedAtEpochMs = prefs.getLong("$storageKey:updatedAt", 0L),
        )
    }

    override fun mark(key: SyncFreshnessKey, state: SyncFreshnessState, nowEpochMs: Long) {
        val storageKey = buildSyncFreshnessStorageKey(key)
        prefs.edit {
            putString("$storageKey:state", state.name)
            putLong("$storageKey:updatedAt", nowEpochMs)
        }
    }

    companion object {
        private const val PREFS_NAME = "lotterynet_sync_freshness"
    }
}

fun buildSyncFreshnessStorageKey(key: SyncFreshnessKey): String {
    return listOf(
        key.type.name.lowercase(Locale.US),
        normalizeSyncFreshnessPart(key.ownerKey),
        normalizeSyncFreshnessPart(key.banca.orEmpty()),
        normalizeSyncFreshnessPart(key.dateKey),
    ).joinToString(":")
}

fun buildSyncFreshnessKey(
    type: SyncFreshnessType,
    ownerKey: String?,
    banca: String?,
    dateKey: String,
): SyncFreshnessKey {
    return SyncFreshnessKey(
        type = type,
        ownerKey = ownerKey?.takeIf { it.isNotBlank() } ?: "global",
        banca = banca?.takeIf { it.isNotBlank() },
        dateKey = dateKey,
    )
}

fun resolveCacheAwareSyncState(
    hasLocalData: Boolean,
    forceRemote: Boolean,
    localComplete: Boolean,
    freshnessRecord: SyncFreshnessRecord?,
    nowEpochMs: Long,
    staleAfterMs: Long,
): SyncFreshnessState {
    if (forceRemote) return SyncFreshnessState.NEEDS_SERVER
    if (!hasLocalData || !localComplete) return SyncFreshnessState.NEEDS_SERVER
    val updatedAt = freshnessRecord?.updatedAtEpochMs ?: return SyncFreshnessState.LOCAL_READY
    return if (nowEpochMs - updatedAt >= staleAfterMs) {
        SyncFreshnessState.NEEDS_SERVER
    } else {
        SyncFreshnessState.LOCAL_READY
    }
}

fun canUseCachedSyncState(state: SyncFreshnessState): Boolean {
    return state == SyncFreshnessState.LOCAL_READY || state == SyncFreshnessState.SERVER_UPDATED
}

private fun normalizeSyncFreshnessPart(value: String): String {
    return value.trim()
        .lowercase(Locale.US)
        .ifBlank { "none" }
        .replace(Regex("""[^a-z0-9._-]+"""), "_")
}
