package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import com.lotterynet.pro.core.remote.isSupabaseAuthRequired
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject

private const val REMOTE_UPDATED_AT_CACHE_TTL_MS = 30_000L

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
    fun fetchSnapshot(
        ownerKey: String,
        fromDate: String? = null,
        toDate: String? = null,
        limit: Int? = null,
    ): NativeTicketRemoteSnapshot {
        val key = normalizedRemoteOwnerKey(ownerKey) ?: return NativeTicketRemoteSnapshot()
        val normalizedFromDate = fromDate?.trim()?.takeIf { it.isNotBlank() }
        val normalizedToDate = toDate?.trim()?.takeIf { it.isNotBlank() }
        val normalizedLimit = limit?.takeIf { it > 0 }?.coerceIn(1, 1000)
        val isBoundedOperationalRead =
            normalizedFromDate != null && normalizedToDate != null && normalizedLimit != null
        val authToken = authTokenOrNull()
        val request = JSONObject()
            .put("action", "fetch")
            .put("ownerKey", key)
            .put("processPendingPrizes", false)
            .put("processPrizeDays", JSONArray(currentPrizeProcessDays()))
        normalizedFromDate?.let { request.put("fromDate", it) }
        normalizedToDate?.let { request.put("toDate", it) }
        normalizedLimit?.let { request.put("limit", it) }
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
        return NativeTicketRemoteSnapshot(
            tickets = parsed.tickets,
            deletedIds = parsed.deletedIds,
            completeScope = response.optBoolean("completeScope", false),
            source = response.optString("source").ifBlank { null },
        )
    }

    fun fetchTickets(ownerKey: String): List<TicketRecord> {
        return fetchSnapshot(ownerKey).tickets
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
    }

    fun upsertTickets(ownerKey: String, tickets: List<TicketRecord>, banca: String? = null) {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return
        val existingDeletedIds = fetchSnapshot(key).deletedIds
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

    fun fetchUpdatedAtFresh(ownerKey: String): String? {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return null
        invalidateTicketUpdatedAtCache(key)
        return fetchUpdatedAt(key)
    }

    private fun invokeTicketList(payload: JSONObject, authToken: String?): JSONObject {
        val requiresAuth = bearerTokenProvider != null && payload.optString("action") != "updated-at"
        if (!requiresAuth && authToken == null) {
            payload.put("includeOfficialStamp", false)
            return edgeClient.invoke("get-ticket-list", payload)
        }
        val token = authToken ?: bearerTokenRefresher?.invoke()?.takeIf { it.isNotBlank() }
        return invokeAuthenticatedTicketList(payload, token, canRefreshAfterFailure = authToken != null)
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

internal fun normalizedRemoteOwnerKey(value: String?): String? {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return null
    val lower = clean.lowercase(Locale.US)
    if (lower == "null" || lower == "undefined") return null
    return clean
}

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

internal fun clearTicketUpdatedAtMemoryCache() {
    ticketUpdatedAtMemoryCache = mutableMapOf()
}

internal fun invalidateTicketUpdatedAtCache(ownerKey: String) {
    ticketUpdatedAtMemoryCache.remove(ownerKey)
}

internal fun cacheTicketUpdatedAt(ownerKey: String, updatedAt: String?, nowMs: Long = System.currentTimeMillis()) {
    ticketUpdatedAtMemoryCache[ownerKey] = TicketUpdatedAtCacheEntry(updatedAt = updatedAt, cachedAtMs = nowMs)
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
