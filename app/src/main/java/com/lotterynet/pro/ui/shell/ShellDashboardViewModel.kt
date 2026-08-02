package com.lotterynet.pro.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ShellDashboardUiState(
    val key: String? = null,
    val snapshot: ShellDashboardSnapshot? = null,
    val isRefreshing: Boolean = false,
)

internal class ShellDashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow(ShellDashboardUiState())
    val state: StateFlow<ShellDashboardUiState> = _state.asStateFlow()

    private var activeKey: String? = null
    private var refreshJob: Job? = null

    fun ensureLoaded(
        key: String,
        loader: suspend () -> ShellDashboardSnapshot,
    ) {
        val current = _state.value
        if (current.key == key && current.snapshot != null) return
        refresh(key, loader)
    }

    fun refresh(
        key: String,
        loader: suspend () -> ShellDashboardSnapshot,
    ) {
        if (refreshJob?.isActive == true && activeKey == key) return
        if (refreshJob?.isActive == true && activeKey != key) {
            refreshJob?.cancel()
        }
        activeKey = key
        val current = _state.value
        _state.value = ShellDashboardUiState(
            key = key,
            snapshot = if (current.key == key) current.snapshot else null,
            isRefreshing = true,
        )
        refreshJob = viewModelScope.launch {
            val result = runCatching { loader() }
            if (activeKey != key) return@launch
            _state.value = ShellDashboardUiState(
                key = key,
                snapshot = result.getOrNull() ?: _state.value.snapshot,
                isRefreshing = false,
            )
        }
    }
}
