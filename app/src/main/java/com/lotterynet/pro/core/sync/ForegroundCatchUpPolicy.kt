package com.lotterynet.pro.core.sync

data class ForegroundCatchUpInput(
    val ownerKey: String,
    val dateKey: String,
    val hasLocalTickets: Boolean,
    val hasLocalResults: Boolean,
    val ticketStampChanged: Boolean,
    val resultsStampChanged: Boolean,
    val realtimeConnected: Boolean,
    val nowMs: Long,
    val force: Boolean = false,
)

data class ForegroundCatchUpDecision(
    val shouldRun: Boolean,
    val refreshTickets: Boolean,
    val refreshResults: Boolean,
    val reconnectRealtime: Boolean,
    val reason: String,
)

class ForegroundCatchUpPolicy(
    private val throttle: OperationalSyncThrottle,
) {
    @Synchronized
    fun decide(input: ForegroundCatchUpInput): ForegroundCatchUpDecision {
        val ownerReady = input.ownerKey.isNotBlank()
        val dateReady = input.dateKey.isNotBlank()
        val refreshTickets = ownerReady && (!input.hasLocalTickets || input.ticketStampChanged || input.force)
        val refreshResults = dateReady && (!input.hasLocalResults || input.resultsStampChanged || input.force)
        val reconnectRealtime = !input.realtimeConnected || input.force
        val needsCatchUp = refreshTickets || refreshResults || reconnectRealtime

        if (!needsCatchUp) {
            return ForegroundCatchUpDecision(
                shouldRun = false,
                refreshTickets = false,
                refreshResults = false,
                reconnectRealtime = false,
                reason = "Local state is fresh.",
            )
        }
        if (!throttle.shouldRun(input.nowMs, input.force)) {
            return ForegroundCatchUpDecision(
                shouldRun = false,
                refreshTickets = false,
                refreshResults = false,
                reconnectRealtime = false,
                reason = "Foreground catch-up throttled.",
            )
        }

        throttle.markRan(input.nowMs)
        return ForegroundCatchUpDecision(
            shouldRun = true,
            refreshTickets = refreshTickets,
            refreshResults = refreshResults,
            reconnectRealtime = reconnectRealtime,
            reason = "Foreground catch-up required.",
        )
    }
}
