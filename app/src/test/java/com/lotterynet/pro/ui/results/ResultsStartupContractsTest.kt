package com.lotterynet.pro.ui.results

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultsStartupContractsTest {
    @Test
    fun `results first frame uses local rows without remote or bitmap work`() {
        val plan = resolveResultsStartupPlan()

        assertTrue(plan.firstFrameWork.contains(ResultsStartupWork.LOAD_SESSION))
        assertTrue(plan.firstFrameWork.contains(ResultsStartupWork.LOAD_LOCAL_RESULTS))
        assertTrue(plan.firstFrameWork.contains(ResultsStartupWork.BUILD_VISIBLE_ROWS))
        assertFalse(plan.firstFrameWork.contains(ResultsStartupWork.HYDRATE_REMOTE_RESULTS))
        assertFalse(plan.firstFrameWork.contains(ResultsStartupWork.RECONCILE_TICKETS))
        assertFalse(plan.firstFrameWork.contains(ResultsStartupWork.RENDER_RESULTS_BITMAP))
    }

    @Test
    fun `results hydrates and reconciles after first frame`() {
        val plan = resolveResultsStartupPlan()

        assertTrue(plan.afterFirstFrameWork.contains(ResultsStartupWork.HYDRATE_REMOTE_RESULTS))
        assertTrue(plan.afterFirstFrameWork.contains(ResultsStartupWork.RECONCILE_TICKETS))
    }
}
