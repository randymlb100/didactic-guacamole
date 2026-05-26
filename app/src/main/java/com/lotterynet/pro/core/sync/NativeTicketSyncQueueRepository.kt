package com.lotterynet.pro.core.sync

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.repository.NativeSyncQueueRepository
import org.json.JSONArray
import org.json.JSONObject

class NativeTicketSyncQueueRepository(
    context: Context,
) : NativeSyncQueueRepository {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun enqueue(ticketJson: JSONObject) {
        val queue = JSONArray(prefs.getString(KEY_PENDING_TICKETS, "[]") ?: "[]")
        queue.put(ticketJson)
        prefs.edit { putString(KEY_PENDING_TICKETS, queue.toString()) }
    }

    override fun peekAll(): List<JSONObject> {
        val queue = JSONArray(prefs.getString(KEY_PENDING_TICKETS, "[]") ?: "[]")
        return buildList {
            for (index in 0 until queue.length()) {
                val item = queue.optJSONObject(index) ?: continue
                add(item)
            }
        }
    }

    override fun removeByIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val normalized = ids.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (normalized.isEmpty()) return
        val queue = JSONArray(prefs.getString(KEY_PENDING_TICKETS, "[]") ?: "[]")
        val next = JSONArray()
        for (index in 0 until queue.length()) {
            val item = queue.optJSONObject(index) ?: continue
            val id = item.optString("id")
            if (id !in normalized) {
                next.put(item)
            }
        }
        prefs.edit { putString(KEY_PENDING_TICKETS, next.toString()) }
    }

    companion object {
        private const val PREFS_NAME = "native_sync_queue_v1"
        private const val KEY_PENDING_TICKETS = "pending_ticket_queue"
    }
}
