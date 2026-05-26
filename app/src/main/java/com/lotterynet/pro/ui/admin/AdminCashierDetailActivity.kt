package com.lotterynet.pro.ui.admin

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Whatsapp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.isPaidTicketStatus
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscription
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactEmptyState
import com.lotterynet.pro.ui.common.CompactKeyValueRow
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.MetricStrip
import com.lotterynet.pro.ui.common.MetricStripItem
import com.lotterynet.pro.ui.common.OperationalListHeader
import com.lotterynet.pro.ui.common.CompactRecordRow
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
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

class AdminCashierDetailActivity : AppCompatActivity() {
    private val syncHandler = Handler(Looper.getMainLooper())
    private val realtimeClient = LotterynetRealtimeClient()
    private val realtimeSubscriptions = mutableListOf<LotterynetRealtimeClient.SubscriptionHandle>()
    private val cashierDetailSyncInFlight = AtomicBoolean(false)
    private lateinit var actorId: String
    private lateinit var actorUser: String
    private lateinit var actorLabel: String
    private lateinit var bancaName: String
    private var session: ActiveSession? = null
    private lateinit var dayKey: String
    private lateinit var salesRepository: LocalSalesRepository
    private lateinit var financeRepository: LocalFinanceRepository
    private lateinit var operationalSyncCoordinator: NativeOperationalSyncCoordinator
    private var summaryState by mutableStateOf(FinanceSummary())
    private var ticketsState by mutableStateOf<List<TicketRecord>>(emptyList())
    private var syncMessageState by mutableStateOf("Sincronizando cajero...")
    private var lastRemoteUpdatedAt: String? = null
    private val syncPollRunnable = object : Runnable {
        override fun run() {
            syncCashierDetail(force = false)
            syncHandler.postDelayed(this, resolveCashierDetailPollIntervalMs(realtimeClient.isConfigured()))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actorId = intent?.getStringExtra(EXTRA_ACTOR_ID).orEmpty()
        actorUser = intent?.getStringExtra(EXTRA_ACTOR_USER).orEmpty()
        actorLabel = intent?.getStringExtra(EXTRA_ACTOR_LABEL).orEmpty().ifBlank { actorUser }
        bancaName = intent?.getStringExtra(EXTRA_BANCA_NAME).orEmpty().ifBlank { "LotteryNet" }
        if (actorId.isBlank()) {
            finish()
            return
        }
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.ADMIN_MONITOR)) return
        session = activeSession
        LocalUsersRepository(this).touchLastSeen(actorId)
        dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date())
        salesRepository = LocalSalesRepository(this)
        financeRepository = LocalFinanceRepository(
            salesRepository = salesRepository,
            rechargeRepository = LocalRechargeRepository(this),
            usersRepository = LocalUsersRepository(this),
        )
        operationalSyncCoordinator = NativeOperationalSyncCoordinator(
            ticketGateway = NativeTicketCloudSyncCoordinator(
                salesRepository = salesRepository,
                queueRepository = NativeTicketSyncQueueRepository(this),
            ),
        )
        refreshCashierDetailData()

        setContent {
            LotteryNetComposeTheme {
                AdminCashierDetailRoute(
                    bancaName = bancaName,
                    dayKey = dayKey,
                    actorLabel = actorLabel,
                    actorUser = actorUser,
                    summary = summaryState,
                    tickets = ticketsState,
                    syncMessage = syncMessageState,
                    onBack = { finish() },
                    onShare = { whatsappOnly ->
                        shareCashierDetail(
                            bancaName = bancaName,
                            dayKey = dayKey,
                            actorLabel = actorLabel,
                            actorUser = actorUser,
                            summary = summaryState,
                            tickets = ticketsState,
                            session = session,
                            whatsappOnly = whatsappOnly,
                        )
                    },
                    onPrint = {
                        val bitmap = NativeBitmapExport.renderCashierDetailBitmap(
                            bancaName = bancaName,
                            dayKey = dayKey,
                            actorLabel = actorLabel,
                            actorUser = actorUser,
                            summary = summaryState,
                            tickets = ticketsState,
                        )
                        NativeBitmapExport.printBitmap(this, bitmap, "detalle-cajero-$actorId-$dayKey")
                    },
                    onSave = {
                        val bitmap = NativeBitmapExport.renderCashierDetailBitmap(
                            bancaName = bancaName,
                            dayKey = dayKey,
                            actorLabel = actorLabel,
                            actorUser = actorUser,
                            summary = summaryState,
                            tickets = ticketsState,
                        )
                        NativeBitmapExport.saveBitmapToDownloads(this, bitmap, "detalle-cajero-$actorId-$dayKey.png")
                    },
                    onOpenTicket = { ticket ->
                        startActivity(Intent(this, TicketOfficialActivity::class.java).apply {
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_ID, ticket.id)
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_EPOCH, ticket.createdAtEpochMs)
                            putExtra(TicketOfficialActivity.EXTRA_BANCA_NAME, bancaName)
                        })
                    },
                )
            }
        }
        syncCashierDetail(force = true)
        subscribeRealtime()
        syncHandler.postDelayed(syncPollRunnable, resolveCashierDetailPollIntervalMs(realtimeClient.isConfigured()))
    }

    override fun onDestroy() {
        syncHandler.removeCallbacks(syncPollRunnable)
        realtimeSubscriptions.forEach { it.close() }
        realtimeSubscriptions.clear()
        realtimeClient.shutdown()
        super.onDestroy()
    }

    private fun refreshCashierDetailData() {
        summaryState = financeRepository.getActorSummary(dayKey, actorId, actorLabel).summary
        ticketsState = salesRepository.getTicketsForActor(dayKey, actorId).sortedByDescending { it.createdAtEpochMs }
    }

    private fun syncCashierDetail(force: Boolean) {
        val active = session ?: return
        if (!cashierDetailSyncInFlight.compareAndSet(false, true)) return
        thread(name = "cashier-detail-sync") {
            runCatching {
                operationalSyncCoordinator.syncTicketsForSession(
                    session = active,
                    lastRemoteUpdatedAt = lastRemoteUpdatedAt,
                    force = force,
                )
            }.onSuccess { state ->
                lastRemoteUpdatedAt = state.remoteUpdatedAt ?: lastRemoteUpdatedAt
                runOnUiThread {
                    refreshCashierDetailData()
                    syncMessageState = state.message
                }
            }.onFailure { error ->
                runOnUiThread {
                    refreshCashierDetailData()
                    syncMessageState = error.message ?: "No se pudo sincronizar cajero."
                }
            }.also {
                cashierDetailSyncInFlight.set(false)
            }
        }
    }

    private fun subscribeRealtime() {
        if (!realtimeClient.isConfigured()) return
        val ownerKey = resolveOperationalOwnerKey(session)
        if (ownerKey.isBlank()) return
        realtimeSubscriptions += realtimeClient.subscribe(LotterynetRealtimeSubscription.ticketOwner(ownerKey)) {
            syncCashierDetail(force = true)
        }
    }

    private fun shareCashierDetail(
        bancaName: String,
        dayKey: String,
        actorLabel: String,
        actorUser: String,
        summary: com.lotterynet.pro.core.finance.FinanceSummary,
        tickets: List<TicketRecord>,
        session: ActiveSession?,
        whatsappOnly: Boolean,
    ) {
        val bitmap = NativeBitmapExport.renderCashierDetailBitmap(
            bancaName = bancaName,
            dayKey = dayKey,
            actorLabel = actorLabel,
            actorUser = actorUser,
            summary = summary,
            tickets = tickets,
        )
        NativeBitmapExport.shareBitmap(
            context = this,
            bitmap = bitmap,
            fileName = "detalle-cajero-${actorUser.ifBlank { actorLabel }}-$dayKey.png",
            title = "Detalle por cajero",
            text = buildCashierDetailShareText(
                bancaName = bancaName,
                dayKey = dayKey,
                actorLabel = actorLabel,
                actorUser = actorUser,
                summary = summary,
                tickets = tickets,
                session = session,
            ),
            whatsappOnly = whatsappOnly,
        )
    }

    companion object {
        const val EXTRA_ACTOR_ID = "admin_cashier_actor_id"
        const val EXTRA_ACTOR_USER = "admin_cashier_actor_user"
        const val EXTRA_ACTOR_LABEL = "admin_cashier_actor_label"
        const val EXTRA_BANCA_NAME = "admin_cashier_banca_name"
    }
}

internal const val CASHIER_DETAIL_POLL_MS = 60_000L
internal const val CASHIER_DETAIL_REALTIME_FALLBACK_POLL_MS = 300_000L

internal fun resolveCashierDetailPollIntervalMs(realtimeEnabled: Boolean): Long {
    return if (realtimeEnabled) CASHIER_DETAIL_REALTIME_FALLBACK_POLL_MS else CASHIER_DETAIL_POLL_MS
}

@Composable
private fun AdminCashierDetailRoute(
    bancaName: String,
    dayKey: String,
    actorLabel: String,
    actorUser: String,
    summary: com.lotterynet.pro.core.finance.FinanceSummary,
    tickets: List<TicketRecord>,
    syncMessage: String,
    onBack: () -> Unit,
    onShare: (Boolean) -> Unit,
    onPrint: () -> Boolean,
    onSave: () -> Boolean,
    onOpenTicket: (TicketRecord) -> Unit,
) {
    var statusMessage by rememberSaveable { mutableStateOf("Resumen local del cajero con salida operativa para cerrar el bloque admin.") }
    val ticketCountLabel by remember(summary.ticketsCount, summary.pagados, summary.anuladosCount, summary.premiosPendientes) {
        mutableStateOf(
            "Tickets ${summary.ticketsCount} · Pagados ${summary.pagados} · Anulados ${summary.anuladosCount} · Pendientes ${detailMoney(summary.premiosPendientes)}",
        )
    }
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
                ScreenHeaderPanel(
                    title = actorLabel,
                    subtitle = "$actorUser · $bancaName · $dayKey · $syncMessage",
                    onBack = onBack,
                    badgeLabel = "${tickets.size} tickets",
                    badgeTone = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                CompactPanel {
                    OperationalListHeader(title = "Estado", meta = "Caja y ventas")
                    Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                    MetricStrip(
                        items = listOf(
                            MetricStripItem("Ventas", detailMoney(summary.ventas), visual.colors.ink),
                            MetricStripItem("Recargas", detailMoney(summary.recargas), Color(0xFF1D4ED8)),
                            MetricStripItem("Caja", detailMoney(summary.cajaDisponible), Color(0xFF4F46E5)),
                        ),
                    )
                    CompactKeyValueRow("Tickets", summary.ticketsCount.toString())
                    CompactKeyValueRow("Pagados", summary.pagados.toString())
                    CompactKeyValueRow("Anulados", summary.anuladosCount.toString())
                    CompactKeyValueRow("Premios pendientes", detailMoney(summary.premiosPendientes), tone = MaterialTheme.colorScheme.error)
                }
            }
            item {
                CompactPanel {
                    SectionHeader(title = "Acciones sensibles", meta = "Exportar y compartir")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactActionButton(
                            "Imprimir",
                            onClick = {
                                val opened = onPrint()
                                statusMessage = if (opened) "Flujo de impresión abierto." else "No se pudo abrir impresión."
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Print,
                        )
                        CompactActionButton(
                            "WhatsApp",
                            onClick = {
                                onShare(true)
                                statusMessage = "Enviando detalle por WhatsApp."
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Whatsapp,
                        )
                        CompactActionButton(
                            "Compartir",
                            onClick = {
                                onShare(false)
                                statusMessage = "Abriendo compartir."
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Share,
                            active = true,
                        )
                        CompactActionButton(
                            "Guardar",
                            onClick = {
                                val saved = onSave()
                                statusMessage = if (saved) "Detalle guardado en Descargas." else "No se pudo guardar el detalle."
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Download,
                        )
                    }
                }
            }
            item {
                CompactPanel {
                    OperationalListHeader(title = "Tickets del día", meta = ticketCountLabel)
                }
            }
            if (tickets.isEmpty()) {
                item {
                    CompactEmptyState("No hay tickets del cajero hoy.")
                }
            } else {
                items(tickets, key = { it.id }) { ticket ->
                    TicketMiniRow(ticket = ticket, onOpen = { onOpenTicket(ticket) })
                }
            }
        }
    }
    }
}

@Composable
private fun DetailMetric(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(modifier = modifier, alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = visual.colors.ink)
            Text(label, style = MaterialTheme.typography.labelMedium, color = visual.colors.muted)
            Text(value, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun TicketMiniRow(
    ticket: TicketRecord,
    onOpen: () -> Unit,
) {
    CompactRecordRow(
        title = ticket.serial ?: ticket.id,
        subtitle = "${formatMiniTime(ticket.createdAtEpochMs)} · ${ticket.plays.size} jugadas",
        meta = detailMoney(ticket.total),
        badgeLabel = miniStatus(ticket.status),
        badgeTone = ticketTone(ticket.status),
        onClick = onOpen,
    )
}

private fun formatMiniTime(epochMs: Long): String {
    val format = SimpleDateFormat("hh:mm a", Locale.US)
    format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(Date(epochMs))
}

private fun miniStatus(status: String): String {
    if (isPaidTicketStatus(status)) return "Pagado"
    return when (status.lowercase(Locale.getDefault())) {
        "winner" -> "Ganador"
        "voided", "invalid" -> "Anulado"
        else -> "Activo"
    }
}

private fun ticketTone(status: String): Color {
    if (isPaidTicketStatus(status)) return Color(0xFF2563EB)
    return when (status.lowercase(Locale.getDefault())) {
        "winner" -> Color(0xFFD97706)
        "voided", "invalid" -> Color(0xFFB91C1C)
        else -> Color(0xFF64748B)
    }
}

private fun detailMoney(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)

private fun buildCashierDetailShareText(
    bancaName: String,
    dayKey: String,
    actorLabel: String,
    actorUser: String,
    summary: com.lotterynet.pro.core.finance.FinanceSummary,
    tickets: List<TicketRecord>,
    session: ActiveSession?,
): String {
    val roleLabel = when (session?.role) {
        UserRole.ADMIN -> "Admin"
        UserRole.CASHIER -> "Cajero"
        else -> "Operador"
    }
    val header = buildString {
        append("Detalle por cajero")
        append('\n')
        append("$actorLabel · ${actorUser.ifBlank { "sin usuario" }}")
        append('\n')
        append("$bancaName · $dayKey · generado por $roleLabel")
        append('\n')
        append("Ventas ${detailMoney(summary.ventas)} · Recargas ${detailMoney(summary.recargas)} · Caja ${detailMoney(summary.cajaDisponible)}")
        append('\n')
        append("Tickets ${summary.ticketsCount} · Pagados ${summary.pagados} · Anulados ${summary.anuladosCount} · Pendientes ${detailMoney(summary.premiosPendientes)}")
    }
    val lines = tickets.take(12).mapIndexed { index, ticket ->
        "${index + 1}. ${(ticket.serial ?: ticket.id)} · ${formatMiniTime(ticket.createdAtEpochMs)} · ${miniStatus(ticket.status)} · ${detailMoney(ticket.total)}"
    }
    val overflow = if (tickets.size > 12) "\n... y ${tickets.size - 12} ticket(s) más" else ""
    return buildString {
        append(header)
        if (lines.isNotEmpty()) {
            append("\n\n")
            append(lines.joinToString("\n"))
            append(overflow)
        }
    }
}
