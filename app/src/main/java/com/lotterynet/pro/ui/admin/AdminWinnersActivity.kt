package com.lotterynet.pro.ui.admin

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.PrizeTableConfig
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.effectiveDrawDateKey
import com.lotterynet.pro.core.model.isPaidStatus
import com.lotterynet.pro.core.model.isPendingWinnerStatus
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscription
import com.lotterynet.pro.core.results.PrizeValidationEngine
import com.lotterynet.pro.core.storage.LocalCashierPrizePayoutRepository
import com.lotterynet.pro.core.storage.LocalResultsRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketRemoteStore
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.ForegroundCatchUpInput
import com.lotterynet.pro.core.sync.ForegroundCatchUpPolicy
import com.lotterynet.pro.core.sync.OperationalSyncThrottle
import com.lotterynet.pro.core.sync.TicketRefreshGovernor
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKeys
import com.lotterynet.pro.core.sync.resolveOperationalRealtimeOwnerKeys
import com.lotterynet.pro.core.sync.ticketRefreshGovernorKey
import com.lotterynet.pro.core.sync.invalidateTicketRealtimeCaches
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactSegmentedSelector
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.OperationalListHeader
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import com.lotterynet.pro.ui.tickets.TicketOfficialActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AdminWinnersActivity : AppCompatActivity() {
    private val adminWinnersViewModel by viewModels<AdminWinnersViewModel>()
    private val syncHandler = Handler(Looper.getMainLooper())
    private val realtimeClient = LotterynetRealtimeClient()
    private val realtimeSubscriptions = mutableListOf<LotterynetRealtimeClient.SubscriptionHandle>()
    private val winnersSyncInFlight = AtomicBoolean(false)
    private lateinit var remoteStampStore: NativeTicketRemoteStore
    private val foregroundCatchUpPolicy = ForegroundCatchUpPolicy(
        OperationalSyncThrottle(ADMIN_WINNERS_FOREGROUND_CATCH_UP_THROTTLE_MS),
    )
    private lateinit var session: ActiveSession
    private lateinit var dayKey: String
    private lateinit var salesRepository: LocalSalesRepository
    private lateinit var usersRepository: LocalUsersRepository
    private lateinit var resultsRepository: LocalResultsRepository
    private lateinit var prizePayoutRepository: LocalCashierPrizePayoutRepository
    private lateinit var operationalSyncCoordinator: NativeOperationalSyncCoordinator
    private var ticketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var lastRemoteUpdatedAt: String? = null
    private val winnersRemoteRefreshGovernor = TicketRefreshGovernor(requestCooldownMs = ADMIN_WINNERS_REMOTE_REFRESH_DEDUP_MS)
    private val resumeSyncRunnable = Runnable { runForegroundCatchUp(force = false) }
    private val syncPollRunnable = object : Runnable {
        override fun run() {
            if (realtimeClient.shouldUsePollingFallback()) {
                syncWinners(force = false)
            }
            syncHandler.postDelayed(this, resolveAdminWinnersPollIntervalMs(realtimeClient.isConfigured()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.ADMIN_WINNERS)) return
        session = activeSession ?: return
        usersRepository = LocalUsersRepository(this)
        usersRepository.touchSession(session)
        dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date())
        salesRepository = LocalSalesRepository(this)
        resultsRepository = LocalResultsRepository(this)
        prizePayoutRepository = LocalCashierPrizePayoutRepository(this)
        val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        remoteStampStore = NativeTicketRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
            bearerTokenRefresher = { sessionTokenProvider.forceFreshAccessToken() },
        )
        operationalSyncCoordinator = NativeOperationalSyncCoordinator(
            ticketGateway = NativeTicketCloudSyncCoordinator(
                salesRepository = salesRepository,
                queueRepository = NativeTicketSyncQueueRepository(this),
                remoteStore = remoteStampStore,
            ),
            remoteStampStore = remoteStampStore,
        )
        refreshWinnersData()

        setContent {
            val screenState by adminWinnersViewModel.state.collectAsState()
            LotteryNetComposeTheme {
                AdminWinnersRoute(
                    session = session,
                    bancaName = session.banca ?: "LotteryNet",
                    dayKey = dayKey,
                    tickets = screenState.tickets,
                    onBack = { finish() },
                    onOpenTicket = { ticket ->
                        startActivity(Intent(this, TicketOfficialActivity::class.java).apply {
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_ID, ticket.id)
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_EPOCH, ticket.createdAtEpochMs)
                            putExtra(TicketOfficialActivity.EXTRA_BANCA_NAME, session.banca ?: "LotteryNet")
                        })
                    },
                )
            }
        }
        syncWinners(force = true)
        subscribeRealtime(reset = false)
        if (realtimeSubscriptions.isEmpty()) {
            syncHandler.postDelayed(syncPollRunnable, resolveAdminWinnersPollIntervalMs(realtimeClient.isConfigured()))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::salesRepository.isInitialized) {
            refreshWinnersData()
            syncHandler.removeCallbacks(resumeSyncRunnable)
            syncHandler.postDelayed(resumeSyncRunnable, ADMIN_WINNERS_RESUME_SYNC_DELAY_MS)
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

    private fun refreshWinnersData() {
        val winnerDayKeys = salesRepository.getAvailableDayKeys()
            .sortedDescending()
            .take(ADMIN_WINNERS_LOOKBACK_DAYS)
        val scopedTickets = filterTicketsForOperationalScope(
            session = session,
            tickets = winnerDayKeys.flatMap(salesRepository::getTicketsForDay),
            cashiers = usersRepository.getCashiers(),
        )
        val resultsByDate = scopedTickets
            .map { it.effectiveDrawDateKey() }
            .distinct()
            .associateWith(resultsRepository::getResultsForDate)
        ticketsState = buildAdminWinnerTicketsByDrawDate(
            tickets = scopedTickets,
            resultsByDate = resultsByDate,
            prizeConfig = PrizeTableConfig(),
            prizeConfigResolver = { ticket ->
                prizePayoutRepository.resolveForTicket(
                    ownerId = ticket.adminId ?: session.adminId ?: session.userId,
                    sellerUser = ticket.sellerUser ?: ticket.adminUser,
                )
            },
        )
            .sortedByDescending { it.createdAtEpochMs }
        adminWinnersViewModel.showLocal(ticketsState)
    }

    private fun syncWinners(force: Boolean) {
        if (!winnersSyncInFlight.compareAndSet(false, true)) return
        adminWinnersViewModel.showCatchingUp()
        thread(name = "admin-winners-sync") {
            var syncFailed = false
            runCatching {
                operationalSyncCoordinator.syncTicketsForSession(
                    session = session,
                    lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                    force = force,
                )
            }.onSuccess { state ->
                lastRemoteUpdatedAt = state.remoteUpdatedAt ?: lastRemoteUpdatedAt
            }.onFailure { error ->
                syncFailed = true
                runOnUiThread {
                    adminWinnersViewModel.showRecoverableError(error.message ?: "No se pudo sincronizar ganadores.")
                }
            }.also {
                winnersSyncInFlight.set(false)
                runOnUiThread {
                    refreshWinnersData()
                    if (!syncFailed) {
                        adminWinnersViewModel.showFresh(ticketsState, lastRemoteUpdatedAt)
                    }
                }
            }
        }
    }

    private fun runForegroundCatchUp(force: Boolean) {
        if (!::operationalSyncCoordinator.isInitialized || !::salesRepository.isInitialized) return
        refreshWinnersData()
        val ownerKeys = resolveOperationalRealtimeOwnerKeys(session)
        if (ownerKeys.isEmpty()) return
        if (shouldSkipAdminWinnersRemoteRefresh(
                governor = winnersRemoteRefreshGovernor,
                ownerKey = ownerKeys.joinToString("|"),
                dayKey = dayKey,
                authScope = session.role.name,
                force = force,
            )
        ) return
        thread(name = "admin-winners-foreground-catch-up") {
            val remoteStamps = ownerKeys.mapNotNull { ownerKey ->
                runCatching { remoteStampStore.fetchUpdatedAtFresh(ownerKey) }.getOrNull()
            }
            val remoteUpdatedAt = remoteStamps.asSequence()
                .firstOrNull { stamp -> !stamp.equals(lastRemoteUpdatedAt.orEmpty(), ignoreCase = true) }
                ?: remoteStamps.firstOrNull()
            val decision = foregroundCatchUpPolicy.decide(
                resolveAdminWinnersForegroundCatchUpInput(
                    session = session,
                    tickets = salesRepository.getTicketsForDay(dayKey),
                    dayKey = dayKey,
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
                runOnUiThread { syncWinners(force = force || decision.refreshTickets) }
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
            ) {
                invalidateTicketRealtimeCaches(ownerKey)
                syncWinners(force = false)
            }
        }
    }
}

private const val ADMIN_WINNERS_REMOTE_REFRESH_DEDUP_MS = 15_000L

internal fun shouldSkipAdminWinnersRemoteRefresh(
    governor: TicketRefreshGovernor,
    ownerKey: String,
    dayKey: String,
    authScope: String,
    force: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
): Boolean {
    if (force) return false
    return governor.shouldReuse(
        ticketRefreshGovernorKey(
            ownerKey = ownerKey,
            requestType = "admin-winners:$dayKey",
            authScope = authScope,
        ),
        nowMs = nowEpochMs,
    )
}

internal fun canOpenWinnersForRole(role: UserRole): Boolean {
    return role == UserRole.ADMIN
}

internal fun isWinnerListTicket(ticket: TicketRecord): Boolean {
    return !ticket.status.equals("voided", true) &&
        !ticket.status.equals("invalid", true) &&
        !ticket.status.equals("nulled", true) &&
        (ticket.isPendingWinnerStatus() || ticket.isPaidStatus() || ticket.totalPrize > 0.0)
}

internal fun buildAdminWinnerTickets(
    tickets: List<TicketRecord>,
    results: List<LotteryResult>,
    prizeConfig: PrizeTableConfig = PrizeTableConfig(),
    prizeConfigResolver: ((TicketRecord) -> PrizeTableConfig)? = null,
): List<TicketRecord> {
    val engine = PrizeValidationEngine()
    return tickets.map { ticket ->
        if (results.isEmpty()) {
            ticket
        } else {
            val validation = engine.validate(
                ticket = ticket,
                results = results,
                prizeConfig = prizeConfigResolver?.invoke(ticket) ?: prizeConfig,
            )
            if (validation.didValidate) {
                resolveWinnerListTicketAfterValidation(ticket, validation.ticket)
            } else {
                ticket
            }
        }
    }.filter(::isWinnerListTicket)
}

internal fun buildAdminWinnerTicketsByDrawDate(
    tickets: List<TicketRecord>,
    resultsByDate: Map<String, List<LotteryResult>>,
    prizeConfig: PrizeTableConfig = PrizeTableConfig(),
    prizeConfigResolver: ((TicketRecord) -> PrizeTableConfig)? = null,
): List<TicketRecord> {
    return tickets
        .groupBy { it.effectiveDrawDateKey() }
        .flatMap { (drawDateKey, dayTickets) ->
            buildAdminWinnerTickets(
                tickets = dayTickets,
                results = resultsByDate[drawDateKey].orEmpty(),
                prizeConfig = prizeConfig,
                prizeConfigResolver = prizeConfigResolver,
            )
        }
}

internal fun resolveWinnerListTicketAfterValidation(
    current: TicketRecord,
    validated: TicketRecord,
): TicketRecord {
    val currentHasPrize = isWinnerListTicket(current)
    val validationRemovedPrize = !isWinnerListTicket(validated) || validated.totalPrize <= 0.0
    return if (currentHasPrize && validationRemovedPrize) current else validated
}

private fun isPendingWinnerListTicket(ticket: TicketRecord): Boolean {
    return !ticket.isPaidStatus() &&
        (ticket.isPendingWinnerStatus() || ticket.totalPrize > 0.0)
}

internal fun filterPendingWinnerTickets(tickets: List<TicketRecord>): List<TicketRecord> {
    return tickets.filter(::isPendingWinnerListTicket)
}

internal fun filterPaidWinnerTickets(tickets: List<TicketRecord>): List<TicketRecord> {
    return tickets.filter { it.isPaidStatus() }
}

private enum class WinnersFilter(
    val id: String,
    val label: String,
) {
    PENDING("pending", "Pendientes"),
    PAID("paid", "Pagados"),
    ALL("all", "Todos"),
}

internal const val ADMIN_WINNERS_POLL_MS = 60_000L
internal const val ADMIN_WINNERS_REALTIME_FALLBACK_POLL_MS = 300_000L
internal const val ADMIN_WINNERS_RESUME_SYNC_DELAY_MS = 100L
internal const val ADMIN_WINNERS_FOREGROUND_CATCH_UP_THROTTLE_MS = 10_000L
internal const val ADMIN_WINNERS_LOOKBACK_DAYS = 30

internal fun resolveAdminWinnersPollIntervalMs(realtimeEnabled: Boolean): Long {
    return if (realtimeEnabled) ADMIN_WINNERS_REALTIME_FALLBACK_POLL_MS else ADMIN_WINNERS_POLL_MS
}

internal fun resolveAdminWinnersForegroundCatchUpInput(
    session: ActiveSession,
    tickets: List<TicketRecord>,
    dayKey: String,
    lastRemoteUpdatedAt: String?,
    remoteUpdatedAt: String?,
    realtimeConfigured: Boolean,
    hasRealtimeSubscription: Boolean,
    nowEpochMs: Long,
    force: Boolean = false,
): ForegroundCatchUpInput {
    return ForegroundCatchUpInput(
        ownerKey = resolveOperationalOwnerKey(session),
        dateKey = dayKey,
        hasLocalTickets = tickets.any { ticket ->
            ticket.effectiveDrawDateKey().equals(dayKey, ignoreCase = true)
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

@Composable
private fun AdminWinnersRoute(
    session: ActiveSession,
    bancaName: String,
    dayKey: String,
    tickets: List<TicketRecord>,
    onBack: () -> Unit,
    onOpenTicket: (TicketRecord) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(WinnersFilter.PENDING) }
    val filteredTickets = remember(tickets, filter) {
        when (filter) {
            WinnersFilter.PENDING -> filterPendingWinnerTickets(tickets)
            WinnersFilter.PAID -> filterPaidWinnerTickets(tickets)
            WinnersFilter.ALL -> tickets
        }
    }
    val pendingCount = remember(tickets) { filterPendingWinnerTickets(tickets).size }
    val paidCount = remember(tickets) { filterPaidWinnerTickets(tickets).size }
    val pendingAmount = remember(tickets) { filterPendingWinnerTickets(tickets).sumOf(::winnerAmount) }
    val paidAmount = remember(tickets) { filterPaidWinnerTickets(tickets).sumOf(::winnerAmount) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = session.role,
                active = NativeBottomTab.MENU,
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
                verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = visual.sizes.screenPaddingV),
            ) {
                item {
                    ScreenHeaderPanel(
                        title = "Cobro",
                        subtitle = "Tickets ganadores · $bancaName · $dayKey",
                        onBack = onBack,
                        badgeLabel = filter.label,
                        badgeTone = if (filter == WinnersFilter.PENDING) warningColor() else Color(0xFF0F9F66),
                    )
                }
                item {
                    CompactPanel(
                        alt = true,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        OperationalListHeader(
                            title = "Estado de cobro",
                            meta = "${filteredTickets.size} visibles",
                        )
                        CompactSegmentedSelector(
                            options = WinnersFilter.values().map { option ->
                                QuickFilterChip(option.id, option.label)
                            },
                            selectedId = filter.id,
                            onSelected = { selectedId ->
                                filter = WinnersFilter.values().firstOrNull { option -> option.id == selectedId }
                                    ?: WinnersFilter.PENDING
                            },
                            columns = 3,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "$pendingCount pendientes · ${formatWinnerMoney(pendingAmount)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = warningColor(),
                            )
                            Text(
                                "$paidCount pagados · ${formatWinnerMoney(paidAmount)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF0F9F66),
                            )
                        }
                    }
                }
                item {
                    SectionHeader(
                        title = "Tickets ganadores",
                        meta = "${filter.label} · ${filteredTickets.size}",
                    )
                }
                if (filteredTickets.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("No hay tickets ganadores en este filtro.", color = visual.colors.muted)
                        }
                    }
                } else {
                    items(filteredTickets, key = { it.id }) { ticket ->
                        WinnerTicketRow(ticket = ticket, onOpen = { onOpenTicket(ticket) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WinnerTicketRow(
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ticket.serial ?: ticket.id, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink)
                    CompactStatusBadge(
                        label = winnerStatus(ticket.status),
                        tone = winnerStatusColor(ticket.status),
                    )
                }
                Text(
                    "${ticket.sellerUser ?: ticket.adminUser ?: "sin usuario"} · ${winnerTicketTime(ticket.createdAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
                Text(
                    ticket.plays.joinToString(" · ") { play -> "${play.playType} ${play.number}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.ink,
                )
                Text(
                    "Premio ${formatWinnerMoney(winnerAmount(ticket))}",
                    style = MaterialTheme.typography.titleSmall,
                    color = winnerStatusColor(ticket.status),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

private fun winnerAmount(ticket: TicketRecord): Double = ticket.totalPrize.coerceAtLeast(0.0)

private fun winnerStatus(status: String): String {
    if (com.lotterynet.pro.core.model.isPaidTicketStatus(status)) return "Pagado"
    return when (status.lowercase(Locale.getDefault())) {
        "winner" -> "Pendiente"
        else -> "Activo"
    }
}

private fun winnerStatusColor(status: String): Color {
    if (com.lotterynet.pro.core.model.isPaidTicketStatus(status)) return Color(0xFF0F9F66)
    return when (status.lowercase(Locale.getDefault())) {
        "winner" -> warningColor()
        else -> Color(0xFF324763)
    }
}

private fun formatWinnerMoney(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)

private fun winnerTicketTime(epochMs: Long): String {
    return SimpleDateFormat("hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date(epochMs))
}
