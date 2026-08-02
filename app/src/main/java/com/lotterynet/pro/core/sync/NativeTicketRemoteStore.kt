package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import com.lotterynet.pro.core.remote.isSupabaseAuthRequired
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

private const val REMOTE_UPDATED_AT_CACHE_TTL_MS = 30_000L
private const val REMOTE_SNAPSHOT_CACHE_TTL_MS = 10_000L
private const val REMOTE_FRESH_UPDATED_AT_CACHE_TTL_MS = 10_000L
private const val RECENT_AUTHORITATIVE_TICKET_LIMIT = 1000

data class NativeTicketRemoteSnapshot(
    val tickets: List<TicketRecord> = emptyList(),
    val deletedIds: Set<String> = emptySet(),
    val completeScope: Boolean = false,
    val source: String? = null,
)

class NativeTicketRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
    private val bearerTokenProvider: (() -> String?)? = null,
    private val bearerTokenRefresher: (() -> String?)? = null,
) : TicketRemoteStampStore {
    companion object {
        private val sharedRefreshGovernor = TicketRefreshGovernor()
    }

    private val refreshGovernor = sharedRefreshGovernor

    fun fetchSnapshot(
        ownerKey: String,
        fromDate: String? = null,
        toDate: String? = null,
        limit: Int? = null,
        forceRefresh: Boolean = false,
    ): NativeTicketRemoteSnapshot {
        val key = normalizedRemoteOwnerKey(ownerKey) ?: return NativeTicketRemoteSnapshot()
        val normalizedFromDate = fromDate?.trim()?.takeIf { it.isNotBlank() }
        val normalizedToDate = toDate?.trim()?.takeIf { it.isNotBlank() }
        val normalizedLimit = limit?.takeIf { it > 0 }?.coerceIn(1, 1000)
        val isBoundedOperationalRead =
            normalizedFromDate != null && normalizedToDate != null && normalizedLimit != null
        val authToken = authTokenOrNull()
        val authScope = if (authToken != null) "auth" else "anon"
        val request = JSONObject()
            .put("action", "fetch")
            .put("ownerKey", key)
            .put("processPendingPrizes", false)
            .put("processPrizeDays", JSONArray(currentPrizeProcessDays()))
        normalizedFromDate?.let { request.put("fromDate", it) }
        normalizedToDate?.let { request.put("toDate", it) }
            normalizedLimit?.let { request.put("limit", it) }
        val requestKey = buildTicketListRequestKey(request, authToken)
        if (!forceRefresh && refreshGovernor.shouldReuse(ticketSnapshotGovernorKey(key, authScope))) {
            readTicketSnapshotCache(requestKey)?.let { cached ->
                return cached
            }
        }
        if (forceRefresh) {
            clearTicketSnapshotMemoryCache(key)
            clearTicketFreshUpdatedAtCache(key)
            invalidateTicketUpdatedAtCache(key)
        }
        val response = if (isBoundedOperationalRead) {
            request
                .put("includeOfficialStamp", true)
                .put("preferSnapshot", false)
            val token = authToken ?: bearerTokenRefresher?.invoke()?.takeIf { it.isNotBlank() }
            invokeAuthenticatedTicketList(
                payload = request,
                token = token,
                canRefreshAfterFailure = authToken != null,
            )
        } else {
            request
                .put("includeOfficialStamp", authToken != null)
                .put("preferSnapshot", authToken == null)
            invokeTicketList(request, authToken)
        }
        val payload = response.opt("payload") ?: return NativeTicketRemoteSnapshot(
            completeScope = false,
            source = response.optString("source").ifBlank { null },
        )
        val parsed = parseWebTicketRemotePayload(payload.toRawJsonString())
        val snapshot = NativeTicketRemoteSnapshot(
            tickets = parsed.tickets,
            deletedIds = parsed.deletedIds,
            completeScope = response.optBoolean("completeScope", false),
            source = response.optString("source").ifBlank { null },
        )
        cacheTicketSnapshot(requestKey, snapshot)
        refreshGovernor.mark(ticketSnapshotGovernorKey(key, authScope), System.currentTimeMillis())
        return snapshot
    }

    fun fetchTickets(ownerKey: String): List<TicketRecord> {
        return fetchRecentAuthoritativeSnapshot(ownerKey).tickets
    }

    fun fetchDeltaTickets(
        ownerKey: String,
        sinceCursor: String? = null,
        limit: Int = 80,
        includeItems: Boolean = true,
    ): Pair<List<TicketRecord>, String?> {
        val key = normalizedRemoteOwnerKey(ownerKey) ?: return emptyList<TicketRecord>() to sinceCursor
        val authToken = authTokenOrNull()
        val payload = JSONObject()
            .put("ownerKey", key)
            .put("limit", limit.coerceIn(1, 150))
            .put("includeItems", includeItems)
            .put("processPendingPrizes", false)
        if (!sinceCursor.isNullOrBlank()) {
            payload.put("sinceCursor", sinceCursor)
        }
        val response = invokeAuthenticatedDelta(payload, authToken)
        val tickets = parseTicketDeltaPayload(response.toString(), ownerKey = key)
        val cursor = response.optString("cursor").ifBlank { sinceCursor }
        cacheTicketUpdatedAt(key, cursor)
        return tickets to cursor
    }

    fun upsertSnapshot(
        ownerKey: String,
        tickets: List<TicketRecord>,
        deletedIds: Set<String>,
        banca: String? = null,
    ) {
        val key = normalizedRemoteOwnerKey(ownerKey) ?: return
        val payload = buildWebTicketRemotePayload(tickets, deletedIds, banca)
        val authToken = authTokenOrNull()
        invokeTicketList(
            JSONObject()
                .put("action", "upsert")
                .put("ownerKey", key)
                .put("includeOfficialStamp", authToken != null)
                .put("payload", JSONObject(payload)),
            authToken,
        )
        invalidateTicketUpdatedAtCache(key)
        clearTicketSnapshotMemoryCache(key)
        clearTicketFreshUpdatedAtCache(key)
    }

    fun upsertTickets(ownerKey: String, tickets: List<TicketRecord>, banca: String? = null) {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return
        val existingDeletedIds = fetchRecentAuthoritativeSnapshot(key).deletedIds
        upsertSnapshot(key, tickets, existingDeletedIds, banca)
    }

    override fun fetchUpdatedAt(ownerKey: String): String? {
        val key = normalizedRemoteOwnerKey(ownerKey) ?: return null
        readTicketUpdatedAtCacheEntry(key)?.let { return it.updatedAt }
        return invokeTicketList(
            JSONObject()
                .put("action", "updated-at")
                .put("ownerKey", key)
                .put("includeOfficialStamp", false),
            authToken = null,
        ).optString("updatedAt").ifBlank { null }.also {
            cacheTicketUpdatedAt(key, it)
        }
    }

    fun fetchUpdatedAtFresh(ownerKey: String, forceFresh: Boolean = false): String? {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return null
        val authScope = "anon"
        if (!forceFresh) {
            readTicketFreshUpdatedAtCache(key)?.let { return it }
        }
        if (!forceFresh && refreshGovernor.shouldReuse(ticketUpdatedAtGovernorKey(key, authScope))) {
            readTicketUpdatedAtCache(key)?.let { cached ->
                cacheTicketFreshUpdatedAt(key, cached)
                return cached
            }
        }
        if (forceFresh) {
            invalidateTicketUpdatedAtCache(key)
            clearTicketFreshUpdatedAtCache(key)
        }
        return fetchUpdatedAt(key).also { fresh ->
            cacheTicketFreshUpdatedAt(key, fresh)
        }
    }

    private fun invokeTicketList(payload: JSONObject, authToken: String?): JSONObject {
        val requiresAuth = bearerTokenProvider != null && payload.optString("action") != "updated-at"
        val token = authToken ?: bearerTokenRefresher?.invoke()?.takeIf { it.isNotBlank() }
        val requestKey = buildTicketListRequestKey(payload, if (requiresAuth) token else null)
        return coalescedTicketList(requestKey) {
            if (!requiresAuth && token == null) {
                payload.put("includeOfficialStamp", false)
                edgeClient.invoke("get-ticket-list", payload)
            } else {
                invokeAuthenticatedTicketList(payload, token, canRefreshAfterFailure = authToken != null)
            }
        }
    }

    private fun invokeAuthenticatedTicketList(
        payload: JSONObject,
        token: String?,
        canRefreshAfterFailure: Boolean,
    ): JSONObject {
        return runCatching {
            edgeClient.invokeAuthenticated("get-ticket-list", payload, token)
        }.getOrElse { error ->
            if (!canRefreshAfterFailure || !isSupabaseAuthRequired(error)) throw error
            val refreshed = bearerTokenRefresher?.invoke()?.takeIf { it.isNotBlank() }
            if (refreshed.isNullOrBlank() || refreshed == token) throw error
            edgeClient.invokeAuthenticated("get-ticket-list", payload, refreshed)
        }
    }

    private fun invokeAuthenticatedDelta(payload: JSONObject, authToken: String?): JSONObject {
        val token = authToken ?: bearerTokenRefresher?.invoke()?.takeIf { it.isNotBlank() }
        return runCatching {
            edgeClient.invokeAuthenticated("get-ticket-delta", payload, token)
        }.getOrElse { error ->
            if (!isSupabaseAuthRequired(error)) throw error
            val refreshed = bearerTokenRefresher?.invoke()?.takeIf { it.isNotBlank() }
            if (refreshed.isNullOrBlank() || refreshed == token) throw error
            edgeClient.invokeAuthenticated("get-ticket-delta", payload, refreshed)
        }
    }

    private fun authTokenOrNull(): String? = bearerTokenProvider?.invoke()?.takeIf { it.isNotBlank() }

    private fun fetchRecentAuthoritativeSnapshot(ownerKey: String): NativeTicketRemoteSnapshot {
        val (fromDate, toDate) = recentAuthoritativeDayRange()
        return fetchSnapshot(
            ownerKey = ownerKey,
            fromDate = fromDate,
            toDate = toDate,
            limit = RECENT_AUTHORITATIVE_TICKET_LIMIT,
        )
    }

    private fun currentPrizeProcessDays(): List<String> {
        return listOf(ticketRemoteDateOffset(0), ticketRemoteDateOffset(-1)).distinct()
    }

    private fun Any.toRawJsonString(): String {
        return when (this) {
            is JSONArray -> toString()
            is JSONObject -> toString()
            is String -> this
            else -> toString()
        }
    }
}

private val ticketListInFlightRequests = ConcurrentHashMap<String, FutureTask<JSONObject>>()

private fun buildTicketListRequestKey(payload: JSONObject, bearerToken: String?): String {
    return listOf(
        "get-ticket-list",
        payload.optString("ownerKey").trim().lowercase(Locale.US).ifBlank { "unknown-owner" },
        authScopeKey(bearerToken),
        payload.toString(),
    ).joinToString("|")
}

private fun authScopeKey(bearerToken: String?): String {
    return if (bearerToken.isNullOrBlank()) "anon" else "auth"
}

private fun coalescedTicketList(
    requestKey: String,
    block: () -> JSONObject,
): JSONObject {
    while (true) {
        val existing = ticketListInFlightRequests[requestKey]
        if (existing != null) return existing.get()

        val task = FutureTask { block() }
        val previous = ticketListInFlightRequests.putIfAbsent(requestKey, task)
        if (previous == null) {
            try {
                task.run()
                return task.get()
            } finally {
                ticketListInFlightRequests.remove(requestKey, task)
            }
        }
    }
}

internal fun normalizedRemoteOwnerKey(value: String?): String? {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return null
    val lower = clean.lowercase(Locale.US)
    if (lower == "null" || lower == "undefined") return null
    return clean
}

private fun ticketSnapshotGovernorKey(ownerKey: String, authScope: String): String =
    ticketRefreshGovernorKey(ownerKey, requestType = "snapshot", authScope = authScope)

private fun ticketUpdatedAtGovernorKey(ownerKey: String, authScope: String): String =
    ticketRefreshGovernorKey(ownerKey, requestType = "updated-at", authScope = authScope)

private fun ticketRemoteDateOffset(offsetDays: Int): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Santo_Domingo"))
    calendar.time = Date()
    calendar.add(Calendar.DAY_OF_YEAR, offsetDays)
    return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(calendar.time)
}

internal data class TicketUpdatedAtCacheEntry(
    val updatedAt: String?,
    val cachedAtMs: Long,
)

private var ticketUpdatedAtMemoryCache = mutableMapOf<String, TicketUpdatedAtCacheEntry>()
private var ticketFreshUpdatedAtMemoryCache = mutableMapOf<String, TicketUpdatedAtCacheEntry>()
private val ticketSnapshotMemoryCache = ConcurrentHashMap<String, TicketSnapshotCacheEntry>()

internal fun clearTicketUpdatedAtMemoryCache() {
    ticketUpdatedAtMemoryCache = mutableMapOf()
    ticketFreshUpdatedAtMemoryCache = mutableMapOf()
    ticketSnapshotMemoryCache.clear()
}

internal fun invalidateTicketUpdatedAtCache(ownerKey: String) {
    ticketUpdatedAtMemoryCache.remove(ownerKey)
}

/**
 * Realtime means the next read must observe the event, not a locally cached
 * freshness stamp or snapshot. This only invalidates local caches; it does
 * not change the remote contract or force an extra request by itself.
 */
internal fun invalidateTicketRealtimeCaches(ownerKey: String) {
    invalidateTicketUpdatedAtCache(ownerKey)
    clearTicketFreshUpdatedAtCache(ownerKey)
    clearTicketSnapshotMemoryCache(ownerKey)
}

internal fun cacheTicketUpdatedAt(ownerKey: String, updatedAt: String?, nowMs: Long = System.currentTimeMillis()) {
    ticketUpdatedAtMemoryCache[ownerKey] = TicketUpdatedAtCacheEntry(updatedAt = updatedAt, cachedAtMs = nowMs)
}

internal fun cacheTicketFreshUpdatedAt(ownerKey: String, updatedAt: String?, nowMs: Long = System.currentTimeMillis()) {
    ticketFreshUpdatedAtMemoryCache[ownerKey] = TicketUpdatedAtCacheEntry(updatedAt = updatedAt, cachedAtMs = nowMs)
}

internal fun readTicketUpdatedAtCache(
    ownerKey: String,
    nowMs: Long = System.currentTimeMillis(),
): String? {
    return readTicketUpdatedAtCacheEntry(ownerKey, nowMs)?.updatedAt
}

internal fun readTicketUpdatedAtCacheEntry(
    ownerKey: String,
    nowMs: Long = System.currentTimeMillis(),
): TicketUpdatedAtCacheEntry? {
    val entry = ticketUpdatedAtMemoryCache[ownerKey] ?: return null
    return if (nowMs - entry.cachedAtMs <= REMOTE_UPDATED_AT_CACHE_TTL_MS) {
        entry
    } else {
        ticketUpdatedAtMemoryCache.remove(ownerKey)
        null
    }
}

internal fun readTicketFreshUpdatedAtCache(
    ownerKey: String,
    nowMs: Long = System.currentTimeMillis(),
): String? {
    val entry = ticketFreshUpdatedAtMemoryCache[ownerKey] ?: return null
    return if (nowMs - entry.cachedAtMs <= REMOTE_FRESH_UPDATED_AT_CACHE_TTL_MS) {
        entry.updatedAt
    } else {
        ticketFreshUpdatedAtMemoryCache.remove(ownerKey)
        null
    }
}

internal data class TicketSnapshotCacheEntry(
    val snapshot: NativeTicketRemoteSnapshot,
    val cachedAtMs: Long,
)

internal fun cacheTicketSnapshot(
    requestKey: String,
    snapshot: NativeTicketRemoteSnapshot,
    nowMs: Long = System.currentTimeMillis(),
) {
    ticketSnapshotMemoryCache[requestKey] = TicketSnapshotCacheEntry(snapshot = snapshot, cachedAtMs = nowMs)
}

internal fun readTicketSnapshotCache(
    requestKey: String,
    nowMs: Long = System.currentTimeMillis(),
): NativeTicketRemoteSnapshot? {
    val entry = ticketSnapshotMemoryCache[requestKey] ?: return null
    return if (nowMs - entry.cachedAtMs <= REMOTE_SNAPSHOT_CACHE_TTL_MS) {
        entry.snapshot
    } else {
        ticketSnapshotMemoryCache.remove(requestKey)
        null
    }
}

internal fun clearTicketSnapshotMemoryCache(ownerKey: String? = null) {
    val normalized = ownerKey?.trim().orEmpty()
    if (normalized.isBlank()) {
        ticketSnapshotMemoryCache.clear()
        return
    }
    ticketSnapshotMemoryCache.entries.removeIf { (requestKey, _) ->
        requestKey.contains("|$normalized|", ignoreCase = true) ||
            requestKey.contains("|ownerKey=$normalized", ignoreCase = true)
    }
}

private fun recentAuthoritativeDayRange(): Pair<String, String> {
    val zone = TimeZone.getTimeZone("America/Santo_Domingo")
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }
    val today = Calendar.getInstance(zone)
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    return format.format(yesterday.time) to format.format(today.time)
}

internal fun clearTicketFreshUpdatedAtCache(ownerKey: String? = null) {
    val normalized = ownerKey?.trim().orEmpty()
    if (normalized.isBlank()) {
        ticketFreshUpdatedAtMemoryCache = mutableMapOf()
        return
    }
    ticketFreshUpdatedAtMemoryCache.remove(normalized)
}
