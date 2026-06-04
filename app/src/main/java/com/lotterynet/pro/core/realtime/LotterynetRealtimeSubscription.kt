package com.lotterynet.pro.core.realtime

data class LotterynetRealtimeSubscription(
    val channelName: String,
    val schema: String,
    val table: String,
    val filter: String? = null,
) {
    fun filterValue(): String? {
        val raw = filter ?: return null
        val parts = raw.split("=eq.", limit = 2)
        return parts.getOrNull(1)
    }

    companion object {
        fun usersGlobal(): LotterynetRealtimeSubscription = LotterynetRealtimeSubscription(
            channelName = "users-global",
            schema = "public",
            table = "lotterynet_users_state",
            filter = "scope=eq.global",
        )

        fun masterKey(key: String): LotterynetRealtimeSubscription = LotterynetRealtimeSubscription(
            channelName = "master-$key",
            schema = "public",
            table = "lotterynet_master_state",
            filter = "config_key=eq.$key",
        )

        fun ticketOwner(ownerKey: String): LotterynetRealtimeSubscription = LotterynetRealtimeSubscription(
            channelName = "tickets-$ownerKey",
            schema = "public",
            table = "lotterynet_tickets_by_owner",
            filter = "owner_key=eq.$ownerKey",
        )

        fun rechargeOwner(ownerKey: String): LotterynetRealtimeSubscription = LotterynetRealtimeSubscription(
            channelName = "recharges-$ownerKey",
            schema = "public",
            table = "lotterynet_master_state",
            filter = "config_key=eq.recharge_history:$ownerKey",
        )

        fun resultsDraws(dateKey: String): LotterynetRealtimeSubscription = LotterynetRealtimeSubscription(
            channelName = "results-draws-$dateKey",
            schema = "public",
            table = "result_draws",
            filter = "result_day_key=eq.$dateKey",
        )

        fun resultsCache(key: String): LotterynetRealtimeSubscription = resultsDraws(
            key.substringAfterLast(':', key),
        )

        fun resultsSignal(dateKey: String): LotterynetRealtimeSubscription = resultsDraws(dateKey)
    }
}
