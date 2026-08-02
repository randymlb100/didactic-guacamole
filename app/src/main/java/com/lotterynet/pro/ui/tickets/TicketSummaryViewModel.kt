package com.lotterynet.pro.ui.tickets

import androidx.lifecycle.ViewModel
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.sync.OperationalUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class TicketSummaryScreenState(
    val tickets: List<TicketRecord> = emptyList(),
    val cashiers: List<UserAccount> = emptyList(),
    val syncMessage: String = "Tickets locales listos.",
    val isRefreshing: Boolean = false,
    val pendingSyncCount: Int = 0,
    val lastRemoteUpdatedAt: String? = null,
    val uiState: OperationalUiState<List<TicketRecord>> = OperationalUiState.LoadingLocal,
)

internal class TicketSummaryViewModel : ViewModel() {
    private val _state = MutableStateFlow(TicketSummaryScreenState())
    val state: StateFlow<TicketSummaryScreenState> = _state.asStateFlow()

    fun showLocal(
        tickets: List<TicketRecord>,
        cashiers: List<UserAccount>,
        pendingSyncCount: Int,
        message: String = "Tickets locales listos.",
    ) {
        _state.value = _state.value.copy(
            tickets = tickets,
            cashiers = cashiers,
            pendingSyncCount = pendingSyncCount,
            syncMessage = message,
            isRefreshing = false,
            uiState = OperationalUiState.ReadyLocal(tickets),
        )
    }

    fun showCatchingUp(message: String = "Refrescando servidor...") {
        _state.update { current ->
            current.copy(
                syncMessage = message,
                isRefreshing = true,
                uiState = OperationalUiState.CatchingUp(current.tickets),
            )
        }
    }

    fun showFresh(
        tickets: List<TicketRecord>,
        cashiers: List<UserAccount>,
        pendingSyncCount: Int,
        remoteUpdatedAt: String?,
        message: String,
    ) {
        _state.value = _state.value.copy(
            tickets = tickets,
            cashiers = cashiers,
            pendingSyncCount = pendingSyncCount,
            lastRemoteUpdatedAt = remoteUpdatedAt ?: _state.value.lastRemoteUpdatedAt,
            syncMessage = message,
            isRefreshing = false,
            uiState = OperationalUiState.Fresh(tickets, System.currentTimeMillis()),
        )
    }

    fun showRecoverableError(message: String) {
        _state.update { current ->
            current.copy(
                syncMessage = message,
                isRefreshing = false,
                uiState = OperationalUiState.ErrorRecoverable(current.tickets, message),
            )
        }
    }
}
