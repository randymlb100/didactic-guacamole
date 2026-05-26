package com.lotterynet.pro.core.realtime

enum class LotterynetRealtimeEventType {
    INSERT,
    UPDATE,
    DELETE,
}

data class LotterynetRealtimeEvent(
    val type: LotterynetRealtimeEventType,
    val table: String,
    val filterValue: String? = null,
    val payloadJson: String,
)
