package com.lotterynet.pro.core.sync

import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

internal class TicketRefreshGovernor(
    private val requestCooldownMs: Long = 10_000L,
) {
    private val lastAcceptedAtMs = ConcurrentHashMap<String, Long>()

    fun shouldReuse(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val normalizedKey = key.trim()
        if (normalizedKey.isBlank()) return false
        val last = lastAcceptedAtMs[normalizedKey]
            ?: run {
                lastAcceptedAtMs[normalizedKey] = nowMs
                return false
            }
        return if (nowMs - last <= requestCooldownMs) {
            true
        } else {
            lastAcceptedAtMs[normalizedKey] = nowMs
            false
        }
    }

    fun mark(key: String, nowMs: Long = System.currentTimeMillis()) {
        val normalizedKey = key.trim()
        if (normalizedKey.isBlank()) return
        lastAcceptedAtMs[normalizedKey] = nowMs
    }

    fun clear(keyPrefix: String? = null) {
        val prefix = keyPrefix?.trim().orEmpty()
        if (prefix.isBlank()) {
            lastAcceptedAtMs.clear()
            return
        }
        lastAcceptedAtMs.keys.removeAll { it.startsWith(prefix) }
    }
}

internal fun ticketRefreshGovernorKey(
    ownerKey: String,
    requestType: String,
    authScope: String,
): String {
    return listOf(
        "ticket-refresh",
        normalizeGovernorKeyPart(ownerKey),
        normalizeGovernorKeyPart(requestType),
        normalizeGovernorKeyPart(authScope),
    ).joinToString("|")
}

private fun normalizeGovernorKeyPart(value: String?): String {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return "unknown"
    val lower = clean.lowercase(Locale.US)
    return if (lower == "null" || lower == "undefined") "unknown" else lower
}
