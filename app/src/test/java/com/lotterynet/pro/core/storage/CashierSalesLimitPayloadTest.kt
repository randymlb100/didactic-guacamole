package com.lotterynet.pro.core.storage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashierSalesLimitPayloadTest {

    @Test
    fun `admin self limits are stored away from cashier defaults`() {
        val payload = buildCashierLimitPayloadWithAdminSelf(
            currentPayload = encodeCashierSalesLimitInputs(
                CashierSalesLimitInputs(superPale = 100.0, pick3Straight = 10.0),
            ),
            limits = CashierSalesLimitInputs(superPale = 75.0, pick3Straight = 0.0),
        )

        val root = JSONObject(payload)
        assertEquals(100.0, root.optJSONObject("defaults")!!.optDouble("sp"), 0.001)
        assertEquals(75.0, root.optJSONObject("adminSelf")!!.optDouble("sp"), 0.001)
        assertFalse(root.optJSONObject("adminSelf")!!.has("defaults"))
    }

    @Test
    fun `empty admin self limits mean admin remains unlimited`() {
        val payload = buildCashierLimitPayloadWithAdminSelf(
            currentPayload = null,
            limits = CashierSalesLimitInputs(
                daySale = 0.0,
                payout = 0.0,
                quiniela = 0.0,
                pale = 0.0,
                superPale = 0.0,
                tripleta = 0.0,
                pick3Straight = 0.0,
                pick3Box = 0.0,
                pick4Straight = 0.0,
                pick4Box = 0.0,
            ),
        )

        assertTrue(resolveAdminSelfLimitsAreEmpty(payload))
    }
}
