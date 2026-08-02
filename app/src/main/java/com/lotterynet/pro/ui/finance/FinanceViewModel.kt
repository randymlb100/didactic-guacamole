package com.lotterynet.pro.ui.finance

import androidx.lifecycle.ViewModel
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.sync.OperationalUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class FinanceScreenState(
    val dayKey: String = "",
    val summary: FinanceSummary? = null,
    val message: String? = null,
    val isRefreshing: Boolean = false,
    val uiState: OperationalUiState<FinanceSummary> = OperationalUiState.LoadingLocal,
)

internal class FinanceViewModel : ViewModel() {
    private val _state = MutableStateFlow(FinanceScreenState())
    val state: StateFlow<FinanceScreenState> = _state.asStateFlow()

    fun showLocal(dayKey: String, summary: FinanceSummary, message: String? = "Finanzas locales listas.") {
        _state.value = FinanceScreenState(
            dayKey = dayKey,
            summary = summary,
            message = message,
            isRefreshing = false,
            uiState = OperationalUiState.ReadyLocal(summary),
        )
    }

    fun showCatchingUp(
        dayKey: String,
        summary: FinanceSummary?,
        message: String? = "Sincronizando cuadre...",
    ) {
        _state.value = FinanceScreenState(
            dayKey = dayKey,
            summary = summary,
            message = message,
            isRefreshing = true,
            uiState = OperationalUiState.CatchingUp(summary),
        )
    }

    fun showFresh(dayKey: String, summary: FinanceSummary, message: String) {
        _state.value = FinanceScreenState(
            dayKey = dayKey,
            summary = summary,
            message = message,
            isRefreshing = false,
            uiState = OperationalUiState.Fresh(summary, System.currentTimeMillis()),
        )
    }

    fun showRecoverableError(
        dayKey: String? = null,
        summary: FinanceSummary? = null,
        message: String,
    ) {
        _state.update { current ->
            current.copy(
                dayKey = dayKey ?: current.dayKey,
                summary = summary ?: current.summary,
                message = message,
                isRefreshing = false,
                uiState = OperationalUiState.ErrorRecoverable(summary ?: current.summary, message),
            )
        }
    }
}
