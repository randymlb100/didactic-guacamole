package com.lotterynet.pro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationalFeedbackContractsTest {

    @Test
    fun `saved feedback carries success tone and concise message`() {
        val feedback = OperationalFeedback.saved("Recarga guardada")

        assertEquals(OperationalFeedbackState.SAVED, feedback.state)
        assertEquals("Recarga guardada", feedback.message)
        assertTrue(feedback.isSuccess)
    }

    @Test
    fun `sync pending feedback is not success and keeps detail`() {
        val feedback = OperationalFeedback.syncPending("Sin conexión")

        assertEquals(OperationalFeedbackState.SYNC_PENDING, feedback.state)
        assertEquals("Sin conexión", feedback.message)
        assertEquals(false, feedback.isSuccess)
    }
}
