package com.lotterynet.pro.ui.sportsbook

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import coil3.compose.AsyncImage
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.format.formatWholeMoney
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SportsbookBoardGame
import com.lotterynet.pro.core.model.SportsbookBoardSnapshot
import com.lotterynet.pro.core.model.SportsbookMarket
import com.lotterynet.pro.core.model.SportsbookMarketKey
import com.lotterynet.pro.core.model.SportsbookOdd
import com.lotterynet.pro.core.model.SportsbookSelection
import com.lotterynet.pro.core.model.SportsbookTicketDraft
import com.lotterynet.pro.core.model.SportsbookTicketLegRecord
import com.lotterynet.pro.core.model.SportsbookTicketRecord
import com.lotterynet.pro.core.model.SportsbookTicketSaleResult
import com.lotterynet.pro.core.model.SportsbookTicketStatus
import com.lotterynet.pro.core.model.SportsbookTicketSummary
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.sportsbook.SportsbookBoardRemoteStore
import com.lotterynet.pro.core.sportsbook.SportsbookTicketSnapshot
import com.lotterynet.pro.core.sportsbook.SportsbookTicketRemoteStore
import com.lotterynet.pro.core.sportsbook.selectionCanBeSold
import com.lotterynet.pro.core.printing.BluetoothThermalPrinter
import com.lotterynet.pro.core.printing.IntegratedThermalPrinter
import com.lotterynet.pro.core.printing.ThermalTicketRenderer
import com.lotterynet.pro.core.printing.TicketPrintMark
import com.lotterynet.pro.core.storage.LocalMasterConfigRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalThermalPrinterRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.MasterSportsbookSettings
import com.lotterynet.pro.core.storage.decodeMasterSportsbookSettings
import com.lotterynet.pro.core.storage.encodeMasterSportsbookSettings
import com.lotterynet.pro.core.storage.sportsbookRemoteKey
import com.lotterynet.pro.core.storage.toFeatureConfig
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.CurrentScopeDropdownCard
import com.lotterynet.pro.ui.common.OperationalModalSheet
import com.lotterynet.pro.ui.common.SearchableOptionSheet
import com.lotterynet.pro.ui.common.SearchableSheetOption
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.concurrent.thread

class SportsbookActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, session?.role, NativeDestination.SPORTSBOOK)) {
            return
        }
        val activeSession = session ?: return
        val configRepository = LocalMasterConfigRepository(this)
        val usersRepository = LocalUsersRepository(this)
        val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        val remoteStore = SupabaseMasterConfigRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
        )
        val boardStore = SportsbookBoardRemoteStore()
        val ticketStore = SportsbookTicketRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
        )
        setContent {
            LotteryNetComposeTheme {
                var settings by remember { mutableStateOf(configRepository.getSportsbookSettings()) }
                var syncStatus by remember { mutableStateOf("Leyendo control Master...") }
                var boardSnapshot by remember { mutableStateOf(SportsbookBoardSnapshot()) }
                var boardStatus by remember { mutableStateOf("Tablero pendiente de sincronizar.") }
                var boardLoading by remember { mutableStateOf(false) }
                var ticketSnapshot by remember { mutableStateOf(SportsbookTicketSnapshot()) }
                var ticketStatus by remember { mutableStateOf("Tickets deportivos pendientes de leer.") }
                var ticketLoading by remember { mutableStateOf(false) }
                var refreshNonce by remember { mutableStateOf(0) }
                val userAccounts = remember { usersRepository.getAdmins() + usersRepository.getSupervisors() + usersRepository.getCashiers() }
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    val remoteSettings = withContext(Dispatchers.IO) {
                        runCatching {
                            remoteStore.fetchValue(sportsbookRemoteKey())
                                ?.toString()
                                ?.let(::decodeMasterSportsbookSettings)
                        }.getOrNull()
                    }
                    if (remoteSettings != null) {
                        settings = configRepository.saveSportsbookSettings(remoteSettings)
                        syncStatus = "Control Master actualizado."
                    } else {
                        syncStatus = "Usando control guardado en este equipo."
                    }
                }
                LaunchedEffect(
                    settings.enabled,
                    settings.adminEnabled,
                    settings.supervisorEnabled,
                    settings.cashierEnabled,
                    settings.allowedActorKeys,
                    settings.cashierAdminKeys,
                    refreshNonce,
                ) {
                    val canLoadBoard = canLoadSportsbookBoard(
                        role = activeSession.role,
                        settings = settings,
                        actorKey = activeSession.userId.ifBlank { activeSession.username },
                        adminKey = activeSession.adminId ?: activeSession.adminUser,
                    )
                    if (!canLoadBoard) {
                        boardLoading = false
                        ticketLoading = false
                        return@LaunchedEffect
                    }
                    boardLoading = true
                    ticketLoading = true
                    boardStatus = "Buscando juegos cacheados..."
                    val nextBoard = withContext(Dispatchers.IO) {
                        runCatching {
                            boardStore.fetchBoard(bearerToken = sessionTokenProvider.freshAccessToken())
                        }.getOrNull()
                    }
                    if (nextBoard != null) {
                        boardSnapshot = nextBoard
                        boardStatus = if (nextBoard.games.isEmpty()) {
                            "No hay juegos deportivos cacheados todavia."
                        } else {
                            "${nextBoard.games.size} juego(s), ${nextBoard.openGames} abierto(s)."
                        }
                    } else {
                        boardStatus = "No se pudo leer el tablero deportivo."
                    }
                    boardLoading = false
                    val nextTickets = withContext(Dispatchers.IO) {
                        runCatching { ticketStore.fetchTickets(activeSession) }.getOrNull()
                    }
                    if (nextTickets != null) {
                        ticketSnapshot = nextTickets
                        ticketStatus = "${nextTickets.tickets.size} ticket(s) deportivos leidos."
                    } else {
                        ticketStatus = "No se pudo leer tickets deportivos."
                    }
                    ticketLoading = false
                }
                SportsbookRoute(
                    session = activeSession,
                    settings = settings,
                    userAccounts = userAccounts,
                    syncStatus = syncStatus,
                    boardSnapshot = boardSnapshot,
                    boardStatus = boardStatus,
                    boardLoading = boardLoading,
                    ticketSnapshot = ticketSnapshot,
                    ticketStatus = ticketStatus,
                    ticketLoading = ticketLoading,
                    onRetryBoard = { refreshNonce += 1 },
                    onCreateTicket = { draft ->
                        val sale = withContext(Dispatchers.IO) {
                            ticketStore.createTicket(activeSession, draft)
                        }
                        val localTicket = buildSportsbookTicketRecordFromSale(activeSession, sale, draft.selections)
                        val nextTickets = withContext(Dispatchers.IO) {
                            runCatching { ticketStore.fetchTickets(activeSession) }.getOrNull()
                        }
                        if (nextTickets != null) {
                            ticketSnapshot = mergeSportsbookTicketSnapshot(nextTickets, localTicket)
                            ticketStatus = "${ticketSnapshot.tickets.size} ticket(s) deportivos visibles."
                        } else {
                            ticketSnapshot = mergeSportsbookTicketSnapshot(ticketSnapshot, localTicket)
                            ticketStatus = "Venta guardada; lista deportiva actualizada localmente."
                        }
                        sale
                    },
                    onShareTicket = { ticket, whatsappOnly ->
                        val bitmap = NativeBitmapExport.renderSportsbookTicketBitmap(
                            ticket = ticket,
                            bancaName = ticket.bancaName.ifBlank { activeSession.banca ?: "Deportes" },
                        )
                        NativeBitmapExport.shareBitmap(
                            context = this@SportsbookActivity,
                            bitmap = bitmap,
                            fileName = "deporte-${ticket.ticketCode}.png",
                            title = "Ticket deportivo ${ticket.ticketCode}",
                            text = "",
                            whatsappOnly = whatsappOnly,
                        )
                    },
                    onPrintThermalTicket = { ticket, printMark ->
                        thread(name = "sportsbook-thermal-print") {
                            val prefs = LocalThermalPrinterRepository(this@SportsbookActivity).getPrefs()
                            val text = ThermalTicketRenderer().renderSportsbookTicket(
                                ticket = ticket,
                                bancaName = ticket.bancaName.ifBlank { activeSession.banca ?: "Deportes" },
                                prefs = prefs,
                                printMark = printMark,
                            )
                            val targetIntegrated = IntegratedThermalPrinter.isAvailable(this@SportsbookActivity)
                            val result = if (targetIntegrated) {
                                IntegratedThermalPrinter.printText(this@SportsbookActivity, text)
                            } else {
                                BluetoothThermalPrinter.printText(
                                    context = this@SportsbookActivity,
                                    content = text,
                                    prefs = prefs,
                                )
                            }
                            runOnUiThread {
                                Toast.makeText(this@SportsbookActivity, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onPayTicket = { ticket ->
                        scope.launch {
                            ticketStatus = "Pagando cobro deportivo ${ticket.ticketCode}..."
                            val paid = withContext(Dispatchers.IO) {
                                runCatching { ticketStore.payTicket(activeSession, ticket.id) }
                            }
                            paid.onSuccess {
                                val nextTickets = withContext(Dispatchers.IO) {
                                    runCatching { ticketStore.fetchTickets(activeSession) }.getOrNull()
                                }
                                if (nextTickets != null) {
                                    ticketSnapshot = nextTickets
                                    ticketStatus = "Cobro deportivo pagado: ${it.ticketCode}."
                                } else {
                                    ticketStatus = "Cobro pagado. No se pudo refrescar la lista."
                                }
                                Toast.makeText(this@SportsbookActivity, "Cobro deportivo pagado.", Toast.LENGTH_SHORT).show()
                            }.onFailure { error ->
                                ticketStatus = error.message ?: "No se pudo pagar el cobro deportivo."
                                Toast.makeText(this@SportsbookActivity, ticketStatus, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onSaveSettings = { nextSettings ->
                        val normalized = nextSettings.copy(
                            updatedAtEpochMs = System.currentTimeMillis(),
                            updatedBy = activeSession.username,
                        )
                        settings = configRepository.saveSportsbookSettings(normalized)
                        syncStatus = "Guardando Deportes..."
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching {
                                    remoteStore.upsertJsonValue(
                                        sportsbookRemoteKey(),
                                        encodeMasterSportsbookSettings(normalized),
                                    )
                                }.isSuccess
                            }
                            syncStatus = if (ok) "Deportes guardado en servidor." else "Guardado local. Servidor no respondio."
                            Toast.makeText(
                                this@SportsbookActivity,
                                syncStatus,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

@Composable
private fun SportsbookRoute(
    session: ActiveSession,
    settings: MasterSportsbookSettings,
    userAccounts: List<UserAccount>,
    syncStatus: String,
    boardSnapshot: SportsbookBoardSnapshot,
    boardStatus: String,
    boardLoading: Boolean,
    ticketSnapshot: SportsbookTicketSnapshot,
    ticketStatus: String,
    ticketLoading: Boolean,
    onRetryBoard: () -> Unit,
    onCreateTicket: suspend (SportsbookTicketDraft) -> SportsbookTicketSaleResult,
    onShareTicket: (SportsbookTicketRecord, Boolean) -> Unit,
    onPrintThermalTicket: (SportsbookTicketRecord, TicketPrintMark) -> Unit,
    onPayTicket: (SportsbookTicketRecord) -> Unit,
    onSaveSettings: (MasterSportsbookSettings) -> Unit,
    onBack: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var selectedTab by remember(session.role) { mutableStateOf(resolveSportsbookInitialTab(session.role)) }
    val featureConfig = settings.toFeatureConfig()
    val masterCanEdit = session.role == UserRole.MASTER
    val tabs = remember(session.role) { sportsbookTabsForRole(session.role) }
    val userCanOpen = masterCanEdit || featureConfig.canOpen(
        role = session.role,
        actorKey = session.userId.ifBlank { session.username },
        adminKey = session.adminId ?: session.adminUser,
    )
    var selectedLeague by remember { mutableStateOf(SportsbookBoardFilterOption.ALL.id) }
    var selectedSport by remember { mutableStateOf(SportsbookBoardFilterOption.ALL.id) }
    var selectedDate by remember { mutableStateOf(SportsbookBoardFilterOption.ALL.id) }
    var selectedMarket by remember { mutableStateOf(SportsbookBoardFilterOption.ALL.id) }
    var selectedStatus by remember { mutableStateOf(SportsbookBoardFilterOption.OPEN.id) }
    var selectedGame by remember { mutableStateOf<SportsbookBoardGame?>(null) }
    var selections by remember { mutableStateOf<List<SportsbookSelection>>(emptyList()) }
    var stakeText by remember { mutableStateOf("") }
    var saleStatus by remember { mutableStateOf<String?>(null) }
    var lastSale by remember { mutableStateOf<SportsbookTicketSaleResult?>(null) }
    var lastSaleSelections by remember { mutableStateOf<List<SportsbookSelection>>(emptyList()) }
    var selling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    selectedGame?.let { game ->
        OperationalModalSheet(
            title = "Mercados deportivos",
            subtitle = "${game.event.awayTeam} @ ${game.event.homeTeam}",
            onDismiss = { selectedGame = null },
        ) {
            SportsbookGameSheet(
                game = game,
                selectedOddsIds = selections.map { it.oddsId }.toSet(),
                onOddSelected = { selection ->
                    selections = toggleSportsbookSelection(selections, selection)
                    saleStatus = "Seleccion agregada al ticket."
                    selectedGame = null
                },
            )
        }
    }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(visual.colors.background),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            AppTopBar(
                spec = ScreenChromeSpec(
                    title = "Deportes",
                    subtitle = "Modulo separado de loteria",
                    showBottomNav = false,
                ),
                onOpenMenu = onBack,
                applyStatusBarInsets = true,
            )
        },
        containerColor = visual.colors.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SportsbookLockedHeader(
                    enabled = featureConfig.enabled,
                    canOpen = userCanOpen,
                    masterView = masterCanEdit,
                    syncStatus = syncStatus,
                )
            }
            if (userCanOpen) {
                item {
                    SportsbookTabStrip(
                        tabs = tabs,
                        selected = selectedTab,
                        onSelected = { selectedTab = it },
                    )
                }
                item {
                    when (selectedTab) {
                        "ticket" -> SportsbookTicketPreview(
                            session = session,
                            selections = selections,
                            stakeText = stakeText,
                            selling = selling,
                            saleStatus = saleStatus,
                            lastSale = lastSale,
                            lastSaleTicket = lastSale?.let { sale ->
                                ticketSnapshot.tickets.firstOrNull { it.ticketCode == sale.ticketCode }
                                    ?: buildSportsbookTicketRecordFromSale(session, sale, lastSaleSelections)
                            },
                            onStakeChange = { stakeText = it.filter { char -> char.isDigit() || char == '.' }.take(8) },
                            onRemoveSelection = { oddsId ->
                                selections = selections.filterNot { it.oddsId == oddsId }
                                saleStatus = null
                            },
                            onClear = {
                                selections = emptyList()
                                stakeText = ""
                                saleStatus = null
                                lastSale = null
                                lastSaleSelections = emptyList()
                            },
                            onSell = {
                                val stake = stakeText.toDoubleOrNull() ?: 0.0
                                val draftSelections = selections
                                val draft = SportsbookTicketDraft(selections = draftSelections, stake = stake)
                                selling = true
                                saleStatus = "Validando en servidor..."
                                scope.launch {
                                    val result = runCatching { onCreateTicket(draft) }
                                    selling = false
                                    result.onSuccess { sale ->
                                        lastSale = sale
                                        lastSaleSelections = draftSelections
                                        saleStatus = "Ticket vendido: ${sale.ticketCode}"
                                        selections = emptyList()
                                        stakeText = ""
                                    }.onFailure { error ->
                                        saleStatus = error.message ?: "No se pudo vender el ticket deportivo."
                                    }
                                }
                            },
                            onShareLastSaleTicket = { ticket -> onShareTicket(ticket, true) },
                            onPrintLastSaleTicket = onPrintThermalTicket,
                        )
                        "cobros" -> SportsbookCollectionPreview(
                            tickets = ticketSnapshot.tickets,
                            ticketStatus = ticketStatus,
                            ticketLoading = ticketLoading,
                            onRetry = onRetryBoard,
                            onShareTicket = onShareTicket,
                            onPrintThermalTicket = onPrintThermalTicket,
                            onPayTicket = onPayTicket,
                        )
                        "finanza" -> SportsbookFinancePreview(ticketSnapshot.summary, ticketStatus)
                        "reportes" -> SportsbookReportPreview(
                            tickets = ticketSnapshot.tickets,
                            summary = ticketSnapshot.summary,
                            onShareTicket = onShareTicket,
                            onPrintThermalTicket = onPrintThermalTicket,
                            onPayTicket = onPayTicket,
                        )
                        "control" -> SportsbookBusinessControlPreview(session.role)
                        "config" -> SportsbookConfigPreview(
                            settings = settings,
                            userAccounts = userAccounts,
                            canEdit = masterCanEdit,
                            onSettingsChange = onSaveSettings,
                        )
                        else -> SportsbookBoardPreview(
                            boardSnapshot = boardSnapshot,
                            boardStatus = boardStatus,
                            boardLoading = boardLoading,
                            onRetry = onRetryBoard,
                            selectedLeague = selectedLeague,
                            onLeagueSelected = { selectedLeague = it },
                            selectedSport = selectedSport,
                            onSportSelected = { selectedSport = it },
                            selectedDate = selectedDate,
                            onDateSelected = { selectedDate = it },
                            selectedMarket = selectedMarket,
                            onMarketSelected = { selectedMarket = it },
                            selectedStatus = selectedStatus,
                            onStatusSelected = { selectedStatus = it },
                            onGameSelected = { selectedGame = it },
                            selectedOddsIds = selections.map { it.oddsId }.toSet(),
                            selections = selections,
                            onOpenTicket = { selectedTab = "ticket" },
                            onOddSelected = { selection ->
                                selections = toggleSportsbookSelection(selections, selection)
                                saleStatus = "Seleccion agregada al ticket."
                            },
                        )
                    }
                }
            } else {
                item {
                    PreviewPanel(
                        title = "Acceso cerrado",
                        rows = listOf(
                            PreviewRow("Estado", "Master debe activar Deportes para este rol.", "Oculto"),
                            PreviewRow("Venta", "No se puede vender ni consultar juegos deportivos.", "\$0"),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SportsbookLockedHeader(
    enabled: Boolean,
    canOpen: Boolean,
    masterView: Boolean,
    syncStatus: String,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        alt = true,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (enabled) visual.colors.financeSurface else visual.colors.warningSurface,
                border = BorderStroke(1.dp, if (enabled) visual.colors.gain else visual.colors.warning),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (enabled) Icons.Rounded.SportsSoccer else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (enabled) visual.colors.gain else visual.colors.warning,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = when {
                        masterView -> "Control Master de Deportes"
                        enabled && canOpen -> "Deportes activo"
                        enabled -> "Deportes sin permiso"
                        else -> "Deportes apagado"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = syncStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactStatusBadge(
                label = if (masterView) "Sistema" else if (enabled && canOpen) "Activo" else "Oculto",
                tone = if (masterView || (enabled && canOpen)) visual.colors.gain else visual.colors.warning,
            )
        }
    }
}

@Composable
private fun SportsbookTabStrip(
    tabs: List<SportsbookTab>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    val selectedTab = tabs.firstOrNull { it.id == selected } ?: tabs.first()
    CurrentScopeDropdownCard(
        title = "Vista deportiva",
        value = selectedTab.label,
        selectedId = selectedTab.id,
        options = tabs.map { it.id to it.label },
        onSelected = onSelected,
        subtitle = when (selectedTab.id) {
            "juegos" -> "Cartelera y cuotas disponibles"
            "ticket" -> "Selecciones y monto de la apuesta"
            "cobros" -> "Tickets ganadores por cobrar"
            "finanza" -> "Resumen financiero deportivo"
            "reportes" -> "Historial y resultados"
            "control" -> "Controles administrativos"
            "config" -> "Permisos y configuración"
            else -> "Contenido deportivo"
        },
        actionLabel = "Cambiar",
        tone = sportsbookTabTone(selectedTab.id, selected = true),
    )
}

private fun sportsbookTabTone(tabId: String, selected: Boolean): ActionTone = when {
    selected && tabId in setOf("juegos", "ticket") -> ActionTone.IntenseBlue
    selected && tabId in setOf("cobros", "finanza", "reportes") -> ActionTone.Success
    selected && tabId in setOf("control", "config") -> ActionTone.Purple
    selected -> ActionTone.Primary
    else -> ActionTone.Secondary
}

@Composable
private fun SportsbookBoardPreview(
    boardSnapshot: SportsbookBoardSnapshot,
    boardStatus: String,
    boardLoading: Boolean,
    onRetry: () -> Unit,
    selectedLeague: String,
    onLeagueSelected: (String) -> Unit,
    selectedSport: String,
    onSportSelected: (String) -> Unit,
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    selectedMarket: String,
    onMarketSelected: (String) -> Unit,
    selectedStatus: String,
    onStatusSelected: (String) -> Unit,
    onGameSelected: (SportsbookBoardGame) -> Unit,
    selectedOddsIds: Set<String>,
    selections: List<SportsbookSelection>,
    onOpenTicket: () -> Unit,
    onOddSelected: (SportsbookSelection) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val leagueOptions = remember(boardSnapshot.games) { buildSportsbookLeagueFilterOptions(boardSnapshot.games) }
    val sportOptions = remember(boardSnapshot.games) { buildSportsbookSportFilterOptions(boardSnapshot.games) }
    val dateOptions = remember { sportsbookDateFilterOptions() }
    val marketOptions = remember(boardSnapshot.games) { buildSportsbookMarketFilterOptions(boardSnapshot.games) }
    val statusOptions = remember { sportsbookStatusFilterOptions() }
    val safeSport = sportOptions.firstOrNull { it.id == selectedSport }?.id ?: SportsbookBoardFilterOption.ALL.id
    val safeDate = dateOptions.firstOrNull { it.id == selectedDate }?.id ?: SportsbookBoardFilterOption.ALL.id
    val safeMarket = marketOptions.firstOrNull { it.id == selectedMarket }?.id ?: SportsbookBoardFilterOption.ALL.id
    val safeLeague = leagueOptions.firstOrNull { it.id == selectedLeague }?.id ?: SportsbookBoardFilterOption.ALL.id
    val safeStatus = statusOptions.firstOrNull { it.id == selectedStatus }?.id ?: SportsbookBoardFilterOption.OPEN.id
    val games = remember(boardSnapshot.games, safeSport, safeDate, safeMarket, safeLeague, safeStatus) {
        filterSportsbookBoardGames(boardSnapshot.games, safeLeague, safeStatus, safeSport, safeDate, safeMarket)
    }
    var showFilters by remember { mutableStateOf(false) }
    if (showFilters) {
        OperationalModalSheet(
            title = "Filtros de cartelera",
            subtitle = "Deporte, fecha, mercado, liga y estado.",
            onDismiss = { showFilters = false },
            primaryActionLabel = "Aplicar",
            onPrimaryAction = { showFilters = false },
            contentScrollable = false,
        ) {
            SportsbookBoardFilterSheet(
                leagueOptions = leagueOptions,
                selectedLeague = safeLeague,
                onLeagueSelected = onLeagueSelected,
                sportOptions = sportOptions,
                selectedSport = selectedSport,
                onSportSelected = onSportSelected,
                dateOptions = dateOptions,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected,
                marketOptions = marketOptions,
                selectedMarket = selectedMarket,
                onMarketSelected = onMarketSelected,
                statusOptions = statusOptions,
                selectedStatus = safeStatus,
                onStatusSelected = onStatusSelected,
            )
        }
    }
    CompactPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(visual.colors.actionPrimarySurface, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.FilterList, contentDescription = null, tint = visual.colors.actionPrimary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Cartelera deportiva",
                    style = MaterialTheme.typography.titleMedium,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "${selectedSportsbookFilterLabel(sportOptions, safeSport)} · ${selectedSportsbookFilterLabel(leagueOptions, safeLeague)} · ${selectedSportsbookFilterLabel(statusOptions, safeStatus)} · $boardStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (boardLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                CompactStatusBadge(label = "${games.size}", tone = visual.colors.actionPrimary)
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            item {
                FilterChip(
                    selected = false,
                    onClick = { showFilters = true },
                    label = { Text("Filtros") },
                )
            }
            item {
                SportsbookActiveFilterChip(
                    label = "Deporte",
                    value = selectedSportsbookFilterLabel(sportOptions, safeSport),
                    active = safeSport != SportsbookBoardFilterOption.ALL.id,
                    onClick = { showFilters = true },
                )
            }
            item {
                SportsbookActiveFilterChip(
                    label = "Liga",
                    value = selectedSportsbookFilterLabel(leagueOptions, safeLeague),
                    active = safeLeague != SportsbookBoardFilterOption.ALL.id,
                    onClick = { showFilters = true },
                )
            }
            item {
                SportsbookActiveFilterChip(
                    label = "Fecha",
                    value = selectedSportsbookFilterLabel(dateOptions, safeDate),
                    active = safeDate != SportsbookBoardFilterOption.ALL.id,
                    onClick = { showFilters = true },
                )
            }
            item {
                SportsbookActiveFilterChip(
                    label = "Mercado",
                    value = selectedSportsbookFilterLabel(marketOptions, safeMarket),
                    active = safeMarket != SportsbookBoardFilterOption.ALL.id,
                    onClick = { showFilters = true },
                )
            }
        }
        if (games.isEmpty()) {
            PreviewPanel(
                title = "Sin juegos",
                rows = listOf(
                    PreviewRow("Filtro", "No hay juegos para esta combinacion.", "0"),
                    PreviewRow("Cron", "Render/Supabase debe sincronizar cuotas cacheadas.", "Cache"),
                ),
                footer = "Cuando lleguen juegos, toca una fila para abrir mercados en modal sheet.",
            )
            if (!boardLoading && boardStatus.contains("No se pudo", ignoreCase = true)) {
                CompactActionButton(
                    label = "Reintentar tablero",
                    onClick = onRetry,
                    icon = Icons.Rounded.QueryStats,
                    modifier = Modifier.fillMaxWidth(),
                    tone = ActionTone.Secondary,
                )
            }
        } else {
            games.take(12).forEachIndexed { index, game ->
                if (index > 0) HorizontalDivider(color = visual.colors.border)
                SportsbookGameRow(
                    game = game,
                    selectedOddsIds = selectedOddsIds,
                    onOddSelected = onOddSelected,
                    onOpenMarkets = { onGameSelected(game) },
                )
            }
            if (games.size > 12) {
                Text(
                    text = "Mostrando 12 de ${games.size}. Usa filtros para reducir la cartelera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (selections.isNotEmpty()) {
            SportsbookCompactBetSlip(
                selections = selections,
                onOpenTicket = onOpenTicket,
            )
        }
    }
}

@Composable
private fun SportsbookActiveFilterChip(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = {
            Text(
                text = if (active) "$label: $value" else label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun SportsbookCompactBetSlip(
    selections: List<SportsbookSelection>,
    onOpenTicket: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val combinedOdds = com.lotterynet.pro.core.model.calculateSportsbookCombinedDecimalOdds(selections)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = visual.colors.actionPrimarySurface,
        border = BorderStroke(1.dp, visual.colors.actionPrimary),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Boleto en preparación",
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "${selections.size} selección(es) · cuota ${"%.2f".format(Locale.US, combinedOdds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactActionButton(
                label = "Revisar",
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                onClick = onOpenTicket,
                tone = ActionTone.IntenseBlue,
            )
        }
    }
}

@Composable
private fun SportsbookBoardFilterSheet(
    leagueOptions: List<SportsbookBoardFilterOption>,
    selectedLeague: String,
    onLeagueSelected: (String) -> Unit,
    sportOptions: List<SportsbookBoardFilterOption>,
    selectedSport: String,
    onSportSelected: (String) -> Unit,
    dateOptions: List<SportsbookBoardFilterOption>,
    selectedDate: String,
    onDateSelected: (String) -> Unit,
    marketOptions: List<SportsbookBoardFilterOption>,
    selectedMarket: String,
    onMarketSelected: (String) -> Unit,
    statusOptions: List<SportsbookBoardFilterOption>,
    selectedStatus: String,
    onStatusSelected: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "La app lee la cartelera cacheada del servidor. No llama Odds ni TheSportsDB desde Android.",
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.muted,
            fontWeight = FontWeight.Bold,
        )
        CompactPanel(alt = true) {
            SportsbookFilterSection(
                title = "Fecha",
                options = dateOptions,
                selectedId = selectedDate,
                onSelected = onDateSelected,
            )
        }
        CompactPanel(alt = true) {
            SportsbookFilterSection(
                title = "Mercado",
                options = marketOptions,
                selectedId = selectedMarket,
                onSelected = onMarketSelected,
            )
        }
        CompactPanel(alt = true) {
            SportsbookFilterSection(
                title = "Estado",
                options = statusOptions,
                selectedId = selectedStatus,
                onSelected = onStatusSelected,
            )
        }
        CompactPanel {
            SportsbookFilterSection(
                title = "Deporte",
                options = sportOptions,
                selectedId = selectedSport,
                onSelected = onSportSelected,
            )
        }
        CompactPanel {
            SportsbookFilterSection(
                title = "Liga",
                options = leagueOptions,
                selectedId = selectedLeague,
                onSelected = onLeagueSelected,
            )
        }
    }
}

@Composable
private fun SportsbookFilterSection(
    title: String,
    options: List<SportsbookBoardFilterOption>,
    selectedId: String,
    onSelected: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = visual.colors.ink,
            fontWeight = FontWeight.Black,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(options, key = { it.id }) { option ->
                SportsbookFilterOptionRow(
                    option = option,
                    selected = option.id == selectedId,
                    onClick = { onSelected(option.id) },
                )
            }
        }
    }
}

@Composable
private fun SportsbookFilterOptionRow(
    option: SportsbookBoardFilterOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) visual.colors.actionPrimarySurface else visual.colors.financeSurface,
        border = BorderStroke(1.dp, if (selected) visual.colors.actionPrimary else visual.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                color = visual.colors.ink,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                CompactStatusBadge(label = "Activo", tone = visual.colors.actionPrimary)
            }
        }
    }
}

@Composable
private fun SportsbookGameRow(
    game: SportsbookBoardGame,
    selectedOddsIds: Set<String>,
    onOddSelected: (SportsbookSelection) -> Unit,
    onOpenMarkets: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val event = game.event
    val primaryMarket = sportsbookPrimaryMarketPreview(game)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onOpenMarkets),
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SportsbookTeamPairLogos(game = game)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "${event.awayTeam} @ ${event.homeTeam}",
                        style = MaterialTheme.typography.labelLarge,
                        color = visual.colors.ink,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactStatusBadge(label = "${game.markets.size} mercados", tone = visual.colors.neutral)
                        CompactStatusBadge(label = "${game.odds.size} cuotas", tone = visual.colors.gain)
                        if (event.homeTeamLogoUrl.isNullOrBlank() || event.awayTeamLogoUrl.isNullOrBlank()) {
                            CompactStatusBadge(label = "logos cache", tone = visual.colors.warning)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactStatusBadge(
                        label = sportsbookGameStatusLabel(game),
                        tone = sportsbookGameStatusTone(game, visual),
                    )
                    CompactActionButton(
                        label = "Mercados",
                        icon = Icons.Rounded.SportsSoccer,
                        onClick = onOpenMarkets,
                        enabled = game.isOpen,
                        tone = if (game.isOpen) ActionTone.IntenseBlue else ActionTone.Secondary,
                    )
                }
            }
            if (primaryMarket == null) {
                Text(
                    text = "Sin cuotas abiertas. Abre mercados para ver detalle cacheado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                val (market, odds) = primaryMarket
                Text(
                    text = market.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    odds.take(3).forEach { odd ->
                        SportsbookInlineOddButton(
                            odd = odd,
                            selected = odd.id in selectedOddsIds,
                            onClick = { onOddSelected(buildSportsbookSelection(game, market, odd)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat((3 - odds.take(3).size).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsbookInlineOddButton(
    odd: SportsbookOdd,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
        Surface(
            modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                role = Role.Button
                this.selected = selected
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) visual.colors.actionPrimarySurface else visual.colors.financeSurface,
        border = BorderStroke(1.dp, if (selected) visual.colors.actionPrimary else visual.colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = odd.selectionLabel,
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.ink,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "%.2f".format(Locale.US, odd.decimalOdds),
                style = MaterialTheme.typography.labelLarge,
                color = visual.colors.gain,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SportsbookTeamPairLogos(game: SportsbookBoardGame) {
    Box(modifier = Modifier.size(48.dp)) {
        SportsbookTeamLogo(
            teamName = game.event.awayTeam,
            logoUrl = game.event.awayTeamLogoUrl,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(32.dp),
        )
        SportsbookTeamLogo(
            teamName = game.event.homeTeam,
            logoUrl = game.event.homeTeamLogoUrl,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(32.dp),
        )
    }
}

@Composable
private fun SportsbookTeamLogo(
    teamName: String,
    logoUrl: String?,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(visual.colors.actionPrimarySurface),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = teamInitials(teamName),
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.actionPrimary,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = teamName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun SportsbookGameSheet(
    game: SportsbookBoardGame,
    selectedOddsIds: Set<String>,
    onOddSelected: (SportsbookSelection) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val event = game.event
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SportsbookTeamPairLogos(game = game)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${event.awayTeam} @ ${event.homeTeam}",
                    style = MaterialTheme.typography.titleMedium,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${event.sportTitle} · ${event.leagueTitle.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactStatusBadge(
                label = sportsbookGameStatusLabel(game),
                tone = sportsbookGameStatusTone(game, visual),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactStatusBadge(label = "${game.markets.size} mercados", tone = visual.colors.neutral)
            CompactStatusBadge(label = "${game.odds.size} cuotas", tone = visual.colors.gain)
        }
        HorizontalDivider(color = visual.colors.border)
        Text(
            text = "Mercados",
            style = MaterialTheme.typography.labelLarge,
            color = visual.colors.ink,
            fontWeight = FontWeight.Black,
        )
        if (game.odds.isEmpty()) {
            Text(
                text = "Este juego todavia no tiene cuotas cacheadas.",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontWeight = FontWeight.Bold,
            )
        } else {
            game.markets.take(4).forEach { market ->
                SportsbookMarketBlock(
                    market = market,
                    odds = sportsbookOddsForMarket(game, market).take(4),
                    selectedOddsIds = selectedOddsIds,
                    onOddSelected = { odd -> onOddSelected(buildSportsbookSelection(game, market, odd)) },
                )
            }
        }
        PreviewPanel(
            title = "Validacion al vender",
            rows = listOf(
                PreviewRow("Cuota congelada", "El servidor guarda la cuota exacta del ticket.", "OK"),
                PreviewRow("Limites", "Valida monto, mercado y permiso de usuario.", "OK"),
                PreviewRow("Finanza", "Cae en Deportes, no en Loteria.", "Sep."),
            ),
            footer = "Toca una cuota para mandarla al ticket. La venta se valida en el servidor antes de guardar.",
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SportsbookMarketBlock(
    market: SportsbookMarket,
    odds: List<SportsbookOdd>,
    selectedOddsIds: Set<String>,
    onOddSelected: (SportsbookOdd) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = market.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CompactStatusBadge(
                    label = market.status.ifBlank { "open" },
                    tone = if (market.status == "open") visual.colors.gain else visual.colors.warning,
                )
            }
            if (odds.isEmpty()) {
                Text(
                    text = "Sin cuotas disponibles para este mercado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                odds.chunked(2).forEach { rowOdds ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowOdds.forEach { odd ->
                            SportsbookOddChip(
                                odd = odd,
                                selected = odd.id in selectedOddsIds,
                                onClick = { onOddSelected(odd) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowOdds.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsbookOddChip(
    odd: SportsbookOdd,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) visual.colors.actionPrimarySurface else visual.colors.financeSurface,
        border = BorderStroke(1.dp, if (selected) visual.colors.actionPrimary else visual.colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = odd.selectionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = visual.colors.ink,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${odd.decimalOdds}",
                style = MaterialTheme.typography.titleMedium,
                color = visual.colors.gain,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = odd.status,
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.muted,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SportsbookTicketPreview(
    session: ActiveSession,
    selections: List<SportsbookSelection>,
    stakeText: String,
    selling: Boolean,
    saleStatus: String?,
    lastSale: SportsbookTicketSaleResult?,
    lastSaleTicket: SportsbookTicketRecord?,
    onStakeChange: (String) -> Unit,
    onRemoveSelection: (String) -> Unit,
    onClear: () -> Unit,
    onSell: () -> Unit,
    onShareLastSaleTicket: (SportsbookTicketRecord) -> Unit,
    onPrintLastSaleTicket: (SportsbookTicketRecord, TicketPrintMark) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val stake = stakeText.toDoubleOrNull() ?: 0.0
    val combinedOdds = com.lotterynet.pro.core.model.calculateSportsbookCombinedDecimalOdds(selections)
    val potentialPayout = com.lotterynet.pro.core.model.calculateSportsbookPotentialPayout(stake, combinedOdds)
    val canSell = !selling &&
        session.role in setOf(UserRole.ADMIN, UserRole.CASHIER) &&
        stake > 0.0 &&
        selections.isNotEmpty() &&
        selections.all(::selectionCanBeSold)
    CompactPanel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Ticket deportivo",
                    style = MaterialTheme.typography.titleMedium,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = if (selections.isEmpty()) "Selecciona una cuota desde Juegos." else "${selections.size} seleccion(es) listas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                )
            }
            CompactStatusBadge(
                label = if (selections.size > 1) "Parlay" else "Directa",
                tone = if (selections.isEmpty()) visual.colors.neutral else visual.colors.gain,
            )
        }
        if (selections.isEmpty()) {
            PreviewPanel(
                title = "Sin seleccion",
                rows = listOf(
                    PreviewRow("Paso 1", "Entra a Juegos y toca un partido.", "Juegos"),
                    PreviewRow("Paso 2", "Toca una cuota para pasarla al ticket.", "Cuota"),
                    PreviewRow("Paso 3", "Pon monto y vende con validacion del servidor.", "Venta"),
                ),
                footer = "Admin y cajero pueden vender. Master solo controla si el modulo se ve.",
            )
        } else {
            selections.forEachIndexed { index, selection ->
                if (index > 0) HorizontalDivider(color = visual.colors.border)
                SportsbookSelectionRow(selection = selection, onRemove = { onRemoveSelection(selection.oddsId) })
            }
        }
        OutlinedTextField(
            value = stakeText,
            onValueChange = onStakeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Monto a apostar") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SportsbookMetricBox(
                label = "Cuota",
                value = if (combinedOdds > 0.0) "%.2f".format(Locale.US, combinedOdds) else "0.00",
                modifier = Modifier.weight(1f),
            )
            SportsbookMetricBox(
                label = "Pago posible",
                value = formatWholeMoney(potentialPayout),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = sportsbookTicketValidationMessage(session, selections, stake, selling),
            style = MaterialTheme.typography.bodySmall,
            color = if (canSell) visual.colors.gain else visual.colors.muted,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactActionButton(
                label = if (selling) "Vendiendo..." else "Vender",
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                active = canSell,
                onClick = { if (canSell) onSell() },
                modifier = Modifier.weight(1f),
                tone = ActionTone.Success,
            )
            CompactActionButton(
                label = "Limpiar",
                icon = Icons.Rounded.FilterList,
                onClick = onClear,
                modifier = Modifier.weight(1f),
                tone = ActionTone.Secondary,
                enabled = selections.isNotEmpty() || stakeText.isNotBlank() || lastSale != null,
            )
        }
        saleStatus?.takeIf { it.isNotBlank() }?.let { status ->
            CompactStatusBadge(
                label = status,
                tone = if (lastSale != null) visual.colors.gain else visual.colors.neutral,
            )
        }
        lastSale?.let { sale ->
            SportsbookLastSalePanel(
                sale = sale,
                ticket = lastSaleTicket,
                onShareTicket = onShareLastSaleTicket,
                onPrintTicket = onPrintLastSaleTicket,
            )
        }
    }
}

private fun sportsbookTicketValidationMessage(
    session: ActiveSession,
    selections: List<SportsbookSelection>,
    stake: Double,
    selling: Boolean,
): String = when {
    selling -> "Validando la venta deportiva con el servidor..."
    session.role !in setOf(UserRole.ADMIN, UserRole.CASHIER) -> "Este perfil solo puede consultar Deportes."
    selections.isEmpty() -> "Agrega al menos una cuota abierta."
    selections.any { !selectionCanBeSold(it) } -> "Hay una selección cerrada o con cuota no disponible."
    stake <= 0.0 -> "Escribe un monto mayor que cero."
    else -> "Listo para validar cuota, permisos y límites en el servidor."
}

@Composable
private fun SportsbookLastSalePanel(
    sale: SportsbookTicketSaleResult,
    ticket: SportsbookTicketRecord?,
    onShareTicket: (SportsbookTicketRecord) -> Unit,
    onPrintTicket: (SportsbookTicketRecord, TicketPrintMark) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.financeSurface,
        border = BorderStroke(1.dp, visual.colors.gain),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Ticket vendido",
                        style = MaterialTheme.typography.titleMedium,
                        color = visual.colors.ink,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = sale.ticketCode,
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CompactStatusBadge(
                    label = if (sale.duplicate) "Sin duplicar" else "Deportes",
                    tone = visual.colors.gain,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SportsbookMetricBox(
                    label = "Apostado",
                    value = formatWholeMoney(sale.stake),
                    modifier = Modifier.weight(1f),
                )
                SportsbookMetricBox(
                    label = "Pago posible",
                    value = formatWholeMoney(sale.potentialPayout),
                    modifier = Modifier.weight(1f),
                )
            }
            ticket?.let { record ->
                val printMark = if (sale.duplicate) TicketPrintMark.COPIA else TicketPrintMark.ORIGINAL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactActionButton(
                        label = if (sale.duplicate) "Imprimir copia" else "Imprimir térmico",
                        icon = Icons.Rounded.Print,
                        onClick = { onPrintTicket(record, printMark) },
                        modifier = Modifier.weight(1f),
                        tone = ActionTone.Secondary,
                    )
                    CompactActionButton(
                        label = "Enviar WhatsApp",
                        icon = Icons.Rounded.Whatsapp,
                        onClick = { onShareTicket(record) },
                        modifier = Modifier.weight(1f),
                        tone = ActionTone.Success,
                    )
                }
            }
            Text(
                text = if (ticket == null) {
                    "Guardado en finanza deportiva separada. Refresca Reportes si necesitas copia."
                } else {
                    "Impresion original disponible aqui; las copias quedan en Reportes deportivos."
                },
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SportsbookSelectionRow(
    selection: SportsbookSelection,
    onRemove: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = selection.eventLabel.ifBlank { selection.eventId },
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${selection.marketTitle} · ${selection.selectionLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "%.2f".format(Locale.US, selection.decimalOdds),
                style = MaterialTheme.typography.titleMedium,
                color = visual.colors.gain,
                fontWeight = FontWeight.Black,
            )
            CompactActionButton(
                label = "Quitar",
                onClick = onRemove,
                tone = ActionTone.Danger,
            )
        }
    }
}

@Composable
private fun SportsbookMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.financeSurface,
        border = BorderStroke(1.dp, visual.colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.muted,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = visual.colors.ink,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SportsbookCollectionPreview(
    tickets: List<SportsbookTicketRecord>,
    ticketStatus: String,
    ticketLoading: Boolean,
    onRetry: () -> Unit,
    onShareTicket: (SportsbookTicketRecord, Boolean) -> Unit,
    onPrintThermalTicket: (SportsbookTicketRecord, TicketPrintMark) -> Unit,
    onPayTicket: (SportsbookTicketRecord) -> Unit,
) {
    val winners = tickets.filter { it.status == SportsbookTicketStatus.WON }
    val pending = tickets.filter { it.status == SportsbookTicketStatus.PENDING }
    val paid = tickets.filter { it.status == SportsbookTicketStatus.PAID }
    PreviewPanel(
        title = "Cobros deportivos",
        rows = listOf(
            PreviewRow("Ganados", "Listos para pagar cuando se liquide el juego.", "${winners.size}"),
            PreviewRow("Pendientes", "Tickets vendidos esperando resultado.", "${pending.size}"),
            PreviewRow("Pagados", "Auditoria separada de loteria.", "${paid.size}"),
        ),
        footer = ticketStatus,
    )
    if (ticketLoading) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    } else if (ticketStatus.contains("No se pudo", ignoreCase = true)) {
        CompactActionButton(
            label = "Reintentar tickets",
            onClick = onRetry,
            icon = Icons.Rounded.QueryStats,
            modifier = Modifier.fillMaxWidth(),
            tone = ActionTone.Secondary,
        )
    }
    if (winners.isNotEmpty()) {
        CompactPanel {
            Text(
                text = "Listos para pagar",
                style = MaterialTheme.typography.titleMedium,
                color = rememberLotteryNetVisualSpec().colors.ink,
                fontWeight = FontWeight.Black,
            )
            winners.take(8).forEach { ticket ->
                SportsbookTicketRow(
                    ticket = ticket,
                    onShareTicket = onShareTicket,
                    onPrintThermalTicket = onPrintThermalTicket,
                    onPayTicket = onPayTicket,
                )
            }
        }
    }
}

@Composable
private fun SportsbookFinancePreview(
    summary: SportsbookTicketSummary,
    ticketStatus: String,
) {
    val netOpen = summary.totalStake - summary.pendingPayout - summary.paidPayout
    PreviewPanel(
        title = "Finanza deportiva",
        rows = listOf(
            PreviewRow("Ventas", "Total apostado en Deportes.", formatWholeMoney(summary.totalStake)),
            PreviewRow("Pendiente", "Riesgo abierto hasta cerrar resultados.", formatWholeMoney(summary.pendingPayout)),
            PreviewRow("Pagado", "Premios deportivos ya pagados.", formatWholeMoney(summary.paidPayout)),
            PreviewRow("Ganancia / perdida", "Lectura separada de Loteria y Recargas.", formatWholeMoney(netOpen)),
        ),
        footer = ticketStatus,
    )
}

@Composable
private fun SportsbookReportPreview(
    tickets: List<SportsbookTicketRecord>,
    summary: SportsbookTicketSummary,
    onShareTicket: (SportsbookTicketRecord, Boolean) -> Unit,
    onPrintThermalTicket: (SportsbookTicketRecord, TicketPrintMark) -> Unit,
    onPayTicket: (SportsbookTicketRecord) -> Unit,
) {
    PreviewPanel(
        title = "Reportes deportivos",
        rows = listOf(
            PreviewRow("Tickets", "Ultimos tickets deportivos del negocio.", "${summary.totalTickets}"),
            PreviewRow("Pendientes", "Riesgo abierto.", "${summary.pendingTickets}"),
            PreviewRow("Ganados/Pagados", "Control de cobros deportivos.", "${summary.wonTickets}/${summary.paidTickets}"),
        ),
        footer = "Reporte separado: no mezcla quiniela, pale, recargas ni premios de loteria.",
    )
    if (tickets.isNotEmpty()) {
        CompactPanel {
            Text(
                text = "Ultimos tickets",
                style = MaterialTheme.typography.titleMedium,
                color = rememberLotteryNetVisualSpec().colors.ink,
                fontWeight = FontWeight.Black,
            )
            tickets.take(10).forEach { ticket ->
                SportsbookTicketRow(
                    ticket = ticket,
                    onShareTicket = onShareTicket,
                    onPrintThermalTicket = onPrintThermalTicket,
                    onPayTicket = onPayTicket,
                )
            }
        }
    }
}

@Composable
private fun SportsbookTicketRow(
    ticket: SportsbookTicketRecord,
    onShareTicket: (SportsbookTicketRecord, Boolean) -> Unit,
    onPrintThermalTicket: (SportsbookTicketRecord, TicketPrintMark) -> Unit,
    onPayTicket: (SportsbookTicketRecord) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = ticket.ticketCode,
                        style = MaterialTheme.typography.labelLarge,
                        color = visual.colors.ink,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${ticket.sellerUsername.ifBlank { ticket.bancaName }} · ${ticket.legs.size} seleccion(es)",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = formatWholeMoney(ticket.stake),
                        style = MaterialTheme.typography.labelLarge,
                        color = visual.colors.gain,
                        fontWeight = FontWeight.Black,
                    )
                    CompactStatusBadge(
                        label = ticket.status.wireValue,
                        tone = sportsTicketStatusTone(ticket.status),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (ticket.status == SportsbookTicketStatus.WON) {
                    CompactActionButton(
                        label = "Pagar",
                        icon = Icons.Rounded.Paid,
                        onClick = { onPayTicket(ticket) },
                        modifier = Modifier.weight(1f),
                        tone = ActionTone.Success,
                    )
                }
                CompactActionButton(
                    label = "Enviar WhatsApp",
                    icon = Icons.Rounded.Whatsapp,
                    onClick = { onShareTicket(ticket, true) },
                    modifier = Modifier.weight(1f),
                    tone = ActionTone.Success,
                )
                CompactActionButton(
                    label = "Imprimir térmico",
                    icon = Icons.Rounded.Print,
                    onClick = { onPrintThermalTicket(ticket, TicketPrintMark.COPIA) },
                    modifier = Modifier.weight(1f),
                    tone = ActionTone.Secondary,
                )
            }
        }
    }
}

@Composable
private fun SportsbookBusinessControlPreview(role: UserRole) {
    val title = if (role == UserRole.ADMIN) "Control del negocio" else "Control asignado"
    PreviewPanel(
        title = title,
        rows = listOf(
            PreviewRow("Cajeros", "Habilitar, pausar y revisar ventas deportivas de la red.", "Admin"),
            PreviewRow("Limites", "Topes por cajero, mercado, ticket y pago posible.", "Propios"),
            PreviewRow("Auditoria", "Anulaciones, cambios de cuota y pagos deportivos.", "Separado"),
            PreviewRow("Cuadre", "Caja deportiva sin mezclar loteria ni recargas.", "\$0"),
        ),
        footer = "Mismo control administrativo que Loteria, pero con datos deportivos separados.",
    )
}

@Composable
private fun SportsbookConfigPreview(
    settings: MasterSportsbookSettings,
    userAccounts: List<UserAccount>,
    canEdit: Boolean,
    onSettingsChange: (MasterSportsbookSettings) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val admins = remember(userAccounts) {
        userAccounts
            .filter { it.role == UserRole.ADMIN }
            .sortedWith(compareBy({ it.user.lowercase(Locale.US) }, { it.id }))
    }
    var selectedAdminId by remember(admins.map { it.id }.joinToString("|")) {
        mutableStateOf(admins.firstOrNull { account ->
            sportsbookAccountKeys(account).any { it in settings.allowedActorKeys || it in settings.cashierAdminKeys }
        }?.id ?: admins.firstOrNull()?.id.orEmpty())
    }
    val selectedAdmin = admins.firstOrNull { it.id == selectedAdminId } ?: admins.firstOrNull()
    val selectedAdminCashiers = remember(userAccounts, selectedAdmin?.id, selectedAdmin?.user) {
        if (selectedAdmin == null) {
            emptyList()
        } else {
            userAccounts
                .filter { account ->
                    account.role == UserRole.CASHIER &&
                        (account.adminId == selectedAdmin.id || account.adminUser == selectedAdmin.user)
                }
                .sortedWith(compareBy({ it.user.lowercase(Locale.US) }, { it.id }))
        }
    }
    CompactPanel {
        Text(
            text = "Control Master",
            style = MaterialTheme.typography.titleMedium,
            color = visual.colors.ink,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = if (canEdit) {
                "Activa Deportes por negocio. Puedes abrir solo el admin, sus cajeros o cajeros puntuales."
            } else {
                "Solo Master puede cambiar esta seccion."
            },
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.muted,
            fontWeight = FontWeight.Bold,
        )
        SportsbookSettingSwitch(
            title = "Activar Deportes",
            subtitle = "Llave principal del modulo. Los permisos de abajo siguen siendo individuales.",
            checked = settings.enabled,
            enabled = canEdit,
            onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) },
        )
        HorizontalDivider(color = visual.colors.border)
        SportsbookSettingSwitch(
            title = "Permitir admins",
            subtitle = "Habilita el rol admin; abajo eliges cuales admins concretos.",
            checked = settings.adminEnabled,
            enabled = canEdit,
            onCheckedChange = { onSettingsChange(settings.copy(adminEnabled = it)) },
        )
        SportsbookSettingSwitch(
            title = "Permitir supervisores",
            subtitle = "Consulta separada si luego decides abrir supervision.",
            checked = settings.supervisorEnabled,
            enabled = canEdit,
            onCheckedChange = { onSettingsChange(settings.copy(supervisorEnabled = it)) },
        )
        SportsbookSettingSwitch(
            title = "Permitir cajeros",
            subtitle = "No abre todos solo; debes elegir cajeros o grupo del admin.",
            checked = settings.cashierEnabled,
            enabled = canEdit,
            onCheckedChange = { onSettingsChange(settings.copy(cashierEnabled = it)) },
        )
        HorizontalDivider(color = visual.colors.border)
        Text(
            text = "Negocio / banca",
            style = MaterialTheme.typography.labelLarge,
            color = visual.colors.ink,
            fontWeight = FontWeight.Black,
        )
        if (admins.isEmpty()) {
            Text(
                text = "No hay admins cacheados en este equipo. Entra a usuarios o sincroniza primero.",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.warning,
                fontWeight = FontWeight.Bold,
            )
        } else {
            SportsbookAdminDropdown(
                admins = admins,
                selectedAdminId = selectedAdmin?.id.orEmpty(),
                onSelected = { selectedAdminId = it },
                enabled = canEdit,
            )
            selectedAdmin?.let { admin ->
                SportsbookAccountPermissionRow(
                    title = admin.displayName ?: admin.user,
                    subtitle = "Admin ${admin.user} · ${admin.banca.orEmpty()} · ${sportsbookAccessReason(settings, UserRole.ADMIN, admin)}",
                    checked = sportsbookAccountKeys(admin).any { it in settings.allowedActorKeys },
                    enabled = canEdit,
                    onCheckedChange = { checked ->
                        onSettingsChange(
                            settings
                                .withAccountAccess(admin, checked)
                                .copy(enabled = true, adminEnabled = true),
                        )
                    },
                )
                SportsbookSettingSwitch(
                    title = "Cajeros de ${admin.user}",
                    subtitle = "Activa o cierra todos los cajeros de este admin sin tocar otros negocios. ${sportsbookGroupAccessReason(settings, admin)}",
                    checked = sportsbookAccountKeys(admin).any { it in settings.cashierAdminKeys },
                    enabled = canEdit,
                    onCheckedChange = { checked ->
                        onSettingsChange(
                            settings
                                .withCashierAdminAccess(admin, checked)
                                .copy(enabled = true, cashierEnabled = checked || settings.cashierEnabled),
                        )
                    },
                )
                selectedAdminCashiers.take(12).forEach { cashier ->
                    SportsbookAccountPermissionRow(
                        title = cashier.displayName ?: cashier.user,
                        subtitle = "Cajero ${cashier.user} · ${sportsbookAccessReason(settings, UserRole.CASHIER, cashier, admin)}",
                        checked = sportsbookAccountKeys(cashier).any { it in settings.allowedActorKeys },
                        enabled = canEdit,
                        onCheckedChange = { checked ->
                            onSettingsChange(
                                settings
                                    .withAccountAccess(cashier, checked)
                                    .copy(enabled = true, cashierEnabled = checked || settings.cashierEnabled),
                            )
                        },
                    )
                }
                if (selectedAdminCashiers.size > 12) {
                    Text(
                        text = "+${selectedAdminCashiers.size - 12} cajeros mas. Usa el switch del grupo para abrirlos todos.",
                        style = MaterialTheme.typography.labelSmall,
                        color = visual.colors.neutral,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        HorizontalDivider(color = visual.colors.border)
        Text(
            text = "Mercados iniciales",
            style = MaterialTheme.typography.labelLarge,
            color = visual.colors.ink,
            fontWeight = FontWeight.Black,
        )
        SportsbookMarketKey.entries.forEach { market ->
            SportsbookSettingSwitch(
                title = market.label,
                subtitle = market.wireValue,
                checked = market in settings.enabledMarkets,
                enabled = canEdit,
                onCheckedChange = { checked ->
                    val nextMarkets = if (checked) {
                        settings.enabledMarkets + market
                    } else {
                        settings.enabledMarkets - market
                    }
                    onSettingsChange(settings.copy(enabledMarkets = nextMarkets))
                },
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "El servidor valida estos permisos antes de guardar cada ticket deportivo.",
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.neutral,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SportsbookAdminDropdown(
    admins: List<UserAccount>,
    selectedAdminId: String,
    onSelected: (String) -> Unit,
    enabled: Boolean,
) {
    var showPicker by remember { mutableStateOf(false) }
    val selected = admins.firstOrNull { it.id == selectedAdminId } ?: admins.first()
    val options = remember(admins) {
        admins.map { admin ->
            SearchableSheetOption(
                id = admin.id,
                title = admin.user,
                subtitle = admin.banca.orEmpty().ifBlank { admin.displayName.orEmpty() },
                meta = admin.id,
            )
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        CompactActionButton(
            label = "Admin: ${selected.user}",
            icon = Icons.Rounded.ExpandMore,
            active = showPicker,
            onClick = { if (enabled) showPicker = true },
            modifier = Modifier.fillMaxWidth(),
            tone = ActionTone.IntenseBlue,
        )
        if (showPicker) {
            SearchableOptionSheet(
                title = "Seleccionar admin",
                subtitle = "Permisos deportivos separados por negocio.",
                options = options,
                selectedId = selectedAdminId,
                onSelected = {
                    showPicker = false
                    onSelected(it)
                },
                onDismiss = { showPicker = false },
                searchLabel = "Buscar admin",
                searchPlaceholder = "Usuario, banca o ID",
            )
        }
    }
}

@Composable
private fun SportsbookAccountPermissionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SportsbookSettingSwitch(
        title = title,
        subtitle = subtitle,
        checked = checked,
        enabled = enabled,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun SportsbookSettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = visual.colors.ink,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun PreviewPanel(
    title: String,
    rows: List<PreviewRow>,
    footer: String = "Todavia no vende ni consulta Odds API desde Android.",
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = visual.colors.ink,
            fontWeight = FontWeight.Black,
        )
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = visual.colors.border)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = visual.colors.ink,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = row.meta,
                    style = MaterialTheme.typography.labelMedium.merge(
                        TextStyle(fontWeight = FontWeight.Black),
                    ),
                    color = visual.colors.actionPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = footer,
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.neutral,
            fontWeight = FontWeight.Bold,
        )
    }
}

private data class SportsbookTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
)

internal data class SportsbookBoardFilterOption(
    val id: String,
    val label: String,
) {
    companion object {
        val ALL = SportsbookBoardFilterOption("all", "Todas")
        val OPEN = SportsbookBoardFilterOption("open", "Abiertos")
        val CLOSED = SportsbookBoardFilterOption("closed", "Cerrados")
    }
}

internal fun sportsbookStatusFilterOptions(): List<SportsbookBoardFilterOption> {
    return listOf(
        SportsbookBoardFilterOption.ALL,
        SportsbookBoardFilterOption.OPEN,
        SportsbookBoardFilterOption.CLOSED,
    )
}

internal fun buildSportsbookLeagueFilterOptions(games: List<SportsbookBoardGame>): List<SportsbookBoardFilterOption> {
    val leagues = games
        .mapNotNull { game -> game.event.leagueTitle?.trim()?.takeIf { it.isNotBlank() } }
        .distinct()
        .sorted()
        .map { league -> SportsbookBoardFilterOption(league, league) }
    return listOf(SportsbookBoardFilterOption.ALL) + leagues
}

internal fun buildSportsbookSportFilterOptions(games: List<SportsbookBoardGame>): List<SportsbookBoardFilterOption> {
    val sports = games
        .mapNotNull { it.event.sportTitle.trim().takeIf(String::isNotBlank) }
        .distinct()
        .sorted()
        .map { sport -> SportsbookBoardFilterOption(sport, sport) }
    return listOf(SportsbookBoardFilterOption.ALL) + sports
}

internal fun sportsbookDateFilterOptions(): List<SportsbookBoardFilterOption> = listOf(
    SportsbookBoardFilterOption.ALL,
    SportsbookBoardFilterOption("today", "Hoy"),
    SportsbookBoardFilterOption("tomorrow", "Mañana"),
)

internal fun buildSportsbookMarketFilterOptions(games: List<SportsbookBoardGame>): List<SportsbookBoardFilterOption> {
    val markets = games.flatMap { it.markets }
        .map { it.key.wireValue to it.key.label }
        .distinctBy { it.first }
        .sortedBy { it.second }
        .map { (id, label) -> SportsbookBoardFilterOption(id, label) }
    return listOf(SportsbookBoardFilterOption.ALL) + markets
}

internal fun filterSportsbookBoardGames(
    games: List<SportsbookBoardGame>,
    leagueId: String,
    statusId: String,
    sportId: String = SportsbookBoardFilterOption.ALL.id,
    dateId: String = SportsbookBoardFilterOption.ALL.id,
    marketId: String = SportsbookBoardFilterOption.ALL.id,
): List<SportsbookBoardGame> {
    val today = LocalDate.now()
    return games.filter { game ->
        val sportMatches = sportId == SportsbookBoardFilterOption.ALL.id ||
            game.event.sportTitle == sportId
        val leagueMatches = leagueId == SportsbookBoardFilterOption.ALL.id ||
            game.event.leagueTitle == leagueId
        val statusMatches = when (statusId) {
            SportsbookBoardFilterOption.OPEN.id -> game.isOpen
            SportsbookBoardFilterOption.CLOSED.id -> !game.isOpen
            else -> true
        }
        val gameDate = Instant.ofEpochMilli(game.event.commenceTimeEpochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val dateMatches = when (dateId) {
            "today" -> gameDate == today
            "tomorrow" -> gameDate == today.plusDays(1)
            else -> true
        }
        val marketMatches = marketId == SportsbookBoardFilterOption.ALL.id ||
            game.markets.any { it.key.wireValue == marketId && it.status.equals("open", ignoreCase = true) }
        sportMatches && leagueMatches && statusMatches && dateMatches && marketMatches
    }
}

private fun sportsbookTabsForRole(role: UserRole): List<SportsbookTab> {
    return when (role) {
        UserRole.MASTER -> listOf(SportsbookTab("config", "Sistema", Icons.Rounded.Tune))
        UserRole.ADMIN -> listOf(
            SportsbookTab("juegos", "Juegos", Icons.Rounded.SportsSoccer),
            SportsbookTab("ticket", "Ticket", Icons.AutoMirrored.Rounded.ReceiptLong),
            SportsbookTab("cobros", "Cobros", Icons.Rounded.Paid),
            SportsbookTab("finanza", "Finanza", Icons.Rounded.Assessment),
            SportsbookTab("reportes", "Reportes", Icons.Rounded.Assessment),
            SportsbookTab("control", "Control", Icons.Rounded.Tune),
        )
        else -> listOf(
            SportsbookTab("juegos", "Juegos", Icons.Rounded.SportsSoccer),
            SportsbookTab("ticket", "Ticket", Icons.AutoMirrored.Rounded.ReceiptLong),
            SportsbookTab("cobros", "Cobros", Icons.Rounded.Paid),
            SportsbookTab("finanza", "Finanza", Icons.Rounded.Assessment),
            SportsbookTab("reportes", "Reportes", Icons.Rounded.Assessment),
        )
    }
}

internal fun resolveSportsbookTabIdsForRole(role: UserRole): List<String> {
    return when (role) {
        UserRole.MASTER -> listOf("config")
        UserRole.ADMIN -> listOf("juegos", "ticket", "cobros", "finanza", "reportes", "control")
        else -> listOf("juegos", "ticket", "cobros", "finanza", "reportes")
    }
}

internal fun resolveSportsbookInitialTab(role: UserRole): String {
    return resolveSportsbookTabIdsForRole(role).first()
}

internal fun canLoadSportsbookBoard(
    role: UserRole,
    settings: MasterSportsbookSettings,
    actorKey: String?,
    adminKey: String? = null,
): Boolean {
    if (role == UserRole.MASTER) return false
    if (settings.allowedActorKeys.isEmpty() && settings.cashierAdminKeys.isEmpty()) {
        return when (role) {
            UserRole.ADMIN -> settings.enabled && settings.adminEnabled
            UserRole.SUPERVISOR -> settings.enabled && settings.supervisorEnabled
            UserRole.CASHIER -> settings.enabled && settings.cashierEnabled
            else -> false
        }
    }
    return settings.toFeatureConfig().canOpen(role, actorKey, adminKey)
}

internal fun sportsbookAccessReason(
    settings: MasterSportsbookSettings,
    role: UserRole,
    account: UserAccount,
    admin: UserAccount? = null,
): String {
    if (!settings.enabled) return "Bloqueado: Deportes global apagado"
    val accountKeys = sportsbookAccountKeys(account)
    val hasIndividualAccess = accountKeys.any { key -> key in settings.allowedActorKeys }
    val hasGroupAccess = role == UserRole.CASHIER && admin != null &&
        sportsbookAccountKeys(admin).any { key -> key in settings.cashierAdminKeys }
    if (hasIndividualAccess) return "Activo individual"
    if (hasGroupAccess) return "Activo por grupo"
    val roleEnabled = when (role) {
        UserRole.ADMIN -> settings.adminEnabled
        UserRole.SUPERVISOR -> settings.supervisorEnabled
        UserRole.CASHIER -> settings.cashierEnabled
        else -> false
    }
    if (settings.allowedActorKeys.isEmpty() && settings.cashierAdminKeys.isEmpty() && roleEnabled) {
        return "Activo por rol"
    }
    return when (role) {
        UserRole.ADMIN -> "Bloqueado: Admin no autorizado"
        UserRole.SUPERVISOR -> "Bloqueado: Supervisor no autorizado"
        UserRole.CASHIER -> "Bloqueado: Cajero no autorizado"
        else -> "Bloqueado"
    }
}

internal fun sportsbookGroupAccessReason(
    settings: MasterSportsbookSettings,
    admin: UserAccount,
): String {
    if (!settings.enabled) return "Bloqueado globalmente."
    return if (sportsbookAccountKeys(admin).any { it in settings.cashierAdminKeys }) {
        "Activo por grupo."
    } else {
        "Grupo apagado."
    }
}

internal fun sportsbookAccountKeys(account: UserAccount): Set<String> {
    return buildSet {
        account.id.trim().takeIf { it.isNotBlank() }?.let(::add)
        account.user.trim().takeIf { it.isNotBlank() }?.let(::add)
        account.authUserId?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
    }
}

internal fun MasterSportsbookSettings.withAccountAccess(
    account: UserAccount,
    enabled: Boolean,
): MasterSportsbookSettings {
    val keys = sportsbookAccountKeys(account)
    return copy(
        allowedActorKeys = if (enabled) {
            allowedActorKeys + keys
        } else {
            allowedActorKeys - keys
        },
    )
}

internal fun MasterSportsbookSettings.withCashierAdminAccess(
    admin: UserAccount,
    enabled: Boolean,
): MasterSportsbookSettings {
    val keys = sportsbookAccountKeys(admin)
    return copy(
        cashierAdminKeys = if (enabled) {
            cashierAdminKeys + keys
        } else {
            cashierAdminKeys - keys
        },
    )
}

private fun sportsbookGameStatusLabel(game: SportsbookBoardGame): String {
    return when {
        game.event.status.equals("started", ignoreCase = true) ||
            game.event.status.equals("in_progress", ignoreCase = true) -> "Iniciado"
        game.event.status.equals("suspended", ignoreCase = true) ||
            game.markets.any { it.status.equals("suspended", ignoreCase = true) } -> "Suspendido"
        game.isOpen -> "Abierto"
        game.event.status.equals("finished", ignoreCase = true) -> "Finalizado"
        else -> "Cerrado"
    }
}

private fun sportsbookGameStatusTone(
    game: SportsbookBoardGame,
    visual: com.lotterynet.pro.ui.common.LotteryNetVisualSpec,
) = when (sportsbookGameStatusLabel(game)) {
    "Abierto" -> visual.colors.gain
    "Finalizado" -> visual.colors.neutral
    else -> visual.colors.warning
}

private fun selectedSportsbookFilterLabel(
    options: List<SportsbookBoardFilterOption>,
    selectedId: String,
): String {
    return options.firstOrNull { it.id == selectedId }?.label ?: SportsbookBoardFilterOption.ALL.label
}

private fun sportsbookPrimaryMarketPreview(
    game: SportsbookBoardGame,
): Pair<SportsbookMarket, List<SportsbookOdd>>? {
    val preferredMarket = game.markets.firstOrNull { market ->
        market.key == SportsbookMarketKey.MONEYLINE && sportsbookOddsForMarket(game, market).any(::sportsbookOddCanBeSold)
    }
    val market = preferredMarket ?: game.markets.firstOrNull { market ->
        sportsbookOddsForMarket(game, market).any(::sportsbookOddCanBeSold)
    } ?: return null
    val odds = sportsbookOddsForMarket(game, market)
        .filter(::sportsbookOddCanBeSold)
        .take(3)
    return if (odds.isEmpty()) null else market to odds
}

private fun sportsbookOddCanBeSold(odd: SportsbookOdd): Boolean {
    return odd.id.isNotBlank() && odd.decimalOdds > 1.0 && odd.status.lowercase(Locale.US) != "closed"
}

internal fun buildSportsbookTicketRecordFromSale(
    session: ActiveSession,
    sale: SportsbookTicketSaleResult,
    selections: List<SportsbookSelection>,
): SportsbookTicketRecord {
    return SportsbookTicketRecord(
        id = "local-${sale.ticketCode}",
        ticketCode = sale.ticketCode,
        sellerUsername = session.username,
        bancaName = session.banca.orEmpty(),
        ticketType = if (selections.size > 1) "parlay" else "straight",
        stake = sale.stake,
        decimalOdds = sale.decimalOdds,
        potentialPayout = sale.potentialPayout,
        status = sale.status,
        soldAtEpochMs = System.currentTimeMillis(),
        legs = selections.map { selection ->
            SportsbookTicketLegRecord(
                eventLabel = selection.eventLabel,
                marketTitle = selection.marketTitle,
                selectionLabel = selection.selectionLabel,
                decimalOdds = selection.decimalOdds,
                status = sale.status,
            )
        },
    )
}

internal fun mergeSportsbookTicketSnapshot(
    snapshot: SportsbookTicketSnapshot,
    recentTicket: SportsbookTicketRecord,
): SportsbookTicketSnapshot {
    val tickets = listOf(recentTicket) + snapshot.tickets.filterNot {
        it.ticketCode == recentTicket.ticketCode ||
            (recentTicket.id.isNotBlank() && it.id == recentTicket.id)
    }
    val pending = tickets.count { it.status == SportsbookTicketStatus.PENDING }
    val won = tickets.count { it.status == SportsbookTicketStatus.WON }
    val paid = tickets.count { it.status == SportsbookTicketStatus.PAID }
    return snapshot.copy(
        tickets = tickets,
        summary = snapshot.summary.copy(
            totalTickets = maxOf(snapshot.summary.totalTickets, tickets.size),
            pendingTickets = maxOf(snapshot.summary.pendingTickets, pending),
            wonTickets = maxOf(snapshot.summary.wonTickets, won),
            paidTickets = maxOf(snapshot.summary.paidTickets, paid),
            totalStake = maxOf(snapshot.summary.totalStake, tickets.sumOf { it.stake }),
        ),
    )
}

@Composable
private fun sportsTicketStatusTone(status: SportsbookTicketStatus) = when (status) {
    SportsbookTicketStatus.WON -> rememberLotteryNetVisualSpec().colors.gain
    SportsbookTicketStatus.PAID -> rememberLotteryNetVisualSpec().colors.actionPrimary
    SportsbookTicketStatus.LOST -> rememberLotteryNetVisualSpec().colors.warning
    SportsbookTicketStatus.VOID -> rememberLotteryNetVisualSpec().colors.warning
    else -> rememberLotteryNetVisualSpec().colors.neutral
}

private fun sportsbookOddsForMarket(
    game: SportsbookBoardGame,
    market: SportsbookMarket,
): List<SportsbookOdd> {
    return game.odds.filter { odd -> odd.marketId == market.id }
}

internal fun buildSportsbookSelection(
    game: SportsbookBoardGame,
    market: SportsbookMarket,
    odd: SportsbookOdd,
): SportsbookSelection {
    return SportsbookSelection(
        oddsId = odd.id,
        eventId = game.event.id,
        market = market.key,
        eventLabel = "${game.event.awayTeam} @ ${game.event.homeTeam}",
        marketTitle = market.title.ifBlank { market.key.label },
        selectionKey = odd.selectionKey,
        selectionLabel = odd.selectionLabel,
        decimalOdds = odd.decimalOdds,
        point = odd.point,
        oddsLockedAtEpochMs = odd.lastUpdatedEpochMs,
    )
}

internal fun toggleSportsbookSelection(
    current: List<SportsbookSelection>,
    selection: SportsbookSelection,
): List<SportsbookSelection> {
    if (selection.oddsId.isBlank()) return current
    if (current.any { it.oddsId == selection.oddsId }) {
        return current.filterNot { it.oddsId == selection.oddsId }
    }
    val withoutSameGameMarket = current.filterNot {
        it.eventId == selection.eventId && it.market == selection.market
    }
    return withoutSameGameMarket + selection
}

private fun teamInitials(teamName: String): String {
    val words = teamName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (words.isEmpty()) return "T"
    return words
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .take(2)
}

private data class PreviewRow(
    val title: String,
    val subtitle: String,
    val meta: String,
)
