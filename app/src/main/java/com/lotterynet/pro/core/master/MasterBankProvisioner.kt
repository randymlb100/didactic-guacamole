package com.lotterynet.pro.core.master

import com.lotterynet.pro.core.auth.CredentialFactory
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.UsersRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MasterBankProvisioner(
    private val usersRepository: UsersRepository,
) {
    fun createBank(request: CreateBankRequest): CreateBankResult {
        val ownerName = request.ownerName.trim()
        val bankName = request.bankName.trim()
        require(ownerName.isNotBlank() && bankName.isNotBlank()) { "Nombre y banca son obligatorios." }

        val admins = usersRepository.getAdmins().toMutableList()
        val cashiers = usersRepository.getCashiers().toMutableList()
        if (admins.any { it.user.equals(bankName, true) || it.banca.equals(bankName, true) }) {
            throw IllegalArgumentException("Ya existe una banca parecida.")
        }

        val takenUsers = (admins + cashiers).map { it.user.lowercase(Locale.US) }.toMutableSet()
        val adminUser = nextUniqueUser(ownerName, admins.size + 1, takenUsers)
        val adminPassword = CredentialFactory.generatePassword()
        val adminSecret = CredentialFactory.buildSecretFields(adminPassword)
        val prefix = sanitizePrefix(request.cashierPrefix).ifBlank {
            suggestCashierPrefix(ownerName, bankName, "ven")
        }
        val createdLabel = createdLabel()
        val adminId = randomId("ADM")
        val issued = mutableListOf(
            IssuedCredential(
                displayName = ownerName,
                username = adminUser,
                password = adminPassword,
                role = UserRole.ADMIN,
            )
        )

        val admin = UserAccount(
            id = adminId,
            user = adminUser,
            role = UserRole.ADMIN,
            displayName = ownerName,
            ownerName = ownerName,
            address = request.address.trim().ifBlank { null },
            active = true,
            banca = bankName,
            cashierPrefix = prefix,
            createdLabel = createdLabel,
            territory = request.territory,
            phone = request.phone.trim().ifBlank { null },
            balance = 0.0,
            rechargesEnabled = false,
            rechargesBalance = 0.0,
            passwordSalt = adminSecret.passwordSalt,
            passwordHash = adminSecret.passwordHash,
            passwordVersion = adminSecret.passwordVersion,
            credChangedAtEpochMs = adminSecret.credChangedAtEpochMs,
        )

        val newCashiers = buildList {
            for (index in 1..request.cashierCount.coerceIn(1, 50)) {
                val username = nextUniqueUser(prefix, index, takenUsers)
                val password = CredentialFactory.generatePassword()
                val secret = CredentialFactory.buildSecretFields(password)
                add(
                    UserAccount(
                        id = randomId("CAJ"),
                        user = username,
                        role = UserRole.CASHIER,
                        displayName = "Cajero $index - $bankName",
                        active = true,
                        adminId = admin.id,
                        adminUser = admin.user,
                        banca = bankName,
                        territory = request.territory,
                        balance = 0.0,
                        passwordSalt = secret.passwordSalt,
                        passwordHash = secret.passwordHash,
                        passwordVersion = secret.passwordVersion,
                        credChangedAtEpochMs = secret.credChangedAtEpochMs,
                    )
                )
                issued += IssuedCredential(
                    displayName = "Cajero $index - $bankName",
                    username = username,
                    password = password,
                    role = UserRole.CASHIER,
                )
            }
        }

        admins += admin
        cashiers += newCashiers
        usersRepository.saveUsers(admins, cashiers)

        return CreateBankResult(
            admin = admin,
            cashiers = newCashiers,
            issuedCredentials = issued,
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
        val base = normalizeLoose(seed).replace(" ", "").take(6).ifBlank { "user" }
        return base + number.toString().padStart(2, '0')
    }

    private fun sanitizePrefix(raw: String?): String {
        return normalizeLoose(raw.orEmpty()).replace(" ", "").take(6)
    }

    private fun suggestCashierPrefix(ownerName: String, bankName: String, fallback: String): String {
        return listOf(bankName, ownerName, fallback)
            .map(::sanitizePrefix)
            .firstOrNull { it.length >= 2 }
            ?: "ven"
    }

    private fun normalizeLoose(raw: String): String {
        return raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private fun randomId(prefix: String): String {
        return "$prefix-${UUID.randomUUID().toString().replace("-", "").take(6).uppercase(Locale.US)}"
    }

    private fun createdLabel(): String {
        return SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date())
    }
}

data class CreateBankRequest(
    val ownerName: String,
    val bankName: String,
    val address: String,
    val phone: String,
    val cashierPrefix: String,
    val cashierCount: Int,
    val territory: String,
    val baseBalance: Double,
)

data class IssuedCredential(
    val displayName: String,
    val username: String,
    val password: String,
    val role: UserRole,
)

data class CreateBankResult(
    val admin: UserAccount,
    val cashiers: List<UserAccount>,
    val issuedCredentials: List<IssuedCredential>,
)

fun resolveCashierCountFromProfileTotal(profileTotal: Int): Int {
    return (profileTotal.coerceIn(2, 51) - 1).coerceAtLeast(1)
}

fun buildIssuedCredentialsShareText(result: CreateBankResult): String {
    return buildString {
        appendLine("LotteryNet - Credenciales de banca")
        appendLine("Banca: ${result.admin.banca ?: result.admin.user}")
        appendLine("Admin: ${result.admin.displayName}")
        appendLine("Total perfiles: ${result.issuedCredentials.size}")
        appendLine("Cajeros: ${result.cashiers.size}")
        appendLine()
        result.issuedCredentials.forEachIndexed { index, credential ->
            appendLine("${index + 1}. ${credential.role.name} - ${credential.displayName}")
            appendLine("Usuario: ${credential.username}")
            appendLine("Clave: ${credential.password}")
            if (index < result.issuedCredentials.lastIndex) appendLine()
        }
    }.trim()
}
