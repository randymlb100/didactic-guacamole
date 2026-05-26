package com.lotterynet.pro.core.auth

import java.security.MessageDigest
import java.security.SecureRandom

object CredentialFactory {
    private const val AUTH_HASH_VERSION = "sha256-v1"
    private const val PASSWORD_CHARS = "abcdefghijkmnpqrstuvwxyz23456789"
    private val secureRandom = SecureRandom()

    fun generatePassword(length: Int = 8): String {
        val safeLength = length.coerceAtLeast(6)
        return buildString(safeLength) {
            repeat(safeLength) {
                append(PASSWORD_CHARS[secureRandom.nextInt(PASSWORD_CHARS.length)])
            }
        }
    }

    fun buildSecretFields(password: String): SecretFields {
        val salt = secureRandomHex(16)
        return SecretFields(
            passwordSalt = salt,
            passwordHash = sha256Hex("$salt:${password.trim()}"),
            passwordVersion = AUTH_HASH_VERSION,
            credChangedAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun secureRandomHex(length: Int): String {
        val bytes = ByteArray((length.coerceAtLeast(2) + 1) / 2)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { "%02x".format(it) }.take(length)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

data class SecretFields(
    val passwordSalt: String,
    val passwordHash: String,
    val passwordVersion: String,
    val credChangedAtEpochMs: Long,
)
