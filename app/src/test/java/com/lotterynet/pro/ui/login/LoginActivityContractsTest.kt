package com.lotterynet.pro.ui.login

import com.lotterynet.pro.core.diagnostics.NativeCrashReport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginActivityContractsTest {

    @Test
    fun `login only shows crash status for unhandled reports`() {
        val handled = NativeCrashReport(
            timestampMs = 1L,
            activityName = null,
            source = "ResultsActivity.autoRefresh",
            threadName = "main",
            errorClass = "TimeoutCancellationException",
            message = "Timed out waiting for 20000 ms",
            stackTrace = "",
        )
        val unhandled = handled.copy(source = "uncaught", activityName = "ResultsActivity")

        assertFalse(shouldShowCrashStatusOnLogin(handled))
        assertTrue(shouldShowCrashStatusOnLogin(unhandled))
    }
}
