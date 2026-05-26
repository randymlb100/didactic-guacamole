package com.lotterynet.pro.core.perf

enum class MainThreadWork {
    UI_STATE_UPDATE,
    TICKET_JSON_IMPORT,
    TICKET_JSON_EXPORT,
    SYNC_FLUSH,
    BITMAP_RENDER,
    BITMAP_EXPORT,
    BLUETOOTH_PRINT,
}

object MainThreadWorkPolicy {
    fun canRunOnMain(work: MainThreadWork): Boolean {
        return work == MainThreadWork.UI_STATE_UPDATE
    }
}
