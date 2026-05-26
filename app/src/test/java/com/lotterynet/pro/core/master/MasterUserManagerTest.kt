package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.auth.LocalAuthenticator
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.UsersRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterUserManagerTest {

    @Test
    fun `master can add cashiers to an existing admin`() {
        val repository = FakeUsersRepository(
            admins = listOf(
                admin(id = "adm-1", user = "dueno01", banca = "La Buena"),
            ),
            cashiers = listOf(
                cashier(id = "caj-1", user = "lb01", adminId = "adm-1", adminUser = "dueno01", banca = "La Buena"),
            ),
        )
        val manager = MasterUserManager(repository)

        val result = manager.addCashiers(adminId = "adm-1", count = 2, prefix = "lb")

        assertEquals(2, result.cashiers.size)
        assertEquals(2, result.issuedCredentials.size)
        assertEquals(3, repository.getCashiers().size)
        assertTrue(repository.getCashiers().all { it.adminId == "adm-1" && it.adminUser == "dueno01" })
    }

    @Test
    fun `master can set a real password for one user`() {
        val repository = FakeUsersRepository(
            admins = listOf(admin(id = "adm-1", user = "dueno01", banca = "La Buena")),
            cashiers = listOf(cashier(id = "caj-1", user = "lb01", adminId = "adm-1", adminUser = "dueno01", banca = "La Buena")),
        )
        val manager = MasterUserManager(repository)

        val result = manager.changePassword(idOrUser = "lb01", newPassword = "clave123")

        assertEquals("lb01", result.credential.username)
        assertEquals("clave123", result.credential.password)
        assertNotNull(LocalAuthenticator(repository).authenticate("lb01", "clave123"))
    }

    @Test
    fun `master can set supervisor password individually`() {
        val repository = FakeUsersRepository(
            admins = listOf(admin(id = "adm-1", user = "dueno01", banca = "La Buena")),
            supervisors = listOf(
                UserAccount(
                    id = "sup-1",
                    user = "super01",
                    role = UserRole.SUPERVISOR,
                    adminId = "adm-1",
                    adminUser = "dueno01",
                    banca = "La Buena",
                ),
            ),
            cashiers = emptyList(),
        )
        val manager = MasterUserManager(repository)

        val result = manager.changePassword(idOrUser = "super01", newPassword = "super123")

        assertEquals("super01", result.credential.username)
        assertEquals("super123", result.credential.password)
        assertNotNull(LocalAuthenticator(repository).authenticate("super01", "super123"))
    }

    @Test
    fun `master can set one password for all cashiers in one admin`() {
        val repository = FakeUsersRepository(
            admins = listOf(
                admin(id = "adm-1", user = "dueno01", banca = "La Buena"),
                admin(id = "adm-2", user = "dueno02", banca = "La Otra"),
            ),
            cashiers = listOf(
                cashier(id = "caj-1", user = "lb01", adminId = "adm-1", adminUser = "dueno01", banca = "La Buena"),
                cashier(id = "caj-2", user = "lb02", adminId = "adm-1", adminUser = "dueno01", banca = "La Buena"),
                cashier(id = "caj-3", user = "otra01", adminId = "adm-2", adminUser = "dueno02", banca = "La Otra"),
            ),
        )
        val manager = MasterUserManager(repository)

        val result = manager.changeCashierGroupPassword(adminId = "adm-1", newPassword = "misma123")

        assertEquals(2, result.credentials.size)
        assertTrue(result.credentials.all { it.password == "misma123" })
        assertNotNull(LocalAuthenticator(repository).authenticate("lb01", "misma123"))
        assertNotNull(LocalAuthenticator(repository).authenticate("lb02", "misma123"))
        assertEquals(null, LocalAuthenticator(repository).authenticate("otra01", "misma123"))
    }

    private fun admin(id: String, user: String, banca: String): UserAccount {
        return UserAccount(
            id = id,
            user = user,
            role = UserRole.ADMIN,
            displayName = "Dueño",
            ownerName = "Dueño",
            active = true,
            banca = banca,
            cashierPrefix = "lb",
            territory = "RD",
        )
    }

    private fun cashier(
        id: String,
        user: String,
        adminId: String,
        adminUser: String,
        banca: String,
    ): UserAccount {
        return UserAccount(
            id = id,
            user = user,
            role = UserRole.CASHIER,
            displayName = "Cajero",
            active = true,
            adminId = adminId,
            adminUser = adminUser,
            banca = banca,
            territory = "RD",
        )
    }

    private class FakeUsersRepository(
        admins: List<UserAccount>,
        supervisors: List<UserAccount> = emptyList(),
        cashiers: List<UserAccount>,
    ) : UsersRepository {
        private var adminState = admins
        private var supervisorState = supervisors
        private var cashierState = cashiers

        override fun getAdmins(): List<UserAccount> = adminState

        override fun getSupervisors(): List<UserAccount> = supervisorState

        override fun getCashiers(): List<UserAccount> = cashierState

        override fun findByIdOrUser(idOrUser: String): UserAccount? {
            return (adminState + supervisorState + cashierState).firstOrNull {
                it.id.equals(idOrUser, ignoreCase = true) || it.user.equals(idOrUser, ignoreCase = true)
            }
        }

        override fun saveUsers(admins: List<UserAccount>, cashiers: List<UserAccount>) {
            adminState = admins
            cashierState = cashiers
        }

        override fun saveUsers(admins: List<UserAccount>, supervisors: List<UserAccount>, cashiers: List<UserAccount>) {
            adminState = admins
            supervisorState = supervisors
            cashierState = cashiers
        }
    }
}
