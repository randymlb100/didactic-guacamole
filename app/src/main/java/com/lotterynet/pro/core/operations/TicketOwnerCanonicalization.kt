package com.lotterynet.pro.core.operations

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole

fun canonicalizeTicketOwnerForSession(
    ticket: TicketRecord,
    session: ActiveSession,
    users: List<UserAccount>,
): TicketRecord {
    val admin = resolveCanonicalAdmin(ticket, session, users) ?: return ticket
    val seller = resolveCanonicalSeller(ticket, admin, users)
    return ticket.copy(
        adminId = admin.id,
        adminUser = admin.user,
        sellerId = seller?.id ?: ticket.sellerId,
        sellerUser = seller?.user ?: ticket.sellerUser,
        role = seller?.role ?: ticket.role,
    )
}

private fun resolveCanonicalAdmin(
    ticket: TicketRecord,
    session: ActiveSession,
    users: List<UserAccount>,
): UserAccount? {
    val keys = listOf(session.adminId, session.userId, session.adminUser, session.username, ticket.adminId, ticket.adminUser)
    return users.firstOrNull { account ->
        account.role == UserRole.ADMIN && keys.any { key -> sameActorKey(account.id, key) || sameActorKey(account.user, key) }
    }
}

private fun resolveCanonicalSeller(
    ticket: TicketRecord,
    admin: UserAccount,
    users: List<UserAccount>,
): UserAccount? {
    val sellerKeys = listOf(ticket.sellerId, ticket.sellerUser)
    if (sellerKeys.any { key -> sameActorKey(admin.id, key) || sameActorKey(admin.user, key) }) {
        return admin
    }
    return users.firstOrNull { account ->
        account.role == UserRole.CASHIER &&
            (sameActorKey(account.adminId, admin.id) || sameActorKey(account.adminUser, admin.user)) &&
            sellerKeys.any { key -> sameActorKey(account.id, key) || sameActorKey(account.user, key) || sameActorKey(account.displayName, key) }
    }
}

private fun sameActorKey(left: String?, right: String?): Boolean {
    val normalizedLeft = normalizeActorLabelKey(left) ?: return false
    val normalizedRight = normalizeActorLabelKey(right) ?: return false
    return normalizedLeft == normalizedRight
}
