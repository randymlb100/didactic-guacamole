package com.lotterynet.pro.ui.sales

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierStartupContractsTest {
    @Test
    fun `cashier first frame excludes remote and render work`() {
        val plan = resolveCashierStartupPlan()

        assertTrue(plan.firstFrameWork.contains(CashierStartupWork.LOAD_SESSION))
        assertTrue(plan.firstFrameWork.contains(CashierStartupWork.LOAD_LOCAL_DRAFT))
        assertTrue(plan.firstFrameWork.contains(CashierStartupWork.LOAD_LOCAL_LIMITS))
        assertFalse(plan.firstFrameWork.contains(CashierStartupWork.HYDRATE_REMOTE_TICKETS))
        assertFalse(plan.firstFrameWork.contains(CashierStartupWork.HYDRATE_REMOTE_LIMITS))
        assertFalse(plan.firstFrameWork.contains(CashierStartupWork.HYDRATE_REMOTE_USERS))
        assertFalse(plan.firstFrameWork.contains(CashierStartupWork.FLUSH_SYNC_QUEUE))
        assertFalse(plan.firstFrameWork.contains(CashierStartupWork.RENDER_TICKET_BITMAP))
    }

    @Test
    fun `cashier heavy startup work runs after first frame`() {
        val plan = resolveCashierStartupPlan()

        assertTrue(plan.afterFirstFrameWork.contains(CashierStartupWork.HYDRATE_REMOTE_TICKETS))
        assertTrue(plan.afterFirstFrameWork.contains(CashierStartupWork.HYDRATE_REMOTE_LIMITS))
        assertTrue(plan.afterFirstFrameWork.contains(CashierStartupWork.HYDRATE_REMOTE_USERS))
        assertTrue(plan.afterFirstFrameWork.contains(CashierStartupWork.FLUSH_SYNC_QUEUE))
    }
}
