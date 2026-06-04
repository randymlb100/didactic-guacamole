package com.lotterynet.pro.ui.users

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.finance.FinanceActorPeriodRow
import com.lotterynet.pro.core.finance.FinancePeriodPreset
import com.lotterynet.pro.core.finance.FinancePeriodReport
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.normalizedPrizeTableConfig
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.CashierSalesLimitInputs
import com.lotterynet.pro.core.storage.LocalAdminLimitRepository
import com.lotterynet.pro.core.storage.LocalCashierPrizePayoutRepository
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.cashierSystemModeOverrideLabel
import com.lotterynet.pro.core.storage.normalizeCashierSystemModeOverride
import com.lotterynet.pro.core.sync.CashierPrizePayoutCloudSyncCoordinator
import com.lotterynet.pro.core.operations.sortCashierAccountsNatural
import com.lotterynet.pro.core.sync.CashierLimitCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeUsersBootstrapper
import com.lotterynet.pro.core.users.UserPasswordBackendClient
import com.lotterynet.pro.core.users.SupabaseUsersRemoteStore
import com.lotterynet.pro.ui.common.*
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.navigation.startSafeNativeDestination
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import kotlin.math.abs
import com.lotterynet.pro.core.auth.CredentialFactory
import com.lotterynet.pro.core.auth.SupabaseAuthBridgeClient
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider

class UserAccountsActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_INITIAL_ADMIN_SECTION = "extra_initial_admin_section"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.USER_ACCOUNTS)) return
        val session = activeSession ?: return
        val usersRepository = LocalUsersRepository(this)
        usersRepository.touchSession(session)
        val initialAccounts = buildAccountsForSession(session, usersRepository)
        val financeRepository = LocalFinanceRepository(
            salesRepository = LocalSalesRepository(this),
            rechargeRepository = LocalRechargeRepository(this),
            usersRepository = usersRepository,
        )
        val adminLimitRepository = LocalAdminLimitRepository(this)
        val cashierSalesLimitRepository = LocalCashierSalesLimitRepository(this)
        val cashierPrizePayoutRepository = LocalCashierPrizePayoutRepository(this)
        val cashierLimitCloudSync = CashierLimitCloudSyncCoordinator(cashierSalesLimitRepository)
        val cashierPrizePayoutCloudSync = CashierPrizePayoutCloudSyncCoordinator(cashierPrizePayoutRepository)
        val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        val usersRemoteStore = SupabaseUsersRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
        )
        val userPasswordBackendClient = UserPasswordBackendClient()
        val authBridgeClient = SupabaseAuthBridgeClient()
        val ownerId = session.userId
        val financeScope = financeRepository.resolveScope(session)
        val latestFinanceDay = LocalDate.now().toString()
        val initialCashierReport = null
        val initialAdminSectionName = intent?.getStringExtra(EXTRA_INITIAL_ADMIN_SECTION)

        setContent {
            LotteryNetComposeTheme {
                UserAccountsRoute(
                    session = session,
                    initialAccounts = initialAccounts,
                    initialCashierReport = initialCashierReport,
                    initialAdminSectionName = initialAdminSectionName,
                    onBack = { finish() },
                    onLoadCashierReport = { preset ->
                        financeRepository.getScopedPeriodReport(
                            scope = financeScope,
                            preset = preset,
                            anchorDayKey = latestFinanceDay,
                        )
                    },
                    onSaveAccount = { account, displayName, balance, commission, recargaTx, active, systemModeOverride ->
                        val renamed = applyEditableCashierDisplayName(account, displayName)
                        val normalized = renamed.copy(
                            balance = balance,
                            commissionRate = commission,
                            recargaTxLimit = recargaTx,
                            active = active,
                            systemModeOverride = normalizeCashierSystemModeOverride(systemModeOverride),
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                        val payload = usersRepository.buildPayloadWithAccount(normalized)
                        usersRepository.cacheRawPayload(payload)
                        thread(name = "user-account-save-sync") {
                            val saved = runCatching {
                                usersRemoteStore.upsertUsersPayload(payload)
                            }.isSuccess
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (saved) {
                                        "${normalized.user} actualizado en servidor"
                                    } else {
                                        "Guardado local. Servidor no disponible"
                                    },
                                    if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                        buildAccountsForSession(session, usersRepository)
                    },
                    onSaveAccountsBatch = { updatedAccounts ->
                        val admins = updatedAccounts.filter { it.role == UserRole.ADMIN }
                        val supervisors = updatedAccounts.filter { it.role == UserRole.SUPERVISOR }
                        val cashiers = updatedAccounts.filter { it.role == UserRole.CASHIER }
                        usersRepository.saveUsers(admins, supervisors, cashiers)
                        val payload = usersRepository.exportPayloadJson()
                        thread(name = "user-accounts-batch-save-sync") {
                            val saved = runCatching {
                                usersRemoteStore.upsertUsersPayload(payload)
                            }.isSuccess
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (saved) {
                                        "Cambios de cajeros guardados en servidor"
                                    } else {
                                        "Guardado local. Servidor no disponible"
                                    },
                                    if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                        buildAccountsForSession(session, usersRepository)
                    },
                    onCreateSupervisor = { rawUser, rawName, rawPassword, assignedCashierIds, active, groupCommissionRate ->
                        val previousPayload = usersRepository.exportPayloadJson()
                        val admin = usersRepository.getAdmins().firstOrNull {
                            it.id.equals(session.userId, ignoreCase = true) || it.user.equals(session.username, ignoreCase = true)
                        } ?: UserAccount(
                            id = session.userId,
                            user = session.username,
                            role = UserRole.ADMIN,
                            banca = session.banca,
                            territory = session.territory,
                        )
                        val result = buildSupervisorCreateResult(
                            admin = admin,
                            existingAccounts = usersRepository.getAdmins() + usersRepository.getSupervisors() + usersRepository.getCashiers(),
                            cashiers = usersRepository.getCashiers(),
                            rawUser = rawUser,
                            rawName = rawName,
                            rawPassword = rawPassword,
                            assignedCashierIds = assignedCashierIds,
                            active = active,
                            groupCommissionRate = groupCommissionRate,
                        )
                        usersRepository.saveUsers(
                            usersRepository.getAdmins(),
                            usersRepository.getSupervisors() + result.supervisor,
                            mergeCashierUpdates(usersRepository.getCashiers(), result.cashiers),
                        )
                        val payload = usersRepository.exportPayloadJson()
                        runCatching {
                            runBlocking {
                                withContext(Dispatchers.IO) {
                                    usersRemoteStore.upsertUsersPayload(payload)
                                    val jwtSession = authBridgeClient.legacyLogin(result.supervisor, result.password)
                                    if (jwtSession.accessToken.isNullOrBlank()) {
                                        throw IllegalStateException("Servidor no genero JWT para ${result.supervisor.user}.")
                                    }
                                }
                            }
                        }.onFailure { error ->
                            usersRepository.cacheRawPayload(previousPayload)
                            throw IllegalStateException(
                                "No se creó el supervisor: el servidor no confirmó guardado. ${error.message ?: "Servidor no disponible"}",
                                error,
                            )
                        }.onSuccess {
                            Toast.makeText(this, "Supervisor guardado en servidor", Toast.LENGTH_SHORT).show()
                        }
                        result
                    },
                    onResetSupervisorPassword = { supervisor, rawPassword ->
                        val result = resetSupervisorPassword(supervisor, rawPassword)
                        runBlocking {
                            withContext(Dispatchers.IO) {
                                userPasswordBackendClient.changePassword(
                                    session = session,
                                    target = supervisor,
                                    newPassword = result.password,
                                )
                            }
                        }
                        usersRepository.saveUsers(
                            usersRepository.getAdmins(),
                            usersRepository.getSupervisors().map {
                                if (it.id.equals(supervisor.id, ignoreCase = true)) result.supervisor else it
                            },
                            usersRepository.getCashiers(),
                        )
                        Toast.makeText(this, "Clave de supervisor guardada en servidor", Toast.LENGTH_SHORT).show()
                        result
                    },
                    onToggleSupervisorActive = { supervisor ->
                        val updatedSupervisor = toggleSupervisorActive(supervisor)
                        usersRepository.saveUsers(
                            usersRepository.getAdmins(),
                            usersRepository.getSupervisors().map {
                                if (it.id.equals(supervisor.id, ignoreCase = true)) updatedSupervisor else it
                            },
                            usersRepository.getCashiers(),
                        )
                        val payload = usersRepository.exportPayloadJson()
                        thread(name = "supervisor-status-sync") {
                            val saved = runCatching { usersRemoteStore.upsertUsersPayload(payload) }.isSuccess
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (saved) {
                                        if (updatedSupervisor.active) "Supervisor desbloqueado en servidor" else "Supervisor bloqueado en servidor"
                                    } else {
                                        if (updatedSupervisor.active) "Supervisor desbloqueado local. Servidor no disponible" else "Supervisor bloqueado local. Servidor no disponible"
                                    },
                                    if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                        buildAccountsForSession(session, usersRepository)
                    },
                    onDeleteSupervisor = { supervisor ->
                        val result = deleteSupervisorAndClearAssignments(
                            supervisors = usersRepository.getSupervisors(),
                            cashiers = usersRepository.getCashiers(),
                            supervisor = supervisor,
                        )
                        usersRepository.saveUsers(
                            usersRepository.getAdmins(),
                            result.supervisors,
                            result.cashiers,
                        )
                        val payload = usersRepository.exportPayloadJson()
                        thread(name = "supervisor-delete-sync") {
                            val saved = runCatching { usersRemoteStore.upsertUsersPayload(payload) }.isSuccess
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (saved) "Supervisor eliminado en servidor" else "Supervisor eliminado local. Servidor no disponible",
                                    if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                        buildAccountsForSession(session, usersRepository)
                    },
                    onSaveSupervisorAssignments = { supervisor, assignedCashierIds, groupCommissionRate ->
                        val normalizedSupervisor = supervisor.copy(
                            commissionRate = groupCommissionRate,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        )
                        val updatedCashiers = applySupervisorAssignments(
                            cashiers = usersRepository.getCashiers(),
                            supervisor = normalizedSupervisor,
                            assignedCashierIds = assignedCashierIds,
                            groupCommissionRate = groupCommissionRate,
                        )
                        usersRepository.saveUsers(
                            usersRepository.getAdmins(),
                            usersRepository.getSupervisors().map {
                                if (it.id.equals(normalizedSupervisor.id, ignoreCase = true)) normalizedSupervisor else it
                            },
                            mergeCashierUpdates(usersRepository.getCashiers(), updatedCashiers),
                        )
                        val payload = usersRepository.exportPayloadJson()
                        thread(name = "supervisor-assignment-sync") {
                            val saved = runCatching { usersRemoteStore.upsertUsersPayload(payload) }.isSuccess
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (saved) "Asignación guardada en servidor" else "Asignación local. Servidor no disponible",
                                    if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                        buildAccountsForSession(session, usersRepository)
                    },
                    onLoadCashierLimits = { account ->
                        if (accountUsesAdminSelfSalesLimits(account)) {
                            cashierSalesLimitRepository.getAdminSelfLimits(ownerId) ?: noLimitSalesInputs()
                        } else {
                            cashierSalesLimitRepository.getUserLimits(ownerId, account.user).let { limits ->
                                if (limits.payout > 0.0) limits else limits.copy(payout = adminLimitRepository.getLimits().cashierPayoutLimit)
                            }
                        }
                    },
                    onLoadDefaultCashierLimits = {
                        cashierSalesLimitRepository.getDefaultLimits(ownerId).let { limits ->
                            if (limits.payout > 0.0) limits else limits.copy(payout = adminLimitRepository.getLimits().cashierPayoutLimit)
                        }
                    },
                    onSaveCashierLimits = { account, limits ->
                        Thread {
                            val ok = if (accountUsesAdminSelfSalesLimits(account)) {
                                cashierLimitCloudSync.pushAdminSelfLimitsServiceFirst(ownerId, limits)
                            } else {
                                cashierLimitCloudSync.pushUserLimitsServiceFirst(ownerId, account.user, limits)
                            }
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (ok) "Límites guardados en servidor" else "No se guardó: servidor no disponible",
                                    if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }.start()
                    },
                    onSaveDefaultCashierLimits = { limits ->
                        val cashierUsernames = usersRepository.getCashiers()
                            .filter { it.adminId.equals(ownerId, ignoreCase = true) || it.adminUser.equals(session.username, ignoreCase = true) }
                            .map { it.user }
                        Thread {
                            val ok = cashierLimitCloudSync.pushDefaultLimitsForUsersServiceFirst(
                                ownerId = ownerId,
                                limits = limits,
                                usernames = cashierUsernames,
                            )
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (ok) "Límites globales guardados en servidor" else "No se guardó: servidor no disponible",
                                    if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }.start()
                    },
                    onLoadCashierPrizePayout = { account ->
                        if (account == null) {
                            cashierPrizePayoutRepository.getDefaultPayout(ownerId)
                        } else {
                            cashierPrizePayoutRepository.getUserPayout(ownerId, account.user)
                        }
                    },
                    onSaveCashierPrizePayout = { account, config ->
                        Thread {
                            val ok = if (account == null) {
                                cashierPrizePayoutCloudSync.pushDefaultPayoutServiceFirst(ownerId, config)
                            } else {
                                cashierPrizePayoutCloudSync.pushUserPayoutServiceFirst(ownerId, account.user, config)
                            }
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (ok) "Premios guardados en Supabase" else "No se guardó: servidor no disponible",
                                    if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }.start()
                    },
                    onRefreshServer = { onDone ->
                        Thread {
                            val usersResult = NativeUsersBootstrapper(usersRepository).bootstrap(forceRemoteRefresh = true)
                            val limitsOk = cashierLimitCloudSync.pullOwner(ownerId)
                            val prizeOk = cashierPrizePayoutCloudSync.pullOwner(ownerId)
                            val nextAccounts = buildAccountsForSession(session, usersRepository)
                            runOnUiThread {
                                onDone(usersResult.ok || limitsOk || prizeOk, nextAccounts)
                            }
                        }.start()
                    },
                )
            }
        }
    }
}

internal data class UserAccountsLayoutContract(
    val tabsColumns: Int,
    val cardSpacingDp: Int,
    val compactReadOnlyHint: Boolean,
    val useCompactRows: Boolean,
    val showLargeCards: Boolean,
    val cardPaddingVerticalDp: Int,
    val filterPaddingVerticalDp: Int,
)

internal enum class UserAccountsStartupWork {
    LOAD_SESSION,
    LOAD_LOCAL_ACCOUNTS,
    LOAD_LIMITS_FOR_SELECTION,
    LOAD_CASHIER_REPORT,
    HYDRATE_REMOTE_USERS,
    HYDRATE_REMOTE_LIMITS,
}

internal data class UserAccountsStartupPlan(
    val firstFrameWork: Set<UserAccountsStartupWork>,
    val afterFirstFrameWork: Set<UserAccountsStartupWork>,
)

internal fun resolveUserAccountsStartupPlan(): UserAccountsStartupPlan {
    return UserAccountsStartupPlan(
        firstFrameWork = setOf(
            UserAccountsStartupWork.LOAD_SESSION,
            UserAccountsStartupWork.LOAD_LOCAL_ACCOUNTS,
        ),
        afterFirstFrameWork = setOf(
            UserAccountsStartupWork.LOAD_LIMITS_FOR_SELECTION,
            UserAccountsStartupWork.LOAD_CASHIER_REPORT,
            UserAccountsStartupWork.HYDRATE_REMOTE_USERS,
            UserAccountsStartupWork.HYDRATE_REMOTE_LIMITS,
        ),
    )
}

internal fun resolveUserAccountsLayout(windowMode: LotteryNetWindowMode): UserAccountsLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> UserAccountsLayoutContract(
            tabsColumns = 2,
            cardSpacingDp = 6,
            compactReadOnlyHint = true,
            useCompactRows = true,
            showLargeCards = false,
            cardPaddingVerticalDp = 7,
            filterPaddingVerticalDp = 7,
        )

        LotteryNetWindowMode.POS -> UserAccountsLayoutContract(
            tabsColumns = 2,
            cardSpacingDp = 7,
            compactReadOnlyHint = true,
            useCompactRows = true,
            showLargeCards = false,
            cardPaddingVerticalDp = 8,
            filterPaddingVerticalDp = 8,
        )

        else -> UserAccountsLayoutContract(
            tabsColumns = 3,
            cardSpacingDp = 8,
            compactReadOnlyHint = false,
            useCompactRows = false,
            showLargeCards = true,
            cardPaddingVerticalDp = 9,
            filterPaddingVerticalDp = 9,
        )
    }
}

internal enum class UserAccountFilter {
    ALL,
    ACTIVE,
    BLOCKED,
    WITH_ACTIVITY,
    NO_MOVEMENT,
    CASHIERS,
    SUPERVISORS,
    ADMINS,
}

internal enum class CashierAdminWindowSegment(val label: String) {
    CASHIERS("Cajeros"),
    MONITOR("Monitoreo"),
    TICKETS("Tickets"),
    REPORT("Reporte"),
}

internal enum class CashierInsightFilter(val label: String) {
    ALL("Todos"),
    ACTIVE("Activos"),
    BLOCKED("Bloqueados"),
    SOLD("Venta"),
    LOSS("Pérdida"),
    BENEFIT("Beneficio"),
}

internal data class UserAccountFilterOption(
    val filter: UserAccountFilter,
    val label: String,
)

internal fun userAccountFilterOptions(): List<UserAccountFilterOption> = listOf(
    UserAccountFilterOption(UserAccountFilter.ALL, "Todos"),
    UserAccountFilterOption(UserAccountFilter.ACTIVE, "Activos"),
    UserAccountFilterOption(UserAccountFilter.BLOCKED, "Bloqueados"),
    UserAccountFilterOption(UserAccountFilter.CASHIERS, "Cajeros"),
    UserAccountFilterOption(UserAccountFilter.SUPERVISORS, "Supervisores"),
    UserAccountFilterOption(UserAccountFilter.ADMINS, "Admin"),
)

internal fun cashierAdminWindowSegmentOptions(role: UserRole): List<QuickFilterChip> {
    return emptyList()
}

internal fun cashierInsightFilterOptions(role: UserRole): List<QuickFilterChip> {
    return when (role) {
        UserRole.ADMIN,
        UserRole.SUPERVISOR -> CashierInsightFilter.entries.map { QuickFilterChip(it.name, it.label) }
        else -> emptyList()
    }
}

internal fun cashierAdminQuickActionLabels(role: UserRole): List<String> {
    return emptyList()
}

internal data class CashierPerformancePeriodOption(
    val preset: FinancePeriodPreset,
    val label: String,
)

internal fun cashierPerformancePeriodOptions(): List<CashierPerformancePeriodOption> = listOf(
    CashierPerformancePeriodOption(FinancePeriodPreset.DAY, "Día"),
    CashierPerformancePeriodOption(FinancePeriodPreset.WEEK, "Semana"),
    CashierPerformancePeriodOption(FinancePeriodPreset.QUINCENA, "Quincena"),
    CashierPerformancePeriodOption(FinancePeriodPreset.MONTH, "Mes"),
)

internal fun cashierPerformancePeriodSelectorVisible(): Boolean = false

internal fun cashierPrizePayoutFieldLabels(): List<String> = listOf(
    "1ra",
    "2da",
    "3ra",
    "1-2",
    "1-3",
    "2-3",
    "3 números",
    "2 números",
    "Super Pale",
    "P3 directo",
    "P3 box",
    "P4 directo",
    "P4 box",
)

internal data class UserAccountsSummary(
    val total: Int,
    val active: Int,
    val blocked: Int,
    val cashiers: Int,
    val supervisors: Int = 0,
)

internal fun buildUserAccountsSummary(accounts: List<UserAccount>): UserAccountsSummary {
    return UserAccountsSummary(
        total = accounts.size,
        active = accounts.count { it.active },
        blocked = accounts.count { !it.active },
        cashiers = accounts.count { it.role == UserRole.CASHIER },
        supervisors = accounts.count { it.role == UserRole.SUPERVISOR },
    )
}

internal data class CashierSelectorOption(
    val id: String,
    val label: String,
)

internal const val ALL_CASHIER_LIMITS_ID = "__all_cashiers__"

internal fun cashierSelectorOptions(accounts: List<UserAccount>): List<CashierSelectorOption> {
    return listOf(CashierSelectorOption(ALL_CASHIER_LIMITS_ID, "Valores globales")) + sortAdminCashierSelectionAccounts(accounts)
        .map { account -> CashierSelectorOption(account.id, account.displayName ?: account.user) }
}

internal fun applyEditableCashierDisplayName(account: UserAccount, rawDisplayName: String): UserAccount {
    if (account.role != UserRole.CASHIER) return account
    return account.copy(displayName = rawDisplayName.trim().takeIf { it.isNotBlank() })
}

internal fun resolveSelectedCashierAccount(accounts: List<UserAccount>, selectedCashierId: String?): UserAccount? {
    if (selectedCashierId == ALL_CASHIER_LIMITS_ID) return null
    val selectable = sortAdminCashierSelectionAccounts(accounts)
    return selectable.firstOrNull { it.id == selectedCashierId } ?: selectable.firstOrNull()
}

internal fun resolveAdminVisibleCashierDetailAccounts(
    cashierAccounts: List<UserAccount>,
    selectedCashierId: String?,
): List<UserAccount> {
    if (selectedCashierId == ALL_CASHIER_LIMITS_ID) return emptyList()
    return listOfNotNull(resolveSelectedCashierAccount(cashierAccounts, selectedCashierId))
}

private fun sortAdminCashierSelectionAccounts(accounts: List<UserAccount>): List<UserAccount> {
    return sortUserAccountsForAdmin(
        accounts.filter { it.role == UserRole.ADMIN || it.role == UserRole.MASTER || it.role == UserRole.CASHIER },
    )
}

internal fun cashierAdminServerActionLabel(): String = "Actualizar servidor"

internal fun cashierAdminSaveServerActionLabel(): String = "Guardar servidor"

internal fun cashierAccountSaveServerActionLabel(): String = "Guardar servidor"

internal fun cashierPayoutLimitLabel(selectedAllCashiers: Boolean): String {
    return if (selectedAllCashiers) "Tope pago premios todos" else "Tope pago premios cajero"
}

internal fun cashierPrizeSectionLabel(): String = "Pago premios"

internal fun cashierPrizeSectionPurpose(): String = "Define cuanto paga cada peso ganador."

internal fun cashierAdminServerStatusLabel(success: Boolean): String {
    return if (success) "Actualizado desde servidor" else "No se pudo actualizar"
}

internal fun cashierAdminFieldLabels(): List<String> = listOf(
    "Estado",
    "Límite diario de venta",
    "Tope pago premios",
    "Quiniela venta diaria",
    "Pale venta diaria",
    "Super Pale venta diaria",
    "Tripleta venta diaria",
    "Pick 3 Straight venta",
    "Pick 3 Box venta",
    "Pick 4 Straight venta",
    "Pick 4 Box venta",
)

internal fun filterUserAccountsForAdmin(
    accounts: List<UserAccount>,
    filter: UserAccountFilter,
    query: String,
): List<UserAccount> {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    return accounts
        .asSequence()
        .filter { account ->
            when (filter) {
                UserAccountFilter.ALL -> true
                UserAccountFilter.ACTIVE -> account.active
                UserAccountFilter.BLOCKED -> !account.active
                UserAccountFilter.WITH_ACTIVITY -> account.lastSeenAtEpochMs != null
                UserAccountFilter.NO_MOVEMENT -> account.lastSeenAtEpochMs == null
                UserAccountFilter.CASHIERS -> account.role == UserRole.CASHIER
                UserAccountFilter.SUPERVISORS -> account.role == UserRole.SUPERVISOR
                UserAccountFilter.ADMINS -> account.role == UserRole.ADMIN || account.role == UserRole.MASTER
            }
        }
        .filter { account ->
            if (normalizedQuery.isBlank()) {
                true
            } else {
                listOfNotNull(account.displayName, account.user, account.phone, account.banca)
                    .any { it.lowercase(Locale.US).contains(normalizedQuery) }
            }
        }
        .toList()
        .let(::sortUserAccountsForAdmin)
}

internal fun accountUsesAdminSelfSalesLimits(account: UserAccount): Boolean {
    return account.role == UserRole.ADMIN || account.role == UserRole.MASTER
}

private fun sortUserAccountsForAdmin(accounts: List<UserAccount>): List<UserAccount> {
    val adminAccounts = accounts
        .filter { it.role == UserRole.ADMIN || it.role == UserRole.MASTER }
        .sortedBy { it.displayName?.lowercase(Locale.US) ?: it.user.lowercase(Locale.US) }
    val supervisorAccounts = accounts
        .filter { it.role == UserRole.SUPERVISOR }
        .sortedBy { it.displayName?.lowercase(Locale.US) ?: it.user.lowercase(Locale.US) }
    val cashierAccounts = sortCashierAccountsNatural(accounts)
    val otherAccounts = accounts
        .filterNot { it.role == UserRole.ADMIN || it.role == UserRole.MASTER || it.role == UserRole.SUPERVISOR || it.role == UserRole.CASHIER }
        .sortedBy { it.displayName?.lowercase(Locale.US) ?: it.user.lowercase(Locale.US) }
    return adminAccounts + supervisorAccounts + cashierAccounts + otherAccounts
}

internal data class SupervisorCreateResult(
    val supervisor: UserAccount,
    val cashiers: List<UserAccount>,
    val password: String,
)

internal data class SupervisorPasswordResetResult(
    val supervisor: UserAccount,
    val password: String,
)

internal data class SupervisorDeleteResult(
    val supervisors: List<UserAccount>,
    val cashiers: List<UserAccount>,
)

internal fun supervisorSectionActionLabels(): List<String> = listOf(
    "Crear supervisor",
    "Asignar cajeros",
    "Comision supervisor",
    "Guardar clave",
    "Compartir credencial",
    "Bloquear supervisor",
    "Desbloquear supervisor",
    "Eliminar supervisor",
    "Guardar grupo",
)

internal fun buildSupervisorCreateResult(
    admin: UserAccount,
    existingAccounts: List<UserAccount>,
    cashiers: List<UserAccount>,
    rawUser: String,
    rawName: String,
    rawPassword: String,
    assignedCashierIds: Set<String>,
    active: Boolean,
    groupCommissionRate: Double? = null,
): SupervisorCreateResult {
    val username = rawUser.trim().lowercase(Locale.US)
    require(username.length >= 3) { "El usuario debe tener 3 caracteres o más." }
    require(existingAccounts.none { it.user.equals(username, ignoreCase = true) }) { "Ese usuario ya existe." }
    val password = rawPassword.trim().takeIf { it.isNotBlank() } ?: CredentialFactory.generatePassword()
    require(password.length >= 6) { "La clave debe tener 6 caracteres o más." }
    val secret = CredentialFactory.buildSecretFields(password)
    val supervisorId = nextSupervisorId(existingAccounts)
    val supervisor = UserAccount(
        id = supervisorId,
        user = username,
        role = UserRole.SUPERVISOR,
        displayName = rawName.trim().takeIf { it.isNotBlank() } ?: username,
        active = active,
        adminId = admin.id,
        adminUser = admin.user,
        banca = admin.banca,
        territory = admin.territory,
        commissionRate = groupCommissionRate,
        passwordSalt = secret.passwordSalt,
        passwordHash = secret.passwordHash,
        passwordVersion = secret.passwordVersion,
        credChangedAtEpochMs = secret.credChangedAtEpochMs,
        updatedAtEpochMs = System.currentTimeMillis(),
    )
    return SupervisorCreateResult(
        supervisor = supervisor,
        cashiers = applySupervisorAssignments(cashiers, supervisor, assignedCashierIds, groupCommissionRate),
        password = password,
    )
}

internal fun resetSupervisorPassword(supervisor: UserAccount, rawPassword: String): SupervisorPasswordResetResult {
    val password = rawPassword.trim().takeIf { it.isNotBlank() } ?: CredentialFactory.generatePassword()
    require(password.length >= 6) { "La clave debe tener 6 caracteres o más." }
    val secret = CredentialFactory.buildSecretFields(password)
    return SupervisorPasswordResetResult(
        supervisor = supervisor.copy(
            passwordSalt = secret.passwordSalt,
            passwordHash = secret.passwordHash,
            passwordVersion = secret.passwordVersion,
            credChangedAtEpochMs = secret.credChangedAtEpochMs,
            updatedAtEpochMs = System.currentTimeMillis(),
        ),
        password = password,
    )
}

internal fun toggleSupervisorActive(
    supervisor: UserAccount,
    nowEpochMs: Long = System.currentTimeMillis(),
): UserAccount {
    require(supervisor.role == UserRole.SUPERVISOR) { "Solo se puede cambiar estado de supervisores." }
    return supervisor.copy(
        active = !supervisor.active,
        updatedAtEpochMs = nowEpochMs,
    )
}

internal fun deleteSupervisorAndClearAssignments(
    supervisors: List<UserAccount>,
    cashiers: List<UserAccount>,
    supervisor: UserAccount,
): SupervisorDeleteResult {
    return SupervisorDeleteResult(
        supervisors = supervisors.filterNot { it.id.equals(supervisor.id, ignoreCase = true) },
        cashiers = cashiers.map { cashier ->
            cashier.copy(
                supervisorIds = cashier.supervisorIds.filterNot { it.equals(supervisor.id, ignoreCase = true) },
                supervisorUsers = cashier.supervisorUsers.filterNot { it.equals(supervisor.user, ignoreCase = true) },
            )
        },
    )
}

internal fun applySupervisorAssignments(
    cashiers: List<UserAccount>,
    supervisor: UserAccount,
    assignedCashierIds: Set<String>,
    groupCommissionRate: Double? = null,
): List<UserAccount> {
    return cashiers.map { cashier ->
        if (!cashierBelongsToSupervisorAdmin(cashier, supervisor)) {
            return@map cashier
        }
        val selected = cashier.id in assignedCashierIds
        val nextIds = if (selected) {
            listOf(supervisor.id)
        } else {
            cashier.supervisorIds.filterNot { it.equals(supervisor.id, ignoreCase = true) }
        }
        val nextUsers = if (selected) {
            listOf(supervisor.user)
        } else {
            cashier.supervisorUsers.filterNot { it.equals(supervisor.user, ignoreCase = true) }
        }
        cashier.copy(
            supervisorIds = nextIds,
            supervisorUsers = nextUsers,
            commissionRate = cashier.commissionRate,
        )
    }
}

internal fun supervisorAssignableCashiers(
    cashiers: List<UserAccount>,
    selectedSupervisor: UserAccount?,
): List<UserAccount> {
    val supervisor = selectedSupervisor ?: return emptyList()
    return cashiers.filter { cashier ->
        cashierBelongsToSupervisorAdmin(cashier, supervisor) &&
            (cashierAssignedToSupervisor(cashier, supervisor) || !cashierAssignedToAnySupervisor(cashier))
    }
}

internal fun supervisorAvailableCashiersForCreate(
    cashiers: List<UserAccount>,
    admin: UserAccount,
): List<UserAccount> {
    return sortCashierAccountsNatural(
        cashiers.filter { cashier ->
            cashierBelongsToAdmin(cashier, admin) && !cashierAssignedToAnySupervisor(cashier)
        },
    )
}

private fun cashierBelongsToAdmin(
    cashier: UserAccount,
    admin: UserAccount,
): Boolean {
    return cashier.adminId.equals(admin.id, ignoreCase = true) ||
        cashier.adminUser.equals(admin.user, ignoreCase = true) ||
        (!admin.banca.isNullOrBlank() && cashier.banca.equals(admin.banca, ignoreCase = true))
}

internal fun mergeCashierUpdates(
    currentCashiers: List<UserAccount>,
    updatedCashiers: List<UserAccount>,
): List<UserAccount> {
    val updatesByKey = updatedCashiers.flatMap { cashier ->
        listOf(cashier.id.lowercase(Locale.US), cashier.user.lowercase(Locale.US)).filter { it.isNotBlank() }.map { it to cashier }
    }.toMap()
    return currentCashiers.map { cashier ->
        updatesByKey[cashier.id.lowercase(Locale.US)]
            ?: updatesByKey[cashier.user.lowercase(Locale.US)]
            ?: cashier
    }
}

private fun cashierBelongsToSupervisorAdmin(
    cashier: UserAccount,
    supervisor: UserAccount,
): Boolean {
    return (!supervisor.adminId.isNullOrBlank() && cashier.adminId.equals(supervisor.adminId, ignoreCase = true)) ||
        (!supervisor.adminUser.isNullOrBlank() && cashier.adminUser.equals(supervisor.adminUser, ignoreCase = true)) ||
        (!supervisor.banca.isNullOrBlank() && cashier.banca.equals(supervisor.banca, ignoreCase = true))
}

private fun cashierAssignedToSupervisor(
    cashier: UserAccount,
    supervisor: UserAccount,
): Boolean {
    return cashier.supervisorIds.any { it.equals(supervisor.id, ignoreCase = true) } ||
        cashier.supervisorUsers.any { it.equals(supervisor.user, ignoreCase = true) }
}

private fun cashierAssignedToAnySupervisor(cashier: UserAccount): Boolean {
    return cashier.supervisorIds.any { it.isNotBlank() } || cashier.supervisorUsers.any { it.isNotBlank() }
}

internal fun parseSupervisorGroupCommission(rawValue: String): Double? {
    return rawValue.trim()
        .takeIf { it.isNotBlank() }
        ?.toDoubleOrNull()
        ?.let(::normalizeCommission)
}

internal fun buildSupervisorCredentialShareText(supervisor: UserAccount, password: String): String {
    val lines = mutableListOf("Credencial supervisor")
    supervisor.banca?.takeIf { it.isNotBlank() }?.let { lines += "Banca: $it" }
    lines += "Nombre: ${supervisor.displayName ?: supervisor.user}"
    lines += "Usuario: ${supervisor.user}"
    lines += "Clave: $password"
    lines += "Rol: Supervisor"
    return lines.joinToString("\n")
}

internal fun supervisorDetailRows(supervisor: UserAccount, assignedCashiers: Int): List<String> {
    val rows = mutableListOf(
        "ID: ${supervisor.id}",
        "Usuario: ${supervisor.user}",
        "Estado: ${if (supervisor.active) "Activo" else "Bloqueado"}",
    )
    supervisor.commissionRate?.let { rows += "Comision: ${formatCommissionLabel(it)}" }
    rows += "Grupo: $assignedCashiers cajero(s)"
    return rows
}

private fun shareSupervisorCredential(context: Context, text: String) {
    if (text.isBlank()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir credencial"))
}

private fun nextSupervisorId(accounts: List<UserAccount>): String {
    val next = accounts
        .mapNotNull { account ->
            Regex("""SUP-(\d+)""", RegexOption.IGNORE_CASE)
                .matchEntire(account.id.trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
        .maxOrNull()
        ?.plus(1)
        ?: 1
    return "SUP-$next"
}

internal data class CashierAccountMetric(
    val actorKey: String,
    val actorDisplay: String,
    val ventas: Double,
    val comision: Double,
    val premios: Double,
    val neto: Double,
    val salesRatio: Float,
    val commissionRatio: Float,
    val resultRatio: Float,
    val isLoss: Boolean,
)

internal data class CashierAdminInsightRow(
    val account: UserAccount,
    val metric: CashierAccountMetric?,
)

internal fun buildCashierAdminInsightRows(
    cashiers: List<UserAccount>,
    metrics: Map<String, CashierAccountMetric>,
): List<CashierAdminInsightRow> {
    return sortCashierAccountsNatural(cashiers)
        .filter { it.role == UserRole.CASHIER }
        .map { cashier ->
            CashierAdminInsightRow(
                account = cashier,
                metric = metrics[cashier.id] ?: metrics[cashier.user],
            )
        }
}

internal fun filterCashierAdminInsightRows(
    rows: List<CashierAdminInsightRow>,
    filter: CashierInsightFilter,
    query: String,
): List<CashierAdminInsightRow> {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    return rows.filter { row ->
        val account = row.account
        val metric = row.metric
        val matchesQuery = normalizedQuery.isBlank() ||
            listOfNotNull(account.displayName, account.user, account.phone, account.banca)
                .any { it.lowercase(Locale.US).contains(normalizedQuery) }
        val matchesFilter = when (filter) {
            CashierInsightFilter.ALL -> true
            CashierInsightFilter.ACTIVE -> account.active
            CashierInsightFilter.BLOCKED -> !account.active
            CashierInsightFilter.SOLD -> (metric?.ventas ?: 0.0) > 0.0
            CashierInsightFilter.LOSS -> metric?.isLoss == true
            CashierInsightFilter.BENEFIT -> (metric?.neto ?: 0.0) > 0.0
        }
        matchesQuery && matchesFilter
    }
}

internal fun cashierResultLabel(metric: CashierAccountMetric?): String {
    if (metric == null) return "Sin venta"
    return if (metric.isLoss) "Pérdida" else "Beneficio"
}

internal fun buildCashierAccountMetrics(rows: List<FinanceActorPeriodRow>): Map<String, CashierAccountMetric> {
    val maxVentas = rows.maxOfOrNull { it.summary.ventas } ?: 0.0
    return rows.associate { row -> row.actorKey to buildCashierAccountMetric(row, maxVentas) }
}

internal fun buildCashierAccountMetric(
    row: FinanceActorPeriodRow,
    maxVentas: Double,
): CashierAccountMetric {
    val summary = row.summary
    val neto = summary.netoProyectado
    val premios = summary.premiosPagados + summary.premiosPendientes
    val base = maxOf(maxVentas, summary.ventas, summary.comision, abs(neto), premios)
    return CashierAccountMetric(
        actorKey = row.actorKey,
        actorDisplay = row.actorDisplay,
        ventas = summary.ventas,
        comision = summary.comision,
        premios = premios,
        neto = neto,
        salesRatio = normalizedRatio(summary.ventas, maxVentas),
        commissionRatio = normalizedRatio(summary.comision, summary.ventas.takeIf { it > 0.0 } ?: base),
        resultRatio = normalizedRatio(abs(neto), base),
        isLoss = neto < 0.0,
    )
}

private fun normalizedRatio(value: Double, maxValue: Double): Float {
    if (value <= 0.0 || maxValue <= 0.0) return 0f
    return (value / maxValue).coerceIn(0.06, 1.0).toFloat()
}

@Composable
private fun UserAccountsRoute(
    session: ActiveSession,
    initialAccounts: List<UserAccount>,
    initialCashierReport: FinancePeriodReport?,
    initialAdminSectionName: String? = null,
    onBack: () -> Unit,
    onLoadCashierReport: (FinancePeriodPreset) -> FinancePeriodReport,
    onSaveAccount: (UserAccount, String, Double, Double?, Double?, Boolean, String?) -> List<UserAccount>,
    onSaveAccountsBatch: (List<UserAccount>) -> List<UserAccount>,
    onCreateSupervisor: (String, String, String, Set<String>, Boolean, Double?) -> SupervisorCreateResult,
    onResetSupervisorPassword: (UserAccount, String) -> SupervisorPasswordResetResult,
    onToggleSupervisorActive: (UserAccount) -> List<UserAccount>,
    onDeleteSupervisor: (UserAccount) -> List<UserAccount>,
    onSaveSupervisorAssignments: (UserAccount, Set<String>, Double?) -> List<UserAccount>,
    onLoadCashierLimits: (UserAccount) -> CashierSalesLimitInputs,
    onLoadDefaultCashierLimits: () -> CashierSalesLimitInputs,
    onSaveCashierLimits: (UserAccount, CashierSalesLimitInputs) -> Unit,
    onSaveDefaultCashierLimits: (CashierSalesLimitInputs) -> Unit,
    onLoadCashierPrizePayout: (UserAccount?) -> PrizeTableConfig,
    onSaveCashierPrizePayout: (UserAccount?, PrizeTableConfig) -> Unit,
    onRefreshServer: ((Boolean, List<UserAccount>) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf(initialAccounts) }
    var selectedFilterName by rememberSaveable { mutableStateOf(UserAccountFilter.ALL.name) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedPerformancePeriodName by rememberSaveable { mutableStateOf(FinancePeriodPreset.DAY.name) }
    var selectedCashierId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSupervisorId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAdminSectionName by rememberSaveable {
        mutableStateOf(resolveInitialAdminSectionName(initialAdminSectionName))
    }
    var selectedSupervisorAdminViewName by rememberSaveable { mutableStateOf(SupervisorAdminView.GROUP.name) }
    var supervisorUserInput by rememberSaveable { mutableStateOf("") }
    var supervisorNameInput by rememberSaveable { mutableStateOf("") }
    var supervisorPasswordInput by rememberSaveable { mutableStateOf("") }
    var supervisorCommissionInput by rememberSaveable { mutableStateOf("") }
    var supervisorActiveInput by rememberSaveable { mutableStateOf(true) }
    var supervisorCredentialShareText by rememberSaveable { mutableStateOf("") }
    var selectedModeCashierId by rememberSaveable { mutableStateOf("") }
    val supervisorAssignedCashierIds = remember { mutableStateMapOf<String, Boolean>() }
    var cashierReport by remember { mutableStateOf(initialCashierReport) }
    var statusMessage by rememberSaveable {
        mutableStateOf("Cuentas locales listas.")
    }
    var selectedCashierLimits by remember { mutableStateOf<CashierSalesLimitInputs?>(null) }
    var daySaleLimitInput by rememberSaveable { mutableStateOf("") }
    var payoutLimitInput by rememberSaveable { mutableStateOf("") }
    var quinielaLimitInput by rememberSaveable { mutableStateOf("") }
    var paleLimitInput by rememberSaveable { mutableStateOf("") }
    var superPaleLimitInput by rememberSaveable { mutableStateOf("") }
    var tripletaLimitInput by rememberSaveable { mutableStateOf("") }
    var pick3StraightLimitInput by rememberSaveable { mutableStateOf("") }
    var pick3BoxLimitInput by rememberSaveable { mutableStateOf("") }
    var pick4StraightLimitInput by rememberSaveable { mutableStateOf("") }
    var pick4BoxLimitInput by rememberSaveable { mutableStateOf("") }
    val prizePayoutInputs = remember { mutableStateMapOf<String, String>() }
    val balanceInputs = remember(accounts) {
        mutableStateMapOf<String, String>().apply {
            accounts.forEach { put(it.id, formatBalanceInput(it.balance)) }
        }
    }
    val commissionInputs = remember(accounts) {
        mutableStateMapOf<String, String>().apply {
            accounts.forEach { put(it.id, formatCommissionInput(it.commissionRate)) }
        }
    }
    val recargaTxInputs = remember(accounts) {
        mutableStateMapOf<String, String>().apply {
            accounts.forEach { put(it.id, formatBalanceInput(it.recargaTxLimit ?: 0.0).takeIf { accountHasTxLimit(it) } ?: "") }
        }
    }
    val displayNameInputs = remember(accounts) {
        mutableStateMapOf<String, String>().apply {
            accounts.forEach { put(it.id, it.displayName.orEmpty()) }
        }
    }
    val systemModeInputs = remember(accounts) {
        mutableStateMapOf<String, String>().apply {
            accounts.forEach { put(it.id, normalizeCashierSystemModeOverride(it.systemModeOverride) ?: "lottery") }
        }
    }
    val selectedFilter = remember(selectedFilterName) {
        UserAccountFilter.entries.firstOrNull { it.name == selectedFilterName } ?: UserAccountFilter.ALL
    }
    val selectedPerformancePeriod = remember(selectedPerformancePeriodName) {
        FinancePeriodPreset.entries.firstOrNull { it.name == selectedPerformancePeriodName } ?: FinancePeriodPreset.DAY
    }
    val selectedPerformancePeriodLabel = remember(selectedPerformancePeriod) {
        cashierPerformancePeriodOptions().firstOrNull { it.preset == selectedPerformancePeriod }?.label ?: "Día"
    }
    val selectedAdminSection = remember(selectedAdminSectionName) {
        CashierAdminSection.entries.firstOrNull { it.name == selectedAdminSectionName } ?: CashierAdminSection.LIMITS
    }
    val supervisorConsole = remember(initialAdminSectionName) {
        resolveInitialAdminSectionName(initialAdminSectionName) == CashierAdminSection.SUPERVISORS.name
    }
    val selectedSupervisorAdminView = remember(selectedSupervisorAdminViewName) {
        SupervisorAdminView.entries.firstOrNull { it.name == selectedSupervisorAdminViewName } ?: SupervisorAdminView.GROUP
    }
    val cashierPickerAccounts = remember(accounts, selectedFilter, searchQuery) {
        filterUserAccountsForAdmin(accounts, selectedFilter, searchQuery)
            .filter { it.role == UserRole.ADMIN || it.role == UserRole.MASTER || it.role == UserRole.CASHIER }
    }
    val supervisorAccounts = remember(accounts) {
        accounts.filter { it.role == UserRole.SUPERVISOR }
            .sortedBy { it.displayName?.lowercase(Locale.US) ?: it.user.lowercase(Locale.US) }
    }
    val selectedSupervisor = remember(supervisorAccounts, selectedSupervisorId) {
        supervisorAccounts.firstOrNull { it.id == selectedSupervisorId } ?: supervisorAccounts.firstOrNull()
    }
    val supervisorOwnerAccount = remember(accounts, session.userId, session.username, session.banca, session.territory) {
        accounts.firstOrNull {
            it.id.equals(session.userId, ignoreCase = true) || it.user.equals(session.username, ignoreCase = true)
        } ?: UserAccount(
            id = session.userId,
            user = session.username,
            role = session.role,
            banca = session.banca,
            territory = session.territory,
        )
    }
    val adminCashiers = remember(
        accounts,
        selectedSupervisor?.id,
        selectedSupervisor?.user,
        selectedSupervisorAdminView,
        supervisorOwnerAccount.id,
        supervisorOwnerAccount.user,
    ) {
        val cashiers = accounts.filter { it.role == UserRole.CASHIER }
        if (selectedSupervisorAdminView == SupervisorAdminView.CREATE) {
            supervisorAvailableCashiersForCreate(cashiers, supervisorOwnerAccount)
        } else {
            sortCashierAccountsNatural(
                supervisorAssignableCashiers(
                    cashiers = cashiers,
                    selectedSupervisor = selectedSupervisor,
                ),
            )
        }
    }
    val modeCashiers = remember(accounts) {
        sortCashierAccountsNatural(accounts.filter { it.role == UserRole.CASHIER })
    }
    val selectedModeCashier = remember(modeCashiers, selectedModeCashierId) {
        modeCashiers.firstOrNull { it.id == selectedModeCashierId } ?: modeCashiers.firstOrNull()
    }
    val selectedCashier = remember(cashierPickerAccounts, selectedCashierId) {
        resolveSelectedCashierAccount(cashierPickerAccounts, selectedCashierId)
    }
    val selectedAllCashiers = selectedCashierId == ALL_CASHIER_LIMITS_ID
    val visibleAccounts = remember(accounts, session.role, selectedCashier?.id, selectedCashierId, cashierPickerAccounts) {
        if (session.role == UserRole.ADMIN) {
            resolveAdminVisibleCashierDetailAccounts(cashierPickerAccounts, selectedCashierId)
        } else {
            accounts.filter { it.id.equals(session.userId, ignoreCase = true) || it.user.equals(session.username, ignoreCase = true) }
        }
    }
    val accountsSummary = remember(accounts) { buildUserAccountsSummary(accounts) }
    val cashierMetrics = remember(cashierReport) { buildCashierAccountMetrics(cashierReport?.actorRows.orEmpty()) }
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveUserAccountsLayout(visual.windowMode) }
    val selfAccount = remember(accounts, session.userId, session.username) {
        accounts.firstOrNull {
            it.id.equals(session.userId, ignoreCase = true) || it.user.equals(session.username, ignoreCase = true)
        } ?: accounts.firstOrNull { it.role == UserRole.ADMIN || it.role == UserRole.MASTER }
    }
    val refreshServerAction = {
        statusMessage = "Actualizando servidor..."
        onRefreshServer { success, refreshedAccounts ->
            accounts = refreshedAccounts
            statusMessage = cashierAdminServerStatusLabel(success)
        }
    }
    LaunchedEffect(Unit) {
        if (session.role == UserRole.ADMIN) {
            refreshServerAction()
        }
    }
    LaunchedEffect(modeCashiers.map { it.id }.joinToString("|")) {
        if (modeCashiers.none { it.id == selectedModeCashierId }) {
            selectedModeCashierId = modeCashiers.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(selectedCashierId, selectedCashier?.id) {
        if (selectedAdminSection == CashierAdminSection.SUPERVISORS) return@LaunchedEffect
        val limits = if (selectedAllCashiers) {
            onLoadDefaultCashierLimits()
        } else {
            val currentCashier = selectedCashier ?: return@LaunchedEffect
            onLoadCashierLimits(currentCashier)
        }
        selectedCashierLimits = limits
        daySaleLimitInput = formatBalanceInput(limits.daySale)
        payoutLimitInput = formatBalanceInput(limits.payout)
        quinielaLimitInput = formatBalanceInput(limits.quiniela)
        paleLimitInput = formatBalanceInput(limits.pale)
        superPaleLimitInput = formatBalanceInput(limits.superPale)
        tripletaLimitInput = formatBalanceInput(limits.tripleta)
        pick3StraightLimitInput = formatBalanceInput(limits.pick3Straight)
        pick3BoxLimitInput = formatBalanceInput(limits.pick3Box)
        pick4StraightLimitInput = formatBalanceInput(limits.pick4Straight)
        pick4BoxLimitInput = formatBalanceInput(limits.pick4Box)
    }
    LaunchedEffect(selectedCashierId, selectedCashier?.id) {
        if (selectedAdminSection == CashierAdminSection.SUPERVISORS) return@LaunchedEffect
        val payout = onLoadCashierPrizePayout(if (selectedAllCashiers) null else selectedCashier)
        prizePayoutInputs.clear()
        prizePayoutInputs.putAll(formatPrizePayoutInputs(payout))
    }
    LaunchedEffect(session.role, selectedPerformancePeriodName) {
        if (session.role != UserRole.ADMIN) return@LaunchedEffect
        val report = withContext(Dispatchers.IO) {
            runCatching { onLoadCashierReport(selectedPerformancePeriod) }
        }
        cashierReport = report.getOrElse {
            statusMessage = "No se pudo cargar el rendimiento de cajeros."
            cashierReport
        }
    }
    LaunchedEffect(selectedSupervisor?.id, selectedSupervisorAdminView, accounts) {
        if (selectedSupervisorAdminView == SupervisorAdminView.CREATE) {
            supervisorAssignedCashierIds.clear()
            adminCashiers.forEach { cashier -> supervisorAssignedCashierIds[cashier.id] = false }
            return@LaunchedEffect
        }
        val supervisor = selectedSupervisor ?: return@LaunchedEffect
        supervisorAssignedCashierIds.clear()
        supervisorCommissionInput = formatCommissionInput(supervisor.commissionRate)
        adminCashiers.forEach { cashier ->
            supervisorAssignedCashierIds[cashier.id] =
                cashier.supervisorIds.any { it.equals(supervisor.id, ignoreCase = true) } ||
                    cashier.supervisorUsers.any { it.equals(supervisor.user, ignoreCase = true) }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = visual.colors.background,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
                verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = visual.sizes.screenPaddingV),
            ) {
            item {
                if (session.role == UserRole.ADMIN && selectedAdminSection == CashierAdminSection.SUPERVISORS) {
                    UserAccountsCompactHeader(
                        title = "Supervisores",
                        subtitle = "Admin · Grupos y cajeros",
                        onBack = onBack,
                        onRefresh = refreshServerAction,
                    )
                } else {
                    ScreenHeaderPanel(
                        title = if (session.role == UserRole.ADMIN) "Cajeros" else "Mi cuenta",
                        subtitle = "${session.banca ?: "LotteryNet"} · ${accounts.size} cuentas",
                        onBack = onBack,
                        badgeLabel = presentUserRoleLabel(session.role),
                        badgeTone = MaterialTheme.colorScheme.primary,
                        actionIcon = if (session.role == UserRole.ADMIN) Icons.Rounded.Sync else null,
                        actionContentDescription = "Actualizar servidor",
                        onAction = if (session.role == UserRole.ADMIN) refreshServerAction else null,
                    )
                }
            }
            if (session.role == UserRole.ADMIN && selectedAdminSection == CashierAdminSection.SUPERVISORS) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CompactStatusBadge(label = "Sincronizado", tone = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item {
                if (
                    statusMessage.contains("Actualizando", ignoreCase = true) ||
                    statusMessage.contains("Guardando", ignoreCase = true)
                ) {
                    CompactLoadingState(label = statusMessage)
                } else {
                    InfoStrip(text = statusMessage)
                }
            }
            if (session.role == UserRole.ADMIN && !supervisorConsole) {
                selfAccount?.let { account ->
                    item {
                        AccountMiniSummary(
                            account = account,
                            title = "Mi cuenta",
                        )
                    }
                }
            }
            if (session.role == UserRole.ADMIN && !supervisorConsole) {
                item {
                    AdminAccountsControlPanel(
                        layout = layout,
                        summary = accountsSummary,
                        selectedFilter = selectedFilter,
                        searchQuery = searchQuery,
                        onFilterChange = { selectedFilterName = it.name },
                        onSearchChange = { searchQuery = it },
                        cashierOptions = cashierSelectorOptions(cashierPickerAccounts),
                        selectedCashierId = if (selectedAllCashiers) ALL_CASHIER_LIMITS_ID else selectedCashier?.id,
                        onCashierSelected = { selectedCashierId = it },
                        onRefreshServer = refreshServerAction,
                    )
                }
            }
            if (session.role == UserRole.ADMIN && !supervisorConsole) {
                item {
                    CashierAdminSectionTabs(
                        selected = selectedAdminSection,
                        onSelected = { selectedAdminSectionName = it.name },
                        sections = cashierAdminSectionsForConsole(supervisorConsole),
                    )
                }
            }
            if (session.role == UserRole.ADMIN && selectedAdminSection == CashierAdminSection.SUPERVISORS) {
                item {
                    SupervisorAdminPanel(
                        supervisors = supervisorAccounts,
                        cashiers = adminCashiers,
                        selectedView = selectedSupervisorAdminView,
                        selectedSupervisor = selectedSupervisor,
                        selectedSupervisorId = selectedSupervisor?.id,
                        assignedCashierIds = supervisorAssignedCashierIds,
                        userValue = supervisorUserInput,
                        nameValue = supervisorNameInput,
                        passwordValue = supervisorPasswordInput,
                        commissionValue = supervisorCommissionInput,
                        active = supervisorActiveInput,
                        credentialShareText = supervisorCredentialShareText,
                        onUserChange = { supervisorUserInput = it.trim().lowercase(Locale.US) },
                        onNameChange = { supervisorNameInput = it },
                        onPasswordChange = { supervisorPasswordInput = it },
                        onCommissionChange = { supervisorCommissionInput = sanitizeDecimal(it) },
                        onActiveChange = { supervisorActiveInput = it },
                        onViewSelected = { selectedSupervisorAdminViewName = it.name },
                        onSupervisorSelected = { selectedSupervisorId = it },
                        onCashierToggle = { cashierId, selected -> supervisorAssignedCashierIds[cashierId] = selected },
                        onCreate = {
                            runCatching {
                                onCreateSupervisor(
                                    supervisorUserInput,
                                    supervisorNameInput,
                                    supervisorPasswordInput,
                                    supervisorAssignedCashierIds.filterValues { it }.keys,
                                    supervisorActiveInput,
                                    parseSupervisorGroupCommission(supervisorCommissionInput),
                                )
                            }.onSuccess { result ->
                                accounts = buildList {
                                    addAll(accounts.filterNot { it.role == UserRole.CASHIER })
                                    add(result.supervisor)
                                    addAll(
                                        mergeCashierUpdates(
                                            currentCashiers = accounts.filter { it.role == UserRole.CASHIER },
                                            updatedCashiers = result.cashiers,
                                        ),
                                    )
                                }.distinctBy { it.id }
                                selectedSupervisorId = result.supervisor.id
                                selectedSupervisorAdminViewName = SupervisorAdminView.GROUP.name
                                supervisorCredentialShareText = buildSupervisorCredentialShareText(result.supervisor, result.password)
                                statusMessage = "Supervisor ${result.supervisor.user} creado. Credencial lista para compartir."
                                supervisorUserInput = ""
                                supervisorNameInput = ""
                                supervisorPasswordInput = ""
                                supervisorCommissionInput = ""
                                supervisorActiveInput = true
                            }.onFailure { throwable ->
                                statusMessage = throwable.message ?: "No se pudo crear supervisor."
                            }
                        },
                        onResetPassword = {
                            selectedSupervisor?.let { supervisor ->
                                runCatching {
                                    onResetSupervisorPassword(supervisor, supervisorPasswordInput)
                                }.onSuccess { result ->
                                    accounts = accounts.map {
                                        if (it.id.equals(result.supervisor.id, ignoreCase = true)) result.supervisor else it
                                    }
                                    selectedSupervisorId = result.supervisor.id
                                    supervisorCredentialShareText = buildSupervisorCredentialShareText(result.supervisor, result.password)
                                    supervisorPasswordInput = ""
                                    statusMessage = "Clave de ${result.supervisor.user} cambiada. Credencial lista para compartir."
                                }.onFailure { throwable ->
                                    statusMessage = throwable.message ?: "No se pudo cambiar la clave."
                                }
                            }
                        },
                        onToggleSupervisorActive = {
                            selectedSupervisor?.let { supervisor ->
                                runCatching {
                                    onToggleSupervisorActive(supervisor)
                                }.onSuccess { updatedAccounts ->
                                    accounts = updatedAccounts
                                    selectedSupervisorId = supervisor.id
                                    val updatedSupervisor = updatedAccounts.firstOrNull {
                                        it.id.equals(supervisor.id, ignoreCase = true)
                                    } ?: supervisor
                                    statusMessage = if (updatedSupervisor.active) {
                                        "Supervisor ${updatedSupervisor.user} desbloqueado."
                                    } else {
                                        "Supervisor ${updatedSupervisor.user} bloqueado."
                                    }
                                }.onFailure { throwable ->
                                    statusMessage = throwable.message ?: "No se pudo cambiar el estado del supervisor."
                                }
                            }
                        },
                        onDeleteSupervisor = {
                            selectedSupervisor?.let { supervisor ->
                                runCatching {
                                    onDeleteSupervisor(supervisor)
                                }.onSuccess { updatedAccounts ->
                                    accounts = updatedAccounts
                                    selectedSupervisorId = updatedAccounts
                                        .firstOrNull { it.role == UserRole.SUPERVISOR }
                                        ?.id
                                    supervisorAssignedCashierIds.clear()
                                    supervisorCredentialShareText = ""
                                    supervisorPasswordInput = ""
                                    statusMessage = "Supervisor ${supervisor.user} eliminado y cajeros liberados."
                                }.onFailure { throwable ->
                                    statusMessage = throwable.message ?: "No se pudo eliminar supervisor."
                                }
                            }
                        },
                        onSaveAssignments = {
                            selectedSupervisor?.let { supervisor ->
                                accounts = onSaveSupervisorAssignments(
                                    supervisor,
                                    supervisorAssignedCashierIds.filterValues { it }.keys,
                                    parseSupervisorGroupCommission(supervisorCommissionInput),
                                )
                                statusMessage = "Cajeros asignados a ${supervisor.user} guardados."
                            }
                        },
                        onShareCredential = {
                            shareSupervisorCredential(context, supervisorCredentialShareText)
                        },
                    )
                }
            }
            if (session.role == UserRole.ADMIN && (selectedAllCashiers || selectedCashier != null)) {
                val currentCashier = selectedCashier
                if (selectedAdminSection == CashierAdminSection.LIMITS) {
                    item {
                        CompactPanel {
                            OperationalListHeader(
                                title = if (selectedAllCashiers) "Límites de venta globales" else "Límites de venta del usuario",
                                meta = if (selectedAllCashiers) "Venta máxima por jugada" else currentCashier?.user.orEmpty(),
                            )
                            CashierLimitsEditor(
                                daySaleLimit = daySaleLimitInput,
                                payoutLimit = payoutLimitInput,
                                payoutLabel = cashierPayoutLimitLabel(selectedAllCashiers),
                                quinielaLimit = quinielaLimitInput,
                                paleLimit = paleLimitInput,
                                superPaleLimit = superPaleLimitInput,
                                tripletaLimit = tripletaLimitInput,
                                pick3StraightLimit = pick3StraightLimitInput,
                                pick3BoxLimit = pick3BoxLimitInput,
                                pick4StraightLimit = pick4StraightLimitInput,
                                pick4BoxLimit = pick4BoxLimitInput,
                                onDaySaleChange = { daySaleLimitInput = sanitizeDecimal(it) },
                                onPayoutChange = { payoutLimitInput = sanitizeDecimal(it) },
                                onQuinielaChange = { quinielaLimitInput = sanitizeDecimal(it) },
                                onPaleChange = { paleLimitInput = sanitizeDecimal(it) },
                                onSuperPaleChange = { superPaleLimitInput = sanitizeDecimal(it) },
                                onTripletaChange = { tripletaLimitInput = sanitizeDecimal(it) },
                                onPick3StraightChange = { pick3StraightLimitInput = sanitizeDecimal(it) },
                                onPick3BoxChange = { pick3BoxLimitInput = sanitizeDecimal(it) },
                                onPick4StraightChange = { pick4StraightLimitInput = sanitizeDecimal(it) },
                                onPick4BoxChange = { pick4BoxLimitInput = sanitizeDecimal(it) },
                                onSave = {
                                    val limits = CashierSalesLimitInputs(
                                        daySale = daySaleLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                        payout = payoutLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                        quiniela = quinielaLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                        pale = paleLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                        superPale = superPaleLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                        tripleta = tripletaLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                        pick3Straight = pick3StraightLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                        pick3Box = pick3BoxLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                        pick4Straight = pick4StraightLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                        pick4Box = pick4BoxLimitInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                    )
                                    if (selectedAllCashiers) {
                                        onSaveDefaultCashierLimits(limits)
                                    } else {
                                        currentCashier?.let { onSaveCashierLimits(it, limits) }
                                    }
                                    selectedCashierLimits = limits
                                    statusMessage = if (selectedAllCashiers) {
                                        "Límites de venta globales guardados."
                                    } else {
                                        "Límites de venta de ${currentCashier?.user.orEmpty()} guardados."
                                    }
                                },
                            )
                        }
                    }
                } else if (selectedAdminSection == CashierAdminSection.MODE) {
                    item {
                        CompactPanel {
                            if (modeCashiers.isEmpty()) {
                                OperationalListHeader(
                                    title = "Modo de venta por cajero",
                                    meta = "Sin cajeros",
                                )
                                CompactEmptyState(
                                    message = "No hay cajeros disponibles para asignar modo.",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                val targetCashier = selectedModeCashier ?: modeCashiers.first()
                                CashierModeAssignmentPanel(
                                    cashiers = modeCashiers,
                                    selectedCashierId = targetCashier.id,
                                    selectedMode = systemModeInputs[targetCashier.id].orEmpty().ifBlank {
                                        normalizeCashierSystemModeOverride(targetCashier.systemModeOverride) ?: "lottery"
                                    },
                                    onCashierSelected = { selectedModeCashierId = it },
                                    onModeSelected = { mode ->
                                        systemModeInputs[targetCashier.id] = mode
                                        accounts = onSaveAccount(
                                            targetCashier,
                                            targetCashier.displayName.orEmpty(),
                                            targetCashier.balance,
                                            targetCashier.commissionRate,
                                            targetCashier.recargaTxLimit,
                                            targetCashier.active,
                                            mode,
                                        )
                                        systemModeInputs.clear()
                                        accounts.forEach {
                                            systemModeInputs[it.id] = normalizeCashierSystemModeOverride(it.systemModeOverride) ?: "lottery"
                                        }
                                        selectedModeCashierId = targetCashier.id
                                        statusMessage = "Modo de ${targetCashier.user} guardado automaticamente: ${cashierSystemModeOverrideLabel(mode)}."
                                    },
                                    onSave = {
                                        val mode = systemModeInputs[targetCashier.id].orEmpty().ifBlank {
                                            normalizeCashierSystemModeOverride(targetCashier.systemModeOverride) ?: "lottery"
                                        }
                                        accounts = onSaveAccount(
                                            targetCashier,
                                            targetCashier.displayName.orEmpty(),
                                            targetCashier.balance,
                                            targetCashier.commissionRate,
                                            targetCashier.recargaTxLimit,
                                            targetCashier.active,
                                            mode,
                                        )
                                        systemModeInputs.clear()
                                        accounts.forEach {
                                            systemModeInputs[it.id] = normalizeCashierSystemModeOverride(it.systemModeOverride) ?: "lottery"
                                        }
                                        selectedModeCashierId = targetCashier.id
                                        statusMessage = "Modo de ${targetCashier.user} guardado en servidor: ${cashierSystemModeOverrideLabel(mode)}."
                                    },
                                    onSaveAll = { mode ->
                                        val cashierIds = modeCashiers.mapTo(mutableSetOf()) { it.id }
                                        val normalizedMode = normalizeCashierSystemModeOverride(mode)
                                        val updatedAccounts = accounts.map { account ->
                                            if (account.id in cashierIds) {
                                                account.copy(
                                                    systemModeOverride = normalizedMode,
                                                    updatedAtEpochMs = System.currentTimeMillis(),
                                                )
                                            } else {
                                                account
                                            }
                                        }
                                        accounts = onSaveAccountsBatch(updatedAccounts)
                                        systemModeInputs.clear()
                                        accounts.forEach {
                                            systemModeInputs[it.id] = normalizeCashierSystemModeOverride(it.systemModeOverride) ?: "lottery"
                                        }
                                        selectedModeCashierId = targetCashier.id
                                        statusMessage = "${cashierSystemModeOverrideLabel(mode)} aplicado a ${modeCashiers.size} cajeros."
                                    },
                                )
                            }
                        }
                    }
                } else if (selectedAdminSection == CashierAdminSection.PRIZES) {
                    item {
                        CompactPanel {
                            if (selectedAllCashiers) {
                                OperationalListHeader(
                                    title = cashierPrizeSectionLabel(),
                                    meta = "Elige un admin o cajero",
                                )
                                CompactEmptyState(
                                    message = "Selecciona un usuario para ajustar cuanto paga cada peso ganador.",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                            OperationalListHeader(
                                title = cashierPrizeSectionLabel(),
                                meta = cashierPrizeSectionPurpose(),
                            )
                            PrizePayoutEditor(
                                values = prizePayoutInputs,
                                onChange = { key, value -> prizePayoutInputs[key] = sanitizeInteger(value) },
                                onSave = {
                                    val config = prizeConfigFromInputs(prizePayoutInputs)
                                    onSaveCashierPrizePayout(if (selectedAllCashiers) null else currentCashier, config)
                                    statusMessage = if (selectedAllCashiers) {
                                        "Premios globales guardados en Supabase."
                                    } else {
                                        "Premios de ${currentCashier?.user.orEmpty()} guardados en Supabase."
                                    }
                                },
                            )
                            }
                        }
                    }
                }
            }
            val shouldRenderAccountCards = session.role != UserRole.ADMIN ||
                selectedAdminSection == CashierAdminSection.ACCOUNTS
            if (shouldRenderAccountCards && visibleAccounts.isEmpty()) {
                item {
                    CompactEmptyState(
                        if (session.role == UserRole.ADMIN) {
                            "No hay usuarios con ese filtro."
                        } else {
                            "No hay usuarios locales para esta banca."
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (shouldRenderAccountCards) {
                    items(visibleAccounts, key = { it.id }) { account ->
                        UserAccountCard(
                            layout = layout,
                            account = account,
                            displayNameValue = displayNameInputs[account.id].orEmpty(),
                            balanceValue = balanceInputs[account.id].orEmpty(),
                            commissionValue = commissionInputs[account.id].orEmpty(),
                            recargaTxValue = recargaTxInputs[account.id].orEmpty(),
                            canEdit = session.role == UserRole.ADMIN,
                            compactReadOnlyHint = layout.compactReadOnlyHint,
                            onDisplayNameChange = { displayNameInputs[account.id] = it },
                            onBalanceChange = { balanceInputs[account.id] = sanitizeDecimal(it) },
                            onCommissionChange = { commissionInputs[account.id] = sanitizeDecimal(it) },
                            onRecargaTxChange = { recargaTxInputs[account.id] = sanitizeDecimal(it) },
                            systemModeValue = systemModeInputs[account.id].orEmpty().ifBlank { "lottery" },
                            onSystemModeChange = { systemModeInputs[account.id] = it },
                            cashierMetric = if (account.role == UserRole.CASHIER) {
                                cashierMetrics[account.id] ?: cashierMetrics[account.user]
                            } else {
                                null
                            },
                            performancePeriodLabel = selectedPerformancePeriodLabel,
                            onSave = {
                                val parsedBalance = balanceInputs[account.id].orEmpty().toDoubleOrNull()
                                if (parsedBalance == null || parsedBalance < 0.0) {
                                    statusMessage = "Balance inválido para ${account.user}."
                                    return@UserAccountCard
                                }
                                val parsedCommission = commissionInputs[account.id].orEmpty().toDoubleOrNull()
                                val normalizedCommission = parsedCommission?.let(::normalizeCommission)
                                val parsedRecargaTx = recargaTxInputs[account.id].orEmpty().takeIf { it.isNotBlank() }?.toDoubleOrNull()
                                if (parsedRecargaTx != null && parsedRecargaTx < 0.0) {
                                    statusMessage = "Tope de recarga inválido para ${account.user}."
                                    return@UserAccountCard
                                }
                                val displayName = displayNameInputs[account.id].orEmpty()
                                val modeOverride = systemModeInputs[account.id].orEmpty()
                                accounts = onSaveAccount(account, displayName, parsedBalance, normalizedCommission, parsedRecargaTx, account.active, modeOverride)
                                balanceInputs.clear()
                                accounts.forEach { balanceInputs[it.id] = formatBalanceInput(it.balance) }
                                commissionInputs.clear()
                                accounts.forEach { commissionInputs[it.id] = formatCommissionInput(it.commissionRate) }
                                recargaTxInputs.clear()
                                accounts.forEach { recargaTxInputs[it.id] = formatRecargaTxInput(it.recargaTxLimit) }
                                displayNameInputs.clear()
                                accounts.forEach { displayNameInputs[it.id] = it.displayName.orEmpty() }
                                systemModeInputs.clear()
                                accounts.forEach { systemModeInputs[it.id] = normalizeCashierSystemModeOverride(it.systemModeOverride) ?: "lottery" }
                                statusMessage = "${displayName.trim().ifBlank { account.user }} quedó con balance ${formatMoney(parsedBalance)} y comisión ${formatCommissionLabel(normalizedCommission)}."
                            },
                            onToggleActive = if (canToggleAccount(session, account)) {
                                {
                                    val nextActive = !account.active
                                    accounts = onSaveAccount(
                                        account,
                                        displayNameInputs[account.id].orEmpty(),
                                        account.balance,
                                        account.commissionRate,
                                        account.recargaTxLimit,
                                        nextActive,
                                        systemModeInputs[account.id],
                                    )
                                    statusMessage = if (nextActive) {
                                        "${account.user} volvió a quedar activo."
                                    } else {
                                        "${account.user} quedó bloqueado."
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
            }
        }
    }
    }
}

private enum class AccountsTab {
    MINE,
    ALL,
    BLOCKED,
}

private enum class CashierAdminSection(val label: String) {
    ACCOUNTS("Cajeros"),
    LIMITS("Límites"),
    MODE("Modo venta"),
    PRIZES("Premios"),
    SUPERVISORS("Supervisores"),
}

internal data class CashierAdminTaskContract(
    val title: String,
    val showsAccountCards: Boolean,
    val showsLimitEditor: Boolean,
    val showsModeEditor: Boolean,
    val showsPrizeEditor: Boolean,
)

internal fun resolveCashierAdminTaskContract(sectionName: String): CashierAdminTaskContract {
    return when (CashierAdminSection.entries.firstOrNull { it.name == sectionName } ?: CashierAdminSection.ACCOUNTS) {
        CashierAdminSection.ACCOUNTS -> CashierAdminTaskContract("Cajeros", showsAccountCards = true, showsLimitEditor = false, showsModeEditor = false, showsPrizeEditor = false)
        CashierAdminSection.LIMITS -> CashierAdminTaskContract("Límites", showsAccountCards = false, showsLimitEditor = true, showsModeEditor = false, showsPrizeEditor = false)
        CashierAdminSection.MODE -> CashierAdminTaskContract("Modo venta", showsAccountCards = false, showsLimitEditor = false, showsModeEditor = true, showsPrizeEditor = false)
        CashierAdminSection.PRIZES -> CashierAdminTaskContract("Premios", showsAccountCards = false, showsLimitEditor = false, showsModeEditor = false, showsPrizeEditor = true)
        CashierAdminSection.SUPERVISORS -> CashierAdminTaskContract("Supervisores", showsAccountCards = false, showsLimitEditor = false, showsModeEditor = false, showsPrizeEditor = false)
    }
}

internal fun resolveInitialAdminSectionName(rawValue: String?): String {
    return CashierAdminSection.entries
        .firstOrNull { it.name.equals(rawValue?.trim().orEmpty(), ignoreCase = true) }
        ?.name
        ?: CashierAdminSection.ACCOUNTS.name
}

internal fun cashierAdminSectionLabels(supervisorConsole: Boolean): List<String> =
    cashierAdminSectionsForConsole(supervisorConsole).map { it.label }

private fun cashierAdminSectionsForConsole(supervisorConsole: Boolean): List<CashierAdminSection> {
    return if (supervisorConsole) {
        listOf(CashierAdminSection.SUPERVISORS)
    } else {
        listOf(CashierAdminSection.ACCOUNTS, CashierAdminSection.LIMITS, CashierAdminSection.MODE, CashierAdminSection.PRIZES)
    }
}

internal enum class SupervisorAdminView(val label: String) {
    CREATE("Crear"),
    GROUP("Grupo"),
    CREDENTIALS("Credenciales"),
}

internal fun supervisorAdminViewLabels(): List<String> =
    SupervisorAdminView.entries.map { it.label }

internal fun supervisorCreateOrganizationLabels(): List<String> =
    listOf("Usuario supervisor", "Nombre", "Clave manual", "Comisión supervisor %", "Cajeros disponibles")

internal enum class SupervisorCashierFilter(val label: String) {
    ALL("Todos"),
    ASSIGNED("Asignados"),
    FREE("Libres"),
}

internal fun supervisorCashierFilterOptions(): List<QuickFilterChip> =
    SupervisorCashierFilter.entries.map { QuickFilterChip(it.name, it.label) }

internal fun filterSupervisorCashierOptions(
    cashiers: List<UserAccount>,
    assignedCashierIds: Map<String, Boolean>,
    query: String,
    filter: SupervisorCashierFilter,
): List<UserAccount> {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    return cashiers
        .asSequence()
        .filter { cashier ->
            val assigned = assignedCashierIds[cashier.id] == true
            when (filter) {
                SupervisorCashierFilter.ALL -> true
                SupervisorCashierFilter.ASSIGNED -> assigned
                SupervisorCashierFilter.FREE -> !assigned
            }
        }
        .filter { cashier ->
            normalizedQuery.isBlank() ||
                listOfNotNull(cashier.displayName, cashier.user, cashier.phone)
                    .any { it.lowercase(Locale.US).contains(normalizedQuery) }
        }
        .toList()
        .let(::sortCashierAccountsNatural)
}

@Composable
private fun AccountMiniSummary(
    account: UserAccount,
    title: String,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.colors.muted,
                    maxLines = 1,
                )
                Text(
                    text = account.displayName ?: account.user,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = account.user,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactStatusBadge(
                label = if (account.active) "Activo" else "Bloqueado",
                tone = if (account.active) visual.colors.admin else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CashierAdminSectionTabs(
    selected: CashierAdminSection,
    onSelected: (CashierAdminSection) -> Unit,
    sections: List<CashierAdminSection>,
) {
    CompactSegmentedSelector(
        options = sections.map { QuickFilterChip(it.name, it.label) },
        selectedId = selected.name,
        onSelected = { id -> sections.firstOrNull { it.name == id }?.let(onSelected) },
        columns = sections.size.coerceAtMost(3).coerceAtLeast(1),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PrizePayoutEditor(
    values: Map<String, String>,
    onChange: (String, String) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrizePayoutGroup(
            title = "Quiniela",
            rows = listOf("q1" to "1ra", "q2" to "2da", "q3" to "3ra"),
            values = values,
            onChange = onChange,
        )
        PrizePayoutGroup(
            title = "Pale",
            rows = listOf("pale12" to "1-2", "pale13" to "1-3", "pale23" to "2-3"),
            values = values,
            onChange = onChange,
        )
        PrizePayoutGroup(
            title = "Tripleta",
            rows = listOf("tripleta3" to "3 números", "tripleta2" to "2 números"),
            values = values,
            onChange = onChange,
        )
        PrizePayoutGroup(
            title = "Otros",
            rows = listOf(
                "superPale" to "Super Pale",
                "pick3Straight" to "P3 directo",
                "pick3Box" to "P3 box",
                "pick4Straight" to "P4 directo",
                "pick4Box" to "P4 box",
            ),
            values = values,
            onChange = onChange,
        )
        CompactActionButton(
            label = "Guardar premios",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Save,
            active = true,
            tone = ActionTone.Primary,
        )
    }
}

@Composable
private fun PrizePayoutGroup(
    title: String,
    rows: List<Pair<String, String>>,
    values: Map<String, String>,
    onChange: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = rememberLotteryNetVisualSpec().colors.ink,
            fontWeight = FontWeight.Bold,
        )
        rows.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { row ->
                    LimitInput(
                        label = row.second,
                        value = values[row.first].orEmpty(),
                        onChange = { onChange(row.first, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AdminAccountsControlPanel(
    layout: UserAccountsLayoutContract,
    summary: UserAccountsSummary,
    selectedFilter: UserAccountFilter,
    searchQuery: String,
    onFilterChange: (UserAccountFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    cashierOptions: List<CashierSelectorOption>,
    selectedCashierId: String?,
    onCashierSelected: (String) -> Unit,
    onRefreshServer: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val selectedFilterOption = userAccountFilterOptions().firstOrNull { it.filter == selectedFilter }
        ?: userAccountFilterOptions().first()

    CompactPanel(
        alt = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = layout.filterPaddingVerticalDp.dp),
    ) {
        MetricStrip(
            items = listOf(
                MetricStripItem("Total", summary.total.toString(), visual.colors.ink),
                MetricStripItem("Activos", summary.active.toString(), visual.colors.admin),
                MetricStripItem("Bloq.", summary.blocked.toString(), MaterialTheme.colorScheme.error),
                MetricStripItem("Cajeros", summary.cashiers.toString(), visual.colors.sale),
                MetricStripItem("Sup.", summary.supervisors.toString(), visual.colors.finance),
            ),
        )
        CompactTextInput(
            label = "Buscar",
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Buscar usuario, supervisor o teléfono",
            leadingIcon = Icons.Rounded.ManageAccounts,
        )
        CompactSegmentedSelector(
            options = userAccountFilterOptions().map { QuickFilterChip(it.filter.name, it.label) },
            selectedId = selectedFilterOption.filter.name,
            onSelected = { id ->
                UserAccountFilter.entries.firstOrNull { it.name == id }?.let(onFilterChange)
            },
            columns = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        if (cashierOptions.isNotEmpty()) {
            AccountsDropdown(
                title = "Usuario",
                selectedLabel = cashierOptions.firstOrNull { it.id == selectedCashierId }?.label ?: "Elegir usuario",
                icon = Icons.Rounded.ManageAccounts,
                modifier = Modifier.fillMaxWidth(),
            ) { close ->
                cashierOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = { Icon(Icons.Rounded.ManageAccounts, contentDescription = null) },
                        onClick = {
                            onCashierSelected(option.id)
                            close()
                        },
                    )
                }
            }
        }
        CompactActionButton(
            label = cashierAdminServerActionLabel(),
            onClick = onRefreshServer,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Sync,
            tone = ActionTone.Secondary,
        )
    }
}

@Composable
private fun CashierAdminOverviewPanel(
    role: UserRole,
    rows: List<CashierAdminInsightRow>,
    onSegmentSelected: (CashierAdminWindowSegment) -> Unit,
    onApplyMode: () -> Unit,
    onToggleCashier: (UserAccount) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilterName by rememberSaveable { mutableStateOf(CashierInsightFilter.ALL.name) }
    val selectedFilter = CashierInsightFilter.entries.firstOrNull { it.name == selectedFilterName } ?: CashierInsightFilter.ALL
    val visibleRows = remember(rows, selectedFilter, query) {
        filterCashierAdminInsightRows(rows, selectedFilter, query)
    }
    val netTotal = visibleRows.sumOf { it.metric?.neto ?: 0.0 }
    CompactPanel(
        alt = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 9.dp),
    ) {
        SectionHeader(
            title = if (role == UserRole.SUPERVISOR) "Mis cajeros" else "Administración de cajeros",
            meta = "${visibleRows.size} visibles",
        )
        CompactStatusBadge(
            label = if (netTotal < 0.0) "Pérdida ${formatMoney(netTotal)}" else "Beneficio ${formatMoney(netTotal)}",
            tone = if (netTotal < 0.0) visual.colors.loss else visual.colors.gain,
        )
        CompactSegmentedSelector(
            options = cashierAdminWindowSegmentOptions(role),
            selectedId = CashierAdminWindowSegment.CASHIERS.name,
            onSelected = { id ->
                CashierAdminWindowSegment.entries.firstOrNull { it.name == id }?.let(onSegmentSelected)
            },
            columns = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        CompactTextInput(
            label = "Buscar cajero",
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Nombre, usuario o teléfono",
            leadingIcon = Icons.Rounded.ManageAccounts,
        )
        QuickFilterChips(
            filters = cashierInsightFilterOptions(role),
            selectedId = selectedFilter.name,
            onSelected = { selectedFilterName = it },
        )
        if (role == UserRole.ADMIN) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactActionButton(
                    "Activos",
                    onClick = { selectedFilterName = CashierInsightFilter.ACTIVE.name },
                    modifier = Modifier.weight(1f),
                    tone = ActionTone.Secondary,
                )
                CompactActionButton(
                    "Limpiar",
                    onClick = { selectedFilterName = CashierInsightFilter.ALL.name },
                    modifier = Modifier.weight(1f),
                    tone = ActionTone.Secondary,
                )
                CompactActionButton(
                    "Aplicar modo",
                    onClick = onApplyMode,
                    modifier = Modifier.weight(1f),
                    tone = ActionTone.Primary,
                )
            }
        }
        visibleRows.take(8).forEach { row ->
            CashierAdminInsightRowItem(
                row = row,
                canToggle = role == UserRole.ADMIN,
                onToggleCashier = onToggleCashier,
            )
        }
        if (visibleRows.size > 8) {
            Text(
                "+${visibleRows.size - 8} cajeros más",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            cashierAdminQuickActionLabels(role).forEach { label ->
                CompactActionButton(
                    label = label,
                    onClick = {
                        when (label) {
                            "Aplicar modo" -> onApplyMode()
                            "Monitoreo" -> onSegmentSelected(CashierAdminWindowSegment.MONITOR)
                            "Tickets" -> onSegmentSelected(CashierAdminWindowSegment.TICKETS)
                            "Reporte" -> onSegmentSelected(CashierAdminWindowSegment.REPORT)
                            else -> onSegmentSelected(CashierAdminWindowSegment.CASHIERS)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    tone = when (label) {
                        "Reporte" -> ActionTone.Warning
                        "Bloquear" -> ActionTone.Danger
                        else -> ActionTone.Primary
                    },
                )
            }
        }
    }
}

@Composable
private fun CashierAdminInsightRowItem(
    row: CashierAdminInsightRow,
    canToggle: Boolean,
    onToggleCashier: (UserAccount) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val account = row.account
    val metric = row.metric
    val resultTone = if (metric?.isLoss == true) visual.colors.loss else visual.colors.gain
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = visual.colors.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    account.displayName ?: account.user,
                    style = MaterialTheme.typography.bodyMedium,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${account.user} · ${if (account.active) "Activo" else "Bloqueado"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Venta ${formatMoney(metric?.ventas ?: 0.0)} · Premios ${formatMoney(metric?.premios ?: 0.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.ink,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactStatusBadge(
                    label = cashierResultLabel(metric),
                    tone = resultTone,
                )
                Text(
                    formatMoney(metric?.neto ?: 0.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = resultTone,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                if (canToggle) {
                    CompactToggleSwitch(
                        checked = account.active,
                        onCheckedChange = { onToggleCashier(account) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserAccountsCompactHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = Color(0xFF062A57),
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("☰", modifier = Modifier.clickable(onClick = onBack), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
            }
            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.clickable(onClick = onRefresh), tint = Color.White)
            Text("⋮", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}

@Composable
private fun SupervisorCompactRow(
    supervisor: UserAccount,
    assignedCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        alt = !selected,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFE6EEFF), RoundedCornerShape(22.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.ManageAccounts, contentDescription = null, tint = Color(0xFF155BD6))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    supervisor.displayName ?: supervisor.user,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${supervisor.user} · $assignedCount cajeros",
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactStatusBadge(
                label = if (supervisor.active) "Activo" else "Bloq.",
                tone = if (supervisor.active) visual.colors.admin else MaterialTheme.colorScheme.error,
            )
            CompactToggleSwitch(checked = supervisor.active, onCheckedChange = {}, enabled = false)
        }
    }
}

@Composable
private fun SupervisorAdminPanel(
    supervisors: List<UserAccount>,
    cashiers: List<UserAccount>,
    selectedView: SupervisorAdminView,
    selectedSupervisor: UserAccount?,
    selectedSupervisorId: String?,
    assignedCashierIds: Map<String, Boolean>,
    userValue: String,
    nameValue: String,
    passwordValue: String,
    commissionValue: String,
    active: Boolean,
    credentialShareText: String,
    onUserChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCommissionChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onViewSelected: (SupervisorAdminView) -> Unit,
    onSupervisorSelected: (String) -> Unit,
    onCashierToggle: (String, Boolean) -> Unit,
    onCreate: () -> Unit,
    onResetPassword: () -> Unit,
    onToggleSupervisorActive: () -> Unit,
    onDeleteSupervisor: () -> Unit,
    onSaveAssignments: () -> Unit,
    onShareCredential: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val assignedCount = assignedCashierIds.count { it.value }
    val availableCount = cashiers.count { assignedCashierIds[it.id] != true }
    CompactPanel {
        OperationalListHeader(
            title = "Supervisores",
            meta = when (selectedView) {
                SupervisorAdminView.CREATE -> "Crear acceso"
                SupervisorAdminView.GROUP -> "Grupo asignado"
                SupervisorAdminView.CREDENTIALS -> "Usuario y clave"
            },
        )
        MetricStrip(
            items = listOf(
                MetricStripItem("Sup.", supervisors.size.toString(), visual.colors.finance),
                MetricStripItem("Asign.", assignedCount.toString(), visual.colors.sale),
                MetricStripItem("Libres", availableCount.toString(), visual.colors.admin),
            ),
        )
        CompactSegmentedSelector(
            options = SupervisorAdminView.entries.map { QuickFilterChip(it.name, it.label) },
            selectedId = selectedView.name,
            onSelected = { id -> SupervisorAdminView.entries.firstOrNull { it.name == id }?.let(onViewSelected) },
            columns = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        if (supervisors.isNotEmpty()) {
            SectionHeader(title = "Lista supervisores", meta = "${supervisors.size} activos/bloqueados")
            supervisors.forEach { supervisor ->
                val assigned = cashiers.count {
                    it.supervisorIds.any { id -> id.equals(supervisor.id, ignoreCase = true) } ||
                        it.supervisorUsers.any { user -> user.equals(supervisor.user, ignoreCase = true) }
                }
                SupervisorCompactRow(
                    supervisor = supervisor,
                    assignedCount = assigned,
                    selected = supervisor.id == selectedSupervisorId,
                    onClick = { onSupervisorSelected(supervisor.id) },
                )
            }
        }
        if (selectedView != SupervisorAdminView.CREATE) {
            if (supervisors.isNotEmpty()) {
                AccountsDropdown(
                    title = "Supervisor",
                    selectedLabel = selectedSupervisor?.displayName ?: selectedSupervisor?.user ?: "Elegir supervisor",
                    icon = Icons.Rounded.ManageAccounts,
                    modifier = Modifier.fillMaxWidth(),
                ) { close ->
                    supervisors.forEach { supervisor ->
                        DropdownMenuItem(
                            text = { Text(supervisor.displayName ?: supervisor.user) },
                            leadingIcon = { Icon(Icons.Rounded.ManageAccounts, contentDescription = null) },
                            onClick = {
                                onSupervisorSelected(supervisor.id)
                                close()
                            },
                        )
                    }
                }
            } else {
                CompactEmptyState(
                    message = "Crea un supervisor para asignar cajeros.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (selectedView == SupervisorAdminView.CREATE) {
            SectionHeader(title = "Datos de acceso", meta = "Supervisor")
            CompactTextInput(
                label = "Usuario supervisor",
                value = userValue,
                onValueChange = onUserChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Rounded.ManageAccounts,
            )
            CompactTextInput(
                label = "Nombre",
                value = nameValue,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
            )
            CompactTextInput(
                label = "Clave manual",
                value = passwordValue,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = Icons.Rounded.Lock,
            )
            CompactTextInput(
                label = "Comisión supervisor %",
                value = commissionValue,
                onValueChange = onCommissionChange,
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
                leadingIcon = Icons.Rounded.Percent,
            )
            CompactSwitchRow(
                title = "Supervisor activo",
                subtitle = if (active) "Puede entrar y ver sus cajeros." else "Acceso bloqueado hasta reactivar.",
                checked = active,
                onCheckedChange = onActiveChange,
                tone = if (active) ActionTone.Success else ActionTone.Secondary,
            )
            SectionHeader(title = "Asignación", meta = "$assignedCount seleccionados")
            SupervisorCashierPicker(
                title = "Cajeros disponibles",
                cashiers = cashiers,
                assignedCashierIds = assignedCashierIds,
                onCashierToggle = onCashierToggle,
            )
            CompactActionButton(
                label = "Crear supervisor",
                onClick = onCreate,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Save,
                tone = ActionTone.Primary,
            )
            if (credentialShareText.isNotBlank()) {
                CompactActionButton(
                    label = "Compartir credencial",
                    onClick = onShareCredential,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Share,
                    tone = ActionTone.Secondary,
                )
            }
            return@CompactPanel
        }
        if (selectedView == SupervisorAdminView.CREDENTIALS) {
            selectedSupervisor?.let {
                CompactTextInput(
                    label = "Nueva clave manual",
                    value = passwordValue,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = Icons.Rounded.Lock,
                )
                CompactActionButton(
                    label = "Guardar clave",
                    onClick = onResetPassword,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Lock,
                    tone = ActionTone.Secondary,
                )
            }
            selectedSupervisor?.let { supervisor ->
                CompactActionButton(
                    label = if (supervisor.active) "Bloquear supervisor" else "Desbloquear supervisor",
                    onClick = onToggleSupervisorActive,
                    modifier = Modifier.fillMaxWidth(),
                    icon = if (supervisor.active) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    tone = if (supervisor.active) ActionTone.Danger else ActionTone.Success,
                )
            }
            if (credentialShareText.isNotBlank()) {
                CompactActionButton(
                    label = "Compartir credencial",
                    onClick = onShareCredential,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Share,
                    tone = ActionTone.Secondary,
                )
            }
            if (selectedSupervisor != null) {
                CompactActionButton(
                    label = "Eliminar supervisor",
                    onClick = onDeleteSupervisor,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Delete,
                    tone = ActionTone.Danger,
                )
            }
        }
        if (selectedView == SupervisorAdminView.GROUP) {
            CompactTextInput(
                label = "Comisión supervisor %",
                value = commissionValue,
                onValueChange = onCommissionChange,
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
                leadingIcon = Icons.Rounded.Percent,
            )
        }
        if (selectedView != SupervisorAdminView.CREATE) {
            selectedSupervisor?.let { supervisor ->
                val assigned = cashiers.filter { assignedCashierIds[it.id] == true }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = visual.colors.panelAlt,
                    border = androidx.compose.foundation.BorderStroke(1.dp, visual.colors.border),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        supervisorDetailRows(supervisor, assigned.size).forEachIndexed { index, row ->
                            Text(
                                text = if (index == 0) "${supervisor.displayName ?: supervisor.user} · $row" else row,
                                style = if (index == 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (index == 0) visual.colors.ink else visual.colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (assigned.isNotEmpty()) {
                            Text(
                                text = assigned.joinToString(", ") { it.displayName ?: it.user },
                                style = MaterialTheme.typography.bodySmall,
                                color = visual.colors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (selectedView == SupervisorAdminView.GROUP) {
            if (cashiers.isEmpty()) {
                CompactEmptyState(
                    message = "No hay cajeros libres o asignados a este supervisor.",
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SupervisorCashierPicker(
                    title = "Grupo de cajeros",
                    cashiers = cashiers,
                    assignedCashierIds = assignedCashierIds,
                    onCashierToggle = onCashierToggle,
                )
            }
            CompactActionButton(
                label = "Guardar grupo",
                onClick = onSaveAssignments,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Sync,
                tone = ActionTone.Secondary,
            )
        }
    }
}

@Composable
private fun SupervisorCashierPicker(
    title: String,
    cashiers: List<UserAccount>,
    assignedCashierIds: Map<String, Boolean>,
    onCashierToggle: (String, Boolean) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(SupervisorCashierFilter.ALL.name) }
    val filter = SupervisorCashierFilter.entries.firstOrNull { it.name == filterName } ?: SupervisorCashierFilter.ALL
    val visibleCashiers = remember(cashiers, assignedCashierIds, query, filter) {
        filterSupervisorCashierOptions(cashiers, assignedCashierIds, query, filter)
    }
    val assigned = cashiers.filter { assignedCashierIds[it.id] == true }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (cashiers.isEmpty()) {
            CompactEmptyState(
                message = "No hay cajeros disponibles para asignar.",
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        OperationalListHeader(title = title, meta = "${assigned.size}/${cashiers.size} asignados")
        CompactTextInput(
            label = "Buscar cajero",
            value = query,
            onValueChange = { query = it },
            placeholder = "Nombre, usuario o teléfono",
            leadingIcon = Icons.Rounded.ManageAccounts,
            modifier = Modifier.fillMaxWidth(),
        )
        CompactSegmentedSelector(
            options = supervisorCashierFilterOptions(),
            selectedId = filter.name,
            onSelected = { filterName = it },
            columns = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        CompactBulkToolbar(
            selectedCount = assigned.size,
            visibleCount = visibleCashiers.size,
            onSelectVisible = {
                visibleCashiers.forEach { cashier -> onCashierToggle(cashier.id, true) }
            },
            onClearSelection = {
                cashiers.forEach { cashier -> onCashierToggle(cashier.id, false) }
            },
            onApply = {
                cashiers.forEach { cashier -> onCashierToggle(cashier.id, false) }
                visibleCashiers.forEach { cashier -> onCashierToggle(cashier.id, true) }
            },
            applyLabel = "Solo visibles",
            modifier = Modifier.fillMaxWidth(),
        )
        if (visibleCashiers.isEmpty()) {
            CompactEmptyState(
                message = "No hay cajeros con ese filtro.",
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                visibleCashiers.take(60).forEach { cashier ->
                val selected = assignedCashierIds[cashier.id] == true
                    CompactSwitchRow(
                        title = cashier.displayName ?: cashier.user,
                        subtitle = cashier.user,
                        checked = selected,
                        onCheckedChange = { checked -> onCashierToggle(cashier.id, checked) },
                        tone = ActionTone.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (visibleCashiers.size > 60) {
                    Text(
                        text = "+${visibleCashiers.size - 60} cajeros más. Usa búsqueda para afinar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (assigned.isEmpty()) {
            Text(
                text = "Sin cajeros asignados.",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontWeight = FontWeight.Bold,
            )
        } else {
            val visibleAssigned = assigned.take(8)
            CompactAdaptiveGrid(
                itemCount = visibleAssigned.size,
                columns = if (visibleAssigned.size == 1) 1 else 2,
                modifier = Modifier.fillMaxWidth(),
            ) { index, itemModifier ->
                CompactStatusBadge(
                    label = visibleAssigned[index].displayName ?: visibleAssigned[index].user,
                    modifier = itemModifier.fillMaxWidth(),
                    tone = visual.colors.finance,
                )
            }
            if (assigned.size > visibleAssigned.size) {
                Text(
                    text = "+${assigned.size - visibleAssigned.size} cajeros mas asignados",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun CashierLimitsEditor(
    daySaleLimit: String,
    payoutLimit: String,
    payoutLabel: String,
    quinielaLimit: String,
    paleLimit: String,
    superPaleLimit: String,
    tripletaLimit: String,
    pick3StraightLimit: String,
    pick3BoxLimit: String,
    pick4StraightLimit: String,
    pick4BoxLimit: String,
    onDaySaleChange: (String) -> Unit,
    onPayoutChange: (String) -> Unit,
    onQuinielaChange: (String) -> Unit,
    onPaleChange: (String) -> Unit,
    onSuperPaleChange: (String) -> Unit,
    onTripletaChange: (String) -> Unit,
    onPick3StraightChange: (String) -> Unit,
    onPick3BoxChange: (String) -> Unit,
    onPick4StraightChange: (String) -> Unit,
    onPick4BoxChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = daySaleLimit,
            onValueChange = onDaySaleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Límite diario de venta") },
            supportingText = { Text("Máximo de dinero que este cajero puede vender en el día. 0 = sin tope.") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(rememberLotteryNetVisualSpec().sizes.panelRadius),
            colors = lotteryNetTextFieldColors(),
        )
        OutlinedTextField(
            value = payoutLimit,
            onValueChange = onPayoutChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(payoutLabel) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LimitInput("Quiniela venta diaria", quinielaLimit, onQuinielaChange, Modifier.weight(1f))
            LimitInput("Pale venta diaria", paleLimit, onPaleChange, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LimitInput("Super Pale venta diaria", superPaleLimit, onSuperPaleChange, Modifier.weight(1f))
            LimitInput("Tripleta venta diaria", tripletaLimit, onTripletaChange, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LimitInput("P3 Straight venta", pick3StraightLimit, onPick3StraightChange, Modifier.weight(1f))
            LimitInput("P3 Box venta", pick3BoxLimit, onPick3BoxChange, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LimitInput("P4 Straight venta", pick4StraightLimit, onPick4StraightChange, Modifier.weight(1f))
            LimitInput("P4 Box venta", pick4BoxLimit, onPick4BoxChange, Modifier.weight(1f))
        }
        CompactActionButton(cashierAdminSaveServerActionLabel(), onClick = onSave, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Save, active = true, tone = ActionTone.Primary)
    }
}

@Composable
private fun LimitInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactTextInput(
        label = label,
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        keyboardType = KeyboardType.Decimal,
    )
}

@Composable
private fun CashierModeAssignmentPanel(
    cashiers: List<UserAccount>,
    selectedCashierId: String,
    selectedMode: String,
    onCashierSelected: (String) -> Unit,
    onModeSelected: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAll: (String) -> Unit,
) {
    val selectedCashier = cashiers.firstOrNull { it.id == selectedCashierId } ?: cashiers.firstOrNull()
    val currentMode = normalizeCashierSystemModeOverride(selectedMode) ?: "lottery"
    var allCashiersMode by rememberSaveable { mutableStateOf("lottery") }
    val visual = rememberLotteryNetVisualSpec()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OperationalListHeader(
            title = "Modo de venta por cajero",
            meta = "Se guarda en servidor y aplica al entrar en cualquier equipo",
        )
        Text(
            text = "Aplicar a todos",
            style = MaterialTheme.typography.labelLarge,
            color = visual.colors.ink,
            fontWeight = FontWeight.Bold,
        )
        CashierSystemModePicker(
            selectedMode = allCashiersMode,
            enabled = cashiers.isNotEmpty(),
            onSelected = { allCashiersMode = it },
        )
        CompactActionButton(
            label = "Guardar en ${cashiers.size} cajeros",
            onClick = { onSaveAll(allCashiersMode) },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Sync,
            enabled = cashiers.isNotEmpty(),
            active = true,
            tone = ActionTone.Primary,
        )
        AccountsDropdown(
            title = "Cajero",
            selectedLabel = selectedCashier?.displayName ?: selectedCashier?.user ?: "Sin cajero",
            icon = Icons.Rounded.ManageAccounts,
        ) { dismiss ->
            cashiers.forEach { cashier ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = cashier.displayName ?: cashier.user,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onCashierSelected(cashier.id)
                        dismiss()
                    },
                )
            }
        }
        selectedCashier?.let { cashier ->
            CompactPanel(
                alt = true,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = cashier.user,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Actual: ${cashierSystemModeOverrideLabel(currentMode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
            }
        }
        CashierSystemModePicker(
            selectedMode = currentMode,
            enabled = selectedCashier != null,
            onSelected = onModeSelected,
        )
        CompactActionButton(
            label = cashierAdminSaveServerActionLabel(),
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Save,
            enabled = selectedCashier != null,
            active = true,
            tone = ActionTone.Primary,
        )
    }
}

@Composable
private fun AccountsDropdown(
    title: String,
    selectedLabel: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ((() -> Unit) -> Unit),
) {
    val visual = rememberLotteryNetVisualSpec()
    val dropdown = resolveFintechDropdownContract(visual.colors)
    val overflow = remember(visual.windowMode) { resolveOverflowLayoutContract(visual.windowMode) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true },
            shape = RoundedCornerShape(visual.sizes.panelRadius),
            color = dropdown.background,
            border = androidx.compose.foundation.BorderStroke(1.dp, dropdown.border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = dropdown.foreground)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = dropdown.foreground.copy(alpha = 0.86f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = dropdown.foreground,
                        fontWeight = dropdown.valueWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = dropdown.foreground)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(max = overflow.dropdownMaxWidth)
                .heightIn(max = overflow.dropdownMaxHeight),
        ) {
            content { expanded = false }
        }
    }
}

@Composable
private fun AccountsTabs(
    layout: UserAccountsLayoutContract,
    selectedTab: AccountsTab,
    totalCount: Int,
    blockedCount: Int,
    onSelect: (AccountsTab) -> Unit,
) {
    CompactAdaptiveGrid(itemCount = 2, columns = layout.tabsColumns) { index, itemModifier ->
        when (index) {
            0 -> CompactActionButton(
                label = "Cuentas $totalCount",
                onClick = { onSelect(AccountsTab.ALL) },
                modifier = itemModifier,
                active = selectedTab == AccountsTab.ALL,
                tone = if (selectedTab == AccountsTab.ALL) ActionTone.Primary else ActionTone.Secondary,
            )

            else -> CompactActionButton(
                label = "Bloqueados $blockedCount",
                onClick = { onSelect(AccountsTab.BLOCKED) },
                modifier = itemModifier,
                active = selectedTab == AccountsTab.BLOCKED,
                tone = if (selectedTab == AccountsTab.BLOCKED) ActionTone.Warning else ActionTone.Secondary,
            )
        }
    }
}

@Composable
private fun InfoStrip(text: String) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        alt = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.admin,
        )
    }
}

@Composable
private fun UserAccountCard(
    layout: UserAccountsLayoutContract,
    account: UserAccount,
    displayNameValue: String,
    balanceValue: String,
    commissionValue: String,
    recargaTxValue: String,
    canEdit: Boolean,
    compactReadOnlyHint: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onBalanceChange: (String) -> Unit,
    onCommissionChange: (String) -> Unit,
    onRecargaTxChange: (String) -> Unit,
    systemModeValue: String,
    onSystemModeChange: (String) -> Unit,
    cashierMetric: CashierAccountMetric?,
    performancePeriodLabel: String,
    onSave: () -> Unit,
    onToggleActive: (() -> Unit)?,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = layout.cardPaddingVerticalDp.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.displayName ?: account.user,
                        style = MaterialTheme.typography.titleMedium,
                        color = visual.colors.ink,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append(account.user)
                            append(" · ")
                            append(presentUserRoleLabel(account.role))
                            account.phone?.takeIf { it.isNotBlank() }?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                }
                CompactStatusBadge(
                    label = if (account.active) "Activo" else "Bloqueado",
                    tone = if (account.active) visual.colors.admin else MaterialTheme.colorScheme.error,
                )
            }
            cashierMetric?.let {
                CashierPerformanceSnapshot(
                    metric = it,
                    periodLabel = performancePeriodLabel,
                )
            }
            if (onToggleActive != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                CompactActionButton(
                        label = if (account.active) "Bloquear" else "Activar",
                        onClick = onToggleActive,
                        icon = if (account.active) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        tone = if (account.active) ActionTone.Danger else ActionTone.Success,
                    )
                }
            }
            if (account.role == UserRole.CASHIER) {
                OutlinedTextField(
                    value = displayNameValue,
                    onValueChange = onDisplayNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre del cajero") },
                    leadingIcon = { Icon(Icons.Rounded.ManageAccounts, contentDescription = null) },
                    enabled = canEdit,
                    singleLine = true,
                    placeholder = { Text(account.user) },
                )
            }
            OutlinedTextField(
                value = balanceValue,
                onValueChange = onBalanceChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Balance") },
                leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null) },
                enabled = canEdit,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = commissionValue,
                onValueChange = onCommissionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Comisión") },
                leadingIcon = { Icon(Icons.Rounded.Percent, contentDescription = null) },
                enabled = canEdit,
                singleLine = true,
                placeholder = { Text("10 = 10%") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (account.role == UserRole.CASHIER || recargaTxValue.isNotBlank()) {
                OutlinedTextField(
                    value = recargaTxValue,
                    onValueChange = onRecargaTxChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tope por recarga") },
                    leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    enabled = canEdit,
                    singleLine = true,
                    placeholder = { Text("Vacío = sin tope") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            if (account.role == UserRole.CASHIER) {
                CashierSystemModePicker(
                    selectedMode = systemModeValue,
                    enabled = canEdit,
                    onSelected = onSystemModeChange,
                )
            }
            if (canEdit) {
                CompactActionButton(cashierAccountSaveServerActionLabel(), onClick = onSave, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Save, active = true, tone = ActionTone.Primary)
            } else {
                if (compactReadOnlyHint) {
                    CompactStatusBadge("Solo lectura", tone = visual.colors.neutral)
                } else {
                    Text(
                        text = "Solo lectura para cajero.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun CashierSystemModePicker(
    selectedMode: String,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    val normalized = normalizeCashierSystemModeOverride(selectedMode) ?: "lottery"
    val options = listOf(
        "lottery" to "Solo Lotería",
        "pick" to "Solo Pick",
        "both" to "Lotería + Pick",
    )
    CompactSegmentedSelector(
        options = options.map { QuickFilterChip(it.first, it.second) },
        selectedId = normalized,
        onSelected = onSelected,
        columns = 3,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CashierPerformanceSnapshot(
    metric: CashierAccountMetric,
    periodLabel: String,
) {
    val visual = rememberLotteryNetVisualSpec()
    val resultTone = if (metric.isLoss) MaterialTheme.colorScheme.error else visual.colors.admin
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panel, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Rendimiento $periodLabel",
                style = MaterialTheme.typography.labelLarge,
                color = visual.colors.ink,
                fontWeight = FontWeight.Bold,
            )
            CompactStatusBadge(
                label = if (metric.isLoss) "Pérdida" else "Positivo",
                tone = resultTone,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PerformanceValue("Ventas", formatMoney(metric.ventas), visual.colors.sale, Modifier.weight(1f))
            PerformanceValue("Comisión", formatMoney(metric.comision), visual.colors.admin, Modifier.weight(1f))
            PerformanceValue(if (metric.isLoss) "Pérdida" else "Neto", formatMoney(metric.neto), resultTone, Modifier.weight(1f))
        }
        PerformanceBar("Ventas", metric.salesRatio, visual.colors.sale)
        PerformanceBar("Comisión", metric.commissionRatio, visual.colors.admin)
        PerformanceBar(if (metric.isLoss) "Pérdida" else "Neto", metric.resultRatio, resultTone)
    }
}

@Composable
private fun PerformanceValue(
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = tone,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PerformanceBar(
    label: String,
    ratio: Float,
    tone: Color,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(58.dp),
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .background(tone.copy(alpha = 0.12f), RoundedCornerShape(99.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(7.dp)
                    .background(tone, RoundedCornerShape(99.dp)),
            )
        }
    }
}

internal fun presentUserRoleLabel(role: UserRole): String {
    return when (role) {
        UserRole.ADMIN -> "Admin"
        UserRole.SUPERVISOR -> "Supervisor"
        UserRole.CASHIER -> "Cajero"
        UserRole.MASTER -> "Master"
        else -> "Usuario"
    }
}

private fun canToggleAccount(session: ActiveSession, account: UserAccount): Boolean {
    if (session.role != UserRole.ADMIN) return false
    if (account.role != UserRole.CASHIER) return false
    if (account.id.equals(session.userId, ignoreCase = true) || account.user.equals(session.username, ignoreCase = true)) return false
    return true
}

private fun buildAccountsForSession(
    session: ActiveSession,
    usersRepository: LocalUsersRepository,
): List<UserAccount> {
    val admins = usersRepository.getAdmins()
    val supervisors = usersRepository.getSupervisors()
    val cashiers = usersRepository.getCashiers()
    return when (session.role) {
        UserRole.ADMIN -> {
            val selfAdmin = admins.firstOrNull {
                it.id.equals(session.userId, ignoreCase = true) || it.user.equals(session.username, ignoreCase = true)
            }
            buildList {
                selfAdmin?.let(::add)
                addAll(
                    usersRepository.getSupervisors().filter { supervisor ->
                        supervisor.adminId.equals(session.userId, ignoreCase = true) ||
                            supervisor.adminUser.equals(session.username, ignoreCase = true) ||
                            (supervisor.banca != null && supervisor.banca == session.banca)
                    },
                )
                addAll(
                    sortCashierAccountsNatural(cashiers.filter { cashier ->
                        cashier.adminId.equals(session.userId, ignoreCase = true) ||
                            cashier.adminUser.equals(session.username, ignoreCase = true) ||
                            (cashier.banca != null && cashier.banca == session.banca)
                    }),
                )
            }.distinctBy { it.id }
        }
        UserRole.CASHIER -> {
            listOfNotNull(
                cashiers.firstOrNull {
                    it.id.equals(session.userId, ignoreCase = true) || it.user.equals(session.username, ignoreCase = true)
                },
                admins.firstOrNull {
                    it.id.equals(session.adminId, ignoreCase = true) || it.user.equals(session.adminUser, ignoreCase = true)
                },
            ).distinctBy { it.id }
        }
        UserRole.SUPERVISOR -> {
            buildList {
                supervisors.firstOrNull {
                    it.id.equals(session.userId, ignoreCase = true) || it.user.equals(session.username, ignoreCase = true)
                }?.let(::add)
                addAll(
                    sortCashierAccountsNatural(cashiers.filter { cashier ->
                        cashier.supervisorIds.any { it.equals(session.userId, ignoreCase = true) } ||
                            cashier.supervisorUsers.any { it.equals(session.username, ignoreCase = true) }
                    }),
                )
            }.distinctBy { it.id }
        }
        else -> emptyList()
    }
}

private fun sanitizeDecimal(value: String): String {
    var dotSeen = false
    return buildString {
        value.forEach { char ->
            when {
                char.isDigit() -> append(char)
                char == '.' && !dotSeen -> {
                    dotSeen = true
                    append(char)
                }
            }
        }
    }
}

private fun sanitizeInteger(value: String): String =
    value.filter(Char::isDigit)

private fun formatPrizePayoutInputs(config: PrizeTableConfig): Map<String, String> {
    val normalized = config.normalizedPrizeTableConfig()
    return mapOf(
        "q1" to normalized.q1.toString(),
        "q2" to normalized.q2.toString(),
        "q3" to normalized.q3.toString(),
        "pale12" to normalized.pale12.toString(),
        "pale13" to normalized.pale13.toString(),
        "pale23" to normalized.pale23.toString(),
        "tripleta3" to normalized.tripleta3.toString(),
        "tripleta2" to normalized.tripleta2.toString(),
        "superPale" to normalized.superPale.toString(),
        "pick3Straight" to normalized.pick3Straight.toString(),
        "pick3Box" to normalized.pick3Box3.toString(),
        "pick4Straight" to normalized.pick4Straight.toString(),
        "pick4Box" to normalized.pick4Box4.toString(),
    )
}

internal fun prizeConfigFromInputs(values: Map<String, String>): PrizeTableConfig {
    fun intValue(key: String, fallback: Int): Int = values[key]?.toIntOrNull()?.coerceAtLeast(1) ?: fallback
    val base = PrizeTableConfig()
    val pick3Box = intValue("pick3Box", intValue("pick3Box3", base.pick3Box3))
    val pick4Box = intValue("pick4Box", intValue("pick4Box4", base.pick4Box4))
    return PrizeTableConfig(
        q1 = intValue("q1", base.q1),
        q2 = intValue("q2", base.q2),
        q3 = intValue("q3", base.q3),
        pale = intValue("pale12", base.pale),
        pale12 = intValue("pale12", base.pale12),
        pale13 = intValue("pale13", base.pale13),
        pale23 = intValue("pale23", base.pale23),
        tripleta = intValue("tripleta3", base.tripleta),
        tripleta3 = intValue("tripleta3", base.tripleta3),
        tripleta2 = intValue("tripleta2", base.tripleta2),
        superPale = intValue("superPale", base.superPale),
        pick3Straight = intValue("pick3Straight", base.pick3Straight),
        pick3Box3 = pick3Box,
        pick3Box6 = pick3Box,
        pick4Straight = intValue("pick4Straight", base.pick4Straight),
        pick4Box4 = pick4Box,
        pick4Box6 = pick4Box,
        pick4Box12 = pick4Box,
        pick4Box24 = pick4Box,
        pick3BackPair = base.pick3BackPair,
        pick4BackPair = base.pick4BackPair,
    ).normalizedPrizeTableConfig()
}

internal fun normalizeCommission(value: Double): Double {
    var normalized = value
    if (normalized > 1.0 && normalized <= 100.0) normalized /= 100.0
    if (normalized < 0.0) normalized = 0.0
    if (normalized > 1.0) normalized = 1.0
    return normalized
}

private fun formatBalanceInput(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.2f", value)
}

internal fun formatCommissionInput(value: Double?): String {
    return value?.let {
        val percent = it * 100
        if (percent % 1.0 == 0.0) percent.toInt().toString() else String.format(Locale.US, "%.2f", percent)
    }.orEmpty()
}

internal fun formatCommissionLabel(value: Double?): String {
    return value?.let {
        val percent = it * 100
        if (percent % 1.0 == 0.0) "${percent.toInt()}%" else "${String.format(Locale.US, "%.2f", percent)}%"
    } ?: "sin dato"
}

private fun formatMoney(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)

private fun formatRecargaTxInput(value: Double?): String {
    if (value == null || value <= 0.0) return ""
    return formatBalanceInput(value)
}

private fun accountHasTxLimit(value: String): Boolean = value.isNotBlank() && value != "0"

private fun noLimitSalesInputs(): CashierSalesLimitInputs = CashierSalesLimitInputs(
    daySale = 0.0,
    payout = 0.0,
    quiniela = 0.0,
    pale = 0.0,
    superPale = 0.0,
    tripleta = 0.0,
    pick3Straight = 0.0,
    pick3Box = 0.0,
    pick4Straight = 0.0,
    pick4Box = 0.0,
)
