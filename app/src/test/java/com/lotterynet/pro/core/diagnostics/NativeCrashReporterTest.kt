package com.lotterynet.pro.core.diagnostics

import com.lotterynet.pro.core.remote.SupabaseEdgeException
import com.lotterynet.pro.core.remote.SupabaseEdgeFailureReason
import java.io.InterruptedIOException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCrashReporterTest {
    @Test
    fun `uncaught crashes are always sent to sentry`() {
        assertTrue(shouldCaptureNativeReportInSentry("uncaught", IllegalStateException("real crash")))
    }

    @Test
    fun `handled unknown host is kept local only`() {
        val error = UnknownHostException("Unable to resolve host \"unhoulkujbtsypccpirc.supabase.co\"")

        assertFalse(shouldCaptureNativeReportInSentry("TicketOfficial.payTicketBackend", error))
    }

    @Test
    fun `handled edge timeout is kept local only`() {
        val error = InterruptedIOException("timeout")

        assertFalse(shouldCaptureNativeReportInSentry("SalesActivity.createTicketBackend", error))
    }

    @Test
    fun `handled edge timeout wrapped by supabase keeps its transport cause and stays local`() {
        val transportCause = InterruptedIOException("timeout")
        val error = SupabaseEdgeException(
            userMessage = "El servidor tardó demasiado.",
            technicalMessage = "timeout",
            reason = SupabaseEdgeFailureReason.REMOTE_ERROR,
        )
        error.initCause(transportCause)

        assertTrue(error.cause === transportCause)
        assertFalse(shouldCaptureNativeReportInSentry("SalesActivity.createTicketBackend", error))
    }

    @Test
    fun `handled missing auth jwt is kept local only`() {
        val error = SupabaseEdgeException(
            userMessage = "Sesion del servidor requerida.",
            technicalMessage = "Missing Supabase Auth JWT for server-first operation.",
            reason = SupabaseEdgeFailureReason.AUTH_REQUIRED,
        )

        assertFalse(shouldCaptureNativeReportInSentry("SalesActivity.startupSync", error))
    }
}
