package com.lotterynet.pro.ui.admin

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.isPaidTicketStatus
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscription
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.core.operations.filterCashiersForSession
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.MetricStrip
import com.lotterynet.pro.ui.common.MetricStripItem
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.OperationalListHeader
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.finance.FinanceActivity
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.startSafeNativeDestination
import com.lotterynet.pro.ui.recharge.canShowRechargeAccess
import com.lotterynet.pro.ui.recharge.RecargasActivity
import com.lotterynet.pro.ui.recharge.resolveRechargeOwnerAccount
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import com.lotterynet.pro.ui.tickets.TicketOfficialActivity
import com.lotterynet.pro.ui.tickets.TicketSummaryActivity
import com.lotterynet.pro.ui.users.UserAccountsActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal data class AdminDashboardLayoutContract(
    val mergeRiskIntoSummary: Boolean,
    val showOperationBadges: Boolean,
    val secondaryInitiallyExpanded: Boolean,
    val showShortcutSubtitle: Boolean,
    val compactSummary: Boolean,
    val useCompactRows: Boolean,
    val showLargeCards: Boolean,
    val summaryPaddingVerticalDp: Int,
    val shortcutPaddingVerticalDp: Int,
)

internal fun resolveAdminDashboardLayout(windowMode: LotteryNetWindowMode): AdminDashboardLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS,
        LotteryNetWindowMode.POS_TIGHT -> AdminDashboardLayoutContract(
            mergeRiskIntoSummary = true,
            showOperationBadges = false,
            secondaryInitiallyExpanded = false,
            showShortcutSubtitle = false,
            compactSummary = true,
            useCompactRows = true,
            showLargeCards = false,
            summaryPaddingVerticalDp = 8,
            shortcutPaddingVerticalDp = 7,
        )
        LotteryNetWindowMode.TABLET,
        LotteryNetWindowMode.WIDE -> AdminDashboardLayoutContract(
            mergeRiskIntoSummary = false,
            showOperationBadges = true,
            secondaryInitiallyExpanded = true,
            showShortcutSubtitle = true,
            compactSummary = false,
            useCompactRows = false,
            showLargeCards = true,
            summaryPaddingVerticalDp = 10,
            shortcutPaddingVerticalDp = 9,
        )
    }
}

internal fun resolveAdminSecondaryShortcutTitles(rechargeVisible: Boolean): List<String> {
    return buildList {
        add("Usuarios")
        add("Loterías")
        add("Configuración")
        if (rechargeVisible) add("Recargas")
    }
}

internal fun resolveAdminCriticalShortcutTitles(): List<String> = listOf(
    "Monitor",
    "Límite venta cajeros",
    "Tickets de cajeros",
    "Ganadores",
    "Caja",
    "Alertas",
)

class AdminDashboardActivity : AppCompatActivity() {
    private val syncHandler = Handler(Looper.getMainLooper())
    private val realtimeClient = LotterynetRealtimeClient()
    private val realtimeSubscriptions = mutableListOf<LotterynetRealtimeClient.SubscriptionHandle>()
    private lateinit var session: ActiveSession
    private lateinit var usersRepository: LocalUsersRepository
    private lateinit var salesRepository: LocalSalesRepository
    private lateinit var financeRepository: LocalFinanceRepository
    private lateinit var dayKey: String
    private var lastRemoteUpdatedAt: String? = null
    private var summaryState by mutableStateOf(FinanceSummary())
    private var cashierCountState by mutableStateOf(0)
    private var recentTicketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var syncMessageState by mutableStateOf("Sincronizando operacion...")
    private val operationalSyncInFlight = AtomicBoolean(false)
    private val syncPollRunnable = object : Runnable {
        override fun run() {
            syncOperational(force = false)
            syncHandler.postDelayed(this, resolveAdminOperationalPollIntervalMs(realtimeClient.isConfigured()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked(
                this,
                activeSession?.role,
                NativeDestination.ADMIN_DASHBOARD,
            )
        ) return
        session = activeSession ?: return
        usersRepository = LocalUsersRepository(this)
        usersRepository.touchSession(session)
        salesRepository = LocalSalesRepository(this)
        dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date())
        financeRepository = LocalFinanceRepository(
            salesRepository = salesRepository,
            rechargeRepository = LocalRechargeRepository(this),
            usersRepository = usersRepository,
        )
        refreshDashboardData()

        setContent {
            LotteryNetComposeTheme {
                AdminDashboardRoute(
                    session = session,
                    dayKey = dayKey,
                    summary = summaryState,
                    cashierCount = cashierCountState,
                    recentTickets = recentTicketsState,
                    syncMessage = syncMessageState,
                    rechargeVisible = canShowRechargeAccess(resolveRechargeOwnerAccount(session, usersRepository)),
                    onBack = { finish() },
                    onOpenUsers = { startSafeNativeDestination(this, session.role, NativeDestination.USER_ACCOUNTS) },
                    onOpenMonitor = { startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_MONITOR) },
                    onOpenLotteryMonitor = { startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_LOTTERY_MONITOR) },
                    onOpenWinners = { startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_WINNERS) },
                    onOpenAlerts = { startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_ALERTS) },
                    onOpenConfig = { startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_CONFIG) },
                    onOpenLimits = { startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_LIMITS) },
                    onOpenRecharges = { startSafeNativeDestination(this, session.role, NativeDestination.RECHARGE) },
                    onOpenFinance = { startSafeNativeDestination(this, session.role, NativeDestination.FINANCE) },
                    onOpenTickets = { startSafeNativeDestination(this, session.role, NativeDestination.TICKET_SUMMARY) },
                    onOpenTicket = { ticket ->
                        startActivity(Intent(this, TicketOfficialActivity::class.java).apply {
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_ID, ticket.id)
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_EPOCH, ticket.createdAtEpochMs)
                            putExtra(TicketOfficialActivity.EXTRA_BANCA_NAME, session.banca ?: "LotteryNet")
                            putExtra(TicketOfficialActivity.EXTRA_ACTION_MODE, "buscar")
                        })
                    },
                )
            }
        }
        syncOperational(force = true)
        subscribeRealtime()
        syncHandler.postDelayed(syncPollRunnable, resolveAdminOperationalPollIntervalMs(realtimeClient.isConfigured()))
    }

    override fun onDestroy() {
        syncHandler.removeCallbacks(syncPollRunnable)
        realtimeSubscriptions.forEach { it.close() }
        realtimeClient.shutdown()
        super.onDestroy()
    }

    private fun refreshDashboardData() {
        val cashiers = filterCashiersForSession(session, usersRepository.getCashiers())
        summaryState = financeRepository.getScopedDaySummary(dayKey, financeRepository.resolveScope(session))
        cashierCountState = cashiers.size
        recentTicketsState = filterTicketsForOperationalScope(
            session = session,
            tickets = salesRepository.getTicketsForDay(dayKey),
            cashiers = cashiers,
        ).sortedByDescending { it.createdAtEpochMs }.take(8)
    }

    private fun syncOperational(force: Boolean) {
        if (!operationalSyncInFlight.compareAndSet(false, true)) return
        thread(name = "admin-dashboard-sync") {
            runCatching {
                NativeOperationalSyncCoordinator(
                    ticketGateway = NativeTicketCloudSyncCoordinator(
                        salesRepository = salesRepository,
                        queueRepository = NativeTicketSyncQueueRepository(this),
                    ),
                ).syncTicketsForSession(
                    session = session,
                    lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                    force = force,
                )
            }.onSuccess { state ->
                lastRemoteUpdatedAt = state.remoteUpdatedAt ?: lastRemoteUpdatedAt
                runOnUiThread {
                    refreshDashboardData()
                    syncMessageState = state.message
                }
            }.also {
                operationalSyncInFlight.set(false)
            }
        }
    }

    private fun subscribeRealtime() {
        if (!realtimeClient.isConfigured()) return
        val ownerKey = resolveOperationalOwnerKey(session)
        if (ownerKey.isBlank()) return
        realtimeSubscriptions += realtimeClient.subscribe(LotterynetRealtimeSubscription.ticketOwner(ownerKey)) {
            syncOperational(force = true)
        }
    }
}

@Composable
private fun AdminDashboardRoute(
    session: ActiveSession,
    dayKey: String,
    summary: FinanceSummary,
    cashierCount: Int,
    recentTickets: List<TicketRecord>,
    syncMessage: String,
    rechargeVisible: Boolean,
    onBack: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenLotteryMonitor: () -> Unit,
    onOpenWinners: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenConfig: () -> Unit,
    onOpenLimits: () -> Unit,
    onOpenRecharges: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenTickets: () -> Unit,
    onOpenTicket: (TicketRecord) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val layout = androidx.compose.runtime.remember(visual.windowMode) { resolveAdminDashboardLayout(visual.windowMode) }
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(modifier = Modifier.fillMaxSize(), color = visual.colors.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
                verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
            ) {
                item {
                    ScreenHeaderPanel(
                        title = "Panel admin",
                        subtitle = "${session.banca ?: "LotteryNet"} · $dayKey · $syncMessage",
                        onBack = onBack,
                        badgeLabel = "$cashierCount cajeros",
                        badgeTone = visual.colors.admin,
                    )
                }
                item {
                    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = layout.summaryPaddingVerticalDp.dp)) {
                        SectionHeader(title = "Resumen operativo", meta = "Banca hoy")
                        MetricStrip(
                            items = listOf(
                                MetricStripItem("Cajeros", cashierCount.toString(), visual.colors.ink),
                                MetricStripItem("Ventas", dashboardMoney(summary.ventas), gainColor()),
                                MetricStripItem("Caja", dashboardMoney(summary.cajaDisponible), visual.colors.admin),
                            ),
                        )
                        MetricStrip(
                            items = listOf(
                                MetricStripItem("Ganadores", summary.ganadores.toString(), warningColor()),
                                MetricStripItem("Pagados", summary.pagados.toString(), visual.colors.ink),
                                MetricStripItem("Pendiente", dashboardMoney(summary.premiosPendientes), warningColor()),
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CompactStatusBadge(
                                label = if (summary.premiosPendientes > 0.0) "Pendientes ${dashboardMoney(summary.premiosPendientes)}" else "Sin pendientes",
                                tone = if (summary.premiosPendientes > 0.0) warningColor() else gainColor(),
                            )
                            CompactStatusBadge(
                                label = if (summary.anuladosCount > 0) "Anulados ${summary.anuladosCount}" else "Sin anulados",
                                tone = if (summary.anuladosCount > 0) Color(0xFFB91C1C) else visual.colors.neutral,
                            )
                            CompactStatusBadge(
                                label = "${recentTickets.size} tickets",
                                tone = Color(0xFFE2E8F0),
                            )
                        }
                    }
                }
                item {
                    CompactPanel {
                        OperationalListHeader(title = "Accesos críticos", meta = "Abrir rápido")
                        DashboardShortcut("Monitor", if (layout.showShortcutSubtitle) "Caja por operador" else null, Icons.Rounded.MonitorHeart, visual.colors.sale, layout, onOpenMonitor)
                        DashboardShortcut("Límite venta cajeros", if (layout.showShortcutSubtitle) "Dinero diario por cajero" else null, Icons.Rounded.PointOfSale, visual.colors.admin, layout, onOpenLimits)
                        DashboardShortcut("Tickets de cajeros", if (layout.showShortcutSubtitle) "Lista filtrada por cajero" else null, Icons.AutoMirrored.Rounded.ReceiptLong, visual.colors.tickets, layout, onOpenTickets)
                        DashboardShortcut("Ganadores", if (layout.showShortcutSubtitle) "Pendientes y pagados" else null, Icons.Rounded.WarningAmber, visual.colors.results, layout, onOpenWinners)
                        DashboardShortcut("Caja", if (layout.showShortcutSubtitle) "Cuadre y reportes" else null, Icons.AutoMirrored.Rounded.ReceiptLong, visual.colors.finance, layout, onOpenFinance)
                        DashboardShortcut("Alertas", if (layout.showShortcutSubtitle) "Avisos del sistema" else null, Icons.Rounded.Notifications, visual.colors.loss, layout, onOpenAlerts)
                    }
                }
                item {
                    CompactPanel {
                        OperationalListHeader(title = "Accesos secundarios", meta = "Gestión")
                        DashboardShortcut("Usuarios", if (layout.showShortcutSubtitle) "Cuentas locales" else null, Icons.Rounded.ManageAccounts, visual.colors.admin, layout, onOpenUsers)
                        DashboardShortcut("Loterías", if (layout.showShortcutSubtitle) "Estado por sorteo" else null, Icons.Rounded.QueryStats, visual.colors.sale, layout, onOpenLotteryMonitor)
                        DashboardShortcut("Configuración", if (layout.showShortcutSubtitle) "Impresora y banca" else null, Icons.Rounded.Print, visual.colors.printer, layout, onOpenConfig)
                        if (rechargeVisible) {
                            DashboardShortcut("Recargas", if (layout.showShortcutSubtitle) "Control diario" else null, Icons.Rounded.PhoneAndroid, visual.colors.recharge, layout, onOpenRecharges)
                        }
                    }
                }
                item {
                    CompactPanel {
                        OperationalListHeader(title = "Últimos tickets", meta = "${recentTickets.size} recientes")
                    }
                }
                if (recentTickets.isEmpty()) {
                    item {
                        CompactPanel {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                Text("Sin tickets registrados hoy.", color = visual.colors.muted)
                            }
                        }
                    }
                } else {
                    items(recentTickets, key = { it.id }) { ticket ->
                        DashboardTicketRow(ticket = ticket, onOpen = { onOpenTicket(ticket) })
                    }
                }
            }
            BottomNavBar(
                role = session.role,
                active = NativeBottomTab.DASHBOARD,
                onSelected = { tab -> openBottomTab(context, session.role, tab) },
            )
        }
    }
}

private const val OPERATIONAL_POLL_MS = 60_000L
private const val OPERATIONAL_REALTIME_FALLBACK_POLL_MS = 300_000L

internal fun resolveAdminOperationalPollIntervalMs(realtimeEnabled: Boolean): Long {
    return if (realtimeEnabled) OPERATIONAL_REALTIME_FALLBACK_POLL_MS else OPERATIONAL_POLL_MS
}

@Composable
private fun DashboardMetric(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    tone: Color,
) {
    CompactPanel(modifier = modifier, alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tone)
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = tone)
                Text(value, style = MaterialTheme.typography.titleSmall, color = tone, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun DashboardShortcut(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    tone: Color,
    layout: AdminDashboardLayoutContract,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(visual.colors.panelAlt, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = layout.shortcutPaddingVerticalDp.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(tone.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tone)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    maxLines = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = tone.copy(alpha = 0.9f))
    }
}

@Composable
private fun DashboardTicketRow(
    ticket: TicketRecord,
    onOpen: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        ticket.serial ?: ticket.id,
                        style = MaterialTheme.typography.titleSmall,
                        color = visual.colors.ink,
                        fontFamily = FontFamily.Monospace,
                    )
                    CompactStatusBadge(
                        label = dashboardStatus(ticket.status),
                        tone = dashboardStatusColor(ticket.status),
                    )
                }
                Text(
                    "${ticket.sellerUser ?: ticket.adminUser ?: "sin usuario"} · ${dashboardTicketTime(ticket.createdAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
                Text(
                    ticket.plays.joinToString(" · ") { play -> "${play.playType} ${play.number} ${dashboardMoney(play.amount)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    dashboardTicketSummary(ticket),
                    style = MaterialTheme.typography.titleSmall,
                    color = dashboardStatusColor(ticket.status),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun dashboardMoney(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)

private fun dashboardStatusColor(status: String): Color {
    if (isPaidTicketStatus(status)) return Color(0xFF0F9F66)
    return when (status.lowercase(Locale.getDefault())) {
        "winner" -> Color(0xFFEA580C)
        "voided", "invalid" -> Color(0xFFB91C1C)
        else -> Color(0xFF324763)
    }
}

private fun dashboardStatus(status: String): String {
    if (isPaidTicketStatus(status)) return "Pagado"
    return when (status.lowercase(Locale.getDefault())) {
        "winner" -> "Ganador"
        "voided", "invalid" -> "Anulado"
        else -> "Activo"
    }
}

private fun dashboardTicketSummary(ticket: TicketRecord): String {
    val amount = if (ticket.status.equals("winner", true) || isPaidTicketStatus(ticket.status)) {
        ticket.totalPrize.coerceAtLeast(0.0)
    } else {
        ticket.total
    }
    return "${dashboardStatus(ticket.status)} · ${dashboardMoney(amount)}"
}

private fun dashboardTicketTime(epochMs: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date(epochMs))
}
