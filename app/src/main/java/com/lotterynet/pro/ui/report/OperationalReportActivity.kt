package com.lotterynet.pro.ui.report

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
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
import com.lotterynet.pro.core.finance.OperationalReportServerCache
import com.lotterynet.pro.core.finance.OperationalReportRemoteLoadResult
import com.lotterynet.pro.core.finance.OperationalReportRequestSnapshot
import com.lotterynet.pro.core.finance.OperationalReportSyncStatus
import com.lotterynet.pro.core.finance.OperationalReportViewState
import com.lotterynet.pro.core.finance.RemoteOperationalReportRepository
import com.lotterynet.pro.core.finance.buildOperationalReportActorFilters
import com.lotterynet.pro.core.finance.buildOperationalReportShareText
import com.lotterynet.pro.core.finance.buildOperationalReportViewState
import com.lotterynet.pro.core.finance.operationalReportCommissionPercent
import com.lotterynet.pro.core.finance.operationalReportMoney
import com.lotterynet.pro.core.finance.isOperationalReportRequestCurrent
import com.lotterynet.pro.core.finance.resolveFinanceRemoteRefreshDecision
import com.lotterynet.pro.core.finance.resolveOperationalReportNet
import com.lotterynet.pro.core.finance.resolveOperationalReportFilterForRefresh
import com.lotterynet.pro.core.finance.shouldFetchOperationalReportEndpoint
import com.lotterynet.pro.core.finance.shouldSynchronizeOperationalReportDependencies
import com.lotterynet.pro.core.finance.updateOperationalReportManualRange
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeRechargeCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketRemoteStore
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
import com.lotterynet.pro.ui.common.CurrentScopeCard
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.OperationalModalSheet
import com.lotterynet.pro.ui.common.ScreenChromeAction
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.SectionHeader
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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val EXTRA_REPORT_CASHIER_KEY = "report_cashier_key"

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

internal fun operationalReportHeaderSubtitle(
    bancaName: String,
    report: OperationalReportViewState?,
    fallbackDayKey: String,
): String {
    val range = report?.periodLabel?.takeIf { it.isNotBlank() } ?: fallbackDayKey
    return "$bancaName · $range"
}

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
    private lateinit var serverReportCache: OperationalReportServerCache
    private lateinit var dayKey: String
    private var forcedCashierKey: String? = null

    private var actorFiltersState by mutableStateOf<List<OperationalReportActorFilter>>(listOf(OperationalReportActorFilter.All))
    private var selectedFilterState by mutableStateOf<OperationalReportActorFilter>(OperationalReportActorFilter.All)
    private var selectedPeriodState by mutableStateOf(OperationalReportPeriod.TODAY)
    private var fromDayState by mutableStateOf("")
    private var toDayState by mutableStateOf("")
    private var manualTargetState by mutableStateOf(OperationalReportManualTarget.FROM)
    private var reportState by mutableStateOf<OperationalReportViewState?>(null)
    private var loadingState by mutableStateOf(true)
    private var messageState by mutableStateOf("Datos locales listos")
    private val reportRequestGeneration = AtomicLong(0L)
    private var reportLoadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.OPERATIONAL_REPORT)) return
        session = activeSession ?: return
        forcedCashierKey = intent?.getStringExtra(EXTRA_REPORT_CASHIER_KEY)?.trim()?.takeIf { it.isNotBlank() }
        usersRepository = LocalUsersRepository(this)
        salesRepository = LocalSalesRepository(this)
        rechargeRepository = LocalRechargeRepository(this)
        financeRepository = LocalFinanceRepository(
            salesRepository = salesRepository,
            rechargeRepository = rechargeRepository,
            usersRepository = usersRepository,
        )
        freshnessRepository = LocalSyncFreshnessRepository(this)
        serverReportCache = OperationalReportServerCache(this)
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
                    onApplyFilters = { selection ->
                        selectedPeriodState = selection.period
                        fromDayState = selection.fromDayKey
                        toDayState = selection.toDayKey
                        manualTargetState = selection.manualTarget
                        selectedFilterState = selection.actorFilter
                        refreshReport(forceRemote = false)
                    },
                )
            }
        }

        refreshReport(forceRemote = false)
    }

    private fun refreshReport(forceRemote: Boolean) {
        reportLoadJob?.cancel()
        val requestId = reportRequestGeneration.incrementAndGet()
        val selectedPeriod = selectedPeriodState
        val request = OperationalReportRequestSnapshot(
            requestId = requestId,
            preset = selectedPeriod.toPreset(),
            fromDayKey = if (selectedPeriod == OperationalReportPeriod.MANUAL) fromDayState else null,
            toDayKey = if (selectedPeriod == OperationalReportPeriod.MANUAL) toDayState else null,
            selectedFilter = selectedFilterState,
            forceRemote = forceRemote,
        )
        loadingState = true
        messageState = if (forceRemote) "Cargando desde servidor..." else "Consultando servidor..."
        val appContext = applicationContext
        reportLoadJob = lifecycleScope.launch(Dispatchers.IO) {
            val ownerKey = resolveOperationalOwnerKey(session)
            val filters = buildOperationalReportActorFilters(
                session = session,
                cashiers = usersRepository.getCashiers(),
                supervisors = usersRepository.getSupervisors(),
            )
            val safeFilter = resolveOperationalReportFilterForRefresh(
                filters = filters,
                selected = request.selectedFilter,
                forcedCashierKey = forcedCashierKey,
            )
            val range = financeRepository.resolveRange(
                preset = request.preset,
                anchorDayKey = dayKey,
                fromDayKey = request.fromDayKey,
                toDayKey = request.toDayKey,
            )
            val cacheKey = buildSyncFreshnessKey(
                type = SyncFreshnessType.REPORT_PERIOD,
                ownerKey = ownerKey,
                banca = session.banca,
                dateKey = buildReportFreshnessDateKey(request, safeFilter),
            )
            val localReport = buildOperationalReportViewState(
                repository = financeRepository,
                session = session,
                preset = request.preset,
                anchorDayKey = dayKey,
                fromDayKey = request.fromDayKey,
                toDayKey = request.toDayKey,
                filter = safeFilter,
                syncStatus = OperationalReportSyncStatus.CACHED_COPY,
            ).let { report ->
                report.copy(syncStatus = resolveReportStatus(remoteSucceeded = false, summary = report.summary))
            }
            val localSummary = localReport.summary
            val decision = resolveFinanceRemoteRefreshDecision(
                hasLocalData = reportSummaryHasData(localSummary),
                forceRemote = request.forceRemote,
                selectedDayKey = request.toDayKey ?: dayKey,
                todayDayKey = dayKey,
                freshnessRecord = freshnessRepository.getRecord(cacheKey),
                nowEpochMs = System.currentTimeMillis(),
            )
            val serverCachedReport = serverReportCache.read(cacheKey, safeFilter)
            withContext(Dispatchers.Main) {
                if (!isOperationalReportRequestCurrent(reportRequestGeneration.get(), request.requestId)) {
                    return@withContext
                }
                actorFiltersState = filters.ifEmpty { listOf(OperationalReportActorFilter.All) }
                selectedFilterState = safeFilter
                reportState = serverCachedReport?.copy(syncStatus = OperationalReportSyncStatus.CACHED_COPY)
                    ?: reportState
                messageState = if (serverCachedReport != null) {
                    "Copia oficial guardada; verificando servidor..."
                } else {
                    decision.initialMessage
                }
            }
            ensureActive()
            if (!isOperationalReportRequestCurrent(reportRequestGeneration.get(), request.requestId)) return@launch
            val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(appContext))
            val shouldSynchronizeDependencies = decision.shouldRefreshRemote &&
                shouldSynchronizeOperationalReportDependencies(request, dayKey)
            val remoteSucceeded = if (shouldSynchronizeDependencies) {
                val ticketRemoteStore = NativeTicketRemoteStore(
                    bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
                    bearerTokenRefresher = { sessionTokenProvider.forceFreshAccessToken() },
                )
                val usersResult = NativeUsersBootstrapper(usersRepository).bootstrap(forceRemoteRefresh = request.forceRemote)
                ensureActive()
                val ticketResult = NativeOperationalSyncCoordinator(
                    ticketGateway = NativeTicketCloudSyncCoordinator(
                        salesRepository = salesRepository,
                        queueRepository = NativeTicketSyncQueueRepository(appContext),
                        remoteStore = ticketRemoteStore,
                    ),
                    remoteStampStore = ticketRemoteStore,
                ).syncTicketsForSession(session = session, force = request.forceRemote)
                ensureActive()
                val rechargeResult = NativeRechargeCloudSyncCoordinator(rechargeRepository).hydrateOwner(ownerKey)
                usersResult.ok && ticketResult.ok && rechargeResult.ok
            } else {
                false
            }
            val refreshedLocalReport = if (shouldSynchronizeDependencies) {
                buildOperationalReportViewState(
                    repository = financeRepository,
                    session = session,
                    preset = request.preset,
                    anchorDayKey = dayKey,
                    fromDayKey = request.fromDayKey,
                    toDayKey = request.toDayKey,
                    filter = safeFilter,
                    syncStatus = OperationalReportSyncStatus.CACHED_COPY,
                ).let { report ->
                    report.copy(
                        syncStatus = resolveReportStatus(
                            remoteSucceeded = remoteSucceeded,
                            summary = report.summary,
                        ),
                    )
                }
            } else {
                localReport
            }
            val refreshedLocalSummary = refreshedLocalReport.summary
            if (decision.shouldRefreshRemote && remoteSucceeded) {
                freshnessRepository.mark(cacheKey, SyncFreshnessState.SERVER_UPDATED)
            } else if (decision.shouldRefreshRemote && reportSummaryHasData(refreshedLocalSummary)) {
                freshnessRepository.mark(cacheKey, SyncFreshnessState.SERVER_FAILED_USING_CACHE)
            }
            ensureActive()
            if (!isOperationalReportRequestCurrent(reportRequestGeneration.get(), request.requestId)) return@launch
            val remoteReport = if (
                shouldFetchOperationalReportEndpoint(
                    refreshDecision = decision,
                    hasLocalReport = reportSummaryHasData(localSummary),
                )
            ) {
                runCatching {
                    RemoteOperationalReportRepository(
                        bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
                        bearerTokenRefresher = { sessionTokenProvider.forceFreshAccessToken() },
                    ).getReport(
                        session = session,
                        filter = safeFilter,
                        preset = request.preset,
                        range = range,
                    )
                }.getOrNull()
            } else {
                null
            }
            remoteReport?.let { serverReportCache.write(cacheKey, it) }
            val report = remoteReport ?: serverCachedReport
            withContext(Dispatchers.Main) {
                if (!isOperationalReportRequestCurrent(reportRequestGeneration.get(), request.requestId)) {
                    return@withContext
                }
                actorFiltersState = filters.ifEmpty { listOf(OperationalReportActorFilter.All) }
                selectedFilterState = safeFilter
                reportState = report
                messageState = when {
                    remoteReport != null -> "Actualizado desde servidor"
                    serverCachedReport != null -> "Sin conexión usando última copia del servidor"
                    else -> "No se pudo cargar el reporte del servidor"
                }
                loadingState = false
            }
        }
    }

    private fun buildReportFreshnessDateKey(
        request: OperationalReportRequestSnapshot,
        filter: OperationalReportActorFilter,
    ): String {
        return listOf(
            request.preset.name,
            request.fromDayKey ?: dayKey,
            request.toDayKey ?: dayKey,
            filter.key,
        ).joinToString("|")
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

internal data class OperationalReportFilterSelection(
    val period: OperationalReportPeriod,
    val fromDayKey: String,
    val toDayKey: String,
    val actorFilter: OperationalReportActorFilter,
    val manualTarget: OperationalReportManualTarget = OperationalReportManualTarget.FROM,
)

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
    onApplyFilters: (OperationalReportFilterSelection) -> Unit,
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
                        subtitle = operationalReportHeaderSubtitle(
                            bancaName = session.banca ?: "LotteryNet",
                            report = report,
                            fallbackDayKey = dayKey,
                        ),
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
                    currentPeriodLabel = report?.periodLabel,
                    fromDay = fromDay,
                    toDay = toDay,
                    manualTarget = manualTarget,
                    filters = filters,
                    selectedFilter = selectedFilter,
                    onApplyFilters = onApplyFilters,
                )
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
                    OperationalReportStatusRow(
                        loading = loading,
                        message = message,
                        status = current.syncStatus,
                    )
                }
                if (current.actorRows.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Desglose por cajero",
                            meta = "${current.actorRows.size} usuarios",
                        )
                    }
                    item {
                        OperationalReportActorList(current.actorRows)
                    }
                }
                item {
                    CompactActionButton(
                        label = "Compartir reporte",
                        onClick = onShare,
                        icon = Icons.Rounded.Share,
                        modifier = Modifier.fillMaxWidth(),
                        tone = ActionTone.Primary,
                    )
                }
            } ?: item {
                OperationalReportStatusRow(
                    loading = loading,
                    message = message,
                    status = null,
                )
            }
        }
    }
}

@Composable
private fun OperationalReportStatusRow(
    loading: Boolean,
    message: String,
    status: OperationalReportSyncStatus?,
) {
    val visual = rememberLotteryNetVisualSpec()
    val tone = if (loading) {
        visual.colors.finance
    } else {
        when (status) {
            OperationalReportSyncStatus.UPDATED -> gainColor()
            OperationalReportSyncStatus.CACHED_COPY -> warningColor()
            OperationalReportSyncStatus.SERVER_FAILED -> MaterialTheme.colorScheme.error
            null -> visual.colors.neutral
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = tone.copy(alpha = 0.08f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tone.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = if (loading) Icons.Rounded.Refresh else Icons.Rounded.QueryStats,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = tone,
            )
            Text(
                text = if (loading) "Actualizando · $message" else message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = tone,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OperationalReportControls(
    selectedPeriod: OperationalReportPeriod,
    currentPeriodLabel: String?,
    fromDay: String,
    toDay: String,
    manualTarget: OperationalReportManualTarget,
    filters: List<OperationalReportActorFilter>,
    selectedFilter: OperationalReportActorFilter,
    onApplyFilters: (OperationalReportFilterSelection) -> Unit,
) {
    var filtersSheetVisible by remember { mutableStateOf(false) }
    var draftPeriod by remember { mutableStateOf(selectedPeriod) }
    var draftFromDay by remember { mutableStateOf(fromDay) }
    var draftToDay by remember { mutableStateOf(toDay) }
    var draftManualTarget by remember { mutableStateOf(manualTarget) }
    var draftActorFilter by remember { mutableStateOf(selectedFilter) }
    var manualCalendarExpanded by remember { mutableStateOf(false) }
    val periodLabel = currentPeriodLabel ?: operationalReportPeriodScopeLabel(selectedPeriod, fromDay, toDay)

    fun applyDraftFilters() {
        filtersSheetVisible = false
        onApplyFilters(
            OperationalReportFilterSelection(
                period = draftPeriod,
                fromDayKey = draftFromDay,
                toDayKey = draftToDay,
                actorFilter = draftActorFilter,
                manualTarget = draftManualTarget,
            ),
        )
    }
    CurrentScopeCard(
        title = "Periodo del reporte",
        value = "${selectedPeriod.label} · $periodLabel",
        subtitle = "Operador: ${selectedFilter.label}",
        actionLabel = "Cambiar",
        onChange = {
            draftPeriod = selectedPeriod
            draftFromDay = fromDay
            draftToDay = toDay
            draftManualTarget = manualTarget
            draftActorFilter = selectedFilter
            manualCalendarExpanded = selectedPeriod == OperationalReportPeriod.MANUAL
            filtersSheetVisible = true
        },
        tone = ActionTone.Primary,
    )
    if (filtersSheetVisible) {
        OperationalReportFilterSheet(
            selectedPeriod = draftPeriod,
            fromDay = draftFromDay,
            toDay = draftToDay,
            manualTarget = draftManualTarget,
            filters = filters,
            selectedFilter = draftActorFilter,
            manualCalendarExpanded = manualCalendarExpanded,
            onManualCalendarExpandedChange = { manualCalendarExpanded = it },
            onPeriodSelected = { period ->
                draftPeriod = period
                manualCalendarExpanded = shouldExpandManualReportCalendarAfterPeriodTap(period)
                if (period != OperationalReportPeriod.MANUAL) {
                    filtersSheetVisible = false
                    onApplyFilters(
                        OperationalReportFilterSelection(
                            period = period,
                            fromDayKey = draftFromDay,
                            toDayKey = draftToDay,
                            actorFilter = draftActorFilter,
                            manualTarget = draftManualTarget,
                        ),
                    )
                }
            },
            onManualTargetSelected = { draftManualTarget = it },
            onManualDaySelected = { selectedDay ->
                val range = updateOperationalReportManualRange(
                    fromDayKey = draftFromDay,
                    toDayKey = draftToDay,
                    selectedDayKey = selectedDay,
                    target = draftManualTarget,
                )
                draftFromDay = range.fromDayKey
                draftToDay = range.toDayKey
                if (draftManualTarget == OperationalReportManualTarget.TO) {
                    onApplyFilters(
                        OperationalReportFilterSelection(
                            period = OperationalReportPeriod.MANUAL,
                            fromDayKey = range.fromDayKey,
                            toDayKey = range.toDayKey,
                            actorFilter = draftActorFilter,
                            manualTarget = draftManualTarget,
                        ),
                    )
                }
            },
            onFilterSelected = {
                draftActorFilter = it
                filtersSheetVisible = false
                onApplyFilters(
                    OperationalReportFilterSelection(
                        period = draftPeriod,
                        fromDayKey = draftFromDay,
                        toDayKey = draftToDay,
                        actorFilter = it,
                        manualTarget = draftManualTarget,
                    ),
                )
            },
            onApply = ::applyDraftFilters,
            onDismiss = { filtersSheetVisible = false },
        )
    }
}

@Composable
private fun OperationalReportFilterSheet(
    selectedPeriod: OperationalReportPeriod,
    fromDay: String,
    toDay: String,
    manualTarget: OperationalReportManualTarget,
    filters: List<OperationalReportActorFilter>,
    selectedFilter: OperationalReportActorFilter,
    manualCalendarExpanded: Boolean,
    onManualCalendarExpandedChange: (Boolean) -> Unit,
    onPeriodSelected: (OperationalReportPeriod) -> Unit,
    onManualTargetSelected: (OperationalReportManualTarget) -> Unit,
    onManualDaySelected: (String) -> Unit,
    onFilterSelected: (OperationalReportActorFilter) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    OperationalModalSheet(
        title = "Filtros del reporte",
        subtitle = "Periodo y operador visible en el reporte.",
        onDismiss = onDismiss,
        primaryActionLabel = "Aplicar filtro",
        onPrimaryAction = onApply,
        primaryActionTone = ActionTone.Primary,
        contentScrollable = true,
    ) {
        CompactPanel(alt = true) {
            Text(
                "Periodo",
                style = MaterialTheme.typography.labelLarge,
                color = visual.colors.ink,
                fontWeight = FontWeight.Black,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OperationalReportPeriod.entries.toList().chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { period ->
                            CompactActionButton(
                                label = period.label,
                                onClick = {
                                    onManualCalendarExpandedChange(shouldExpandManualReportCalendarAfterPeriodTap(period))
                                    onPeriodSelected(period)
                                },
                                modifier = Modifier.weight(1f),
                                tone = if (period == selectedPeriod) ActionTone.Success else ActionTone.Secondary,
                            )
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (selectedPeriod == OperationalReportPeriod.MANUAL) {
            CompactPanel(alt = true) {
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
                        onClick = { onManualCalendarExpandedChange(true) },
                        modifier = Modifier.fillMaxWidth(),
                        tone = ActionTone.Secondary,
                    )
                }
            }
        }
        if (shouldShowOperationalReportActorFilter(filters)) {
            CompactPanel(alt = true) {
                Text(
                    "Operador",
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filters, key = { it.key }) { filter ->
                        OperationalReportFilterRow(
                            filter = filter,
                            selected = filter.key == selectedFilter.key,
                            onClick = { onFilterSelected(filter) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationalReportFilterRow(
    filter: OperationalReportActorFilter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) visual.colors.finance.copy(alpha = 0.13f) else visual.colors.panelAlt,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) visual.colors.finance else visual.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.QueryStats, contentDescription = null, tint = if (selected) visual.colors.finance else visual.colors.muted)
            Text(
                filter.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = visual.colors.ink,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (selected) "Activo" else "Elegir",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) visual.colors.finance else visual.colors.muted,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun operationalReportPeriodScopeLabel(
    selectedPeriod: OperationalReportPeriod,
    fromDay: String,
    toDay: String,
): String {
    return if (selectedPeriod == OperationalReportPeriod.MANUAL) {
        "$fromDay a $toDay"
    } else {
        selectedPeriod.label
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
private fun OperationalReportActorList(rows: List<FinanceActorPeriodRow>) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
    ) {
        rows.forEachIndexed { index, row ->
            OperationalReportActorContent(row)
            if (index < rows.lastIndex) {
                HorizontalDivider(color = visual.colors.border)
            }
        }
    }
}

@Composable
private fun OperationalReportActorContent(row: FinanceActorPeriodRow) {
    val visual = rememberLotteryNetVisualSpec()
    val net = resolveOperationalReportNet(row.summary)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
}
