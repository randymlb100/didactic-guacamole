package com.lotterynet.pro.core.perf

object PosPerformanceBudget {
    const val SCREEN_FIRST_FRAME_TARGET_MS = 700L
    const val SCREEN_USABLE_TARGET_MS = 1_200L
    const val CASHIER_FIRST_FRAME_MS = 450L
    const val LOCAL_READ_UI_WARNING_MS = 120L
    const val SECOND_SHARE_MAX_MS = 1_500L
    const val BLUETOOTH_CONNECT_TIMEOUT_MS = 4_000L
    const val BLUETOOTH_WRITE_TIMEOUT_MS = 8_000L
    const val SYNC_RESUME_THROTTLE_MS = 10_000L
    const val LOW_RAM_BITMAP_MAX_WIDTH_PX = 1260
}
