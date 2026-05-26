package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeSessionAuthRepositoryTest {

    @Test
    fun `auth bridge timeout helper returns session when bridge succeeds`() {
        val session = SupabaseAuthBridgeSession(
            authUserId = "user-1",
            accessToken = "token",
            refreshToken = "refresh",
            expiresAtEpochSeconds = 123L,
        )

        val result = attemptSupabaseAuthSession(timeoutMs = 4000L) { session }

        assertEquals("user-1", result?.authUserId)
    }

    @Test
    fun `auth bridge timeout helper returns null when bridge throws`() {
        val result = attemptSupabaseAuthSession(timeoutMs = 4000L) {
            throw IllegalStateException("bridge down")
        }

        assertNull(result)
    }

    @Test
    fun `auth bridge timeout helper returns null when timeout disabled`() {
        val result = attemptSupabaseAuthSession(timeoutMs = 0L) {
            SupabaseAuthBridgeSession(
                authUserId = "user-1",
                accessToken = "token",
                refreshToken = "refresh",
                expiresAtEpochSeconds = 123L,
            )
        }

        assertNull(result)
    }

    @Test
    fun `cashier login requires a usable Supabase JWT session`() {
        val cashier = UserAccount(id = "CAJ-1", user = "cajero1", role = UserRole.CASHIER)

        assertTrue(requiresSupabaseServerSession(cashier))
        assertFalse(hasUsableSupabaseAuthSession(null))
        assertFalse(
            hasUsableSupabaseAuthSession(
                SupabaseAuthBridgeSession(
                    authUserId = "auth-1",
                    accessToken = "",
                    refreshToken = "refresh",
                    expiresAtEpochSeconds = 123L,
                ),
            ),
        )
    }

    @Test
    fun `master login does not require Supabase JWT session`() {
        val master = UserAccount(id = "master", user = "master", role = UserRole.MASTER)

        assertFalse(requiresSupabaseServerSession(master))
    }

    @Test
    fun `successful login clears saved password when remember is off`() {
        assertNull(buildSavedLoginForSuccessfulAuth("cajero1", "123456", remember = false))
    }

    @Test
    fun `successful login stores saved password only when remember is on`() {
        val saved = buildSavedLoginForSuccessfulAuth(" cajero1 ", "123456", remember = true)

        assertEquals("cajero1", saved?.username)
        assertEquals("123456", saved?.password)
        assertTrue(saved?.remember == true)
    }
}
