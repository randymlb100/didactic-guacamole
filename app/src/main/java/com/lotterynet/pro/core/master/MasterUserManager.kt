package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.auth.CredentialFactory
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.UsersRepository
import java.util.Locale
import java.util.UUID

class MasterUserManager(
    private val usersRepository: UsersRepository,
) {
    fun addCashiers(adminId: String, count: Int, prefix: String? = null): AddCashiersResult {
        val admins = usersRepository.getAdmins()
        val cashiers = usersRepository.getCashiers().toMutableList()
        val admin = admins.firstOrNull { it.id.equals(adminId, ignoreCase = true) }
            ?: throw IllegalArgumentException("No se encontró la banca.")
        val quantity = count.coerceIn(1, 50)
        val safePrefix = sanitizePrefix(prefix.orEmpty()).ifBlank {
            sanitizePrefix(admin.cashierPrefix.orEmpty()).ifBlank {
                suggestCashierPrefix(admin)
            }
        }
        val takenUsers = (admins + cashiers).map { it.user.lowercase(Locale.US) }.toMutableSet()
        val startIndex = cashiers.count { belongsToAdmin(it, admin) } + 1
        val issued = mutableListOf<IssuedCredential>()
        val newCashiers = buildList {
            for (offset in 0 until quantity) {
                val username = nextUniqueUser(safePrefix, startIndex + offset, takenUsers)
                val password = CredentialFactory.generatePassword()
                val secret = CredentialFactory.buildSecretFields(password)
                val displayName = "Cajero ${startIndex + offset} - ${admin.banca ?: admin.user}"
                val cashier = UserAccount(
                    id = randomId("CAJ"),
                    user = username,
                    role = UserRole.CASHIER,
                    displayName = displayName,
                    active = admin.active,
                    adminId = admin.id,
                    adminUser = admin.user,
                    banca = admin.banca,
                    territory = admin.territory,
                    balance = 0.0,
                    passwordSalt = secret.passwordSalt,
                    passwordHash = secret.passwordHash,
                    passwordVersion = secret.passwordVersion,
                    credChangedAtEpochMs = secret.credChangedAtEpochMs,
                )
                add(cashier)
                issued += IssuedCredential(
                    displayName = displayName,
                    username = username,
                    password = password,
                    role = UserRole.CASHIER,
                )
            }
        }

        usersRepository.saveUsers(admins, cashiers + newCashiers)
        return AddCashiersResult(
            admin = admin,
            cashiers = newCashiers,
            issuedCredentials = issued,
        )
    }

    fun changePassword(idOrUser: String, newPassword: String): ChangeUserPasswordResult {
        val password = newPassword.trim()
        require(password.length >= 6) { "La clave debe tener 6 caracteres o más." }
        val admins = usersRepository.getAdmins().toMutableList()
        val supervisors = usersRepository.getSupervisors().toMutableList()
        val cashiers = usersRepository.getCashiers().toMutableList()
        val secret = CredentialFactory.buildSecretFields(password)

        val adminIndex = admins.indexOfFirst { matches(it, idOrUser) }
        if (adminIndex >= 0) {
            val updated = admins[adminIndex].withSecret(secret)
            admins[adminIndex] = updated
            usersRepository.saveUsers(admins, supervisors, cashiers)
            return ChangeUserPasswordResult(updated, updated.toIssuedCredential(password))
        }

        val supervisorIndex = supervisors.indexOfFirst { matches(it, idOrUser) }
        if (supervisorIndex >= 0) {
            val updated = supervisors[supervisorIndex].withSecret(secret)
            supervisors[supervisorIndex] = updated
            usersRepository.saveUsers(admins, supervisors, cashiers)
            return ChangeUserPasswordResult(updated, updated.toIssuedCredential(password))
        }

        val cashierIndex = cashiers.indexOfFirst { matches(it, idOrUser) }
        require(cashierIndex >= 0) { "No se encontró el usuario." }
        val updated = cashiers[cashierIndex].withSecret(secret)
        cashiers[cashierIndex] = updated
        usersRepository.saveUsers(admins, supervisors, cashiers)
        return ChangeUserPasswordResult(updated, updated.toIssuedCredential(password))
    }

    fun changeCashierGroupPassword(adminId: String, newPassword: String): ChangeCashierGroupPasswordResult {
        val password = newPassword.trim()
        require(password.length >= 6) { "La clave debe tener 6 caracteres o más." }
        val admins = usersRepository.getAdmins()
        val cashiers = usersRepository.getCashiers().toMutableList()
        val admin = admins.firstOrNull { it.id.equals(adminId, ignoreCase = true) }
            ?: throw IllegalArgumentException("No se encontró la banca.")
        val issued = mutableListOf<IssuedCredential>()
        val updatedCashiers = cashiers.map { cashier ->
            if (!belongsToAdmin(cashier, admin)) return@map cashier
            val secret = CredentialFactory.buildSecretFields(password)
            val updated = cashier.withSecret(secret)
            issued += updated.toIssuedCredential(password)
            updated
        }
        require(issued.isNotEmpty()) { "Esta banca no tiene cajeros." }

        usersRepository.saveUsers(admins, updatedCashiers)
        return ChangeCashierGroupPasswordResult(
            admin = admin,
            credentials = issued,
        )
    }

    private fun UserAccount.withSecret(secret: com.lotterynet.pro.core.auth.SecretFields): UserAccount {
        return copy(
            passwordSalt = secret.passwordSalt,
            passwordHash = secret.passwordHash,
            passwordVersion = secret.passwordVersion,
            credChangedAtEpochMs = secret.credChangedAtEpochMs,
        )
    }

    private fun UserAccount.toIssuedCredential(password: String): IssuedCredential {
        return IssuedCredential(
            displayName = displayName ?: banca ?: user,
            username = user,
            password = password,
            role = role,
        )
    }

    private fun matches(account: UserAccount, idOrUser: String): Boolean {
        val needle = idOrUser.trim()
        return account.id.equals(needle, ignoreCase = true) || account.user.equals(needle, ignoreCase = true)
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

    private fun nextUniqueUser(seed: String, start: Int, taken: MutableSet<String>): String {
        var number = start.coerceAtLeast(1)
        var candidate = generateUser(seed, number)
        while (taken.contains(candidate.lowercase(Locale.US))) {
            number += 1
            candidate = generateUser(seed, number)
        }
        taken += candidate.lowercase(Locale.US)
        return candidate
    }

    private fun generateUser(seed: String, number: Int): String {
        val base = sanitizePrefix(seed).ifBlank { "caj" }
        return base + number.toString().padStart(2, '0')
    }

    private fun sanitizePrefix(raw: String): String {
        return raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "").take(6)
    }

    private fun suggestCashierPrefix(admin: UserAccount): String {
        return listOf(admin.banca, admin.ownerName, admin.user, "caj")
            .filterNotNull()
            .map(::sanitizePrefix)
            .firstOrNull { it.length >= 2 }
            ?: "caj"
    }

    private fun randomId(prefix: String): String {
        return "$prefix-${UUID.randomUUID().toString().replace("-", "").take(6).uppercase(Locale.US)}"
    }
}

data class AddCashiersResult(
    val admin: UserAccount,
    val cashiers: List<UserAccount>,
    val issuedCredentials: List<IssuedCredential>,
)

data class ChangeUserPasswordResult(
    val account: UserAccount,
    val credential: IssuedCredential,
)

data class ChangeCashierGroupPasswordResult(
    val admin: UserAccount,
    val credentials: List<IssuedCredential>,
)
