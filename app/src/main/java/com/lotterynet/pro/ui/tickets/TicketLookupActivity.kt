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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.calendar.LotteryClosePolicy
import com.lotterynet.pro.core.calendar.StaticHolidayCalendarRepository
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.dominicanDayKey
import com.lotterynet.pro.core.model.isPaidStatus
import com.lotterynet.pro.core.model.isPendingWinnerStatus
import com.lotterynet.pro.core.operations.buildUserActorLabelLookup
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.core.operations.resolveTicketActorLabel
import com.lotterynet.pro.core.sales.SupabaseTicketBackendClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeOrchestrator
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketRemoteStore
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.invalidateTicketRealtimeCaches
import com.lotterynet.pro.core.sync.resolveOperationalRealtimeOwnerKeys
import com.lotterynet.pro.core.sync.isTerminalCancelTicketStatus
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.core.storage.LocalAdminLotteryConfigRepository
import com.lotterynet.pro.core.storage.LocalTrustedClockRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactEmptyState
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.ScreenChromeAction
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.normalizeTicketLookupModeForRole
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.sales.resolveSalesStartupSystemModeConfig
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.json.JSONObject

class TicketLookupActivity : AppCompatActivity() {
    private val lookupRefreshHandler = Handler(Looper.getMainLooper())
    private val realtimeClient = LotterynetRealtimeClient()
    private val realtimeSubscriptions = mutableListOf<LotterynetRealtimeClient.SubscriptionHandle>()
    private val lookupSyncInFlight = AtomicBoolean(false)
    private lateinit var lookupSession: ActiveSession
    private lateinit var lookupSalesRepository: LocalSalesRepository
    private lateinit var operationalSyncCoordinator: NativeOperationalSyncCoordinator
    private var allTicketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var deletedTicketIdsState by mutableStateOf<Set<String>>(emptySet())
    private val realtimeOrchestrator = LotterynetRealtimeOrchestrator(
        onTicketOwnerChanged = { ownerKey ->
            invalidateTicketRealtimeCaches(ownerKey)
            scheduleRealtimeLookupRefresh()
        },
    )
    private val realtimeLookupRefreshRunnable = Runnable { refreshLookupFromRealtime() }
    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents?.trim().orEmpty()
        if (contents.isNotBlank()) {
            pendingQrResult = resolveTicketLookupQueryFromScan(contents)
        }
    }

    private var pendingQrResult by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, session?.role, NativeDestination.TICKET_LOOKUP)) {
            return
        }
        checkNotNull(session)
        lookupSession = session
        val usersRepository = LocalUsersRepository(this)
        usersRepository.touchSession(session)
        val lookupMode = LookupMode.from(normalizeTicketLookupModeForRole(session.role, intent?.getStringExtra(EXTRA_MODE)))
        val requestedCashierKey = intent?.getStringExtra(EXTRA_CASHIER_KEY)?.trim().orEmpty()
        val salesRepository = LocalSalesRepository(this)
        lookupSalesRepository = salesRepository
        allTicketsState = salesRepository.getAllTickets()
        deletedTicketIdsState = salesRepository.getDeletedTicketIds()
        val remoteStore = NativeTicketRemoteStore(
            bearerTokenProvider = { SupabaseSessionTokenProvider(LocalSessionRepository(this)).freshAccessToken() },
            bearerTokenRefresher = { SupabaseSessionTokenProvider(LocalSessionRepository(this)).forceFreshAccessToken() },
        )
        operationalSyncCoordinator = NativeOperationalSyncCoordinator(
            ticketGateway = NativeTicketCloudSyncCoordinator(
                salesRepository = salesRepository,
                queueRepository = NativeTicketSyncQueueRepository(this),
                remoteStore = remoteStore,
            ),
            remoteStampStore = remoteStore,
        )
        val cashiers = usersRepository.getCashiers()
        val systemModeConfig = resolveSalesStartupSystemModeConfig(
            session = session,
            usersRepository = usersRepository,
            adminLotteryConfigRepository = LocalAdminLotteryConfigRepository(this),
        )
        val openOfficialTicket: (TicketRecord) -> Unit = { ticket ->
            startActivity(Intent(this, TicketOfficialActivity::class.java).apply {
                putExtra(TicketOfficialActivity.EXTRA_TICKET_ID, ticket.id)
                putExtra(TicketOfficialActivity.EXTRA_TICKET_EPOCH, ticket.createdAtEpochMs)
                putExtra(TicketOfficialActivity.EXTRA_BANCA_NAME, session.banca ?: "LotteryNet")
                putExtra(TicketOfficialActivity.EXTRA_ACTION_MODE, lookupMode.officialModeKey)
            })
        }

        setContent {
            LotteryNetComposeTheme {
                var query by rememberSaveable { mutableStateOf(intent?.getStringExtra(EXTRA_INITIAL_QUERY).orEmpty()) }
                var qrValue by remember { mutableStateOf("") }
                var autoScanStarted by rememberSaveable { mutableStateOf(false) }
                var autoOpenedQr by rememberSaveable { mutableStateOf("") }
                var payingAll by rememberSaveable { mutableStateOf(false) }
                var payAllProgress by rememberSaveable { mutableStateOf("") }
                LaunchedEffect(Unit) {
                    if (intent?.getBooleanExtra(EXTRA_AUTO_SCAN, false) == true && !autoScanStarted) {
                        autoScanStarted = true
                        launchQrScanner()
                    }
                }
                pendingQrResult?.takeIf { it.isNotBlank() }?.let { scanned ->
                    if (scanned != qrValue) {
                        qrValue = scanned
                        query = scanned
                    }
                }
                val tickets = remember(query, allTicketsState, cashiers, lookupMode, deletedTicketIdsState, systemModeConfig, requestedCashierKey) {
                    filterLookupTicketsForSession(
                        session = session,
                        tickets = allTicketsState,
                        cashiers = cashiers,
                        mode = lookupMode,
                        query = query,
                        deletedTicketIds = deletedTicketIdsState,
                        systemModeConfig = systemModeConfig,
                    )
                }
                LaunchedEffect(qrValue, tickets) {
                    if (qrValue.isBlank() || qrValue == autoOpenedQr) return@LaunchedEffect
                    val scannedTicket = resolveAutoOpenScannedTicket(qrValue, tickets)
                    if (scannedTicket != null) {
                        autoOpenedQr = qrValue
                        openOfficialTicket(scannedTicket)
                    }
                }
                TicketLookupRoute(
                    role = session.role,
                    mode = lookupMode,
                    bancaName = session.banca ?: "LotteryNet",
                    requestedCashierKey = requestedCashierKey,
                    query = query,
                    tickets = tickets,
                    actorLabelsByKey = buildUserActorLabelLookup(cashiers),
                    onQueryChange = { query = it.uppercase(Locale.getDefault()) },
                    onScanQr = { launchQrScanner() },
                    isPayingAll = payingAll,
                    payAllProgress = payAllProgress,
                    onPayAll = { visibleTickets ->
                        if (payingAll) return@TicketLookupRoute
                        val pendingTickets = visibleTickets.filter(::isLookupBulkPayableTicket)
                        if (pendingTickets.isEmpty()) {
                            Toast.makeText(this, "No hay premios pendientes para pagar.", Toast.LENGTH_SHORT).show()
                            return@TicketLookupRoute
                        }
                        payingAll = true
                        payAllProgress = "0/${pendingTickets.size}"
                        Toast.makeText(this, "Pagando ${pendingTickets.size} ticket(s) en orden...", Toast.LENGTH_SHORT).show()
                        thread(name = "ticket-pay-all") {
                            val tokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
                            val backendClient = SupabaseTicketBackendClient()
                            var paidCount = 0
                            var failedCount = 0
                            pendingTickets.forEachIndexed { index, ticket ->
                                val result = runCatching {
                                    val freshBearerToken = tokenProvider.freshAccessToken()
                                    val response = backendClient.payTicket(
                                        request = resolveTicketPayoutBackendRequest(session, ticket),
                                        bearerToken = freshBearerToken,
                                    )
                                    val paidPrize = response.optDouble("amount", ticket.totalPrize)
                                    val paidTicket = ticket.copy(
                                        status = "paid",
                                        totalPrize = paidPrize.takeIf { it > 0.0 } ?: ticket.totalPrize,
                                    )
                                    salesRepository.replaceTicket(paidTicket)
                                }
                                if (result.isSuccess) {
                                    paidCount += 1
                                } else {
                                    failedCount += 1
                                }
                                runOnUiThread {
                                    allTicketsState = salesRepository.getAllTickets()
                                    payAllProgress = "${index + 1}/${pendingTickets.size}"
                                }
                                Thread.sleep(180)
                            }
                            runOnUiThread {
                                allTicketsState = salesRepository.getAllTickets()
                                payingAll = false
                                payAllProgress = ""
                                val message = if (failedCount == 0) {
                                    "Pagados $paidCount ticket(s)."
                                } else {
                                    "Pagados $paidCount; fallaron $failedCount. Revisa pendientes."
                                }
                                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onDuplicateTicket = { ticket ->
                        startActivity(Intent(this, TicketOfficialActivity::class.java).apply {
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_ID, ticket.id)
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_EPOCH, ticket.createdAtEpochMs)
                            putExtra(TicketOfficialActivity.EXTRA_BANCA_NAME, session.banca ?: "LotteryNet")
                            putExtra(
                                TicketOfficialActivity.EXTRA_ACTION_MODE,
                                resolveTicketLookupDuplicateActionOfficialModeKey(),
                            )
                        })
                    },
                    onOpenTicket = { ticket ->
                        val resolution = resolveTicketOpenRequest(
                            requestedTicket = ticket,
                            currentTickets = salesRepository.getAllTickets(),
                            deletedTicketIds = deletedTicketIdsState,
                        )
                        val currentTicket = resolution.ticket
                        if (currentTicket == null) {
                            Toast.makeText(this, resolution.message ?: STALE_TICKET_MESSAGE, Toast.LENGTH_SHORT).show()
                            return@TicketLookupRoute
                        }
                        openOfficialTicket(currentTicket)
                    },
                )
            }
        }
        subscribeRealtime()
    }

    override fun onResume() {
        super.onResume()
        refreshLookupLocalState()
        // Catch up with the authoritative yesterday/today window when entering
        // or returning to Cobro. The shared sync governor prevents duplicate
        // requests, while Realtime continues to handle changes while visible.
        refreshLookupFromRealtime()
    }

    override fun onDestroy() {
        lookupRefreshHandler.removeCallbacks(realtimeLookupRefreshRunnable)
        realtimeSubscriptions.forEach { it.close() }
        realtimeSubscriptions.clear()
        realtimeClient.shutdown()
        super.onDestroy()
    }

    private fun refreshLookupLocalState() {
        if (!::lookupSalesRepository.isInitialized) return
        allTicketsState = lookupSalesRepository.getAllTickets()
        deletedTicketIdsState = lookupSalesRepository.getDeletedTicketIds()
    }

    private fun subscribeRealtime() {
        if (!realtimeClient.isConfigured() || realtimeSubscriptions.isNotEmpty()) return
        val tokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        resolveTicketLookupRealtimeOwnerKeys(lookupSession).forEach { ownerKey ->
            realtimeSubscriptions += realtimeClient.subscribeTicketOwnerSignals(
                ownerKey = ownerKey,
                bearerTokenProvider = { tokenProvider.freshAccessToken() },
                onEvent = realtimeOrchestrator::onEvent,
            )
        }
    }

    private fun scheduleRealtimeLookupRefresh() {
        lookupRefreshHandler.removeCallbacks(realtimeLookupRefreshRunnable)
        lookupRefreshHandler.postDelayed(realtimeLookupRefreshRunnable, LOOKUP_REALTIME_DEBOUNCE_MS)
    }

    private fun refreshLookupFromRealtime() {
        if (!::operationalSyncCoordinator.isInitialized || !lookupSyncInFlight.compareAndSet(false, true)) return
        thread(name = "ticket-lookup-realtime-sync") {
            runCatching {
                resolveTicketLookupRealtimeOwnerKeys(lookupSession).forEach { ownerKey ->
                    operationalSyncCoordinator.refreshOwnerFromRealtime(ownerKey, lookupSession.banca)
                }
            }
            runOnUiThread {
                refreshLookupLocalState()
                lookupSyncInFlight.set(false)
            }
        }
    }

    private fun launchQrScanner() {
        val contract = resolveTicketQrScannerContract()
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE, ScanOptions.CODE_128, ScanOptions.CODE_39)
            setPrompt("Escanea el ticket")
            setBeepEnabled(false)
            setOrientationLocked(contract.orientationLocked)
            setCaptureActivity(QrCaptureActivity::class.java)
        }
        qrLauncher.launch(options)
    }

    companion object {
        const val EXTRA_MODE = "ticket_lookup_mode"
        const val EXTRA_CASHIER_KEY = "ticket_lookup_cashier_key"
        const val EXTRA_INITIAL_QUERY = "ticket_lookup_query"
        const val EXTRA_AUTO_SCAN = "ticket_lookup_auto_scan"
    }
}

internal data class TicketQrScannerContract(
    val orientationLocked: Boolean,
    val captureActivityClassName: String,
    val formats: List<String>,
)

internal fun resolveTicketQrScannerContract(): TicketQrScannerContract {
    return TicketQrScannerContract(
        orientationLocked = true,
        captureActivityClassName = QrCaptureActivity::class.java.name,
        formats = listOf(ScanOptions.QR_CODE, ScanOptions.CODE_128, ScanOptions.CODE_39),
    )
}

internal fun resolveTicketLookupQueryFromScan(raw: String): String {
    val cleaned = raw.trim()
    if (cleaned.isBlank()) return ""
    runCatching {
        val json = JSONObject(cleaned)
        listOf("serial", "id", "ticketId", "ticket", "securityCode").forEach { key ->
            json.optString(key).trim().takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    val urlTicket = Regex("""(?:ticket|ticketId|id|serial)=([^&#\s]+)""", RegexOption.IGNORE_CASE)
        .find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!urlTicket.isNullOrBlank()) return java.net.URLDecoder.decode(urlTicket, "UTF-8")
    val thermalParts = cleaned.split("|")
    if (thermalParts.size >= 2 && thermalParts.first().equals("LN", ignoreCase = true)) {
        thermalParts[1].trim().takeIf { it.isNotBlank() }?.let { return it }
    }
    return cleaned
}

internal fun resolveAutoOpenScannedTicket(
    scannedQuery: String,
    tickets: List<TicketRecord>,
): TicketRecord? {
    val normalized = scannedQuery.trim().lowercase(Locale.getDefault())
    if (normalized.isBlank()) return null
    val exactMatches = tickets.filter { ticket ->
        matchesExactTicketLookup(ticket, normalized)
    }
    return exactMatches.singleOrNull()
}

internal enum class LookupMode(
    val title: String,
    val subtitle: String,
    val emptyLabel: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val officialModeKey: String,
) {
    SEARCH(
        title = "Buscar ticket",
        subtitle = "Busca tickets por serial, usuario, loteria o QR y abre la vista oficial.",
        emptyLabel = "No hay tickets que coincidan con este filtro.",
        icon = Icons.Rounded.Search,
        officialModeKey = "buscar",
    ),
    PAY(
        title = "Cobros · Buscar ganador",
        subtitle = "Busca ganadores por fecha del premio y abre el ticket oficial para cobrar.",
        emptyLabel = "No hay tickets para cobro con este filtro.",
        icon = Icons.Rounded.Paid,
        officialModeKey = "pagar",
    ),
    VOID(
        title = "Eliminar ticket",
        subtitle = "Busca tickets activos o ganadores para eliminarlos con validacion del servidor.",
        emptyLabel = "No hay tickets eliminables con este filtro.",
        icon = Icons.Rounded.DeleteForever,
        officialModeKey = "anular",
    ),
    DUPLICATE(
        title = "Duplicar ticket",
        subtitle = "Busca el ticket y duplica sus jugadas para una nueva venta.",
        emptyLabel = "No hay tickets para duplicar con este filtro.",
        icon = Icons.Rounded.ContentCopy,
        officialModeKey = "duplicar",
    );

    companion object {
        fun from(raw: String?): LookupMode {
            return when (raw?.trim()?.lowercase(Locale.US)) {
                "buscar", "lookup", "ticket_lookup" -> SEARCH
                "pagar" -> PAY
                "anular" -> VOID
                "duplicar", "duplicate", "copy" -> DUPLICATE
                else -> SEARCH
            }
        }
    }
}

internal enum class TicketLookupRowAction {
    OPEN,
    DUPLICATE,
}

internal fun resolveTicketLookupRowActions(mode: LookupMode): List<TicketLookupRowAction> {
    return if (mode == LookupMode.DUPLICATE) {
        listOf(TicketLookupRowAction.OPEN, TicketLookupRowAction.DUPLICATE)
    } else {
        listOf(TicketLookupRowAction.OPEN)
    }
}

internal fun resolveTicketLookupDuplicateActionOfficialModeKey(): String = LookupMode.DUPLICATE.officialModeKey

internal fun resolveDuplicateLotteriesForTicket(
    ticket: TicketRecord,
    availableLotteries: List<LotteryCatalogItem>,
): List<LotteryCatalogItem> {
    val byId = availableLotteries.associateBy { it.id }
    val originalIds = ticket.plays.mapNotNull { it.lotteryId?.takeIf(String::isNotBlank) }.distinct()
    return originalIds.mapNotNull(byId::get)
}

internal fun filterLookupTicketsForSession(
    session: ActiveSession,
    tickets: List<TicketRecord>,
    cashiers: List<UserAccount>,
    mode: LookupMode,
    query: String,
    deletedTicketIds: Set<String> = emptySet(),
    systemModeConfig: AdminSystemModeConfig? = null,
): List<TicketRecord> {
    return filterTickets(
        tickets = filterTicketsForOperationalScope(session, tickets, cashiers),
        mode = mode,
        query = query,
        deletedTicketIds = deletedTicketIds,
        actorLabelsByKey = buildUserActorLabelLookup(cashiers),
        systemModeConfig = systemModeConfig,
    )
}

private fun filterTickets(
    tickets: List<TicketRecord>,
    mode: LookupMode,
    query: String,
    deletedTicketIds: Set<String>,
    actorLabelsByKey: Map<String, String> = emptyMap(),
    systemModeConfig: AdminSystemModeConfig? = null,
): List<TicketRecord> {
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    return tickets
        .asSequence()
        .filter { ticket -> ticket.id !in deletedTicketIds }
        .filter { ticket ->
            when (mode) {
                LookupMode.SEARCH -> true
                LookupMode.PAY -> isLookupPayableTicket(ticket) ||
                    (normalizedQuery.isNotBlank() && matchesExactTicketLookup(ticket, normalizedQuery))
                LookupMode.VOID -> !ticket.isPaidStatus() && canRepeatTicket(ticket)
                LookupMode.DUPLICATE -> canRepeatTicket(ticket) &&
                    (systemModeConfig == null || ticketMatchesDuplicateSystemMode(ticket, systemModeConfig))
            }
        }
        .filter { ticket ->
            if (normalizedQuery.isBlank()) return@filter true
            val lotteries = ticket.plays.mapNotNull { it.lotteryName }.joinToString(" ").lowercase(Locale.getDefault())
            val owner = resolveTicketActorLabel(ticket, actorLabelsByKey, fallback = "").lowercase(Locale.getDefault())
            val serial = (ticket.serial ?: "").lowercase(Locale.getDefault())
            val security = (ticket.securityCode ?: "").lowercase(Locale.getDefault())
            val id = ticket.id.lowercase(Locale.getDefault())
            normalizedQuery in id || normalizedQuery in serial || normalizedQuery in security ||
                normalizedQuery in lotteries || normalizedQuery in owner
        }
        .take(60)
        .toList()
}

internal fun filterTicketLookupPaymentView(
    tickets: List<TicketRecord>,
    mode: LookupMode,
    query: String,
    dateFilter: String,
    statusFilter: String,
    todayDayKey: String,
): List<TicketRecord> {
    if (mode != LookupMode.PAY || query.trim().isNotBlank()) return tickets
    val today = runCatching { LocalDate.parse(todayDayKey) }.getOrNull()
    val yesterdayKey = today?.minusDays(1)?.toString()
    return tickets.filter { ticket ->
        val ticketDayKey = dominicanDayKey(ticket.createdAtEpochMs)
        val dateMatches = when (dateFilter) {
            "today" -> ticketDayKey == todayDayKey
            "yesterday" -> yesterdayKey != null && ticketDayKey == yesterdayKey
            else -> dateFilter.removePrefix("date:").takeIf { it != dateFilter }?.let { ticketDayKey == it } ?: true
        }
        val statusMatches = when (statusFilter) {
            "pending" -> isLookupBulkPayableTicket(ticket)
            "paid" -> ticket.isPaidStatus()
            else -> true
        }
        dateMatches && statusMatches
    }
}

internal fun filterTicketLookupToolbarTickets(
    tickets: List<TicketRecord>,
    dateFilter: String,
    statusFilter: String,
    todayDayKey: String,
): List<TicketRecord> {
    val today = runCatching { LocalDate.parse(todayDayKey) }.getOrNull()
    val yesterdayKey = today?.minusDays(1)?.toString()
    return tickets.filter { ticket ->
        val ticketDayKey = dominicanDayKey(ticket.createdAtEpochMs)
        val dateMatches = when (dateFilter) {
            "today" -> ticketDayKey == todayDayKey
            "yesterday" -> yesterdayKey != null && ticketDayKey == yesterdayKey
            else -> dateFilter.removePrefix("date:").takeIf { it != dateFilter }?.let { ticketDayKey == it } ?: true
        }
        val statusMatches = when (statusFilter) {
            "paid" -> ticket.isPaidStatus()
            "unpaid" -> !ticket.isPaidStatus()
            else -> true
        }
        dateMatches && statusMatches
    }
}

private fun lookupStatusLabel(value: String): String = when (value) {
    "pending" -> "Pendientes"
    "paid" -> "Pagados"
    else -> "Todos"
}

private fun ticketStatusLabel(value: String): String = when (value) {
    "paid" -> "Pagados"
    "unpaid" -> "No pagados"
    else -> "Todos"
}

internal fun ticketLookupPaymentDateOptions(): List<QuickFilterChip> = listOf(
    QuickFilterChip("today", "Hoy"),
    QuickFilterChip("yesterday", "Ayer"),
    QuickFilterChip("all", "Todos"),
)

internal fun ticketLookupPaymentDateLabel(dateKey: String): String {
    val date = parseTicketDateKey(dateKey)?.timeInMillis ?: return "Fecha exacta"
    return SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("es-DO")).format(Date(date))
        .replaceFirstChar { it.uppercase(Locale.forLanguageTag("es-DO")) }
}

internal fun ticketLookupPaymentStatusOptions(): List<QuickFilterChip> = listOf(
    QuickFilterChip("pending", "Pendientes"),
    QuickFilterChip("paid", "Pagados"),
    QuickFilterChip("all", "Todos"),
)

internal fun ticketMatchesDuplicateSystemMode(
    ticket: TicketRecord,
    config: AdminSystemModeConfig,
): Boolean {
    if (config.lotteryModeEnabled && config.pickModeEnabled) return true
    val hasPick = ticket.plays.any(::isPickDuplicatePlay)
    val hasLottery = ticket.plays.any { !isPickDuplicatePlay(it) }
    return when {
        config.pickModeEnabled -> hasPick
        config.lotteryModeEnabled -> hasLottery
        else -> false
    }
}

private fun isPickDuplicatePlay(play: com.lotterynet.pro.core.model.PlayItem): Boolean {
    val type = play.playType.filter(Char::isLetterOrDigit).uppercase(Locale.US)
    val lotteryId = play.lotteryId.orEmpty().uppercase(Locale.US)
    return type in setOf("P3", "P4", "P3BOX", "P4BOX", "P3STRAIGHT", "P4STRAIGHT") ||
        type.contains("PICK3") ||
        type.contains("PICK4") ||
        lotteryId.startsWith("US-P3-") ||
        lotteryId.startsWith("US-P4-")
}

private fun matchesExactTicketLookup(ticket: TicketRecord, normalizedQuery: String): Boolean {
    return ticket.id.lowercase(Locale.getDefault()) == normalizedQuery ||
        (ticket.serial ?: "").lowercase(Locale.getDefault()) == normalizedQuery ||
        (ticket.securityCode ?: "").lowercase(Locale.getDefault()) == normalizedQuery
}

internal fun isLookupPayableTicket(ticket: TicketRecord): Boolean {
    return ticket.isPendingWinnerStatus() ||
        ticket.isPaidStatus() ||
        (
            ticket.totalPrize > 0.0 &&
                !isTerminalCancelTicketStatus(ticket.status)
            )
}

internal fun isLookupBulkPayableTicket(ticket: TicketRecord): Boolean {
    return !ticket.isPaidStatus() &&
        !isTerminalCancelTicketStatus(ticket.status) &&
        (ticket.isPendingWinnerStatus() || ticket.totalPrize > 0.0)
}

private fun resolveLookupDuplicateLotteries(
    context: android.content.Context,
    session: ActiveSession,
): List<LotteryCatalogItem> {
    val catalogRepository = StaticLotteryCatalogRepository()
    val trustedClockRepository = LocalTrustedClockRepository(context)
    val holidayRepository = StaticHolidayCalendarRepository(
        dominicanLotteryIds = catalogRepository.getCalendarRule().dominicanLotteryIds,
        americanLotteryIds = catalogRepository.getCalendarRule().americanLotteryIds,
    )
    val closePolicy = LotteryClosePolicy(trustedClockRepository, holidayRepository)
    val territory = if (session.territory.equals("USA", ignoreCase = true) || session.territory.equals("US", ignoreCase = true)) {
        LotteryTerritory.USA
    } else {
        LotteryTerritory.RD
    }
    val nowUtcMs = trustedClockRepository.getTrustedUtcMs()
    return catalogRepository.getAllLotteries().filter { lottery ->
        !closePolicy.resolveCloseDecision(
            lottery = lottery,
            operationTerritory = territory,
            nowUtcMs = nowUtcMs,
        ).isClosed
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TicketLookupRoute(
    role: UserRole,
    mode: LookupMode,
    bancaName: String,
    requestedCashierKey: String,
    query: String,
    tickets: List<TicketRecord>,
    actorLabelsByKey: Map<String, String>,
    onQueryChange: (String) -> Unit,
    onScanQr: () -> Unit,
    isPayingAll: Boolean,
    payAllProgress: String,
    onPayAll: (List<TicketRecord>) -> Unit,
    onDuplicateTicket: (TicketRecord) -> Unit,
    onOpenTicket: (TicketRecord) -> Unit,
) {
    val context = LocalContext.current
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveTicketCollectionLayout(visual.windowMode) }
    val todayDayKey = remember { dominicanDayKey(System.currentTimeMillis()) }
    val cashierOptions = remember(actorLabelsByKey) {
        actorLabelsByKey.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .groupBy { it.value.trim().lowercase(Locale.getDefault()) }
            .values
            .mapNotNull { entries ->
                entries.minWithOrNull(
                    compareBy<Map.Entry<String, String>> { entry ->
                        // Prefer the readable username over the technical CAJ-* id.
                        entry.key.trim().uppercase(Locale.US).startsWith("CAJ-")
                    }.thenBy { it.key.lowercase(Locale.getDefault()) },
                )
            }
            .sortedBy { it.value.lowercase(Locale.getDefault()) }
    }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var selectedCashierKey by rememberSaveable { mutableStateOf("") }
    var ticketDateFilter by rememberSaveable { mutableStateOf("all") }
    var ticketExactDate by rememberSaveable { mutableStateOf(todayDayKey) }
    var ticketStatusFilter by rememberSaveable { mutableStateOf("all") }
    var paymentDateFilter by rememberSaveable { mutableStateOf("today") }
    var paymentExactDate by rememberSaveable { mutableStateOf(todayDayKey) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var paymentStatusFilter by rememberSaveable { mutableStateOf("pending") }
    var cashierMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var dateMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var statusMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val visibleTickets = remember(
        tickets,
        mode,
        query,
        selectedCashierKey,
        ticketDateFilter,
        ticketExactDate,
        ticketStatusFilter,
        paymentDateFilter,
        paymentStatusFilter,
        todayDayKey,
        requestedCashierKey,
    ) {
        val dateFiltered = if (mode == LookupMode.PAY) {
            filterTicketLookupPaymentView(
                tickets = tickets,
                mode = mode,
                query = query,
                dateFilter = paymentDateFilter,
                statusFilter = paymentStatusFilter,
                todayDayKey = todayDayKey,
            )
        } else {
            filterTicketLookupToolbarTickets(
                tickets = tickets,
                dateFilter = ticketDateFilter,
                statusFilter = ticketStatusFilter,
                todayDayKey = todayDayKey,
            )
        }
        dateFiltered.filter { ticket ->
            val requestedMatches = requestedCashierKey.isBlank() ||
                ticket.sellerId.equals(requestedCashierKey, ignoreCase = true) ||
                ticket.sellerUser.equals(requestedCashierKey, ignoreCase = true)
            val selectedMatches = selectedCashierKey.isBlank() ||
                ticket.sellerId.equals(selectedCashierKey, ignoreCase = true) ||
                ticket.sellerUser.equals(selectedCashierKey, ignoreCase = true)
            requestedMatches && selectedMatches
        }
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ticketDateKeyToPickerUtcMillis(
                if (mode == LookupMode.PAY) paymentExactDate else ticketExactDate,
            ),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            val selectedDate = pickerUtcMillisToTicketDateKey(it)
                            if (mode == LookupMode.PAY) {
                                paymentExactDate = selectedDate
                                paymentDateFilter = "date:$selectedDate"
                            } else {
                                ticketExactDate = selectedDate
                                ticketDateFilter = "date:$selectedDate"
                            }
                        }
                        showDatePicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = role,
                active = NativeBottomTab.MENU,
                onSelected = { tab -> openBottomTab(context, role, tab) },
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
                        title = mode.title,
                        subtitle = "$bancaName · ${mode.subtitle.substringBefore('.')}",
                        activeBottomTab = NativeBottomTab.MENU,
                        rightAction = ScreenChromeAction(
                            icon = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Escanear QR",
                            onClick = onScanQr,
                        ),
                    ),
                    onOpenMenu = { com.lotterynet.pro.ui.common.openShellMenu(context) },
                )
                Spacer(modifier = Modifier.height(6.dp))
                val pendingPayCount = visibleTickets.count(::isLookupBulkPayableTicket)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = showSearch || query.isNotBlank(),
                            onClick = { showSearch = !showSearch },
                            label = { Text("Buscar") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        )
                        FilterChip(
                            selected = showFilters,
                            onClick = { showFilters = !showFilters },
                            label = { Text("Filtros") },
                            leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
                        )
                    }
                    Text(
                        text = if (mode == LookupMode.PAY && pendingPayCount > 0) {
                            "$pendingPayCount pendiente(s)"
                        } else {
                            "${visibleTickets.size} ticket(s)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = visual.colors.muted,
                    )
                }
                AnimatedVisibility(visible = showSearch) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onQueryChange("")
                                    showSearch = false
                                },
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cerrar búsqueda")
                            }
                        },
                        label = { Text("Buscar ticket, serial o usuario") },
                    )
                }
                AnimatedVisibility(visible = showFilters) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                androidx.compose.foundation.layout.Box {
                                    FilterChip(
                                        selected = selectedCashierKey.isNotBlank(),
                                        onClick = { cashierMenuExpanded = true },
                                        label = {
                                            Text(
                                                if (selectedCashierKey.isBlank()) "Cajero: Todos"
                                                else "Cajero: ${actorLabelsByKey[selectedCashierKey] ?: selectedCashierKey}",
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
                                    )
                                    DropdownMenu(
                                        expanded = cashierMenuExpanded,
                                        onDismissRequest = { cashierMenuExpanded = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Todos") },
                                            onClick = {
                                                selectedCashierKey = ""
                                                cashierMenuExpanded = false
                                            },
                                        )
                                        cashierOptions.forEach { (key, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    selectedCashierKey = key
                                                    cashierMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                androidx.compose.foundation.layout.Box {
                                    FilterChip(
                                        selected = if (mode == LookupMode.PAY) paymentDateFilter != "today" else ticketDateFilter != "all",
                                        onClick = { dateMenuExpanded = true },
                                        label = {
                                            Text(
                                                if (mode == LookupMode.PAY) {
                                                    if (paymentDateFilter.startsWith("date:")) "Fecha: ${ticketLookupPaymentDateLabel(paymentExactDate)}" else "Fecha: Hoy"
                                                } else {
                                                    when {
                                                        ticketDateFilter.startsWith("date:") -> "Fecha: ${ticketLookupPaymentDateLabel(ticketExactDate)}"
                                                        ticketDateFilter == "today" -> "Fecha: Hoy"
                                                        ticketDateFilter == "yesterday" -> "Fecha: Ayer"
                                                        else -> "Fecha: Todas"
                                                    }
                                                },
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Event, contentDescription = null) },
                                    )
                                    DropdownMenu(
                                        expanded = dateMenuExpanded,
                                        onDismissRequest = { dateMenuExpanded = false },
                                    ) {
                                        listOf("today" to "Hoy", "yesterday" to "Ayer", "all" to "Todas").forEach { (id, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    if (mode == LookupMode.PAY) paymentDateFilter = id else ticketDateFilter = id
                                                    dateMenuExpanded = false
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Fecha exacta") },
                                            onClick = {
                                                dateMenuExpanded = false
                                                showDatePicker = true
                                            },
                                        )
                                    }
                                }
                            }
                            item {
                                androidx.compose.foundation.layout.Box {
                                    val statusValue = if (mode == LookupMode.PAY) paymentStatusFilter else ticketStatusFilter
                                    FilterChip(
                                        selected = statusValue != if (mode == LookupMode.PAY) "pending" else "all",
                                        onClick = { statusMenuExpanded = true },
                                        label = { Text(if (mode == LookupMode.PAY) "Estado: ${lookupStatusLabel(paymentStatusFilter)}" else "Estado: ${ticketStatusLabel(ticketStatusFilter)}") },
                                        leadingIcon = { Icon(Icons.Rounded.Paid, contentDescription = null) },
                                    )
                                    DropdownMenu(
                                        expanded = statusMenuExpanded,
                                        onDismissRequest = { statusMenuExpanded = false },
                                    ) {
                                        val options = if (mode == LookupMode.PAY) {
                                            listOf("pending" to "Pendientes", "paid" to "Pagados", "all" to "Todos")
                                        } else {
                                            listOf("all" to "Todos", "paid" to "Pagados", "unpaid" to "No pagados")
                                        }
                                        options.forEach { (id, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    if (mode == LookupMode.PAY) paymentStatusFilter = id else ticketStatusFilter = id
                                                    statusMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (mode == LookupMode.PAY) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CompactActionButton(
                                    label = if (isPayingAll) "Pagando $payAllProgress" else "Paga todo",
                                    onClick = { onPayAll(visibleTickets) },
                                    enabled = pendingPayCount > 0 && !isPayingAll,
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.Paid,
                                    tone = ActionTone.Primary,
                                )
                                CompactStatusBadge(
                                    label = if (pendingPayCount > 0) "$pendingPayCount pendientes" else "Sin pendientes",
                                    tone = if (pendingPayCount > 0) visual.colors.results else visual.colors.gain,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                TicketLookupResults(
                    modifier = Modifier.weight(1f, fill = true),
                    visibleTickets = visibleTickets,
                    mode = mode,
                    paymentDateFilter = if (mode == LookupMode.PAY) paymentDateFilter else ticketDateFilter,
                    paymentExactDate = if (mode == LookupMode.PAY) paymentExactDate else ticketExactDate,
                    layout = layout,
                    actorLabelsByKey = actorLabelsByKey,
                    onOpenTicket = onOpenTicket,
                    onDuplicateTicket = onDuplicateTicket,
                )
            }
        }
    }
}

@Composable
private fun TicketLookupResults(
    modifier: Modifier,
    visibleTickets: List<TicketRecord>,
    mode: LookupMode,
    paymentDateFilter: String,
    paymentExactDate: String,
    layout: TicketCollectionLayoutContract,
    actorLabelsByKey: Map<String, String>,
    onOpenTicket: (TicketRecord) -> Unit,
    onDuplicateTicket: (TicketRecord) -> Unit,
) {
    if (visibleTickets.isEmpty()) {
        CompactEmptyState(
            modifier = modifier,
            message = if (mode == LookupMode.PAY && paymentDateFilter.startsWith("date:")) {
                "No hay tickets ganadores para ${ticketLookupPaymentDateLabel(paymentExactDate)}."
            } else {
                mode.emptyLabel
            },
        )
    } else {
        LazyColumn(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(layout.listSpacingDp.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
        ) {
            items(visibleTickets, key = { it.id }) { ticket ->
                TicketLookupRow(
                    ticket = ticket,
                    mode = mode,
                    actorLabelsByKey = actorLabelsByKey,
                    onOpen = { onOpenTicket(ticket) },
                    onDuplicate = { onDuplicateTicket(ticket) },
                )
            }
        }
    }
}

@Composable
private fun TicketLookupRow(
    ticket: TicketRecord,
    mode: LookupMode,
    actorLabelsByKey: Map<String, String>,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveTicketCollectionLayout(visual.windowMode) }
    CompactPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = layout.rowPaddingVerticalDp.dp),
        alt = true,
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
                    text = ticket.serial ?: ticket.id,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${ticketLotteriesLabel(ticket)} · ${lookupDateTime(ticket.createdAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = "${resolveTicketActorLabel(ticket, actorLabelsByKey, fallback = "sin usuario")} · ${lookupAmountLabel(ticket, mode)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mode == LookupMode.PAY) visual.colors.warning else visual.colors.actionPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CompactStatusBadge(
                    label = lookupStatusLabel(ticket),
                    tone = ticketStatusTone(ticket.status),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    resolveTicketLookupRowActions(mode).forEach { action ->
                        when (action) {
                            TicketLookupRowAction.OPEN -> CompactActionButton(
                                label = "Abrir",
                                onClick = onOpen,
                                tone = ActionTone.Primary,
                            )
                            TicketLookupRowAction.DUPLICATE -> CompactActionButton(
                                label = "Duplicar",
                                onClick = onDuplicate,
                                tone = ActionTone.Secondary,
                                icon = Icons.Rounded.ContentCopy,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun lookupStatusLabel(ticket: TicketRecord): String {
    if (ticket.totalPrize > 0.0 && !ticket.isPaidStatus()) return "Pendiente pago"
    return when (ticket.status.lowercase(Locale.getDefault())) {
        "winner" -> "Ganador"
        "paid", "pagado", "paid_out", "payout", "cobrado", "premio_pagado" -> "Pagado"
        "voided", "invalid" -> "Anulado"
        else -> "Activo"
    }
}

internal fun lookupAmountLabel(ticket: TicketRecord, mode: LookupMode = LookupMode.SEARCH): String {
    if (mode == LookupMode.PAY) {
        return ticket.totalPrize.takeIf { it > 0.0 }?.let { "Premio ganado: ${formatTicketMoney(it)}" }
            ?: "Premio pendiente de confirmar"
    }
    val amount = ticket.totalPrize.takeIf { it > 0.0 && !ticket.isPaidStatus() } ?: ticket.total
    return formatTicketMoney(amount)
}

private fun lookupDateTime(epochMs: Long): String {
    return SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date(epochMs))
}

internal fun resolveTicketLookupRealtimeOwnerKeys(session: ActiveSession?): List<String> {
    session ?: return emptyList()
    if (session.role == UserRole.CASHIER || session.role == UserRole.SUPERVISOR) {
        val adminOwnerKey = listOf(session.adminId, session.adminUser)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .firstOrNull()
        if (adminOwnerKey != null) return listOf(adminOwnerKey)
    }
    return resolveOperationalRealtimeOwnerKeys(session)
}

private const val LOOKUP_REALTIME_DEBOUNCE_MS = 300L
