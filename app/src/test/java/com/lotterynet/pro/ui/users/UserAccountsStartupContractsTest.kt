package com.lotterynet.pro.ui.users

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAccountsStartupContractsTest {
    @Test
    fun `user accounts first frame excludes reports and server hydration`() {
        val plan = resolveUserAccountsStartupPlan()

        assertTrue(plan.firstFrameWork.contains(UserAccountsStartupWork.LOAD_SESSION))
        assertTrue(plan.firstFrameWork.contains(UserAccountsStartupWork.LOAD_LOCAL_ACCOUNTS))
        assertFalse(plan.firstFrameWork.contains(UserAccountsStartupWork.LOAD_LIMITS_FOR_SELECTION))
        assertFalse(plan.firstFrameWork.contains(UserAccountsStartupWork.LOAD_CASHIER_REPORT))
        assertFalse(plan.firstFrameWork.contains(UserAccountsStartupWork.HYDRATE_REMOTE_USERS))
        assertFalse(plan.firstFrameWork.contains(UserAccountsStartupWork.HYDRATE_REMOTE_LIMITS))
    }

    @Test
    fun `user accounts loads limits and reports after first frame`() {
        val plan = resolveUserAccountsStartupPlan()

        assertTrue(plan.afterFirstFrameWork.contains(UserAccountsStartupWork.LOAD_LIMITS_FOR_SELECTION))
        assertTrue(plan.afterFirstFrameWork.contains(UserAccountsStartupWork.LOAD_CASHIER_REPORT))
        assertTrue(plan.afterFirstFrameWork.contains(UserAccountsStartupWork.HYDRATE_REMOTE_USERS))
        assertTrue(plan.afterFirstFrameWork.contains(UserAccountsStartupWork.HYDRATE_REMOTE_LIMITS))
    }
}
