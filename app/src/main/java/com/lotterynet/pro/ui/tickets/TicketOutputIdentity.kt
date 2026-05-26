package com.lotterynet.pro.ui.tickets

import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount

internal fun resolveTicketOutputBancaName(
    ticket: TicketRecord?,
    defaultBancaName: String,
    accounts: List<UserAccount>,
): String {
    val fallback = defaultBancaName.trim().ifBlank { "LotteryNet" }
    val seller = ticket?.let { record ->
        accounts.firstOrNull { account ->
            account.id.equals(record.sellerId.orEmpty(), ignoreCase = true) ||
                account.user.equals(record.sellerUser.orEmpty(), ignoreCase = true)
        }
    }
    return seller?.displayName?.trim()?.takeIf { it.isNotBlank() }
        ?: seller?.banca?.trim()?.takeIf { it.isNotBlank() && !it.equals(fallback, ignoreCase = true) }
        ?: fallback
}
