package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.UsersRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAuthenticatorContractsTest {
    @Test
    fun `failed non master login can retry with remote users`() {
        assertTrue(shouldRetryLoginWithRemoteUsers("admin01"))
        assertTrue(shouldRetryLoginWithRemoteUsers("sup01"))
        assertTrue(shouldRetryLoginWithRemoteUsers("cajero01"))
        assertFalse(shouldRetryLoginWithRemoteUsers("master"))
        assertFalse(shouldRetryLoginWithRemoteUsers("   "))
    }

    @Test
    fun `supervisor can authenticate from synced users payload`() {
        val secret = CredentialFactory.buildSecretFields("123456")
        val supervisor = UserAccount(
            id = "SUP-1",
            user = "sup01",
            role = UserRole.SUPERVISOR,
            active = true,
            passwordSalt = secret.passwordSalt,
            passwordHash = secret.passwordHash,
            passwordVersion = secret.passwordVersion,
        )
        val authenticator = LocalAuthenticator(
            object : UsersRepository {
                override fun getAdmins(): List<UserAccount> = emptyList()
                override fun getSupervisors(): List<UserAccount> = listOf(supervisor)
                override fun getCashiers(): List<UserAccount> = emptyList()
                override fun findByIdOrUser(idOrUser: String): UserAccount? = null
                override fun saveUsers(admins: List<UserAccount>, cashiers: List<UserAccount>) = Unit
            },
        )

        assertEquals(UserRole.SUPERVISOR, authenticator.authenticate("sup01", "123456")?.role)
    }
}
