package com.lotterynet.pro.ui.results

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.calendar.LotteryClosePolicy
import com.lotterynet.pro.core.calendar.LotteryTimeZones
import com.lotterynet.pro.core.calendar.StaticHolidayCalendarRepository
import com.lotterynet.pro.core.catalog.LotteryAssetResolver
import com.lotterynet.pro.core.catalog.LotteryLogoBitmapLoader
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.catalog.UsPickScheduleResolver
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.export.StaticExportTemplateRepository
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryCalendarRule
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.ResultsSharePayload
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.render.LocalRenderCacheRepository
import com.lotterynet.pro.core.render.resultsRenderCacheKey
import com.lotterynet.pro.core.results.ResultsRefreshResult
import com.lotterynet.pro.core.results.ResultsRemoteStore
import com.lotterynet.pro.core.results.ResultsScraperOrchestrator
import com.lotterynet.pro.core.results.ResultsSupabaseStore
import com.lotterynet.pro.core.results.SupabaseResultsRemoteStore
import com.lotterynet.pro.core.results.TicketReconcileSummary
import com.lotterynet.pro.core.results.TicketPrizeReconciler
import com.lotterynet.pro.core.results.PickResultIdentityResolver
import com.lotterynet.pro.core.results.RESULT_STATUS_MISSING_FROM_SOURCES
import com.lotterynet.pro.core.results.RESULT_STATUS_PENDING
import com.lotterynet.pro.core.results.isNoDrawResult
import com.lotterynet.pro.core.results.normalizeResultDateKey
import com.lotterynet.pro.core.results.resultBelongsToDate
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscription
import com.lotterynet.pro.core.repository.ResultsRepository
import com.lotterynet.pro.core.storage.LocalBrandingRepository
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.core.storage.LocalAdminLotteryConfigRepository
import com.lotterynet.pro.core.storage.LocalPrizeConfigRepository
import com.lotterynet.pro.core.storage.LocalCashierPrizePayoutRepository
import com.lotterynet.pro.core.storage.LocalResultsRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalTrustedClockRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.sync.LocalSyncFreshnessRepository
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.SyncFreshnessType
import com.lotterynet.pro.core.sync.SyncGovernor
import com.lotterynet.pro.core.sync.buildSyncFreshnessKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactAdaptiveGrid
import com.lotterynet.pro.ui.common.CompactEmptyState
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.DropdownSelectorCard
import com.lotterynet.pro.ui.common.LotteryLogo
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.ScreenChromeAction
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.lossColor
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.sales.resolveSalesStartupSystemModeConfig
import com.lotterynet.pro.ui.shell.ShellActivity
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.concurrent.thread
import androidx.compose.ui.text.input.KeyboardType

class ResultsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val session = LocalSessionRepository(this).getActiveSession()
            if (redirectIfNativeDestinationBlocked(this, session?.role, NativeDestination.RESULTS)) {
                return
            }
            checkNotNull(session)
            val usersRepository = LocalUsersRepository(this)
            usersRepository.touchSession(session)
            val catalogRepository = StaticLotteryCatalogRepository()
            val trustedClockRepository = LocalTrustedClockRepository(this)
            val resultsRepository: ResultsRepository = LocalResultsRepository(this)
            val salesRepository = LocalSalesRepository(this)
            val ownerKey = resolveOperationalOwnerKey(session)
            val cashierPrizePayoutRepository = LocalCashierPrizePayoutRepository(this)
            val nativeOperationalSyncCoordinator = NativeOperationalSyncCoordinator(
                ticketGateway = NativeTicketCloudSyncCoordinator(
                    salesRepository = salesRepository,
                    queueRepository = NativeTicketSyncQueueRepository(this),
                ),
            )
            val ticketReconciler = TicketPrizeReconciler(
                salesRepository = salesRepository,
                prizeRepository = LocalPrizeConfigRepository(this),
                prizeConfigResolver = { ticket ->
                    cashierPrizePayoutRepository.resolveForTicket(
                        ownerId = ticket.adminId ?: ownerKey,
                        sellerUser = ticket.sellerUser,
                    )
                },
                onTicketUpdated = { ticket ->
                    runCatching {
                        nativeOperationalSyncCoordinator.flushTicket(ticket, session.banca)
                    }
                },
            )
            val freshnessRepository = LocalSyncFreshnessRepository(this)
            val systemModeConfig = resolveSalesStartupSystemModeConfig(
                session = session,
                usersRepository = usersRepository,
                adminLotteryConfigRepository = LocalAdminLotteryConfigRepository(this),
            )
            val allLotteries = catalogRepository.getAllLotteries()
            prewarmResultsLogos(allLotteries)
            val calendarRule = catalogRepository.getCalendarRule()
            val expectedResultIdsForDate = { date: String ->
                expectedResultIdsForMode(
                    lotteries = allLotteries,
                    config = systemModeConfig,
                    calendarRule = calendarRule,
                    date = date,
                )
            }
            val remoteStore: ResultsRemoteStore = ResultsSupabaseStore(
                expectedResultIdsForDate = { date ->
                    if (normalizeResultDateKey(date) == normalizeResultDateKey(todayDateKey())) {
                        emptySet()
                    } else {
                        expectedResultIdsForDate(date)
                    }
                },
            )
            val orchestrator = ResultsScraperOrchestrator(
                remoteStore = remoteStore,
                localResultsRepository = resultsRepository,
                freshnessRepository = freshnessRepository,
                freshnessKeyFactory = { date ->
                    buildSyncFreshnessKey(
                        type = SyncFreshnessType.RESULTS,
                        ownerKey = ownerKey,
                        banca = session.banca,
                        dateKey = date,
                    )
                },
                expectedResultIdsProvider = expectedResultIdsForDate,
                syncGovernor = SyncGovernor.shared,
            )
            val holidayRepository = StaticHolidayCalendarRepository(
                dominicanLotteryIds = calendarRule.dominicanLotteryIds,
                americanLotteryIds = calendarRule.americanLotteryIds,
            )
            val closePolicy = LotteryClosePolicy(trustedClockRepository, holidayRepository)
            val exportRepository = StaticExportTemplateRepository()
            val branding = LocalBrandingRepository(this).getBranding()
            val defaultDate = intent?.getStringExtra("results_date")?.takeIf { it.isNotBlank() } ?: todayDateKey()
            val initialResults = resultsRepository.getResultsForDate(defaultDate)

            setContent {
                LotteryNetComposeTheme {
                    ResultsRoute(
                        activeSession = session,
                        bancaName = session.banca ?: "LotteryNet",
                        bancaLogoUri = branding.logoUri,
                        operationTerritory = normalizeTerritory(session.territory),
                        defaultDate = defaultDate,
                        initialResults = initialResults,
                        resultsRepository = resultsRepository,
                        lotteries = allLotteries,
                        calendarRule = calendarRule,
                        trustedClockRepository = trustedClockRepository,
                        closePolicy = closePolicy,
                        orchestrator = orchestrator,
                        ticketReconciler = ticketReconciler,
                        exportRepository = exportRepository,
                        systemModeConfig = systemModeConfig,
                        role = session.role,
                        onShare = { payload, whatsappOnly ->
                            shareResults(exportRepository, payload, whatsappOnly, session.role)
                        },
                        onShareWhatsAppUris = { uris ->
                            NativeBitmapExport.shareImageUris(
                                context = this,
                                uris = uris,
                                title = "Resultados WhatsApp",
                                whatsappOnly = true,
                            )
                        },
                        onSave = { payload ->
                            saveResults(exportRepository, payload)
                        },
                        onPrint = { payload ->
                            printResults(payload)
                        },
                    )
                }
            }
        } catch (error: Throwable) {
            NativeCrashReporter(this).recordHandled("ResultsActivity.onCreate", error)
            Toast.makeText(this, "Resultados fallo al abrir. Volviendo al menu.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, ShellActivity::class.java).apply {
                putExtra(ShellActivity.EXTRA_FORCE_MENU, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }
    }

    private fun prewarmResultsLogos(lotteries: List<LotteryCatalogItem>) {
        val appContext = applicationContext
        val assetResolver = LotteryAssetResolver()
        val logoAssetPaths = lotteries.mapNotNull { lottery ->
            assetResolver.resolveLogoAssetPath(lottery)
        }
        thread(name = "results-logo-prewarm", isDaemon = true) {
            Thread.currentThread().priority = Thread.MIN_PRIORITY
            LotteryLogoBitmapLoader.prewarm(appContext, logoAssetPaths)
        }
    }

    private fun normalizeTerritory(raw: String?): LotteryTerritory {
        return if (raw.equals("USA", ignoreCase = true) || raw.equals("US", ignoreCase = true)) {
            LotteryTerritory.USA
        } else {
            LotteryTerritory.RD
        }
    }

    private fun shareResults(
        exportRepository: StaticExportTemplateRepository,
        payload: ResultsSharePayload,
        whatsappOnly: Boolean,
        role: UserRole,
    ): NativeBitmapExport.ExportActionResult {
        val envelope = exportRepository.buildResultsWhatsAppShare(payload)
        if (shouldShareResultsAsPlainText(payload)) {
            return NativeBitmapExport.shareText(
                context = this,
                title = envelope.title,
                text = exportRepository.buildResultsShareText(
                    payload.copy(rows = publishedResultShareRows(payload.rows)),
                ),
                whatsappOnly = whatsappOnly,
            )
        }
        return when (resolveResultsShareRenderMode(role, payload, whatsappOnly)) {
            ResultsShareRenderMode.CASHIER_GENERIC -> {
                val uris = getOrRenderResultsShareUris(payload)
                NativeBitmapExport.shareImageUris(
                    context = this,
                    uris = uris,
                    title = envelope.title,
                    whatsappOnly = whatsappOnly,
                )
            }
            ResultsShareRenderMode.ADMIN_POSTER -> {
                val bitmaps = NativeBitmapExport.renderResultsBitmaps(payload, this)
                NativeBitmapExport.shareBitmaps(
                    context = this,
                    bitmaps = bitmaps,
                    fileNames = resolveResultsShareFileNames(envelope.fileName, payload, bitmaps.size),
                    title = envelope.title,
                    whatsappOnly = whatsappOnly,
                )
            }
        }
    }

    private fun getOrRenderResultsShareUris(payload: ResultsSharePayload): List<Uri> {
        val renderCache = LocalRenderCacheRepository(this)
        val pages = NativeBitmapExport.resolveCashierResultsShareImagePages(payload)
        return pages.mapIndexedNotNull { pageIndex, page ->
            val key = resultsRenderCacheKey(payload.dateLabel, page.rows, pageIndex, page.template.name)
            renderCache.getUriIfPresent(key) ?: run {
                val bitmap = NativeBitmapExport.renderResultsShareImagePage(
                    payload = payload.copy(rows = page.rows),
                    page = page,
                    pageIndex = pageIndex,
                    pageCount = pages.size,
                    context = this,
                )
                renderCache.saveBitmap(key, bitmap)
            }
        }
    }

    private fun resolveResultsShareFileNames(
        baseFileName: String?,
        payload: ResultsSharePayload,
        count: Int,
    ): List<String> {
        val base = baseFileName ?: "resultados-${payload.dateLabel}.png"
        return (0 until count).map { index ->
            if (count == 1) {
                base
            } else {
                base.removeSuffix(".png") + "-${index + 1}.png"
            }
        }
    }

    private fun saveResults(
        exportRepository: StaticExportTemplateRepository,
        payload: ResultsSharePayload,
    ): String {
        val bitmaps = NativeBitmapExport.renderResultsBitmaps(payload, this)
        val envelope = exportRepository.buildResultsWhatsAppShare(payload)
        val fileNames = bitmaps.mapIndexed { index, _ ->
            if (bitmaps.size == 1) {
                envelope.fileName ?: "resultados-${payload.dateLabel}.png"
            } else {
                (envelope.fileName ?: "resultados-${payload.dateLabel}.png").removeSuffix(".png") + "-${index + 1}.png"
            }
        }
        NativeBitmapExport.saveBitmapsToDownloads(this, bitmaps, fileNames)
        return "Resultados guardados en Descargas"
    }

    private fun printResults(
        payload: ResultsSharePayload,
    ): Boolean {
        val bitmaps = NativeBitmapExport.renderResultsBitmaps(payload, this)
        return NativeBitmapExport.printBitmaps(this, bitmaps, "resultados-${payload.dateLabel}")
    }

    private fun todayDateKey(): String {
        return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date())
    }

}

internal enum class ResultsStartupWork {
    LOAD_SESSION,
    LOAD_LOCAL_RESULTS,
    BUILD_VISIBLE_ROWS,
    HYDRATE_REMOTE_RESULTS,
    RECONCILE_TICKETS,
    RENDER_RESULTS_BITMAP,
}

internal data class ResultsStartupPlan(
    val firstFrameWork: Set<ResultsStartupWork>,
    val afterFirstFrameWork: Set<ResultsStartupWork>,
)

internal fun resolveResultsStartupPlan(): ResultsStartupPlan {
    return ResultsStartupPlan(
        firstFrameWork = setOf(
            ResultsStartupWork.LOAD_SESSION,
            ResultsStartupWork.LOAD_LOCAL_RESULTS,
            ResultsStartupWork.BUILD_VISIBLE_ROWS,
        ),
        afterFirstFrameWork = setOf(
            ResultsStartupWork.HYDRATE_REMOTE_RESULTS,
            ResultsStartupWork.RECONCILE_TICKETS,
        ),
    )
}

internal data class ResultsRefreshActionUi(
    val spinning: Boolean,
    val enabled: Boolean,
    val message: String?,
)

internal fun resolveResultsRefreshActionUi(
    isRefreshing: Boolean,
    lastManualRefreshSucceeded: Boolean?,
): ResultsRefreshActionUi {
    if (isRefreshing) {
        return ResultsRefreshActionUi(
            spinning = true,
            enabled = false,
            message = "Buscando resultados remotos...",
        )
    }
    return when (lastManualRefreshSucceeded) {
        true -> ResultsRefreshActionUi(
            spinning = false,
            enabled = true,
            message = "Resultados actualizados.",
        )
        false -> ResultsRefreshActionUi(
            spinning = false,
            enabled = true,
            message = "Actualización falló.",
        )
        null -> ResultsRefreshActionUi(
            spinning = false,
            enabled = true,
            message = null,
        )
    }
}

@Composable
private fun ResultsRoute(
    activeSession: ActiveSession,
    bancaName: String,
    bancaLogoUri: String,
    role: UserRole,
    operationTerritory: LotteryTerritory,
    defaultDate: String,
    initialResults: List<LotteryResult>,
    resultsRepository: ResultsRepository,
    lotteries: List<LotteryCatalogItem>,
    calendarRule: LotteryCalendarRule,
    trustedClockRepository: LocalTrustedClockRepository,
    closePolicy: LotteryClosePolicy,
    orchestrator: ResultsScraperOrchestrator,
    ticketReconciler: TicketPrizeReconciler,
    exportRepository: StaticExportTemplateRepository,
    systemModeConfig: AdminSystemModeConfig,
    onShare: (ResultsSharePayload, Boolean) -> NativeBitmapExport.ExportActionResult,
    onShareWhatsAppUris: (List<Uri>) -> NativeBitmapExport.ExportActionResult,
    onSave: (ResultsSharePayload) -> String,
    onPrint: (ResultsSharePayload) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val crashReporter = remember(context) { NativeCrashReporter(context) }
    val realtimeClient = remember { LotterynetRealtimeClient() }
    val realtimeEnabled = remember { realtimeClient.isConfigured() }
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    var selectedDate by remember(defaultDate) { mutableStateOf(defaultDate) }
    var results by remember(defaultDate, initialResults) { mutableStateOf(initialResults) }
    var resultsByDate by remember(defaultDate, initialResults) {
        mutableStateOf(mapOf(defaultDate to initialResults))
    }
    var syncSource by remember(defaultDate) { mutableStateOf("local") }
    var syncMessage by remember(defaultDate, initialResults) {
        mutableStateOf<String?>(if (initialResults.isNotEmpty()) "Resultados locales listos." else null)
    }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastManualRefreshSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var tickUtcMs by remember { mutableStateOf(trustedClockRepository.getTrustedUtcMs()) }
    val stableBoardUtcMs = remember(selectedDate) { trustedClockRepository.getTrustedUtcMs() }
    var lastAutoRefreshStartedMs by remember { mutableStateOf<Long?>(null) }
    val realtimeRefreshInFlight = remember { AtomicBoolean(false) }
    var selectedModeWindow by remember(systemModeConfig) {
        mutableStateOf(resolveResultsModeWindowTabs(systemModeConfig).firstOrNull() ?: ResultsModeWindow.LOTTERY)
    }

    fun applyResultsForDate(date: String, nextResults: List<LotteryResult>) {
        resultsByDate = resultsByDate + (date to nextResults)
        if (shouldApplyResultsRefreshForSelectedDate(refreshDate = date, selectedDate = selectedDate)) {
            results = nextResults
        }
    }

    fun applyRefreshForDate(
        date: String,
        refresh: ResultsRefreshResult,
        reconcile: TicketReconcileSummary,
        showMessage: Boolean = true,
    ) {
        applyRefreshResult(
            refresh = refresh,
            reconcile = reconcile,
            onResults = { applyResultsForDate(date, it) },
            onSource = {
                if (shouldApplyResultsRefreshForSelectedDate(refreshDate = date, selectedDate = selectedDate)) {
                    syncSource = it
                }
            },
            onMessage = {
                if (showMessage && shouldApplyResultsRefreshForSelectedDate(refreshDate = date, selectedDate = selectedDate)) {
                    syncMessage = it
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            tickUtcMs = trustedClockRepository.getTrustedUtcMs()
            kotlinx.coroutines.delay(30_000)
        }
    }

    LaunchedEffect(defaultDate) {
        val warmupDates = resolveResultsDateCacheWarmupDates(
            defaultDate = defaultDate,
            today = todayOffset(0),
        )
        val localResultsByDate = withContext(Dispatchers.IO) {
            warmupDates.associateWith { date ->
                resolveLocalResultsForSelectedDate(
                    selectedDate = date,
                    defaultDate = defaultDate,
                    initialResults = initialResults,
                    loadLocalResults = resultsRepository::getResultsForDate,
                )
            }.filterValues { it.isNotEmpty() }
        }
        localResultsByDate.forEach { (date, dateResults) ->
            applyResultsForDate(date, dateResults)
        }
    }

    LaunchedEffect(selectedDate) {
        val requestedDate = selectedDate
        val cachedResultsForDate = resultsByDate[requestedDate]
        if (cachedResultsForDate != null) {
            results = cachedResultsForDate
            syncSource = "local"
            syncMessage = "Resultados guardados listos."
        }
        val localResultsForDate = withContext(Dispatchers.IO) {
            resolveLocalResultsForSelectedDate(
                selectedDate = requestedDate,
                defaultDate = defaultDate,
                initialResults = initialResults,
                loadLocalResults = resultsRepository::getResultsForDate,
            )
        }
        if (localResultsForDate.isNotEmpty()) {
            applyResultsForDate(requestedDate, localResultsForDate)
        } else if (cachedResultsForDate == null) {
            results = emptyList()
        }
        if (localResultsForDate.isNotEmpty()) {
            syncSource = "local"
            syncMessage = "Resultados locales listos."
            logResultsDiagnostics(
                requestedDate = requestedDate,
                source = "local",
                results = localResultsForDate,
                stage = "initial-local",
            )
        }
        val localResultsComplete = hasCompleteLocalResultsForExpectedDate(
            results = localResultsForDate,
            expectedResultIds = expectedResultIdsForMode(
                lotteries = lotteries,
                config = systemModeConfig,
                calendarRule = calendarRule,
                date = requestedDate,
            ),
        )
        if (shouldSkipInitialResultsHydration(requestedDate, todayOffset(0), localResultsComplete)) {
            syncSource = "local"
            syncMessage = "Resultados guardados listos."
            return@LaunchedEffect
        }
        if (requestedDate == defaultDate && localResultsForDate.isNotEmpty()) {
            delay(RESULTS_STARTUP_REMOTE_DELAY_MS)
        }
        runResultsRefresh(
            date = requestedDate,
            forceRemote = shouldForceRemoteOnInitialResultsLoad(
                selectedDate = requestedDate,
                today = todayOffset(0),
            ),
            allowLive = false,
            timeoutMs = null,
            orchestrator = orchestrator,
            ticketReconciler = ticketReconciler,
            onApplied = { refresh, reconcile ->
                applyRefreshForDate(requestedDate, refresh, reconcile)
            },
            onFailure = { error ->
                crashReporter.recordHandled("ResultsActivity.initialRefresh", error)
                if (shouldApplyResultsRefreshForSelectedDate(refreshDate = requestedDate, selectedDate = selectedDate)) {
                    syncMessage = "Resultados nativos fallaron al actualizar. Intenta manual o vuelve al menú."
                    actionMessage = "Error nativo al cargar resultados"
                }
            },
        )
    }

    DisposableEffect(selectedDate) {
        if (!realtimeClient.isConfigured()) {
            onDispose { }
        } else {
            val realtimeDate = selectedDate
            val subscriptions = listOf(
                LotterynetRealtimeSubscription.resultsCache("lot_results_cache_by_day:$realtimeDate"),
                LotterynetRealtimeSubscription.resultsCache("pick_results_cache_by_day:$realtimeDate"),
                LotterynetRealtimeSubscription.resultsCache("manual_results_overrides_by_day:$realtimeDate"),
            ).map { subscription ->
                realtimeClient.subscribe(subscription) {
                    if (!realtimeRefreshInFlight.compareAndSet(false, true)) {
                        return@subscribe
                    }
                    scope.launch {
                        try {
                            runResultsRefresh(
                                date = realtimeDate,
                                forceRemote = false,
                                allowLive = false,
                                timeoutMs = RESULTS_REALTIME_REFRESH_TIMEOUT_MS,
                                orchestrator = orchestrator,
                                ticketReconciler = ticketReconciler,
                                onApplied = { refresh, reconcile ->
                                    applyRefreshForDate(realtimeDate, refresh, reconcile, showMessage = true)
                                },
                                onFailure = { error ->
                                    crashReporter.recordHandled("ResultsActivity.realtimeRefresh", error)
                                    if (shouldApplyResultsRefreshForSelectedDate(refreshDate = realtimeDate, selectedDate = selectedDate)) {
                                        syncMessage = "Realtime no pudo actualizar. Se mantienen resultados guardados."
                                    }
                                },
                            )
                        } finally {
                            realtimeRefreshInFlight.set(false)
                        }
                    }
                }
            }
            onDispose {
                subscriptions.forEach { it.close() }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            realtimeClient.shutdown()
        }
    }

    val modeInputs = remember(lotteries, results, systemModeConfig, selectedModeWindow) {
        filterResultsInputsForModeWindow(
            lotteries = lotteries,
            results = results,
            config = systemModeConfig,
            selectedWindow = selectedModeWindow,
        )
    }
    val boardClockUtcMs = resolveResultsBoardClockUtcMs(
        selectedDate = selectedDate,
        today = todayOffset(0),
        liveUtcMs = tickUtcMs,
        stableUtcMs = stableBoardUtcMs,
    )
    val boardRows = remember(modeInputs, selectedDate, boardClockUtcMs, operationTerritory) {
        buildResultsBoardRows(
            lotteries = modeInputs.lotteries,
            results = modeInputs.results,
            selectedDate = selectedDate,
            nowUtcMs = boardClockUtcMs,
            operationTerritory = operationTerritory,
            closePolicy = closePolicy,
            noDrawLotteryIds = noDrawLotteryIdsForResultDate(calendarRule, selectedDate),
        )
    }
    val allBoardSections = remember(boardRows) { buildResultsBoardSections(boardRows) }
    val modeWindowTabs = remember(systemModeConfig) { resolveResultsModeWindowTabs(systemModeConfig) }
    LaunchedEffect(modeWindowTabs) {
        if (selectedModeWindow !in modeWindowTabs) {
            selectedModeWindow = modeWindowTabs.firstOrNull() ?: ResultsModeWindow.LOTTERY
        }
    }
    val boardSections = remember(allBoardSections, systemModeConfig, selectedModeWindow) {
        filterResultsBoardSectionsForMode(allBoardSections, systemModeConfig, selectedModeWindow)
    }
    val visibleBoardRows = remember(boardSections) { boardSections.flatMap { it.rows } }
    val rows = remember(visibleBoardRows) { visibleBoardRows.map { it.toShareRow() } }
    val publishedRows = remember(rows) { publishedResultShareRows(rows) }
    val payload = remember(bancaName, bancaLogoUri, selectedDate, publishedRows) {
        ResultsSharePayload(
            bancaName = bancaName,
            dateLabel = selectedDate,
            rows = exportRepository.buildResultsShareRows(publishedRows),
            bancaLogoUri = bancaLogoUri,
        )
    }

    LaunchedEffect(selectedDate, boardRows, tickUtcMs) {
        val shouldAutoRefresh = shouldAutoRefreshResultsFromServer(
            selectedDateIsToday = selectedDate == todayOffset(0),
            hasWaitingResult = boardRows.any { row -> row.stateTone == ResultsStateTone.WAITING_SYNC },
            hasRecoverableNoDrawResult = boardRows.any { row -> row.stateTone == ResultsStateTone.NO_DRAW },
            realtimeEnabled = realtimeEnabled,
        )
        if (!shouldAutoRefresh || isRefreshing) return@LaunchedEffect
        kotlinx.coroutines.delay(resolveResultsAutoRefreshDelayMs(realtimeEnabled))
        if (isRefreshing) return@LaunchedEffect
        val refreshStartMs = System.currentTimeMillis()
        if (!shouldRunResultsAutoRefresh(refreshStartMs, lastAutoRefreshStartedMs)) return@LaunchedEffect
        val autoRefreshDate = selectedDate
        lastAutoRefreshStartedMs = refreshStartMs
        isRefreshing = true
        syncMessage = "Buscando resultado disponible en servidor..."
        try {
            runResultsRefresh(
                date = autoRefreshDate,
                forceRemote = true,
                allowLive = true,
                timeoutMs = RESULTS_AUTO_REFRESH_TIMEOUT_MS,
                orchestrator = orchestrator,
                ticketReconciler = ticketReconciler,
                onApplied = { refresh, reconcile ->
                    applyRefreshForDate(autoRefreshDate, refresh, reconcile)
                },
                onFailure = { error ->
                    crashReporter.recordHandled("ResultsActivity.autoRefresh", error)
                    if (shouldApplyResultsRefreshForSelectedDate(refreshDate = autoRefreshDate, selectedDate = selectedDate)) {
                        syncMessage = "Auto refresh nativo fallo. Sigue viendo resultados guardados."
                        actionMessage = "Auto refresh falló"
                    }
                },
            )
        } finally {
            isRefreshing = false
        }
    }

    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveResultsLayout(visual.windowMode) }
    val refreshActionUi = remember(isRefreshing, lastManualRefreshSucceeded) {
        resolveResultsRefreshActionUi(isRefreshing, lastManualRefreshSucceeded)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = role,
                active = NativeBottomTab.RESULTS,
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
                        title = "Resultados",
                        subtitle = "Sorteos del día",
                        activeBottomTab = NativeBottomTab.RESULTS,
                        rightAction = ScreenChromeAction(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "Sync",
                            onClick = {
                                scope.launch {
                                    val manualRefreshDate = selectedDate
                                    try {
                                        isRefreshing = true
                                        lastManualRefreshSucceeded = null
                                        syncMessage = refreshActionUi.message ?: "Buscando resultados remotos..."
                                        runResultsRefresh(
                                            date = manualRefreshDate,
                                            forceRemote = true,
                                            allowLive = true,
                                            timeoutMs = RESULTS_MANUAL_REFRESH_TIMEOUT_MS,
                                            orchestrator = orchestrator,
                                            ticketReconciler = ticketReconciler,
                                            onApplied = { refresh, reconcile ->
                                                applyRefreshForDate(manualRefreshDate, refresh, reconcile)
                                                if (shouldApplyResultsRefreshForSelectedDate(refreshDate = manualRefreshDate, selectedDate = selectedDate)) {
                                                    lastManualRefreshSucceeded = true
                                                    actionMessage = resolveResultsRefreshActionUi(
                                                        isRefreshing = false,
                                                        lastManualRefreshSucceeded = true,
                                                    ).message
                                                }
                                            },
                                            onFailure = { error ->
                                                crashReporter.recordHandled("ResultsActivity.manualRefresh", error)
                                                if (shouldApplyResultsRefreshForSelectedDate(refreshDate = manualRefreshDate, selectedDate = selectedDate)) {
                                                    syncMessage = "No se pudieron traer resultados remotos."
                                                    lastManualRefreshSucceeded = false
                                                    actionMessage = resolveResultsRefreshActionUi(
                                                        isRefreshing = false,
                                                        lastManualRefreshSucceeded = false,
                                                    ).message
                                                }
                                            },
                                        )
                                    } catch (_: TimeoutCancellationException) {
                                        if (shouldApplyResultsRefreshForSelectedDate(refreshDate = manualRefreshDate, selectedDate = selectedDate)) {
                                            syncMessage = "Servidor tardó demasiado. Intenta otra vez."
                                            lastManualRefreshSucceeded = false
                                            actionMessage = "Actualización falló."
                                        }
                                    } finally {
                                        isRefreshing = false
                                    }
                                }
                            },
                            enabled = refreshActionUi.enabled,
                            spinning = refreshActionUi.spinning,
                        ),
                    ),
                    onOpenMenu = { com.lotterynet.pro.ui.common.openShellMenu(context) },
                )
                Spacer(modifier = Modifier.size(8.dp))
                ResultsDateChips(
                    selectedDate = selectedDate,
                    onDateSelected = { selectedDate = it },
                )
                Spacer(modifier = Modifier.size(8.dp))
                ResultsDateNavigator(
                    selectedDate = selectedDate,
                    onPrevious = { selectedDate = todayOffset(dayDiff(selectedDate) - 1) },
                    onNext = { selectedDate = todayOffset((dayDiff(selectedDate) + 1).coerceAtMost(0)) },
                )
                Spacer(modifier = Modifier.size(8.dp))
                if (modeWindowTabs.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        modeWindowTabs.forEach { window ->
                            CompactActionButton(
                                label = if (window == ResultsModeWindow.PICK) "Ver Pick" else "Ver Lotería",
                                onClick = { selectedModeWindow = window },
                                active = selectedModeWindow == window,
                                modifier = Modifier.weight(1f),
                                tone = if (selectedModeWindow == window) ActionTone.Primary else ActionTone.Secondary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                }
                ResultsActionPanel(
                    layout = layout,
                    availableCount = visibleBoardRows.count { it.result != null },
                    totalCount = visibleBoardRows.size,
                    showPrint = !systemModeConfig.pickAndLotteryEnabled,
                    onCopy = {
                        clipboard.setPrimaryClip(ClipData.newPlainText("Resultados", exportRepository.buildResultsShareText(payload)))
                        actionMessage = "Texto de resultados copiado"
                    },
                    onShare = { actionMessage = onShare(payload, false).message },
                    onWhatsApp = {
                        scope.launch {
                            val availableRows = visibleBoardRows.filter { row ->
                                shouldIncludeResultInWhatsAppCapture(row.toShareRow())
                            }
                            actionMessage = if (availableRows.isEmpty()) {
                                "No hay resultados disponibles para compartir."
                            } else {
                                val uris = captureResultsWhatsAppUris(
                                    context = context,
                                    bancaName = bancaName,
                                    bancaLogoUri = bancaLogoUri,
                                    selectedDate = selectedDate,
                                    rows = availableRows,
                                )
                                onShareWhatsAppUris(uris).message
                            }
                        }
                    },
                    onSave = { actionMessage = onSave(payload) },
                    onPrint = {
                        val opened = onPrint(payload)
                        actionMessage = if (opened) "Flujo de impresión abierto" else "No se pudo abrir la impresión"
                    },
                    syncMessage = syncMessage,
                    actionMessage = actionMessage,
                )
                Spacer(modifier = Modifier.size(visual.sizes.sectionGap))
                if (boardSections.isEmpty()) {
                    EmptyResultsCard(selectedDate = selectedDate, syncMessage = syncMessage)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(layout.listSpacingDp.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = visual.sizes.screenPaddingV),
                    ) {
                        boardSections.forEach { section ->
                            if (boardSections.size > 1) {
                                item(key = "section:${section.title}") {
                                    SectionHeader(
                                        title = section.title,
                                        meta = section.meta,
                                        tone = if (section.pickSection) visual.colors.results else visual.colors.tickets,
                                    )
                                }
                            }
                            items(section.rows, key = { "${section.title}:${it.lottery.id}:${selectedDate}" }) { row ->
                                ResultCard(
                                    row = row,
                                    pickKind = section.pickKind,
                                    layout = layout,
                                    onShare = {
                                        val singlePayload = ResultsSharePayload(
                                            bancaName = bancaName,
                                            dateLabel = selectedDate,
                                            rows = exportRepository.buildResultsShareRows(listOf(row.toShareRow())),
                                            bancaLogoUri = bancaLogoUri,
                                        )
                                        actionMessage = onShare(singlePayload, false).message
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val RESULTS_STARTUP_REMOTE_DELAY_MS = 450L
private const val RESULTS_AUTO_REFRESH_DELAY_MS = 60_000L
private const val RESULTS_AUTO_REFRESH_TIMEOUT_MS = 15_000L
private const val RESULTS_REALTIME_REFRESH_TIMEOUT_MS = 12_000L
private const val RESULTS_MANUAL_REFRESH_TIMEOUT_MS = 20_000L

private suspend fun runResultsRefresh(
    date: String,
    forceRemote: Boolean,
    allowLive: Boolean,
    timeoutMs: Long?,
    orchestrator: ResultsScraperOrchestrator,
    ticketReconciler: TicketPrizeReconciler,
    onApplied: (ResultsRefreshResult, TicketReconcileSummary) -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    runCatching {
        val refresh = if (timeoutMs != null) {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) {
                    orchestrator.refreshDate(
                        date = date,
                        forceRemote = forceRemote,
                        allowLive = allowLive,
                    )
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                orchestrator.refreshDate(
                    date = date,
                    forceRemote = forceRemote,
                    allowLive = allowLive,
                )
            }
        }
        val reconcile = withContext(Dispatchers.IO) {
            ticketReconciler.reconcileTicketsForDate(date, refresh.results)
        }
        onApplied(refresh, reconcile)
    }.onFailure { error ->
        if (shouldReportResultsRefreshError(error)) {
            onFailure(error)
        } else {
            throw error
        }
    }
}

private fun logResultsDiagnostics(
    requestedDate: String,
    source: String,
    results: List<LotteryResult>,
    stage: String,
) {
    val receivedDates = results
        .mapNotNull { result -> result.date.takeIf(String::isNotBlank) }
        .map(::normalizeResultDateKey)
        .distinct()
        .sorted()
        .joinToString(",")
        .ifBlank { "none" }
    val pickCount = results.count(::isResultPickLottery)
    val lotteryCount = results.size - pickCount
    Log.d(
        "ResultsActivity",
        "stage=$stage requestedDate=$requestedDate receivedDates=$receivedDates source=$source lotteryCount=$lotteryCount pickCount=$pickCount",
    )
}

internal fun shouldReportResultsRefreshError(error: Throwable): Boolean {
    return error is TimeoutCancellationException || error !is CancellationException
}

private suspend fun captureResultsListBitmap(
    context: android.content.Context,
    selectedDate: String,
    rows: List<ResultsBoardRow>,
): Bitmap = withContext(Dispatchers.Main) {
    val activity = context as? AppCompatActivity
        ?: throw IllegalArgumentException("La captura de resultados requiere una Activity.")
    val root = activity.window.decorView as ViewGroup
    val screenWidth = root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
    val width = resultsWhatsAppCaptureCanvasWidthPx(
        screenWidthPx = screenWidth,
        density = context.resources.displayMetrics.density,
    )
    val composeView = ComposeView(context).apply {
        visibility = View.INVISIBLE
        setContent {
            LotteryNetComposeTheme {
                ResultsFullListCapture(selectedDate = selectedDate, rows = rows)
            }
        }
    }
    root.addView(composeView, ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
    try {
        composeView.awaitNextLayoutPass()
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        composeView.measure(widthSpec, heightSpec)
        val height = composeView.measuredHeight.coerceAtLeast(1)
        composeView.layout(0, 0, width, height)
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            composeView.draw(Canvas(bitmap))
        }
    } finally {
        root.removeView(composeView)
    }
}

private suspend fun captureResultsListUris(
    context: android.content.Context,
    bancaName: String,
    bancaLogoUri: String,
    selectedDate: String,
    rows: List<ResultsBoardRow>,
): List<Uri> {
    val renderCache = LocalRenderCacheRepository(context)
    val pages = rows.chunked(resultsWhatsAppCardsPerImage()).ifEmpty { listOf(emptyList()) }
    return pages.mapIndexedNotNull { pageIndex, pageRows ->
        val shareRows = pageRows.map { it.toShareRow() }
        val key = resultsRenderCacheKey(selectedDate, shareRows, pageIndex, template = "LIGHT_RESULT_LIST")
        val cached = renderCache.getUriIfPresent(key)
        if (cached != null) {
            cached
        } else {
            val bitmap = captureResultsListBitmap(context, selectedDate, pageRows)
            renderCache.saveBitmap(key, bitmap)
        }
    }
}

private suspend fun captureResultsWhatsAppUris(
    context: android.content.Context,
    bancaName: String,
    bancaLogoUri: String,
    selectedDate: String,
    rows: List<ResultsBoardRow>,
): List<Uri> {
    val normalRows = rows.filter { row -> row.toShareRow().isPrimaryNormalShareRow() }
    val pickRows = rows.filter { row ->
        val shareRow = row.toShareRow()
        !shareRow.isPrimaryNormalShareRow() && isPickOnlyShareRow(shareRow)
    }
    val uris = mutableListOf<Uri>()
    if (normalRows.isNotEmpty()) {
        uris += captureResultsListUris(
            context = context,
            bancaName = bancaName,
            bancaLogoUri = bancaLogoUri,
            selectedDate = selectedDate,
            rows = normalRows,
        )
    }
    if (pickRows.isNotEmpty()) {
        uris += renderPickResultsShareUris(
            context = context,
            bancaName = bancaName,
            bancaLogoUri = bancaLogoUri,
            selectedDate = selectedDate,
            rows = pickRows.map { it.toShareRow() },
        )
    }
    return uris
}

private fun renderPickResultsShareUris(
    context: android.content.Context,
    bancaName: String,
    bancaLogoUri: String,
    selectedDate: String,
    rows: List<com.lotterynet.pro.core.model.ResultShareRow>,
): List<Uri> {
    val renderCache = LocalRenderCacheRepository(context)
    val payload = ResultsSharePayload(
        bancaName = bancaName,
        dateLabel = selectedDate,
        rows = rows,
        bancaLogoUri = bancaLogoUri,
    )
    val pages = NativeBitmapExport.resolveResultsShareImagePages(payload)
        .filter { page ->
            page.template == NativeBitmapExport.ResultsShareImageTemplate.PICK3_DENSE_LIST ||
                page.template == NativeBitmapExport.ResultsShareImageTemplate.PICK4_DENSE_LIST
        }
    return pages.mapIndexedNotNull { pageIndex, page ->
        val key = resultsRenderCacheKey(selectedDate, page.rows, pageIndex, page.template.name)
        renderCache.getUriIfPresent(key) ?: run {
            val bitmap = NativeBitmapExport.renderResultsShareImagePage(
                payload = payload.copy(rows = page.rows),
                page = page,
                pageIndex = pageIndex,
                pageCount = pages.size,
                context = context,
            )
            renderCache.saveBitmap(key, bitmap)
        }
    }
}

private suspend fun View.awaitNextLayoutPass() {
    suspendCancellableCoroutine { continuation ->
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (viewTreeObserver.isAlive) {
                    viewTreeObserver.removeOnPreDrawListener(this)
                }
                if (continuation.isActive) continuation.resume(Unit)
                return true
            }
        }
        viewTreeObserver.addOnPreDrawListener(listener)
        continuation.invokeOnCancellation {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }
    }
}

@Composable
private fun ResultsFullListCapture(selectedDate: String, rows: List<ResultsBoardRow>) {
    Surface(
        color = Color(0xFFF2F2F2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEach { row ->
                WhatsAppResultImageCard(
                    dateLabel = formatWhatsAppResultDateLabel(selectedDate),
                    row = row,
                )
            }
        }
    }
}

@Composable
private fun WhatsAppResultImageCard(dateLabel: String, row: ResultsBoardRow) {
    val captureSpec = resolveResultsWhatsAppCaptureVisualSpec()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 11.dp, end = 14.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.width(captureSpec.infoColumnWidthDp.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = dateLabel,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF151922),
                    fontSize = captureSpec.dateFontSp.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(captureSpec.logoBoxHeightDp.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LotteryLogo(
                        assetPath = row.lottery.logoAssetPath,
                        fallback = row.lottery.name,
                        modifier = Modifier
                            .width(captureSpec.logoMaxWidthDp.dp)
                            .height(captureSpec.logoMaxHeightDp.dp),
                        tintColor = Color.White,
                        fillBounds = true,
                    )
                }
                Text(
                    text = row.drawTimeLabel.replace(" ", ""),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF0B78A6),
                    fontSize = captureSpec.timeFontSp.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                whatsappResultNumbers(row).forEach { number ->
                    WhatsAppResultBall(number, captureSpec)
                }
            }
        }
    }
}

@Composable
private fun WhatsAppResultBall(number: String, captureSpec: ResultsWhatsAppCaptureVisualSpec) {
    Box(
        modifier = Modifier
            .size(captureSpec.ballSizeDp.dp)
            .background(Color(0xFFD8EEFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number,
            color = Color(0xFF151C2B),
            fontSize = captureSpec.numberFontSp.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

private fun applyRefreshResult(
    refresh: ResultsRefreshResult,
    reconcile: TicketReconcileSummary,
    onResults: (List<LotteryResult>) -> Unit,
    onSource: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    onResults(refresh.results)
    onSource(refresh.source)
    logResultsDiagnostics(
        requestedDate = refresh.date,
        source = refresh.source,
        results = refresh.results,
        stage = "refresh",
    )
    onMessage(
        refresh.message ?: when {
            refresh.updated && reconcile.updated > 0 -> "Resultados actualizados desde ${presentResultsSourceLabel(refresh.source)}. Tickets ajustados: ${reconcile.updated}."
            refresh.updated -> "Resultados actualizados desde ${presentResultsSourceLabel(refresh.source)}."
            refresh.results.isNotEmpty() && reconcile.updated > 0 -> "Resultados cargados desde ${presentResultsSourceLabel(refresh.source)}. Tickets ajustados: ${reconcile.updated}."
            refresh.results.isNotEmpty() -> "Resultados cargados desde ${presentResultsSourceLabel(refresh.source)}."
            else -> "No hay resultados guardados para esta fecha."
        },
    )
}

@Composable
private fun ResultsDateNavigator(
    selectedDate: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.results.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(onClick = onPrevious, color = visual.colors.resultsSurface, shape = RoundedCornerShape(7.dp)) {
                Box(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                    Text("‹", color = visual.colors.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "${presentResultsDateLabel(selectedDate)} · $selectedDate",
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                )
                CompactStatusBadge(label = formatDayName(selectedDate), tone = visual.colors.results)
            }
            Surface(onClick = onNext, color = visual.colors.resultsSurface, shape = RoundedCornerShape(7.dp)) {
                Box(modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                    Text("›", color = visual.colors.ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ResultsActionPanel(
    layout: ResultsLayoutContract,
    availableCount: Int,
    totalCount: Int,
    showPrint: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onWhatsApp: () -> Unit,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    syncMessage: String?,
    actionMessage: String?,
) {
    val visual = rememberLotteryNetVisualSpec()
    var exportMenuExpanded by remember { mutableStateOf(false) }
    val exportMenu = remember(showPrint) { resolveResultsExportMenuContract(showPrint) }
    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = layout.headerPaddingVerticalDp.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Tablero del día", style = MaterialTheme.typography.labelLarge, color = visual.colors.ink)
            }
            CompactStatusBadge(label = "$availableCount/$totalCount", tone = visual.colors.results)
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            CompactActionButton(
                label = exportMenu.visibleButtonLabel,
                onClick = { exportMenuExpanded = true },
                icon = Icons.Rounded.Download,
                modifier = Modifier.fillMaxWidth(),
                tone = ActionTone.Primary,
            )
            DropdownMenu(
                expanded = exportMenuExpanded && exportMenu.usesOverflowMenu,
                onDismissRequest = { exportMenuExpanded = false },
                modifier = Modifier.fillMaxWidth(0.92f),
            ) {
                DropdownMenuItem(
                    text = { Text("WhatsApp") },
                    leadingIcon = { Icon(Icons.Rounded.Whatsapp, contentDescription = null) },
                    onClick = {
                        exportMenuExpanded = false
                        onWhatsApp()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Compartir") },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    onClick = {
                        exportMenuExpanded = false
                        onShare()
                    },
                )
                if (showPrint) {
                    DropdownMenuItem(
                        text = { Text("Imprimir") },
                        leadingIcon = { Icon(Icons.Rounded.Print, contentDescription = null) },
                        onClick = {
                            exportMenuExpanded = false
                            onPrint()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Guardar") },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    onClick = {
                        exportMenuExpanded = false
                        onSave()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copiar") },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    onClick = {
                        exportMenuExpanded = false
                        onCopy()
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultsDateChips(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
) {
    val dates = remember { listOf(todayOffset(0), todayOffset(-1), todayOffset(-2)) }
    CompactAdaptiveGrid(
        itemCount = dates.size,
        columns = dates.size,
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
    ) { index, itemModifier ->
        val dateKey = dates[index]
        val active = dateKey == selectedDate
        CompactActionButton(
            label = when (dateKey) {
                todayOffset(0) -> "Hoy"
                todayOffset(-1) -> "Ayer"
                else -> "Anteayer"
            },
            onClick = { onDateSelected(dateKey) },
            active = active,
            modifier = itemModifier,
            tone = if (active) ActionTone.Primary else ActionTone.Secondary,
        )
    }
}

internal data class ManualResultOption(
    val resultId: String,
    val name: String,
    val label: String,
    val game: String,
    val currentNumber: String?,
)

@Composable
internal fun ManualResultsEditorPanel(
    selectedDate: String,
    options: List<ManualResultOption>,
    selectedOption: ManualResultOption?,
    manualResultNumber: String,
    onOpenSelector: () -> Unit,
    onManualResultNumberChange: (String) -> Unit,
    busy: Boolean,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Edición manual admin",
                style = MaterialTheme.typography.titleSmall,
                color = visual.colors.ink,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Fecha $selectedDate. El cambio se guarda en servidor y lo ven tus cajeros al refrescar.",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
            )
            DropdownSelectorCard(
                label = "Sorteo",
                value = selectedOption?.label ?: if (options.isEmpty()) "Sin sorteos disponibles" else "Elegir sorteo",
                onClick = onOpenSelector,
            )
            OutlinedTextField(
                value = manualResultNumber,
                onValueChange = onManualResultNumberChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy && selectedOption != null,
                label = { Text("Resultado manual") },
                placeholder = { Text(manualResultPlaceholder(selectedOption?.game)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = visual.colors.panel,
                    unfocusedContainerColor = visual.colors.panel,
                    disabledContainerColor = visual.colors.panelAlt,
                    focusedIndicatorColor = visual.colors.actionPrimary,
                    unfocusedIndicatorColor = visual.colors.border,
                    focusedLabelColor = visual.colors.actionPrimary,
                    unfocusedLabelColor = visual.colors.muted,
                    cursorColor = visual.colors.actionPrimary,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactActionButton(
                    label = "Guardar cambio",
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    active = true,
                    enabled = !busy && selectedOption != null,
                    tone = ActionTone.Primary,
                )
                CompactActionButton(
                    label = "Quitar manual",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    active = false,
                    enabled = !busy && selectedOption != null,
                    tone = ActionTone.Secondary,
                )
            }
        }
    }
}

@Composable
internal fun ManualResultsPickerDialog(
    options: List<ManualResultOption>,
    selectedResultId: String,
    onDismiss: () -> Unit,
    onSelected: (ManualResultOption) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Elegir sorteo") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(options, key = { it.resultId }) { option ->
                    val active = option.resultId == selectedResultId
                    CompactActionButton(
                        label = option.label,
                        onClick = { onSelected(option) },
                        active = active,
                        tone = if (active) ActionTone.Primary else ActionTone.Secondary,
                    )
                }
            }
        },
        confirmButton = {
            CompactActionButton(label = "Cerrar", onClick = onDismiss, tone = ActionTone.Secondary)
        },
    )
}

private data class ResultsBoardRow(
    val lottery: LotteryCatalogItem,
    val result: LotteryResult?,
    val drawTimeLabel: String,
    val stateLabel: String,
    val stateTone: ResultsStateTone,
    val sourceLabel: String?,
    val minutesToDraw: Int?,
    val isManualOverride: Boolean = false,
)

private data class ResultsBoardSection(
    val title: String,
    val meta: String,
    val pickSection: Boolean,
    val pickKind: ResultsPickSectionKind? = null,
    val rows: List<ResultsBoardRow>,
)

private enum class ResultsPickSectionKind {
    PICK3,
    PICK4,
}

private fun buildResultsBoardSections(rows: List<ResultsBoardRow>): List<ResultsBoardSection> {
    val normalRows = rows.filterNot { it.isPickResultRow() }
    val pickRows = rows.filter { it.isPickResultRow() }
    val pick3Rows = pickRows.filter { it.belongsToPick3Section() }
    val pick4Rows = pickRows.filter { it.belongsToPick4Section() }
    return buildList {
        if (normalRows.isNotEmpty()) {
            add(ResultsBoardSection("Loterías", "${normalRows.size} resultados", pickSection = false, rows = normalRows))
        }
        if (pick3Rows.isNotEmpty()) {
            add(ResultsBoardSection("Pick 3", "${pick3Rows.size} sorteos", pickSection = true, pickKind = ResultsPickSectionKind.PICK3, rows = pick3Rows))
        }
        if (pick4Rows.isNotEmpty()) {
            add(ResultsBoardSection("Pick 4", "${pick4Rows.size} sorteos", pickSection = true, pickKind = ResultsPickSectionKind.PICK4, rows = pick4Rows))
        }
    }
}

internal data class ResultsModeInputs(
    val lotteries: List<LotteryCatalogItem>,
    val results: List<LotteryResult>,
)

internal fun filterResultsInputsForModeWindow(
    lotteries: List<LotteryCatalogItem>,
    results: List<LotteryResult>,
    config: AdminSystemModeConfig,
    selectedWindow: ResultsModeWindow,
): ResultsModeInputs {
    val normalized = normalizeResultsModeWindow(config, selectedWindow)
    val pickWindow = normalized == ResultsModeWindow.PICK
    val filteredLotteries = lotteries.filter { lottery -> isCatalogPickLottery(lottery) == pickWindow }
    val filteredResults = results.filter { result -> isResultPickLottery(result) == pickWindow }
    return ResultsModeInputs(filteredLotteries, filteredResults)
}

internal fun expectedResultIdsForMode(
    lotteries: List<LotteryCatalogItem>,
    config: AdminSystemModeConfig,
    calendarRule: LotteryCalendarRule? = null,
    date: String? = null,
): Set<String> {
    val normalized = com.lotterynet.pro.core.storage.normalizeAdminSystemModeConfig(config)
    val noDrawLotteryIds = calendarRule?.let { rule ->
        date?.let { noDrawLotteryIdsForResultDate(rule, it) }
    }.orEmpty()
    return lotteries
        .filterNot { lottery -> lottery.id in noDrawLotteryIds }
        .filter { lottery ->
            when {
                normalized.lotteryModeEnabled && normalized.pickModeEnabled -> true
                normalized.pickModeEnabled -> isCatalogPickLottery(lottery)
                else -> !isCatalogPickLottery(lottery)
            }
        }
        .mapTo(linkedSetOf()) { lottery ->
            if (isCatalogPickLottery(lottery)) {
                PickResultIdentityResolver.canonicalKeyForLottery(lottery)
            } else {
                lottery.id
            }
        }
}

internal fun noDrawLotteryIdsForResultDate(
    calendarRule: LotteryCalendarRule,
    date: String,
): Set<String> {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Santo_Domingo"))
    val parsed = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.parse(date) ?: return emptySet()
    calendar.time = parsed
    val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(parsed)
    val weekdayIndex = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> 0
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        else -> 0
    }
    return linkedSetOf<String>().apply {
        addAll(calendarRule.noDrawDatesByLottery[dateKey].orEmpty())
        addAll(calendarRule.dayDisabledByWeekday[weekdayIndex].orEmpty())
    }
}

private fun normalizeResultsModeWindow(
    config: AdminSystemModeConfig,
    selectedWindow: ResultsModeWindow,
): ResultsModeWindow {
    val tabs = resolveResultsModeWindowTabs(config)
    return selectedWindow.takeIf { it in tabs } ?: tabs.firstOrNull() ?: ResultsModeWindow.LOTTERY
}

private fun isCatalogPickLottery(lottery: LotteryCatalogItem): Boolean {
    return lottery.playCapabilities.supportsStraight ||
        lottery.playCapabilities.supportsBox ||
        lottery.type.contains("pick", ignoreCase = true) ||
        lottery.id.uppercase(Locale.US).startsWith("US-P3-") ||
        lottery.id.uppercase(Locale.US).startsWith("US-P4-")
}

private fun isResultPickLottery(result: LotteryResult): Boolean {
    val id = result.lotteryId.uppercase(Locale.US)
    return !result.pick3.isNullOrBlank() ||
        !result.pick4.isNullOrBlank() ||
        id.startsWith("US-P3-") ||
        id.startsWith("US-P4-")
}

private fun ResultsBoardRow.isPickResultRow(): Boolean {
    return isPickLotteryType(lottery.type) ||
        lottery.id.uppercase(Locale.US).startsWith("US-P3-") ||
        lottery.id.uppercase(Locale.US).startsWith("US-P4-") ||
        !result?.pick3.isNullOrBlank() ||
        !result?.pick4.isNullOrBlank()
}

private fun ResultsBoardRow.belongsToPick3Section(): Boolean {
    if (!result?.pick3.isNullOrBlank()) return true
    val identity = "${lottery.id} ${lottery.name} ${lottery.type}".filter(Char::isLetterOrDigit).lowercase(Locale.US)
    return "usp3" in identity || "pick3" in identity || "daily3" in identity || "numbers" in identity
}

private fun ResultsBoardRow.belongsToPick4Section(): Boolean {
    if (!result?.pick4.isNullOrBlank()) return true
    val identity = "${lottery.id} ${lottery.name} ${lottery.type}".filter(Char::isLetterOrDigit).lowercase(Locale.US)
    return "usp4" in identity || "pick4" in identity || "daily4" in identity || "cash4" in identity || "win4" in identity
}

private fun isPickLotteryType(type: String): Boolean {
    val normalized = type.filter(Char::isLetterOrDigit).lowercase(Locale.US)
    return normalized == "pick3" ||
        normalized == "p3" ||
        normalized.contains("pick3") ||
        normalized == "pick4" ||
        normalized == "p4" ||
        normalized.contains("pick4")
}

internal enum class ResultsModeWindow {
    LOTTERY,
    PICK,
}

private enum class ResultsStateTone {
    PUBLISHED,
    PENDING,
    WAITING_SYNC,
    NO_DRAW,
    MISSING,
}

internal enum class ResultsActionId {
    WHATSAPP,
    SHARE,
    SAVE,
    PRINT,
    COPY,
}

internal data class ResultsLayoutContract(
    val compactHeader: Boolean,
    val useCompactRows: Boolean,
    val inlinePrimaryNumbers: Boolean,
    val minTouchTargetDp: Int,
    val actionColumns: Int,
    val headerPaddingVerticalDp: Int,
    val rowPaddingVerticalDp: Int,
    val listSpacingDp: Int,
    val resultBallSizeDp: Int,
)

internal data class ResultsExportMenuContract(
    val visibleButtonLabel: String,
    val visibleButtonCount: Int,
    val menuLabels: List<String>,
    val usesOverflowMenu: Boolean,
)

internal fun resolveResultsExportMenuContract(showPrint: Boolean): ResultsExportMenuContract {
    return ResultsExportMenuContract(
        visibleButtonLabel = "Exportar",
        visibleButtonCount = 1,
        menuLabels = buildList {
            add("WhatsApp")
            add("Compartir")
            if (showPrint) add("Imprimir")
            add("Guardar")
            add("Copiar")
        },
        usesOverflowMenu = true,
    )
}

internal fun resolveResultsLayout(windowMode: LotteryNetWindowMode): ResultsLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> ResultsLayoutContract(
            compactHeader = true,
            useCompactRows = true,
            inlinePrimaryNumbers = true,
            minTouchTargetDp = 44,
            actionColumns = 3,
            headerPaddingVerticalDp = 6,
            rowPaddingVerticalDp = 6,
            listSpacingDp = 5,
            resultBallSizeDp = 40,
        )

        LotteryNetWindowMode.POS -> ResultsLayoutContract(
            compactHeader = true,
            useCompactRows = true,
            inlinePrimaryNumbers = true,
            minTouchTargetDp = 44,
            actionColumns = 3,
            headerPaddingVerticalDp = 7,
            rowPaddingVerticalDp = 7,
            listSpacingDp = 6,
            resultBallSizeDp = 42,
        )

        else -> ResultsLayoutContract(
            compactHeader = false,
            useCompactRows = false,
            inlinePrimaryNumbers = true,
            minTouchTargetDp = 44,
            actionColumns = 3,
            headerPaddingVerticalDp = 9,
            rowPaddingVerticalDp = 10,
            listSpacingDp = 8,
            resultBallSizeDp = 44,
        )
    }
}

internal fun resolveResultsActionOrder(showPrint: Boolean = true): List<ResultsActionId> {
    return buildList {
        add(ResultsActionId.WHATSAPP)
        add(ResultsActionId.SHARE)
        if (showPrint) add(ResultsActionId.PRINT)
    }
}

internal fun resolveResultsModeWindowTabs(config: AdminSystemModeConfig): List<ResultsModeWindow> {
    return when {
        config.lotteryModeEnabled && config.pickModeEnabled -> listOf(ResultsModeWindow.LOTTERY, ResultsModeWindow.PICK)
        config.pickModeEnabled -> listOf(ResultsModeWindow.PICK)
        else -> listOf(ResultsModeWindow.LOTTERY)
    }
}

private fun filterResultsBoardSectionsForMode(
    sections: List<ResultsBoardSection>,
    config: AdminSystemModeConfig,
    selectedWindow: ResultsModeWindow,
): List<ResultsBoardSection> {
    val target = resolveResultsModeWindowTabs(config).let { tabs ->
        if (selectedWindow in tabs) selectedWindow else tabs.firstOrNull() ?: ResultsModeWindow.LOTTERY
    }
    return sections.filter { section ->
        when (target) {
            ResultsModeWindow.PICK -> section.pickSection
            ResultsModeWindow.LOTTERY -> !section.pickSection
        }
    }
}

@Composable
private fun ResultCard(
    row: ResultsBoardRow,
    pickKind: ResultsPickSectionKind? = null,
    layout: ResultsLayoutContract,
    onShare: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val logoAssetPath = remember(row.lottery) { LotteryAssetResolver().resolveLogoAssetPath(row.lottery) }
    val hasPrimaryResults = hasPrimaryResults(row.result)
    val pick3Digits = row.result?.pick3?.let(::splitPickDigits).orEmpty()
    val pick4Digits = row.result?.pick4?.let(::splitPickDigits).orEmpty()
    CompactPanel(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (row.result != null) {
                        Modifier.clickable {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Resultado", resultClipboardText(row)))
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 10.dp, vertical = layout.rowPaddingVerticalDp.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LotteryLogo(
                        assetPath = logoAssetPath,
                        fallback = row.lottery.name,
                        modifier = Modifier.size(30.dp),
                    )
                    Column {
                        Text(
                            text = row.lottery.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = visual.colors.ink,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.size(1.dp))
                        Text(
                            text = "Sorteo ${row.drawTimeLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (shouldShowIndividualResultShareAction(row.result != null)) {
                        Surface(
                            onClick = onShare,
                            color = visual.colors.resultsSurface,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, visual.colors.results.copy(alpha = 0.22f)),
                        ) {
                            Box(
                                modifier = Modifier.size(30.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Compartir ${row.lottery.name}",
                                    tint = visual.colors.results,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    if (row.isManualOverride) {
                        CompactStatusBadge(label = "Manual", tone = warningColor())
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StatusChip(label = row.stateLabel, tone = row.stateTone)
                    }
                }
            }
            row.sourceLabel?.takeIf { shouldShowResultSourceBadge() && it != "Local" }?.let {
                Spacer(modifier = Modifier.size(3.dp))
                CompactStatusBadge(label = it, tone = visual.colors.neutral)
            }
            if (hasPrimaryResults) {
                Spacer(modifier = Modifier.size(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ResultBall(label = row.result?.first ?: "-", active = row.result?.first != null, prizeLabel = resultPrizeIconLabel(0), sizeDp = layout.resultBallSizeDp)
                    ResultBall(label = row.result?.second ?: "-", active = row.result?.second != null, prizeLabel = resultPrizeIconLabel(1), sizeDp = layout.resultBallSizeDp)
                    ResultBall(label = row.result?.third ?: "-", active = row.result?.third != null, prizeLabel = resultPrizeIconLabel(2), sizeDp = layout.resultBallSizeDp)
                }
            }
            if (pick3Digits.isNotEmpty() || pick4Digits.isNotEmpty()) {
                Spacer(modifier = Modifier.size(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (pick3Digits.isNotEmpty() && pickKind != ResultsPickSectionKind.PICK4) {
                        PickResultRow(label = "Pick 3", digits = pick3Digits)
                    }
                    if (pick4Digits.isNotEmpty() && pickKind != ResultsPickSectionKind.PICK3) {
                        PickResultRow(label = "Pick 4", digits = pick4Digits)
                    }
                }
            } else if (row.result == null) {
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = when (row.stateTone) {
                        ResultsStateTone.PENDING -> "Disponible hasta ${row.drawTimeLabel}."
                        ResultsStateTone.WAITING_SYNC -> "Sorteo cerrado. Falta sincronizar."
                        ResultsStateTone.NO_DRAW -> "No hubo sorteo para esta fecha."
                        ResultsStateTone.MISSING -> "Sin resultado guardado."
                        ResultsStateTone.PUBLISHED -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun ResultBall(
    label: String,
    active: Boolean,
    prizeLabel: String? = null,
    sizeDp: Int = 42,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = sizeDp.dp),
    ) {
        if (prizeLabel != null) {
            Text(
                text = prizeLabel,
                fontSize = resultPrizeIconFontSizeSp().sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(2.dp))
        }
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .background(
                    color = if (active) Color(0xFF12A35F) else Color(0xFFE5E7EB),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.White else Color(0xFF64748B),
            )
        }
    }
}

@Composable
private fun PickResultRow(
    label: String,
    digits: List<String>,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (shouldShowPickResultTextLabel()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = visual.colors.muted,
                modifier = Modifier.widthIn(min = 44.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            digits.forEachIndexed { index, digit ->
                ResultBall(label = digit, active = true, prizeLabel = resultPrizeIconLabel(index))
            }
        }
    }
}

internal fun shouldShowPickResultTextLabel(): Boolean = false

internal fun shouldShowResultSourceBadge(): Boolean = false

internal fun resultPrizeIconFontSizeSp(): Int = 18

internal fun shouldShowIndividualResultShareAction(rowHasResult: Boolean): Boolean = rowHasResult

internal enum class ResultsShareRenderMode {
    CASHIER_GENERIC,
    ADMIN_POSTER,
}

internal fun resolveResultsShareRenderMode(
    role: UserRole,
    payload: ResultsSharePayload,
    whatsappOnly: Boolean,
): ResultsShareRenderMode {
    return if (role == UserRole.CASHIER && shouldShareResultsAsSingleWhatsAppListImage(payload, whatsappOnly, role)) {
        ResultsShareRenderMode.CASHIER_GENERIC
    } else if (role == UserRole.CASHIER && whatsappOnly) {
        ResultsShareRenderMode.CASHIER_GENERIC
    } else {
        ResultsShareRenderMode.ADMIN_POSTER
    }
}

internal fun shouldShareResultsAsSingleWhatsAppListImage(
    payload: ResultsSharePayload,
    whatsappOnly: Boolean,
    role: UserRole,
): Boolean {
    return role == UserRole.CASHIER && whatsappOnly && payload.rows.size > 1
}

internal fun resultsWhatsAppCardsPerImage(): Int = 8

internal data class ResultsWhatsAppCaptureVisualSpec(
    val infoColumnWidthDp: Int,
    val dateFontSp: Int,
    val logoMaxWidthDp: Int,
    val logoMaxHeightDp: Int,
    val logoBoxHeightDp: Int,
    val timeFontSp: Int,
    val ballSizeDp: Int,
    val numberFontSp: Int,
)

internal fun resolveResultsWhatsAppCaptureVisualSpec(): ResultsWhatsAppCaptureVisualSpec {
    return ResultsWhatsAppCaptureVisualSpec(
        infoColumnWidthDp = 228,
        dateFontSp = 22,
        logoMaxWidthDp = 214,
        logoMaxHeightDp = 78,
        logoBoxHeightDp = 88,
        timeFontSp = 23,
        ballSizeDp = 98,
        numberFontSp = 50,
    )
}

internal fun resultsWhatsAppCaptureCanvasWidthPx(screenWidthPx: Int, density: Float): Int {
    val hdWidth = resultsWhatsAppCaptureMinWidthPx()
    val listWidth = (resultsWhatsAppCaptureCanvasWidthDp() * density).toInt()
    return screenWidthPx.coerceAtLeast(listWidth)
        .coerceAtLeast(hdWidth)
        .coerceAtMost(resultsWhatsAppCaptureMaxWidthPx())
}

private fun resultsWhatsAppCaptureCanvasWidthDp(): Int = 760

private fun resultsWhatsAppCaptureMinWidthPx(): Int = 1600

private fun resultsWhatsAppCaptureMaxWidthPx(): Int = 1920

internal fun chunkResultsForWhatsAppCapture(rows: List<com.lotterynet.pro.core.model.ResultShareRow>): List<List<com.lotterynet.pro.core.model.ResultShareRow>> {
    return publishedResultShareRows(rows).chunked(resultsWhatsAppCardsPerImage())
}

internal fun chunkResultsForNormalShareCapture(rows: List<com.lotterynet.pro.core.model.ResultShareRow>): List<List<com.lotterynet.pro.core.model.ResultShareRow>> {
    return publishedResultShareRows(rows).chunked(resultsWhatsAppCardsPerImage())
}

internal data class NormalResultsSharePage(
    val pageIndex: Int,
    val rows: List<com.lotterynet.pro.core.model.ResultShareRow>,
)

internal fun normalResultsSharePages(
    rows: List<com.lotterynet.pro.core.model.ResultShareRow>,
): List<NormalResultsSharePage> {
    return chunkResultsForNormalShareCapture(rows).mapIndexed { index, pageRows ->
        NormalResultsSharePage(pageIndex = index, rows = pageRows)
    }
}

internal fun shouldShareResultsAsPlainText(payload: ResultsSharePayload): Boolean {
    return false
}

private fun isPickOnlyShareRow(row: com.lotterynet.pro.core.model.ResultShareRow): Boolean {
    val hasPrimary = row.first.isNotBlank() || row.second.isNotBlank() || row.third.isNotBlank()
    val hasPick = row.pick3.orEmpty().any(Char::isDigit) || row.pick4.orEmpty().any(Char::isDigit)
    return (hasPick || isPickShareCandidate(row)) && !hasPrimary
}

private fun com.lotterynet.pro.core.model.ResultShareRow.isPrimaryNormalShareRow(): Boolean {
    return first.isNotBlank() || second.isNotBlank() || third.isNotBlank()
}

internal fun shouldIncludeResultInWhatsAppCapture(row: com.lotterynet.pro.core.model.ResultShareRow): Boolean {
    return row.first.isNotBlank() ||
        row.second.isNotBlank() ||
        row.third.isNotBlank() ||
        row.pick3.orEmpty().any(Char::isDigit) ||
        row.pick4.orEmpty().any(Char::isDigit) ||
        isPickShareCandidate(row)
}

internal fun publishedResultShareRows(
    rows: List<com.lotterynet.pro.core.model.ResultShareRow>,
): List<com.lotterynet.pro.core.model.ResultShareRow> {
    return rows.filter(::shouldIncludeResultInWhatsAppCapture)
}

private fun isPickShareCandidate(row: com.lotterynet.pro.core.model.ResultShareRow): Boolean {
    val name = row.displayName.filter(Char::isLetterOrDigit).lowercase(Locale.US)
    return "pick3" in name ||
        "pick4" in name ||
        "daily3" in name ||
        "daily4" in name ||
        "cash4" in name ||
        "win4" in name ||
        "numbers" in name
}

internal enum class ResultsWhatsAppCaptureStyle {
    SCREENSHOT_LIST,
}

internal fun resolveResultsWhatsAppCaptureStyle(): ResultsWhatsAppCaptureStyle {
    return ResultsWhatsAppCaptureStyle.SCREENSHOT_LIST
}

internal fun shouldRunResultsAutoRefresh(
    nowMs: Long,
    lastStartedMs: Long?,
): Boolean {
    val previous = lastStartedMs ?: return true
    return nowMs - previous >= 60_000L
}

internal fun resolveResultsAutoRefreshDelayMs(realtimeEnabled: Boolean): Long {
    return RESULTS_AUTO_REFRESH_DELAY_MS
}

internal fun formatWhatsAppResultDateLabel(date: String): String {
    return runCatching {
        val parsed = SimpleDateFormat("dd-MM-yyyy", Locale.US).parse(date) ?: return date
        val spanishLocale = Locale.forLanguageTag("es-ES")
        SimpleDateFormat("EEEE d MMM", spanishLocale).format(parsed)
            .replaceFirstChar { it.uppercase(spanishLocale) }
            .replace(".", "")
    }.getOrElse { date }
}

internal fun resultPrizeIconLabel(index: Int): String {
    return when (index) {
        0 -> "🥇"
        1 -> "🥈"
        2 -> "🥉"
        3 -> "🏅"
        else -> (index + 1).toString()
    }
}

@Composable
private fun StatusChip(label: String, tone: ResultsStateTone) {
    val background = when (tone) {
        ResultsStateTone.PUBLISHED -> Color(0xFFDCFCE7)
        ResultsStateTone.PENDING -> Color(0xFFE8F0FF)
        ResultsStateTone.WAITING_SYNC -> Color(0xFFFFF5DA)
        ResultsStateTone.NO_DRAW -> Color(0xFFE5E7EB)
        ResultsStateTone.MISSING -> Color(0xFFF3F4F6)
    }
    val foreground = when (tone) {
        ResultsStateTone.PUBLISHED -> gainColor()
        ResultsStateTone.PENDING -> Color(0xFF2154D6)
        ResultsStateTone.WAITING_SYNC -> warningColor()
        ResultsStateTone.NO_DRAW -> Color(0xFF374151)
        ResultsStateTone.MISSING -> Color(0xFF4B5563)
    }
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = foreground,
        )
    }
}

@Composable
private fun EmptyResultsCard(
    selectedDate: String,
    syncMessage: String?,
) {
    CompactEmptyState("Sin resultados para $selectedDate. ${syncMessage ?: "Haz una actualización manual si ya cerró el sorteo."}")
}

private fun resultClipboardText(row: ResultsBoardRow): String {
    val numbers = listOfNotNull(
        row.result?.first?.takeIf { it.isNotBlank() },
        row.result?.second?.takeIf { it.isNotBlank() },
        row.result?.third?.takeIf { it.isNotBlank() },
    ).joinToString("-")
    return "${row.lottery.name}: $numbers"
}

private fun whatsappResultNumbers(row: ResultsBoardRow): List<String> {
    val primary = listOfNotNull(
        row.result?.first?.takeIf { it.isNotBlank() },
        row.result?.second?.takeIf { it.isNotBlank() },
        row.result?.third?.takeIf { it.isNotBlank() },
    )
    if (primary.isNotEmpty()) return primary
    row.result?.pick4?.takeIf { it.isNotBlank() }?.let { return splitPickDigits(it) }
    row.result?.pick3?.takeIf { it.isNotBlank() }?.let { return splitPickDigits(it) }
    return listOf("-", "-", "-")
}

private fun hasPrimaryResults(result: LotteryResult?): Boolean {
    return !result?.first.isNullOrBlank() ||
        !result?.second.isNullOrBlank() ||
        !result?.third.isNullOrBlank()
}

internal fun splitPickDigits(raw: String): List<String> {
    return raw.trim().filter(Char::isDigit).map { it.toString() }
}

private fun todayOffset(offsetDays: Int): String {
    val now = System.currentTimeMillis() + offsetDays * 24L * 60L * 60L * 1000L
    return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date(now))
}

internal fun shouldForceRemoteOnInitialResultsLoad(selectedDate: String, today: String): Boolean {
    return false
}

internal fun shouldSkipInitialResultsHydration(
    selectedDate: String,
    today: String,
    hasCompleteLocalResults: Boolean,
): Boolean {
    return hasCompleteLocalResults && dayDiffFromToday(selectedDate = selectedDate, today = today) < -2
}

internal fun shouldApplyResultsRefreshForSelectedDate(
    refreshDate: String,
    selectedDate: String,
): Boolean {
    return refreshDate == selectedDate
}

internal fun resolveResultsDateCacheWarmupDates(
    defaultDate: String,
    today: String,
): List<String> {
    return listOf(
        defaultDate,
        today,
        offsetResultDate(today, -1),
        offsetResultDate(today, -2),
    ).distinct()
}

internal fun offsetResultDate(date: String, offsetDays: Int): String {
    val format = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }
    val parsed = format.parse(date) ?: return date
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Santo_Domingo"), Locale.US).apply {
        time = parsed
        add(Calendar.DAY_OF_YEAR, offsetDays)
    }
    return format.format(calendar.time)
}

internal fun resolveLocalResultsForSelectedDate(
    selectedDate: String,
    defaultDate: String,
    initialResults: List<LotteryResult>,
    loadLocalResults: (String) -> List<LotteryResult>,
): List<LotteryResult> {
    return if (selectedDate == defaultDate) {
        initialResults
    } else {
        loadLocalResults(selectedDate)
    }
}

internal fun resolveResultsBoardClockUtcMs(
    selectedDate: String,
    today: String,
    liveUtcMs: Long,
    stableUtcMs: Long,
): Long {
    return if (selectedDate == today) liveUtcMs else stableUtcMs
}

private fun dayDiff(date: String): Int {
    return dayDiffFromToday(selectedDate = date, today = todayOffset(0))
}

private fun dayDiffFromToday(selectedDate: String, today: String): Int {
    val format = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }
    val selected = format.parse(selectedDate)?.time ?: return 0
    val todayValue = format.parse(today)?.time ?: return 0
    return ((selected - todayValue) / (24L * 60L * 60L * 1000L)).toInt()
}

private fun presentResultsDateLabel(date: String): String {
    return when (dayDiff(date)) {
        0 -> "Hoy"
        -1 -> "Ayer"
        -2 -> "Anteayer"
        else -> formatDayName(date)
    }
}

private fun formatDayName(date: String): String {
    val format = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }
    val dateValue = format.parse(date) ?: return ""
    return SimpleDateFormat("EEEE", Locale.forLanguageTag("es-DO")).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(dateValue).replaceFirstChar { it.uppercase() }
}

private fun buildResultsBoardRows(
    lotteries: List<LotteryCatalogItem>,
    results: List<LotteryResult>,
    selectedDate: String,
    nowUtcMs: Long,
    operationTerritory: LotteryTerritory,
    closePolicy: LotteryClosePolicy,
    noDrawLotteryIds: Set<String> = emptySet(),
): List<ResultsBoardRow> {
    val resultsForSelectedDate = results.filter { result -> resultBelongsToDate(result, selectedDate) }
    val resultsByLottery = resultsForSelectedDate.associateBy(PickResultIdentityResolver::canonicalKeyForResult)
    val dayFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }
    val todayDate = dayFormat.parse(dayFormat.format(Date(nowUtcMs))) ?: Date(nowUtcMs)
    val selectedDay = dayFormat.parse(selectedDate) ?: todayDate
    val visibleCatalogLotteries = lotteries.filterNot { lottery ->
        shouldSkipGenericPickCatalogRow(lottery, resultsForSelectedDate)
    }.dedupeResultsCatalogRowsByCanonicalDraw()
    val catalogRows = visibleCatalogLotteries.map { lottery ->
        val matched = resultsByLottery[PickResultIdentityResolver.canonicalKeyForLottery(lottery)]
            ?: resultsByLottery[lottery.id]
            ?: resultsForSelectedDate.firstOrNull { shouldMatchResultByCatalogName(lottery, it) }
        buildResultsBoardRow(
            lottery = lottery,
            matched = matched,
            selectedDay = selectedDay,
            todayDate = todayDate,
            nowUtcMs = nowUtcMs,
            operationTerritory = operationTerritory,
            closePolicy = closePolicy,
            forcedNoDraw = lottery.id in noDrawLotteryIds,
        )
    }
    val catalogIds = visibleCatalogLotteries.flatMap { lottery ->
        listOf(lottery.id, PickResultIdentityResolver.canonicalKeyForLottery(lottery))
    }.toSet()
    val catalogNames = visibleCatalogLotteries.map { it.name.lowercase(Locale.US) }.toSet()
    val syntheticRows = resultsForSelectedDate
        .filter { result ->
            result.lotteryId !in catalogIds &&
                PickResultIdentityResolver.canonicalKeyForResult(result) !in catalogIds &&
                shouldCreateSyntheticPickResultRow(result, lotteries)
        }
        .mapNotNull { result ->
            val lottery = buildSyntheticPickLotteryForResult(result) ?: return@mapNotNull null
            buildResultsBoardRow(
                lottery = lottery,
                matched = result,
                selectedDay = selectedDay,
                todayDate = todayDate,
                nowUtcMs = nowUtcMs,
                operationTerritory = operationTerritory,
                closePolicy = closePolicy,
                forcedNoDraw = false,
            )
        }
    return (catalogRows + syntheticRows).sortedWith(compareBy<ResultsBoardRow>(
        { parseResultsClockMinutes(it.drawTimeLabel) },
        { it.lottery.name.lowercase(Locale.US) },
    ))
}

private fun List<LotteryCatalogItem>.dedupeResultsCatalogRowsByCanonicalDraw(): List<LotteryCatalogItem> {
    val byCanonicalDraw = linkedMapOf<String, LotteryCatalogItem>()
    forEach { lottery ->
        val key = if (isCatalogPickLottery(lottery)) {
            PickResultIdentityResolver.canonicalKeyForLottery(lottery)
        } else {
            lottery.id
        }
        val current = byCanonicalDraw[key]
        if (current == null || resultsCatalogRowPriority(lottery) < resultsCatalogRowPriority(current)) {
            byCanonicalDraw[key] = lottery
        }
    }
    return byCanonicalDraw.values.toList()
}

private fun resultsCatalogRowPriority(lottery: LotteryCatalogItem): Int {
    val identity = "${lottery.id} ${lottery.name}".uppercase(Locale.US)
    return when {
        "NUMBERS" in identity -> 0
        "WIN-4" in identity || "WIN 4" in identity -> 0
        lottery.id in setOf("19", "20", "21", "22") -> 0
        "PICK-3" in identity || "PICK 3" in identity -> 2
        "PICK-4" in identity || "PICK 4" in identity -> 2
        else -> 1
    }
}

internal fun buildResultsBoardRowVerificationSummary(
    lotteries: List<LotteryCatalogItem>,
    results: List<LotteryResult>,
    selectedDate: String,
    nowUtcMs: Long,
    operationTerritory: LotteryTerritory,
    closePolicy: LotteryClosePolicy,
    noDrawLotteryIds: Set<String> = emptySet(),
): List<Pair<String, Boolean>> {
    return buildResultsBoardRows(
        lotteries = lotteries,
        results = results,
        selectedDate = selectedDate,
        nowUtcMs = nowUtcMs,
        operationTerritory = operationTerritory,
        closePolicy = closePolicy,
        noDrawLotteryIds = noDrawLotteryIds,
    ).map { row -> row.lottery.id to (row.result != null) }
}

internal fun buildResultsBoardStateVerificationSummary(
    lotteries: List<LotteryCatalogItem>,
    results: List<LotteryResult>,
    selectedDate: String,
    nowUtcMs: Long,
    operationTerritory: LotteryTerritory,
    closePolicy: LotteryClosePolicy,
    noDrawLotteryIds: Set<String> = emptySet(),
): List<Pair<String, String>> {
    return buildResultsBoardRows(
        lotteries = lotteries,
        results = results,
        selectedDate = selectedDate,
        nowUtcMs = nowUtcMs,
        operationTerritory = operationTerritory,
        closePolicy = closePolicy,
        noDrawLotteryIds = noDrawLotteryIds,
    ).map { row -> row.lottery.id to row.stateLabel }
}

internal fun buildResultsBoardSectionVerificationSummary(
    lotteries: List<LotteryCatalogItem>,
    results: List<LotteryResult>,
    selectedDate: String,
    nowUtcMs: Long,
    operationTerritory: LotteryTerritory,
    closePolicy: LotteryClosePolicy,
    systemModeConfig: AdminSystemModeConfig,
    selectedWindow: ResultsModeWindow,
    noDrawLotteryIds: Set<String> = emptySet(),
): List<Pair<String, Int>> {
    val rows = buildResultsBoardRows(
        lotteries = lotteries,
        results = results,
        selectedDate = selectedDate,
        nowUtcMs = nowUtcMs,
        operationTerritory = operationTerritory,
        closePolicy = closePolicy,
        noDrawLotteryIds = noDrawLotteryIds,
    )
    return filterResultsBoardSectionsForMode(
        sections = buildResultsBoardSections(rows),
        config = systemModeConfig,
        selectedWindow = selectedWindow,
    ).map { section -> section.title to section.rows.size }
}

internal fun shouldSkipGenericPickCatalogRow(
    lottery: LotteryCatalogItem,
    results: List<LotteryResult>,
): Boolean {
    val id = lottery.id.uppercase(Locale.US)
    if (!isGenericDynamicPickLotteryId(id)) return false
    return results.any { result ->
        result.lotteryId.uppercase(Locale.US).startsWith("$id-")
    }
}

internal fun shouldMatchResultByCatalogName(
    lottery: LotteryCatalogItem,
    result: LotteryResult,
): Boolean {
    if (isRemotePickResultId(result.lotteryId) && !result.lotteryId.equals(lottery.id, ignoreCase = true)) {
        return false
    }
    return result.lotteryName.equals(lottery.name, ignoreCase = true)
}

internal fun shouldCreateSyntheticPickResultRow(
    result: LotteryResult,
    catalogLotteries: List<LotteryCatalogItem>,
): Boolean {
    val id = result.lotteryId.uppercase(Locale.US)
    if (!isRemotePickResultId(id)) return false
    val genericParent = Regex("""^(US-P[34]-([A-Z]{2}|DC))(?:-|$)""")
        .find(id)
        ?.groupValues
        ?.getOrNull(1)
        ?: return false
    return catalogLotteries.any { lottery ->
        lottery.id.equals(genericParent, ignoreCase = true) && isGenericDynamicPickLotteryId(lottery.id.uppercase(Locale.US))
    }
}

private fun isGenericDynamicPickLotteryId(id: String): Boolean {
    return id.matches(Regex("""US-P[34]-([A-Z]{2}|DC)"""))
}

private fun isRemotePickResultId(id: String): Boolean {
    val upperId = id.uppercase(Locale.US)
    return upperId.startsWith("US-P3-") || upperId.startsWith("US-P4-")
}

private fun buildResultsBoardRow(
    lottery: LotteryCatalogItem,
    matched: LotteryResult?,
    selectedDay: Date,
    todayDate: Date,
    nowUtcMs: Long,
    operationTerritory: LotteryTerritory,
    closePolicy: LotteryClosePolicy,
    forcedNoDraw: Boolean = false,
): ResultsBoardRow {
    val decision = closePolicy.resolveCloseDecision(
        lottery = lottery,
        operationTerritory = operationTerritory,
        nowUtcMs = nowUtcMs,
    )
    val drawTime = decision.drawTime ?: lottery.baseDrawTime
    val minutesToDraw = minutesUntilDraw(drawTime, nowUtcMs, operationTerritory)
    val selectedDateIsPast = selectedDay.before(todayDate)
    val selectedDateIsToday = selectedDay == todayDate
    val canPublishForDraw = shouldPublishResultForDraw(
        selectedDateIsPast = selectedDateIsPast,
        selectedDateIsToday = selectedDateIsToday,
        minutesToDraw = minutesToDraw,
    )
    val normalizedStatus = matched?.status?.trim()?.lowercase(Locale.US)
    val hasPublished = matched?.let(::hasPublishedNumbers) == true && canPublishForDraw
    val noDraw = (matched?.let(::isNoDrawResult) == true || (forcedNoDraw && matched == null)) && canPublishForDraw
    val explicitPending = normalizedStatus == RESULT_STATUS_PENDING || normalizedStatus == RESULT_STATUS_MISSING_FROM_SOURCES
    val state = when {
        hasPublished -> ResultsStateTone.PUBLISHED
        noDraw -> ResultsStateTone.NO_DRAW
        explicitPending && selectedDateIsToday && hasDrawTimePassed(drawTime, nowUtcMs, operationTerritory) -> ResultsStateTone.WAITING_SYNC
        explicitPending -> ResultsStateTone.PENDING
        selectedDay.after(todayDate) -> ResultsStateTone.PENDING
        selectedDay.before(todayDate) -> ResultsStateTone.MISSING
        hasDrawTimePassed(drawTime, nowUtcMs, operationTerritory) -> ResultsStateTone.WAITING_SYNC
        else -> ResultsStateTone.PENDING
    }
    return ResultsBoardRow(
        lottery = lottery,
        result = if (hasPublished || matched?.let(::isNoDrawResult) == true) matched else null,
        drawTimeLabel = formatResultsClock12(drawTime),
        stateLabel = when (state) {
            ResultsStateTone.PUBLISHED -> "Publicado"
            ResultsStateTone.PENDING -> "Pendiente"
            ResultsStateTone.WAITING_SYNC -> "Esperando resultado"
            ResultsStateTone.NO_DRAW -> "No hubo sorteo"
            ResultsStateTone.MISSING -> "Sin resultado"
        },
        stateTone = state,
        sourceLabel = matched?.source?.takeIf { it.isNotBlank() }?.let(::presentResultsSourceLabel) ?: "Local",
        minutesToDraw = minutesToDraw,
        isManualOverride = matched?.isManualOverride == true,
    )
}

private fun buildManualResultOptions(rows: List<ResultsBoardRow>): List<ManualResultOption> {
    return rows
        .distinctBy { it.lottery.id }
        .sortedBy { it.lottery.name.lowercase(Locale.getDefault()) }
        .map { row ->
            ManualResultOption(
                resultId = row.lottery.id,
                name = row.lottery.name,
                label = "${row.lottery.name} · ${row.drawTimeLabel}",
                game = resolveManualResultGame(row),
                currentNumber = resolvedManualResultNumber(row.result),
            )
        }
}

internal fun buildManualResultOptionsFromLotteries(
    lotteries: List<LotteryCatalogItem>,
    results: List<LotteryResult>,
): List<ManualResultOption> {
    val resultByLotteryId = results.associateBy { it.lotteryId }
    return lotteries
        .distinctBy { it.id }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
        .map { lottery ->
            val result = resultByLotteryId[lottery.id]
            ManualResultOption(
                resultId = lottery.id,
                name = lottery.name,
                label = "${lottery.name} · ${lottery.baseDrawTime}",
                game = resolveManualResultGameForLottery(lottery, result),
                currentNumber = resolvedManualResultNumber(result),
            )
        }
}

internal fun shouldShowManualResultsEditor(role: UserRole): Boolean {
    return role == UserRole.ADMIN || role == UserRole.MASTER
}

private fun resolveManualResultGame(row: ResultsBoardRow): String {
    return resolveManualResultGameForLottery(row.lottery, row.result)
}

internal fun resolveManualResultGameForLottery(
    lottery: LotteryCatalogItem,
    result: LotteryResult?,
): String {
    if (result?.pick4 != null) return "pick4"
    if (result?.pick3 != null) return "pick3"
    val normalizedName = lottery.name.lowercase(Locale.US)
    val normalizedId = lottery.id.uppercase(Locale.US)
    return when {
        normalizedId in setOf("21", "22") ||
            "pick 4" in normalizedName ||
            "daily 4" in normalizedName ||
            "cash 4" in normalizedName ||
            "win 4" in normalizedName ||
            normalizedId.startsWith("US-P4-") -> "pick4"
        normalizedId in setOf("19", "20") ||
            "pick 3" in normalizedName ||
            "daily 3" in normalizedName ||
            "cash 3" in normalizedName ||
            "play3" in normalizedName ||
            normalizedId.startsWith("US-P3-") -> "pick3"
        else -> "lottery"
    }
}

internal fun validateManualResultInput(value: String, game: String): Boolean {
    val trimmed = value.trim()
    return when (game) {
        "pick3" -> Regex("""^\d-\d-\d$""").matches(trimmed)
        "pick4" -> Regex("""^\d-\d-\d-\d$""").matches(trimmed)
        else -> Regex("""^\d{2}-\d{2}-\d{2}$""").matches(trimmed)
    }
}

internal fun manualResultPlaceholder(game: String?): String {
    return when (game) {
        "pick3" -> "0-0-0"
        "pick4" -> "0-0-0-0"
        else -> "00-00-00"
    }
}

internal fun manualResultValidationMessage(game: String): String {
    return when (game) {
        "pick3" -> "Usa formato Pick 3: 0-0-0"
        "pick4" -> "Usa formato Pick 4: 0-0-0-0"
        else -> "Usa formato de lotería: 00-00-00"
    }
}

internal fun resolvedManualResultNumber(result: LotteryResult?): String? {
    result ?: return null
    return result.pick4 ?: result.pick3 ?: listOfNotNull(result.first, result.second, result.third).takeIf { it.isNotEmpty() }?.joinToString("-")
}

internal fun hasCompleteLocalResultsForExpectedDate(
    results: List<LotteryResult>,
    expectedResultIds: Set<String>,
): Boolean {
    if (results.isEmpty()) return false
    if (expectedResultIds.isEmpty()) return true
    val available = results.mapTo(linkedSetOf(), PickResultIdentityResolver::canonicalKeyForResult)
    val expected = expectedResultIds.mapTo(linkedSetOf(), PickResultIdentityResolver::canonicalKeyForExpectedId)
    return available.containsAll(expected)
}

internal fun buildSyntheticPickLotteryForResult(result: LotteryResult): LotteryCatalogItem? {
    val id = result.lotteryId.trim()
    val upperId = id.uppercase(Locale.US)
    val pickType = when {
        upperId.startsWith("US-P4-") || !result.pick4.isNullOrBlank() -> "Pick4"
        upperId.startsWith("US-P3-") || !result.pick3.isNullOrBlank() -> "Pick3"
        else -> return null
    }
    if (!upperId.startsWith("US-P3-") && !upperId.startsWith("US-P4-")) return null
    val stateCode = upperId.split("-").getOrNull(2)
        ?.lowercase(Locale.US)
        ?.takeIf { it.matches(Regex("[a-z]{2}|dc")) }
        ?: return null
    val resolvedSchedule = UsPickScheduleResolver.resolve(id, result.lotteryName)
    val drawTime = resolvedSchedule?.drawTime ?: inferRemotePickDrawTime(id, result.lotteryName)
    val rawName = result.lotteryName.orEmpty().trim()
    val baseName = rawName.ifBlank {
        "${stateCode.uppercase(Locale.US)} ${if (pickType == "Pick4") "Pick 4" else "Pick 3"}"
    }
    val name = if (drawTime.isBlank() || baseName.contains(drawTime, ignoreCase = true)) {
        baseName
    } else {
        "$baseName $drawTime"
    }
    val logoFolder = if (pickType == "Pick4") "pick4" else "pick3"
    return LotteryCatalogItem(
        id = id,
        name = name,
        type = pickType,
        baseDrawTime = drawTime.ifBlank { "11:00 PM" },
        baseCloseTime = drawTime.ifBlank { "10:55 PM" },
        colorHex = if (pickType == "Pick4") "#16a34a" else "#0ea5e9",
        logoAssetPath = "lot-logos/us-pick/$logoFolder/$stateCode.svg",
        territory = LotteryTerritory.USA,
        timeZoneId = resolvedSchedule?.timeZoneId,
    )
}

private fun inferRemotePickDrawTime(id: String, name: String?): String {
    val text = "$id ${name.orEmpty()}".uppercase(Locale.US)
    Regex("""(\d{1,2})-(\d{2})-(AM|PM)""").find(text)?.let { match ->
        return "${match.groupValues[1].toInt()}:${match.groupValues[2]} ${match.groupValues[3]}"
    }
    Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)""").find(text)?.let { match ->
        return "${match.groupValues[1].toInt()}:${match.groupValues[2]} ${match.groupValues[3]}"
    }
    return when {
        "MIDDAY" in text || hasRemotePickDrawToken(text, "DIA") -> "1:00 PM"
        "EVENING" in text || "TARDE" in text -> "7:00 PM"
        "NIGHT" in text || "NOCHE" in text -> "11:00 PM"
        hasRemotePickDrawToken(text, "DAY") -> "1:00 PM"
        else -> "11:00 PM"
    }
}

private fun hasRemotePickDrawToken(text: String, token: String): Boolean {
    return Regex("""(^|[^A-Z0-9])${Regex.escape(token)}([^A-Z0-9]|$)""").containsMatchIn(text)
}

internal fun presentResultsSourceLabel(raw: String?): String {
    return when (raw?.trim()?.lowercase(Locale.US)) {
        "supabase", "remote", "server", "api" -> "Servidor"
        "manual-override", "manual override", "manual" -> "Manual"
        "local", "cache", "device" -> "Local"
        "no_draw", "no draw", "no sorteo", "sin sorteo" -> "No sorteo"
        else -> raw?.replaceFirstChar { it.uppercase() } ?: "Local"
    }
}

internal fun formatResultsClock12(raw: String?): String {
    val text = raw?.trim().orEmpty()
    if (text.isBlank()) return text
    val minutes = parseResultsClockMinutes(text)
    if (minutes == Int.MAX_VALUE) return text
    val hour24 = minutes / 60
    val minute = (minutes % 60).toString().padStart(2, '0')
    val suffix = if (hour24 >= 12) "PM" else "AM"
    val hour12 = when (val normalized = hour24 % 12) {
        0 -> 12
        else -> normalized
    }
    return "$hour12:$minute $suffix"
}

internal fun parseResultsClockMinutes(raw: String): Int {
    val text = raw.trim().uppercase(Locale.US)
    val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM))?""").find(text) ?: return Int.MAX_VALUE
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    val meridiem = match.groupValues.getOrNull(3).orEmpty()
    if (meridiem == "AM" && hour == 12) hour = 0
    if (meridiem == "PM" && hour < 12) hour += 12
    return hour * 60 + minute
}

private fun ResultsBoardRow.toShareRow(): com.lotterynet.pro.core.model.ResultShareRow {
    val localLogo = LotteryAssetResolver().resolveLogoAssetPath(lottery)
    return com.lotterynet.pro.core.model.ResultShareRow(
        displayName = lottery.name,
        first = result?.first.orEmpty(),
        second = result?.second.orEmpty(),
        third = result?.third.orEmpty(),
        pick3 = result?.pick3?.takeIf { it.isNotBlank() },
        pick4 = result?.pick4?.takeIf { it.isNotBlank() },
        source = sourceLabel,
        accentColor = lottery.colorHex,
        logoAssetPath = localLogo,
        drawTimeLabel = drawTimeLabel,
        stateLabel = stateLabel,
    )
}

private fun hasPublishedNumbers(result: LotteryResult): Boolean {
    return !result.first.isNullOrBlank() ||
        !result.second.isNullOrBlank() ||
        !result.third.isNullOrBlank() ||
        !result.pick3.isNullOrBlank() ||
        !result.pick4.isNullOrBlank()
}

private fun hasDrawTimePassed(
    drawTime: String,
    nowUtcMs: Long,
    operationTerritory: LotteryTerritory,
): Boolean {
    return (minutesUntilDraw(drawTime, nowUtcMs, operationTerritory) ?: Int.MAX_VALUE) <= 0
}

private fun minutesUntilDraw(
    drawTime: String,
    nowUtcMs: Long,
    operationTerritory: LotteryTerritory,
): Int? {
    val zone = TimeZone.getTimeZone(
        LotteryTimeZones.zoneId(operationTerritory),
    )
    val calendar = java.util.Calendar.getInstance(zone).apply { timeInMillis = nowUtcMs }
    val nowMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM))?""").find(drawTime.trim().uppercase(Locale.US)) ?: return null
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    val meridiem = match.groupValues.getOrNull(3).orEmpty()
    if (meridiem == "AM" && hour == 12) hour = 0
    if (meridiem == "PM" && hour < 12) hour += 12
    return (hour * 60 + minute) - nowMinutes
}

internal fun shouldAutoRefreshResultsFromServer(
    selectedDateIsToday: Boolean,
    hasWaitingResult: Boolean,
    hasRecoverableNoDrawResult: Boolean,
    realtimeEnabled: Boolean,
): Boolean {
    return selectedDateIsToday && (hasRecoverableNoDrawResult || hasWaitingResult)
}

internal fun shouldPublishResultForDraw(
    selectedDateIsPast: Boolean,
    selectedDateIsToday: Boolean,
    minutesToDraw: Int?,
): Boolean {
    if (selectedDateIsPast) return true
    if (!selectedDateIsToday) return false
    return (minutesToDraw ?: Int.MAX_VALUE) <= 0
}
