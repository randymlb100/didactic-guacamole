package com.lotterynet.pro.core.sync

class OperationalSyncThrottle(
    private val minIntervalMs: Long,
) {
    private var lastRunMs: Long? = null

    @Synchronized
    fun shouldRun(nowMs: Long, force: Boolean): Boolean {
        if (force) return true
        val previous = lastRunMs ?: return true
        return nowMs - previous >= minIntervalMs
    }

    @Synchronized
    fun markRan(nowMs: Long) {
        lastRunMs = nowMs
    }
}
