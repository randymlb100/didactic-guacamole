package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalOwnerIdentityTest {

    @Test
    fun `rejects placeholder owner values`() {
        assertNull(normalizeOperationalOwnerKey(null))
        assertNull(normalizeOperationalOwnerKey(""))
        assertNull(normalizeOperationalOwnerKey(" null "))
        assertNull(normalizeOperationalOwnerKey("undefined"))
    }

    @Test
    fun `admin legacy id wins over alias and uuid`() {
        val session = ActiveSession(
            userId = "5e9553d2-72b2-484e-8b85-095fbce6f2a4",
            username = "nicola01",
            role = UserRole.ADMIN,
            adminId = "ADM-163C38",
            adminUser = "nicola01",
        )
        assertEquals(
            CanonicalOwnerIdentity(
                canonicalOwnerKey = "ADM-163C38",
                aliases = listOf("nicola01", "5e9553d2-72b2-484e-8b85-095fbce6f2a4"),
            ),
            resolveCanonicalOwnerIdentity(session),
        )
    }
}
