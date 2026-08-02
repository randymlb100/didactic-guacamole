package com.lotterynet.pro.ui.master

import com.lotterynet.pro.core.model.UserAccount

internal enum class MasterDestination(
    val id: String,
    val label: String,
) {
    OVERVIEW("overview", "Resumen"),
    BANKS("banks", "Bancas"),
    MODULES("modules", "Módulos"),
    SYSTEM("system", "Sistema"),
    SECURITY("security", "Seguridad"),
}

internal fun masterPrimaryDestinations(): List<MasterDestination> =
    MasterDestination.entries

internal data class MasterModuleEntry(
    val id: String,
    val label: String,
)

internal fun masterModuleEntries(): List<MasterModuleEntry> = listOf(
    MasterModuleEntry(id = "services", label = "Servicios"),
    MasterModuleEntry(id = "video_games", label = "Videojuegos"),
    MasterModuleEntry(id = "sportsbook", label = "Deporte"),
)

internal enum class MasterSyncState {
    IDLE,
    LOADING,
    CONFIRMED,
    PENDING,
    OFFLINE,
    ERROR,
}

internal enum class MasterStatusKind {
    NEUTRAL,
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

internal data class MasterStatusBadge(
    val label: String,
    val kind: MasterStatusKind,
)

internal fun resolveMasterStatusBadge(
    busy: Boolean,
    statusMessage: String?,
): MasterStatusBadge {
    if (busy) return MasterStatusBadge("Actualizando", MasterStatusKind.INFO)
    val normalized = statusMessage.orEmpty().lowercase()
    return when {
        "pendiente" in normalized || "offline" in normalized ->
            MasterStatusBadge("Cambios pendientes", MasterStatusKind.WARNING)

        "fall" in normalized || "error" in normalized || "no se " in normalized ->
            MasterStatusBadge("Revisar operación", MasterStatusKind.ERROR)

        "confirm" in normalized || "actualizado" in normalized || "guardado" in normalized ->
            MasterStatusBadge("Cambio confirmado", MasterStatusKind.SUCCESS)

        else -> MasterStatusBadge("Estado local listo", MasterStatusKind.NEUTRAL)
    }
}

internal data class MasterDashboardUiState(
    val destination: MasterDestination = MasterDestination.OVERVIEW,
    val admins: List<UserAccount> = emptyList(),
    val cashiers: List<UserAccount> = emptyList(),
    val selectedAdminId: String? = null,
    val searchQuery: String = "",
    val bankFilter: MasterBankFilter = MasterBankFilter.ALL,
    val syncState: MasterSyncState = MasterSyncState.IDLE,
    val statusMessage: String? = null,
    val lastConfirmedAtEpochMs: Long? = null,
)

internal enum class MasterBackResult {
    CLEAR_BANK_SELECTION,
    GO_TO_OVERVIEW,
    EXIT,
}

internal enum class MasterBankDetailArea(
    val label: String,
) {
    OVERVIEW("Resumen"),
    CASHIERS("Cajeros"),
    FUNDS("Fondos"),
    SECURITY("Seguridad"),
}

internal fun masterBackDestination(
    current: MasterDestination,
    hasSelectedAdmin: Boolean,
): MasterBackResult {
    return when {
        current == MasterDestination.BANKS && hasSelectedAdmin ->
            MasterBackResult.CLEAR_BANK_SELECTION

        current != MasterDestination.OVERVIEW ->
            MasterBackResult.GO_TO_OVERVIEW

        else -> MasterBackResult.EXIT
    }
}
