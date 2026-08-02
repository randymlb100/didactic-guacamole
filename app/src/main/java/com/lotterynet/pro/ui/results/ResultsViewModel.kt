package com.lotterynet.pro.ui.results

import androidx.lifecycle.ViewModel
import com.lotterynet.pro.core.sync.OperationalUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class ResultsScreenState(
    val selectedDate: String = "",
    val resultCount: Int = 0,
    val syncMessage: String? = null,
    val isRefreshing: Boolean = false,
    val uiState: OperationalUiState<Int> = OperationalUiState.LoadingLocal,
)

internal class ResultsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ResultsScreenState())
    val state: StateFlow<ResultsScreenState> = _state.asStateFlow()

    fun showLocal(selectedDate: String, resultCount: Int, message: String? = null) {
        _state.value = ResultsScreenState(
            selectedDate = selectedDate,
            resultCount = resultCount,
            syncMessage = message ?: if (resultCount > 0) "Resultados locales listos." else null,
            isRefreshing = false,
            uiState = OperationalUiState.ReadyLocal(resultCount),
        )
    }

    fun showCatchingUp(selectedDate: String, resultCount: Int, message: String = "Buscando resultados remotos...") {
        _state.value = ResultsScreenState(
            selectedDate = selectedDate,
            resultCount = resultCount,
            syncMessage = message,
            isRefreshing = true,
            uiState = OperationalUiState.CatchingUp(resultCount),
        )
    }

    fun showFresh(selectedDate: String, resultCount: Int, message: String = "Resultados actualizados.") {
        _state.value = ResultsScreenState(
            selectedDate = selectedDate,
            resultCount = resultCount,
            syncMessage = message,
            isRefreshing = false,
            uiState = OperationalUiState.Fresh(resultCount, System.currentTimeMillis()),
        )
    }

    fun showRecoverableError(selectedDate: String? = null, resultCount: Int? = null, message: String) {
        _state.update { current ->
            current.copy(
                selectedDate = selectedDate ?: current.selectedDate,
                resultCount = resultCount ?: current.resultCount,
                syncMessage = message,
                isRefreshing = false,
                uiState = OperationalUiState.ErrorRecoverable(resultCount ?: current.resultCount, message),
            )
        }
    }
}
