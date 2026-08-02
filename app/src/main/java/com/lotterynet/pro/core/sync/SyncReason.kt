package com.lotterynet.pro.core.sync

enum class SyncReason {
    APP_START,
    FOREGROUND,
    BROADCAST,
    FCM,
    MANUAL_REFRESH,
    PERIODIC,
}

internal fun syncReasonFromRaw(value: String?): SyncReason {
    return runCatching {
        SyncReason.valueOf(value.orEmpty().trim().uppercase())
    }.getOrDefault(SyncReason.PERIODIC)
}
