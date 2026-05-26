package com.lotterynet.pro.core.auth

import com.lotterynet.pro.core.config.MasterCredentials
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.UsersRepository
import java.security.MessageDigest

class LocalAuthenticator(
    private val usersRepository: UsersRepository,
    private val masterCredentials: MasterCredentials = MasterCredentials.DEFAULT,
) {
    fun authenticate(username: String, password: String): UserAccount? {
        val user = username.trim().lowercase()
        val rawPassword = password.trim()
        if (user.isBlank() || rawPassword.isBlank()) return null

        if (user == masterCredentials.masterUser && verifyHash(rawPassword, masterCredentials.masterSalt, masterCredentials.masterHash)) {
            return UserAccount(
                id = masterCredentials.masterUser,
                user = masterCredentials.masterUser,
                role = UserRole.MASTER,
                displayName = "Master",
                active = true,
                passwordSalt = masterCredentials.masterSalt,
                passwordHash = masterCredentials.masterHash,
                passwordVersion = masterCredentials.authHashVersion,
            )
        }

        return (usersRepository.getAdmins() + usersRepository.getSupervisors() + usersRepository.getCashiers())
            .firstOrNull { account ->
                account.active &&
                    account.user.equals(user, ignoreCase = true) &&
                    !account.passwordSalt.isNullOrBlank() &&
                    !account.passwordHash.isNullOrBlank() &&
                    verifyHash(rawPassword, account.passwordSalt, account.passwordHash)
            }
    }

    private fun verifyHash(password: String, salt: String?, expectedHash: String?): Boolean {
        if (salt.isNullOrBlank() || expectedHash.isNullOrBlank()) return false
        val computed = sha256Hex("$salt:$password")
        return computed.equals(expectedHash.trim(), ignoreCase = true)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
