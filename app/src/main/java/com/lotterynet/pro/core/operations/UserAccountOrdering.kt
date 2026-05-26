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
    val text = "${account.displayName.orEmpty()} ${account.user}"
    return Regex("""\d+""").find(text)?.value?.toIntOrNull()
}

fun cashierSortLabel(account: UserAccount): String = account.displayName ?: account.user
