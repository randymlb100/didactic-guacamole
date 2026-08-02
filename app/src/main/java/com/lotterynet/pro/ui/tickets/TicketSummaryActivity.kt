package com.lotterynet.pro.ui.tickets

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Search
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeOrchestrator
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalAdminLotteryConfigRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.SalesStorageKeys
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketRemoteStore
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.ForegroundCatchUpInput
import com.lotterynet.pro.core.sync.ForegroundCatchUpPolicy
import com.lotterynet.pro.core.sync.matchesNativeTicketSyncOwner
import com.lotterynet.pro.core.sync.OperationalSyncThrottle
import com.lotterynet.pro.core.sync.TicketRefreshGovernor
import com.lotterynet.pro.core.sync.ticketRefreshGovernorKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKeys
import com.lotterynet.pro.core.sync.resolveOperationalRealtimeOwnerKeys
import com.lotterynet.pro.core.sync.invalidateTicketRealtimeCaches
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactEmptyState
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.CompactTicketSaveSyncStatus
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.OperationalModalSheet
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.TicketSaveSyncStage
import com.lotterynet.pro.ui.common.QuickFilterChip
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

internal fun shouldShowTicketSummarySyncBanner(
    pendingSyncCount: Int,
    isRefreshing: Boolean,
): Boolean {
    return isRefreshing && pendingSyncCount > 0
}

internal fun shouldUseFullTicketHydrationForAutomaticCatchUp(
    hasTodayTickets: Boolean,
    lastRemoteUpdatedAt: String?,
    lastDeltaCursor: String?,
): Boolean {
    return !hasTodayTickets && lastRemoteUpdatedAt.isNullOrBlank() && lastDeltaCursor.isNullOrBlank()
}

internal fun shouldHydrateVisibleTicketsAfterOperationalSync(
    showRefreshing: Boolean,
    shouldForce: Boolean,
): Boolean {
    return showRefreshing || shouldForce
}

internal fun shouldFlushPendingTicketsBeforeHydration(
    allowPendingFlush: Boolean,
    pendingSyncCount: Int,
): Boolean {
    return allowPendingFlush && pendingSyncCount > 0
}

internal fun shouldContinuePendingTicketFlush(
    passIndex: Int,
    pendingSyncCount: Int,
    previousPendingSyncCount: Int?,
    maxPasses: Int,
): Boolean {
    if (pendingSyncCount <= 0) return false
    if (passIndex >= maxPasses) return false
    return previousPendingSyncCount == null || pendingSyncCount < previousPendingSyncCount
}

internal fun shouldSkipTicketSummaryRemoteRefresh(
    governor: TicketRefreshGovernor,
    ownerKey: String,
    requestType: String,
    authScope: String,
    force: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
): Boolean {
    if (force) return false
    return governor.shouldReuse(
        ticketRefreshGovernorKey(
            ownerKey = ownerKey,
            requestType = requestType,
            authScope = authScope,
        ),
        nowMs = nowEpochMs,
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

internal fun resolveTicketSummarySyncMessage(
    pendingSyncCount: Int,
    currentMessage: String,
): String {
    if (pendingSyncCount > 0) return currentMessage
    val normalized = currentMessage.lowercase(Locale.getDefault())
    val wasPendingMessage = normalized.contains("pendiente de sync") ||
        normalized.contains("esperando servidor") ||
        normalized.contains("ticket guardado en el celular")
    return if (wasPendingMessage) "Tickets sincronizados con servidor." else currentMessage
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
    private val ticketSummaryViewModel by viewModels<TicketSummaryViewModel>()
    private val syncHandler = Handler(Looper.getMainLooper())
    private val realtimeClient = LotterynetRealtimeClient()
    private val realtimeOrchestrator = LotterynetRealtimeOrchestrator(
        onTicketOwnerChanged = { ownerKey ->
            invalidateTicketRealtimeCaches(ownerKey)
            runForegroundCatchUp(force = false, freshRemoteStamp = true)
        },
    )
    private val realtimeSubscriptions = mutableListOf<LotterynetRealtimeClient.SubscriptionHandle>()
    private lateinit var session: ActiveSession
    private lateinit var salesRepository: LocalSalesRepository
    private lateinit var usersRepository: LocalUsersRepository
    private val catalogRepository = StaticLotteryCatalogRepository()
    private lateinit var remoteStampStore: NativeTicketRemoteStore
    private val foregroundCatchUpPolicy = ForegroundCatchUpPolicy(
        OperationalSyncThrottle(TICKET_SUMMARY_FOREGROUND_CATCH_UP_THROTTLE_MS),
    )
    private lateinit var operationalSyncCoordinator: NativeOperationalSyncCoordinator
    private lateinit var ticketSyncQueueRepository: NativeTicketSyncQueueRepository
    private lateinit var salesPrefs: SharedPreferences
    private val summaryRefreshGovernor = TicketRefreshGovernor(requestCooldownMs = TICKET_SUMMARY_REMOTE_REFRESH_DEDUP_MS)
    private val ticketStorageListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || !isTicketStorageKey(key)) return@OnSharedPreferenceChangeListener
        syncHandler.removeCallbacks(localTicketStorageRefreshRunnable)
        syncHandler.postDelayed(localTicketStorageRefreshRunnable, TICKET_SUMMARY_LOCAL_STORAGE_REFRESH_DELAY_MS)
    }
    private val localTicketStorageRefreshRunnable = Runnable {
        if (::salesRepository.isInitialized && ::usersRepository.isInitialized) {
            refreshTicketData(todayFirst = false)
        }
    }

    private var ticketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var cashiersState by mutableStateOf<List<UserAccount>>(emptyList())
    private var syncMessageState by mutableStateOf("Tickets locales listos.")
    private var isRefreshingTicketsState by mutableStateOf(false)
    private var pendingSyncCountState by mutableStateOf(0)
    private var lastRemoteUpdatedAt: String? = null
    private var lastTicketDeltaCursor: String? = null
    private var currentSummaryPeriodId: String = TicketSummaryPeriod.TODAY.id
    private var currentSummaryMonthValue: String = todayMonthValue()
    private val summarySyncInFlight = AtomicBoolean(false)
    private val resumeSyncRunnable = Runnable { runForegroundCatchUp(force = false) }
    private val syncPollRunnable = object : Runnable {
        override fun run() {
            if (realtimeClient.shouldUsePollingFallback()) {
                syncOperationalTickets(force = shouldForceTicketSummaryLivePoll())
            }
            syncHandler.postDelayed(
                this,
                resolveTicketSummaryPollIntervalMs(
                    realtimeEnabled = realtimeClient.isConfigured(),
                    realtimeConnected = realtimeSubscriptions.isNotEmpty(),
                ),
            )
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
        salesPrefs = getSharedPreferences(SalesStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)
        salesPrefs.registerOnSharedPreferenceChangeListener(ticketStorageListener)
        usersRepository = LocalUsersRepository(this)
        ticketSyncQueueRepository = NativeTicketSyncQueueRepository(this)
        val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        remoteStampStore = NativeTicketRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
            bearerTokenRefresher = { sessionTokenProvider.forceFreshAccessToken() },
        )
        operationalSyncCoordinator = NativeOperationalSyncCoordinator(
            ticketGateway = NativeTicketCloudSyncCoordinator(
                salesRepository = salesRepository,
                queueRepository = ticketSyncQueueRepository,
                remoteStore = remoteStampStore,
            ),
            remoteStampStore = remoteStampStore,
        )
        usersRepository.touchSession(session)
        refreshTicketData(todayFirst = true)
        val ticketCatalogLotteries = catalogRepository.getAllLotteries().filter { lottery ->
            val config = LocalAdminLotteryConfigRepository(this).getSystemModeConfig()
            config.pickModeEnabled || (
                !lottery.playCapabilities.supportsStraight &&
                    !lottery.playCapabilities.supportsBox &&
                    !lottery.type.contains("pick", ignoreCase = true)
            )
        }

        setContent {
            val screenState by ticketSummaryViewModel.state.collectAsState()
            LotteryNetComposeTheme {
                TicketSummaryRoute(
                    session = session,
                    tickets = screenState.tickets,
                    cashiers = screenState.cashiers,
                    catalogLotteries = ticketCatalogLotteries,
                    initialFilters = resolveTicketSummaryInitialFilters(
                        ownerScopeRaw = intent?.getStringExtra(EXTRA_OWNER_SCOPE),
                        cashierKeyRaw = intent?.getStringExtra(EXTRA_CASHIER_KEY),
                    ),
                    syncMessage = screenState.syncMessage,
                    isRefreshing = screenState.isRefreshing,
                    pendingSyncCount = screenState.pendingSyncCount,
                    onRefresh = { syncOperationalTickets(force = true, showRefreshing = true) },
                    onPeriodLoadRequested = { periodId, monthValue ->
                        loadVisibleTicketPeriod(periodId, monthValue)
                    },
                    onStatusBucketRefresh = {
                        syncOperationalTickets(force = true, showRefreshing = false)
                    },
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
        if (realtimeClient.isConfigured() || shouldStartTicketSummaryFallbackPoll(realtimeClient.isConfigured())) {
            syncHandler.postDelayed(
                syncPollRunnable,
                resolveTicketSummaryPollIntervalMs(
                    realtimeEnabled = realtimeClient.isConfigured(),
                    realtimeConnected = realtimeSubscriptions.isNotEmpty(),
                ),
            )
        }
    }

    companion object {
        const val EXTRA_OWNER_SCOPE = "ownerScope"
        const val EXTRA_CASHIER_KEY = "cashierKey"
    }

    override fun onResume() {
        super.onResume()
        if (::salesRepository.isInitialized) {
            refreshTicketData(todayFirst = false)
            syncHandler.removeCallbacks(resumeSyncRunnable)
            syncHandler.postDelayed(resumeSyncRunnable, TICKET_SUMMARY_RESUME_SYNC_DELAY_MS)
        }
    }

    override fun onDestroy() {
        syncHandler.removeCallbacks(resumeSyncRunnable)
        syncHandler.removeCallbacks(syncPollRunnable)
        syncHandler.removeCallbacks(localTicketStorageRefreshRunnable)
        if (::salesPrefs.isInitialized) {
            salesPrefs.unregisterOnSharedPreferenceChangeListener(ticketStorageListener)
        }
        realtimeSubscriptions.forEach { it.close() }
        realtimeSubscriptions.clear()
        realtimeClient.shutdown()
        super.onDestroy()
    }

    private fun refreshTicketData(todayFirst: Boolean = false) {
        ticketsState = if (todayFirst) {
            salesRepository.getTicketsForDay(resolveTicketSummaryLocalLoadPlan().firstFrameDayKey)
        } else {
            loadTicketsForSummaryPeriod(currentSummaryPeriodId, currentSummaryMonthValue)
        }
        cashiersState = usersRepository.getCashiers()
        pendingSyncCountState = currentPendingTicketSyncCount()
        syncMessageState = resolveTicketSummarySyncMessage(pendingSyncCountState, syncMessageState)
        ticketSummaryViewModel.showLocal(
            tickets = ticketsState,
            cashiers = cashiersState,
            pendingSyncCount = pendingSyncCountState,
            message = syncMessageState,
        )
    }

    private fun refreshFullTicketDataInBackground() {
        if (!::salesRepository.isInitialized || !::usersRepository.isInitialized) return
        thread(name = "ticket-summary-local-full-refresh") {
            val nextTickets = loadTicketsForSummaryPeriod(currentSummaryPeriodId, currentSummaryMonthValue)
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
                syncMessageState = resolveTicketSummarySyncMessage(nextPendingCount, syncMessageState)
                ticketSummaryViewModel.showLocal(
                    tickets = nextTickets,
                    cashiers = nextCashiers,
                    pendingSyncCount = nextPendingCount,
                    message = syncMessageState,
                )
            }
        }
    }

    private fun syncOperationalTickets(
        force: Boolean,
        showRefreshing: Boolean = false,
        allowPendingFlush: Boolean = true,
    ) {
        if (!::operationalSyncCoordinator.isInitialized) return
        refreshTicketData(todayFirst = false)
        val freshPendingSyncCount = currentPendingTicketSyncCount()
        pendingSyncCountState = freshPendingSyncCount
        val shouldFlushPending = shouldFlushPendingTicketsBeforeHydration(
            allowPendingFlush = allowPendingFlush,
            pendingSyncCount = freshPendingSyncCount,
        )
        val shouldForce = force || shouldFlushPending
        if (!summarySyncInFlight.compareAndSet(false, true)) return
        if (showRefreshing) {
            syncMessageState = "Refrescando servidor..."
            isRefreshingTicketsState = true
            ticketSummaryViewModel.showCatchingUp(syncMessageState)
        }
        thread(name = "ticket-summary-sync") {
            var nextMessage: String? = null
            var shouldApplyMessage = false
            var refreshedTickets: List<TicketRecord>? = null
            try {
                runCatching {
                    if (shouldFlushPending) {
                        flushPendingTicketsForSession(session)
                    } else if (!shouldForce && !showRefreshing) {
                        if (shouldUseFullTicketHydrationForAutomaticCatchUp(
                                hasTodayTickets = salesRepository.getTicketsForDay(dominicanDayKey(System.currentTimeMillis())).isNotEmpty(),
                                lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                                lastDeltaCursor = lastTicketDeltaCursor,
                            )
                        ) {
                            operationalSyncCoordinator.syncTicketsForSession(
                                session = session,
                                lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                                force = true,
                            )
                        } else {
                            fetchTicketDeltaFast()
                        }
                    } else {
                        operationalSyncCoordinator.syncTicketsForSession(
                            session = session,
                            lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                            force = shouldForce,
                        )
                    }
                }.onSuccess { state ->
                    lastRemoteUpdatedAt = state.remoteUpdatedAt ?: lastRemoteUpdatedAt
                    val shouldHydrateVisibleTickets = shouldHydrateVisibleTicketsAfterOperationalSync(
                        showRefreshing = showRefreshing,
                        shouldForce = shouldForce,
                    )
                    if (state.ok && shouldHydrateVisibleTickets) {
                        refreshedTickets = runCatching { hydrateVisibleTicketsForSession() }.getOrNull()
                    }
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
                    val nextTickets = refreshedTickets ?: loadTicketsForSummaryPeriod(
                        currentSummaryPeriodId,
                        currentSummaryMonthValue,
                    )
                    ticketsState = nextTickets
                    cashiersState = usersRepository.getCashiers()
                    pendingSyncCountState = currentPendingTicketSyncCount()
                    syncMessageState = resolveTicketSummarySyncMessage(pendingSyncCountState, syncMessageState)
                    if (shouldApplyMessage && !nextMessage.isNullOrBlank()) {
                        syncMessageState = nextMessage.orEmpty()
                        syncMessageState = resolveTicketSummarySyncMessage(pendingSyncCountState, syncMessageState)
                    }
                    isRefreshingTicketsState = false
                    if (shouldApplyMessage && !nextMessage.isNullOrBlank()) {
                        ticketSummaryViewModel.showFresh(
                            tickets = ticketsState,
                            cashiers = cashiersState,
                            pendingSyncCount = pendingSyncCountState,
                            remoteUpdatedAt = lastRemoteUpdatedAt,
                            message = syncMessageState,
                        )
                    } else {
                        ticketSummaryViewModel.showLocal(
                            tickets = ticketsState,
                            cashiers = cashiersState,
                            pendingSyncCount = pendingSyncCountState,
                            message = syncMessageState,
                        )
                    }
                    summarySyncInFlight.set(false)
                    if (!showRefreshing) {
                        refreshFullTicketDataInBackground()
                    }
                }
            }
        }
    }

    private fun hydrateVisibleTicketsForSession(): List<TicketRecord> {
        val (fromDate, toDate) = resolveTicketSummaryDateRange(
            currentSummaryPeriodId,
            currentSummaryMonthValue,
        )
        val ownerKeys = resolveOperationalRealtimeOwnerKeys(session).ifEmpty { listOf(resolveOperationalOwnerKey(session)) }
        val remoteTickets = ownerKeys
            .flatMap { ownerKey ->
                runCatching {
                    remoteStampStore.fetchSnapshot(
                        ownerKey = ownerKey,
                        fromDate = fromDate,
                        toDate = toDate,
                        limit = TICKET_SUMMARY_PERIOD_FETCH_LIMIT,
                    ).tickets
                }.getOrDefault(emptyList())
            }
            .distinctBy { ticket ->
                ticket.serial?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
                    ?: ticket.id.trim().lowercase(Locale.US)
            }
        if (remoteTickets.isNotEmpty()) {
            salesRepository.mergeImportedTickets(remoteTickets)
        }
        return loadTicketsForSummaryPeriod(currentSummaryPeriodId, currentSummaryMonthValue)
    }

    private fun hydrateTicketPeriodInBackground(periodId: String, monthValue: String) {
        val (fromDate, toDate) = resolveTicketSummaryDateRange(periodId, monthValue)
        if (fromDate.isBlank() || toDate.isBlank()) return
        val ownerKeys = resolveOperationalRealtimeOwnerKeys(session).ifEmpty { listOf(resolveOperationalOwnerKey(session)) }
        if (shouldSkipTicketSummaryRemoteRefresh(
                governor = summaryRefreshGovernor,
                ownerKey = resolveOperationalOwnerKey(session),
                requestType = "period-hydrate:$periodId:$monthValue",
                authScope = session.role.name,
                force = false,
            )
        ) return
        thread(name = "ticket-summary-period-hydration") {
            val remoteTickets = ownerKeys
                .flatMap { ownerKey ->
                    runCatching {
                        remoteStampStore.fetchSnapshot(
                            ownerKey = ownerKey,
                            fromDate = fromDate,
                            toDate = toDate,
                            limit = TICKET_SUMMARY_PERIOD_FETCH_LIMIT,
                        ).tickets
                    }.getOrDefault(emptyList())
                }
                .distinctBy { ticket ->
                    ticket.serial?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
                        ?: ticket.id.trim().lowercase(Locale.US)
                }
            if (remoteTickets.isNotEmpty()) {
                salesRepository.mergeImportedTickets(remoteTickets)
            }
            runOnUiThread {
                if (currentSummaryPeriodId != periodId || currentSummaryMonthValue != monthValue) return@runOnUiThread
                refreshTicketData(todayFirst = false)
            }
        }
    }

    private fun loadVisibleTicketPeriod(periodId: String, monthValue: String) {
        currentSummaryPeriodId = periodId
        currentSummaryMonthValue = monthValue
        if (!::salesRepository.isInitialized || !::usersRepository.isInitialized) return
        refreshTicketData(todayFirst = false)
        hydrateTicketPeriodInBackground(periodId, monthValue)
    }

    private fun loadTicketsForSummaryPeriod(periodId: String, monthValue: String): List<TicketRecord> {
        val dayKeys = resolveTicketSummaryLocalDayKeys(
            availableDayKeys = salesRepository.getAvailableDayKeys(),
            periodId = periodId,
            monthValue = monthValue,
        )
        return dayKeys
            .flatMap { dayKey -> salesRepository.getTicketsForDay(dayKey) }
            .sortedByDescending { it.createdAtEpochMs }
    }

    private fun flushPendingTicketsForSession(session: ActiveSession): com.lotterynet.pro.core.sync.NativeOperationalSyncState {
        val ownerKeys = resolveOperationalRealtimeOwnerKeys(session).ifEmpty { listOf(resolveOperationalOwnerKey(session)) }
        var lastState: com.lotterynet.pro.core.sync.NativeOperationalSyncState? = null
        var previousPendingCount: Int? = null
        var pendingCount = countPendingTicketSyncForSession(
            pendingTickets = ticketSyncQueueRepository.peekAll(),
            session = session,
        )
        var passIndex = 0
        while (shouldContinuePendingTicketFlush(
                passIndex = passIndex,
                pendingSyncCount = pendingCount,
                previousPendingSyncCount = previousPendingCount,
                maxPasses = MAX_PENDING_FLUSH_PASSES,
            )
        ) {
            ownerKeys.forEach { ownerKey ->
                lastState = operationalSyncCoordinator.flushOwner(
                    ownerKey = ownerKey,
                    banca = session.banca,
                )
            }
            previousPendingCount = pendingCount
            pendingCount = countPendingTicketSyncForSession(
                pendingTickets = ticketSyncQueueRepository.peekAll(),
                session = session,
            )
            passIndex += 1
        }
        return lastState ?: operationalSyncCoordinator.syncTicketsForSession(
            session = session,
            lastRemoteUpdatedAt = lastRemoteUpdatedAt,
            force = true,
        )
    }

    private fun currentPendingTicketSyncCount(): Int {
        return if (::ticketSyncQueueRepository.isInitialized) {
            countPendingTicketSyncForSession(ticketSyncQueueRepository.peekAll(), session)
        } else {
            0
        }
    }

    private fun runForegroundCatchUp(force: Boolean, freshRemoteStamp: Boolean = false) {
        if (!::operationalSyncCoordinator.isInitialized || !::salesRepository.isInitialized) return
        refreshTicketData(todayFirst = false)
        val ownerKeys = resolveOperationalRealtimeOwnerKeys(session)
        if (ownerKeys.isEmpty()) return
        val primaryOwnerKey = resolveOperationalOwnerKey(session)
        if (shouldSkipTicketSummaryRemoteRefresh(
                governor = summaryRefreshGovernor,
                ownerKey = primaryOwnerKey,
                requestType = "foreground-catch-up",
                authScope = session.role.name,
                force = force,
            )
        ) return
        thread(name = "ticket-summary-foreground-catch-up") {
            val remoteUpdatedAt = runCatching {
                if (force || freshRemoteStamp) {
                    remoteStampStore.fetchUpdatedAtFresh(primaryOwnerKey, forceFresh = force || freshRemoteStamp)
                } else {
                    remoteStampStore.fetchUpdatedAt(primaryOwnerKey)
                }
            }.getOrNull()
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
                        allowPendingFlush = true,
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
        val tokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        resolveOperationalRealtimeOwnerKeys(session).forEach { ownerKey ->
            realtimeSubscriptions += realtimeClient.subscribeTicketOwnerSignals(
                ownerKey = ownerKey,
                bearerTokenProvider = { tokenProvider.freshAccessToken() },
                onEvent = realtimeOrchestrator::onEvent,
            )
        }
    }

    private fun fetchTicketDeltaFast(): com.lotterynet.pro.core.sync.NativeOperationalSyncState {
        val ownerKey = resolveOperationalOwnerKey(session)
        if (ownerKey.isBlank()) {
            return com.lotterynet.pro.core.sync.NativeOperationalSyncState(
                ok = false,
                status = com.lotterynet.pro.core.sync.NativeOperationalSyncStatus.ERROR,
                ownerKey = ownerKey,
                message = "No hay admin/banca para sincronizar.",
            )
        }
        val (tickets, cursor) = remoteStampStore.fetchDeltaTickets(
            ownerKey = ownerKey,
            sinceCursor = lastTicketDeltaCursor ?: lastRemoteUpdatedAt,
            limit = TICKET_SUMMARY_DELTA_LIMIT,
            includeItems = true,
        )
        if (tickets.isNotEmpty()) {
            salesRepository.mergeImportedTickets(tickets)
        }
        lastTicketDeltaCursor = cursor ?: lastTicketDeltaCursor
        return com.lotterynet.pro.core.sync.NativeOperationalSyncState(
            ok = true,
            status = if (tickets.isNotEmpty()) {
                com.lotterynet.pro.core.sync.NativeOperationalSyncStatus.SYNCED
            } else {
                com.lotterynet.pro.core.sync.NativeOperationalSyncStatus.UP_TO_DATE
            },
            ownerKey = ownerKey,
            message = if (tickets.isNotEmpty()) "Tickets actualizados rapido." else "Datos al dia.",
            pulledCount = tickets.size,
            remoteUpdatedAt = cursor ?: lastRemoteUpdatedAt,
        )
    }
}

internal const val TICKET_SUMMARY_DELTA_LIMIT = 80
internal const val TICKET_SUMMARY_PERIOD_FETCH_LIMIT = 1000
private const val TICKET_SUMMARY_LOCAL_STORAGE_REFRESH_DELAY_MS = 120L
private const val TICKET_SUMMARY_REMOTE_REFRESH_DEDUP_MS = 15_000L

private fun isTicketStorageKey(key: String): Boolean {
    return key.startsWith(SalesStorageKeys.TICKETS_PREFIX) ||
        key == SalesStorageKeys.DELETED_TICKETS_KEY ||
        key == SalesStorageKeys.DELETED_TICKET_REFS_KEY
}

internal fun resolveTicketSummaryLocalDayKeys(
    availableDayKeys: List<String>,
    periodId: String,
    monthValue: String,
    nowEpochMs: Long = System.currentTimeMillis(),
): List<String> {
    val period = TicketSummaryPeriod.entries.firstOrNull { it.id == periodId } ?: TicketSummaryPeriod.TODAY
    val available = availableDayKeys.toSet()
    fun relativeDayKeys(daysBackInclusive: Int): List<String> {
        val calendar = java.util.Calendar.getInstance(
            java.util.TimeZone.getTimeZone("America/Santo_Domingo"),
            Locale.US,
        ).apply { timeInMillis = nowEpochMs }
        return (0..daysBackInclusive).map { offset ->
            calendar.timeInMillis = nowEpochMs
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -offset)
            dominicanDayKey(calendar.timeInMillis)
        }
    }
    return when (period) {
        TicketSummaryPeriod.TODAY -> listOf(dominicanDayKey(nowEpochMs))
        TicketSummaryPeriod.YESTERDAY -> relativeDayKeys(1).drop(1)
        TicketSummaryPeriod.WEEK -> relativeDayKeys(6)
        TicketSummaryPeriod.QUINZA -> relativeDayKeys(14)
        TicketSummaryPeriod.MONTH -> {
            val calendar = java.util.Calendar.getInstance(
                java.util.TimeZone.getTimeZone("America/Santo_Domingo"),
                Locale.US,
            ).apply { timeInMillis = nowEpochMs }
            val monthMatch = Regex("(\\d{4})-(\\d{2})").matchEntire(monthValue.trim())
            val year = monthMatch?.groupValues?.get(1)?.toIntOrNull() ?: calendar.get(java.util.Calendar.YEAR)
            val month = monthMatch?.groupValues?.get(2)?.toIntOrNull()?.coerceIn(1, 12)
                ?: (calendar.get(java.util.Calendar.MONTH) + 1)
            val prefix = "$year-${month.toString().padStart(2, '0')}-"
            availableDayKeys.filter { it.startsWith(prefix) }.sortedDescending()
        }
        TicketSummaryPeriod.EXACT_DATE -> listOf(monthValue).filter { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        TicketSummaryPeriod.ALL -> availableDayKeys.sortedDescending()
    }
        .filter { it in available || period == TicketSummaryPeriod.TODAY || period == TicketSummaryPeriod.YESTERDAY }
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
    onPeriodLoadRequested: (String, String) -> Unit,
    onStatusBucketRefresh: () -> Unit,
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
    var dateFilter by rememberSaveable { mutableStateOf(todayTicketDateKey()) }
    var statusBucket by rememberSaveable { mutableStateOf(TicketStatusBucket.ALL.id) }
    var ownerScope by rememberSaveable { mutableStateOf(initialFilters.ownerScope.name) }
    var selectedCashierKey by rememberSaveable { mutableStateOf(initialFilters.cashierKey) }
    var lotteryFilter by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(periodFilter, monthFilter, dateFilter) {
        onPeriodLoadRequested(
            periodFilter,
            if (periodFilter == TicketSummaryPeriod.EXACT_DATE.id) dateFilter else monthFilter,
        )
    }

    val ownerScopeValue = TicketOwnerScope.valueOf(ownerScope)
    val activeDateFilter = if (periodFilter == TicketSummaryPeriod.EXACT_DATE.id) dateFilter else monthFilter
    val (fromDateTime, toDateTime) = remember(periodFilter, activeDateFilter) {
        resolveTicketSummaryDateRange(periodFilter, activeDateFilter)
    }
    val filteredTickets = remember(
        directory,
        periodFilter,
        activeDateFilter,
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
                    if (shouldShowTicketSummarySyncBanner(pendingSyncCount, isRefreshing)) {
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
                        dateFilter = dateFilter,
                        monthOptions = monthOptions,
                        syncMessage = syncMessage,
                        isRefreshing = isRefreshing,
                        onPeriodChange = { periodFilter = it },
                        onMonthChange = { monthFilter = it },
                        onDateChange = { dateFilter = it },
                        onStatusBucketChange = {
                            statusBucket = it
                            onStatusBucketRefresh()
                        },
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
                            items(
                                items = filteredTickets,
                                key = { it.id },
                                contentType = { "ticket-summary-row" },
                            ) { ticket ->
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

internal const val TICKET_SUMMARY_POLL_MS = 300_000L
internal const val TICKET_SUMMARY_REALTIME_FALLBACK_POLL_MS = 60_000L
internal const val TICKET_SUMMARY_STARTUP_SYNC_DELAY_MS = 120L
internal const val TICKET_SUMMARY_RESUME_SYNC_DELAY_MS = 250L
internal const val TICKET_SUMMARY_FOREGROUND_CATCH_UP_THROTTLE_MS = 20_000L
internal const val MAX_PENDING_FLUSH_PASSES = 5

internal fun shouldForceTicketSummaryLivePoll(): Boolean = false

internal fun shouldStartTicketSummaryFallbackPoll(realtimeEnabled: Boolean): Boolean = !realtimeEnabled

internal fun resolveTicketSummaryPollIntervalMs(
    realtimeEnabled: Boolean,
    realtimeConnected: Boolean = realtimeEnabled,
): Long {
    return if (realtimeEnabled && realtimeConnected) {
        TICKET_SUMMARY_POLL_MS
    } else {
        TICKET_SUMMARY_REALTIME_FALLBACK_POLL_MS
    }
}

private fun todayMonthValue(): String {
    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Santo_Domingo"), Locale.US)
    return "${calendar.get(java.util.Calendar.YEAR)}-${(calendar.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')}"
}

private fun ticketSummaryFilterSummary(
    periodFilter: String,
    monthFilter: String,
    dateFilter: String,
    statusBucket: String,
    ownerScope: TicketOwnerScope,
    selectedCashierKey: String,
    cashierOptions: List<CompactDropdownOption>,
    lotteryFilter: String,
    lotteryOptions: List<CompactDropdownOption>,
    query: String,
): String {
    val periodLabel = TicketSummaryPeriod.entries.firstOrNull { it.id == periodFilter }?.label ?: periodFilter
    val statusLabel = TicketStatusBucket.entries.firstOrNull { it.id == statusBucket }?.label ?: statusBucket
    val cashierLabel = cashierOptions.firstOrNull { it.value == selectedCashierKey }?.label
    val lotteryLabel = lotteryOptions.firstOrNull { it.value == lotteryFilter }?.label
    return buildList {
        add(
            when (periodFilter) {
                TicketSummaryPeriod.EXACT_DATE.id -> "Fecha: $dateFilter"
                TicketSummaryPeriod.MONTH.id -> "Mes: $monthFilter"
                else -> "Periodo: $periodLabel"
            },
        )
        add("Estado: $statusLabel")
        if (ownerScope != TicketOwnerScope.ALL) add("Vista: ${ownerScope.label}")
        if (!cashierLabel.isNullOrBlank() && selectedCashierKey.isNotBlank()) add(cashierLabel)
        if (!lotteryLabel.isNullOrBlank() && lotteryFilter.isNotBlank()) add(lotteryLabel)
        if (query.isNotBlank()) add("Busca: ${query.take(24)}")
    }.joinToString(" · ")
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
    dateFilter: String,
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
    onDateChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveTicketCollectionLayout(visual.windowMode) }
    val refreshUi = remember(isRefreshing, syncMessage) { resolveTicketSummaryRefreshUi(isRefreshing, syncMessage) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val secondaryFilterActive = (periodFilter == TicketSummaryPeriod.EXACT_DATE.id && dateFilter != todayTicketDateKey()) ||
        (periodFilter != TicketSummaryPeriod.EXACT_DATE.id && monthFilter != todayMonthValue()) ||
        statusBucket != TicketStatusBucket.ALL.id ||
        lotteryFilter.isNotBlank() ||
        ownerScope != TicketOwnerScope.ALL ||
        selectedCashierKey.isNotBlank() ||
        query.isNotBlank()
    val filterSummary = remember(
        periodFilter,
        monthFilter,
        statusBucket,
        ownerScope,
        selectedCashierKey,
        cashierOptions,
        lotteryFilter,
        lotteryOptions,
        query,
        dateFilter,
    ) {
        ticketSummaryFilterSummary(
            periodFilter = periodFilter,
            monthFilter = monthFilter,
            dateFilter = dateFilter,
            statusBucket = statusBucket,
            ownerScope = ownerScope,
            selectedCashierKey = selectedCashierKey,
            cashierOptions = cashierOptions,
            lotteryFilter = lotteryFilter,
            lotteryOptions = lotteryOptions,
            query = query,
        )
    }
    if (showFilterSheet) {
        OperationalModalSheet(
            title = "Filtros de tickets",
            subtitle = "Periodo, estado, cajero y búsqueda.",
            onDismiss = { showFilterSheet = false },
        ) {
            TicketSummaryFilterSheet(
                periodFilter = periodFilter,
                monthFilter = monthFilter,
                dateFilter = dateFilter,
                statusBucket = statusBucket,
                ownerScope = ownerScope,
                canFilterOwner = canFilterOwner,
                selectedCashierKey = selectedCashierKey,
                cashierOptions = cashierOptions,
                lotteryFilter = lotteryFilter,
                lotteryOptions = lotteryOptions,
                monthOptions = monthOptions,
                query = query,
                onPeriodChange = onPeriodChange,
                onMonthChange = onMonthChange,
                onDateChange = onDateChange,
                onStatusBucketChange = onStatusBucketChange,
                onOwnerScopeChange = onOwnerScopeChange,
                onCashierChange = onCashierChange,
                onLotteryFilterChange = onLotteryFilterChange,
                onQueryChange = onQueryChange,
                onClear = {
                    onPeriodChange(TicketSummaryPeriod.TODAY.id)
                    onMonthChange(todayMonthValue())
                    onDateChange(todayTicketDateKey())
                    onStatusBucketChange(TicketStatusBucket.ALL.id)
                    onLotteryFilterChange("")
                    onQueryChange("")
                    onOwnerScopeChange(TicketOwnerScope.ALL)
                    onCashierChange("")
                },
                onClose = { showFilterSheet = false },
            )
        }
    }
    CompactPanel(
        alt = true,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = layout.headerPaddingVerticalDp.dp),
    ) {
        var showSearch by rememberSaveable { mutableStateOf(query.isNotBlank()) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = showSearch || query.isNotBlank(),
                onClick = { showSearch = !showSearch },
                label = { Text("Buscar") },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = secondaryFilterActive,
                onClick = { showFilterSheet = true },
                label = { Text("Filtros") },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Rounded.Tune, contentDescription = null) },
                modifier = Modifier.weight(1f),
            )
        }
        AnimatedVisibility(visible = showSearch) {
            com.lotterynet.pro.ui.common.SearchBox(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                placeholder = "Buscar por número, cajero o estado",
                minHeight = 44.dp,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
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
            if (!refreshUi.showStatus) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Text(
            text = filterSummary,
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (secondaryFilterActive) {
            CompactStatusBadge(
                label = "Filtros activos",
                tone = visual.colors.actionPrimary,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TicketSummaryFilterSheet(
    periodFilter: String,
    monthFilter: String,
    dateFilter: String,
    statusBucket: String,
    ownerScope: TicketOwnerScope,
    canFilterOwner: Boolean,
    selectedCashierKey: String,
    cashierOptions: List<CompactDropdownOption>,
    lotteryFilter: String,
    lotteryOptions: List<CompactDropdownOption>,
    monthOptions: List<CompactDropdownOption>,
    query: String,
    onPeriodChange: (String) -> Unit,
    onMonthChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onStatusBucketChange: (String) -> Unit,
    onOwnerScopeChange: (TicketOwnerScope) -> Unit,
    onCashierChange: (String) -> Unit,
    onLotteryFilterChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveTicketCollectionLayout(visual.windowMode) }
    var datePickerVisible by rememberSaveable { mutableStateOf(false) }
    if (datePickerVisible) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ticketDateKeyToPickerUtcMillis(dateFilter),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onDateChange(pickerUtcMillisToTicketDateKey(it)) }
                        onPeriodChange(TicketSummaryPeriod.EXACT_DATE.id)
                        datePickerVisible = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Tiempo y estado",
            style = MaterialTheme.typography.titleSmall,
            color = visual.colors.ink,
        )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(layout.filterRowSpacingDp.dp),
        ) {
            if (periodFilter == TicketSummaryPeriod.EXACT_DATE.id) {
                CompactActionButton(
                    label = "Fecha: $dateFilter",
                    onClick = { datePickerVisible = true },
                    icon = Icons.Rounded.Event,
                    modifier = Modifier.weight(1f),
                    tone = ActionTone.Secondary,
                )
            } else {
                CompactFilterDropdown(
                    label = "Mes",
                    selectedValue = monthFilter,
                    options = monthOptions,
                    onValueSelected = onMonthChange,
                    modifier = Modifier.weight(1f),
                )
            }
            CompactFilterDropdown(
                label = "Seleccionar lotería",
                selectedValue = lotteryFilter,
                options = lotteryOptions,
                onValueSelected = onLotteryFilterChange,
                modifier = Modifier.weight(1f),
            )
        }
        if (canFilterOwner) {
            Text(
                text = "Responsable",
                style = MaterialTheme.typography.labelMedium,
                color = visual.colors.muted,
            )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactActionButton(
                label = "Limpiar",
                onClick = onClear,
                tone = ActionTone.Secondary,
                modifier = Modifier.weight(1f),
            )
            CompactActionButton(
                label = "Cerrar",
                onClick = onClose,
                tone = ActionTone.Primary,
                modifier = Modifier.weight(1f),
            )
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
