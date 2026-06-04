package com.lotterynet.pro.ui.shell

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import android.content.Context
import android.content.ContextWrapper
import com.lotterynet.pro.ui.admin.AdminAuditActivity
import com.lotterynet.pro.ui.admin.AdminConfigActivity
import com.lotterynet.pro.ui.admin.AdminLimitsActivity
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.format.formatWholeMoney
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.operations.filterCashiersForSession
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalMasterConfigRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalPosModeRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.decodeMasterSportsbookSettings
import com.lotterynet.pro.core.storage.sportsbookRemoteKey
import com.lotterynet.pro.core.storage.toFeatureConfig
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.sync.NativeRechargeCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.update.OtaCheckResult
import com.lotterynet.pro.core.update.UpdateRepository
import com.lotterynet.pro.ui.admin.AdminMonitorActivity
import com.lotterynet.pro.ui.finance.FinanceActivity
import com.lotterynet.pro.ui.login.LoginActivity
import com.lotterynet.pro.ui.master.MasterCreateBankActivity
import com.lotterynet.pro.ui.master.MasterDashboardActivity
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.startSafeNativeDestination
import com.lotterynet.pro.ui.printer.PrinterActivity
import com.lotterynet.pro.ui.report.OperationalReportActivity
import com.lotterynet.pro.ui.recharge.canShowRechargeAccess
import com.lotterynet.pro.ui.recharge.RecargasActivity
import com.lotterynet.pro.ui.recharge.resolveRechargeOwnerAccount
import com.lotterynet.pro.ui.results.ResultsActivity
import com.lotterynet.pro.ui.sales.SalesActivity
import com.lotterynet.pro.ui.sportsbook.SportsbookActivity
import com.lotterynet.pro.ui.tickets.TicketLookupActivity
import com.lotterynet.pro.ui.tickets.TicketSummaryActivity
import com.lotterynet.pro.ui.update.UpdatePromptActivity
import com.lotterynet.pro.ui.users.UserAccountsActivity
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.BrandLogo
import com.lotterynet.pro.ui.common.CompactAdaptiveGrid
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.MetricStrip
import com.lotterynet.pro.ui.common.MetricStripItem
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.openSafeResults
import com.lotterynet.pro.ui.common.openSafeSales
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

class ShellActivity : AppCompatActivity() {
    private lateinit var sessionRepository: LocalSessionRepository
    private var pendingNativeLoginUser: String? = null
    private var pendingNativeLoginPassword: String? = null
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionRepository = LocalSessionRepository(this)
        pendingNativeLoginUser = intent?.getStringExtra("native_login_user")
        pendingNativeLoginPassword = intent?.getStringExtra("native_login_password")
        val session = sessionRepository.getActiveSession()
        if (session == null) {
            openLoginAndFinish()
            return
        }
        requestStartupRuntimePermissions()
        val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date())
        val rechargeAccessVisible = canShowRechargeAccess(
            resolveRechargeOwnerAccount(session, LocalUsersRepository(this)),
        )
        val sportsbookSettingsRepository = LocalMasterConfigRepository(this)
        val initialSportsbookAccessVisible = resolveSportsbookAccessVisible(
            session = session,
            settingsRepository = sportsbookSettingsRepository,
        )
        setContent {
            com.lotterynet.pro.ui.theme.LotteryNetComposeTheme {
                var sportsbookAccessVisible by remember(session.userId) {
                    mutableStateOf(initialSportsbookAccessVisible)
                }
                var dashboardSnapshot by remember(session.userId, dayKey) {
                    mutableStateOf(ShellDashboardSnapshot.empty())
                }
                var dashboardRefreshTick by remember(session.userId, dayKey) {
                    mutableStateOf(0L)
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, session.userId, dayKey) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (
                            event == Lifecycle.Event.ON_RESUME &&
                            shouldRefreshShellDashboardOnResume(session.role)
                        ) {
                            dashboardRefreshTick = System.currentTimeMillis()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }
                LaunchedEffect(session.userId, dayKey, dashboardRefreshTick) {
                    dashboardSnapshot = if (shouldLoadShellBusinessSnapshot(session.role)) {
                        withContext(Dispatchers.IO) {
                            buildShellDashboardSnapshot(session, dayKey)
                        }
                    } else {
                        ShellDashboardSnapshot.empty()
                    }
                }
                LaunchedEffect(session.userId, session.username) {
                    withContext(Dispatchers.IO) {
                        LocalUsersRepository(applicationContext).touchSession(session)
                    }
                }
                LaunchedEffect(session.userId, session.role) {
                    val remoteSettings = withContext(Dispatchers.IO) {
                        runCatching {
                            SupabaseMasterConfigRemoteStore()
                                .fetchValue(sportsbookRemoteKey())
                                ?.toString()
                                ?.let(::decodeMasterSportsbookSettings)
                        }.getOrNull()
                    }
                    if (remoteSettings != null) {
                        sportsbookSettingsRepository.saveSportsbookSettings(remoteSettings)
                        sportsbookAccessVisible = resolveSportsbookAccessVisible(
                            session = session,
                            settingsRepository = sportsbookSettingsRepository,
                        )
                    }
                }
                LaunchedEffect(session.adminId, session.banca) {
                    val ownerKey = session.adminId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
                    delay(350)
                    hydrateShellDataAfterFirstFrame(ownerKey, session.banca) {
                        dashboardRefreshTick = System.currentTimeMillis()
                    }
                }
                ShellRoute(
                    session = session,
                    dayKey = dayKey,
                    recentTickets = dashboardSnapshot.recentTickets,
                    salesTotal = dashboardSnapshot.salesTotal,
                    cashTotal = dashboardSnapshot.cashTotal,
                    pendingTotal = dashboardSnapshot.pendingTotal,
                    assignedCashiersCount = dashboardSnapshot.assignedCashiersCount,
                    rechargeAccessVisible = rechargeAccessVisible,
                    sportsbookAccessVisible = sportsbookAccessVisible,
                    onOpenNativeSales = {
                        openSafeSales(this, session.role)
                    },
                    onOpenNativeResults = {
                        openSafeResults(this, session.role)
                    },
                    onOpenNativeRecharge = {
                        startSafeNativeDestination(this, session.role, NativeDestination.RECHARGE)
                    },
                    onOpenNativeSportsbook = {
                        startSafeNativeDestination(this, session.role, NativeDestination.SPORTSBOOK)
                    },
                    onOpenTicketSummary = {
                        startSafeNativeDestination(this, session.role, NativeDestination.TICKET_SUMMARY)
                    },
                    onOpenTicketDetail = {
                        startSafeNativeDestination(this, session.role, NativeDestination.TICKET_SUMMARY)
                    },
                    onOpenTicketLookup = { mode ->
                        startSafeNativeDestination(this, session.role, NativeDestination.TICKET_LOOKUP, ticketLookupMode = mode)
                    },
                    onOpenNativeUsers = {
                        startSafeNativeDestination(this, session.role, NativeDestination.USER_ACCOUNTS)
                    },
                    onOpenNativeSupervisors = {
                        if (session.role == UserRole.ADMIN) {
                            startActivity(
                                Intent(this, UserAccountsActivity::class.java).putExtra(
                                    UserAccountsActivity.EXTRA_INITIAL_ADMIN_SECTION,
                                    "SUPERVISORS",
                                ),
                            )
                        }
                    },
                    onOpenNativeMonitor = {
                        startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_MONITOR)
                    },
                    onOpenNativeAdminLimits = {
                        startSafeNativeDestination(this, session.role, NativeDestination.USER_ACCOUNTS)
                    },
                    onOpenNativeAlerts = {
                        startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_ALERTS)
                    },
                    onOpenNativeAudit = {
                        startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_AUDIT)
                    },
                    onOpenNativeWinners = {
                        startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_WINNERS)
                    },
                    onOpenNativeConfig = {
                        startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_CONFIG)
                    },
                    onOpenNativePrinter = {
                        startSafeNativeDestination(this, session.role, NativeDestination.PRINTER)
                    },
                    onOpenNativeFinance = {
                        startSafeNativeDestination(this, session.role, NativeDestination.FINANCE)
                    },
                    onOpenNativeReport = {
                        startSafeNativeDestination(this, session.role, NativeDestination.OPERATIONAL_REPORT)
                    },
                    onOpenNativeMasterDashboard = {
                        startSafeNativeDestination(this, session.role, NativeDestination.MASTER_DASHBOARD)
                    },
                    onOpenNativeMasterCreate = {
                        startSafeNativeDestination(this, session.role, NativeDestination.MASTER_CREATE_BANK)
                    },
                    onLogout = {
                        sessionRepository.saveActiveSession(null)
                        sessionRepository.saveSessionSnapshot(null)
                        openLoginAndFinish()
                    },
                )
            }
        }
    }

    private fun hydrateShellDataAfterFirstFrame(
        ownerKey: String,
        banca: String?,
        onTicketsHydrated: () -> Unit = {},
    ) {
        val appContext = applicationContext
        thread(name = "shell-ticket-hydrate") {
            NativeTicketCloudSyncCoordinator(
                salesRepository = LocalSalesRepository(appContext),
                queueRepository = NativeTicketSyncQueueRepository(appContext),
            ).hydrateOwner(ownerKey, banca)
            runOnUiThread(onTicketsHydrated)
        }
        thread(name = "shell-recharge-hydrate") {
            NativeRechargeCloudSyncCoordinator(LocalRechargeRepository(appContext)).hydrateOwner(ownerKey)
        }
    }

    private fun buildShellDashboardSnapshot(
        session: ActiveSession,
        dayKey: String,
    ): ShellDashboardSnapshot {
        val salesRepositoryForShell = LocalSalesRepository(this)
        val usersRepositoryForShell = LocalUsersRepository(this)
        val financeRepository = LocalFinanceRepository(
            salesRepository = salesRepositoryForShell,
            rechargeRepository = LocalRechargeRepository(this),
            usersRepository = usersRepositoryForShell,
        )
        val financeSummary = financeRepository.getScopedDaySummary(
            dayKey = dayKey,
            scope = financeRepository.resolveScope(session),
        )
        val scopedTodayTickets = filterTicketsForOperationalScope(
            session = session,
            tickets = salesRepositoryForShell.getTicketsForDay(dayKey),
            cashiers = usersRepositoryForShell.getCashiers(),
        )
        val assignedCashiersCount = if (session.role == UserRole.SUPERVISOR) {
            filterCashiersForSession(session, usersRepositoryForShell.getCashiers()).count { it.active }
        } else {
            0
        }
        val recentTickets = scopedTodayTickets
            .sortedByDescending { it.createdAtEpochMs }
            .take(6)
        val visibleSalesTotal = if (session.role == UserRole.CASHIER) {
            scopedTodayTickets.filterNot { it.status.equals("voided", true) || it.status.equals("invalid", true) }.sumOf { it.total }
        } else {
            financeSummary.ventas
        }
        val visiblePendingTotal = if (session.role == UserRole.CASHIER) {
            scopedTodayTickets.filter { it.status.equals("winner", true) }.sumOf { it.totalPrize.coerceAtLeast(0.0) }
        } else {
            financeSummary.premiosPendientes
        }
        return ShellDashboardSnapshot(
            recentTickets = recentTickets,
            salesTotal = visibleSalesTotal,
            cashTotal = resolveShellCashTotalForRole(
                role = session.role,
                visibleSalesTotal = visibleSalesTotal,
                scopedCajaDisponible = financeSummary.cajaDisponible,
            ),
            pendingTotal = visiblePendingTotal,
            assignedCashiersCount = assignedCashiersCount,
        )
    }

    private fun openLoginAndFinish() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun requestStartupRuntimePermissions() {
        val missing = resolveStartupRuntimePermissions(Build.VERSION.SDK_INT).filter { permission ->
            checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    companion object {
        const val EXTRA_FORCE_MENU = "force_menu"
    }
}

internal data class ShellMenuLayoutContract(
    val showActionDescriptions: Boolean,
    val showDayLabel: Boolean,
    val iconSizeDp: Int,
    val actionPaddingDp: Int,
    val useDenseRows: Boolean,
    val showLargeCards: Boolean,
    val collapseSecondaryActions: Boolean,
)

internal enum class ShellScrollStrategy {
    LAZY_SECTIONS,
}

internal data class LogoutClearPolicy(
    val clearActiveSession: Boolean,
    val clearSessionSnapshot: Boolean,
    val clearSavedLogin: Boolean,
)

internal enum class ShellStartupWork {
    LOAD_SESSION,
    TOUCH_LAST_SEEN,
    LOAD_DASHBOARD_METRICS,
    HYDRATE_REMOTE_DATA,
}

internal data class ShellStartupPlan(
    val firstFrameWork: Set<ShellStartupWork>,
    val afterFirstFrameWork: Set<ShellStartupWork>,
)

internal data class ShellDashboardSnapshot(
    val recentTickets: List<TicketRecord>,
    val salesTotal: Double,
    val cashTotal: Double,
    val pendingTotal: Double,
    val assignedCashiersCount: Int = 0,
) {
    companion object {
        fun empty(): ShellDashboardSnapshot {
            return ShellDashboardSnapshot(
                recentTickets = emptyList(),
                salesTotal = 0.0,
                cashTotal = 0.0,
                pendingTotal = 0.0,
                assignedCashiersCount = 0,
            )
        }
    }
}

internal data class ShellButtonRoute(
    val title: String,
    val activityClassName: String,
    val lookupMode: String? = null,
)

internal fun resolveShellStartupPlan(): ShellStartupPlan {
    return ShellStartupPlan(
        firstFrameWork = setOf(ShellStartupWork.LOAD_SESSION),
        afterFirstFrameWork = setOf(
            ShellStartupWork.TOUCH_LAST_SEEN,
            ShellStartupWork.LOAD_DASHBOARD_METRICS,
            ShellStartupWork.HYDRATE_REMOTE_DATA,
        ),
    )
}

internal fun resolveShellScrollStrategy(): ShellScrollStrategy = ShellScrollStrategy.LAZY_SECTIONS

internal fun resolveShellButtonRoutes(
    role: UserRole,
    rechargeVisible: Boolean = true,
    sportsbookVisible: Boolean = false,
    manualPosModeEnabled: Boolean = false,
): List<ShellButtonRoute> {
    if (role == UserRole.MASTER) {
        return listOf(
            ShellButtonRoute("Panel master", MasterDashboardActivity::class.java.name),
            ShellButtonRoute("Finanzas", FinanceActivity::class.java.name),
            ShellButtonRoute("Deportes", SportsbookActivity::class.java.name),
            ShellButtonRoute("Crear banca", MasterCreateBankActivity::class.java.name),
            ShellButtonRoute("Auditoría", AdminAuditActivity::class.java.name),
        )
    }
    if (role == UserRole.SUPERVISOR) {
        return buildList {
            add(ShellButtonRoute("Mis cajeros", AdminMonitorActivity::class.java.name))
            add(ShellButtonRoute("Monitoreo", AdminMonitorActivity::class.java.name))
            add(ShellButtonRoute("Finanzas", FinanceActivity::class.java.name))
            add(ShellButtonRoute("Reporte", OperationalReportActivity::class.java.name))
            add(ShellButtonRoute("Tickets", TicketSummaryActivity::class.java.name))
            add(ShellButtonRoute("Resultados", ResultsActivity::class.java.name))
            if (sportsbookVisible) add(ShellButtonRoute("Deportes", SportsbookActivity::class.java.name))
            add(ShellButtonRoute("Impresora", PrinterActivity::class.java.name))
        }
    }
    return buildList {
        add(ShellButtonRoute("Vender", SalesActivity::class.java.name))
        add(ShellButtonRoute("Tickets", TicketSummaryActivity::class.java.name))
        add(ShellButtonRoute("Resultados", ResultsActivity::class.java.name))
        if (rechargeVisible) {
            add(ShellButtonRoute("Recargas", RecargasActivity::class.java.name))
        }
        if (sportsbookVisible) {
            add(ShellButtonRoute("Deportes", SportsbookActivity::class.java.name))
        }
        add(ShellButtonRoute("Repetir ticket", TicketLookupActivity::class.java.name, lookupMode = "duplicar"))
        add(ShellButtonRoute("Cuadre", FinanceActivity::class.java.name))
        add(ShellButtonRoute("Reporte", OperationalReportActivity::class.java.name))
        if (role != UserRole.CASHIER) {
            if (role != UserRole.SUPERVISOR) {
                add(ShellButtonRoute("Cobros", TicketLookupActivity::class.java.name, lookupMode = "pagar"))
            }
            if (role == UserRole.ADMIN) {
                add(ShellButtonRoute("Eliminar Ticket", TicketLookupActivity::class.java.name, lookupMode = "anular"))
            }
        }
        add(ShellButtonRoute("Impresora", PrinterActivity::class.java.name))
        if (role == UserRole.ADMIN || role == UserRole.SUPERVISOR) {
            add(ShellButtonRoute("Monitoreo", AdminMonitorActivity::class.java.name))
        }
        if (role == UserRole.ADMIN) {
            add(ShellButtonRoute("Supervisor", UserAccountsActivity::class.java.name))
            add(ShellButtonRoute("Límites", AdminLimitsActivity::class.java.name))
            add(ShellButtonRoute("Sistema", AdminConfigActivity::class.java.name))
        }
        if (role == UserRole.CASHIER) {
            add(ShellButtonRoute("Modo POS", ShellActivity::class.java.name))
        }
    }
}

internal fun resolveShellRolePriorityActionTitles(
    role: UserRole,
    rechargeVisible: Boolean = true,
    sportsbookVisible: Boolean = false,
): List<String> {
    return when (role) {
        UserRole.MASTER -> listOf("Panel master", "Deportes", "Crear banca", "Auditoría")
        UserRole.SUPERVISOR -> buildList {
            addAll(listOf("Mis cajeros", "Monitoreo", "Reporte", "Tickets", "Resultados", "Finanzas"))
            if (sportsbookVisible) add("Deportes")
        }
        UserRole.CASHIER -> buildList {
            addAll(listOf("Vender", "Tickets", "Resultados"))
            if (rechargeVisible) add("Recargas")
            if (sportsbookVisible) add("Deportes")
            addAll(listOf("Repetir ticket", "Cuadre", "Reporte", "Impresora"))
        }
        else -> buildList {
            addAll(listOf("Vender", "Tickets", "Resultados"))
            if (sportsbookVisible) add("Deportes")
            addAll(listOf("Supervisor", "Límites", "Monitoreo"))
        }
    }
}

internal fun shouldLoadShellBusinessSnapshot(role: UserRole): Boolean {
    return role == UserRole.ADMIN || role == UserRole.SUPERVISOR || role == UserRole.CASHIER
}

internal fun shouldRefreshShellDashboardOnResume(role: UserRole): Boolean {
    return shouldLoadShellBusinessSnapshot(role)
}

internal fun resolveSportsbookAccessVisible(
    session: ActiveSession,
    settingsRepository: LocalMasterConfigRepository,
): Boolean {
    if (session.role == UserRole.MASTER) return true
    return settingsRepository.getSportsbookSettings()
        .toFeatureConfig()
        .canOpen(
            role = session.role,
            actorKey = session.userId.ifBlank { session.username },
            adminKey = session.adminId ?: session.adminUser,
        )
}

internal fun resolveShellCashTotalForRole(
    role: UserRole,
    visibleSalesTotal: Double,
    scopedCajaDisponible: Double,
): Double {
    return if (role == UserRole.CASHIER) scopedCajaDisponible else scopedCajaDisponible
}

internal fun resolveShellMenuLayout(windowMode: LotteryNetWindowMode): ShellMenuLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> ShellMenuLayoutContract(
            showActionDescriptions = false,
            showDayLabel = false,
            iconSizeDp = 28,
            actionPaddingDp = 6,
            useDenseRows = true,
            showLargeCards = false,
            collapseSecondaryActions = true,
        )

        LotteryNetWindowMode.POS -> ShellMenuLayoutContract(
            showActionDescriptions = false,
            showDayLabel = false,
            iconSizeDp = 30,
            actionPaddingDp = 7,
            useDenseRows = true,
            showLargeCards = false,
            collapseSecondaryActions = true,
        )

        else -> ShellMenuLayoutContract(
            showActionDescriptions = true,
            showDayLabel = true,
            iconSizeDp = 34,
            actionPaddingDp = 9,
            useDenseRows = false,
            showLargeCards = true,
            collapseSecondaryActions = false,
        )
    }
}

internal fun resolveLogoutClearPolicy(): LogoutClearPolicy {
    return LogoutClearPolicy(
        clearActiveSession = true,
        clearSessionSnapshot = true,
        clearSavedLogin = false,
    )
}

internal fun resolveShellMenuSectionTitles(
    role: UserRole,
    manualPosModeEnabled: Boolean = false,
): List<String> {
    return when (role) {
        UserRole.MASTER -> listOf("Operación master", "Sesión")
        UserRole.SUPERVISOR -> listOf("Supervisión", "Consulta", "Soporte")
        UserRole.CASHIER -> listOf("Operación", "Caja", "Sistema")
        else -> listOf("Operación", "Caja", "Administración", "Sistema")
    }
}

internal fun resolveShellMenuActionTitles(
    role: UserRole,
    rechargeVisible: Boolean = true,
    sportsbookVisible: Boolean = false,
    manualPosModeEnabled: Boolean = false,
): List<String> {
    return when (role) {
        UserRole.CASHIER -> buildList {
            addAll(listOf("Vender", "Tickets", "Resultados"))
            if (rechargeVisible) add("Recargas")
            if (sportsbookVisible) add("Deportes")
            addAll(listOf("Repetir ticket", "Cuadre", "Reporte", "Impresora", "Actualizar sistema", "Cerrar Sesión"))
            add(size - 1, "Modo POS")
        }
        UserRole.SUPERVISOR -> buildList {
            addAll(listOf("Mis cajeros", "Monitoreo", "Reporte", "Tickets", "Resultados", "Finanzas"))
            if (sportsbookVisible) add("Deportes")
            addAll(listOf("Impresora", "Actualizar sistema", "Cerrar Sesión"))
        }
        UserRole.MASTER -> listOf("Panel master", "Deportes", "Crear banca", "Auditoría", "Actualizar sistema", "Cerrar sesión")
        else -> buildList {
            addAll(listOf("Vender", "Tickets", "Resultados"))
            if (rechargeVisible) add("Recargas")
            if (sportsbookVisible) add("Deportes")
            addAll(
                listOf(
                    "Repetir ticket",
                    "Cuadre",
                    "Reporte",
                    "Cobros",
                    "Eliminar Ticket",
                    "Supervisor",
                    "Límites",
                    "Monitoreo",
                    "Impresora",
                    "Sistema",
                    "Actualizar sistema",
                    "Cerrar Sesión",
                ),
            )
        }
    }
}

internal fun resolveShellCashboxActionTitles(role: UserRole): List<String> {
    return when (role) {
        UserRole.CASHIER -> listOf("Cuadre", "Reporte", "Impresora")
        UserRole.ADMIN -> listOf("Cuadre", "Reporte", "Cobros", "Eliminar Ticket", "Impresora")
        UserRole.SUPERVISOR -> listOf("Reporte", "Monitoreo", "Impresora")
        else -> emptyList()
    }
}

internal fun resolveShellRoleBadgeLabel(role: UserRole): String {
    return when (role) {
        UserRole.ADMIN -> "Admin"
        UserRole.SUPERVISOR -> "Supervisor"
        UserRole.CASHIER -> "Cajero"
        UserRole.MASTER -> "Master"
        UserRole.UNKNOWN -> "Sin rol"
    }
}

internal fun resolveShellHeaderMetricLabels(role: UserRole): List<String> {
    return if (role == UserRole.SUPERVISOR) {
        listOf("Cajeros", "Venta", "Caja", "Pend.")
    } else {
        listOf("Venta", "Caja", "Pend.")
    }
}

internal fun resolveStartupRuntimePermissions(sdkInt: Int): List<String> {
    return buildList {
        add(Manifest.permission.CAMERA)
        if (sdkInt >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

internal fun resolvePermissionStatusMessage(missingPermissions: List<String>): String {
    if (missingPermissions.isEmpty()) return "Listo para imprimir y escanear"
    val labels = buildList {
        if (missingPermissions.contains(Manifest.permission.BLUETOOTH_CONNECT)) {
            add("Permiso Bluetooth pendiente")
        }
        if (missingPermissions.contains(Manifest.permission.CAMERA)) {
            add("Permiso cámara pendiente")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && missingPermissions.contains(Manifest.permission.POST_NOTIFICATIONS)) {
            add("Permiso notificaciones pendiente")
        }
    }
    return labels.ifEmpty { listOf("Permisos pendientes") }.joinToString(" · ")
}

@Composable
private fun ShellRoute(
    session: ActiveSession,
    dayKey: String,
    recentTickets: List<TicketRecord>,
    salesTotal: Double,
    cashTotal: Double,
    pendingTotal: Double,
    assignedCashiersCount: Int,
    rechargeAccessVisible: Boolean,
    sportsbookAccessVisible: Boolean,
    onOpenNativeSales: () -> Unit,
    onOpenNativeResults: () -> Unit,
    onOpenNativeRecharge: () -> Unit,
    onOpenNativeSportsbook: () -> Unit,
    onOpenTicketSummary: () -> Unit,
    onOpenTicketDetail: () -> Unit,
    onOpenTicketLookup: (String) -> Unit,
    onOpenNativeUsers: () -> Unit,
    onOpenNativeSupervisors: () -> Unit,
    onOpenNativeMonitor: () -> Unit,
    onOpenNativeAdminLimits: () -> Unit,
    onOpenNativeAlerts: () -> Unit,
    onOpenNativeAudit: () -> Unit,
    onOpenNativeWinners: () -> Unit,
    onOpenNativeConfig: () -> Unit,
    onOpenNativePrinter: () -> Unit,
    onOpenNativeFinance: () -> Unit,
    onOpenNativeReport: () -> Unit,
    onOpenNativeMasterDashboard: () -> Unit,
    onOpenNativeMasterCreate: () -> Unit,
    onLogout: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var checkingSystemUpdate by rememberSaveable { mutableStateOf(false) }
    val onCheckSystemUpdate = {
        if (!checkingSystemUpdate) {
            checkingSystemUpdate = true
            Toast.makeText(context, "Revisando actualizacion...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                val result = UpdateRepository(context.applicationContext).checkForUpdate(
                    packageName = context.packageName,
                    session = session,
                    forceNetwork = true,
                )
                checkingSystemUpdate = false
                val updateInfo = when (result) {
                    is OtaCheckResult.Success -> result.info.takeIf { it.shouldInstall }
                    is OtaCheckResult.Offline -> result.cachedInfo?.takeIf { it.shouldInstall }
                    is OtaCheckResult.Error -> result.cachedInfo?.takeIf { it.shouldInstall }
                }
                if (updateInfo != null) {
                    context.startActivity(
                        Intent(context, UpdatePromptActivity::class.java).apply {
                            putExtra(UpdatePromptActivity.EXTRA_UPDATE_JSON, updateInfo.toJson().toString())
                        },
                    )
                } else {
                    val message = when (result) {
                        is OtaCheckResult.Success -> "Sistema al dia"
                        is OtaCheckResult.Offline -> result.message
                        is OtaCheckResult.Error -> result.message
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = session.role,
                active = if (session.role == UserRole.MASTER) NativeBottomTab.MENU else NativeBottomTab.DASHBOARD,
                onSelected = { tab -> openBottomTab(context, session.role, tab) },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val compact = maxWidth < 760.dp
            val contentModifier = if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(max = 820.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (session.role == UserRole.MASTER) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                horizontal = visual.sizes.screenPaddingH,
                                vertical = visual.sizes.screenPaddingV,
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
                    ) {
                        item(key = "master-top") {
                            Column(modifier = contentModifier) {
                                AppTopBar(
                                    spec = ScreenChromeSpec(
                                        title = "Panel",
                                        subtitle = "${session.banca ?: "LotteryNet"} · $dayKey",
                                        activeBottomTab = NativeBottomTab.MENU,
                                    ),
                                    onOpenMenu = { },
                                )
                            }
                        }
                        item(key = "master-operation") {
                            Column(modifier = contentModifier) {
                                ShellActionSection(
                                    title = "Operación master",
                                    meta = "Principal",
                                    actions = listOf(
                                        ShellMenuAction("Panel master", "Bancas y estado", Icons.Rounded.AdminPanelSettings, onOpenNativeMasterDashboard, active = true),
                                        ShellMenuAction("Finanzas", "Caja global", Icons.Rounded.Analytics, onOpenNativeFinance, accent = Color(0xFF2563EB)),
                                        ShellMenuAction("Deportes", "Control separado", Icons.Rounded.SportsSoccer, onOpenNativeSportsbook, accent = Color(0xFF16A34A)),
                                        ShellMenuAction("Crear banca", "Alta y credenciales", Icons.Rounded.ManageAccounts, onOpenNativeMasterCreate),
                                        ShellMenuAction("Auditoría", "Bitácora y cambios", Icons.Rounded.Visibility, onOpenNativeAudit),
                                    ),
                                )
                            }
                        }
                        item(key = "master-session") {
                            Column(modifier = contentModifier) {
                                ShellActionSection(
                                    title = "Sesión",
                                    meta = "Equipo",
                                    actions = listOf(
                                        ShellMenuAction(
                                            "Actualizar sistema",
                                            if (checkingSystemUpdate) "Revisando OTA..." else "Buscar nueva version",
                                            Icons.Rounded.SystemUpdate,
                                            onCheckSystemUpdate,
                                            accent = Color(0xFF2563EB),
                                            enabled = !checkingSystemUpdate,
                                        ),
                                        ShellMenuAction("Cerrar sesión", "Salir del equipo", Icons.AutoMirrored.Rounded.Logout, onLogout, accent = Color(0xFFB91C1C)),
                                    ),
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                horizontal = visual.sizes.screenPaddingH,
                                vertical = visual.sizes.screenPaddingV,
                            ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        ShellDrawerStyleMenu(
                            modifier = contentModifier,
                            session = session,
                            dayKey = dayKey,
                            salesTotal = salesTotal,
                            cashTotal = cashTotal,
                            pendingTotal = pendingTotal,
                            assignedCashiersCount = assignedCashiersCount,
                            rechargeAccessVisible = rechargeAccessVisible,
                            sportsbookAccessVisible = sportsbookAccessVisible,
                            onOpenNativeSales = onOpenNativeSales,
                            onOpenTicketSummary = onOpenTicketSummary,
                            onOpenTicketLookup = onOpenTicketLookup,
                            onOpenNativeResults = onOpenNativeResults,
                            onOpenNativeRecharge = onOpenNativeRecharge,
                            onOpenNativeSportsbook = onOpenNativeSportsbook,
                            onOpenNativeFinance = onOpenNativeFinance,
                            onOpenNativeReport = onOpenNativeReport,
                            onOpenNativeUsers = onOpenNativeUsers,
                            onOpenNativeSupervisors = onOpenNativeSupervisors,
                            onOpenNativeAdminLimits = onOpenNativeAdminLimits,
                            onOpenNativeMonitor = onOpenNativeMonitor,
                            onOpenNativeConfig = onOpenNativeConfig,
                            onOpenNativePrinter = onOpenNativePrinter,
                            onOpenNativeWinners = onOpenNativeWinners,
                            onCheckSystemUpdate = onCheckSystemUpdate,
                            checkingSystemUpdate = checkingSystemUpdate,
                            onLogout = onLogout,
                        )
                    }
                }
        }
    }
    }
}

@Composable
private fun ShellDrawerStyleMenu(
    modifier: Modifier = Modifier,
    session: ActiveSession,
    dayKey: String,
    salesTotal: Double,
    cashTotal: Double,
    pendingTotal: Double,
    assignedCashiersCount: Int,
    rechargeAccessVisible: Boolean,
    sportsbookAccessVisible: Boolean,
    onOpenNativeSales: () -> Unit,
    onOpenTicketSummary: () -> Unit,
    onOpenTicketLookup: (String) -> Unit,
    onOpenNativeResults: () -> Unit,
    onOpenNativeRecharge: () -> Unit,
    onOpenNativeSportsbook: () -> Unit,
    onOpenNativeFinance: () -> Unit,
    onOpenNativeReport: () -> Unit,
    onOpenNativeUsers: () -> Unit,
    onOpenNativeSupervisors: () -> Unit,
    onOpenNativeAdminLimits: () -> Unit,
    onOpenNativeMonitor: () -> Unit,
    onOpenNativeConfig: () -> Unit,
    onOpenNativePrinter: () -> Unit,
    onOpenNativeWinners: () -> Unit,
    onCheckSystemUpdate: () -> Unit,
    checkingSystemUpdate: Boolean,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val posModeRepository = remember(context) { LocalPosModeRepository(context) }
    var manualPosModeEnabled by remember(context) { mutableStateOf(posModeRepository.isEnabled()) }
    val layout = resolveShellMenuLayout(rememberLotteryNetVisualSpec().windowMode)
    val sections = buildList {
        if (session.role == UserRole.SUPERVISOR) {
            add(
                ShellMenuSection(
                    title = "Supervisión",
                    actions = listOf(
                        ShellMenuAction("Mis cajeros", "Asignados y movimiento", Icons.Rounded.Groups, onOpenNativeMonitor, accent = Color(0xFFA7F3D0), active = true),
                        ShellMenuAction("Monitoreo", "Ventas y pendientes", Icons.Rounded.MonitorHeart, onOpenNativeMonitor, accent = Color(0xFF7DD3FC)),
                        ShellMenuAction("Finanzas", "Caja del grupo", Icons.Rounded.Analytics, onOpenNativeFinance, accent = Color(0xFF2563EB)),
                        ShellMenuAction("Reporte", "Grupo asignado", Icons.Rounded.QueryStats, onOpenNativeReport, accent = Color(0xFF0F766E)),
                        ShellMenuAction("Tickets", "Tickets del grupo", Icons.AutoMirrored.Rounded.ReceiptLong, onOpenTicketSummary, accent = Color(0xFF60A5FA)),
                    ),
                ),
            )
            add(
                ShellMenuSection(
                    title = "Consulta",
                    actions = buildList {
                        add(ShellMenuAction("Resultados", "Sorteos del día", Icons.Rounded.Today, onOpenNativeResults, accent = Color(0xFFF59E0B)))
                        if (sportsbookAccessVisible) {
                            add(ShellMenuAction("Deportes", "Apuestas separadas", Icons.Rounded.SportsSoccer, onOpenNativeSportsbook, accent = Color(0xFF16A34A)))
                        }
                    },
                ),
            )
            add(
                ShellMenuSection(
                    title = "Soporte",
                    actions = listOf(
                        ShellMenuAction("Impresora", "Bluetooth térmica", Icons.Rounded.Print, onOpenNativePrinter, accent = Color(0xFF111827)),
                        ShellMenuAction(
                            "Actualizar sistema",
                            if (checkingSystemUpdate) "Revisando OTA..." else "Buscar nueva version",
                            Icons.Rounded.SystemUpdate,
                            onCheckSystemUpdate,
                            accent = Color(0xFF2563EB),
                            enabled = !checkingSystemUpdate,
                        ),
                    ),
                ),
            )
        } else {
            add(
                ShellMenuSection(
                    title = "Operación",
                    actions = buildList {
                        add(ShellMenuAction("Vender", "Ticket nuevo", Icons.Rounded.PointOfSale, onOpenNativeSales, accent = Color(0xFF2DD4BF), active = true))
                        add(ShellMenuAction("Tickets", "Resumen del día", Icons.AutoMirrored.Rounded.ReceiptLong, onOpenTicketSummary, accent = Color(0xFF60A5FA)))
                        add(ShellMenuAction("Resultados", "Sorteos del día", Icons.Rounded.Today, onOpenNativeResults, accent = Color(0xFFF59E0B)))
                        if (rechargeAccessVisible) {
                            add(ShellMenuAction("Recargas", "Carga móvil", Icons.Rounded.PhoneAndroid, onOpenNativeRecharge, accent = Color(0xFF67E8F9)))
                        }
                        if (sportsbookAccessVisible) {
                            add(ShellMenuAction("Deportes", "Apuestas separadas", Icons.Rounded.SportsSoccer, onOpenNativeSportsbook, accent = Color(0xFF16A34A)))
                        }
                        add(ShellMenuAction("Repetir ticket", "Duplicar ticket anterior", Icons.Rounded.Sell, { onOpenTicketLookup("duplicar") }, accent = Color(0xFFD8B4FE)))
                    },
                ),
            )
        }
        if (shouldShowCashboxControls(session.role) && session.role != UserRole.SUPERVISOR) {
            add(
                ShellMenuSection(
                    title = if (session.role == UserRole.SUPERVISOR) "Reportes" else "Caja",
                    actions = buildList {
                        add(
                        ShellMenuAction("Cuadre", "Caja y turno", Icons.Rounded.Analytics, onOpenNativeFinance, accent = Color(0xFF14B8A6)),
                        )
                        if (session.role == UserRole.ADMIN) {
                            add(ShellMenuAction("Reporte", "Negocio y tendencia", Icons.Rounded.QueryStats, onOpenNativeReport, accent = Color(0xFF0F766E)))
                        }
                        if (session.role == UserRole.CASHIER) {
                            add(ShellMenuAction("Reporte", "Solo mi usuario", Icons.Rounded.QueryStats, onOpenNativeReport, accent = Color(0xFF0F766E)))
                        }
                        if (session.role == UserRole.ADMIN) {
                            add(ShellMenuAction("Cobros", "Buscar ganador", Icons.Rounded.Visibility, { onOpenTicketLookup("pagar") }, accent = Color(0xFF5EEAD4)))
                        }
                        if (session.role == UserRole.ADMIN) {
                            add(ShellMenuAction("Eliminar Ticket", "Eliminar ticket con servidor", Icons.Rounded.Shield, { onOpenTicketLookup("anular") }, accent = Color(0xFFFCA5A5)))
                        }
                        add(ShellMenuAction("Impresora", "Bluetooth térmica", Icons.Rounded.Print, onOpenNativePrinter, accent = Color(0xFF111827)))
                        if (session.role == UserRole.CASHIER) {
                            add(
                                ShellMenuAction(
                                    "Actualizar sistema",
                                    if (checkingSystemUpdate) "Revisando OTA..." else "Buscar nueva version",
                                    Icons.Rounded.SystemUpdate,
                                    onCheckSystemUpdate,
                                    accent = Color(0xFF2563EB),
                                    enabled = !checkingSystemUpdate,
                                ),
                            )
                        }
                    },
                ),
            )
        }
        if (session.role == UserRole.CASHIER) {
            add(
                ShellMenuSection(
                    title = "Sistema",
                    actions = listOf(
                        ShellMenuAction(
                            "Modo POS",
                            if (manualPosModeEnabled) "Pantalla pequeña activa" else "Acomodar pantalla pequeña",
                            Icons.Rounded.PointOfSale,
                            {
                                val next = !manualPosModeEnabled
                                posModeRepository.setEnabled(next)
                                manualPosModeEnabled = next
                                Toast.makeText(
                                    context,
                                    if (next) "Modo POS activo" else "Modo POS desactivado",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                context.findShellActivity()?.recreate()
                            },
                            accent = Color(0xFF99F6E4),
                            active = manualPosModeEnabled,
                        ),
                    ),
                ),
            )
        }
        if (session.role == UserRole.ADMIN) {
            add(
                ShellMenuSection(
                    title = "Administración",
                    actions = buildList {
                        add(ShellMenuAction("Supervisor", "Grupos, claves y cajeros", Icons.Rounded.ManageAccounts, onOpenNativeSupervisors, accent = Color(0xFFA7F3D0)))
                        add(ShellMenuAction("Límites", "Venta por cajero y jugadas", Icons.Rounded.PointOfSale, onOpenNativeAdminLimits, accent = Color(0xFF2563EB)))
                        add(ShellMenuAction("Monitoreo", "Caja por banca", Icons.Rounded.MonitorHeart, onOpenNativeMonitor, accent = Color(0xFF7DD3FC)))
                    },
                ),
            )
            add(
                ShellMenuSection(
                    title = "Sistema",
                    actions = listOf(
                        ShellMenuAction("Sistema", "Config. general", Icons.Rounded.AdminPanelSettings, onOpenNativeConfig, accent = Color(0xFF99F6E4)),
                        ShellMenuAction(
                            "Actualizar sistema",
                            if (checkingSystemUpdate) "Revisando OTA..." else "Buscar nueva version",
                            Icons.Rounded.SystemUpdate,
                            onCheckSystemUpdate,
                            accent = Color(0xFF2563EB),
                            enabled = !checkingSystemUpdate,
                        ),
                    ),
                ),
            )
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            ShellMenuHeader(
                session = session,
                dayKey = dayKey,
                salesTotal = salesTotal,
                cashTotal = cashTotal,
                pendingTotal = pendingTotal,
                assignedCashiersCount = assignedCashiersCount,
                layout = layout,
            )
        }
        items(sections, key = { it.title }) { section ->
            ShellMenuSectionCard(section = section, layout = layout)
        }
        item(key = "logout") {
            ShellMenuFooterAction(
                action = ShellMenuAction(
                    title = "Cerrar Sesión",
                    description = "Salir del sistema actual",
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    onClick = onLogout,
                    accent = Color(0xFFFCA5A5),
                ),
                layout = layout,
            )
        }
    }
}

internal fun shouldShowCashboxControls(role: UserRole): Boolean {
    return role == UserRole.ADMIN || role == UserRole.SUPERVISOR || role == UserRole.CASHIER
}

@Composable
private fun ShellMenuHeader(
    session: ActiveSession,
    dayKey: String,
    salesTotal: Double,
    cashTotal: Double,
    pendingTotal: Double,
    assignedCashiersCount: Int,
    layout: ShellMenuLayoutContract,
) {
    val operationStamp = SimpleDateFormat("hh:mm:ss a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date())
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 8.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = session.banca?.ifBlank { "Banca Central" } ?: "Banca Central",
                        style = MaterialTheme.typography.titleMedium,
                        color = visual.colors.ink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${session.username} · ${resolveShellRoleBadgeLabel(session.role)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CompactStatusBadge(
                    label = if (layout.showDayLabel) {
                        "$operationStamp RD".replace("PM PM", "PM").replace("AM AM", "AM")
                    } else {
                        resolveShellRoleBadgeLabel(session.role)
                    },
                    tone = visual.colors.gain,
                )
            }
            if (layout.showDayLabel) {
                Text(
                    text = dayKey,
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                )
            }
            MetricStrip(
                items = resolveShellHeaderMetricLabels(session.role).map { label ->
                    when (label) {
                        "Cajeros" -> MetricStripItem(label, assignedCashiersCount.toString(), visual.colors.finance)
                        "Venta" -> MetricStripItem(label, shellMoney(salesTotal), visual.colors.sale)
                        "Caja" -> MetricStripItem(label, shellMoney(cashTotal), visual.colors.gain)
                        else -> MetricStripItem(label, shellMoney(pendingTotal), visual.colors.warning)
                    }
                },
            )
        }
    }
}

@Composable
private fun ShellMenuSectionCard(
    section: ShellMenuSection,
    layout: ShellMenuLayoutContract,
) {
    val visual = rememberLotteryNetVisualSpec()
    val panelPadding = if (layout.useDenseRows) 7.dp else 9.dp
    val rowGap = if (layout.useDenseRows) 4.dp else 5.dp
    CompactPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = panelPadding, vertical = if (layout.useDenseRows) 7.dp else 8.dp),
    ) {
        SectionHeader(title = section.title)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            section.actions.forEach { action ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = action.onClick,
                    shape = RoundedCornerShape(if (layout.showLargeCards) visual.sizes.panelRadius else 7.dp),
                    color = if (action.active) visual.colors.panelAlt else visual.colors.panel,
                    border = BorderStroke(1.dp, visual.colors.border),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = layout.actionPaddingDp.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(layout.iconSizeDp.dp)
                                .background(
                                    (action.accent ?: visual.colors.neutral).copy(alpha = 0.12f),
                                    RoundedCornerShape(if (layout.useDenseRows) 8.dp else 9.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = action.accent ?: visual.colors.ink,
                                modifier = Modifier.size((layout.iconSizeDp - 14).dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = action.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = visual.colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (layout.showActionDescriptions) {
                                Text(
                                    text = action.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = visual.colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = visual.colors.muted,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellMenuFooterAction(
    action: ShellMenuAction,
    layout: ShellMenuLayoutContract,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = action.onClick,
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = Color(0xFFFEF2F2),
        border = BorderStroke(1.dp, Color(0xFFF4B9B9)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = layout.actionPaddingDp.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(layout.iconSizeDp.dp)
                    .background(Color.White, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = action.accent ?: visual.colors.loss,
                    modifier = Modifier.size((layout.iconSizeDp - 14).dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!layout.collapseSecondaryActions) {
                    Text(
                        text = action.description,
                        style = if (layout.showActionDescriptions) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                        color = visual.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShellSessionPanel(
    session: ActiveSession,
) {
    CompactPanel {
        Text(
            text = session.username,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
    }
}

private data class ShellMenuSection(
    val title: String,
    val actions: List<ShellMenuAction>,
)

@Composable
private fun LegacyShellSessionPanel(
    session: ActiveSession,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel {
        com.lotterynet.pro.ui.common.SectionHeader(title = "Sesión activa", meta = "Entrada nativa")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = session.username,
                style = MaterialTheme.typography.titleSmall,
                color = visual.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = session.banca?.ifBlank { "Sin banca" } ?: "Sin banca",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactStatusBadge(
                label = when (session.role) {
                    UserRole.ADMIN -> "ADMIN"
                    UserRole.SUPERVISOR -> "Supervisor"
                    UserRole.CASHIER -> "CAJA"
                    UserRole.MASTER -> "MASTER"
                    UserRole.UNKNOWN -> "SIN ROL"
                },
                tone = when (session.role) {
                    UserRole.ADMIN -> warningColor()
                    UserRole.SUPERVISOR -> visual.colors.finance
                    UserRole.CASHIER -> gainColor()
                    UserRole.MASTER -> visual.colors.admin
                    UserRole.UNKNOWN -> visual.colors.neutral
                },
            )
        }
    }
}

@Composable
private fun ShellActionSection(
    title: String,
    meta: String,
    actions: List<ShellMenuAction>,
) {
    var expanded by rememberSaveable(title) {
        mutableStateOf(title == "Operación hoy" || title == "Operación master" || title == "Sesión")
    }
    CompactPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = title, meta = if (expanded) meta else "${actions.size} accesos")
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Ocultar sección" else "Mostrar sección",
                tint = rememberLotteryNetVisualSpec().colors.muted,
            )
        }
        if (expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.chunked(2).forEach { rowActions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowActions.forEach { action ->
                            ShellActionRow(action = action, modifier = Modifier.weight(1f))
                        }
                        if (rowActions.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellActionRow(action: ShellMenuAction, modifier: Modifier = Modifier) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = action.enabled, onClick = action.onClick),
        alt = action.active,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        color = (action.accent ?: visual.colors.neutral).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(9.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = action.accent ?: visual.colors.ink,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (action.active) {
                    Text(
                        text = "Principal",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = gainColor(),
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = action.accent ?: visual.colors.neutral,
            )
        }
    }
}

private data class ShellMenuAction(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val accent: Color? = null,
    val active: Boolean = false,
    val enabled: Boolean = true,
)

private fun Context.findShellActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun roleTitle(role: UserRole): String {
    return when (role) {
        UserRole.MASTER -> "Entrada master"
        UserRole.ADMIN -> "Entrada admin"
        UserRole.SUPERVISOR -> "Entrada supervisor"
        UserRole.CASHIER -> "Entrada cajero"
        UserRole.UNKNOWN -> "Sesión no resuelta"
    }
}

@Composable
private fun ShellQuickRow(
    onOpenNativeSales: () -> Unit,
    onOpenTicketSummary: () -> Unit,
    onOpenTicketLookup: (String) -> Unit,
    onOpenNativeResults: () -> Unit,
    onOpenNativeRecharge: () -> Unit,
    onOpenNativeFinance: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(alt = true) {
        SectionHeader(title = "Acceso rápido", meta = "Inicio")
        CompactAdaptiveGrid(itemCount = 8, modifier = Modifier.fillMaxWidth()) { index, itemModifier ->
            when (index) {
                0 -> ShellMiniShortcut("Vender", visual.colors.saleSurface, visual.colors.sale, onOpenNativeSales, itemModifier)
                1 -> ShellMiniShortcut("Tickets", visual.colors.ticketsSurface, visual.colors.tickets, onOpenTicketSummary, itemModifier)
                2 -> ShellMiniShortcut("Pagar", visual.colors.resultsSurface, visual.colors.results, { onOpenTicketLookup("pagar") }, itemModifier)
                3 -> ShellMiniShortcut("Duplicar", Color(0xFFF5EEFF), Color(0xFF7C3AED), { onOpenTicketLookup("duplicar") }, itemModifier)
                4 -> ShellMiniShortcut("Anular", visual.colors.dangerSurface, visual.colors.loss, { onOpenTicketLookup("anular") }, itemModifier)
                5 -> ShellMiniShortcut("Recargas", visual.colors.rechargeSurface, visual.colors.recharge, onOpenNativeRecharge, itemModifier)
                6 -> ShellMiniShortcut("Cuadre", visual.colors.financeSurface, visual.colors.finance, onOpenNativeFinance, itemModifier)
                else -> ShellMiniShortcut("Resultados", visual.colors.resultsSurface, visual.colors.results, onOpenNativeResults, itemModifier)
            }
        }
    }
}

@Composable
private fun ShellMiniShortcut(
    label: String,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
        }
    }
}

private fun shellMoney(value: Double): String = formatWholeMoney(value)
