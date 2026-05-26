package com.lotterynet.pro.core.model

enum class OperationalFeedbackState {
    IDLE,
    SAVED,
    ERROR,
    SYNC_PENDING,
    OFFLINE,
}

data class OperationalFeedback(
    val state: OperationalFeedbackState,
    val message: String,
) {
    val isSuccess: Boolean
        get() = state == OperationalFeedbackState.SAVED

    companion object {
        fun idle(message: String) = OperationalFeedback(OperationalFeedbackState.IDLE, message)
        fun saved(message: String) = OperationalFeedback(OperationalFeedbackState.SAVED, message)
        fun error(message: String) = OperationalFeedback(OperationalFeedbackState.ERROR, message)
        fun syncPending(message: String) = OperationalFeedback(OperationalFeedbackState.SYNC_PENDING, message)
        fun offline(message: String) = OperationalFeedback(OperationalFeedbackState.OFFLINE, message)
    }
}
