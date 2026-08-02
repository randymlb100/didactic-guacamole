package com.lotterynet.pro.ui.admin

import androidx.lifecycle.ViewModel
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.sync.OperationalUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class AdminWinnersScreenState(
    val tickets: List<TicketRecord> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastRemoteUpdatedAt: String? = null,
    val uiState: OperationalUiState<List<TicketRecord>> = OperationalUiState.LoadingLocal,
)

internal class AdminWinnersViewModel : ViewModel() {
    private val _state = MutableStateFlow(AdminWinnersScreenState())
    val state: StateFlow<AdminWinnersScreenState> = _state.asStateFlow()

    fun showLocal(tickets: List<TicketRecord>) {
        _state.value = _state.value.copy(
            tickets = tickets,
            isRefreshing = false,
            uiState = OperationalUiState.ReadyLocal(tickets),
        )
    }

    fun showCatchingUp() {
        _state.update { current ->
            current.copy(
                isRefreshing = true,
                uiState = OperationalUiState.CatchingUp(current.tickets),
            )
        }
    }

    fun showFresh(tickets: List<TicketRecord>, remoteUpdatedAt: String?) {
        _state.value = _state.value.copy(
            tickets = tickets,
            isRefreshing = false,
            lastRemoteUpdatedAt = remoteUpdatedAt ?: _state.value.lastRemoteUpdatedAt,
            uiState = OperationalUiState.Fresh(tickets, System.currentTimeMillis()),
        )
    }

    fun showRecoverableError(message: String) {
        _state.update { current ->
            current.copy(
                isRefreshing = false,
                uiState = OperationalUiState.ErrorRecoverable(current.tickets, message),
            )
        }
    }
}
