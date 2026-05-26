package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseAuthBridgeClientTest {
    @Test
    fun `legacy auth login payload keeps current user id and password`() {
        val payload = buildLegacyAuthLoginPayload(
            account = UserAccount(
                id = "CAJ-1",
                user = "banca01",
                role = UserRole.CASHIER,
            ),
            password = "miClave123",
        )

        assertEquals("banca01", payload.getString("username"))
        assertEquals("CAJ-1", payload.getString("legacyId"))
        assertEquals("cashier", payload.getString("roleHint"))
        assertEquals("miClave123", payload.getString("password"))
    }
}
