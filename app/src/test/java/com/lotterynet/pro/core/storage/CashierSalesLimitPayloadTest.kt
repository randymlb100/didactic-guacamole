package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
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

    @Test
    fun `cashier user limits resolve by every known account alias`() {
        val payload = """{"defaults":{"daySale":1000,"q":50},"byUser":{"srv-11":{"daySale":4000,"q":25}}}"""
        val account = UserAccount(
            id = "srv-11",
            user = "cashier-11",
            role = UserRole.CASHIER,
            displayName = "Caja 11",
            authUserId = "auth-11",
            adminId = "admin-1",
            adminUser = "admin-one",
            ownerName = "owner-one",
            banca = "Banca Uno",
            cashierPrefix = "CAJ",
        )

        val resolved = decodeCashierUserSalesLimitInputs(payload, account)

        assertEquals(4000.0, resolved?.daySale ?: 0.0, 0.001)
        assertEquals(25.0, resolved?.quiniela ?: 0.0, 0.001)
    }

    @Test
    fun `pool limits are stored separately from cashier defaults`() {
        val defaults = CashierSalesLimitInputs(quiniela = 2_000.0, pale = 500.0)
        val pool = CashierSalesLimitInputs(quiniela = 10_000.0, pale = 4_000.0, superPale = 1_500.0)

        val payload = buildCashierLimitPayloadWithPool(
            currentPayload = encodeCashierSalesLimitInputs(defaults),
            limits = pool,
        )

        assertEquals(defaults, decodeCashierSalesLimitInputs(payload))
        assertEquals(pool, decodeCashierPoolLimitInputs(payload))
        assertTrue(payload.contains("\"pool\""))
    }
}
