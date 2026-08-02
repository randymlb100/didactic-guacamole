package com.lotterynet.pro.core.sync

sealed interface OperationalUiState<out T> {
    data object LoadingLocal : OperationalUiState<Nothing>
    data class ReadyLocal<T>(val data: T) : OperationalUiState<T>
    data class CatchingUp<T>(val data: T?) : OperationalUiState<T>
    data class Fresh<T>(val data: T, val syncedAtEpochMs: Long) : OperationalUiState<T>
    data class ErrorRecoverable<T>(val data: T?, val message: String) : OperationalUiState<T>
}
