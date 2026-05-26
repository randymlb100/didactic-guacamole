package com.lotterynet.pro.core.perf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosPerformanceBudgetTest {
    @Test
    fun `cashier first frame budget is strict for old pos`() {
        assertEquals(700L, PosPerformanceBudget.SCREEN_FIRST_FRAME_TARGET_MS)
        assertEquals(1_200L, PosPerformanceBudget.SCREEN_USABLE_TARGET_MS)
        assertEquals(450L, PosPerformanceBudget.CASHIER_FIRST_FRAME_MS)
        assertEquals(120L, PosPerformanceBudget.LOCAL_READ_UI_WARNING_MS)
        assertEquals(1_500L, PosPerformanceBudget.SECOND_SHARE_MAX_MS)
    }

    @Test
    fun `expensive cashier work is never allowed on main thread`() {
        assertFalse(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.TICKET_JSON_IMPORT))
        assertFalse(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.SYNC_FLUSH))
        assertFalse(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.BITMAP_RENDER))
        assertFalse(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.BLUETOOTH_PRINT))
        assertTrue(MainThreadWorkPolicy.canRunOnMain(MainThreadWork.UI_STATE_UPDATE))
    }
}
