package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole

data class CanonicalOwnerIdentity(
    val canonicalOwnerKey: String,
    val aliases: List<String>,
)

fun normalizeOperationalOwnerKey(value: String?): String? =
    value?.trim()?.takeIf {
        it.isNotBlank() &&
            !it.equals("null", ignoreCase = true) &&
            !it.equals("undefined", ignoreCase = true)
    }

fun resolveCanonicalOwnerIdentity(session: ActiveSession?): CanonicalOwnerIdentity? {
    session ?: return null
    val valid = when (session.role) {
        UserRole.CASHIER,
        UserRole.SUPERVISOR -> listOf(
            session.userId,
            session.username,
            session.authUserId,
            session.adminId,
            session.adminUser,
        )
        else -> listOf(
            session.adminId,
            session.adminUser,
            session.userId,
            session.username,
            session.authUserId,
        )
    }.mapNotNull(::normalizeOperationalOwnerKey)
        .distinctBy(String::lowercase)
    val canonical = valid.firstOrNull { it.startsWith("ADM-", ignoreCase = true) && session.role == UserRole.ADMIN }
        ?: valid.firstOrNull()
        ?: return null
    return CanonicalOwnerIdentity(
        canonicalOwnerKey = canonical,
        aliases = valid.filterNot { it.equals(canonical, ignoreCase = true) },
    )
}
