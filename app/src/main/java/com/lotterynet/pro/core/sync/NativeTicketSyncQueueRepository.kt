package com.lotterynet.pro.core.sync

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.repository.NativeSyncQueueRepository
import java.util.LinkedHashMap
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class NativeTicketSyncQueueRepository(
    context: Context,
) : NativeSyncQueueRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun enqueue(ticketJson: JSONObject) {
        val ticketId = normalizedTicketQueueId(ticketJson)
        val current = readQueue()
        val next = if (ticketId.isBlank()) {
            current + ticketJson
        } else {
            current.filterNot { normalizedTicketQueueId(it) == ticketId } + ticketJson
        }
        writeQueue(dedupeTicketSyncQueue(next))
    }

    override fun peekAll(): List<JSONObject> {
        val current = readQueue()
        val deduped = dedupeTicketSyncQueue(current)
        if (deduped.size != current.size) writeQueue(deduped)
        return deduped
    }

    override fun removeByIds(ids: Collection<String>) {
        val next = removeTicketSyncQueueIds(peekAll(), ids)
        writeQueue(next)
    }

    private fun readQueue(): List<JSONObject> {
        val queue = JSONArray(prefs.getString(KEY_PENDING_TICKETS, "[]") ?: "[]")
        return buildList {
            for (index in 0 until queue.length()) {
                val item = queue.optJSONObject(index) ?: continue
                add(item)
            }
        }
    }

    private fun writeQueue(items: List<JSONObject>) {
        val next = JSONArray()
        items.forEach { next.put(it) }
        prefs.edit { putString(KEY_PENDING_TICKETS, next.toString()) }
    }

    companion object {
        private const val PREFS_NAME = "native_sync_queue_v1"
        private const val KEY_PENDING_TICKETS = "pending_ticket_queue"
    }
}

internal fun normalizedTicketQueueId(json: JSONObject): String {
    return json.optString("id").trim().lowercase(Locale.US)
}

internal fun dedupeTicketSyncQueue(items: List<JSONObject>): List<JSONObject> {
    val withoutId = mutableListOf<JSONObject>()
    val byId = LinkedHashMap<String, JSONObject>()
    items.forEach { item ->
        val id = normalizedTicketQueueId(item)
        if (id.isBlank()) {
            withoutId += item
        } else {
            byId.remove(id)
            byId[id] = item
        }
    }
    return withoutId + byId.values
}

internal fun removeTicketSyncQueueIds(
    items: List<JSONObject>,
    ids: Collection<String>,
): List<JSONObject> {
    if (ids.isEmpty()) return items
    val normalized = ids.map { it.trim().lowercase(Locale.US) }.filter { it.isNotBlank() }.toSet()
    if (normalized.isEmpty()) return items
    return items.filterNot { normalizedTicketQueueId(it) in normalized }
}
