package com.lotterynet.pro.ui.tickets

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.lotterynet.pro.core.calendar.LotteryClosePolicy
import com.lotterynet.pro.core.calendar.StaticHolidayCalendarRepository
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.isPaidStatus
import com.lotterynet.pro.core.model.isPendingWinnerStatus
import com.lotterynet.pro.core.operations.buildUserActorLabelLookup
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.core.operations.resolveTicketActorLabel
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
import com.lotterynet.pro.ui.common.ScreenChromeAction
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.resolveAdaptiveScreenContract
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.normalizeTicketLookupModeForRole
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.sales.resolveSalesStartupSystemModeConfig
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject

class TicketLookupActivity : AppCompatActivity() {
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
        val usersRepository = LocalUsersRepository(this)
        usersRepository.touchSession(session)
        val lookupMode = LookupMode.from(normalizeTicketLookupModeForRole(session.role, intent?.getStringExtra(EXTRA_MODE)))
        val salesRepository = LocalSalesRepository(this)
        val allTickets = salesRepository.getAllTickets()
        val deletedTicketIds = salesRepository.getDeletedTicketIds()
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
                val tickets = remember(query, allTickets, cashiers, lookupMode, deletedTicketIds, systemModeConfig) {
                    filterLookupTicketsForSession(
                        session = session,
                        tickets = allTickets,
                        cashiers = cashiers,
                        mode = lookupMode,
                        query = query,
                        deletedTicketIds = deletedTicketIds,
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
                    query = query,
                    tickets = tickets,
                    actorLabelsByKey = buildUserActorLabelLookup(cashiers),
                    onQueryChange = { query = it.uppercase(Locale.getDefault()) },
                    onScanQr = { launchQrScanner() },
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
                            deletedTicketIds = salesRepository.getDeletedTicketIds(),
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
        title = "Cobro de ticket",
        subtitle = "Busca un ganador pendiente o pagado y abre el ticket oficial para cobro.",
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
private fun TicketLookupRoute(
    role: UserRole,
    mode: LookupMode,
    bancaName: String,
    query: String,
    tickets: List<TicketRecord>,
    actorLabelsByKey: Map<String, String>,
    onQueryChange: (String) -> Unit,
    onScanQr: () -> Unit,
    onDuplicateTicket: (TicketRecord) -> Unit,
    onOpenTicket: (TicketRecord) -> Unit,
) {
    val context = LocalContext.current
    val visual = rememberLotteryNetVisualSpec()
    val adaptive = resolveAdaptiveScreenContract(visual.windowMode)
    val layout = remember(visual.windowMode) { resolveTicketCollectionLayout(visual.windowMode) }
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
                CompactPanel(
                    alt = true,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = layout.headerPaddingVerticalDp.dp),
                ) {
                    SectionHeader(title = "Abrir ticket", meta = "${tickets.size} coincidencias")
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        label = { Text("Buscar ticket, serial o usuario") },
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (tickets.isEmpty()) {
                    CompactEmptyState(mode.emptyLabel)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(layout.listSpacingDp.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = visual.sizes.screenPaddingV),
                    ) {
                        items(tickets, key = { it.id }) { ticket ->
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
                    text = "${resolveTicketActorLabel(ticket, actorLabelsByKey, fallback = "sin usuario")} · ${lookupAmountLabel(ticket)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.actionPrimary,
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

internal fun lookupAmountLabel(ticket: TicketRecord): String {
    val amount = ticket.totalPrize.takeIf { it > 0.0 && !ticket.isPaidStatus() } ?: ticket.total
    return formatTicketMoney(amount)
}

private fun lookupDateTime(epochMs: Long): String {
    return SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date(epochMs))
}
