package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.config.SupabaseConfig
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import org.json.JSONArray
import org.json.JSONObject

private const val REMOTE_UPDATED_AT_CACHE_TTL_MS = 120_000L

data class NativeTicketRemoteSnapshot(
    val tickets: List<TicketRecord> = emptyList(),
    val deletedIds: Set<String> = emptySet(),
)

class NativeTicketRemoteStore(
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.KEY,
    private val edgeClient: SupabaseEdgeClient = SupabaseEdgeClient(baseUrl, apiKey),
) : TicketRemoteStampStore {
    fun fetchSnapshot(ownerKey: String): NativeTicketRemoteSnapshot {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return NativeTicketRemoteSnapshot()
        val payload = edgeClient.invoke(
            "get-ticket-list",
            JSONObject()
                .put("action", "fetch")
                .put("ownerKey", key),
        ).opt("payload") ?: return NativeTicketRemoteSnapshot()
        val parsed = parseWebTicketRemotePayload(payload.toRawJsonString())
        return NativeTicketRemoteSnapshot(
            tickets = parsed.tickets,
            deletedIds = parsed.deletedIds,
        )
    }

    fun fetchTickets(ownerKey: String): List<TicketRecord> {
        return fetchSnapshot(ownerKey).tickets
    }

    fun upsertSnapshot(
        ownerKey: String,
        tickets: List<TicketRecord>,
        deletedIds: Set<String>,
        banca: String? = null,
    ) {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return
        val payload = buildWebTicketRemotePayload(tickets, deletedIds, banca)
        edgeClient.invoke(
            "get-ticket-list",
            JSONObject()
                .put("action", "upsert")
                .put("ownerKey", key)
                .put("payload", JSONObject(payload)),
        )
        invalidateTicketUpdatedAtCache(key)
    }

    fun upsertTickets(ownerKey: String, tickets: List<TicketRecord>, banca: String? = null) {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return
        val existingDeletedIds = fetchSnapshot(key).deletedIds
        upsertSnapshot(key, tickets, existingDeletedIds, banca)
    }

    override fun fetchUpdatedAt(ownerKey: String): String? {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return null
        readTicketUpdatedAtCacheEntry(key)?.let { return it.updatedAt }
        return edgeClient.invoke(
            "get-ticket-list",
            JSONObject()
                .put("action", "updated-at")
                .put("ownerKey", key)
                .put("includeOfficialStamp", false),
        ).optString("updatedAt").ifBlank { null }.also {
            cacheTicketUpdatedAt(key, it)
        }
    }

    fun fetchUpdatedAtFresh(ownerKey: String): String? {
        val key = ownerKey.trim().takeIf { it.isNotBlank() } ?: return null
        invalidateTicketUpdatedAtCache(key)
        return fetchUpdatedAt(key)
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
