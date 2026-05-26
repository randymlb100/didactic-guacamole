package com.lotterynet.pro.core.users

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPasswordBackendClientTest {

    @Test
    fun `password change payload names actor and target`() {
        val payload = buildChangeUserPasswordPayload(
            session = ActiveSession(
                userId = "master",
                username = "master",
                role = UserRole.MASTER,
                banca = null,
                territory = null,
            ),
            target = UserAccount(id = "adm-1", user = "admin01", role = UserRole.ADMIN),
            newPassword = "clave123",
        )

        assertEquals("master", payload.getString("actorId"))
        assertEquals("master", payload.getString("actorUser"))
        assertEquals("master", payload.getString("actorRole"))
        assertEquals("adm-1", payload.getString("targetId"))
        assertEquals("admin01", payload.getString("targetUser"))
        assertEquals("admin", payload.getString("targetRole"))
        assertEquals("clave123", payload.getString("newPassword"))
    }
}
