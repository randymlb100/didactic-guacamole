package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.repository.UsersRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterBankProvisionerTest {

    @Test
    fun `new admin and cashiers start with zero caja`() {
        val repository = FakeUsersRepository()
        val provisioner = MasterBankProvisioner(repository)

        val result = provisioner.createBank(
            CreateBankRequest(
                ownerName = "Duenio Nuevo",
                bankName = "Banca Nueva",
                address = "",
                phone = "",
                cashierPrefix = "bn",
                cashierCount = 2,
                territory = "RD",
                baseBalance = 5000.0,
            ),
        )

        assertEquals(0.0, result.admin.balance, 0.0)
        assertEquals(listOf(0.0, 0.0), result.cashiers.map { it.balance })
        assertEquals(0.0, repository.getAdmins().single().balance, 0.0)
        assertEquals(listOf(0.0, 0.0), repository.getCashiers().map { it.balance })
    }

    @Test
    fun `profile total converts to one admin plus remaining cashiers`() {
        assertEquals(19, resolveCashierCountFromProfileTotal(20))
        assertEquals(1, resolveCashierCountFromProfileTotal(2))
        assertEquals(1, resolveCashierCountFromProfileTotal(1))
    }

    @Test
    fun `issued credentials share text includes every generated profile`() {
        val repository = FakeUsersRepository()
        val provisioner = MasterBankProvisioner(repository)

        val result = provisioner.createBank(
            CreateBankRequest(
                ownerName = "Randy",
                bankName = "Banca Full",
                address = "",
                phone = "",
                cashierPrefix = "bf",
                cashierCount = resolveCashierCountFromProfileTotal(20),
                territory = "RD",
                baseBalance = 0.0,
            ),
        )

        val shareText = buildIssuedCredentialsShareText(result)

        assertEquals(20, result.issuedCredentials.size)
        assertEquals(19, result.cashiers.size)
        assertTrue(shareText.contains("Banca Full"))
        assertTrue(shareText.contains("Total perfiles: 20"))
        assertTrue(shareText.contains("Cajeros: 19"))
        result.issuedCredentials.forEach { credential ->
            assertTrue(shareText.contains(credential.username))
            assertTrue(shareText.contains(credential.password))
        }
    }

    private class FakeUsersRepository : UsersRepository {
        private var admins: List<UserAccount> = emptyList()
        private var cashiers: List<UserAccount> = emptyList()

        override fun getAdmins(): List<UserAccount> = admins

        override fun getCashiers(): List<UserAccount> = cashiers

        override fun findByIdOrUser(idOrUser: String): UserAccount? {
            return (admins + cashiers).firstOrNull {
                it.id.equals(idOrUser, ignoreCase = true) || it.user.equals(idOrUser, ignoreCase = true)
            }
        }

        override fun saveUsers(admins: List<UserAccount>, cashiers: List<UserAccount>) {
            this.admins = admins
            this.cashiers = cashiers
        }
    }
}
