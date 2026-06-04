package com.lotterynet.pro.core.realtime

import java.util.concurrent.ConcurrentHashMap

class LotterynetRealtimeOrchestrator(
    private val onUsersChanged: () -> Unit = {},
    private val onMasterKeyChanged: (String) -> Unit = {},
    private val onTicketOwnerChanged: (String) -> Unit = {},
    private val onResultsCacheChanged: (String) -> Unit = {},
    private val coalesceWindowMs: Long = 2_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lastDispatchMs = ConcurrentHashMap<String, Long>()

    fun onEvent(event: LotterynetRealtimeEvent) {
        val gateKey = buildString {
            append(event.table)
            append('|')
            append(event.filterValue.orEmpty())
        }
        val now = nowMs()
        val last = lastDispatchMs[gateKey]
        if (last != null && now - last < coalesceWindowMs) return
        lastDispatchMs[gateKey] = now

        when (event.table) {
            "lotterynet_users_state" -> onUsersChanged()
            "lotterynet_master_state" -> event.filterValue?.let(onMasterKeyChanged)
            "lotterynet_tickets_by_owner" -> event.filterValue?.let(onTicketOwnerChanged)
            "result_draws" -> event.filterValue?.let(onResultsCacheChanged)
        }
    }
}
