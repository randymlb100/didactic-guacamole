package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.auth.CredentialFactory
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.storage.LocalUsersDeletedRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository

class MasterBankManager(
    private val usersRepository: LocalUsersRepository,
    private val deletedRepository: LocalUsersDeletedRepository,
) {
    fun toggleBank(adminId: String): ToggleBankResult {
        val admins = usersRepository.getAdmins().toMutableList()
        val cashiers = usersRepository.getCashiers().toMutableList()
        val index = admins.indexOfFirst { it.id.equals(adminId, ignoreCase = true) }
        require(index >= 0) { "No se encontró la banca." }

        val admin = admins[index]
        val nextActive = !admin.active
        val updatedAdmin = admin.copy(active = nextActive)
        admins[index] = updatedAdmin

        var affectedCashiers = 0
        val updatedCashiers = cashiers.map { cashier ->
            if (belongsToAdmin(cashier, admin)) {
                affectedCashiers += 1
                cashier.copy(active = nextActive)
            } else {
                cashier
            }
        }

        usersRepository.saveUsers(admins, updatedCashiers)
        return ToggleBankResult(
            admin = updatedAdmin,
            affectedCashiers = affectedCashiers,
        )
    }

    fun deleteBank(adminId: String): DeleteBankResult {
        val admins = usersRepository.getAdmins()
        val cashiers = usersRepository.getCashiers()
        val admin = admins.firstOrNull { it.id.equals(adminId, ignoreCase = true) }
            ?: throw IllegalArgumentException("No se encontró la banca.")

        val remainingAdmins = admins.filterNot { it.id.equals(admin.id, ignoreCase = true) }
        val removedCashiers = cashiers.filter { belongsToAdmin(it, admin) }
        val remainingCashiers = cashiers.filterNot { belongsToAdmin(it, admin) }

        usersRepository.saveUsers(remainingAdmins, remainingCashiers)
        deletedRepository.rememberDeletedAdmin(admin, removedCashiers)
        return DeleteBankResult(
            admin = admin,
            removedCashiers = removedCashiers.size,
        )
    }

    fun regenerateCredentials(adminId: String): RegenerateCredentialsResult {
        val admins = usersRepository.getAdmins().toMutableList()
        val cashiers = usersRepository.getCashiers().toMutableList()
        val index = admins.indexOfFirst { it.id.equals(adminId, ignoreCase = true) }
        require(index >= 0) { "No se encontró la banca." }

        val admin = admins[index]
        val issued = mutableListOf<IssuedCredential>()
        val adminPassword = CredentialFactory.generatePassword()
        val adminSecret = CredentialFactory.buildSecretFields(adminPassword)
        admins[index] = admin.copy(
            passwordSalt = adminSecret.passwordSalt,
            passwordHash = adminSecret.passwordHash,
            passwordVersion = adminSecret.passwordVersion,
            credChangedAtEpochMs = adminSecret.credChangedAtEpochMs,
        )
        issued += IssuedCredential(
            displayName = admin.displayName ?: admin.banca ?: admin.user,
            username = admin.user,
            password = adminPassword,
            role = admin.role,
        )

        val updatedCashiers = cashiers.map { cashier ->
            if (!belongsToAdmin(cashier, admin)) return@map cashier
            val nextPassword = CredentialFactory.generatePassword()
            val secret = CredentialFactory.buildSecretFields(nextPassword)
            issued += IssuedCredential(
                displayName = cashier.displayName ?: cashier.user,
                username = cashier.user,
                password = nextPassword,
                role = cashier.role,
            )
            cashier.copy(
                passwordSalt = secret.passwordSalt,
                passwordHash = secret.passwordHash,
                passwordVersion = secret.passwordVersion,
                credChangedAtEpochMs = secret.credChangedAtEpochMs,
            )
        }

        usersRepository.saveUsers(admins, updatedCashiers)
        return RegenerateCredentialsResult(
            admin = admins[index],
            affectedCashiers = issued.count { it.role.name == "CASHIER" },
            issuedCredentials = issued,
        )
    }

    private fun belongsToAdmin(account: UserAccount, admin: UserAccount): Boolean {
        return account.adminId.equals(admin.id, ignoreCase = true) ||
            account.adminUser.equals(admin.user, ignoreCase = true) ||
            (
                !account.banca.isNullOrBlank() &&
                    !admin.banca.isNullOrBlank() &&
                    account.banca.equals(admin.banca, ignoreCase = true)
                )
    }
}

data class ToggleBankResult(
    val admin: UserAccount,
    val affectedCashiers: Int,
)

data class DeleteBankResult(
    val admin: UserAccount,
    val removedCashiers: Int,
)

data class RegenerateCredentialsResult(
    val admin: UserAccount,
    val affectedCashiers: Int,
    val issuedCredentials: List<IssuedCredential>,
)
