package com.lotterynet.pro.core.storage

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalUsersRepositorySecurityContractsTest {
    @Test
    fun `users payload omits Recargas Rapidas provider credentials`() {
        val payload = buildUsersPayloadForTest(
            admins = listOf(
                UserAccount(
                    id = "ADM-1",
                    user = "admin01",
                    role = UserRole.ADMIN,
                    recargasRapidasUsername = "rr_admin",
                    recargasRapidasPassword = "rr_secret",
                ),
            ),
            cashiers = emptyList(),
        )

        assertTrue(payload.contains("admin01"))
        assertFalse(payload.contains("recargasRapidasUsername"))
        assertFalse(payload.contains("recargasRapidasPassword"))
        assertFalse(payload.contains("rr_secret"))
    }
}
