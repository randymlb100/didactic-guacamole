package com.lotterynet.pro.ui.report

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lotterynet.pro.core.finance.FinanceActorPeriodRow
import com.lotterynet.pro.core.finance.FinancePeriodPreset
import com.lotterynet.pro.core.finance.FinancePeriodReport
import com.lotterynet.pro.core.finance.FinancePeriodRow
import com.lotterynet.pro.core.finance.FinanceResolvedRange
import com.lotterynet.pro.core.finance.FinanceScope
import com.lotterynet.pro.core.finance.FinanceScopeType
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.finance.OperationalReportActorFilter
import com.lotterynet.pro.core.finance.OperationalReportManualTarget
import com.lotterynet.pro.core.finance.OperationalReportRemoteLoadResult
import com.lotterynet.pro.core.finance.OperationalReportSyncStatus
import com.lotterynet.pro.core.finance.OperationalReportViewState
import com.lotterynet.pro.core.finance.buildOperationalReportActorFilters
import com.lotterynet.pro.core.finance.buildOperationalReportShareText
import com.lotterynet.pro.core.finance.buildOperationalReportViewState
import com.lotterynet.pro.core.finance.operationalReportCommissionPercent
import com.lotterynet.pro.core.finance.operationalReportMoney
import com.lotterynet.pro.core.finance.resolveFinanceRemoteRefreshDecision
import com.lotterynet.pro.core.finance.resolveOperationalReportNet
import com.lotterynet.pro.core.finance.updateOperationalReportManualRange
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.sync.CashierLimitCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeRechargeCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.NativeUsersBootstrapper
import com.lotterynet.pro.core.sync.LocalSyncFreshnessRepository
import com.lotterynet.pro.core.sync.SyncFreshnessState
import com.lotterynet.pro.core.sync.SyncFreshnessType
import com.lotterynet.pro.core.sync.buildSyncFreshnessKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.ScreenChromeAction
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

internal data class OperationalReportLayoutContract(
    val compactHeader: Boolean,
    val useDenseRows: Boolean,
    val inlineTotals: Boolean,
    val showChart: Boolean,
    val showLedgerStrip: Boolean,
    val metricPaddingVerticalDp: Int,
)

internal fun resolveOperationalReportLayout(windowMode: LotteryNetWindowMode): OperationalReportLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT,
        LotteryNetWindowMode.POS -> OperationalReportLayoutContract(
            compactHeader = true,
            useDenseRows = true,
            inlineTotals = true,
            showChart = false,
            showLedgerStrip = true,
            metricPaddingVerticalDp = 5,
        )
        LotteryNetWindowMode.TABLET,
        LotteryNetWindowMode.WIDE -> OperationalReportLayoutContract(
            compactHeader = false,
            useDenseRows = false,
            inlineTotals = true,
            showChart = false,
            showLedgerStrip = true,
            metricPaddingVerticalDp = 6,
        )
    }
}

internal fun operationalReportVisibleServerLabels(): List<String> = listOf(
    "Actualizar servidor",
    "Actualizado",
    "Sin conexión usando última copia",
    "No se pudo cargar servidor",
)

internal data class OperationalReportMetricSpec(
    val label: String,
    val value: Double,
    val tone: String,
    val bold: Boolean = false,
)

internal fun resolveOperationalReportPrimaryResultLabel(isSupervisorCommissionReport: Boolean): String {
    return if (isSupervisorCommissionReport) "Comisión supervisión" else "Beneficio"
}

internal fun isSupervisorCommissionReport(
    session: ActiveSession,
    filter: OperationalReportActorFilter,
): Boolean {
    return session.role == UserRole.SUPERVISOR || filter is OperationalReportActorFilter.Supervisor
}

internal fun buildOperationalReportMetricSpecs(
    summary: FinanceSummary,
    isSupervisorCommissionReport: Boolean = false,
): List<OperationalReportMetricSpec> {
    val net = resolveOperationalReportNet(summary)
    val resultValue = if (isSupervisorCommissionReport) summary.supervisorComision else net
    return listOf(
        OperationalReportMetricSpec("Venta", summary.ventas, "ink", bold = true),
        OperationalReportMetricSpec("Recarga", summary.recargas, "ink", bold = true),
        OperationalReportMetricSpec("Comisión", summary.comision, "loss"),
        OperationalReportMetricSpec("Premio", summary.premiosPagados, "warning"),
        OperationalReportMetricSpec("Caja", summary.cajaDisponible, "ink"),
        OperationalReportMetricSpec(
            resolveOperationalReportPrimaryResultLabel(isSupervisorCommissionReport),
            resultValue,
            when {
                resultValue > 0.0 -> "gain"
                resultValue < 0.0 -> "loss"
                else -> "ink"
            },
            bold = true,
        ),
    )
}

class OperationalReportActivity : AppCompatActivity() {
    private lateinit var session: ActiveSession
    private lateinit var usersRepository: LocalUsersRepository
    private lateinit var salesRepository: LocalSalesRepository
    private lateinit var rechargeRepository: LocalRechargeRepository
    private lateinit var financeRepository: LocalFinanceRepository
    private lateinit var freshnessRepository: LocalSyncFreshnessRepository
    private lateinit var dayKey: String

    private var actorFiltersState by mutableStateOf<List<OperationalReportActorFilter>>(listOf(OperationalReportActorFilter.All))
    private var selectedFilterState by mutableStateOf<OperationalReportActorFilter>(OperationalReportActorFilter.All)
    private var selectedPeriodState by mutableStateOf(OperationalReportPeriod.TODAY)
    private var fromDayState by mutableStateOf("")
    private var toDayState by mutableStateOf("")
    private var manualTargetState by mutableStateOf(OperationalReportManualTarget.FROM)
    private var reportState by mutableStateOf<OperationalReportViewState?>(null)
    private var loadingState by mutableStateOf(true)
    private var messageState by mutableStateOf("Datos locales listos")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.OPERATIONAL_REPORT)) return
        session = activeSession ?: return
        usersRepository = LocalUsersRepository(this)
        salesRepository = LocalSalesRepository(this)
        rechargeRepository = LocalRechargeRepository(this)
        financeRepository = LocalFinanceRepository(
            salesRepository = salesRepository,
            rechargeRepository = rechargeRepository,
            usersRepository = usersRepository,
        )
        freshnessRepository = LocalSyncFreshnessRepository(this)
        dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date())
        fromDayState = dayKey
        toDayState = dayKey

        setContent {
            LotteryNetComposeTheme {
                OperationalReportRoute(
                    session = session,
                    dayKey = dayKey,
                    filters = actorFiltersState,
                    selectedFilter = selectedFilterState,
                    selectedPeriod = selectedPeriodState,
                    fromDay = fromDayState,
                    toDay = toDayState,
                    manualTarget = manualTargetState,
                    loading = loadingState,
                    message = messageState,
                    report = reportState,
                    onBack = { finish() },
                    onRefresh = { refreshReport(forceRemote = true) },
                    onShare = { shareReport() },
                    onFilterSelected = { filter ->
                        selectedFilterState = filter
                        refreshReport(forceRemote = false)
                    },
                    onPeriodSelected = { period ->
                        selectedPeriodState = period
                        refreshReport(forceRemote = false)
                    },
                    onManualTargetSelected = { target -> manualTargetState = target },
                    onManualDaySelected = { day ->
                        val range = updateOperationalReportManualRange(
                            fromDayKey = fromDayState,
                            toDayKey = toDayState,
                            selectedDayKey = day,
                            target = manualTargetState,
                        )
                        fromDayState = range.fromDayKey
                        toDayState = range.toDayKey
                    },
                    onApplyManual = { refreshReport(forceRemote = false) },
                )
            }
        }

        refreshReport(forceRemote = false)
    }

    private fun refreshReport(forceRemote: Boolean) {
        loadingState = true
        messageState = if (forceRemote) "Cargando desde servidor..." else "Datos locales listos"
        val appContext = applicationContext
        thread(name = "operational-report-refresh") {
            val ownerKey = resolveOperationalOwnerKey(session)
            val filters = buildOperationalReportActorFilters(
                session = session,
                cashiers = usersRepository.getCashiers(),
                supervisors = usersRepository.getSupervisors(),
            )
            val safeFilter = filters.firstOrNull { it.key == selectedFilterState.key } ?: OperationalReportActorFilter.All
            val preset = selectedPeriodState.toPreset()
            val cacheKey = buildSyncFreshnessKey(
                type = SyncFreshnessType.REPORT_PERIOD,
                ownerKey = ownerKey,
                banca = session.banca,
                dateKey = buildReportFreshnessDateKey(preset, safeFilter),
            )
            val localSummary = reportCacheProbe(preset, safeFilter)
            val decision = resolveFinanceRemoteRefreshDecision(
                hasLocalData = reportSummaryHasData(localSummary),
                forceRemote = forceRemote,
                selectedDayKey = if (preset == FinancePeriodPreset.CALENDAR) toDayState else dayKey,
                todayDayKey = dayKey,
                freshnessRecord = freshnessRepository.getRecord(cacheKey),
                nowEpochMs = System.currentTimeMillis(),
            )
            val remoteSucceeded = if (decision.shouldRefreshRemote) {
                val usersResult = NativeUsersBootstrapper(usersRepository).bootstrap(forceRemoteRefresh = forceRemote)
                CashierLimitCloudSyncCoordinator(
                    LocalCashierSalesLimitRepository(appContext),
                ).pullOwner(ownerKey)
                val ticketResult = NativeOperationalSyncCoordinator(
                    ticketGateway = NativeTicketCloudSyncCoordinator(
                        salesRepository = salesRepository,
                        queueRepository = NativeTicketSyncQueueRepository(appContext),
                    ),
                ).syncTicketsForSession(session = session, force = forceRemote)
                val rechargeResult = NativeRechargeCloudSyncCoordinator(rechargeRepository).hydrateOwner(ownerKey)
                usersResult.ok && ticketResult.ok && rechargeResult.ok
            } else {
                false
            }
            val refreshedLocalSummary = reportCacheProbe(preset, safeFilter)
            if (decision.shouldRefreshRemote && remoteSucceeded) {
                freshnessRepository.mark(cacheKey, SyncFreshnessState.SERVER_UPDATED)
            } else if (decision.shouldRefreshRemote && reportSummaryHasData(refreshedLocalSummary)) {
                freshnessRepository.mark(cacheKey, SyncFreshnessState.SERVER_FAILED_USING_CACHE)
            }
            val report = buildOperationalReportViewState(
                repository = financeRepository,
                session = session,
                preset = preset,
                anchorDayKey = dayKey,
                fromDayKey = if (preset == FinancePeriodPreset.CALENDAR) fromDayState else null,
                toDayKey = if (preset == FinancePeriodPreset.CALENDAR) toDayState else null,
                filter = safeFilter,
                syncStatus = resolveReportStatus(
                    remoteSucceeded = remoteSucceeded,
                    summary = refreshedLocalSummary,
                ),
            )
            runOnUiThread {
                actorFiltersState = filters.ifEmpty { listOf(OperationalReportActorFilter.All) }
                selectedFilterState = safeFilter
                reportState = report
                messageState = when {
                    !decision.shouldRefreshRemote -> "Datos locales listos"
                    remoteSucceeded -> "Actualizado desde servidor"
                    reportSummaryHasData(refreshedLocalSummary) -> "Sin conexión usando última copia"
                    else -> "No se pudo cargar servidor"
                }
                loadingState = false
            }
        }
    }

    private fun buildReportFreshnessDateKey(
        preset: FinancePeriodPreset,
        filter: OperationalReportActorFilter,
    ): String {
        return listOf(
            preset.name,
            if (preset == FinancePeriodPreset.CALENDAR) fromDayState else dayKey,
            if (preset == FinancePeriodPreset.CALENDAR) toDayState else dayKey,
            filter.key,
        ).joinToString("|")
    }

    private fun reportCacheProbe(
        preset: FinancePeriodPreset,
        filter: OperationalReportActorFilter,
    ): FinanceSummary {
        return buildOperationalReportViewState(
            repository = financeRepository,
            session = session,
            preset = preset,
            anchorDayKey = dayKey,
            fromDayKey = if (preset == FinancePeriodPreset.CALENDAR) fromDayState else null,
            toDayKey = if (preset == FinancePeriodPreset.CALENDAR) toDayState else null,
            filter = filter,
            syncStatus = OperationalReportSyncStatus.SERVER_FAILED,
        ).summary
    }

    private fun resolveReportStatus(
        remoteSucceeded: Boolean,
        summary: FinanceSummary,
    ): OperationalReportSyncStatus {
        return com.lotterynet.pro.core.finance.resolveOperationalReportSyncStatus(
            OperationalReportRemoteLoadResult(
                remoteSucceeded = remoteSucceeded,
                cacheAvailable = summary.ticketsCount > 0 || summary.ventas > 0.0 || summary.recargas > 0.0,
            ),
        )
    }

    private fun reportSummaryHasData(summary: FinanceSummary): Boolean {
        return summary.ticketsCount > 0 ||
            summary.ventas > 0.0 ||
            summary.recargas > 0.0 ||
            summary.comision > 0.0 ||
            summary.premiosPagados > 0.0 ||
            summary.premiosPendientes > 0.0
    }

    private fun shareReport() {
        val report = reportState ?: return
        val text = buildOperationalReportShareText(
            bancaName = session.banca ?: session.username,
            periodLabel = report.periodLabel,
            filter = report.filter,
            syncStatus = report.syncStatus,
            summary = report.summary,
            isSupervisorCommissionReport = isSupervisorCommissionReport(session, report.filter),
        )
        val periodReport = FinancePeriodReport(
            scope = FinanceScope(
                type = FinanceScopeType.BANK,
                adminId = session.userId,
                adminUser = session.username,
                bancaName = session.banca,
            ),
            preset = selectedPeriodState.toPreset(),
            range = FinanceResolvedRange(
                preset = selectedPeriodState.toPreset(),
                anchorDayKey = dayKey,
                fromDayKey = fromDayState,
                toDayKey = toDayState,
                label = report.periodLabel,
            ),
            fromDayKey = fromDayState,
            toDayKey = toDayState,
            summary = report.summary,
            rows = report.trend.map { FinancePeriodRow(it.label, it.summary) },
            actorRows = report.actorRows,
        )
        val bitmap = NativeBitmapExport.renderFinancePeriodBitmap(
            bancaName = session.banca ?: session.username,
            report = periodReport,
            actorLabel = report.filter.label,
        )
        NativeBitmapExport.shareBitmap(
            context = this,
            bitmap = bitmap,
            fileName = "reporte_${dayKey}.png",
            title = "Compartir reporte",
            text = text,
            whatsappOnly = false,
        )
    }
}

internal enum class OperationalReportPeriod(val label: String) {
    TODAY("Hoy"),
    WEEK("Semana"),
    QUINCENA("Quincena"),
    MONTH("Mes"),
    MANUAL("Manual"),
}

private fun OperationalReportPeriod.toPreset(): FinancePeriodPreset {
    return when (this) {
        OperationalReportPeriod.TODAY -> FinancePeriodPreset.DAY
        OperationalReportPeriod.WEEK -> FinancePeriodPreset.WEEK
        OperationalReportPeriod.QUINCENA -> FinancePeriodPreset.QUINCENA
        OperationalReportPeriod.MONTH -> FinancePeriodPreset.MONTH
        OperationalReportPeriod.MANUAL -> FinancePeriodPreset.CALENDAR
    }
}

@Composable
private fun OperationalReportRoute(
    session: ActiveSession,
    dayKey: String,
    filters: List<OperationalReportActorFilter>,
    selectedFilter: OperationalReportActorFilter,
    selectedPeriod: OperationalReportPeriod,
    fromDay: String,
    toDay: String,
    manualTarget: OperationalReportManualTarget,
    loading: Boolean,
    message: String,
    report: OperationalReportViewState?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onFilterSelected: (OperationalReportActorFilter) -> Unit,
    onPeriodSelected: (OperationalReportPeriod) -> Unit,
    onManualTargetSelected: (OperationalReportManualTarget) -> Unit,
    onManualDaySelected: (String) -> Unit,
    onApplyManual: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveOperationalReportLayout(visual.windowMode) }
    val context = LocalContext.current
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
            verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
        ) {
            item {
                AppTopBar(
                    spec = ScreenChromeSpec(
                        title = "Reporte",
                        subtitle = "${session.banca ?: "LotteryNet"} · $dayKey",
                        activeBottomTab = NativeBottomTab.MENU,
                        rightAction = ScreenChromeAction(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "Actualizar servidor",
                            onClick = onRefresh,
                        ),
                    ),
                    onOpenMenu = onBack,
                )
            }
            item {
                OperationalReportControls(
                    selectedPeriod = selectedPeriod,
                    fromDay = fromDay,
                    toDay = toDay,
                    manualTarget = manualTarget,
                    filters = filters,
                    selectedFilter = selectedFilter,
                    onPeriodSelected = onPeriodSelected,
                    onManualTargetSelected = onManualTargetSelected,
                    onManualDaySelected = onManualDaySelected,
                    onApplyManual = onApplyManual,
                    onFilterSelected = onFilterSelected,
                )
            }
            item {
                CompactPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.QueryStats, contentDescription = null, modifier = Modifier.size(18.dp), tint = visual.colors.ink)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (loading) "Actualizando..." else message,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (report?.syncStatus) {
                                OperationalReportSyncStatus.UPDATED -> gainColor()
                                OperationalReportSyncStatus.CACHED_COPY -> warningColor()
                                OperationalReportSyncStatus.SERVER_FAILED -> MaterialTheme.colorScheme.error
                                null -> visual.colors.neutral
                            },
                        )
                    }
                }
            }
            report?.let { current ->
                if (layout.showLedgerStrip) {
                    item {
                        OperationalReportLedgerSummary(
                            summary = current.summary,
                            layout = layout,
                            isSupervisorCommissionReport = isSupervisorCommissionReport(session, current.filter),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactActionButton(
                            label = "Compartir",
                            onClick = onShare,
                            icon = Icons.Rounded.Share,
                            modifier = Modifier.weight(1f),
                            tone = ActionTone.Primary,
                        )
                        CompactActionButton(
                            label = "Actualizar servidor",
                            onClick = onRefresh,
                            icon = Icons.Rounded.Refresh,
                            modifier = Modifier.weight(1f),
                            tone = ActionTone.Secondary,
                        )
                    }
                }
                if (current.actorRows.isNotEmpty()) {
                    item {
                        Text(
                            text = "Cajeros",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = visual.colors.ink,
                        )
                    }
                    items(current.actorRows) { row ->
                        OperationalReportActorRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationalReportControls(
    selectedPeriod: OperationalReportPeriod,
    fromDay: String,
    toDay: String,
    manualTarget: OperationalReportManualTarget,
    filters: List<OperationalReportActorFilter>,
    selectedFilter: OperationalReportActorFilter,
    onPeriodSelected: (OperationalReportPeriod) -> Unit,
    onManualTargetSelected: (OperationalReportManualTarget) -> Unit,
    onManualDaySelected: (String) -> Unit,
    onApplyManual: () -> Unit,
    onFilterSelected: (OperationalReportActorFilter) -> Unit,
) {
    var manualCalendarExpanded by remember(selectedPeriod) {
        mutableStateOf(selectedPeriod == OperationalReportPeriod.MANUAL)
    }
    CompactPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            OperationalReportPeriod.entries.forEach { period ->
                CompactActionButton(
                    label = period.label,
                    onClick = {
                        manualCalendarExpanded = shouldExpandManualReportCalendarAfterPeriodTap(period)
                        onPeriodSelected(period)
                    },
                    modifier = Modifier.weight(1f),
                    tone = if (period == selectedPeriod) ActionTone.Success else ActionTone.Secondary,
                )
            }
        }
        if (selectedPeriod == OperationalReportPeriod.MANUAL) {
            if (manualCalendarExpanded) {
                ManualReportRangePicker(
                    fromDay = fromDay,
                    toDay = toDay,
                    target = manualTarget,
                    onTargetSelected = onManualTargetSelected,
                    onDaySelected = onManualDaySelected,
                )
            } else {
                CompactActionButton(
                    label = "Cambiar fechas: $fromDay a $toDay",
                    onClick = { manualCalendarExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    tone = ActionTone.Secondary,
                )
            }
            CompactActionButton(
                label = "Aplicar",
                onClick = {
                    manualCalendarExpanded = false
                    onApplyManual()
                },
                modifier = Modifier.fillMaxWidth(),
                tone = ActionTone.Primary,
            )
        }
        if (shouldShowOperationalReportActorFilter(filters)) {
            OperationalReportFilterDropdown(
                filters = filters,
                selectedFilter = selectedFilter,
                onFilterSelected = onFilterSelected,
            )
        }
    }
}

internal fun shouldExpandManualReportCalendarAfterPeriodTap(period: OperationalReportPeriod): Boolean {
    return period == OperationalReportPeriod.MANUAL
}

internal fun shouldShowOperationalReportActorFilter(filters: List<OperationalReportActorFilter>): Boolean {
    return filters.size > 1
}

@Composable
private fun ManualReportRangePicker(
    fromDay: String,
    toDay: String,
    target: OperationalReportManualTarget,
    onTargetSelected: (OperationalReportManualTarget) -> Unit,
    onDaySelected: (String) -> Unit,
) {
    val fromDate = parseReportDayKey(fromDay)
    val toDate = parseReportDayKey(toDay)
    var visibleMonth by remember(fromDay, toDay, target) {
        mutableStateOf(YearMonth.from(if (target == OperationalReportManualTarget.FROM) fromDate else toDate))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ManualDateSelector(
            label = "Desde",
            day = fromDate,
            selected = target == OperationalReportManualTarget.FROM,
            onClick = { onTargetSelected(OperationalReportManualTarget.FROM) },
            modifier = Modifier.weight(1f),
        )
        ManualDateSelector(
            label = "Hasta",
            day = toDate,
            selected = target == OperationalReportManualTarget.TO,
            onClick = { onTargetSelected(OperationalReportManualTarget.TO) },
            modifier = Modifier.weight(1f),
        )
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, rememberLotteryNetVisualSpec().colors.border),
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactActionButton(
                    label = "<",
                    onClick = { visibleMonth = visibleMonth.minusMonths(1) },
                    modifier = Modifier.width(42.dp),
                    tone = ActionTone.Secondary,
                )
                Text(
                    text = monthTitle(visibleMonth),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                CompactActionButton(
                    label = ">",
                    onClick = { visibleMonth = visibleMonth.plusMonths(1) },
                    modifier = Modifier.width(42.dp),
                    tone = ActionTone.Secondary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = rememberLotteryNetVisualSpec().colors.muted,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            calendarCells(visibleMonth).chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        CalendarDayButton(
                            day = day,
                            fromDate = fromDate,
                            toDate = toDate,
                            onClick = { selected -> onDaySelected(selected.format(reportDayFormatter)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualDateSelector(
    label: String,
    day: LocalDate,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier
            .height(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) visual.colors.ink else visual.colors.panelAlt,
        border = androidx.compose.foundation.BorderStroke(1.dp, visual.colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White.copy(alpha = 0.72f) else visual.colors.muted,
                maxLines = 1,
            )
            Text(
                text = formatReportShortDay(day),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else visual.colors.ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CalendarDayButton(
    day: LocalDate?,
    fromDate: LocalDate,
    toDate: LocalDate,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    val inRange = day != null && !day.isBefore(fromDate) && !day.isAfter(toDate)
    val isEdge = day == fromDate || day == toDate
    Surface(
        modifier = modifier
            .height(34.dp)
            .then(if (day != null) Modifier.clickable { onClick(day) } else Modifier),
        shape = RoundedCornerShape(6.dp),
        color = when {
            isEdge -> visual.colors.ink
            inRange -> Color(0xFFE8F4EE)
            else -> Color.Transparent
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = day?.dayOfMonth?.toString() ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = when {
                    isEdge -> Color.White
                    day == null -> Color.Transparent
                    else -> visual.colors.ink
                },
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val reportDayFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private fun parseReportDayKey(dayKey: String): LocalDate {
    return runCatching { LocalDate.parse(dayKey, reportDayFormatter) }.getOrElse { LocalDate.now() }
}

private fun formatReportShortDay(day: LocalDate): String {
    return day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US))
}

private fun monthTitle(month: YearMonth): String {
    val dominicanSpanish = Locale.forLanguageTag("es-DO")
    val label = month.month.getDisplayName(JavaTextStyle.FULL, dominicanSpanish)
    return label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(dominicanSpanish) else it.toString() } +
        " ${month.year}"
}

private fun calendarCells(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    val leading = firstDay.dayOfWeek.value - 1
    val days = buildList {
        repeat(leading) { add(null) }
        for (day in 1..month.lengthOfMonth()) {
            add(month.atDay(day))
        }
    }
    val trailing = (7 - (days.size % 7)) % 7
    return days + List(trailing) { null }
}

@Composable
private fun OperationalReportFilterDropdown(
    filters: List<OperationalReportActorFilter>,
    selectedFilter: OperationalReportActorFilter,
    onFilterSelected: (OperationalReportActorFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val visual = rememberLotteryNetVisualSpec()
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clickable { expanded = true },
            shape = RoundedCornerShape(visual.sizes.panelRadius),
            color = visual.colors.panelAlt,
            border = androidx.compose.foundation.BorderStroke(1.dp, visual.colors.border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selectedFilter.label,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            filters.forEach { filter ->
                DropdownMenuItem(
                    text = { Text(filter.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onFilterSelected(filter)
                    },
                )
            }
        }
    }
}

@Composable
private fun OperationalReportLedgerSummary(
    summary: FinanceSummary,
    layout: OperationalReportLayoutContract,
    isSupervisorCommissionReport: Boolean = false,
) {
    val visual = rememberLotteryNetVisualSpec()
    val net = resolveOperationalReportNet(summary)
    val primaryValue = if (isSupervisorCommissionReport) summary.supervisorComision else net
    val metricSpecs = buildOperationalReportMetricSpecs(summary, isSupervisorCommissionReport)
    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = layout.metricPaddingVerticalDp.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    resolveOperationalReportPrimaryResultLabel(isSupervisorCommissionReport),
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
                Text(
                    text = operationalReportMoney(primaryValue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = reportMetricColor(
                        when {
                            primaryValue > 0.0 -> "gain"
                            primaryValue < 0.0 -> "loss"
                            else -> "ink"
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Caja", style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                Text(
                    text = operationalReportMoney(summary.cajaDisponible),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = visual.colors.ink,
                    maxLines = 1,
                )
            }
        }
        HorizontalDivider(color = visual.colors.border)
        metricSpecs.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { spec ->
                    ReportMiniMetric(
                        spec = spec,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            text = if (isSupervisorCommissionReport) {
                "Comisión supervisión: ${operationalReportMoney(summary.supervisorComision)} · Riesgo pendiente: ${operationalReportMoney(summary.premiosPendientes)}"
            } else {
                "Comisión ${operationalReportCommissionPercent(summary)} · Riesgo pendiente: ${operationalReportMoney(summary.premiosPendientes)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = warningColor(),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun reportMetricColor(tone: String): Color {
    val visual = rememberLotteryNetVisualSpec()
    return when (tone) {
        "gain" -> gainColor()
        "loss" -> MaterialTheme.colorScheme.error
        "warning" -> warningColor()
        else -> visual.colors.ink
    }
}

@Composable
private fun ReportMiniMetric(
    spec: OperationalReportMetricSpec,
    modifier: Modifier = Modifier,
) {
    ReportMiniMetric(
        label = spec.label,
        value = spec.value,
        color = reportMetricColor(spec.tone),
        bold = spec.bold,
        modifier = modifier,
    )
}

@Composable
private fun ReportMiniMetric(
    label: String,
    value: Double,
    color: Color,
    bold: Boolean,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    Column(
        modifier = modifier
            .background(visual.colors.panelAlt, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (bold) visual.colors.ink else visual.colors.muted,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = operationalReportMoney(value),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Black else FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReportMiniMetric(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    ReportMiniMetric(
        label = label,
        value = value,
        color = color,
        bold = false,
        modifier = modifier,
    )
}

@Composable
private fun OperationalReportActorRow(row: FinanceActorPeriodRow) {
    val visual = rememberLotteryNetVisualSpec()
    val net = resolveOperationalReportNet(row.summary)
    CompactPanel(alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.actorDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Venta ${operationalReportMoney(row.summary.ventas)} · Premio ${operationalReportMoney(row.summary.premiosPagados)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                operationalReportMoney(net),
                style = MaterialTheme.typography.bodyMedium.merge(TextStyle(fontWeight = FontWeight.Bold)),
                color = reportMetricColor(
                    when {
                        net > 0.0 -> "gain"
                        net < 0.0 -> "loss"
                        else -> "ink"
                    },
                ),
            )
        }
        HorizontalDivider(color = visual.colors.border)
    }
}
