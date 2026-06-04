package com.lotterynet.pro.core.operations

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import java.util.Locale

data class OperationalScope(
    val role: UserRole,
    val bancaName: String?,
    val adminKeys: Set<String>,
    val cashierKeys: Set<String>,
    val canSeeAllBanks: Boolean,
    val canSeeAllCashiersInBank: Boolean,
)

fun resolveOperationalScope(
    session: ActiveSession,
    cashiers: List<UserAccount> = emptyList(),
): OperationalScope {
    val scopedCashiers = filterCashiersForSession(session, cashiers)
    val cashierKeys = cashierIdentityKeys(scopedCashiers)
    return OperationalScope(
        role = session.role,
        bancaName = session.banca,
        adminKeys = when (session.role) {
            UserRole.CASHIER -> identityKeys(session.userId, session.username)
            else -> identityKeys(session.userId, session.username, session.adminId, session.adminUser)
        },
        cashierKeys = cashierKeys,
        canSeeAllBanks = session.role == UserRole.MASTER,
        canSeeAllCashiersInBank = session.role == UserRole.ADMIN || session.role == UserRole.MASTER,
    )
}

fun filterCashiersForSession(
    session: ActiveSession,
    cashiers: List<UserAccount>,
): List<UserAccount> {
    return when (session.role) {
        UserRole.MASTER -> cashiers.filter { it.role == UserRole.CASHIER }
        UserRole.ADMIN -> cashiers.filter { cashier ->
            val adminKeys = identityKeys(session.userId, session.username, session.adminId, session.adminUser)
            cashier.role == UserRole.CASHIER && (
                normalizeKey(cashier.adminId) in adminKeys ||
                    normalizeKey(cashier.adminUser) in adminKeys ||
                    equalsKey(cashier.banca, session.banca)
                )
        }
        UserRole.SUPERVISOR -> cashiers.filter { cashier ->
            cashier.role == UserRole.CASHIER && supervisorAssignedToSession(cashier, session)
        }
        UserRole.CASHIER -> cashiers.filter { cashier ->
            cashier.role == UserRole.CASHIER && (
                equalsKey(cashier.id, session.userId) ||
                    equalsKey(cashier.user, session.username)
                )
        }
        UserRole.UNKNOWN -> emptyList()
    }.let(::sortCashierAccountsNatural)
}

fun filterTicketsForOperationalScope(
    session: ActiveSession,
    tickets: List<TicketRecord>,
    cashiers: List<UserAccount> = emptyList(),
): List<TicketRecord> {
    val scope = resolveOperationalScope(session, cashiers)
    return tickets.filter { ticketVisibleInScope(it, scope) }
}

fun ticketVisibleInScope(
    ticket: TicketRecord,
    scope: OperationalScope,
): Boolean {
    return when (scope.role) {
        UserRole.MASTER -> true
        UserRole.ADMIN -> {
            matchesTicketAdmin(ticket, scope.adminKeys) ||
                matchesTicketSeller(ticket, scope.adminKeys) ||
                matchesTicketSeller(ticket, scope.cashierKeys)
        }
        UserRole.SUPERVISOR -> matchesTicketSeller(ticket, scope.cashierKeys)
        UserRole.CASHIER -> matchesTicketSeller(ticket, scope.adminKeys) ||
            matchesTicketSeller(ticket, scope.cashierKeys)
        UserRole.UNKNOWN -> false
    }
}

fun supervisorAssignedToSession(
    cashier: UserAccount,
    session: ActiveSession,
): Boolean {
    if (session.role != UserRole.SUPERVISOR) return false
    val supervisorKeys = identityKeys(session.userId, session.username)
    val cashierSupervisorKeys = cashier.supervisorIds
        .plus(cashier.supervisorUsers)
        .mapNotNullTo(mutableSetOf()) { normalizeKey(it) }
    return cashierSupervisorKeys.any { it in supervisorKeys }
}

private fun matchesTicketAdmin(ticket: TicketRecord, keys: Set<String>): Boolean {
    if (keys.isEmpty()) return false
    return normalizeKey(ticket.adminId) in keys || normalizeKey(ticket.adminUser) in keys
}

private fun matchesTicketSeller(ticket: TicketRecord, keys: Set<String>): Boolean {
    if (keys.isEmpty()) return false
    return normalizeKey(ticket.sellerId) in keys || normalizeKey(ticket.sellerUser) in keys
}

private fun cashierIdentityKeys(cashiers: List<UserAccount>): Set<String> {
    val displayNameCounts = cashiers
        .mapNotNull { normalizeKey(it.displayName) }
        .groupingBy { it }
        .eachCount()
    return cashiers.flatMapTo(mutableSetOf()) { cashier ->
        val keys = identityKeys(cashier.id, cashier.user).toMutableSet()
        val displayNameKey = normalizeKey(cashier.displayName)
        if (displayNameKey != null && displayNameCounts[displayNameKey] == 1) {
            keys += displayNameKey
        }
        keys
    }
}

private fun identityKeys(vararg values: String?): Set<String> {
    return values.mapNotNullTo(mutableSetOf()) { normalizeKey(it) }
}

private fun normalizeKey(value: String?): String? {
    return value?.trim()?.lowercase(Locale.getDefault())?.takeIf { it.isNotBlank() }
}

private fun equalsKey(left: String?, right: String?): Boolean {
    val normalizedLeft = normalizeKey(left) ?: return false
    val normalizedRight = normalizeKey(right) ?: return false
    return normalizedLeft == normalizedRight
}
