package com.lotterynet.pro.core.operations

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import java.util.Locale

fun sortCashierAccountsNatural(accounts: List<UserAccount>): List<UserAccount> {
    return accounts
        .filter { it.role == UserRole.CASHIER }
        .sortedWith(
            compareBy<UserAccount> { naturalCashierNumber(it) == null }
                .thenBy { naturalCashierNumber(it) ?: Int.MAX_VALUE }
                .thenBy { cashierSortLabel(it).lowercase(Locale.US) }
                .thenBy { it.user.lowercase(Locale.US) },
        )
}

fun naturalCashierNumber(account: UserAccount): Int? {
    return extractCashierNumberFromUsername(account.user)
        ?: extractCashierNumberFromCashierId(account.id)
        ?: extractCashierNumberFromUsername(account.cashierPrefix.orEmpty())
}

fun cashierSortLabel(account: UserAccount): String {
    return account.user.trim().takeIf { it.isNotBlank() }
        ?: account.cashierPrefix?.trim()?.takeIf { it.isNotBlank() }
        ?: account.id.trim()
}

fun cashierDisplayLabel(account: UserAccount): String {
    listOf(account.displayName, account.ownerName).forEach { candidate ->
        val clean = candidate?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
        if (!clean.looksLikeTechnicalCashierId()) return clean
    }
    account.user.trim().takeIf { it.isNotBlank() && !it.looksLikeTechnicalCashierId() }?.let { return it }
    naturalCashierNumber(account)?.let { number -> return "Cajero ${number.toString().padStart(2, '0')}" }
    account.user.trim().takeIf { it.isNotBlank() && it.looksLikeTechnicalCashierId() }?.let { return "Cajero sin nombre" }
    return account.user.trim().takeIf { it.isNotBlank() } ?: account.id.trim()
}

private fun extractCashierNumberFromUsername(value: String): Int? {
    val clean = value.trim()
    if (clean.isBlank() || clean.looksLikeTechnicalCashierId()) return null
    return USERNAME_WITH_NUMBER_REGEX.matchEntire(clean)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

private fun extractCashierNumberFromCashierId(value: String): Int? {
    val clean = value.trim()
    if (clean.isBlank()) return null
    return TECHNICAL_CASHIER_NUMBER_ID_REGEX.matchEntire(clean)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

private fun String.looksLikeTechnicalCashierId(): Boolean {
    return TECHNICAL_CASHIER_ID_REGEX.matches(trim())
}

private val USERNAME_WITH_NUMBER_REGEX = Regex("""(?i)^[a-z][a-z0-9_-]*?0*(\d+)$""")
private val TECHNICAL_CASHIER_ID_REGEX = Regex("""(?i)^caj(?:[-_].+|\d+)$""")
private val TECHNICAL_CASHIER_NUMBER_ID_REGEX = Regex("""(?i)^caj[-_]?0*(\d+)$""")
