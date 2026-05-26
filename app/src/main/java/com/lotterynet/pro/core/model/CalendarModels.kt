package com.lotterynet.pro.core.model

data class TrustedClockSnapshot(
    val trustedUtcMs: Long,
    val source: ClockSource = ClockSource.DEVICE,
    val syncedAtDeviceEpochMs: Long = System.currentTimeMillis(),
    val operationTerritory: LotteryTerritory = LotteryTerritory.RD,
)

enum class ClockSource {
    DEVICE,
    CACHE,
    TIME_API,
    WORLD_TIME_API,
    NATIVE_SYNC,
}

data class LotteryCloseDecision(
    val isClosed: Boolean,
    val reason: String? = null,
    val drawTime: String? = null,
    val closeTime: String? = null,
    val state: CloseState = if (isClosed) CloseState.CLOSED else CloseState.OPEN,
)

enum class CloseState {
    OPEN,
    WARNING,
    DANGER,
    CLOSED,
}
