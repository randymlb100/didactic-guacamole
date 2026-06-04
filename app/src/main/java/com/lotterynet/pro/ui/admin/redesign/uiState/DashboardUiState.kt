package com.lotterynet.pro.ui.admin.redesign.uiState

import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole

data class MasterDashboardState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val totalAdmins: Int = 0,
    val activeBancas: Int = 0,
    val globalSalesToday: Double = 0.0,
    val activeAdminsList: List<UserAccount> = emptyList(),
    val bancasSummary: List<BancaDetailState> = emptyList()
)

data class AdminDashboardState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val activeCashiers: Int = 0,
    val salesToday: Double = 0.0,
    val gainsToday: Double = 0.0,
    val pendingPrizes: Double = 0.0,
    val cashiersList: List<UserAccount> = emptyList(),
    val recentActivity: List<ActivityLogState> = emptyList()
)

data class BancaDetailState(
    val id: String,
    val name: String,
    val assignedAdmin: String,
    val salesToday: Double,
    val activeCajeros: Int,
    val active: Boolean = true
)

data class ActivityLogState(
    val id: String,
    val timestampMs: Long,
    val type: String,
    val cashierName: String,
    val detail: String,
    val isAlert: Boolean = false
)

object MockDataFactory {
    fun createPreviewMasterState(): MasterDashboardState {
        return MasterDashboardState(
            isLoading = false,
            totalAdmins = 4,
            activeBancas = 8,
            globalSalesToday = 245380.0,
            activeAdminsList = listOf(
                UserAccount(
                    id = "adm-1",
                    user = "admin_antillas",
                    role = UserRole.ADMIN,
                    displayName = "Admin Antillas",
                    active = true,
                    balance = 125000.0
                ),
                UserAccount(
                    id = "adm-2",
                    user = "admin_oriental",
                    role = UserRole.ADMIN,
                    displayName = "Admin Oriental",
                    active = false,
                    balance = 45000.0
                ),
                UserAccount(
                    id = "adm-3",
                    user = "admin_capital",
                    role = UserRole.ADMIN,
                    displayName = "Admin Capital (Santo Domingo)",
                    active = true,
                    balance = 75380.0
                )
            ),
            bancasSummary = listOf(
                BancaDetailState("b-1", "Banca Central SDE", "Admin Antillas", 85400.0, 5, true),
                BancaDetailState("b-2", "Banca Mega Oeste", "Admin Oriental", 42300.0, 3, false),
                BancaDetailState("b-3", "Banca Sabana Perdida", "Admin Antillas", 32100.0, 2, true),
                BancaDetailState("b-4", "Banca Naco Premium", "Admin Capital", 85580.0, 4, true)
            )
        )
    }

    fun createPreviewAdminState(): AdminDashboardState {
        return AdminDashboardState(
            isLoading = false,
            activeCashiers = 5,
            salesToday = 85400.0,
            gainsToday = 53100.0,
            pendingPrizes = 12500.0,
            cashiersList = listOf(
                UserAccount(
                    id = "c-1",
                    user = "cajero_pedro",
                    role = UserRole.CASHIER,
                    displayName = "Pedro Martínez",
                    active = true,
                    banca = "Banca Central SDE",
                    balance = 12450.0,
                    commissionRate = 10.0
                ),
                UserAccount(
                    id = "c-2",
                    user = "cajero_lucia",
                    role = UserRole.CASHIER,
                    displayName = "Lucía Santos",
                    active = true,
                    banca = "Banca Central SDE",
                    balance = -3420.0,
                    commissionRate = 8.5
                ),
                UserAccount(
                    id = "c-3",
                    user = "cajero_ramon",
                    role = UserRole.CASHIER,
                    displayName = "Ramón Vargas",
                    active = false,
                    banca = "Banca Central SDE",
                    balance = 0.0,
                    commissionRate = 10.0
                )
            ),
            recentActivity = listOf(
                ActivityLogState("act-1", System.currentTimeMillis() - 600000, "Venta", "Pedro Martínez", "Venta ticket #89423 por $250.00"),
                ActivityLogState("act-2", System.currentTimeMillis() - 1800000, "Alerta", "Lucía Santos", "Límite de venta diario alcanzado (95%)", isAlert = true),
                ActivityLogState("act-3", System.currentTimeMillis() - 3600000, "Anulación", "Pedro Martínez", "Anuló ticket #89412 por $500.00")
            )
        )
    }
}
