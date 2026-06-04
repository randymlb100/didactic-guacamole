package com.lotterynet.pro.core.model

import java.util.Locale

enum class SportsbookMarketKey(val wireValue: String, val label: String) {
    MONEYLINE("moneyline", "Moneyline"),
    RUNLINE("runline", "Runline"),
    SPREAD("spread", "Spread"),
    TOTAL("total", "Alta/Baja"),
    FIRST_HALF("first_half", "Mitad"),
    FIRST_FIVE("first_five", "F5"),
}

enum class SportsbookTicketStatus(val wireValue: String) {
    PENDING("pending"),
    WON("won"),
    LOST("lost"),
    PUSH("push"),
    VOID("void"),
    PAID("paid"),
}

data class SportsbookFeatureConfig(
    val enabled: Boolean = false,
    val allowedRoles: Set<UserRole> = emptySet(),
    val allowedActorKeys: Set<String> = emptySet(),
    val cashierAdminKeys: Set<String> = emptySet(),
    val enabledMarkets: Set<SportsbookMarketKey> = emptySet(),
) {
    fun canOpen(role: UserRole, actorKey: String?, adminKey: String? = null): Boolean {
        if (!enabled) return false
        if (allowedRoles.isNotEmpty() && role !in allowedRoles) return false
        val normalizedActor = normalizeSportsbookActorKey(actorKey)
        val normalizedAdmin = normalizeSportsbookActorKey(adminKey)
        val normalizedAllowedActors = allowedActorKeys.map(::normalizeSportsbookActorKey).toSet()
        val normalizedCashierAdmins = cashierAdminKeys.map(::normalizeSportsbookActorKey).toSet()
        if (normalizedAllowedActors.isEmpty() && normalizedCashierAdmins.isEmpty()) return false
        if (role == UserRole.CASHIER && normalizedAdmin.isNotBlank() && normalizedAdmin in normalizedCashierAdmins) {
            return true
        }
        return normalizedActor.isNotBlank() && normalizedActor in normalizedAllowedActors
    }

    fun isMarketEnabled(market: SportsbookMarketKey): Boolean {
        return enabledMarkets.isEmpty() || market in enabledMarkets
    }
}

data class SportsbookEvent(
    val id: String,
    val sportKey: String,
    val sportTitle: String,
    val leagueTitle: String?,
    val homeTeam: String,
    val awayTeam: String,
    val homeTeamLogoUrl: String? = null,
    val awayTeamLogoUrl: String? = null,
    val commenceTimeEpochMs: Long,
    val status: String = "scheduled",
)

data class SportsbookMarket(
    val id: String,
    val eventId: String,
    val key: SportsbookMarketKey,
    val title: String,
    val status: String = "open",
    val line: Double? = null,
)

data class SportsbookOdd(
    val id: String,
    val marketId: String,
    val selectionKey: String,
    val selectionLabel: String,
    val decimalOdds: Double,
    val americanOdds: Int? = null,
    val point: Double? = null,
    val status: String = "open",
    val lastUpdatedEpochMs: Long = 0L,
)

data class SportsbookBoardGame(
    val event: SportsbookEvent,
    val markets: List<SportsbookMarket> = emptyList(),
    val odds: List<SportsbookOdd> = emptyList(),
) {
    val isOpen: Boolean
        get() = event.status == "open" || markets.any { it.status == "open" }
}

data class SportsbookBoardSnapshot(
    val games: List<SportsbookBoardGame> = emptyList(),
    val fetchedAtEpochMs: Long = 0L,
    val source: String = "cache",
) {
    val openGames: Int get() = games.count { it.isOpen }
}

data class SportsbookSelection(
    val oddsId: String = "",
    val eventId: String,
    val market: SportsbookMarketKey,
    val eventLabel: String = "",
    val marketTitle: String = market.label,
    val selectionKey: String,
    val selectionLabel: String,
    val decimalOdds: Double,
    val point: Double? = null,
    val oddsLockedAtEpochMs: Long,
)

data class SportsbookTicketDraft(
    val selections: List<SportsbookSelection>,
    val stake: Double,
) {
    val isParlay: Boolean get() = selections.size > 1
}

data class SportsbookTicketSaleResult(
    val ticketCode: String,
    val status: SportsbookTicketStatus,
    val stake: Double,
    val decimalOdds: Double,
    val potentialPayout: Double,
    val duplicate: Boolean = false,
)

data class SportsbookTicketLegRecord(
    val eventLabel: String,
    val marketTitle: String,
    val selectionLabel: String,
    val decimalOdds: Double,
    val status: SportsbookTicketStatus,
)

data class SportsbookTicketRecord(
    val id: String,
    val ticketCode: String,
    val sellerUsername: String,
    val bancaName: String,
    val ticketType: String,
    val stake: Double,
    val decimalOdds: Double,
    val potentialPayout: Double,
    val status: SportsbookTicketStatus,
    val soldAtEpochMs: Long,
    val legs: List<SportsbookTicketLegRecord> = emptyList(),
)

data class SportsbookTicketSummary(
    val totalTickets: Int = 0,
    val pendingTickets: Int = 0,
    val wonTickets: Int = 0,
    val paidTickets: Int = 0,
    val totalStake: Double = 0.0,
    val pendingPayout: Double = 0.0,
    val paidPayout: Double = 0.0,
)

fun calculateSportsbookPotentialPayout(stake: Double, decimalOdds: Double): Double {
    if (stake <= 0.0 || decimalOdds <= 1.0) return 0.0
    return stake * decimalOdds
}

fun calculateSportsbookCombinedDecimalOdds(selections: List<SportsbookSelection>): Double {
    if (selections.isEmpty()) return 0.0
    return selections.fold(1.0) { total, selection ->
        total * selection.decimalOdds.coerceAtLeast(1.0)
    }
}

fun normalizeSportsbookActorKey(raw: String?): String {
    return raw?.trim().orEmpty().lowercase(Locale.US)
}
