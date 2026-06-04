package com.lotterynet.pro.core.operations

import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import java.util.Locale

fun buildUserActorLabelLookup(users: List<UserAccount>): Map<String, String> {
    return buildActorLabelLookup(
        users.flatMap { user ->
            val label = resolveUserActorDisplayLabel(user)
            listOf(user.id to label, user.user to label, user.displayName to label, user.authUserId to label)
        },
    )
}

fun buildActorLabelLookup(entries: Iterable<Pair<String?, String?>>): Map<String, String> {
    val labels = linkedMapOf<String, String>()
    entries.forEach { (key, label) ->
        val normalizedKey = normalizeActorLabelKey(key) ?: return@forEach
        val cleanLabel = label?.trim()?.takeIf { it.isNotBlank() } ?: return@forEach
        labels.putIfAbsent(normalizedKey, cleanLabel)
    }
    return labels
}

fun resolveTicketActorLabel(
    ticket: TicketRecord,
    actorLabelsByKey: Map<String, String> = emptyMap(),
    fallback: String = "sin cajero",
): String {
    val sellerLabel = listOf(ticket.sellerId, ticket.sellerUser)
        .firstNotNullOfOrNull { key ->
            normalizeActorLabelKey(key)?.let(actorLabelsByKey::get)
        }
    if (!sellerLabel.isNullOrBlank()) return sellerLabel

    return ticket.sellerUser?.takeIf { it.isNotBlank() }
        ?: ticket.adminUser?.takeIf { it.isNotBlank() }
        ?: ticket.sellerId?.takeIf { it.isNotBlank() }
        ?: ticket.adminId?.takeIf { it.isNotBlank() }
        ?: fallback
}

fun resolveUserActorDisplayLabel(user: UserAccount): String {
    return user.displayName?.takeIf { it.isNotBlank() }
        ?: user.banca?.takeIf { it.isNotBlank() }
        ?: user.ownerName?.takeIf { it.isNotBlank() }
        ?: user.user
}

fun normalizeActorLabelKey(value: String?): String? {
    return value?.trim()?.lowercase(Locale.getDefault())?.takeIf { it.isNotBlank() }
}
