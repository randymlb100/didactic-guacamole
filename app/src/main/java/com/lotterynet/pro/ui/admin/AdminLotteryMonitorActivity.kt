package com.lotterynet.pro.ui.admin

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.storage.LocalAdminLotteryConfigRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscription
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKeys
import com.lotterynet.pro.ui.common.CompactEmptyState
import com.lotterynet.pro.ui.common.CompactSegmentedSelector
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.CompactToggleSwitch
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactRecordRow
import com.lotterynet.pro.ui.common.MetricStrip
import com.lotterynet.pro.ui.common.MetricStripItem
import com.lotterynet.pro.ui.common.OperationalListHeader
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.sales.resolveSalesStartupSystemModeConfig
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AdminLotteryMonitorActivity : AppCompatActivity() {
    private val syncHandler = Handler(Looper.getMainLooper())
    private val realtimeClient = LotterynetRealtimeClient()
    private val realtimeSubscriptions = mutableListOf<LotterynetRealtimeClient.SubscriptionHandle>()
    private lateinit var session: ActiveSession
    private lateinit var salesRepository: LocalSalesRepository
    private lateinit var operationalSyncCoordinator: NativeOperationalSyncCoordinator
    private var ticketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var lastRemoteUpdatedAt: String? = null
    private val lotteryMonitorSyncInFlight = AtomicBoolean(false)
    private val syncPollRunnable = object : Runnable {
        override fun run() {
            syncLotteryMonitor(force = false)
            syncHandler.postDelayed(this, resolveAdminLotteryMonitorPollIntervalMs(realtimeClient.isConfigured()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.ADMIN_LOTTERY_MONITOR)) return
        session = activeSession ?: return
        LocalUsersRepository(this).touchSession(session)
        salesRepository = LocalSalesRepository(this)
        operationalSyncCoordinator = NativeOperationalSyncCoordinator(
            ticketGateway = NativeTicketCloudSyncCoordinator(
                salesRepository = salesRepository,
                queueRepository = NativeTicketSyncQueueRepository(this),
            ),
        )
        val catalog = StaticLotteryCatalogRepository().getAllLotteries()
        val visibleCatalog = filterMonitorLotteriesForSystemMode(
            catalog,
            resolveSalesStartupSystemModeConfig(
                session = session,
                usersRepository = LocalUsersRepository(this),
                adminLotteryConfigRepository = LocalAdminLotteryConfigRepository(this),
            ),
        )
        refreshLotteryMonitorData()

        setContent {
            LotteryNetComposeTheme {
                AdminLotteryMonitorRoute(
                    bancaName = session.banca ?: "LotteryNet",
                    tickets = ticketsState,
                    lotteries = visibleCatalog,
                    onBack = { finish() },
                    onRefresh = { syncLotteryMonitor(force = true) },
                )
            }
        }
        syncLotteryMonitor(force = true)
        subscribeRealtime()
        syncHandler.postDelayed(syncPollRunnable, resolveAdminLotteryMonitorPollIntervalMs(realtimeClient.isConfigured()))
    }

    override fun onDestroy() {
        syncHandler.removeCallbacks(syncPollRunnable)
        realtimeSubscriptions.forEach { it.close() }
        realtimeClient.shutdown()
        super.onDestroy()
    }

    private fun refreshLotteryMonitorData() {
        val visibleCatalog = filterMonitorLotteriesForSystemMode(
            StaticLotteryCatalogRepository().getAllLotteries(),
            resolveSalesStartupSystemModeConfig(
                session = session,
                usersRepository = LocalUsersRepository(this),
                adminLotteryConfigRepository = LocalAdminLotteryConfigRepository(this),
            ),
        )
        val visibleLotteryIds = visibleCatalog.mapTo(linkedSetOf()) { it.id }
        ticketsState = filterMonitorTicketsForLotteries(
            salesRepository.getAvailableDayKeys().flatMap(salesRepository::getTicketsForDay),
            visibleLotteryIds,
        )
    }

    private fun syncLotteryMonitor(force: Boolean) {
        if (!lotteryMonitorSyncInFlight.compareAndSet(false, true)) return
        thread(name = "admin-lottery-monitor-sync") {
            runCatching {
                operationalSyncCoordinator.syncTicketsForSession(
                    session = session,
                    lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                    force = force,
                )
            }.onSuccess { state ->
                lastRemoteUpdatedAt = state.remoteUpdatedAt ?: lastRemoteUpdatedAt
            }.also {
                lotteryMonitorSyncInFlight.set(false)
                runOnUiThread { refreshLotteryMonitorData() }
            }
        }
    }

    private fun subscribeRealtime() {
        if (!realtimeClient.isConfigured()) return
        resolveOperationalOwnerKeys(session).forEach { ownerKey ->
            realtimeSubscriptions += realtimeClient.subscribe(LotterynetRealtimeSubscription.ticketOwner(ownerKey)) {
                syncLotteryMonitor(force = true)
            }
        }
    }
}

private enum class LotteryMonitorRange(val label: String) {
    DAY("Día"),
    WEEK("Semana"),
    QUINCENA("Quincenal"),
    MONTH("Mensual"),
}

private enum class LotteryMonitorTab(val id: String, val label: String) {
    LOTTERIES("lotteries", "Loterías"),
    PLAYS("plays", "Números"),
    RANKING("ranking", "Ranking"),
    TOTALS("totals", "Totales"),
}

private enum class LotteryMonitorPlayFocus(val id: String, val label: String) {
    ALL("all", "Todo"),
    Q("q", "Q"),
    P("p", "Pale"),
    T("t", "Tripleta"),
    SP("sp", "SP"),
    PICK("pick", "Pick"),
}

private const val ADMIN_LOTTERY_MONITOR_POLL_MS = 60_000L
private const val ADMIN_LOTTERY_MONITOR_REALTIME_FALLBACK_POLL_MS = 300_000L

internal fun resolveAdminLotteryMonitorPollIntervalMs(realtimeEnabled: Boolean): Long {
    return if (realtimeEnabled) ADMIN_LOTTERY_MONITOR_REALTIME_FALLBACK_POLL_MS else ADMIN_LOTTERY_MONITOR_POLL_MS
}

private data class LotteryMonitorRow(
    val lotteryId: String,
    val lotteryName: String,
    val colorHex: String,
    val q: Double = 0.0,
    val p: Double = 0.0,
    val t: Double = 0.0,
    val sp: Double = 0.0,
    val pick3: Double = 0.0,
    val pick4: Double = 0.0,
    val other: Double = 0.0,
    val total: Double = 0.0,
)

@Composable
private fun AdminLotteryMonitorRoute(
    bancaName: String,
    tickets: List<TicketRecord>,
    lotteries: List<LotteryCatalogItem>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    var range by rememberSaveable { mutableStateOf(LotteryMonitorRange.DAY) }
    var tabId by rememberSaveable { mutableStateOf(LotteryMonitorTab.LOTTERIES.id) }
    var playFocusId by rememberSaveable { mutableStateOf(LotteryMonitorPlayFocus.ALL.id) }
    var selectedPlayViewName by rememberSaveable { mutableStateOf(LotteryMonitorPlayView.QUINIELA.name) }
    var highestFirst by rememberSaveable { mutableStateOf(true) }
    var showEmptyLotteries by rememberSaveable { mutableStateOf(false) }
    val filteredTickets = remember(tickets, range) { filterTicketsByRange(tickets, range) }
    val rows = remember(filteredTickets, lotteries, showEmptyLotteries) { buildLotteryRows(filteredTickets, lotteries, includeEmpty = showEmptyLotteries) }
    val visibleRows = remember(rows, playFocusId) { filterLotteryRowsByPlayFocus(rows, playFocusId) }
    val playViews = remember(lotteries) { resolveLotteryMonitorPlayViews(lotteries) }
    val selectedPlayView = remember(selectedPlayViewName, playViews) {
        resolveSelectedLotteryMonitorPlayView(selectedPlayViewName, playViews)
    }
    val numberRows = remember(filteredTickets, selectedPlayView, highestFirst) {
        val sorted = buildLotteryMonitorRows(
            tickets = filteredTickets,
            lotteryId = null,
            view = selectedPlayView,
        )
        if (highestFirst) sorted else sorted.sortedWith(compareBy<LotteryNumberMonitorRow> { it.amount }.thenBy { it.displayNumber })
    }
    val totalSales = remember(rows) { rows.sumOf { it.total } }
    val totalQ = remember(rows) { rows.sumOf { it.q } }
    val totalP = remember(rows) { rows.sumOf { it.p } }
    val totalT = remember(rows) { rows.sumOf { it.t } }
    val totalSP = remember(rows) { rows.sumOf { it.sp } }
    val totalPick3 = remember(rows) { rows.sumOf { it.pick3 } }
    val totalPick4 = remember(rows) { rows.sumOf { it.pick4 } }
    val totalOther = remember(rows) { rows.sumOf { it.other } }
    val visual = rememberLotteryNetVisualSpec()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
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
                LotteryMonitorCompactHeader(
                    title = "Monitoreo",
                    subtitle = "$bancaName · ${range.label}",
                    onBack = onBack,
                    onRefresh = onRefresh,
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CompactStatusBadge(label = "Sincronizado", tone = Color(0xFF059669))
                }
            }
            item {
                CompactSegmentedSelector(
                    options = LotteryMonitorTab.entries.map { QuickFilterChip(it.id, it.label) },
                    selectedId = tabId,
                    onSelected = { tabId = it },
                    columns = 4,
                )
            }
            item {
                CompactPanel {
                    OperationalListHeader(title = "Resumen", meta = "${filteredTickets.size} tickets")
                    MetricStrip(
                        items = listOf(
                            MetricStripItem("Ventas", monitorMoney(totalSales), MaterialTheme.colorScheme.primary),
                            MetricStripItem("Q/P/T", "${monitorMoney(totalQ)} / ${monitorMoney(totalP)} / ${monitorMoney(totalT)}", Color(0xFF0F766E)),
                            MetricStripItem("SP/Pick", "${monitorMoney(totalSP)} / ${monitorMoney(totalPick3 + totalPick4)}", Color(0xFF7C3AED)),
                        ),
                    )
                    CompactSegmentedSelector(
                        options = LotteryMonitorRange.entries.map { QuickFilterChip(it.name, it.label) },
                        selectedId = range.name,
                        onSelected = { id -> range = LotteryMonitorRange.entries.first { it.name == id } },
                        columns = 4,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Mostrar sin venta", style = MaterialTheme.typography.labelMedium, color = visual.colors.ink, fontWeight = FontWeight.Bold)
                            Text("Incluye loterías en $ 0", style = MaterialTheme.typography.labelSmall, color = visual.colors.muted)
                        }
                        CompactToggleSwitch(
                            checked = showEmptyLotteries,
                            onCheckedChange = { showEmptyLotteries = it },
                            tone = ActionTone.IntenseBlue,
                        )
                    }
                }
            }
            if (tabId == LotteryMonitorTab.LOTTERIES.id || tabId == LotteryMonitorTab.RANKING.id) {
                item {
                    CompactPanel(alt = true) {
                        OperationalListHeader(title = "Vista", meta = LotteryMonitorPlayFocus.entries.first { it.id == playFocusId }.label)
                        CompactSegmentedSelector(
                            options = LotteryMonitorPlayFocus.entries.map { QuickFilterChip(it.id, it.label) },
                            selectedId = playFocusId,
                            onSelected = { playFocusId = it },
                            columns = 3,
                        )
                    }
                }
                if (visibleRows.isEmpty()) {
                    item {
                        CompactEmptyState("Sin tickets vendidos en este corte.")
                    }
                } else {
                    item {
                        OperationalListHeader(title = "Ventas por lotería", meta = "${visibleRows.size} visibles")
                    }
                    items(visibleRows, key = { it.lotteryId }) { row ->
                        LotterySalesRow(row = row, totalSales = totalSales, playFocusId = playFocusId)
                    }
                }
            }
            if (tabId == LotteryMonitorTab.PLAYS.id) {
                item {
                    CompactPanel(alt = true) {
                        OperationalListHeader(title = "Números jugados", meta = selectedPlayView.label)
                        CompactSegmentedSelector(
                            options = playViews.map { QuickFilterChip(it.name, it.label) },
                            selectedId = selectedPlayView.name,
                            onSelected = { id -> playViews.firstOrNull { it.name == id }?.let { selectedPlayViewName = it.name } },
                            columns = if (playViews.size <= 2) 2 else 3,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Mayor apuesta primero", style = MaterialTheme.typography.labelMedium, color = visual.colors.ink, fontWeight = FontWeight.Bold)
                                Text("Cambia a menor apuesta apagando", style = MaterialTheme.typography.labelSmall, color = visual.colors.muted)
                            }
                            CompactToggleSwitch(checked = highestFirst, onCheckedChange = { highestFirst = it }, tone = ActionTone.IntenseBlue)
                        }
                    }
                }
                if (numberRows.isEmpty()) {
                    item { CompactEmptyState("No hay números jugados para ${selectedPlayView.label}.") }
                } else {
                    item {
                        OperationalListHeader(title = if (highestFirst) "Mayor apuesta" else "Menor apuesta", meta = "${numberRows.size} números")
                    }
                    items(numberRows, key = { "${selectedPlayView.name}-${it.displayNumber}" }) { row ->
                        LotteryNumberRankingRow(row = row)
                    }
                }
            }
            if (tabId == LotteryMonitorTab.TOTALS.id) {
            item {
                CompactPanel(alt = true) {
                    OperationalListHeader(title = "Totales por tipo", meta = monitorMoney(totalSales))
                    LotteryTypeRow("Quinielas", totalQ, Color(0xFF059669))
                    LotteryTypeRow("Pale", totalP, Color(0xFF2563EB))
                    LotteryTypeRow("Tripleta", totalT, Color(0xFFD97706))
                    LotteryTypeRow("Super Pale", totalSP, Color(0xFF7C3AED))
                    LotteryTypeRow("Pick 3", totalPick3, Color(0xFF155BD6))
                    LotteryTypeRow("Pick 4", totalPick4, Color(0xFF155BD6))
                    if (totalOther > 0.0) {
                        LotteryTypeRow("Otros", totalOther, visual.colors.ink)
                    }
                    LotteryTypeRow("Total ventas", totalSales, MaterialTheme.colorScheme.primary, emphasized = true)
                }
            }
            }
        }
    }
    }
}

@Composable
private fun LotteryNumberRankingRow(row: LotteryNumberMonitorRow) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(Color(0xFFE6EEFF), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(row.displayNumber, style = MaterialTheme.typography.titleMedium, color = Color(0xFF155BD6), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Apostado ${monitorMoney(row.amount)}", style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontWeight = FontWeight.Bold)
                val limitLine = row.remainingAmount?.let { remaining ->
                    "Cajeros ${monitorMoney(row.cashierAmount)} · queda ${monitorMoney(remaining)}"
                }
                Text(limitLine ?: "${row.playsCount} jugadas · ${row.actors.take(3).joinToString(", ").ifBlank { "sin cajero" }}", style = MaterialTheme.typography.labelSmall, color = visual.colors.muted, maxLines = 1)
            }
            CompactStatusBadge(label = "${row.playsCount}", tone = gainColor())
        }
    }
}

@Composable
private fun LotteryMonitorCompactHeader(
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
private fun LotteryMonitorMetric(
    modifier: Modifier,
    label: String,
    value: String,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(modifier = modifier, alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = visual.colors.muted)
        Text(value, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun LotterySalesRow(
    row: LotteryMonitorRow,
    totalSales: Double,
    playFocusId: String,
) {
    val share = if (totalSales > 0.0) row.total / totalSales else 0.0
    val accent = safeColor(row.colorHex)
    val focusAmount = lotteryRowFocusAmount(row, playFocusId)
    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 9.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.QueryStats, contentDescription = null, tint = accent)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.lotteryName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(
                    "Q ${monitorMoney(row.q)} · P ${monitorMoney(row.p)} · T ${monitorMoney(row.t)} · SP ${monitorMoney(row.sp)} · Pick ${monitorMoney(row.pick3 + row.pick4)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (playFocusId == LotteryMonitorPlayFocus.ALL.id) "Total" else "Vista", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(monitorMoney(focusAmount), style = MaterialTheme.typography.titleSmall, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text("${"%.1f".format(Locale.US, share * 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CompactToggleSwitch(checked = row.total > 0.0, onCheckedChange = {}, enabled = false, tone = ActionTone.Success)
        }
    }
}

@Composable
private fun LotteryTypeRow(
    label: String,
    value: Double,
    color: Color,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (emphasized) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(monitorMoney(value), style = MaterialTheme.typography.titleSmall, color = color, fontFamily = FontFamily.Monospace)
    }
}

private fun filterTicketsByRange(
    tickets: List<TicketRecord>,
    range: LotteryMonitorRange,
): List<TicketRecord> {
    val zone = ZoneId.of("America/Santo_Domingo")
    val today = LocalDate.now(zone)
    val from = when (range) {
        LotteryMonitorRange.DAY -> today
        LotteryMonitorRange.WEEK -> today.minusDays(6)
        LotteryMonitorRange.QUINCENA -> today.minusDays(14)
        LotteryMonitorRange.MONTH -> today.minusDays(29)
    }
    return tickets.filterNot { it.status.equals("voided", true) || it.status.equals("invalid", true) }
        .filter { ticket ->
            val date = java.time.Instant.ofEpochMilli(ticket.createdAtEpochMs).atZone(zone).toLocalDate()
            !date.isBefore(from) && !date.isAfter(today)
        }
}

private fun buildLotteryRows(
    tickets: List<TicketRecord>,
    lotteries: List<LotteryCatalogItem>,
    includeEmpty: Boolean = false,
): List<LotteryMonitorRow> {
    val rows = linkedMapOf<String, LotteryMonitorRow>()
    lotteries.forEach { lottery ->
        rows[lottery.id] = LotteryMonitorRow(
            lotteryId = lottery.id,
            lotteryName = lottery.name,
            colorHex = lottery.colorHex,
        )
    }

    tickets.forEach { ticket ->
        ticket.plays.forEach { play ->
            val lotteryId = play.lotteryId ?: return@forEach
            rows[lotteryId] = mergePlayIntoLottery(rows[lotteryId] ?: return@forEach, play.playType, play.amount)
            if (play.playType.equals("SP", true)) {
                play.secondaryLotteryId?.let { secondaryId ->
                    rows[secondaryId]?.let { secondary ->
                        rows[secondaryId] = mergePlayIntoLottery(secondary, play.playType, play.amount)
                    }
                }
            }
        }
    }

    return rows.values
        .filter { includeEmpty || it.total > 0.0 }
        .sortedByDescending { it.total }
}

private fun safeColor(hex: String): Color {
    return runCatching {
        Color(android.graphics.Color.parseColor(hex))
    }.getOrDefault(Color(0xFF475569))
}

private fun monitorMoney(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)

private fun mergePlayIntoLottery(
    row: LotteryMonitorRow,
    playType: String,
    amount: Double,
): LotteryMonitorRow {
    return when (playType.uppercase(Locale.ROOT)) {
        "Q" -> row.copy(q = row.q + amount, total = row.total + amount)
        "P" -> row.copy(p = row.p + amount, total = row.total + amount)
        "T" -> row.copy(t = row.t + amount, total = row.total + amount)
        "SP" -> row.copy(sp = row.sp + amount, total = row.total + amount)
        "P3", "P3BOX", "P3B" -> row.copy(pick3 = row.pick3 + amount, total = row.total + amount)
        "P4", "P4BOX", "P4B" -> row.copy(pick4 = row.pick4 + amount, total = row.total + amount)
        else -> row.copy(other = row.other + amount, total = row.total + amount)
    }
}

private fun filterLotteryRowsByPlayFocus(
    rows: List<LotteryMonitorRow>,
    playFocusId: String,
): List<LotteryMonitorRow> {
    return rows.filter { row -> lotteryRowFocusAmount(row, playFocusId) > 0.0 || row.total == 0.0 }
}

private fun lotteryRowFocusAmount(row: LotteryMonitorRow, playFocusId: String): Double {
    return when (playFocusId) {
        LotteryMonitorPlayFocus.Q.id -> row.q
        LotteryMonitorPlayFocus.P.id -> row.p
        LotteryMonitorPlayFocus.T.id -> row.t
        LotteryMonitorPlayFocus.SP.id -> row.sp
        LotteryMonitorPlayFocus.PICK.id -> row.pick3 + row.pick4
        else -> row.total
    }
}
