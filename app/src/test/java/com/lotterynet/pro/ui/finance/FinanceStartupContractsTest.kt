package com.lotterynet.pro.ui.finance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceStartupContractsTest {
    @Test
    fun `finance first frame keeps only session and day totals`() {
        val plan = resolveFinanceStartupPlan()

        assertTrue(plan.firstFrameWork.contains(FinanceStartupWork.LOAD_SESSION))
        assertTrue(plan.firstFrameWork.contains(FinanceStartupWork.LOAD_DAY_TOTALS))
        assertFalse(plan.firstFrameWork.contains(FinanceStartupWork.LOAD_ACTOR_SUMMARY))
        assertFalse(plan.firstFrameWork.contains(FinanceStartupWork.LOAD_TURNO_SUMMARY))
        assertFalse(plan.firstFrameWork.contains(FinanceStartupWork.LOAD_HISTORY))
        assertFalse(plan.firstFrameWork.contains(FinanceStartupWork.HYDRATE_REMOTE_DATA))
        assertFalse(plan.firstFrameWork.contains(FinanceStartupWork.RENDER_FINANCE_BITMAP))
        assertFalse(plan.firstFrameWork.contains(FinanceStartupWork.SHOW_TREND_BARS))
    }

    @Test
    fun `finance heavy work runs after first frame`() {
        val plan = resolveFinanceStartupPlan()

        assertTrue(plan.afterFirstFrameWork.contains(FinanceStartupWork.LOAD_ACTOR_SUMMARY))
        assertTrue(plan.afterFirstFrameWork.contains(FinanceStartupWork.LOAD_TURNO_SUMMARY))
        assertTrue(plan.afterFirstFrameWork.contains(FinanceStartupWork.LOAD_HISTORY))
        assertTrue(plan.afterFirstFrameWork.contains(FinanceStartupWork.HYDRATE_REMOTE_DATA))
    }
}
