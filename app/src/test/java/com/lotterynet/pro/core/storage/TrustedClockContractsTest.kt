package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.ClockSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedClockContractsTest {

    @Test
    fun `trusted clock refreshes from device when cached elapsed time moves backwards`() {
        val read = resolveTrustedClockRead(
            baseUtcMs = 1_000_000L,
            baseElapsedMs = 500_000L,
            nowElapsedMs = 100_000L,
            deviceUtcMs = 2_000_000L,
            source = ClockSource.DEVICE,
        )

        assertEquals(2_000_000L, read.utcMs)
        assertTrue(read.shouldPersistDeviceBase)
    }

    @Test
    fun `trusted clock refreshes stale device cache when drift is too large`() {
        val read = resolveTrustedClockRead(
            baseUtcMs = 1_000_000L,
            baseElapsedMs = 0L,
            nowElapsedMs = 1_000L,
            deviceUtcMs = 2_000_000L,
            source = ClockSource.DEVICE,
            maxDeviceDriftMs = 60_000L,
        )

        assertEquals(2_000_000L, read.utcMs)
        assertTrue(read.shouldPersistDeviceBase)
    }

    @Test
    fun `trusted clock keeps valid monotonic cache`() {
        val read = resolveTrustedClockRead(
            baseUtcMs = 1_000_000L,
            baseElapsedMs = 10_000L,
            nowElapsedMs = 40_000L,
            deviceUtcMs = 1_030_500L,
            source = ClockSource.DEVICE,
            maxDeviceDriftMs = 60_000L,
        )

        assertEquals(1_030_000L, read.utcMs)
        assertEquals(false, read.shouldPersistDeviceBase)
    }

    @Test
    fun `trusted server clock ignores manual device wall clock changes`() {
        val read = resolveTrustedClockRead(
            baseUtcMs = 1_000_000L,
            baseElapsedMs = 10_000L,
            nowElapsedMs = 70_000L,
            deviceUtcMs = 9_000_000L,
            source = ClockSource.TIME_API,
            maxDeviceDriftMs = 60_000L,
        )

        assertEquals(1_060_000L, read.utcMs)
        assertEquals(false, read.shouldPersistDeviceBase)
    }
}
