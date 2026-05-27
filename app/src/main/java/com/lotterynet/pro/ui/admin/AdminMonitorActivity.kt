package com.lotterynet.pro.ui.admin

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lotterynet.pro.core.calendar.LotteryClosePolicy
import com.lotterynet.pro.core.calendar.StaticHolidayCalendarRepository
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.finance.isFinanceVoidStatus
import com.lotterynet.pro.core.finance.resolveOperationalReportNet
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.formatPlayDisplayNumber
import com.lotterynet.pro.core.model.isPaidStatus
import com.lotterynet.pro.core.operations.filterCashiersForSession
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.core.operations.buildActorLabelLookup
import com.lotterynet.pro.core.operations.resolveTicketActorLabel
import com.lotterynet.pro.core.operations.sortCashierAccountsNatural
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.core.storage.CashierSalesLimitInputs
import com.lotterynet.pro.core.storage.LocalAdminLotteryConfigRepository
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalTrustedClockRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.decodeCashierSalesLimitInputs
import com.lotterynet.pro.core.storage.decodeCashierUserSalesLimitInputs
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscription
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeRechargeCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.NativeUsersBootstrapper
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKeys
import com.lotterynet.pro.core.users.SupabaseUsersRemoteStore
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactToggleSwitch
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.MetricStrip
import com.lotterynet.pro.ui.common.MetricStripItem
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.OperationalListHeader
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.QuickFilterChips
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactSegmentedSelector
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.CompactTicketSaveSyncStatus
import com.lotterynet.pro.ui.common.LotteryLogo
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.TicketSaveSyncStage
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.resolveTicketSaveSyncUiContract
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.navigation.safeNativeDestinationIntent
import com.lotterynet.pro.ui.navigation.startSafeNativeDestination
import com.lotterynet.pro.ui.sales.resolveSalesStartupSystemModeConfig
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import com.lotterynet.pro.ui.tickets.TicketLookupActivity
import com.lotterynet.pro.ui.tickets.TicketOwnerScope
import com.lotterynet.pro.ui.tickets.TicketSummaryActivity
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

internal data class AdminMonitorLayoutContract(
    val splitActions: Boolean,
    val compactSummary: Boolean,
    val compactCards: Boolean,
    val useCompactRows: Boolean,
    val showLargeCards: Boolean,
    val summaryPaddingVerticalDp: Int,
    val rowPaddingVerticalDp: Int,
    val statPaddingDp: Int,
    val boldPrimaryText: Boolean,
)

internal data class CashierMonitorCardVisualContract(
    val singleLineIdentity: Boolean,
    val inlineMetrics: Boolean,
    val singleStatusIndicator: Boolean,
    val stackedMetricCards: Boolean,
    val minTouchTargetDp: Int,
    val rowPaddingVerticalDp: Int,
)

internal data class CashierCardActionContract(
    val cardTapOpensMenu: Boolean,
    val actions: List<String>,
    val filterTicketsByCashier: Boolean,
    val filterReportsByCashier: Boolean,
    val maxVisibleRowActions: Int,
)

internal fun resolveCashierMonitorCardVisualContract(): CashierMonitorCardVisualContract {
    return CashierMonitorCardVisualContract(
        singleLineIdentity = true,
        inlineMetrics = true,
        singleStatusIndicator = true,
        stackedMetricCards = false,
        minTouchTargetDp = 44,
        rowPaddingVerticalDp = 6,
    )
}

internal fun resolveCashierCardActionContract(windowMode: LotteryNetWindowMode): CashierCardActionContract {
    val compact = windowMode == LotteryNetWindowMode.POS_TIGHT || windowMode == LotteryNetWindowMode.POS
    return CashierCardActionContract(
        cardTapOpensMenu = true,
        actions = listOf("Detalle", "Tickets", "Reporte", "Cuadre", "Cobros"),
        filterTicketsByCashier = true,
        filterReportsByCashier = true,
        maxVisibleRowActions = if (compact) 1 else 2,
    )
}

internal fun resolveAdminMonitorLayout(windowMode: LotteryNetWindowMode): AdminMonitorLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> AdminMonitorLayoutContract(
            splitActions = true,
            compactSummary = true,
            compactCards = true,
            useCompactRows = true,
            showLargeCards = false,
            summaryPaddingVerticalDp = 6,
            rowPaddingVerticalDp = 5,
            statPaddingDp = 6,
            boldPrimaryText = true,
        )
        LotteryNetWindowMode.POS -> AdminMonitorLayoutContract(
            splitActions = true,
            compactSummary = true,
            compactCards = true,
            useCompactRows = true,
            showLargeCards = false,
            summaryPaddingVerticalDp = 8,
            rowPaddingVerticalDp = 7,
            statPaddingDp = 8,
            boldPrimaryText = true,
        )
        LotteryNetWindowMode.TABLET,
        LotteryNetWindowMode.WIDE -> AdminMonitorLayoutContract(
            splitActions = false,
            compactSummary = false,
            compactCards = false,
            useCompactRows = true,
            showLargeCards = false,
            summaryPaddingVerticalDp = 10,
            rowPaddingVerticalDp = 10,
            statPaddingDp = 10,
            boldPrimaryText = true,
        )
    }
}

internal fun adminMonitorActionLabels(): List<String> = listOf(
    "Actualizar servidor",
    "WhatsApp",
    "Compartir",
    "Guardar",
    "Impr.",
)

internal enum class AdminMonitorRoleSegment(val label: String) {
    CASHIERS("Cajeros"),
    MONITOR("Monitoreo"),
    TICKETS("Tickets"),
    REPORT("Reporte"),
}

internal fun adminMonitorRoleSegmentOptions(role: UserRole): List<QuickFilterChip> {
    return emptyList()
}

internal fun adminMonitorFilterOptions(): List<QuickFilterChip> = listOf(
    QuickFilterChip("all", "Todos"),
    QuickFilterChip("sold", "Venta"),
    QuickFilterChip("pending_payment", "Pend. cobro"),
    QuickFilterChip("loss", "Pérdida"),
    QuickFilterChip("benefit", "Beneficio"),
)

internal fun monitorResultLabel(resultado: Double): String {
    return when {
        resultado > 0.0 -> "Beneficio"
        resultado < 0.0 -> "Pérdida"
        else -> "Neutro"
    }
}

@Composable
private fun monitorResultTone(resultado: Double): Color {
    val visual = rememberLotteryNetVisualSpec()
    return when {
        resultado > 0.0 -> gainColor()
        resultado < 0.0 -> visual.colors.loss
        else -> visual.colors.ink
    }
}

internal fun sortMonitorCashierLabelsNatural(labels: List<String>): List<String> {
    return labels.sortedWith(
        compareBy<String>({ monitorCashierNumber(it) }, { it.lowercase(Locale.US) }),
    )
}

class AdminMonitorActivity : AppCompatActivity() {
    private val syncHandler = Handler(Looper.getMainLooper())
    private val realtimeClient = LotterynetRealtimeClient()
    private val realtimeSubscriptions = mutableListOf<LotterynetRealtimeClient.SubscriptionHandle>()
    private lateinit var session: ActiveSession
    private lateinit var usersRepository: LocalUsersRepository
    private lateinit var usersRemoteStore: SupabaseUsersRemoteStore
    private lateinit var financeRepository: LocalFinanceRepository
    private lateinit var salesRepository: LocalSalesRepository
    private lateinit var rechargeRepository: LocalRechargeRepository
    private lateinit var cashierSalesLimitRepository: LocalCashierSalesLimitRepository
    private lateinit var catalogRepository: StaticLotteryCatalogRepository
    private lateinit var trustedClockRepository: LocalTrustedClockRepository
    private lateinit var closePolicy: LotteryClosePolicy
    private lateinit var ticketSyncQueueRepository: NativeTicketSyncQueueRepository
    private lateinit var dayKey: String
    private lateinit var operationalSyncCoordinator: NativeOperationalSyncCoordinator
    private var rowsState by mutableStateOf<List<MonitorRow>>(emptyList())
    private var bancaSummaryState by mutableStateOf(FinanceSummary())
    private var monitorTicketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var monitorWinnerTicketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var monitorLotteriesState by mutableStateOf<List<LotteryCatalogItem>>(emptyList())
    private var cashierSalesLimitsPayloadState by mutableStateOf("")
    private var monitorNowUtcMsState by mutableStateOf(0L)
    private var pendingSyncCountState by mutableStateOf(0)
    private var lastRemoteUpdatedAt: String? = null
    private val monitorSyncInFlight = AtomicBoolean(false)
    private val syncPollRunnable = object : Runnable {
        override fun run() {
            syncMonitor(force = false)
            syncHandler.postDelayed(this, resolveAdminMonitorPollIntervalMs(realtimeClient.isConfigured()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.ADMIN_MONITOR)) return
        session = activeSession ?: return
        usersRepository = LocalUsersRepository(this)
        val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        usersRemoteStore = SupabaseUsersRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
        )
        usersRepository.touchSession(session)
        catalogRepository = StaticLotteryCatalogRepository()
        trustedClockRepository = LocalTrustedClockRepository(this)
        val holidayRepository = StaticHolidayCalendarRepository(
            dominicanLotteryIds = catalogRepository.getCalendarRule().dominicanLotteryIds,
            americanLotteryIds = catalogRepository.getCalendarRule().americanLotteryIds,
        )
        closePolicy = LotteryClosePolicy(trustedClockRepository, holidayRepository)
        salesRepository = LocalSalesRepository(this)
        rechargeRepository = LocalRechargeRepository(this)
        cashierSalesLimitRepository = LocalCashierSalesLimitRepository(this)
        financeRepository = LocalFinanceRepository(
            salesRepository = salesRepository,
            rechargeRepository = rechargeRepository,
            usersRepository = usersRepository,
        )
        dayKey = buildAdminMonitorDayKey(
            nowUtcMs = trustedClockRepository.getTrustedUtcMs(),
            operationTerritory = normalizeMonitorTerritory(session.territory),
        )
        ticketSyncQueueRepository = NativeTicketSyncQueueRepository(this)
        operationalSyncCoordinator = NativeOperationalSyncCoordinator(
            ticketGateway = NativeTicketCloudSyncCoordinator(
                salesRepository = salesRepository,
                queueRepository = ticketSyncQueueRepository,
            ),
        )
        refreshMonitorData()

        setContent {
            LotteryNetComposeTheme {
                AdminMonitorRoute(
                    session = session,
                    dayKey = dayKey,
                    rows = rowsState,
                    bancaSummary = bancaSummaryState,
                    tickets = monitorTicketsState,
                    winnerTickets = monitorWinnerTicketsState,
                    lotteries = monitorLotteriesState,
                    cashierSalesLimitsPayload = cashierSalesLimitsPayloadState,
                    nowUtcMs = monitorNowUtcMsState,
                    closePolicy = closePolicy,
                    operationTerritory = normalizeMonitorTerritory(session.territory),
                    pendingSyncCount = pendingSyncCountState,
                    onBack = { finish() },
                    onRefresh = { syncMonitor(force = true) },
                    onOpenCashier = { row ->
                        startActivity(Intent(this, AdminCashierDetailActivity::class.java).apply {
                            putExtra(AdminCashierDetailActivity.EXTRA_ACTOR_ID, row.userId)
                            putExtra(AdminCashierDetailActivity.EXTRA_ACTOR_USER, row.username)
                            putExtra(AdminCashierDetailActivity.EXTRA_ACTOR_LABEL, row.displayName)
                            putExtra(AdminCashierDetailActivity.EXTRA_BANCA_NAME, session.banca ?: "LotteryNet")
                        })
                    },
                    onShare = { rowsForShare, whatsappOnly ->
                        shareMonitor(session, dayKey, bancaSummaryState, rowsForShare, whatsappOnly)
                    },
                    onShareLottery = { lotteryLabel, viewLabel, statusLabel, rowsForShare ->
                        shareLotteryMonitor(
                            lotteryLabel = lotteryLabel,
                            viewLabel = viewLabel,
                            statusLabel = statusLabel,
                            rows = rowsForShare,
                        )
                    },
                    onPrint = { rowsForPrint ->
                        val bitmap = NativeBitmapExport.renderAdminMonitorBitmap(
                            bancaName = session.banca ?: "LotteryNet",
                            dayKey = dayKey,
                            bancaSummary = bancaSummaryState,
                            rows = rowsForPrint.map(::buildMonitorRowLine),
                        )
                        NativeBitmapExport.printBitmap(this, bitmap, "monitor-admin-$dayKey")
                    },
                    onSave = { rowsForSave ->
                        val bitmap = NativeBitmapExport.renderAdminMonitorBitmap(
                            bancaName = session.banca ?: "LotteryNet",
                            dayKey = dayKey,
                            bancaSummary = bancaSummaryState,
                            rows = rowsForSave.map(::buildMonitorRowLine),
                        )
                        NativeBitmapExport.saveBitmapToDownloads(this, bitmap, "monitor-admin-$dayKey.png")
                    },
                    onSetCashierActive = { cashierIds, active ->
                        setCashiersActive(cashierIds, active)
                    },
                )
            }
        }
        syncMonitor(force = true)
        subscribeRealtime()
        syncHandler.postDelayed(syncPollRunnable, resolveAdminMonitorPollIntervalMs(realtimeClient.isConfigured()))
    }

    override fun onDestroy() {
        syncHandler.removeCallbacks(syncPollRunnable)
        realtimeSubscriptions.forEach { it.close() }
        realtimeClient.shutdown()
        super.onDestroy()
    }

    private fun refreshMonitorData() {
        rowsState = buildMonitorRows(session, usersRepository, financeRepository, dayKey)
        bancaSummaryState = financeRepository.getScopedDaySummary(dayKey, financeRepository.resolveScope(session))
        val modeConfig = resolveSalesStartupSystemModeConfig(
            session = session,
            usersRepository = usersRepository,
            adminLotteryConfigRepository = LocalAdminLotteryConfigRepository(this),
        )
        val visibleLotteries = filterMonitorLotteriesForSystemMode(
            catalogRepository.getAllLotteries(),
            modeConfig,
        )
        val visibleLotteryIds = visibleLotteries.mapTo(linkedSetOf()) { it.id }
        monitorTicketsState = filterMonitorTicketsForLotteries(
            buildScopedMonitorTickets(
                session = session,
                usersRepository = usersRepository,
                tickets = salesRepository.getTicketsForDay(dayKey),
            ),
            visibleLotteryIds,
        )
        monitorWinnerTicketsState = filterMonitorTicketsForLotteries(
            buildScopedMonitorTickets(
                session = session,
                usersRepository = usersRepository,
                tickets = salesRepository.getAllTickets(),
            ),
            visibleLotteryIds,
        )
        monitorLotteriesState = visibleLotteries
        cashierSalesLimitsPayloadState = cashierSalesLimitRepository.exportPayload(session.adminId ?: session.userId)
        monitorNowUtcMsState = trustedClockRepository.getTrustedUtcMs()
        pendingSyncCountState = if (::ticketSyncQueueRepository.isInitialized) {
            ticketSyncQueueRepository.peekAll().size
        } else {
            0
        }
    }

    private fun setCashiersActive(cashierIds: List<String>, active: Boolean) {
        if (session.role != UserRole.ADMIN || cashierIds.isEmpty()) return
        val targetIds = cashierIds.toSet()
        val actionLabel = if (active) "activados" else "bloqueados"
        var latestPayload: String? = null
        usersRepository.getCashiers()
            .filter { it.id in targetIds || it.user in targetIds }
            .forEach { cashier ->
                val updated = cashier.copy(active = active, updatedAtEpochMs = System.currentTimeMillis())
                val payload = usersRepository.buildPayloadWithAccount(updated)
                usersRepository.cacheRawPayload(payload)
                latestPayload = payload
            }
        refreshMonitorData()
        latestPayload?.let { payload ->
            thread(name = "admin-monitor-cashier-active-sync") {
                val saved = runCatching { usersRemoteStore.upsertUsersPayload(payload) }.isSuccess
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (saved) "Cajeros $actionLabel en servidor" else "Cajeros $actionLabel local. Servidor no disponible",
                        if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                    refreshMonitorData()
                }
            }
        }
    }

    private fun syncMonitor(force: Boolean) {
        if (!monitorSyncInFlight.compareAndSet(false, true)) return
        thread(name = "admin-monitor-sync") {
            runCatching {
                refreshMonitorUsers(forceRemoteRefresh = force)
                val ticketState = operationalSyncCoordinator.syncTicketsForSession(
                    session = session,
                    lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                    force = force,
                )
                val rechargeSync = NativeRechargeCloudSyncCoordinator(rechargeRepository)
                resolveOperationalOwnerKeys(session).forEach { ownerKey ->
                    rechargeSync.hydrateOwner(ownerKey)
                }
                ticketState
            }.onSuccess { state ->
                lastRemoteUpdatedAt = state.remoteUpdatedAt ?: lastRemoteUpdatedAt
            }.also {
                monitorSyncInFlight.set(false)
                runOnUiThread { refreshMonitorData() }
            }
        }
    }

    private fun refreshMonitorUsers(forceRemoteRefresh: Boolean) {
        NativeUsersBootstrapper(
            usersRepository = usersRepository,
            usersRemoteStore = usersRemoteStore,
        ).bootstrap(forceRemoteRefresh = forceRemoteRefresh)
    }

    private fun subscribeRealtime() {
        if (!realtimeClient.isConfigured()) return
        resolveOperationalOwnerKeys(session).forEach { ownerKey ->
            realtimeSubscriptions += realtimeClient.subscribe(LotterynetRealtimeSubscription.ticketOwner(ownerKey)) {
                syncMonitor(force = true)
            }
        }
    }

    private fun shareMonitor(
        session: ActiveSession,
        dayKey: String,
        bancaSummary: com.lotterynet.pro.core.finance.FinanceSummary,
        rows: List<MonitorRow>,
        whatsappOnly: Boolean,
    ) {
        val bitmap = NativeBitmapExport.renderAdminMonitorBitmap(
            bancaName = session.banca ?: "LotteryNet",
            dayKey = dayKey,
            bancaSummary = bancaSummary,
            rows = rows.map(::buildMonitorRowLine),
        )
        NativeBitmapExport.shareBitmap(
            context = this,
            bitmap = bitmap,
            fileName = "monitor-admin-$dayKey.png",
            title = "Monitor admin",
            text = buildMonitorShareText(session, dayKey, bancaSummary, rows),
            whatsappOnly = whatsappOnly,
        )
    }

    private fun shareLotteryMonitor(
        lotteryLabel: String,
        viewLabel: String,
        statusLabel: String,
        rows: List<LotteryNumberMonitorRow>,
    ) {
        val text = buildLotteryMonitorShareText(
            bancaName = session.banca ?: "LotteryNet",
            dayKey = dayKey,
            lotteryLabel = lotteryLabel,
            viewLabel = viewLabel,
            statusLabel = statusLabel,
            rows = rows,
        )
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Monitor lotería $dayKey")
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "Compartir monitor de lotería",
            ),
        )
    }
}

internal fun filterMonitorLotteriesForSystemMode(
    lotteries: List<LotteryCatalogItem>,
    config: AdminSystemModeConfig,
): List<LotteryCatalogItem> {
    return when {
        config.lotteryModeEnabled && config.pickModeEnabled -> lotteries
        config.pickModeEnabled -> lotteries.filter(::isMonitorPickLottery)
        else -> lotteries.filterNot(::isMonitorPickLottery)
    }
}

internal fun filterMonitorTicketsForLotteries(
    tickets: List<TicketRecord>,
    allowedLotteryIds: Set<String>,
): List<TicketRecord> {
    if (allowedLotteryIds.isEmpty()) return emptyList()
    return tickets.mapNotNull { ticket ->
        val allowedPlays = ticket.plays.filter { play ->
            play.lotteryId in allowedLotteryIds || play.secondaryLotteryId in allowedLotteryIds
        }
        if (allowedPlays.isEmpty()) {
            null
        } else {
            ticket.copy(
                plays = allowedPlays,
                subtotal = allowedPlays.sumOf { it.amount },
                total = allowedPlays.sumOf { it.amount },
            )
        }
    }
}

private fun isMonitorPickLottery(lottery: LotteryCatalogItem): Boolean {
    val id = lottery.id.uppercase(Locale.US)
    return lottery.playCapabilities.supportsStraight ||
        lottery.playCapabilities.supportsBox ||
        lottery.type.contains("pick", ignoreCase = true) ||
        id.startsWith("US-P3-") ||
        id.startsWith("US-P4-")
}

@Composable
private fun AdminMonitorRoute(
    session: ActiveSession,
    dayKey: String,
    rows: List<MonitorRow>,
    bancaSummary: com.lotterynet.pro.core.finance.FinanceSummary,
    tickets: List<TicketRecord>,
    winnerTickets: List<TicketRecord>,
    lotteries: List<LotteryCatalogItem>,
    cashierSalesLimitsPayload: String,
    nowUtcMs: Long,
    closePolicy: LotteryClosePolicy,
    operationTerritory: LotteryTerritory,
    pendingSyncCount: Int,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCashier: (MonitorRow) -> Unit,
    onShare: (List<MonitorRow>, Boolean) -> Unit,
    onShareLottery: (String, String, String, List<LotteryNumberMonitorRow>) -> Unit,
    onPrint: (List<MonitorRow>) -> Boolean,
    onSave: (List<MonitorRow>) -> Boolean,
    onSetCashierActive: (List<String>, Boolean) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveAdminMonitorLayout(visual.windowMode) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var filter by rememberSaveable { mutableStateOf("all") }
    var selectedLotteryId by rememberSaveable { mutableStateOf(ALL_MONITOR_LOTTERIES_ID) }
    var selectedCashierRowId by rememberSaveable { mutableStateOf(ALL_MONITOR_CASHIERS_ID) }
    var selectedPlayViewName by rememberSaveable { mutableStateOf(LotteryMonitorPlayView.QUINIELA.name) }
    var selectedWinnerPeriodName by rememberSaveable { mutableStateOf(LotteryWinnerMonitorPeriod.TODAY.name) }
    var selectedWinnerManualDay by rememberSaveable { mutableStateOf(dayKey) }
    val availablePlayViews = remember(lotteries) {
        resolveLotteryMonitorPlayViews(lotteries)
    }
    val selectedPlayView = remember(selectedPlayViewName, availablePlayViews) {
        resolveSelectedLotteryMonitorPlayView(selectedPlayViewName, availablePlayViews)
    }
    val selectedWinnerPeriod = remember(selectedWinnerPeriodName) {
        LotteryWinnerMonitorPeriod.entries.firstOrNull { it.name == selectedWinnerPeriodName } ?: LotteryWinnerMonitorPeriod.TODAY
    }
    val selectedLottery = remember(lotteries, selectedLotteryId) {
        lotteries.firstOrNull { it.id == selectedLotteryId }
    }
    val selectedCashierSellerKeys = remember(rows, selectedCashierRowId) {
        rows.firstOrNull { it.userId == selectedCashierRowId }
            ?.let { row -> setOf(row.userId, row.username).filter { it.isNotBlank() }.toSet() }
            ?: emptySet()
    }
    val monitorCashierSellerKeys = remember(rows, selectedCashierRowId, selectedCashierSellerKeys) {
        resolveMonitorCashierSellerKeys(rows, selectedCashierRowId, selectedCashierSellerKeys)
    }
    val monitorCashierLimits = remember(cashierSalesLimitsPayload, rows, selectedCashierRowId) {
        resolveMonitorCashierLimits(cashierSalesLimitsPayload, rows, selectedCashierRowId)
    }
    val selectedMonitorTickets = remember(tickets, selectedCashierRowId, selectedCashierSellerKeys) {
        filterMonitorTicketsBySelectedCashier(
            tickets = tickets,
            selectedCashierId = selectedCashierRowId,
            selectedSellerKeys = selectedCashierSellerKeys,
        )
    }
    val selectedWinnerTickets = remember(winnerTickets, selectedCashierRowId, selectedCashierSellerKeys) {
        filterMonitorTicketsBySelectedCashier(
            tickets = winnerTickets,
            selectedCashierId = selectedCashierRowId,
            selectedSellerKeys = selectedCashierSellerKeys,
        )
    }
    val actorLabelsByKey = remember(rows) { buildMonitorActorLabelsByKey(rows) }
    val lotteryRows = remember(selectedMonitorTickets, selectedLotteryId, selectedPlayView, actorLabelsByKey, monitorCashierSellerKeys, monitorCashierLimits) {
        buildLotteryMonitorRows(
            tickets = selectedMonitorTickets,
            lotteryId = selectedLotteryId.takeIf { it != ALL_MONITOR_LOTTERIES_ID },
            view = selectedPlayView,
            actorLabelsByKey = actorLabelsByKey,
            cashierSellerKeys = monitorCashierSellerKeys,
            cashierLimits = monitorCashierLimits,
        )
    }
    val winnerRows = remember(selectedWinnerTickets, nowUtcMs, selectedWinnerPeriod, selectedWinnerManualDay, operationTerritory, actorLabelsByKey) {
        buildLotteryWinnerMonitorRows(
            tickets = selectedWinnerTickets,
            nowUtcMs = nowUtcMs,
            period = selectedWinnerPeriod,
            manualDayKey = selectedWinnerManualDay,
            operationTerritory = operationTerritory,
            actorLabelsByKey = actorLabelsByKey,
        )
    }
    val cashierDropdownOptions = remember(rows) {
        listOf(ALL_MONITOR_CASHIERS_ID to "Todos los cajeros") +
            rows.sortedWith(compareBy<MonitorRow>({ monitorCashierNumber(it.displayName) }, { it.displayName.lowercase(Locale.US) }))
                .map { it.userId to it.displayName }
    }
    LaunchedEffect(cashierDropdownOptions) {
        if (selectedCashierRowId.isBlank() || cashierDropdownOptions.none { it.first == selectedCashierRowId }) {
            selectedCashierRowId = ALL_MONITOR_CASHIERS_ID
        }
    }
    LaunchedEffect(availablePlayViews) {
        val resolved = resolveSelectedLotteryMonitorPlayView(selectedPlayViewName, availablePlayViews)
        if (resolved.name != selectedPlayViewName) {
            selectedPlayViewName = resolved.name
        }
    }
    val filteredRows = remember(rows, filter, selectedCashierRowId) {
        rows.filter { row ->
            val matchesCashier = selectedCashierRowId.isBlank() ||
                selectedCashierRowId == ALL_MONITOR_CASHIERS_ID ||
                row.userId == selectedCashierRowId
            matchesCashier && when (filter) {
                "sold" -> row.ventas > 0.0 || row.tickets > 0
                "pending_payment" -> row.premiosPendientes > 0.0
                "negative_balance" -> row.balance < 0.0
                "loss" -> row.resultado < 0.0
                "benefit" -> row.resultado > 0.0
                else -> true
            }
        }.sortedByDescending { monitorPriority(it) }
    }
    val activeCount = remember(rows) { rows.count { it.presence == "Activo" } }
    val blockedCount = remember(rows) { rows.count { it.presence == "Bloqueado" } }
    val pendingCount = remember(rows) { rows.count { it.premiosPendientes > 0.0 } }
    val syncContract = remember(pendingSyncCount) {
        if (pendingSyncCount > 0) {
            resolveTicketSaveSyncUiContract(
                stage = TicketSaveSyncStage.PENDING,
                detail = "$pendingSyncCount ticket(s) esperando servidor.",
            )
        } else {
            resolveTicketSaveSyncUiContract(stage = TicketSaveSyncStage.SYNCED)
        }
    }
    if (session.role == UserRole.SUPERVISOR) {
        SupervisorPanelRoute(
            rows = rows,
            tickets = tickets,
            lotteries = lotteries,
            cashierSalesLimitsPayload = cashierSalesLimitsPayload,
            nowUtcMs = nowUtcMs,
            closePolicy = closePolicy,
            operationTerritory = operationTerritory,
            syncContract = syncContract,
            onBack = onBack,
            onRefresh = onRefresh,
            onOpenCashier = onOpenCashier,
            onShareLottery = onShareLottery,
        )
        return
    }
    if (session.role == UserRole.ADMIN) {
        AdminCashierPanelRoute(
            rows = rows,
            tickets = tickets,
            lotteries = lotteries,
            cashierSalesLimitsPayload = cashierSalesLimitsPayload,
            nowUtcMs = nowUtcMs,
            closePolicy = closePolicy,
            operationTerritory = operationTerritory,
            syncContract = syncContract,
            onBack = onBack,
            onRefresh = onRefresh,
            onOpenCashier = onOpenCashier,
            onShareLottery = onShareLottery,
            onSetCashierActive = onSetCashierActive,
        )
        return
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = session.role,
                active = NativeBottomTab.DASHBOARD,
                onSelected = { tab -> openBottomTab(context, session.role, tab) },
            )
        },
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = visual.sizes.screenPaddingV + 16.dp),
                verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
            ) {
                item {
                    ScreenHeaderPanel(
                        title = "Monitor admin",
                        subtitle = "${session.banca ?: "LotteryNet"} · $dayKey",
                        onBack = onBack,
                        badgeLabel = "$activeCount activos",
                        badgeTone = gainColor(),
                    )
                }
                item {
                    CompactTicketSaveSyncStatus(contract = syncContract)
                }
                item {
                    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = layout.summaryPaddingVerticalDp.dp)) {
                        SectionHeader(title = "Acciones", meta = "Exportar")
                        CompactActionButton(
                            "Actualizar servidor",
                            onClick = {
                                Toast.makeText(context, "Actualizando monitor...", Toast.LENGTH_SHORT).show()
                                onRefresh()
                            },
                            icon = Icons.Rounded.Sync,
                            modifier = Modifier.fillMaxWidth(),
                            tone = ActionTone.Secondary,
                        )
                        if (layout.splitActions) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                CompactActionButton("WhatsApp", onClick = { onShare(filteredRows, true) }, icon = Icons.Rounded.Whatsapp, modifier = Modifier.weight(1f), tone = ActionTone.Success)
                                CompactActionButton("Compartir", onClick = { onShare(filteredRows, false) }, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f), tone = ActionTone.Primary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                CompactActionButton("Guardar", onClick = { onSave(filteredRows) }, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f), tone = ActionTone.Secondary)
                                CompactActionButton("Impr.", onClick = { onPrint(filteredRows) }, icon = Icons.Rounded.Print, modifier = Modifier.weight(1f), tone = ActionTone.Secondary)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                CompactActionButton("Impr.", onClick = { onPrint(filteredRows) }, icon = Icons.Rounded.Print, modifier = Modifier.weight(1f), tone = ActionTone.Secondary)
                                CompactActionButton("WhatsApp", onClick = { onShare(filteredRows, true) }, icon = Icons.Rounded.Whatsapp, modifier = Modifier.weight(1f), tone = ActionTone.Success)
                                CompactActionButton("Compartir", onClick = { onShare(filteredRows, false) }, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f), tone = ActionTone.Primary)
                                CompactActionButton("Guardar", onClick = { onSave(filteredRows) }, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f), tone = ActionTone.Secondary)
                            }
                        }
                    }
                }
                item {
                    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = layout.summaryPaddingVerticalDp.dp)) {
                        OperationalListHeader(title = "Resumen", meta = "${rows.size} cajeros")
                        MetricStrip(
                            items = listOf(
                                MetricStripItem("Activos", activeCount.toString(), gainColor()),
                                MetricStripItem("Bloqueados", blockedCount.toString(), if (blockedCount > 0) Color(0xFFB91C1C) else visual.colors.neutral),
                                MetricStripItem("Pendientes", pendingCount.toString(), warningColor()),
                            ),
                        )
                        if (layout.compactSummary) {
                            val bankResult = resolveOperationalReportNet(bancaSummary)
                            Text(
                                "Caja ${formatMonitorMoney(bancaSummary.cajaDisponible)} · Ventas ${formatMonitorMoney(bancaSummary.ventas)} · ${monitorResultLabel(bankResult)} ${formatMonitorMoney(bankResult)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = monitorResultTone(bankResult),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            val bankResult = resolveOperationalReportNet(bancaSummary)
                            MetricStrip(
                                items = listOf(
                                    MetricStripItem("Caja banca", formatMonitorMoney(bancaSummary.cajaDisponible), visual.colors.ink),
                                    MetricStripItem("Ventas", formatMonitorMoney(bancaSummary.ventas), gainColor()),
                                    MetricStripItem(monitorResultLabel(bankResult), formatMonitorMoney(bankResult), monitorResultTone(bankResult)),
                                ),
                            )
                        }
                    }
                }
                item {
                    CompactPanel(alt = true) {
                        OperationalListHeader(title = "Filtro rápido", meta = filterLabel(filter))
                        QuickFilterChips(
                            filters = adminMonitorFilterOptions(),
                            selectedId = filter,
                            onSelected = { filter = it },
                        )
                    }
                }
                item {
                    LotteryMonitorPanel(
                        lotteries = lotteries,
                        selectedLotteryId = selectedLotteryId,
                        onLotterySelected = { selectedLotteryId = it },
                        selectedView = selectedPlayView,
                        playViews = availablePlayViews,
                        onViewSelected = { selectedPlayViewName = it.name },
                        selectedLottery = selectedLottery,
                        nowUtcMs = nowUtcMs,
                        closePolicy = closePolicy,
                        operationTerritory = operationTerritory,
                        rows = lotteryRows,
                        onShare = { onShareLottery(selectedLottery?.name ?: "Todas", selectedPlayView.label, it, lotteryRows) },
                    )
                }
                item {
                    LotteryWinnerMonitorPanel(
                        period = selectedWinnerPeriod,
                        manualDayKey = selectedWinnerManualDay,
                        rows = winnerRows,
                        operationTerritory = operationTerritory,
                        onPeriodSelected = { selectedWinnerPeriodName = it.name },
                        onManualDaySelected = { selectedWinnerManualDay = it },
                    )
                }
                item {
                    OperationalListHeader(title = "Cajeros", meta = "${filteredRows.size} visibles")
                }
                item {
                    CompactPanel(alt = true) {
                        SectionHeader(title = "Selector de cajero", meta = "Ordenado por nombre")
                        MonitorDropdown(
                            modifier = Modifier.fillMaxWidth(),
                            label = cashierDropdownOptions.firstOrNull { it.first == selectedCashierRowId }?.second ?: "Seleccionar cajero",
                            options = cashierDropdownOptions,
                            onSelected = { selectedCashierRowId = it },
                        )
                    }
                }
                items(filteredRows, key = { it.userId }) { row ->
                    MonitorRowCard(row = row, onOpen = { onOpenCashier(row) }, layout = layout)
                }
            }
        }
    }
}

@Composable
private fun SupervisorPanelRoute(
    rows: List<MonitorRow>,
    tickets: List<TicketRecord>,
    lotteries: List<LotteryCatalogItem>,
    cashierSalesLimitsPayload: String,
    nowUtcMs: Long,
    closePolicy: LotteryClosePolicy,
    operationTerritory: LotteryTerritory,
    syncContract: com.lotterynet.pro.ui.common.TicketSaveSyncUiContract,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCashier: (MonitorRow) -> Unit,
    onShareLottery: (String, String, String, List<LotteryNumberMonitorRow>) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = androidx.compose.ui.platform.LocalContext.current
    var activeTab by rememberSaveable { mutableStateOf(AdminMonitorRoleSegment.CASHIERS.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("active") }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var quickActionRow by remember { mutableStateOf<MonitorRow?>(null) }
    var selectedLotteryId by rememberSaveable { mutableStateOf(ALL_MONITOR_LOTTERIES_ID) }
    var selectedCashierRowId by rememberSaveable { mutableStateOf(ALL_MONITOR_CASHIERS_ID) }
    var selectedPlayViewName by rememberSaveable { mutableStateOf(LotteryMonitorPlayView.QUINIELA.name) }
    val filteredRows = remember(rows, query, filter) {
        rows.filter { row ->
            val matchesQuery = query.isBlank() ||
                row.displayName.contains(query, ignoreCase = true) ||
                row.username.contains(query, ignoreCase = true) ||
                row.userId.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                "active" -> row.presence == "Activo"
                "blocked" -> row.presence == "Bloqueado"
                "sold" -> row.ventas > 0.0 || row.tickets > 0
                else -> true
            }
            matchesQuery && matchesFilter
        }.sortedWith(compareByDescending<MonitorRow> { it.ventas }.thenBy { it.displayName.lowercase(Locale.US) })
    }
    LaunchedEffect(filteredRows) {
        val visibleIds = filteredRows.map { it.userId }.toSet()
        selectedIds = selectedIds.filter { it in visibleIds }
    }
    val selectedRows = remember(rows, selectedIds) { rows.filter { it.userId in selectedIds } }
    val cashierDropdownOptions = remember(rows) {
        listOf(ALL_MONITOR_CASHIERS_ID to "Todos los cajeros") +
            rows.sortedWith(compareBy<MonitorRow>({ monitorCashierNumber(it.displayName) }, { it.displayName.lowercase(Locale.US) }))
                .map { it.userId to it.displayName }
    }
    val selectedCashierSellerKeys = remember(rows, selectedCashierRowId) {
        rows.firstOrNull { it.userId == selectedCashierRowId }
            ?.let { row -> setOf(row.userId, row.username).filter { it.isNotBlank() }.toSet() }
            ?: emptySet()
    }
    val monitorCashierSellerKeys = remember(rows, selectedCashierRowId, selectedCashierSellerKeys) {
        resolveMonitorCashierSellerKeys(rows, selectedCashierRowId, selectedCashierSellerKeys)
    }
    val monitorCashierLimits = remember(cashierSalesLimitsPayload, rows, selectedCashierRowId) {
        resolveMonitorCashierLimits(cashierSalesLimitsPayload, rows, selectedCashierRowId)
    }
    val selectedMonitorTickets = remember(tickets, selectedCashierRowId, selectedCashierSellerKeys) {
        filterMonitorTicketsBySelectedCashier(
            tickets = tickets,
            selectedCashierId = selectedCashierRowId,
            selectedSellerKeys = selectedCashierSellerKeys,
        )
    }
    val selectedLottery = remember(lotteries, selectedLotteryId) {
        lotteries.firstOrNull { it.id == selectedLotteryId }
    }
    val availablePlayViews = remember(lotteries) { resolveLotteryMonitorPlayViews(lotteries) }
    val selectedPlayView = remember(selectedPlayViewName, availablePlayViews) {
        resolveSelectedLotteryMonitorPlayView(selectedPlayViewName, availablePlayViews)
    }
    val actorLabelsByKey = remember(rows) { buildMonitorActorLabelsByKey(rows) }
    val lotteryRows = remember(selectedMonitorTickets, selectedLotteryId, selectedPlayView, actorLabelsByKey, monitorCashierSellerKeys, monitorCashierLimits) {
        buildLotteryMonitorRows(
            tickets = selectedMonitorTickets,
            lotteryId = selectedLotteryId.takeIf { it != ALL_MONITOR_LOTTERIES_ID },
            view = selectedPlayView,
            actorLabelsByKey = actorLabelsByKey,
            cashierSellerKeys = monitorCashierSellerKeys,
            cashierLimits = monitorCashierLimits,
        )
    }
    LaunchedEffect(cashierDropdownOptions) {
        if (cashierDropdownOptions.none { it.first == selectedCashierRowId }) {
            selectedCashierRowId = ALL_MONITOR_CASHIERS_ID
        }
    }
    LaunchedEffect(availablePlayViews) {
        val resolved = resolveSelectedLotteryMonitorPlayView(selectedPlayViewName, availablePlayViews)
        if (resolved.name != selectedPlayViewName) selectedPlayViewName = resolved.name
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = UserRole.SUPERVISOR,
                active = NativeBottomTab.DASHBOARD,
                onSelected = { tab -> openBottomTab(context, UserRole.SUPERVISOR, tab) },
            )
        },
    ) { innerPadding ->
        quickActionRow?.let { row ->
            CashierQuickActionMenu(
                row = row,
                role = UserRole.SUPERVISOR,
                onDismiss = { quickActionRow = null },
                onOpenDetail = {
                    quickActionRow = null
                    onOpenCashier(row)
                },
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 14.dp),
        ) {
            item {
                SupervisorPanelHeader(
                    title = "Panel Supervisor",
                    subtitle = "Mis cajeros · Monitoreo compacto",
                    onBack = onBack,
                    onRefresh = onRefresh,
                )
            }
            item { CompactTicketSaveSyncStatus(contract = syncContract) }
            item {
                SupervisorTopTabs(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                    onTickets = { startSafeNativeDestination(context, UserRole.SUPERVISOR, NativeDestination.TICKET_SUMMARY) },
                    onReport = { startSafeNativeDestination(context, UserRole.SUPERVISOR, NativeDestination.OPERATIONAL_REPORT) },
                )
            }
            if (activeTab == AdminMonitorRoleSegment.MONITOR.name) {
                item {
                    CompactPanel(alt = true) {
                        SectionHeader(title = "Cajero", meta = "Filtro de monitoreo")
                        MonitorDropdown(
                            modifier = Modifier.fillMaxWidth(),
                            label = cashierDropdownOptions.firstOrNull { it.first == selectedCashierRowId }?.second ?: "Todos los cajeros",
                            options = cashierDropdownOptions,
                            onSelected = { selectedCashierRowId = it },
                        )
                    }
                }
                item {
                    LotteryMonitorPanel(
                        lotteries = lotteries,
                        selectedLotteryId = selectedLotteryId,
                        onLotterySelected = { selectedLotteryId = it },
                        selectedView = selectedPlayView,
                        playViews = availablePlayViews,
                        onViewSelected = { selectedPlayViewName = it.name },
                        selectedLottery = selectedLottery,
                        nowUtcMs = nowUtcMs,
                        closePolicy = closePolicy,
                        operationTerritory = operationTerritory,
                        rows = lotteryRows,
                        onShare = { onShareLottery(selectedLottery?.name ?: "Todas", selectedPlayView.label, it, lotteryRows) },
                    )
                }
            } else {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    label = { Text("Buscar cajero") },
                )
            }
            item {
                QuickFilterChips(
                    filters = supervisorPanelFilterOptions(),
                    selectedId = filter,
                    onSelected = { filter = it },
                )
            }
            item {
                SupervisorSelectionBar(
                    selectedCount = selectedIds.size,
                    visibleCount = filteredRows.size,
                    allVisibleSelected = filteredRows.isNotEmpty() && filteredRows.all { it.userId in selectedIds },
                    onToggleVisible = {
                        selectedIds = if (filteredRows.all { it.userId in selectedIds }) {
                            emptyList()
                        } else {
                            filteredRows.map { it.userId }
                        }
                    },
                    onClear = { selectedIds = emptyList() },
                )
            }
            if (rows.isEmpty()) {
                item {
                    CompactPanel {
                        Text("No tienes cajeros asignados", style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontWeight = FontWeight.Bold)
                        Text("Cuando el admin te asigne cajeros aparecerán aquí.", style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                    }
                }
            } else if (filteredRows.isEmpty()) {
                item {
                    CompactPanel {
                        Text("Sin cajeros para este filtro", style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontWeight = FontWeight.Bold)
                        Text("Cambia búsqueda o filtro para ver otros cajeros reales.", style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                    }
                }
            } else {
                items(filteredRows, key = { it.userId }) { row ->
                    SupervisorCajeroCard(
                        row = row,
                        selected = row.userId in selectedIds,
                        onSelectedChange = { checked ->
                            selectedIds = if (checked) {
                                (selectedIds + row.userId).distinct()
                            } else {
                                selectedIds.filterNot { it == row.userId }
                            }
                        },
                        onOpen = { quickActionRow = row },
                    )
                }
            }
            item {
                SupervisorQuickActions(
                    hasSelection = selectedRows.isNotEmpty(),
                    onMode = { Toast.makeText(context, "Modo se cambia en Límites por el admin.", Toast.LENGTH_SHORT).show() },
                    onBlock = { Toast.makeText(context, "Supervisor no tiene permiso para bloquear cajeros.", Toast.LENGTH_SHORT).show() },
                    onReport = { startSafeNativeDestination(context, UserRole.SUPERVISOR, NativeDestination.OPERATIONAL_REPORT) },
                )
            }
            }
        }
    }
}

@Composable
private fun AdminCashierPanelRoute(
    rows: List<MonitorRow>,
    tickets: List<TicketRecord>,
    lotteries: List<LotteryCatalogItem>,
    cashierSalesLimitsPayload: String,
    nowUtcMs: Long,
    closePolicy: LotteryClosePolicy,
    operationTerritory: LotteryTerritory,
    syncContract: com.lotterynet.pro.ui.common.TicketSaveSyncUiContract,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenCashier: (MonitorRow) -> Unit,
    onShareLottery: (String, String, String, List<LotteryNumberMonitorRow>) -> Unit,
    onSetCashierActive: (List<String>, Boolean) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = androidx.compose.ui.platform.LocalContext.current
    var activeTab by rememberSaveable { mutableStateOf(AdminMonitorRoleSegment.CASHIERS.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var selectedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var quickActionRow by remember { mutableStateOf<MonitorRow?>(null) }
    var selectedLotteryId by rememberSaveable { mutableStateOf(ALL_MONITOR_LOTTERIES_ID) }
    var selectedCashierRowId by rememberSaveable { mutableStateOf(ALL_MONITOR_CASHIERS_ID) }
    var selectedPlayViewName by rememberSaveable { mutableStateOf(LotteryMonitorPlayView.QUINIELA.name) }
    val filteredRows = remember(rows, query, filter) {
        rows.filter { row ->
            val matchesQuery = query.isBlank() ||
                row.displayName.contains(query, ignoreCase = true) ||
                row.username.contains(query, ignoreCase = true) ||
                row.userId.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                "active" -> row.presence == "Activo"
                "blocked" -> row.presence == "Bloqueado"
                "sold" -> row.ventas > 0.0 || row.tickets > 0
                "loss" -> row.resultado < 0.0
                "benefit" -> row.resultado > 0.0
                else -> true
            }
            matchesQuery && matchesFilter
        }.sortedWith(compareByDescending<MonitorRow> { kotlin.math.abs(it.resultado) }.thenBy { it.displayName.lowercase(Locale.US) })
    }
    LaunchedEffect(filteredRows) {
        val visibleIds = filteredRows.map { it.userId }.toSet()
        selectedIds = selectedIds.filter { it in visibleIds }
    }
    val cashierDropdownOptions = remember(rows) {
        listOf(ALL_MONITOR_CASHIERS_ID to "Todos los cajeros") +
            rows.sortedWith(compareBy<MonitorRow>({ monitorCashierNumber(it.displayName) }, { it.displayName.lowercase(Locale.US) }))
                .map { it.userId to it.displayName }
    }
    val selectedCashierSellerKeys = remember(rows, selectedCashierRowId) {
        rows.firstOrNull { it.userId == selectedCashierRowId }
            ?.let { row -> setOf(row.userId, row.username).filter { it.isNotBlank() }.toSet() }
            ?: emptySet()
    }
    val monitorCashierSellerKeys = remember(rows, selectedCashierRowId, selectedCashierSellerKeys) {
        resolveMonitorCashierSellerKeys(rows, selectedCashierRowId, selectedCashierSellerKeys)
    }
    val monitorCashierLimits = remember(cashierSalesLimitsPayload, rows, selectedCashierRowId) {
        resolveMonitorCashierLimits(cashierSalesLimitsPayload, rows, selectedCashierRowId)
    }
    val selectedMonitorTickets = remember(tickets, selectedCashierRowId, selectedCashierSellerKeys) {
        filterMonitorTicketsBySelectedCashier(
            tickets = tickets,
            selectedCashierId = selectedCashierRowId,
            selectedSellerKeys = selectedCashierSellerKeys,
        )
    }
    val selectedLottery = remember(lotteries, selectedLotteryId) {
        lotteries.firstOrNull { it.id == selectedLotteryId }
    }
    val availablePlayViews = remember(lotteries) { resolveLotteryMonitorPlayViews(lotteries) }
    val selectedPlayView = remember(selectedPlayViewName, availablePlayViews) {
        resolveSelectedLotteryMonitorPlayView(selectedPlayViewName, availablePlayViews)
    }
    val actorLabelsByKey = remember(rows) { buildMonitorActorLabelsByKey(rows) }
    val lotteryRows = remember(selectedMonitorTickets, selectedLotteryId, selectedPlayView, actorLabelsByKey, monitorCashierSellerKeys, monitorCashierLimits) {
        buildLotteryMonitorRows(
            tickets = selectedMonitorTickets,
            lotteryId = selectedLotteryId.takeIf { it != ALL_MONITOR_LOTTERIES_ID },
            view = selectedPlayView,
            actorLabelsByKey = actorLabelsByKey,
            cashierSellerKeys = monitorCashierSellerKeys,
            cashierLimits = monitorCashierLimits,
        )
    }
    LaunchedEffect(cashierDropdownOptions) {
        if (cashierDropdownOptions.none { it.first == selectedCashierRowId }) {
            selectedCashierRowId = ALL_MONITOR_CASHIERS_ID
        }
    }
    LaunchedEffect(availablePlayViews) {
        val resolved = resolveSelectedLotteryMonitorPlayView(selectedPlayViewName, availablePlayViews)
        if (resolved.name != selectedPlayViewName) selectedPlayViewName = resolved.name
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = UserRole.ADMIN,
                active = NativeBottomTab.DASHBOARD,
                onSelected = { tab -> openBottomTab(context, UserRole.ADMIN, tab) },
            )
        },
    ) { innerPadding ->
        quickActionRow?.let { row ->
            CashierQuickActionMenu(
                row = row,
                role = UserRole.ADMIN,
                onDismiss = { quickActionRow = null },
                onOpenDetail = {
                    quickActionRow = null
                    onOpenCashier(row)
                },
            )
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 14.dp),
        ) {
            item {
                SupervisorPanelHeader(
                    title = "Panel Admin",
                    subtitle = "Cajeros · Administración compacta",
                    onBack = onBack,
                    onRefresh = onRefresh,
                )
            }
            item { CompactTicketSaveSyncStatus(contract = syncContract) }
            item {
                AdminCashierTopTabs(
                    context = context,
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                )
            }
            if (activeTab == AdminMonitorRoleSegment.MONITOR.name) {
                item {
                    CompactPanel(alt = true) {
                        SectionHeader(title = "Cajero", meta = "Todos o uno")
                        MonitorDropdown(
                            modifier = Modifier.fillMaxWidth(),
                            label = cashierDropdownOptions.firstOrNull { it.first == selectedCashierRowId }?.second ?: "Todos los cajeros",
                            options = cashierDropdownOptions,
                            onSelected = { selectedCashierRowId = it },
                        )
                    }
                }
                item {
                    LotteryMonitorPanel(
                        lotteries = lotteries,
                        selectedLotteryId = selectedLotteryId,
                        onLotterySelected = { selectedLotteryId = it },
                        selectedView = selectedPlayView,
                        playViews = availablePlayViews,
                        onViewSelected = { selectedPlayViewName = it.name },
                        selectedLottery = selectedLottery,
                        nowUtcMs = nowUtcMs,
                        closePolicy = closePolicy,
                        operationTerritory = operationTerritory,
                        rows = lotteryRows,
                        onShare = { onShareLottery(selectedLottery?.name ?: "Todas", selectedPlayView.label, it, lotteryRows) },
                    )
                }
            } else {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    label = { Text("Buscar cajero") },
                )
            }
            item {
                CompactSegmentedSelector(
                    options = adminCashierPanelFilterOptions(),
                    selectedId = filter,
                    onSelected = { filter = it },
                    columns = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SupervisorSelectionBar(
                    selectedCount = selectedIds.size,
                    visibleCount = filteredRows.size,
                    allVisibleSelected = filteredRows.isNotEmpty() && filteredRows.all { it.userId in selectedIds },
                    onToggleVisible = {
                        selectedIds = if (filteredRows.all { it.userId in selectedIds }) emptyList() else filteredRows.map { it.userId }
                    },
                    onClear = { selectedIds = emptyList() },
                )
            }
            if (rows.isEmpty()) {
                item {
                    CompactPanel {
                        Text("No hay cajeros creados", style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontWeight = FontWeight.Bold)
                        Text("Crea cajeros desde Usuarios para administrarlos aquí.", style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                    }
                }
            } else if (filteredRows.isEmpty()) {
                item {
                    CompactPanel {
                        Text("Sin cajeros para este filtro", style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontWeight = FontWeight.Bold)
                        Text("Cambia búsqueda o filtro para ver otros cajeros reales.", style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                    }
                }
            } else {
                items(filteredRows, key = { it.userId }) { row ->
                    AdminCajeroCard(
                        row = row,
                        selected = row.userId in selectedIds,
                        onSelectedChange = { checked ->
                            selectedIds = if (checked) (selectedIds + row.userId).distinct() else selectedIds.filterNot { it == row.userId }
                        },
                        onOpen = { quickActionRow = row },
                        onActiveChange = { active -> onSetCashierActive(listOf(row.userId), active) },
                    )
                }
            }
            item {
                AdminQuickActions(
                    hasSelection = selectedIds.isNotEmpty(),
                    onMode = { startSafeNativeDestination(context, UserRole.ADMIN, NativeDestination.ADMIN_LIMITS) },
                    onBlock = { onSetCashierActive(selectedIds, false) },
                    onReport = { startSafeNativeDestination(context, UserRole.ADMIN, NativeDestination.OPERATIONAL_REPORT) },
                    onTickets = { startSafeNativeDestination(context, UserRole.ADMIN, NativeDestination.TICKET_SUMMARY) },
                )
            }
            }
        }
    }
}

@Composable
private fun AdminCashierTopTabs(
    context: android.content.Context,
    activeTab: String,
    onTabSelected: (String) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        CompactActionButton("Cajeros", onClick = { onTabSelected(AdminMonitorRoleSegment.CASHIERS.name) }, icon = Icons.Rounded.Groups, active = activeTab == AdminMonitorRoleSegment.CASHIERS.name, tone = if (activeTab == AdminMonitorRoleSegment.CASHIERS.name) ActionTone.IntenseBlue else ActionTone.Secondary, modifier = Modifier.weight(1f))
        CompactActionButton("Monit.", onClick = { onTabSelected(AdminMonitorRoleSegment.MONITOR.name) }, icon = Icons.Rounded.QueryStats, active = activeTab == AdminMonitorRoleSegment.MONITOR.name, modifier = Modifier.weight(1f), tone = if (activeTab == AdminMonitorRoleSegment.MONITOR.name) ActionTone.IntenseBlue else ActionTone.Secondary)
        CompactActionButton("Tickets", onClick = { startSafeNativeDestination(context, UserRole.ADMIN, NativeDestination.TICKET_SUMMARY) }, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f), tone = ActionTone.Secondary)
        CompactActionButton("Reporte", onClick = { startSafeNativeDestination(context, UserRole.ADMIN, NativeDestination.OPERATIONAL_REPORT) }, icon = Icons.Rounded.QueryStats, modifier = Modifier.weight(1f), tone = ActionTone.Secondary)
    }
}

@Composable
private fun CashierQuickActionMenu(
    row: MonitorRow,
    role: UserRole,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val visual = rememberLotteryNetVisualSpec()
    val contract = resolveCashierCardActionContract(visual.windowMode)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = row.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${row.username} · ${row.tickets} tickets · ${formatMonitorMoney(row.ventas)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                contract.actions.forEach { action ->
                    val onClick = when (action) {
                        "Detalle" -> onOpenDetail
                        "Tickets" -> {
                            {
                                onDismiss()
                                openCashierScopedTickets(context, role, row)
                            }
                        }
                        "Reporte" -> {
                            {
                                onDismiss()
                                openCashierScopedDestination(context, role, NativeDestination.OPERATIONAL_REPORT, row)
                            }
                        }
                        "Cuadre" -> {
                            {
                                onDismiss()
                                openCashierScopedDestination(context, role, NativeDestination.FINANCE, row)
                            }
                        }
                        else -> {
                            {
                                onDismiss()
                                openCashierScopedLookup(context, role, row)
                            }
                        }
                    }
                    CompactActionButton(
                        label = action,
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                        tone = when (action) {
                            "Detalle" -> ActionTone.IntenseBlue
                            "Cobros" -> ActionTone.Success
                            else -> ActionTone.Secondary
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

private fun cashierScopedKey(row: MonitorRow): String {
    return row.userId.ifBlank { row.username }
}

private fun openCashierScopedTickets(
    context: android.content.Context,
    role: UserRole,
    row: MonitorRow,
) {
    val intent = safeNativeDestinationIntent(context, role, NativeDestination.TICKET_SUMMARY).apply {
        putExtra(TicketSummaryActivity.EXTRA_OWNER_SCOPE, TicketOwnerScope.CASHIER.name)
        putExtra(TicketSummaryActivity.EXTRA_CASHIER_KEY, cashierScopedKey(row))
    }
    context.startActivity(intent)
}

private fun openCashierScopedDestination(
    context: android.content.Context,
    role: UserRole,
    destination: NativeDestination,
    row: MonitorRow,
) {
    val intent = safeNativeDestinationIntent(context, role, destination).apply {
        putExtra(TicketSummaryActivity.EXTRA_OWNER_SCOPE, TicketOwnerScope.CASHIER.name)
        putExtra(TicketSummaryActivity.EXTRA_CASHIER_KEY, cashierScopedKey(row))
    }
    context.startActivity(intent)
}

private fun openCashierScopedLookup(
    context: android.content.Context,
    role: UserRole,
    row: MonitorRow,
) {
    val intent = safeNativeDestinationIntent(context, role, NativeDestination.TICKET_LOOKUP, "pagar").apply {
        putExtra(TicketLookupActivity.EXTRA_INITIAL_QUERY, row.username.ifBlank { row.displayName })
    }
    context.startActivity(intent)
}

@Composable
private fun AdminCajeroCard(
    row: MonitorRow,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val active = row.presence == "Activo"
    val resultTone = monitorResultTone(row.resultado)
    val resultLabel = monitorResultLabel(row.resultado)
    CashierMonitorDenseCard(
        row = row,
        selected = selected,
        onSelectedChange = onSelectedChange,
        onOpen = onOpen,
        active = active,
        resultLabel = resultLabel,
        resultTone = resultTone,
        onActiveChange = onActiveChange,
    )
}

@Composable
private fun CashierMonitorDenseCard(
    row: MonitorRow,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    active: Boolean,
    resultLabel: String,
    resultTone: Color,
    onActiveChange: ((Boolean) -> Unit)? = null,
) {
    val visual = rememberLotteryNetVisualSpec()
    val cardContract = resolveCashierMonitorCardVisualContract()
    val statusTone = if (active) gainColor() else Color(0xFFB91C1C)
    val statusBackground by animateColorAsState(
        targetValue = if (active) gainColor().copy(alpha = 0.045f) else Color(0xFFB91C1C).copy(alpha = 0.055f),
        label = "cashierDenseStatusBackground",
    )
    CompactPanel(
        modifier = Modifier.clickable(onClick = onOpen),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 8.dp,
            vertical = cardContract.rowPaddingVerticalDp.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = cardContract.minTouchTargetDp.dp)
                .background(statusBackground, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            Box(
                modifier = Modifier
                    .background(Color(0xFFE8F0FF), RoundedCornerShape(16.dp))
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = Color(0xFF155BD6))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        row.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = visual.colors.ink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CompactStatusBadge(label = if (active) "Activo" else "Bloq.", tone = statusTone)
                }
                Text(
                    "${row.tickets} tickets · ${row.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                InlineMonitorMetrics(
                    sales = formatMonitorMoney(row.ventas),
                    resultLabel = resultLabel,
                    result = formatMonitorMoney(row.resultado),
                    resultTone = resultTone,
                    pending = formatMonitorMoney(row.premiosPendientes),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (onActiveChange != null) {
                    CompactToggleSwitch(
                        checked = active,
                        onCheckedChange = onActiveChange,
                        enabled = true,
                        tone = if (active) ActionTone.Success else ActionTone.Danger,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Ver cajero",
                    tint = visual.colors.actionPrimary,
                )
            }
        }
    }
}

@Composable
private fun InlineMonitorMetrics(
    sales: String,
    resultLabel: String,
    result: String,
    resultTone: Color,
    pending: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CompactInlineMetric(label = "Ventas", value = sales, tone = gainColor(), modifier = Modifier.weight(1f))
        CompactInlineMetric(label = resultLabel, value = result, tone = resultTone, modifier = Modifier.weight(1f))
        CompactInlineMetric(label = "Pend.", value = pending, tone = warningColor(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompactInlineMetric(
    label: String,
    value: String,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF64748B), maxLines = 1)
        Text(value, style = MaterialTheme.typography.labelSmall, color = tone, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AdminQuickActions(
    hasSelection: Boolean,
    onMode: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
    onTickets: () -> Unit,
) {
    CompactPanel {
        OperationalListHeader(title = "Accion rapida", meta = if (hasSelection) "Selección activa" else "Selecciona cajeros")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactActionButton("Modo", onClick = onMode, icon = Icons.Rounded.PointOfSale, modifier = Modifier.weight(1f), tone = ActionTone.IntenseBlue)
            CompactActionButton("Bloquear", onClick = onBlock, modifier = Modifier.weight(1f), tone = ActionTone.Danger, enabled = hasSelection)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactActionButton("Tickets", onClick = onTickets, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f), tone = ActionTone.Secondary)
            CompactActionButton("Reporte", onClick = onReport, icon = Icons.Rounded.QueryStats, modifier = Modifier.weight(1f), tone = ActionTone.Warning)
        }
    }
}

internal fun adminCashierPanelFilterOptions(): List<QuickFilterChip> = listOf(
    QuickFilterChip("all", "Todo"),
    QuickFilterChip("active", "Activo"),
    QuickFilterChip("blocked", "Bloq."),
    QuickFilterChip("sold", "Venta"),
    QuickFilterChip("loss", "Pérd."),
    QuickFilterChip("benefit", "Benef."),
)

@Composable
private fun SupervisorPanelHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
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
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                    .clickable {
                        Toast.makeText(context, "Actualizando monitor...", Toast.LENGTH_SHORT).show()
                        onRefresh()
                    }
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = "Actualizar monitor", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SupervisorTopTabs(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    onTickets: () -> Unit,
    onReport: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        CompactActionButton("Mis cajeros", onClick = { onTabSelected(AdminMonitorRoleSegment.CASHIERS.name) }, icon = Icons.Rounded.Groups, active = activeTab == AdminMonitorRoleSegment.CASHIERS.name, tone = if (activeTab == AdminMonitorRoleSegment.CASHIERS.name) ActionTone.IntenseBlue else ActionTone.Secondary, modifier = Modifier.weight(1f))
        CompactActionButton("Monitoreo", onClick = { onTabSelected(AdminMonitorRoleSegment.MONITOR.name) }, icon = Icons.Rounded.QueryStats, active = activeTab == AdminMonitorRoleSegment.MONITOR.name, modifier = Modifier.weight(1f), tone = if (activeTab == AdminMonitorRoleSegment.MONITOR.name) ActionTone.IntenseBlue else ActionTone.Secondary)
        CompactActionButton("Tickets", onClick = onTickets, icon = Icons.Rounded.Download, modifier = Modifier.weight(1f), tone = ActionTone.Secondary)
        CompactActionButton("Reporte", onClick = onReport, icon = Icons.Rounded.QueryStats, modifier = Modifier.weight(1f), tone = ActionTone.Secondary)
    }
}

@Composable
private fun SupervisorSelectionBar(
    selectedCount: Int,
    visibleCount: Int,
    allVisibleSelected: Boolean,
    onToggleVisible: () -> Unit,
    onClear: () -> Unit,
) {
    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = allVisibleSelected, onCheckedChange = { onToggleVisible() })
            Text("$selectedCount seleccionados", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            CompactActionButton("Visibles", onClick = onToggleVisible, icon = Icons.Rounded.Groups, tone = ActionTone.Secondary, enabled = visibleCount > 0)
            CompactActionButton("Limpiar", onClick = onClear, tone = ActionTone.Secondary, enabled = selectedCount > 0)
        }
    }
}

@Composable
private fun SupervisorCajeroCard(
    row: MonitorRow,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val active = row.presence == "Activo"
    val resultTone = monitorResultTone(row.resultado)
    val resultLabel = monitorResultLabel(row.resultado)
    CashierMonitorDenseCard(
        row = row,
        selected = selected,
        onSelectedChange = onSelectedChange,
        onOpen = onOpen,
        active = active,
        resultLabel = resultLabel,
        resultTone = resultTone,
    )
}

@Composable
private fun SupervisorQuickActions(
    hasSelection: Boolean,
    onMode: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit,
) {
    CompactPanel {
        OperationalListHeader(title = "Accion rapida", meta = if (hasSelection) "Selección activa" else "Selecciona cajeros")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactActionButton("Aplicar modo", onClick = onMode, icon = Icons.Rounded.PointOfSale, modifier = Modifier.weight(1f), tone = ActionTone.IntenseBlue, enabled = hasSelection)
            CompactActionButton("Bloquear", onClick = onBlock, modifier = Modifier.weight(1f), tone = ActionTone.Danger, enabled = hasSelection)
            CompactActionButton("Reporte", onClick = onReport, icon = Icons.Rounded.QueryStats, modifier = Modifier.weight(1f), tone = ActionTone.Warning)
        }
    }
}

internal fun supervisorPanelFilterOptions(): List<QuickFilterChip> = listOf(
    QuickFilterChip("all", "Todos"),
    QuickFilterChip("active", "Activos"),
    QuickFilterChip("blocked", "Bloqueados"),
    QuickFilterChip("sold", "Venta"),
)

@Composable
private fun MonitorMetric(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
private fun MonitorRowCard(
    row: MonitorRow,
    onOpen: () -> Unit,
    layout: AdminMonitorLayoutContract,
) {
    val visual = rememberLotteryNetVisualSpec()
    val compact = layout.compactCards
    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = layout.rowPaddingVerticalDp.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.displayName,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        color = visual.colors.ink,
                        fontWeight = FontWeight.Bold,
                    )
                    CompactStatusBadge(
                        label = row.presence,
                        tone = if (row.presence == "Activo") gainColor() else visual.colors.neutral,
                    )
                }
                Text(
                    "${row.username} · ${row.lastSeenLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp), modifier = Modifier.fillMaxWidth()) {
                    MonitorStat(modifier = Modifier.weight(1f), icon = Icons.Rounded.PointOfSale, label = "Ventas", value = formatMonitorMoney(row.ventas), layout = layout)
                    MonitorStat(modifier = Modifier.weight(1f), icon = Icons.Rounded.PhoneAndroid, label = "Recargas", value = formatMonitorMoney(row.recargas), layout = layout)
                    MonitorStat(modifier = Modifier.weight(1f), icon = Icons.Rounded.QueryStats, label = "Caja", value = formatMonitorMoney(row.caja), layout = layout)
                }
                val resultTone = monitorResultTone(row.resultado)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactStatusBadge(
                        label = monitorResultLabel(row.resultado),
                        tone = resultTone,
                    )
                    Text(
                        formatMonitorMoney(row.resultado),
                        style = MaterialTheme.typography.bodySmall,
                        color = resultTone,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (compact) {
                    Text(
                        "Tickets ${row.tickets} · Premios ${formatMonitorMoney(row.premiosPendientes)} · Bal. ${formatMonitorMoney(row.balance)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    Text(
                        "Activos ${row.activos} · Ganadores ${row.ganadores} · Pagados ${row.pagados}",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.ink,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "Tickets ${row.tickets} · Pendientes ${formatMonitorMoney(row.premiosPendientes)} · Balance ${formatMonitorMoney(row.balance)}",
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
private fun MonitorStat(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    layout: AdminMonitorLayoutContract,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(modifier = modifier, alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(layout.statPaddingDp.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, contentDescription = null, tint = visual.colors.neutral)
            Text(label, style = MaterialTheme.typography.labelMedium, color = visual.colors.muted)
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = visual.colors.ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LotteryMonitorPanel(
    lotteries: List<LotteryCatalogItem>,
    selectedLotteryId: String,
    onLotterySelected: (String) -> Unit,
    selectedView: LotteryMonitorPlayView,
    playViews: List<LotteryMonitorPlayView>,
    onViewSelected: (LotteryMonitorPlayView) -> Unit,
    selectedLottery: LotteryCatalogItem?,
    nowUtcMs: Long,
    closePolicy: LotteryClosePolicy,
    operationTerritory: LotteryTerritory,
    rows: List<LotteryNumberMonitorRow>,
    onShare: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var showAllRows by rememberSaveable { mutableStateOf(false) }
    val status = remember(selectedLottery, nowUtcMs, operationTerritory) {
        selectedLottery?.let { lottery ->
            val decision = closePolicy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = operationTerritory,
                nowUtcMs = nowUtcMs,
            )
            if (decision.isClosed) "Cerrada" else decision.reason.orEmpty().ifBlank { "Abierta" }
        } ?: "Todas"
    }
    CompactPanel {
        OperationalListHeader(title = "Lotería", meta = status)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selectedLottery != null) {
                LotteryLogo(
                    assetPath = selectedLottery.logoAssetPath,
                    fallback = selectedLottery.name,
                    modifier = Modifier.weight(0.18f),
                    fillBounds = true,
                )
            }
            MonitorDropdown(
                modifier = Modifier.weight(1f),
                label = selectedLottery?.name ?: "Todas",
                options = listOf<Pair<String, String>>(ALL_MONITOR_LOTTERIES_ID to "Todas") + lotteries.map { it.id to it.name },
                onSelected = onLotterySelected,
            )
        }
        Spacer(Modifier.padding(top = 2.dp))
        CompactSegmentedSelector(
            options = playViews.map { QuickFilterChip(it.name, it.label) },
            selectedId = selectedView.name,
            onSelected = { id -> playViews.firstOrNull { it.name == id }?.let(onViewSelected) },
            columns = if (playViews.size <= 2) 2 else 3,
            modifier = Modifier.fillMaxWidth(),
        )
        val visibleRows = remember(rows) { resolveLotteryMonitorVisibleRows(rows) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CompactActionButton(
                "Compartir lotería",
                onClick = { onShare(status) },
                icon = Icons.Rounded.Share,
                modifier = Modifier.weight(1f),
                tone = ActionTone.Primary,
            )
            if (visibleRows.showViewAll) {
                CompactActionButton(
                    visibleRows.overflowActionLabel,
                    onClick = { showAllRows = true },
                    icon = Icons.Rounded.QueryStats,
                    modifier = Modifier.weight(1f),
                    tone = ActionTone.Secondary,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            visibleRows.visibleRows.forEach { row ->
                LotteryNumberMonitorRowItem(row = row)
            }
            if (visibleRows.hiddenCount > 0) {
                Text(
                    "+${visibleRows.hiddenCount} combinaciones más · ${visibleRows.totalCount} total",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
            }
        }
    }
    if (showAllRows) {
        AlertDialog(
            onDismissRequest = { showAllRows = false },
            title = { Text("Todas las combinaciones") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    items(rows, key = { "${it.displayNumber}-${it.amount}-${it.playsCount}" }) { row ->
                        LotteryNumberMonitorRowItem(row = row)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllRows = false }) {
                    Text("Cerrar")
                }
            },
        )
    }
}

@Composable
private fun MonitorDropdown(
    modifier: Modifier = Modifier,
    label: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
    maxMenuHeightDp: Int = 320,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier.clickable { expanded = true },
        color = visual.colors.actionPrimarySurface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, visual.colors.actionPrimary.copy(alpha = 0.28f)),
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(Icons.Rounded.Groups, contentDescription = null, tint = visual.colors.actionPrimary, modifier = Modifier.heightIn(max = 18.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = visual.colors.ink, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = visual.colors.actionPrimary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxMenuHeightDp.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.second, fontWeight = FontWeight.Bold, color = visual.colors.ink) },
                            onClick = {
                                expanded = false
                                onSelected(option.first)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LotteryNumberMonitorRowItem(row: LotteryNumberMonitorRow) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panel, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.displayNumber, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            val limitLine = row.remainingAmount?.let { remaining ->
                "Cajeros ${formatMonitorMoney(row.cashierAmount)} · queda ${formatMonitorMoney(remaining)}"
            }
            Text(
                limitLine ?: "${row.playsCount} jugada(s) · ${row.actors.joinToString(", ").ifBlank { "sin cajero" }}",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatMonitorMoney(row.amount), style = MaterialTheme.typography.titleSmall, color = gainColor(), fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LotteryWinnerMonitorPanel(
    period: LotteryWinnerMonitorPeriod,
    manualDayKey: String,
    rows: List<LotteryWinnerMonitorRow>,
    operationTerritory: LotteryTerritory,
    onPeriodSelected: (LotteryWinnerMonitorPeriod) -> Unit,
    onManualDaySelected: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = androidx.compose.ui.platform.LocalContext.current
    val totalPrize = remember(rows) { rows.sumOf { it.prizeAmount } }
    CompactPanel {
        OperationalListHeader(title = "Números ganadores", meta = "${rows.size} ticket(s)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MonitorDropdown(
                modifier = Modifier.weight(1f),
                label = period.label,
                options = LotteryWinnerMonitorPeriod.entries.map { it.name to it.label },
                onSelected = { id ->
                    LotteryWinnerMonitorPeriod.entries.firstOrNull { it.name == id }?.let(onPeriodSelected)
                },
            )
            if (period == LotteryWinnerMonitorPeriod.MANUAL) {
                CompactActionButton(
                    label = manualDayKey,
                    onClick = {
                        val selected = runCatching { LocalDate.parse(manualDayKey) }.getOrElse {
                            LocalDate.now(monitorZoneId(operationTerritory))
                        }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                onManualDaySelected(LocalDate.of(year, month + 1, day).toString())
                            },
                            selected.year,
                            selected.monthValue - 1,
                            selected.dayOfMonth,
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    tone = ActionTone.Secondary,
                )
            }
        }
        Text(
            text = "Total sacado: ${formatMonitorMoney(totalPrize)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        if (rows.isEmpty()) {
            Text(
                "Sin premios registrados en este periodo.",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                rows.take(30).forEach { row ->
                    LotteryWinnerMonitorRowItem(row)
                }
                if (rows.size > 30) {
                    Text(
                        "+${rows.size - 30} premios más",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun LotteryWinnerMonitorRowItem(row: LotteryWinnerMonitorRow) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panel, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${row.dayKey} · ${row.lotteryLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                row.displayPlays,
                style = MaterialTheme.typography.titleSmall,
                color = visual.colors.ink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "${row.actorLabel} · ${row.statusLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Text(
            formatMonitorMoney(row.prizeAmount),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class MonitorRow(
    val userId: String,
    val username: String,
    val displayName: String,
    val presence: String,
    val lastSeenLabel: String,
    val tickets: Int,
    val activos: Int,
    val ganadores: Int,
    val pagados: Int,
    val ventas: Double,
    val recargas: Double,
    val caja: Double,
    val premiosPendientes: Double,
    val resultado: Double,
    val balance: Double,
)

private fun buildMonitorActorLabelsByKey(rows: List<MonitorRow>): Map<String, String> {
    return buildActorLabelLookup(
        rows.flatMap { row ->
            listOf(row.userId to row.displayName, row.username to row.displayName, row.displayName to row.displayName)
        },
    )
}

internal enum class LotteryMonitorPlayView(
    val playType: String,
    val label: String,
) {
    QUINIELA("Q", "Quiniela"),
    PALE("P", "Pale"),
    TRIPLETA("T", "Tripleta"),
    SUPER_PALE("SP", "Super Pale"),
    PICK_3("P3", "Pick 3"),
    PICK_4("P4", "Pick 4"),
}

internal data class LotteryNumberMonitorRow(
    val displayNumber: String,
    val amount: Double,
    val playsCount: Int,
    val actors: List<String>,
    val cashierAmount: Double = amount,
    val limitAmount: Double? = null,
    val remainingAmount: Double? = null,
)

internal data class LotteryMonitorVisibleRowsContract(
    val visibleRows: List<LotteryNumberMonitorRow>,
    val hiddenCount: Int,
    val totalCount: Int,
    val showViewAll: Boolean,
    val overflowActionLabel: String,
)

internal enum class LotteryWinnerMonitorPeriod(val label: String) {
    TODAY("Hoy"),
    WEEK("Semana"),
    QUINCENA("Quincena"),
    MONTH("Mes"),
    MANUAL("Manual"),
}

internal data class LotteryWinnerMonitorRow(
    val ticketId: String,
    val dayKey: String,
    val lotteryLabel: String,
    val displayPlays: String,
    val actorLabel: String,
    val statusLabel: String,
    val prizeAmount: Double,
)

internal fun buildLotteryMonitorRows(
    tickets: List<TicketRecord>,
    lotteryId: String?,
    view: LotteryMonitorPlayView,
    actorLabelsByKey: Map<String, String> = emptyMap(),
    cashierSellerKeys: Set<String> = emptySet(),
    cashierLimits: CashierSalesLimitInputs? = null,
): List<LotteryNumberMonitorRow> {
    val buckets = linkedMapOf<String, MutableLotteryMonitorBucket>()
    tickets
        .asSequence()
        .filterNot(::isFinanceVoidStatus)
        .flatMap { ticket ->
            ticket.plays.map { play -> ticket to play }
        }
        .filter { (_, play) -> lotteryMonitorPlayMatchesView(play.playType, view) }
        .filter { (_, play) -> lotteryId == null || playMatchesLottery(play, lotteryId) }
        .forEach { (ticket, play) ->
            val key = lotteryMonitorKey(play, view)
            val bucket = buckets.getOrPut(key) { MutableLotteryMonitorBucket(displayNumber = key) }
            bucket.amount += play.amount
            bucket.playsCount += 1
            bucket.playTypes += play.playType.trim().uppercase(Locale.US)
            if (ticketCountsAgainstCashierLimit(ticket, cashierSellerKeys)) {
                bucket.cashierAmount += play.amount
            }
            val actor = resolveTicketActorLabel(ticket, actorLabelsByKey)
            if (!actor.isNullOrBlank()) bucket.actors += actor
        }

    return buckets.values
        .map { bucket ->
            val limitAmount = cashierLimits?.let { monitorCashierLimitForBucket(view, bucket.playTypes, it) }
            LotteryNumberMonitorRow(
                displayNumber = bucket.displayNumber,
                amount = bucket.amount,
                playsCount = bucket.playsCount,
                actors = bucket.actors.distinct(),
                cashierAmount = bucket.cashierAmount,
                limitAmount = limitAmount,
                remainingAmount = limitAmount?.let { (it - bucket.cashierAmount).coerceAtLeast(0.0) },
            )
        }
        .sortedWith(compareByDescending<LotteryNumberMonitorRow> { it.amount }.thenBy { it.displayNumber })
}

internal fun resolveLotteryMonitorPlayViews(lotteries: List<LotteryCatalogItem>): List<LotteryMonitorPlayView> {
    val hasPick = lotteries.any(::isMonitorPickLottery)
    val hasLottery = lotteries.any { !isMonitorPickLottery(it) }
    return buildList {
        if (hasLottery || (!hasPick && lotteries.isEmpty())) {
            add(LotteryMonitorPlayView.QUINIELA)
            add(LotteryMonitorPlayView.PALE)
            add(LotteryMonitorPlayView.TRIPLETA)
            add(LotteryMonitorPlayView.SUPER_PALE)
        }
        if (hasPick) {
            add(LotteryMonitorPlayView.PICK_3)
            add(LotteryMonitorPlayView.PICK_4)
        }
    }
}

internal fun resolveSelectedLotteryMonitorPlayView(
    selectedName: String,
    availableViews: List<LotteryMonitorPlayView>,
): LotteryMonitorPlayView {
    return availableViews.firstOrNull { it.name == selectedName }
        ?: availableViews.firstOrNull()
        ?: LotteryMonitorPlayView.QUINIELA
}

internal fun lotteryMonitorPlayMatchesView(
    playType: String,
    view: LotteryMonitorPlayView,
): Boolean {
    val normalized = playType.trim().uppercase(Locale.US)
    return when (view) {
        LotteryMonitorPlayView.QUINIELA,
        LotteryMonitorPlayView.PALE,
        LotteryMonitorPlayView.TRIPLETA,
        LotteryMonitorPlayView.SUPER_PALE -> normalized == view.playType
        LotteryMonitorPlayView.PICK_3 -> normalized in PICK_3_MONITOR_TYPES
        LotteryMonitorPlayView.PICK_4 -> normalized in PICK_4_MONITOR_TYPES
    }
}

internal fun resolveLotteryMonitorVisibleRows(
    rows: List<LotteryNumberMonitorRow>,
    maxVisibleRows: Int = LOTTERY_MONITOR_MAX_VISIBLE_ROWS,
): LotteryMonitorVisibleRowsContract {
    val safeMax = maxVisibleRows.coerceAtLeast(1)
    val visible = rows.take(safeMax)
    return LotteryMonitorVisibleRowsContract(
        visibleRows = visible,
        hiddenCount = (rows.size - visible.size).coerceAtLeast(0),
        totalCount = rows.size,
        showViewAll = rows.size > visible.size,
        overflowActionLabel = "Ver todo",
    )
}

internal fun filterMonitorTicketsBySelectedCashier(
    tickets: List<TicketRecord>,
    selectedCashierId: String,
    selectedSellerKeys: Set<String>,
): List<TicketRecord> {
    if (selectedCashierId.isBlank() || selectedCashierId == ALL_MONITOR_CASHIERS_ID) return tickets
    if (selectedSellerKeys.isEmpty()) return emptyList()
    return tickets.filter { ticket ->
        ticket.sellerId in selectedSellerKeys || ticket.sellerUser in selectedSellerKeys
    }
}

internal fun buildLotteryWinnerMonitorRows(
    tickets: List<TicketRecord>,
    nowUtcMs: Long,
    period: LotteryWinnerMonitorPeriod,
    manualDayKey: String,
    operationTerritory: LotteryTerritory,
    actorLabelsByKey: Map<String, String> = emptyMap(),
): List<LotteryWinnerMonitorRow> {
    val zoneId = monitorZoneId(operationTerritory)
    val range = resolveLotteryWinnerDateRange(nowUtcMs, period, manualDayKey, zoneId)
    return tickets
        .asSequence()
        .filterNot(::isFinanceVoidStatus)
        .filter { it.totalPrize > 0.0 || it.isPaidStatus() || it.status.equals("winner", true) || it.status.equals("ganador", true) }
        .map { ticket ->
            val day = Instant.ofEpochMilli(ticket.createdAtEpochMs).atZone(zoneId).toLocalDate()
            ticket to day
        }
        .filter { (_, day) -> !day.isBefore(range.first) && !day.isAfter(range.second) }
        .map { (ticket, day) ->
            LotteryWinnerMonitorRow(
                ticketId = ticket.id,
                dayKey = day.toString(),
                lotteryLabel = ticket.plays.firstOrNull()?.lotteryName.orEmpty().ifBlank { "Lotería" },
                displayPlays = ticket.plays.take(4).joinToString(" / ") { play ->
                    "${play.playType.uppercase(Locale.US)} ${formatPlayDisplayNumber(play.number, play.playType)}"
                }.ifBlank { ticket.serial ?: ticket.id },
                actorLabel = resolveTicketActorLabel(ticket, actorLabelsByKey),
                statusLabel = when {
                    ticket.isPaidStatus() -> "Pagado"
                    else -> "Pendiente"
                },
                prizeAmount = ticket.totalPrize.coerceAtLeast(0.0),
            )
        }
        .sortedWith(compareByDescending<LotteryWinnerMonitorRow> { it.dayKey }.thenByDescending { it.prizeAmount })
        .toList()
}

private fun resolveLotteryWinnerDateRange(
    nowUtcMs: Long,
    period: LotteryWinnerMonitorPeriod,
    manualDayKey: String,
    zoneId: ZoneId,
): Pair<LocalDate, LocalDate> {
    val today = Instant.ofEpochMilli(nowUtcMs).atZone(zoneId).toLocalDate()
    return when (period) {
        LotteryWinnerMonitorPeriod.TODAY -> today to today
        LotteryWinnerMonitorPeriod.WEEK -> today.minusDays(6) to today
        LotteryWinnerMonitorPeriod.QUINCENA -> today.minusDays(14) to today
        LotteryWinnerMonitorPeriod.MONTH -> today.withDayOfMonth(1) to today
        LotteryWinnerMonitorPeriod.MANUAL -> {
            val selected = runCatching { LocalDate.parse(manualDayKey) }.getOrElse { today }
            selected to selected
        }
    }
}

private fun monitorZoneId(operationTerritory: LotteryTerritory): ZoneId {
    return when (operationTerritory) {
        LotteryTerritory.USA -> ZoneId.of("America/New_York")
        LotteryTerritory.RD -> ZoneId.of("America/Santo_Domingo")
    }
}

private data class MutableLotteryMonitorBucket(
    val displayNumber: String,
    var amount: Double = 0.0,
    var cashierAmount: Double = 0.0,
    var playsCount: Int = 0,
    val actors: MutableList<String> = mutableListOf(),
    val playTypes: MutableSet<String> = linkedSetOf(),
)

private fun resolveMonitorCashierSellerKeys(
    rows: List<MonitorRow>,
    selectedCashierId: String,
    selectedSellerKeys: Set<String>,
): Set<String> {
    if (selectedCashierId.isNotBlank() && selectedCashierId != ALL_MONITOR_CASHIERS_ID) {
        return selectedSellerKeys
    }
    return rows
        .flatMap { row -> listOf(row.userId, row.username) }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
}

private fun resolveMonitorCashierLimits(
    payload: String,
    rows: List<MonitorRow>,
    selectedCashierId: String,
): CashierSalesLimitInputs {
    val selectedUsername = rows.firstOrNull { it.userId == selectedCashierId }?.username
    return decodeCashierUserSalesLimitInputs(payload, selectedUsername)
        ?: decodeCashierSalesLimitInputs(payload)
}

private fun ticketCountsAgainstCashierLimit(ticket: TicketRecord, cashierSellerKeys: Set<String>): Boolean {
    if (ticket.role == UserRole.ADMIN || ticket.role == UserRole.MASTER) return false
    if (cashierSellerKeys.isEmpty()) return ticket.role == UserRole.CASHIER
    return ticket.sellerId in cashierSellerKeys || ticket.sellerUser in cashierSellerKeys
}

private fun monitorCashierLimitForBucket(
    view: LotteryMonitorPlayView,
    playTypes: Set<String>,
    limits: CashierSalesLimitInputs,
): Double {
    return when (view) {
        LotteryMonitorPlayView.QUINIELA -> limits.quiniela
        LotteryMonitorPlayView.PALE -> limits.pale
        LotteryMonitorPlayView.TRIPLETA -> limits.tripleta
        LotteryMonitorPlayView.SUPER_PALE -> limits.superPale
        LotteryMonitorPlayView.PICK_3 -> {
            val hasBox = playTypes.any { it == "P3BOX" || it == "P3B" }
            val hasStraight = playTypes.any { it == "P3" }
            when {
                hasBox && !hasStraight -> limits.pick3Box
                hasStraight && !hasBox -> limits.pick3Straight
                else -> minOf(limits.pick3Straight, limits.pick3Box)
            }
        }
        LotteryMonitorPlayView.PICK_4 -> {
            val hasBox = playTypes.any { it == "P4BOX" || it == "P4B" }
            val hasStraight = playTypes.any { it == "P4" }
            when {
                hasBox && !hasStraight -> limits.pick4Box
                hasStraight && !hasBox -> limits.pick4Straight
                else -> minOf(limits.pick4Straight, limits.pick4Box)
            }
        }
    }
}

private fun playMatchesLottery(play: PlayItem, lotteryId: String): Boolean {
    return play.lotteryId == lotteryId || play.secondaryLotteryId == lotteryId
}

private fun lotteryMonitorKey(play: PlayItem, view: LotteryMonitorPlayView): String {
    return when (view) {
        LotteryMonitorPlayView.QUINIELA -> play.number.filter(Char::isDigit).takeLast(2).padStart(2, '0')
        LotteryMonitorPlayView.PALE,
        LotteryMonitorPlayView.SUPER_PALE -> formatPlayDisplayNumber(play.number, view.playType)
        LotteryMonitorPlayView.TRIPLETA -> formatTripletaMonitorNumber(play.number)
        LotteryMonitorPlayView.PICK_3,
        LotteryMonitorPlayView.PICK_4 -> formatPlayDisplayNumber(play.number, play.playType)
    }
}

private fun formatTripletaMonitorNumber(number: String): String {
    if (number.contains("/")) return number
    val cleaned = number.filter(Char::isDigit)
    return if (cleaned.length >= 6) {
        listOf(cleaned.take(2), cleaned.drop(2).take(2), cleaned.drop(4).take(2)).joinToString("/")
    } else {
        number
    }
}

private fun buildMonitorRows(
    session: ActiveSession,
    usersRepository: LocalUsersRepository,
    financeRepository: LocalFinanceRepository,
    dayKey: String,
): List<MonitorRow> {
    if (session.role != UserRole.ADMIN && session.role != UserRole.SUPERVISOR && session.role != UserRole.MASTER) return emptyList()
    return sortCashierAccountsNatural(filterCashiersForSession(session, usersRepository.getCashiers()))
        .map { cashier ->
            val summary = financeRepository.getActorSummary(dayKey, cashier.id, cashier.user).summary
            MonitorRow(
                userId = cashier.id,
                username = cashier.user,
                displayName = cashier.displayName ?: cashier.user,
                presence = resolvePresence(cashier, summary.ticketsCount, summary.recargas),
                lastSeenLabel = cashier.lastSeenAtEpochMs?.let(::formatPresenceStamp) ?: "sin actividad marcada",
                tickets = summary.ticketsCount,
                activos = summary.activos,
                ganadores = summary.ganadores,
                pagados = summary.pagados,
                ventas = summary.ventas,
                recargas = summary.recargas,
                caja = summary.cajaDisponible,
                premiosPendientes = summary.premiosPendientes,
                resultado = resolveOperationalReportNet(summary),
                balance = cashier.balance,
            )
        }
}

private fun buildScopedMonitorTickets(
    session: ActiveSession,
    usersRepository: LocalUsersRepository,
    tickets: List<TicketRecord>,
): List<TicketRecord> {
    return filterScopedMonitorTickets(session, usersRepository.getCashiers(), tickets)
}

internal fun filterScopedMonitorTickets(
    session: ActiveSession,
    cashiers: List<UserAccount>,
    tickets: List<TicketRecord>,
): List<TicketRecord> {
    return filterTicketsForOperationalScope(session, tickets, cashiers)
}

private fun normalizeMonitorTerritory(raw: String?): LotteryTerritory {
    return if (raw.equals("USA", ignoreCase = true) || raw.equals("US", ignoreCase = true)) {
        LotteryTerritory.USA
    } else {
        LotteryTerritory.RD
    }
}

private fun resolvePresence(
    cashier: UserAccount,
    ticketsCount: Int,
    recargas: Double,
): String {
    return when {
        !cashier.active -> "Bloqueado"
        ticketsCount > 0 || recargas > 0.0 -> "Activo"
        else -> "Sin movimiento"
    }
}

private fun formatPresenceStamp(epochMs: Long): String {
    return SimpleDateFormat("dd/MM hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date(epochMs))
}

private fun formatMonitorMoney(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)

internal fun buildAdminMonitorDayKey(
    nowUtcMs: Long,
    operationTerritory: LotteryTerritory,
): String {
    val zone = when (operationTerritory) {
        LotteryTerritory.USA -> "America/New_York"
        LotteryTerritory.RD -> "America/Santo_Domingo"
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(zone)
    }.format(Date(nowUtcMs))
}

private const val ADMIN_MONITOR_POLL_MS = 60_000L
private const val ADMIN_MONITOR_REALTIME_FALLBACK_POLL_MS = 300_000L

internal fun resolveAdminMonitorPollIntervalMs(realtimeEnabled: Boolean): Long {
    return if (realtimeEnabled) ADMIN_MONITOR_REALTIME_FALLBACK_POLL_MS else ADMIN_MONITOR_POLL_MS
}
private const val ALL_MONITOR_LOTTERIES_ID = "__all__"
private const val ALL_MONITOR_CASHIERS_ID = "__all_cashiers__"
private const val LOTTERY_MONITOR_MAX_VISIBLE_ROWS = 40
private val PICK_3_MONITOR_TYPES = setOf("P3", "P3BOX", "P3B")
private val PICK_4_MONITOR_TYPES = setOf("P4", "P4BOX", "P4B")

private fun buildMonitorShareText(
    session: ActiveSession,
    dayKey: String,
    bancaSummary: com.lotterynet.pro.core.finance.FinanceSummary,
    rows: List<MonitorRow>,
): String {
    return buildString {
        append("Monitor admin · ${session.banca ?: "LotteryNet"}")
        append('\n')
        append("Fecha: $dayKey")
        append('\n')
        append("Caja banca: ${formatMonitorMoney(bancaSummary.cajaDisponible)}")
        append('\n')
        append("Cajeros: ${rows.size}")
    }
}

private fun buildMonitorRowLine(row: MonitorRow): String {
    return "${row.displayName} · ${row.presence} · Ventas ${formatMonitorMoney(row.ventas)} · Caja ${formatMonitorMoney(row.caja)} · ${monitorResultLabel(row.resultado)} ${formatMonitorMoney(row.resultado)}"
}

internal fun buildLotteryMonitorShareText(
    bancaName: String,
    dayKey: String,
    lotteryLabel: String,
    viewLabel: String,
    statusLabel: String,
    rows: List<LotteryNumberMonitorRow>,
): String {
    val soldRows = rows.filter { it.amount > 0.0 || it.playsCount > 0 }
    val total = soldRows.sumOf { it.amount }
    val plays = soldRows.sumOf { it.playsCount }
    return buildString {
        appendLine("Monitor de lotería · $bancaName")
        appendLine("Fecha: $dayKey")
        appendLine("Lotería: $lotteryLabel")
        appendLine("Vista: $viewLabel")
        appendLine("Estado: $statusLabel")
        appendLine("Total vendido: ${formatMonitorMoney(total)}")
        appendLine("Jugadas: $plays")
        appendLine()
        if (soldRows.isEmpty()) {
            append("Sin ventas para este filtro.")
        } else {
            soldRows.take(80).forEach { row ->
                val actors = row.actors.joinToString(", ").ifBlank { "sin cajero" }
                val limitText = row.remainingAmount?.let { " · queda ${formatMonitorMoney(it)}" }.orEmpty()
                appendLine("${row.displayNumber} · ${formatMonitorMoney(row.amount)} · ${row.playsCount} jugada(s)$limitText · $actors")
            }
            if (soldRows.size > 80) {
                append("+${soldRows.size - 80} filas más")
            }
        }
    }.trim()
}

private fun monitorPriority(row: MonitorRow): Int {
    return when {
        row.presence == "Bloqueado" -> 400
        row.premiosPendientes > 0.0 -> 300
        row.presence == "Activo" && (row.ventas > 0.0 || row.recargas > 0.0) -> 200
        row.presence == "Activo" -> 100
        else -> 0
    }
}

private fun monitorCashierNumber(label: String): Int {
    return Regex("""\d+""").find(label)?.value?.toIntOrNull() ?: Int.MAX_VALUE
}

private fun filterLabel(filter: String): String {
    return when (filter) {
        "sold" -> "Venta"
        "pending_payment" -> "Pendiente de cobro"
        "negative_balance" -> "Balance negativo"
        "loss" -> "Pérdida"
        "benefit" -> "Beneficio"
        else -> "Todos"
    }
}
