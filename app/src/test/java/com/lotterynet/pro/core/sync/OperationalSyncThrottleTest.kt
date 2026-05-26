package com.lotterynet.pro.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalSyncThrottleTest {
    @Test
    fun `resume sync is throttled`() {
        val throttle = OperationalSyncThrottle(minIntervalMs = 60_000L)

        assertTrue(throttle.shouldRun(nowMs = 100_000L, force = false))
        throttle.markRan(nowMs = 100_000L)
        assertFalse(throttle.shouldRun(nowMs = 120_000L, force = false))
        assertTrue(throttle.shouldRun(nowMs = 161_000L, force = false))
    }

    @Test
    fun `manual sync bypasses throttle`() {
        val throttle = OperationalSyncThrottle(minIntervalMs = 60_000L)
        throttle.markRan(nowMs = 100_000L)

        assertTrue(throttle.shouldRun(nowMs = 101_000L, force = true))
    }
}
