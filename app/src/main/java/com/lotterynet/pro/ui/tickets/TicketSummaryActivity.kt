package com.lotterynet.pro.ui.tickets

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.dominicanDayKey
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscription
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketRemoteStore
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.ForegroundCatchUpInput
import com.lotterynet.pro.core.sync.ForegroundCatchUpPolicy
import com.lotterynet.pro.core.sync.matchesNativeTicketSyncOwner
import com.lotterynet.pro.core.sync.OperationalSyncThrottle
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKeys
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactEmptyState
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.CompactTicketSaveSyncStatus
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.SearchBox
import com.lotterynet.pro.ui.common.TicketSaveSyncStage
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.resolveTicketSaveSyncUiContract
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.json.JSONObject

internal enum class TicketSummaryStartupWork {
    LOAD_SESSION,
    LOAD_LOCAL_TICKETS,
    LOAD_LOCAL_CASHIERS,
    HYDRATE_REMOTE_TICKETS,
    FLUSH_SYNC_QUEUE,
    RENDER_TICKET_BITMAP,
}

internal data class TicketSummaryStartupPlan(
    val firstFrameWork: Set<TicketSummaryStartupWork>,
    val afterFirstFrameWork: Set<TicketSummaryStartupWork>,
)

internal data class TicketSummaryRefreshAction(
    val label: String,
    val compact: Boolean,
    val forceRemoteSync: Boolean,
)

internal data class TicketSummaryRefreshUi(
    val buttonLabel: String,
    val statusLabel: String,
    val buttonEnabled: Boolean,
    val showProgress: Boolean,
    val showStatus: Boolean,
)

internal data class TicketSummaryLocalLoadPlan(
    val firstFrameDayKey: String,
    val loadSingleDayFirst: Boolean,
    val loadFullArchiveAfterFirstFrame: Boolean,
)

internal fun resolveTicketSummaryStartupPlan(): TicketSummaryStartupPlan {
    return TicketSummaryStartupPlan(
        firstFrameWork = setOf(
            TicketSummaryStartupWork.LOAD_SESSION,
            TicketSummaryStartupWork.LOAD_LOCAL_TICKETS,
            TicketSummaryStartupWork.LOAD_LOCAL_CASHIERS,
        ),
        afterFirstFrameWork = setOf(
            TicketSummaryStartupWork.HYDRATE_REMOTE_TICKETS,
            TicketSummaryStartupWork.FLUSH_SYNC_QUEUE,
        ),
    )
}

internal fun resolveTicketSummaryLocalLoadPlan(nowEpochMs: Long = System.currentTimeMillis()): TicketSummaryLocalLoadPlan {
    return TicketSummaryLocalLoadPlan(
        firstFrameDayKey = dominicanDayKey(nowEpochMs),
        loadSingleDayFirst = true,
        loadFullArchiveAfterFirstFrame = true,
    )
}

internal fun resolveTicketSummaryRefreshAction(): TicketSummaryRefreshAction {
    return TicketSummaryRefreshAction(
        label = "Refrescar",
        compact = true,
        forceRemoteSync = true,
    )
}

internal fun resolveTicketSummaryRefreshUi(
    isRefreshing: Boolean,
    syncMessage: String,
): TicketSummaryRefreshUi {
    if (isRefreshing) {
        return TicketSummaryRefreshUi(
            buttonLabel = "Refrescando",
            statusLabel = "Refrescando servidor...",
            buttonEnabled = false,
            showProgress = true,
            showStatus = true,
        )
    }
    val normalized = syncMessage.lowercase(Locale.getDefault())
    val hasError = normalized.contains("pendiente") ||
        normalized.contains("error") ||
        normalized.contains("no se pudo") ||
        normalized.contains("sin conexión") ||
        normalized.contains("sin conexion")
    return TicketSummaryRefreshUi(
        buttonLabel = "Refrescar",
        statusLabel = if (hasError) "Error" else "",
        buttonEnabled = true,
        showProgress = false,
        showStatus = hasError,
    )
}

internal fun countPendingTicketSyncForSession(
    pendingTickets: List<JSONObject>,
    session: ActiveSession,
): Int {
    val ownerKeys = resolveOperationalOwnerKeys(session)
    if (ownerKeys.isEmpty()) return 0
    return pendingTickets.count { json ->
        ownerKeys.any { ownerKey -> matchesNativeTicketSyncOwner(json, ownerKey) }
    }
}

internal fun resolveTicketSummaryForegroundCatchUpInput(
    session: ActiveSession,
    tickets: List<TicketRecord>,
    lastRemoteUpdatedAt: String?,
    remoteUpdatedAt: String?,
    realtimeConfigured: Boolean,
    hasRealtimeSubscription: Boolean,
    nowEpochMs: Long,
    force: Boolean = false,
): ForegroundCatchUpInput {
    val dateKey = dominicanDayKey(nowEpochMs)
    return ForegroundCatchUpInput(
        ownerKey = resolveOperationalOwnerKey(session),
        dateKey = dateKey,
        hasLocalTickets = tickets.any { ticket ->
            ticket.drawDateKey.equals(dateKey, ignoreCase = true)
        },
        hasLocalResults = true,
        ticketStampChanged = remoteUpdatedAt.isNullOrBlank() ||
            !remoteUpdatedAt.equals(lastRemoteUpdatedAt.orEmpty(), ignoreCase = true),
        resultsStampChanged = false,
        realtimeConnected = !realtimeConfigured || hasRealtimeSubscription,
        nowMs = nowEpochMs,
        force = force,
    )
}

class TicketSummaryActivity : AppCompatActivity() {
    private val syncHandler = Handler(Looper.getMainLooper())
    private val realtimeClient = LotterynetRealtimeClient()
    private val realtimeSubscriptions = mutableListOf<LotterynetRealtimeClient.SubscriptionHandle>()
    private lateinit var session: ActiveSession
    private lateinit var salesRepository: LocalSalesRepository
    private lateinit var usersRepository: LocalUsersRepository
    private val catalogRepository = StaticLotteryCatalogRepository()
    private val remoteStampStore = NativeTicketRemoteStore()
    private val foregroundCatchUpPolicy = ForegroundCatchUpPolicy(
        OperationalSyncThrottle(TICKET_SUMMARY_FOREGROUND_CATCH_UP_THROTTLE_MS),
    )
    private lateinit var operationalSyncCoordinator: NativeOperationalSyncCoordinator
    private lateinit var ticketSyncQueueRepository: NativeTicketSyncQueueRepository

    private var ticketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var cashiersState by mutableStateOf<List<UserAccount>>(emptyList())
    private var syncMessageState by mutableStateOf("Tickets locales listos.")
    private var isRefreshingTicketsState by mutableStateOf(false)
    private var pendingSyncCountState by mutableStateOf(0)
    private var lastRemoteUpdatedAt: String? = null
    private val summarySyncInFlight = AtomicBoolean(false)
    private val resumeSyncRunnable = Runnable { runForegroundCatchUp(force = false) }
    private val syncPollRunnable = object : Runnable {
        override fun run() {
            syncOperationalTickets(force = shouldForceTicketSummaryLivePoll())
            syncHandler.postDelayed(this, resolveTicketSummaryPollIntervalMs(realtimeClient.isConfigured()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.TICKET_SUMMARY)) {
            return
        }
        session = checkNotNull(activeSession)
        salesRepository = LocalSalesRepository(this)
        usersRepository = LocalUsersRepository(this)
        ticketSyncQueueRepository = NativeTicketSyncQueueRepository(this)
        operationalSyncCoordinator = NativeOperationalSyncCoordinator(
            ticketGateway = NativeTicketCloudSyncCoordinator(
                salesRepository = salesRepository,
                queueRepository = ticketSyncQueueRepository,
            ),
        )
        usersRepository.touchSession(session)
        refreshTicketData(todayFirst = true)

        setContent {
            LotteryNetComposeTheme {
                TicketSummaryRoute(
                    session = session,
                    tickets = ticketsState,
                    cashiers = cashiersState,
                    catalogLotteries = catalogRepository.getAllLotteries(),
                    initialFilters = resolveTicketSummaryInitialFilters(
                        ownerScopeRaw = intent?.getStringExtra(EXTRA_OWNER_SCOPE),
                        cashierKeyRaw = intent?.getStringExtra(EXTRA_CASHIER_KEY),
                    ),
                    syncMessage = syncMessageState,
                    isRefreshing = isRefreshingTicketsState,
                    pendingSyncCount = pendingSyncCountState,
                    onRefresh = { syncOperationalTickets(force = true, showRefreshing = true) },
                    onOpenTicket = { ticket, action ->
                        val resolution = resolveTicketOpenRequest(
                            requestedTicket = ticket,
                            currentTickets = salesRepository.getAllTickets(),
                            deletedTicketIds = salesRepository.getDeletedTicketIds(),
                        )
                        val currentTicket = resolution.ticket
                        if (currentTicket == null) {
                            refreshTicketData()
                            syncMessageState = resolution.message ?: STALE_TICKET_MESSAGE
                            Toast.makeText(this, syncMessageState, Toast.LENGTH_SHORT).show()
                            return@TicketSummaryRoute
                        }
                        startActivity(Intent(this, TicketOfficialActivity::class.java).apply {
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_ID, currentTicket.id)
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_EPOCH, currentTicket.createdAtEpochMs)
                            putExtra(TicketOfficialActivity.EXTRA_BANCA_NAME, session.banca ?: "LotteryNet")
                            putExtra(TicketOfficialActivity.EXTRA_ACTION_MODE, action.mode)
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_SNAPSHOT_JSON, encodeTicketRecordSnapshot(currentTicket))
                        })
                    },
                )
            }
        }
        refreshFullTicketDataInBackground()
        syncHandler.postDelayed(
            { runForegroundCatchUp(force = false) },
            TICKET_SUMMARY_STARTUP_SYNC_DELAY_MS,
        )
        subscribeRealtime(reset = false)
        syncHandler.postDelayed(syncPollRunnable, resolveTicketSummaryPollIntervalMs(realtimeClient.isConfigured()))
    }

    companion object {
        const val EXTRA_OWNER_SCOPE = "ownerScope"
        const val EXTRA_CASHIER_KEY = "cashierKey"
    }

    override fun onResume() {
        super.onResume()
        if (::salesRepository.isInitialized) {
            refreshTicketData()
            syncHandler.removeCallbacks(resumeSyncRunnable)
            syncHandler.postDelayed(resumeSyncRunnable, TICKET_SUMMARY_RESUME_SYNC_DELAY_MS)
        }
    }

    override fun onDestroy() {
        syncHandler.removeCallbacks(resumeSyncRunnable)
        syncHandler.removeCallbacks(syncPollRunnable)
        realtimeSubscriptions.forEach { it.close() }
        realtimeSubscriptions.clear()
        realtimeClient.shutdown()
        super.onDestroy()
    }

    private fun refreshTicketData(todayFirst: Boolean = false) {
        ticketsState = if (todayFirst) {
            salesRepository.getTicketsForDay(resolveTicketSummaryLocalLoadPlan().firstFrameDayKey)
        } else {
            salesRepository.getAllTickets()
        }
        cashiersState = usersRepository.getCashiers()
        pendingSyncCountState = if (::ticketSyncQueueRepository.isInitialized) {
            countPendingTicketSyncForSession(ticketSyncQueueRepository.peekAll(), session)
        } else {
            0
        }
    }

    private fun refreshFullTicketDataInBackground() {
        if (!::salesRepository.isInitialized || !::usersRepository.isInitialized) return
        thread(name = "ticket-summary-local-full-refresh") {
            val nextTickets = salesRepository.getAllTickets()
            val nextCashiers = usersRepository.getCashiers()
            val nextPendingCount = if (::ticketSyncQueueRepository.isInitialized) {
                countPendingTicketSyncForSession(ticketSyncQueueRepository.peekAll(), session)
            } else {
                0
            }
            runOnUiThread {
                ticketsState = nextTickets
                cashiersState = nextCashiers
                pendingSyncCountState = nextPendingCount
            }
        }
    }

    private fun syncOperationalTickets(
        force: Boolean,
        showRefreshing: Boolean = false,
        allowPendingFlush: Boolean = force || showRefreshing,
    ) {
        if (!::operationalSyncCoordinator.isInitialized) return
        refreshTicketData()
        val shouldForce = force || (allowPendingFlush && pendingSyncCountState > 0)
        if (!summarySyncInFlight.compareAndSet(false, true)) return
        if (showRefreshing) {
            syncMessageState = "Refrescando servidor..."
            isRefreshingTicketsState = true
        }
        thread(name = "ticket-summary-sync") {
            var nextMessage: String? = null
            var shouldApplyMessage = false
            try {
                runCatching {
                    operationalSyncCoordinator.syncTicketsForSession(
                        session = session,
                        lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                        force = shouldForce,
                    )
                }.onSuccess { state ->
                    lastRemoteUpdatedAt = state.remoteUpdatedAt ?: lastRemoteUpdatedAt
                    if (showRefreshing || state.status != com.lotterynet.pro.core.sync.NativeOperationalSyncStatus.UP_TO_DATE) {
                        nextMessage = state.message
                        shouldApplyMessage = true
                    }
                }.onFailure { error ->
                    nextMessage = error.message ?: "No se pudo sincronizar tickets."
                    shouldApplyMessage = true
                }
            } finally {
                runOnUiThread {
                    refreshTicketData()
                    if (shouldApplyMessage && !nextMessage.isNullOrBlank()) {
                        syncMessageState = nextMessage.orEmpty()
                    }
                    isRefreshingTicketsState = false
                    summarySyncInFlight.set(false)
                }
            }
        }
    }

    private fun runForegroundCatchUp(force: Boolean) {
        if (!::operationalSyncCoordinator.isInitialized || !::salesRepository.isInitialized) return
        refreshTicketData()
        val ownerKeys = resolveOperationalOwnerKeys(session)
        if (ownerKeys.isEmpty()) return
        thread(name = "ticket-summary-foreground-catch-up") {
            val remoteStamps = ownerKeys.mapNotNull { ownerKey ->
                runCatching { remoteStampStore.fetchUpdatedAtFresh(ownerKey) }.getOrNull()
            }
            val remoteUpdatedAt = remoteStamps.asSequence()
                .firstOrNull { stamp -> !stamp.equals(lastRemoteUpdatedAt.orEmpty(), ignoreCase = true) }
                ?: remoteStamps.firstOrNull()
            val decision = foregroundCatchUpPolicy.decide(
                resolveTicketSummaryForegroundCatchUpInput(
                    session = session,
                    tickets = salesRepository.getTicketsForDay(dominicanDayKey(System.currentTimeMillis())),
                    lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                    remoteUpdatedAt = remoteUpdatedAt,
                    realtimeConfigured = realtimeClient.isConfigured(),
                    hasRealtimeSubscription = realtimeSubscriptions.isNotEmpty(),
                    nowEpochMs = System.currentTimeMillis(),
                    force = force,
                ),
            )
            if (!decision.shouldRun) return@thread
            if (decision.reconnectRealtime) {
                runOnUiThread { subscribeRealtime(reset = true) }
            }
            if (decision.refreshTickets) {
                runOnUiThread {
                    syncOperationalTickets(
                        force = force,
                        showRefreshing = false,
                        allowPendingFlush = false,
                    )
                }
            } else if (remoteUpdatedAt != null) {
                lastRemoteUpdatedAt = remoteUpdatedAt
            }
        }
    }

    private fun subscribeRealtime(reset: Boolean) {
        if (!realtimeClient.isConfigured()) return
        if (reset) {
            realtimeSubscriptions.forEach { it.close() }
            realtimeSubscriptions.clear()
        } else if (realtimeSubscriptions.isNotEmpty()) {
            return
        }
        resolveOperationalOwnerKeys(session).forEach { ownerKey ->
            realtimeSubscriptions += realtimeClient.subscribe(LotterynetRealtimeSubscription.ticketOwner(ownerKey)) {
                runForegroundCatchUp(force = false)
            }
        }
    }
}

@Composable
private fun TicketSummaryRoute(
    session: ActiveSession,
    tickets: List<TicketRecord>,
    cashiers: List<UserAccount>,
    catalogLotteries: List<com.lotterynet.pro.core.model.LotteryCatalogItem>,
    initialFilters: TicketSummaryInitialFilters,
    syncMessage: String,
    isRefreshing: Boolean,
    pendingSyncCount: Int,
    onRefresh: () -> Unit,
    onOpenTicket: (TicketRecord, TicketSummaryPrimaryAction) -> Unit,
) {
    val context = LocalContext.current
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveTicketCollectionLayout(visual.windowMode) }
    val directory = remember(session, tickets, cashiers) {
        buildTicketDirectory(session, tickets, cashiers)
    }
    val lotteryOptions = remember(directory.tickets, catalogLotteries) { buildLotteryOptions(directory.tickets, catalogLotteries) }
    val cashierOptions = remember(directory.cashierOptions) { buildCashierOptions(directory.cashierOptions) }
    val monthOptions = remember { buildTicketMonthOptions() }

    var periodFilter by rememberSaveable { mutableStateOf(TicketSummaryPeriod.TODAY.id) }
    var monthFilter by rememberSaveable { mutableStateOf(todayMonthValue()) }
    var statusBucket by rememberSaveable { mutableStateOf(TicketStatusBucket.ALL.id) }
    var ownerScope by rememberSaveable { mutableStateOf(initialFilters.ownerScope.name) }
    var selectedCashierKey by rememberSaveable { mutableStateOf(initialFilters.cashierKey) }
    var lotteryFilter by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }

    val ownerScopeValue = TicketOwnerScope.valueOf(ownerScope)
    val (fromDateTime, toDateTime) = remember(periodFilter, monthFilter) {
        resolveTicketSummaryDateRange(periodFilter, monthFilter)
    }
    val filteredTickets = remember(
        directory,
        periodFilter,
        monthFilter,
        statusBucket,
        ownerScopeValue,
        selectedCashierKey,
        lotteryFilter,
        query,
    ) {
        filterSummaryTickets(
            directory = directory,
            statusBucket = statusBucket,
            lotteryName = lotteryFilter,
            ownerScope = ownerScopeValue,
            cashierKey = selectedCashierKey,
            query = query,
            fromDateTime = fromDateTime,
            toDateTime = toDateTime,
        )
    }
    val metrics = remember(filteredTickets) { buildSummaryMetrics(filteredTickets) }
    val syncContract = remember(pendingSyncCount, syncMessage) {
        if (pendingSyncCount > 0) {
            resolveTicketSaveSyncUiContract(
                stage = TicketSaveSyncStage.PENDING,
                detail = "$pendingSyncCount ticket(s) esperando servidor.",
            )
        } else {
            resolveTicketSaveSyncUiContract(stage = TicketSaveSyncStage.SYNCED)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = session.role,
                active = NativeBottomTab.LIST,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
            ) {
                    AppTopBar(
                        spec = ScreenChromeSpec(
                            title = "Tickets",
                            subtitle = session.banca ?: "LotteryNet",
                            activeBottomTab = NativeBottomTab.LIST,
                        ),
                        onOpenMenu = { com.lotterynet.pro.ui.common.openShellMenu(context) },
                    )
                    if (pendingSyncCount > 0 || isRefreshing) {
                        Spacer(modifier = Modifier.height(4.dp))
                        CompactTicketSaveSyncStatus(contract = syncContract)
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    TicketSummaryHeader(
                        visibleCount = metrics.visibleCount,
                        visibleTotal = metrics.visibleTotal,
                        activeTotal = metrics.activeTotal,
                        query = query,
                        statusBucket = statusBucket,
                        ownerScope = ownerScopeValue,
                        canFilterOwner = session.role == com.lotterynet.pro.core.model.UserRole.ADMIN,
                        selectedCashierKey = selectedCashierKey,
                        cashierOptions = cashierOptions,
                        lotteryFilter = lotteryFilter,
                        lotteryOptions = lotteryOptions,
                        periodFilter = periodFilter,
                        monthFilter = monthFilter,
                        monthOptions = monthOptions,
                        syncMessage = syncMessage,
                        isRefreshing = isRefreshing,
                        onPeriodChange = { periodFilter = it },
                        onMonthChange = { monthFilter = it },
                        onStatusBucketChange = { statusBucket = it },
                        onOwnerScopeChange = { scope ->
                            ownerScope = scope.name
                            if (scope != TicketOwnerScope.CASHIER) {
                                selectedCashierKey = ""
                            }
                        },
                        onCashierChange = { selectedCashierKey = it },
                        onLotteryFilterChange = { lotteryFilter = it },
                        onQueryChange = { query = it.uppercase(Locale.getDefault()) },
                        onRefresh = onRefresh,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    if (filteredTickets.isEmpty()) {
                        CompactEmptyState("Sin tickets para este filtro.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f, fill = true),
                            verticalArrangement = Arrangement.spacedBy(layout.listSpacingDp.dp),
                            contentPadding = PaddingValues(bottom = visual.sizes.screenPaddingV),
                        ) {
                            items(filteredTickets, key = { it.id }) { ticket ->
                                val primaryAction = resolveTicketSummaryPrimaryAction(ticket)
                                TicketSummaryRow(
                                    ticket = ticket,
                                    actorLabelsByKey = directory.actorLabelsByKey,
                                    primaryAction = primaryAction,
                                    onOpen = { onOpenTicket(ticket, primaryAction) },
                                )
                            }
                        }
                    }
            }
        }
    }
}

internal const val TICKET_SUMMARY_POLL_MS = 60_000L
internal const val TICKET_SUMMARY_REALTIME_FALLBACK_POLL_MS = 120_000L
internal const val TICKET_SUMMARY_STARTUP_SYNC_DELAY_MS = 500L
internal const val TICKET_SUMMARY_RESUME_SYNC_DELAY_MS = 1_500L
internal const val TICKET_SUMMARY_FOREGROUND_CATCH_UP_THROTTLE_MS = 45_000L

internal fun shouldForceTicketSummaryLivePoll(): Boolean = false

internal fun resolveTicketSummaryPollIntervalMs(realtimeEnabled: Boolean): Long {
    return if (realtimeEnabled) TICKET_SUMMARY_REALTIME_FALLBACK_POLL_MS else TICKET_SUMMARY_POLL_MS
}

private fun todayMonthValue(): String {
    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Santo_Domingo"), Locale.US)
    return (calendar.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
}

@Composable
private fun TicketSummaryHeader(
    visibleCount: Int,
    statusBucket: String,
    ownerScope: TicketOwnerScope,
    canFilterOwner: Boolean,
    selectedCashierKey: String,
    cashierOptions: List<CompactDropdownOption>,
    lotteryFilter: String,
    lotteryOptions: List<CompactDropdownOption>,
    periodFilter: String,
    monthFilter: String,
    monthOptions: List<CompactDropdownOption>,
    query: String,
    visibleTotal: Double,
    activeTotal: Double,
    syncMessage: String,
    isRefreshing: Boolean,
    onStatusBucketChange: (String) -> Unit,
    onOwnerScopeChange: (TicketOwnerScope) -> Unit,
    onCashierChange: (String) -> Unit,
    onLotteryFilterChange: (String) -> Unit,
    onPeriodChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveTicketCollectionLayout(visual.windowMode) }
    val refreshAction = remember { resolveTicketSummaryRefreshAction() }
    val refreshUi = remember(isRefreshing, syncMessage) { resolveTicketSummaryRefreshUi(isRefreshing, syncMessage) }
    var secondaryFiltersExpanded by rememberSaveable { mutableStateOf(!layout.collapseSecondarySummaryFilters) }
    val secondaryFilterActive = monthFilter != todayMonthValue() ||
        lotteryFilter.isNotBlank() ||
        ownerScope != TicketOwnerScope.ALL ||
        selectedCashierKey.isNotBlank()
    val showSecondaryFilters = !layout.collapseSecondarySummaryFilters || secondaryFiltersExpanded
    CompactPanel(
        alt = true,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = layout.headerPaddingVerticalDp.dp),
    ) {
        Text(
            text = "$visibleCount tickets · visible ${formatTicketMoney(visibleTotal)} · activos ${formatTicketMoney(activeTotal)}",
            style = MaterialTheme.typography.labelLarge,
            color = visual.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            if (refreshUi.showStatus) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (refreshUi.showProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = visual.colors.actionPrimary,
                        )
                    }
                    CompactStatusBadge(
                        label = refreshUi.statusLabel,
                        tone = if (refreshUi.statusLabel == "Error") visual.colors.loss else visual.colors.gain,
                    )
                }
            }
            CompactActionButton(
                label = refreshUi.buttonLabel,
                onClick = onRefresh,
                icon = Icons.Rounded.Refresh,
                tone = ActionTone.Secondary,
                enabled = refreshUi.buttonEnabled,
                modifier = Modifier.weight(1f),
            )
            CompactActionButton(
                label = if (showSecondaryFilters) "Menos" else "Filtros",
                onClick = { secondaryFiltersExpanded = !secondaryFiltersExpanded },
                active = secondaryFilterActive || showSecondaryFilters,
                icon = Icons.Rounded.Tune,
                modifier = Modifier.weight(1f),
            )
            if (!refreshUi.showStatus) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(layout.filterRowSpacingDp.dp),
        ) {
            CompactFilterDropdown(
                label = "Periodo",
                selectedValue = periodFilter,
                options = TicketSummaryPeriod.entries.map { CompactDropdownOption(it.id, it.label) },
                onValueSelected = onPeriodChange,
                modifier = Modifier.weight(1f),
            )
            CompactFilterDropdown(
                label = "Estado",
                selectedValue = statusBucket,
                options = TicketStatusBucket.entries.map { CompactDropdownOption(it.id, it.label) },
                onValueSelected = onStatusBucketChange,
                modifier = Modifier.weight(1f),
            )
        }
        if (showSecondaryFilters) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.filterRowSpacingDp.dp),
            ) {
                CompactFilterDropdown(
                    label = "Mes",
                    selectedValue = monthFilter,
                    options = monthOptions,
                    onValueSelected = onMonthChange,
                    modifier = Modifier.weight(1f),
                )
                CompactFilterDropdown(
                    label = "Loteria",
                    selectedValue = lotteryFilter,
                    options = lotteryOptions,
                    onValueSelected = onLotteryFilterChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SearchBox(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Buscar ticket",
            minHeight = 44.dp,
        )
        if (canFilterOwner && showSecondaryFilters) {
            TicketOwnerScopeRow(
                ownerScope = ownerScope,
                onOwnerScopeChange = onOwnerScopeChange,
            )
            if (ownerScope == TicketOwnerScope.CASHIER && cashierOptions.isNotEmpty()) {
                CompactFilterDropdown(
                    label = "Cajero",
                    selectedValue = selectedCashierKey,
                    options = cashierOptions,
                    onValueSelected = onCashierChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (ownerScope == TicketOwnerScope.CASHIER) {
                Text(
                    text = "Sin cajeros disponibles para este admin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun TicketSummaryRow(
    ticket: TicketRecord,
    actorLabelsByKey: Map<String, String>,
    primaryAction: TicketSummaryPrimaryAction,
    onOpen: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveTicketCollectionLayout(visual.windowMode) }
    CompactPanel(
        modifier = Modifier.fillMaxWidth(),
        alt = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = (layout.rowPaddingVerticalDp + 1).dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = ticket.serial ?: ticket.id,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = visual.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = ticketLotteriesLabel(ticket),
                        style = MaterialTheme.typography.labelMedium,
                        color = visual.colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CompactStatusBadge(
                    label = ticketListStatusLabel(ticketStatusBucket(ticket)),
                    tone = ticketStatusTone(ticketStatusBucket(ticket)),
                )
            }
            Text(
                text = ticketNumbersLabel(ticket),
                style = MaterialTheme.typography.labelMedium,
                color = visual.colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = "${ticketOwnerLabel(ticket, actorLabelsByKey)} · ${ticketDateTime(ticket.createdAtEpochMs)} · ${ticket.plays.size} jugadas",
                        style = MaterialTheme.typography.labelSmall,
                        color = visual.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatTicketMoney(ticket.total),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = visual.colors.gain,
                )
                Spacer(modifier = Modifier.width(6.dp))
                CompactActionButton(
                    label = primaryAction.label,
                    onClick = onOpen,
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    tone = ActionTone.Warning,
                )
            }
        }
    }
}

@Composable
private fun StatusCompactDropdown(
    statusBucket: String,
    onStatusBucketChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactFilterDropdown(
        label = "Estado",
        selectedValue = statusBucket,
        options = TicketStatusBucket.entries.map { CompactDropdownOption(it.id, it.label) },
        onValueSelected = onStatusBucketChange,
        modifier = modifier,
    )
}
