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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import coil3.compose.AsyncImage
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
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactAdaptiveGrid
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
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
        val remoteStore = SupabaseMasterConfigRemoteStore()
        val boardStore = SportsbookBoardRemoteStore()
        val ticketStore = SportsbookTicketRemoteStore()
        setContent {
            LotteryNetComposeTheme {
                var settings by remember { mutableStateOf(configRepository.getSportsbookSettings()) }
                var syncStatus by remember { mutableStateOf("Leyendo control Master...") }
                var boardSnapshot by remember { mutableStateOf(SportsbookBoardSnapshot()) }
                var boardStatus by remember { mutableStateOf("Tablero pendiente de sincronizar.") }
                var ticketSnapshot by remember { mutableStateOf(SportsbookTicketSnapshot()) }
                var ticketStatus by remember { mutableStateOf("Tickets deportivos pendientes de leer.") }
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
                ) {
                    val canLoadBoard = canLoadSportsbookBoard(
                        role = activeSession.role,
                        settings = settings,
                        actorKey = activeSession.userId.ifBlank { activeSession.username },
                        adminKey = activeSession.adminId ?: activeSession.adminUser,
                    )
                    if (!canLoadBoard) return@LaunchedEffect
                    boardStatus = "Buscando juegos cacheados..."
                    val nextBoard = withContext(Dispatchers.IO) {
                        runCatching {
                            boardStore.fetchBoard(bearerToken = activeSession.authAccessToken)
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
                    val nextTickets = withContext(Dispatchers.IO) {
                        runCatching { ticketStore.fetchTickets(activeSession) }.getOrNull()
                    }
                    if (nextTickets != null) {
                        ticketSnapshot = nextTickets
                        ticketStatus = "${nextTickets.tickets.size} ticket(s) deportivos leidos."
                    } else {
                        ticketStatus = "No se pudo leer tickets deportivos."
                    }
                }
                SportsbookRoute(
                    session = activeSession,
                    settings = settings,
                    userAccounts = userAccounts,
                    syncStatus = syncStatus,
                    boardSnapshot = boardSnapshot,
                    boardStatus = boardStatus,
                    ticketSnapshot = ticketSnapshot,
                    ticketStatus = ticketStatus,
                    onCreateTicket = { draft ->
                        val sale = withContext(Dispatchers.IO) {
                            ticketStore.createTicket(activeSession, draft)
                        }
                        val nextTickets = withContext(Dispatchers.IO) {
                            runCatching { ticketStore.fetchTickets(activeSession) }.getOrNull()
                        }
                        if (nextTickets != null) {
                            ticketSnapshot = nextTickets
                            ticketStatus = "${nextTickets.tickets.size} ticket(s) deportivos leidos."
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
                    onPrintThermalTicket = { ticket ->
                        thread(name = "sportsbook-thermal-print") {
                            val prefs = LocalThermalPrinterRepository(this@SportsbookActivity).getPrefs()
                            val text = ThermalTicketRenderer().renderSportsbookTicket(
                                ticket = ticket,
                                bancaName = ticket.bancaName.ifBlank { activeSession.banca ?: "Deportes" },
                                prefs = prefs,
                                printMark = TicketPrintMark.COPIA,
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
@OptIn(ExperimentalMaterial3Api::class)
private fun SportsbookRoute(
    session: ActiveSession,
    settings: MasterSportsbookSettings,
    userAccounts: List<UserAccount>,
    syncStatus: String,
    boardSnapshot: SportsbookBoardSnapshot,
    boardStatus: String,
    ticketSnapshot: SportsbookTicketSnapshot,
    ticketStatus: String,
    onCreateTicket: suspend (SportsbookTicketDraft) -> SportsbookTicketSaleResult,
    onShareTicket: (SportsbookTicketRecord, Boolean) -> Unit,
    onPrintThermalTicket: (SportsbookTicketRecord) -> Unit,
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
    var selectedStatus by remember { mutableStateOf(SportsbookBoardFilterOption.OPEN.id) }
    var selectedGame by remember { mutableStateOf<SportsbookBoardGame?>(null) }
    var selections by remember { mutableStateOf<List<SportsbookSelection>>(emptyList()) }
    var stakeText by remember { mutableStateOf("") }
    var saleStatus by remember { mutableStateOf<String?>(null) }
    var lastSale by remember { mutableStateOf<SportsbookTicketSaleResult?>(null) }
    var selling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (selectedGame != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedGame = null },
            sheetState = sheetState,
            containerColor = visual.colors.panel,
        ) {
            SportsbookGameSheet(
                game = selectedGame ?: return@ModalBottomSheet,
                selectedOddsIds = selections.map { it.oddsId }.toSet(),
                onOddSelected = { selection ->
                    selections = toggleSportsbookSelection(selections, selection)
                    saleStatus = "Seleccion agregada al ticket."
                    selectedTab = "ticket"
                    selectedGame = null
                },
                onDismiss = { selectedGame = null },
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
                            },
                            onSell = {
                                val stake = stakeText.toDoubleOrNull() ?: 0.0
                                val draft = SportsbookTicketDraft(selections = selections, stake = stake)
                                selling = true
                                saleStatus = "Validando en servidor..."
                                scope.launch {
                                    val result = runCatching { onCreateTicket(draft) }
                                    selling = false
                                    result.onSuccess { sale ->
                                        lastSale = sale
                                        saleStatus = "Ticket vendido: ${sale.ticketCode}"
                                        selections = emptyList()
                                        stakeText = ""
                                    }.onFailure { error ->
                                        saleStatus = error.message ?: "No se pudo vender el ticket deportivo."
                                    }
                                }
                            },
                        )
                        "cobros" -> SportsbookCollectionPreview(
                            tickets = ticketSnapshot.tickets,
                            ticketStatus = ticketStatus,
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
                            selectedLeague = selectedLeague,
                            onLeagueSelected = { selectedLeague = it },
                            selectedStatus = selectedStatus,
                            onStatusSelected = { selectedStatus = it },
                            onGameSelected = { selectedGame = it },
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
    CompactAdaptiveGrid(
        itemCount = tabs.size,
        columns = 3,
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
    ) { index, modifier ->
        val tab = tabs[index]
        CompactActionButton(
            label = tab.label,
            icon = tab.icon,
            active = selected == tab.id,
            onClick = { onSelected(tab.id) },
            modifier = modifier,
        )
    }
}

@Composable
private fun SportsbookBoardPreview(
    boardSnapshot: SportsbookBoardSnapshot,
    boardStatus: String,
    selectedLeague: String,
    onLeagueSelected: (String) -> Unit,
    selectedStatus: String,
    onStatusSelected: (String) -> Unit,
    onGameSelected: (SportsbookBoardGame) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val leagueOptions = remember(boardSnapshot.games) { buildSportsbookLeagueFilterOptions(boardSnapshot.games) }
    val statusOptions = remember { sportsbookStatusFilterOptions() }
    val safeLeague = leagueOptions.firstOrNull { it.id == selectedLeague }?.id ?: SportsbookBoardFilterOption.ALL.id
    val safeStatus = statusOptions.firstOrNull { it.id == selectedStatus }?.id ?: SportsbookBoardFilterOption.OPEN.id
    val games = remember(boardSnapshot.games, safeLeague, safeStatus) {
        filterSportsbookBoardGames(boardSnapshot.games, safeLeague, safeStatus)
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
                    text = "Juegos disponibles",
                    style = MaterialTheme.typography.titleMedium,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = boardStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactStatusBadge(label = "${games.size}", tone = visual.colors.actionPrimary)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SportsbookFilterDropdown(
                label = "Liga",
                selectedId = safeLeague,
                options = leagueOptions,
                onSelected = onLeagueSelected,
                modifier = Modifier.weight(1f),
            )
            SportsbookFilterDropdown(
                label = "Estado",
                selectedId = safeStatus,
                options = statusOptions,
                onSelected = onStatusSelected,
                modifier = Modifier.weight(1f),
            )
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
        } else {
            games.take(12).forEachIndexed { index, game ->
                if (index > 0) HorizontalDivider(color = visual.colors.border)
                SportsbookGameRow(game = game, onClick = { onGameSelected(game) })
            }
        }
    }
}

@Composable
private fun SportsbookFilterDropdown(
    label: String,
    selectedId: String,
    options: List<SportsbookBoardFilterOption>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull { it.id == selectedId } ?: options.first()
    Box(modifier = modifier) {
        CompactActionButton(
            label = "$label: ${selected.label}",
            icon = Icons.Rounded.ExpandMore,
            active = expanded,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = visual.colors.panel,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontWeight = if (option.id == selectedId) FontWeight.Black else FontWeight.Bold,
                            color = visual.colors.ink,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun SportsbookGameRow(
    game: SportsbookBoardGame,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val event = game.event
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
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
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompactStatusBadge(
                    label = sportsbookGameStatusLabel(game),
                    tone = if (game.isOpen) visual.colors.gain else visual.colors.warning,
                )
                CompactActionButton(label = "Ver", icon = Icons.Rounded.SportsSoccer, onClick = onClick)
            }
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
    onDismiss: () -> Unit,
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
                tone = if (game.isOpen) visual.colors.gain else visual.colors.warning,
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
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Cerrar", fontWeight = FontWeight.Black)
        }
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
    onStakeChange: (String) -> Unit,
    onRemoveSelection: (String) -> Unit,
    onClear: () -> Unit,
    onSell: () -> Unit,
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
            )
            CompactActionButton(
                label = "Limpiar",
                icon = Icons.Rounded.FilterList,
                onClick = onClear,
                modifier = Modifier.weight(1f),
            )
        }
        saleStatus?.takeIf { it.isNotBlank() }?.let { status ->
            CompactStatusBadge(
                label = status,
                tone = if (lastSale != null) visual.colors.gain else visual.colors.neutral,
            )
        }
        lastSale?.let { sale ->
            PreviewPanel(
                title = "Ultima venta",
                rows = listOf(
                    PreviewRow("Ticket", sale.ticketCode, sale.status.wireValue),
                    PreviewRow("Monto", "Apostado ${formatWholeMoney(sale.stake)}", formatWholeMoney(sale.potentialPayout)),
                    PreviewRow("Cuota", "Congelada por servidor", "%.2f".format(Locale.US, sale.decimalOdds)),
                ),
                footer = if (sale.duplicate) "El servidor detecto reintento y no duplico la venta." else "Guardado en finanza deportiva separada.",
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
            TextButton(onClick = onRemove) {
                Text("Quitar", fontWeight = FontWeight.Black)
            }
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
    onShareTicket: (SportsbookTicketRecord, Boolean) -> Unit,
    onPrintThermalTicket: (SportsbookTicketRecord) -> Unit,
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
    onPrintThermalTicket: (SportsbookTicketRecord) -> Unit,
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
    onPrintThermalTicket: (SportsbookTicketRecord) -> Unit,
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
                    )
                }
                CompactActionButton(
                    label = "WhatsApp",
                    icon = Icons.Rounded.Whatsapp,
                    onClick = { onShareTicket(ticket, true) },
                    modifier = Modifier.weight(1f),
                )
                CompactActionButton(
                    label = "Termico",
                    icon = Icons.Rounded.Print,
                    onClick = { onPrintThermalTicket(ticket) },
                    modifier = Modifier.weight(1f),
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
                    subtitle = "Admin ${admin.user} · ${admin.banca.orEmpty()}",
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
                    subtitle = "Activa o cierra todos los cajeros de este admin sin tocar otros negocios.",
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
                        subtitle = "Cajero ${cashier.user}",
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
    val visual = rememberLotteryNetVisualSpec()
    var expanded by remember { mutableStateOf(false) }
    val selected = admins.firstOrNull { it.id == selectedAdminId } ?: admins.first()
    Box(modifier = Modifier.fillMaxWidth()) {
        CompactActionButton(
            label = "Admin: ${selected.user}",
            icon = Icons.Rounded.ExpandMore,
            active = expanded,
            onClick = { if (enabled) expanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = visual.colors.panel,
        ) {
            admins.forEach { admin ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = admin.user,
                                fontWeight = FontWeight.Black,
                                color = visual.colors.ink,
                            )
                            Text(
                                text = admin.banca.orEmpty().ifBlank { admin.displayName.orEmpty() },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = visual.colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(admin.id)
                    },
                )
            }
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

internal fun filterSportsbookBoardGames(
    games: List<SportsbookBoardGame>,
    leagueId: String,
    statusId: String,
): List<SportsbookBoardGame> {
    return games.filter { game ->
        val leagueMatches = leagueId == SportsbookBoardFilterOption.ALL.id ||
            game.event.leagueTitle == leagueId
        val statusMatches = when (statusId) {
            SportsbookBoardFilterOption.OPEN.id -> game.isOpen
            SportsbookBoardFilterOption.CLOSED.id -> !game.isOpen
            else -> true
        }
        leagueMatches && statusMatches
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
    return settings.toFeatureConfig().canOpen(role, actorKey, adminKey)
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
    return if (game.isOpen) "Abierto" else "Cerrado"
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
