package com.lotterynet.pro.ui.tickets

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactEmptyState
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.MetricStrip
import com.lotterynet.pro.ui.common.MetricStripItem
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.ScreenChromeAction
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.shell.ShellActivity
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.util.Locale

class TicketDetailActivity : AppCompatActivity() {
    private lateinit var session: ActiveSession
    private lateinit var salesRepository: LocalSalesRepository
    private lateinit var usersRepository: LocalUsersRepository

    private var ticketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var cashiersState by mutableStateOf<List<UserAccount>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.TICKET_SUMMARY)) return
        session = activeSession ?: return
        salesRepository = LocalSalesRepository(this)
        usersRepository = LocalUsersRepository(this)
        usersRepository.touchSession(session)
        refreshTicketData()

        setContent {
            LotteryNetComposeTheme {
                TicketDetailRoute(
                    session = session,
                    tickets = ticketsState,
                    cashiers = cashiersState,
                    onBackToSummary = {
                        startActivity(Intent(this, TicketSummaryActivity::class.java))
                        finish()
                    },
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
    }

    override fun onResume() {
        super.onResume()
        if (::salesRepository.isInitialized) {
            refreshTicketData()
        }
    }

    private fun refreshTicketData() {
        ticketsState = salesRepository.getAllTickets()
        cashiersState = usersRepository.getCashiers()
    }
}

@Composable
private fun TicketDetailRoute(
    session: ActiveSession,
    tickets: List<TicketRecord>,
    cashiers: List<UserAccount>,
    onBackToSummary: () -> Unit,
    onOpenTicket: (TicketRecord) -> Unit,
) {
    val context = LocalContext.current
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveTicketCollectionLayout(visual.windowMode) }
    val directory = remember(session, tickets, cashiers) {
        buildTicketDirectory(session, tickets, cashiers)
    }
    val allRows = remember(directory.tickets) { buildTicketDetailRows(directory.tickets) }
    val playTypeOptions = remember(allRows) { buildPlayTypeOptions(allRows) }
    val lotteryOptions = remember(directory.tickets) { buildLotteryOptions(directory.tickets) }
    val cashierOptions = remember(directory.cashierOptions) { buildCashierOptions(directory.cashierOptions) }

    var playTypeFilter by rememberSaveable { mutableStateOf("") }
    var ownerScope by rememberSaveable { mutableStateOf(TicketOwnerScope.ALL.name) }
    var selectedCashierKey by rememberSaveable { mutableStateOf("") }
    var lotteryFilter by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var fromDateTime by rememberSaveable { mutableStateOf("") }
    var toDateTime by rememberSaveable { mutableStateOf("") }
    var selectedPreset by rememberSaveable { mutableStateOf("") }

    val ownerScopeValue = TicketOwnerScope.valueOf(ownerScope)
    val filteredRows = remember(
        allRows,
        directory,
        playTypeFilter,
        ownerScopeValue,
        selectedCashierKey,
        lotteryFilter,
        query,
        fromDateTime,
        toDateTime,
    ) {
        filterDetailRows(
            rows = allRows,
            directory = directory,
            playType = playTypeFilter,
            lotteryName = lotteryFilter,
            ownerScope = ownerScopeValue,
            cashierKey = selectedCashierKey,
            query = query,
            fromDateTime = fromDateTime,
            toDateTime = toDateTime,
        )
    }
    val filteredGroups = remember(filteredRows) { groupTicketDetailRows(filteredRows) }
    val visibleAmount = remember(filteredRows) { filteredRows.sumOf { it.play.amount } }

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
                        title = "Tickets detallados",
                        subtitle = "${session.banca ?: "LotteryNet"} · consulta organizada",
                        activeBottomTab = NativeBottomTab.LIST,
                        rightAction = ScreenChromeAction(
                            icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            contentDescription = "Volver al resumen",
                            onClick = onBackToSummary,
                            label = "Resumen",
                        ),
                    ),
                        onOpenMenu = { com.lotterynet.pro.ui.common.openShellMenu(context) },
                )
                Spacer(modifier = Modifier.height(visual.sizes.sectionGap))
                CompactPanel {
                    SectionHeader(title = "Consulta", meta = "${filteredGroups.size} tickets")
                    TicketDetailFilters(
                        playTypeFilter = playTypeFilter,
                        ownerScope = ownerScopeValue,
                        canFilterOwner = session.role == com.lotterynet.pro.core.model.UserRole.ADMIN,
                        selectedCashierKey = selectedCashierKey,
                        cashierOptions = cashierOptions,
                        lotteryFilter = lotteryFilter,
                        lotteryOptions = lotteryOptions,
                        playTypeOptions = playTypeOptions,
                        query = query,
                        fromDateTime = fromDateTime,
                        toDateTime = toDateTime,
                        selectedPreset = selectedPreset,
                        visibleCount = filteredRows.size,
                        onPlayTypeFilterChange = { playTypeFilter = it },
                        onOwnerScopeChange = { scope ->
                            ownerScope = scope.name
                            if (scope != TicketOwnerScope.CASHIER) {
                                selectedCashierKey = ""
                            }
                        },
                        onCashierChange = { selectedCashierKey = it },
                        onLotteryFilterChange = { lotteryFilter = it },
                        onQueryChange = { query = it.uppercase(Locale.getDefault()) },
                        onFromDateTimeChange = {
                            fromDateTime = it
                            selectedPreset = ""
                        },
                        onToDateTimeChange = {
                            toDateTime = it
                            selectedPreset = ""
                        },
                        onPresetSelected = { preset ->
                            val (fromValue, toValue) = applyQuickPreset(preset)
                            fromDateTime = fromValue
                            toDateTime = toValue
                            selectedPreset = preset.id
                        },
                        onClearDateFilters = {
                            fromDateTime = ""
                            toDateTime = ""
                            selectedPreset = ""
                        },
                    )
                }
                Spacer(modifier = Modifier.height(visual.sizes.sectionGap))
                MetricStrip(
                    items = listOf(
                        MetricStripItem(
                            label = "Tickets",
                            value = filteredGroups.size.toString(),
                            tone = visual.colors.ink,
                        ),
                        MetricStripItem(
                            label = "Monto visible",
                            value = formatTicketMoney(visibleAmount),
                            tone = visual.colors.gain,
                        ),
                    ),
                )
                Spacer(modifier = Modifier.height(visual.sizes.sectionGap))
                if (filteredGroups.isEmpty()) {
                    CompactEmptyState("Sin tickets para este filtro.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(layout.listSpacingDp.dp),
                        contentPadding = PaddingValues(bottom = visual.sizes.screenPaddingV),
                    ) {
                        items(filteredGroups, key = { it.id }) { group ->
                            TicketDetailListRow(
                                group = group,
                                actorLabelsByKey = directory.actorLabelsByKey,
                                onOpen = { onOpenTicket(group.ticket) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketDetailFilters(
    playTypeFilter: String,
    ownerScope: TicketOwnerScope,
    canFilterOwner: Boolean,
    selectedCashierKey: String,
    cashierOptions: List<CompactDropdownOption>,
    lotteryFilter: String,
    lotteryOptions: List<CompactDropdownOption>,
    playTypeOptions: List<CompactDropdownOption>,
    query: String,
    fromDateTime: String,
    toDateTime: String,
    selectedPreset: String,
    visibleCount: Int,
    onPlayTypeFilterChange: (String) -> Unit,
    onOwnerScopeChange: (TicketOwnerScope) -> Unit,
    onCashierChange: (String) -> Unit,
    onLotteryFilterChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onFromDateTimeChange: (String) -> Unit,
    onToDateTimeChange: (String) -> Unit,
    onPresetSelected: (TicketQuickDatePreset) -> Unit,
    onClearDateFilters: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar ticket, usuario, loteria o numero") },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactFilterDropdown(
                label = "Tipo",
                selectedValue = playTypeFilter,
                options = playTypeOptions,
                onValueSelected = onPlayTypeFilterChange,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TicketDateTimeField(
                label = "Desde",
                value = fromDateTime,
                onValueChange = onFromDateTimeChange,
                modifier = Modifier.weight(1f),
            )
            TicketDateTimeField(
                label = "Hasta",
                value = toDateTime,
                onValueChange = onToDateTimeChange,
                modifier = Modifier.weight(1f),
            )
        }
        TicketQuickPresetRow(
            selectedPreset = selectedPreset,
            onPresetSelected = onPresetSelected,
            onClear = onClearDateFilters,
        )
        if (canFilterOwner) {
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
private fun TicketDetailListRow(
    group: TicketDetailGroup,
    actorLabelsByKey: Map<String, String>,
    onOpen: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        alt = false,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = group.ticket.serial ?: group.ticket.id,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${ticketOwnerLabel(group.ticket, actorLabelsByKey)} · ${ticketDateTime(group.ticket.createdAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = ticketLotteriesLabel(group.ticket),
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                group.ticket.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.warning,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Total ticket",
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                )
                Text(
                    text = formatTicketMoney(group.ticket.total),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    color = visual.colors.gain,
                    fontWeight = FontWeight.Bold,
                )
                CompactStatusBadge(
                    label = ticketListStatusLabel(group.ticket.status),
                    tone = ticketStatusTone(group.ticket.status),
                )
                CompactActionButton(
                    label = "Ticket",
                    onClick = onOpen,
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    tone = ActionTone.Warning,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            group.plays.forEach { play ->
                TicketDetailPlayRow(play = play)
            }
        }
    }
}

@Composable
private fun TicketDetailPlayRow(play: com.lotterynet.pro.core.model.PlayItem) {
    val visual = rememberLotteryNetVisualSpec()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panelAlt.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = play.number,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = playTypeLabel(play.playType),
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.warning,
                    fontWeight = FontWeight.Bold,
                )
                play.lotteryName?.takeIf { it.isNotBlank() }?.let { lotteryName ->
                    Text(
                        text = lotteryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatTicketMoney(play.amount),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = visual.colors.gain,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
