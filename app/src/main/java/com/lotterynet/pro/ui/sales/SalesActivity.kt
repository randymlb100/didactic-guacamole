package com.lotterynet.pro.ui.sales

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.calendar.LotteryAvailabilityResolver
import com.lotterynet.pro.core.calendar.LotteryClosePolicy
import com.lotterynet.pro.core.calendar.StaticHolidayCalendarRepository
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.export.StaticExportTemplateRepository
import com.lotterynet.pro.core.export.TicketSecurity
import com.lotterynet.pro.core.format.formatWholeAmount
import com.lotterynet.pro.core.model.CloseState
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.PickPlayMode
import com.lotterynet.pro.core.model.SaleDraft
import com.lotterynet.pro.core.model.SaleDraftSnapshot
import com.lotterynet.pro.core.model.SaleStagedRow
import com.lotterynet.pro.core.model.SaleValidationResult
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.dominicanDayKey
import com.lotterynet.pro.core.model.formatPlayDisplayNumber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.network.ProductionNetworkGuard
import com.lotterynet.pro.core.notification.LotteryClosingNotifier
import com.lotterynet.pro.core.notification.WinningTicketNotifier
import com.lotterynet.pro.core.notification.buildLotteryClosingAlerts
import com.lotterynet.pro.core.perf.PosPerformanceBudget
import com.lotterynet.pro.core.render.LocalRenderCacheRepository
import com.lotterynet.pro.core.render.ticketRenderCacheKey
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.realtime.LotterynetRealtimeSubscription
import com.lotterynet.pro.core.results.ResultsScraperOrchestrator
import com.lotterynet.pro.core.results.ResultsSupabaseStore
import com.lotterynet.pro.core.results.PickResultIdentityResolver
import com.lotterynet.pro.core.catalog.UsPickScheduleResolver
import com.lotterynet.pro.core.results.TicketPrizeReconciler
import com.lotterynet.pro.core.results.resultBelongsToDate
import com.lotterynet.pro.core.sales.LigarBuildTarget
import com.lotterynet.pro.core.sales.SaleExposureEngine
import com.lotterynet.pro.core.sales.SaleValidator
import com.lotterynet.pro.core.sales.BackendTicketPlay
import com.lotterynet.pro.core.sales.BackendTicketRequest
import com.lotterynet.pro.core.sales.CashierLimits
import com.lotterynet.pro.core.sales.SupabaseTicketBackendClient
import com.lotterynet.pro.core.sales.calculateGlobalLimitExposure
import com.lotterynet.pro.core.sales.calculateGlobalStagedExposure
import com.lotterynet.pro.core.sales.presentSupabaseTicketBackendMessage
import com.lotterynet.pro.core.sales.resolveExposureCashierKeys
import com.lotterynet.pro.core.sales.resolveExposureOwnerKey
import com.lotterynet.pro.core.sales.resolveLigarBuildTargets
import com.lotterynet.pro.core.sales.resolveSaleLimitRemainingRows
import com.lotterynet.pro.core.sales.shouldReportSupabaseTicketBackendFailure
import com.lotterynet.pro.core.sales.ticketBackendUserMessage
import com.lotterynet.pro.core.sales.resolveSaleExposureLimitBucket
import com.lotterynet.pro.core.sales.SaleLimitRemainingRow
import com.lotterynet.pro.core.operations.canonicalizeTicketOwnerForSession
import com.lotterynet.pro.core.operations.filterCashiersForSession
import com.lotterynet.pro.core.repository.NativeSyncQueueRepository
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.CashierLimitCloudSyncCoordinator
import com.lotterynet.pro.core.sync.cashierLimitRemoteKey
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.NativeUsersBootstrapper
import com.lotterynet.pro.core.sync.OperationalSyncThrottle
import com.lotterynet.pro.core.sync.SyncFreshnessType
import com.lotterynet.pro.core.sync.SyncGovernor
import com.lotterynet.pro.core.sync.buildSyncFreshnessKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.core.storage.CashierSalesLimitInputs
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.core.storage.decodeAdminSystemModeConfig
import com.lotterynet.pro.core.storage.effectiveSystemModeConfigForSession
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import com.lotterynet.pro.core.storage.LocalCashierPrizePayoutRepository
import com.lotterynet.pro.core.storage.LocalPrizeConfigRepository
import com.lotterynet.pro.core.storage.LocalSaleDraftRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalAdminLotteryConfigRepository
import com.lotterynet.pro.core.storage.LocalBrandingRepository
import com.lotterynet.pro.core.storage.LocalResultsRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.sync.LocalSyncFreshnessRepository
import com.lotterynet.pro.core.storage.LocalThermalPrinterRepository
import com.lotterynet.pro.core.storage.LocalTrustedClockRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.manualDisabledLotteriesRemoteKey
import com.lotterynet.pro.core.storage.systemModeRemoteKey
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.remote.SupabaseEdgeClient
import com.lotterynet.pro.core.remote.isSupabaseAuthRequired
import com.lotterynet.pro.core.printing.ThermalTicketRenderer
import com.lotterynet.pro.core.printing.BluetoothThermalPrinter
import com.lotterynet.pro.core.printing.IntegratedThermalPrinter
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactAdaptiveGrid
import com.lotterynet.pro.ui.common.CompactEmptyState
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactRecordRow
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.LotteryLogo
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.ScreenChromeAction
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.TicketSaveSyncUiContract
import com.lotterynet.pro.ui.common.TicketSaveSyncStage
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.lossColor
import com.lotterynet.pro.ui.common.openShellMenu
import com.lotterynet.pro.ui.common.resolveTicketSaveSyncUiContract
import com.lotterynet.pro.ui.common.resolveOverflowLayoutContract
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.warningColor
import com.lotterynet.pro.ui.tickets.TicketOfficialActivity
import com.lotterynet.pro.ui.tickets.TicketLookupActivity
import com.lotterynet.pro.ui.tickets.resolveTicketOutputBancaName
import com.lotterynet.pro.ui.printer.PrinterActivity
import com.lotterynet.pro.ui.results.expectedResultIdsForMode
import com.lotterynet.pro.ui.login.LoginActivity
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.shell.ShellActivity
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

internal fun resolveSalesStartupSystemModeConfig(
    session: com.lotterynet.pro.core.model.ActiveSession,
    usersRepository: LocalUsersRepository,
    adminLotteryConfigRepository: LocalAdminLotteryConfigRepository,
): AdminSystemModeConfig {
    val localConfig = adminLotteryConfigRepository.getSystemModeConfig()
    val ownerKey = resolveOperationalOwnerKey(session)
    val serverConfig = runCatching {
        SupabaseMasterConfigRemoteStore(
            edgeClient = SupabaseEdgeClient(connectTimeoutMs = 2_500, readTimeoutMs = 3_500),
        ).fetchValue(systemModeRemoteKey(ownerKey))?.toString()?.let(::decodeAdminSystemModeConfig)
    }.getOrNull()
    val baseConfig = serverConfig?.let(adminLotteryConfigRepository::saveSystemModeConfig) ?: localConfig
    return effectiveSystemModeConfigForSession(
        config = baseConfig,
        session = session,
        accounts = usersRepository.getAdmins() + usersRepository.getSupervisors() + usersRepository.getCashiers(),
    )
}

internal fun resolveSalesStartupManualDisabledLotteryIds(
    session: com.lotterynet.pro.core.model.ActiveSession,
    adminLotteryConfigRepository: LocalAdminLotteryConfigRepository,
): Set<String> {
    val localIds = adminLotteryConfigRepository.getManualDisabledLotteryIds()
    val ownerKey = resolveOperationalOwnerKey(session)
    val serverIds = runCatching {
        SupabaseMasterConfigRemoteStore(
            edgeClient = SupabaseEdgeClient(connectTimeoutMs = 2_500, readTimeoutMs = 3_500),
        ).fetchValue(manualDisabledLotteriesRemoteKey(ownerKey))
            ?.toString()
            ?.let(adminLotteryConfigRepository::cacheManualDisabledLotteryConfig)
    }.getOrNull()
    return serverIds ?: localIds
}

class SalesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val session = LocalSessionRepository(this).getActiveSession()
            if (redirectIfNativeDestinationBlocked(this, session?.role, NativeDestination.SALES)) {
                return
            }
            checkNotNull(session)
            WinningTicketNotifier.requestPermissionIfNeeded(this, session.role)
            val usersRepository = LocalUsersRepository(this)
            usersRepository.touchSession(session)
            val catalogRepository = StaticLotteryCatalogRepository()
            val trustedClockRepository = LocalTrustedClockRepository(this)
            val saleDraftRepository = LocalSaleDraftRepository(this)
            val salesRepository = LocalSalesRepository(this)
            val resultsRepository = LocalResultsRepository(this)
            val ownerKey = resolveOperationalOwnerKey(session)
            val cashierPrizePayoutRepository = LocalCashierPrizePayoutRepository(this)
            val nativeSyncQueueRepository = NativeTicketSyncQueueRepository(this)
            val nativeTicketCloudSyncCoordinator = NativeTicketCloudSyncCoordinator(
                salesRepository = salesRepository,
                queueRepository = nativeSyncQueueRepository,
            )
            val nativeOperationalSyncCoordinator = NativeOperationalSyncCoordinator(
                ticketGateway = nativeTicketCloudSyncCoordinator,
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
            val adminLotteryConfigRepository = LocalAdminLotteryConfigRepository(this)
            val systemModeConfig = resolveSalesStartupSystemModeConfig(
                session = session,
                usersRepository = usersRepository,
                adminLotteryConfigRepository = adminLotteryConfigRepository,
            )
            val allLotteries = catalogRepository.getAllLotteries()
            val runtimeLotteries = resolveSaleRuntimeLotteriesForSystemMode(allLotteries, systemModeConfig)
            val calendarRule = catalogRepository.getCalendarRule()
            val resultsSaleGuardOrchestrator = ResultsScraperOrchestrator(
                remoteStore = ResultsSupabaseStore(),
                localResultsRepository = resultsRepository,
                freshnessRepository = LocalSyncFreshnessRepository(this),
                freshnessKeyFactory = { date ->
                    buildSyncFreshnessKey(
                        type = SyncFreshnessType.RESULTS,
                        ownerKey = ownerKey,
                        banca = session.banca,
                        dateKey = date,
                    )
                },
                expectedResultIdsProvider = { date ->
                    expectedResultIdsForMode(
                        lotteries = allLotteries,
                        config = systemModeConfig,
                        calendarRule = calendarRule,
                        date = date,
                    )
                },
                syncGovernor = SyncGovernor.shared,
            )
            val cashierSalesLimitRepository = LocalCashierSalesLimitRepository(this)
            val cashierLimitCloudSyncCoordinator = CashierLimitCloudSyncCoordinator(cashierSalesLimitRepository)
            val saleExposureEngine = SaleExposureEngine(this, salesRepository)
            val holidayRepository = StaticHolidayCalendarRepository(
                dominicanLotteryIds = calendarRule.dominicanLotteryIds,
                americanLotteryIds = calendarRule.americanLotteryIds,
            )
            val closePolicy = LotteryClosePolicy(trustedClockRepository, holidayRepository)
            val availabilityResolver = LotteryAvailabilityResolver(
                trustedClockRepository = trustedClockRepository,
                holidayCalendarRepository = holidayRepository,
                calendarRule = calendarRule,
            )

            setContent {
                LotteryNetComposeTheme {
                    SalesRoute(
                        session = session,
                        role = session.role,
                        banca = session.banca,
                        territory = normalizeTerritory(session.territory),
                        lotteries = runtimeLotteries,
                        trustedClockRepository = trustedClockRepository,
                        closePolicy = closePolicy,
                        availabilityResolver = availabilityResolver,
                        saleDraftRepository = saleDraftRepository,
                        salesRepository = salesRepository,
                        resultsRepository = resultsRepository,
                        resultsSaleGuardOrchestrator = resultsSaleGuardOrchestrator,
                        manualClosedLotteryIds = resolveSalesStartupManualDisabledLotteryIds(
                            session = session,
                            adminLotteryConfigRepository = adminLotteryConfigRepository,
                        ),
                        initialSystemModeConfig = systemModeConfig,
                        nativeSyncQueueRepository = nativeSyncQueueRepository,
                        nativeTicketCloudSyncCoordinator = nativeTicketCloudSyncCoordinator,
                        nativeOperationalSyncCoordinator = nativeOperationalSyncCoordinator,
                        saleExposureEngine = saleExposureEngine,
                        cashierSalesLimitRepository = cashierSalesLimitRepository,
                        cashierLimitCloudSyncCoordinator = cashierLimitCloudSyncCoordinator,
                        usersRepository = usersRepository,
                        preloadMessage = intent?.getStringExtra(EXTRA_PRELOAD_MESSAGE),
                        preselectLotteryId = intent?.getStringExtra(EXTRA_PRESELECT_LOTTERY_ID),
                        ticketReconciler = ticketReconciler,
                    )
                }
            }
        } catch (error: Throwable) {
            NativeCrashReporter(this).recordHandled("SalesActivity.onCreate", error)
            val sessionRepository = LocalSessionRepository(this)
            if (!shouldKeepSalesSessionAfterStartupFailure(error)) {
                sessionRepository.saveActiveSession(null)
                sessionRepository.saveSessionSnapshot(null)
            }
            val statusMessage = if (shouldKeepSalesSessionAfterStartupFailure(error)) {
                "Sesion del servidor requerida. Vuelve a iniciar sesion con internet."
            } else {
                "Venta fallo al abrir. Revisa el mensaje de error en login."
            }
            Toast.makeText(this, statusMessage, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(LoginActivity.EXTRA_LOGIN_STATUS, statusMessage)
            })
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        runCatching {
            LocalTrustedClockRepository(this).getTrustedUtcMs()
        }.onFailure { error ->
            NativeCrashReporter(this).recordHandled("SalesActivity.resumeClock", error)
        }
    }

    private fun normalizeTerritory(raw: String?): LotteryTerritory {
        return if (raw.equals("USA", ignoreCase = true) || raw.equals("US", ignoreCase = true)) {
            LotteryTerritory.USA
        } else {
            LotteryTerritory.RD
        }
    }

    companion object {
        const val EXTRA_PRELOAD_MESSAGE = "sales_preload_message"
        const val EXTRA_PRESELECT_LOTTERY_ID = LotteryClosingNotifier.EXTRA_PRESELECT_LOTTERY_ID
    }
}

internal fun shouldKeepSalesSessionAfterStartupFailure(error: Throwable): Boolean {
    return isSupabaseAuthRequired(error)
}

internal enum class SaleInputTarget {
    NUMBER,
    AMOUNT,
}

internal enum class SaleDrawDay {
    TODAY,
    TOMORROW,
}

internal data class SaleDrawDayContract(
    val dayKey: String,
    val resultsDayKey: String,
    val title: String,
    val subtitle: String,
    val isFuture: Boolean,
)

internal fun resolveSaleDrawDayContract(
    mode: SaleDrawDay,
    nowUtcMs: Long,
    territory: LotteryTerritory,
): SaleDrawDayContract {
    val zone = when (territory) {
        LotteryTerritory.USA -> "America/New_York"
        LotteryTerritory.RD -> "America/Santo_Domingo"
    }
    val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone(zone), Locale.US).apply {
        timeInMillis = nowUtcMs
    }
    if (mode == SaleDrawDay.TOMORROW) {
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(zone)
    }.format(calendar.time)
    return SaleDrawDayContract(
        dayKey = dayKey,
        resultsDayKey = dayKey,
        title = if (mode == SaleDrawDay.TOMORROW) "Mañana" else "Hoy",
        subtitle = if (mode == SaleDrawDay.TOMORROW) {
            "Venta futura · ${formatCountdownToNextSaleDay(nowUtcMs, territory)}"
        } else {
            "Venta de hoy"
        },
        isFuture = mode == SaleDrawDay.TOMORROW,
    )
}

internal fun canUseFutureSale(role: UserRole): Boolean = false

internal fun canUseFutureSaleControls(role: UserRole, featureEnabled: Boolean): Boolean {
    return canUseFutureSale(role) && featureEnabled
}

internal fun resolveSaleFutureModeAfterRole(
    mode: SaleDrawDay,
    role: UserRole,
    featureEnabled: Boolean = true,
): SaleDrawDay {
    return if (canUseFutureSaleControls(role, featureEnabled)) mode else SaleDrawDay.TODAY
}

private fun futureSaleDecisionClockMs(mode: SaleDrawDay, nowUtcMs: Long, territory: LotteryTerritory): Long {
    if (mode == SaleDrawDay.TODAY) return nowUtcMs
    val zone = when (territory) {
        LotteryTerritory.USA -> "America/New_York"
        LotteryTerritory.RD -> "America/Santo_Domingo"
    }
    return java.util.Calendar.getInstance(TimeZone.getTimeZone(zone), Locale.US).apply {
        timeInMillis = nowUtcMs
        add(java.util.Calendar.DAY_OF_YEAR, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 1)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun formatCountdownToNextSaleDay(nowUtcMs: Long, territory: LotteryTerritory): String {
    val zone = when (territory) {
        LotteryTerritory.USA -> "America/New_York"
        LotteryTerritory.RD -> "America/Santo_Domingo"
    }
    val nextDay = java.util.Calendar.getInstance(TimeZone.getTimeZone(zone), Locale.US).apply {
        timeInMillis = nowUtcMs
        add(java.util.Calendar.DAY_OF_YEAR, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    val remainingMs = (nextDay - nowUtcMs).coerceAtLeast(0L)
    val totalMinutes = remainingMs / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return "faltan %02d:%02d".format(Locale.US, hours, minutes)
}

internal data class SaleEntryCarryState(
    val activeInput: SaleInputTarget,
    val number: String,
    val amount: String,
    val replaceAmountOnNextDigit: Boolean,
)

internal data class NumberAdvanceState(
    val canAdvanceToAmount: Boolean,
    val showNumberError: Boolean,
)

private data class PendingDuplicatePlay(
    val validation: SaleValidationResult,
    val selectedLotteries: List<LotteryCatalogItem>,
    val existingRow: SaleStagedRow,
)

private data class PendingPublishedResultPlay(
    val validation: SaleValidationResult,
    val selectedLotteries: List<LotteryCatalogItem>,
    val normalizedAmount: Double,
    val lotteryNames: List<String>,
)

internal fun resolveNumberAdvanceState(
    number: String,
    hasDetectedPlay: Boolean,
    hasPartialHint: Boolean,
): NumberAdvanceState {
    val hasNumber = number.isNotBlank()
    val canAdvance = hasNumber && hasDetectedPlay && !hasPartialHint
    return NumberAdvanceState(
        canAdvanceToAmount = canAdvance,
        showNumberError = hasNumber && !canAdvance,
    )
}

internal fun resolveAutoAdvanceInputAfterNumber(
    activeInput: SaleInputTarget,
    number: String,
    selectedLotteries: List<LotteryCatalogItem>,
    classicMode: String,
): SaleInputTarget {
    return activeInput
}

internal fun resolveEnterInputTarget(
    activeInput: SaleInputTarget,
    canAdvanceToAmount: Boolean,
): SaleInputTarget {
    return if (activeInput == SaleInputTarget.NUMBER && canAdvanceToAmount) {
        SaleInputTarget.AMOUNT
    } else {
        activeInput
    }
}

internal fun shouldReverseVentaStagedListForLatestPlay(): Boolean = false

internal data class VentaQrLookupActionContract(
    val label: String,
    val lookupMode: String,
    val autoScan: Boolean,
)

internal data class VentaQrLookupChoice(
    val label: String,
    val lookupMode: String,
    val autoScan: Boolean,
)

internal fun resolveVentaQrLookupActionContract(): VentaQrLookupActionContract {
    return VentaQrLookupActionContract(
        label = "QR",
        lookupMode = "buscar",
        autoScan = false,
    )
}

internal fun resolveVentaQrLookupChoices(): List<VentaQrLookupChoice> = listOf(
    VentaQrLookupChoice(label = "Pagar ticket", lookupMode = "pagar", autoScan = true),
    VentaQrLookupChoice(label = "Copiar ticket", lookupMode = "duplicar", autoScan = true),
)

internal const val CASHIER_LIMIT_PULL_INTERVAL_MS: Long = 60_000L
internal const val SALES_EXPOSURE_REFRESH_INTERVAL_MS: Long = 60_000L
internal const val SALES_RESULTS_WINNER_REFRESH_INTERVAL_MS: Long = 120_000L
internal const val SALES_SERVER_TICKET_VALIDATION_TIMEOUT_MS: Long = 4_500L

internal fun shouldPollSalesServerAccountGuardInBackground(
    role: UserRole,
    realtimeEnabled: Boolean,
): Boolean = false

internal fun shouldPollSalesResultsWinnerRefreshInBackground(
    realtimeEnabled: Boolean,
): Boolean = !realtimeEnabled

internal enum class SalesServerAccountGuard(val message: String?) {
    ALLOW(null),
    CASHIER_BLOCKED_BY_ADMIN("Tus credenciales están bloqueadas por admin."),
    ADMIN_BLOCKED_BY_MASTER("Tu admin está bloqueado por Master."),
    MISSING("Cuenta no disponible en servidor."),
}

internal fun resolveSalesServerAccountGuard(
    session: com.lotterynet.pro.core.model.ActiveSession?,
    accounts: List<UserAccount>,
): SalesServerAccountGuard {
    if (session == null || session.role != UserRole.CASHIER) return SalesServerAccountGuard.ALLOW
    val account = accounts.firstOrNull { account ->
        account.id.equals(session.userId, ignoreCase = true) ||
            account.user.equals(session.username, ignoreCase = true)
    } ?: return SalesServerAccountGuard.MISSING
    val ownerAdmin = accounts.firstOrNull { owner ->
        owner.role == UserRole.ADMIN &&
            (
                owner.id.equals(session.adminId.orEmpty(), ignoreCase = true) ||
                    owner.user.equals(session.adminUser.orEmpty(), ignoreCase = true) ||
                    owner.id.equals(account.adminId.orEmpty(), ignoreCase = true) ||
                    owner.user.equals(account.adminUser.orEmpty(), ignoreCase = true)
                )
    }
    if (ownerAdmin?.active == false) return SalesServerAccountGuard.ADMIN_BLOCKED_BY_MASTER
    return if (account.active) SalesServerAccountGuard.ALLOW else SalesServerAccountGuard.CASHIER_BLOCKED_BY_ADMIN
}

internal fun shouldShowVentaInlineFeedbackBanner(
    feedbackMessage: String?,
    feedbackIsError: Boolean,
    numberHasError: Boolean,
): Boolean {
    return !numberHasError && feedbackIsError && !feedbackMessage.isNullOrBlank()
}

internal fun shouldShowVentaTicketSaveSyncStatus(): Boolean = false

internal fun findDuplicateStagedRows(
    existingRows: List<SaleStagedRow>,
    validation: SaleValidationResult,
    selectedLotteries: List<LotteryCatalogItem>,
): List<SaleStagedRow> {
    val play = validation.resolvedPlay ?: return emptyList()
    if (!validation.isValid || validation.normalizedAmount == null) return emptyList()
    return if (play.playType == "SP") {
        val primary = selectedLotteries.firstOrNull() ?: return emptyList()
        val secondary = selectedLotteries.getOrNull(1)
        existingRows.filter { row ->
            row.lotteryId == primary.id &&
                row.secondaryLotteryId == secondary?.id &&
                row.playType == play.playType &&
                row.number == play.normalizedNumber
        }
    } else {
        val selectedIds = selectedLotteries.map { it.id }.toSet()
        existingRows.filter { row ->
            row.lotteryId in selectedIds &&
                row.secondaryLotteryId == null &&
                row.playType == play.playType &&
                row.number == play.normalizedNumber
        }
    }
}

internal fun resolveDuplicatePlayPromptText(
    existingRow: SaleStagedRow,
    amountToAdd: Double,
): String {
    return "La jugada ${existingRow.displayNumber} ya tiene ${formatWholeAmount(existingRow.amount)}. Quieres sumar ${formatWholeAmount(amountToAdd)}?"
}

internal data class VentaKeypadLayoutContract(
    val showStatsBadges: Boolean,
    val keySpacingDp: Int,
    val keyHeightDp: Int,
    val numberKeyFontSp: Int,
    val commandKeyFontSp: Int,
    val strongTextOnly: Boolean,
    val secondaryTextUsesInk: Boolean,
    val totalAboveKeypad: Boolean,
)

internal data class VentaStagedListLayoutContract(
    val headerVerticalPaddingDp: Int,
    val rowVerticalPaddingDp: Int,
    val separatorAlpha: Float,
    val numberWeight: Int,
    val enlargeNumberFont: Boolean,
    val prioritizeListSpace: Boolean,
)

internal data class VentaKeyVisualContract(
    val dangerTone: Boolean,
    val useIcon: Boolean,
    val contentDescription: String?,
)

internal data class VentaEntryStripLayoutContract(
    val jugadaWeight: Float,
    val montoWeight: Float,
    val limitWidthDp: Int,
    val limitFontSp: Int,
    val itemSpacingDp: Int,
)

internal data class VentaPosLiteContract(
    val includeSales: Boolean,
    val windowMode: LotteryNetWindowMode,
    val useTightSellingLayout: Boolean,
)

internal enum class SaleLimitBadgeTone {
    GREEN,
    RED,
    NEUTRAL,
}

internal fun resolveVentaPosLiteContract(
    windowMode: LotteryNetWindowMode,
    posLiteEnabled: Boolean = false,
): VentaPosLiteContract {
    val tight = posLiteEnabled || windowMode == LotteryNetWindowMode.POS_TIGHT
    return VentaPosLiteContract(
        includeSales = true,
        windowMode = if (tight) LotteryNetWindowMode.POS_TIGHT else windowMode,
        useTightSellingLayout = tight,
    )
}

internal fun resolveVentaEntryStripLayout(windowMode: LotteryNetWindowMode): VentaEntryStripLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> VentaEntryStripLayoutContract(
            jugadaWeight = 0.96f,
            montoWeight = 1.04f,
            limitWidthDp = 100,
            limitFontSp = 15,
            itemSpacingDp = 2,
        )
        LotteryNetWindowMode.POS -> VentaEntryStripLayoutContract(
            jugadaWeight = 0.96f,
            montoWeight = 1.04f,
            limitWidthDp = 106,
            limitFontSp = 15,
            itemSpacingDp = 2,
        )
        else -> VentaEntryStripLayoutContract(
            jugadaWeight = 1f,
            montoWeight = 1f,
            limitWidthDp = 116,
            limitFontSp = 16,
            itemSpacingDp = 3,
        )
    }
}

internal fun resolvePostAddFeedbackMessage(): String? = null

internal fun resolveVentaStagedListLayout(windowMode: LotteryNetWindowMode): VentaStagedListLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> VentaStagedListLayoutContract(
            headerVerticalPaddingDp = 2,
            rowVerticalPaddingDp = 2,
            separatorAlpha = 0f,
            numberWeight = 900,
            enlargeNumberFont = false,
            prioritizeListSpace = true,
        )
        LotteryNetWindowMode.POS -> VentaStagedListLayoutContract(
            headerVerticalPaddingDp = 2,
            rowVerticalPaddingDp = 3,
            separatorAlpha = 0f,
            numberWeight = 900,
            enlargeNumberFont = false,
            prioritizeListSpace = false,
        )
        else -> VentaStagedListLayoutContract(
            headerVerticalPaddingDp = 3,
            rowVerticalPaddingDp = 4,
            separatorAlpha = 0f,
            numberWeight = 900,
            enlargeNumberFont = false,
            prioritizeListSpace = false,
        )
    }
}

internal fun resolveVentaKeyVisualContract(key: String): VentaKeyVisualContract {
    return when (key) {
        "⌫" -> VentaKeyVisualContract(
            dangerTone = true,
            useIcon = true,
            contentDescription = "Borrar",
        )
        else -> VentaKeyVisualContract(
            dangerTone = false,
            useIcon = false,
            contentDescription = null,
        )
    }
}

internal fun resolveVentaKeypadLayout(windowMode: LotteryNetWindowMode): VentaKeypadLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> VentaKeypadLayoutContract(
            showStatsBadges = false,
            keySpacingDp = 0,
            keyHeightDp = 44,
            numberKeyFontSp = 22,
            commandKeyFontSp = 14,
            strongTextOnly = true,
            secondaryTextUsesInk = true,
            totalAboveKeypad = true,
        )

        LotteryNetWindowMode.POS -> VentaKeypadLayoutContract(
            showStatsBadges = false,
            keySpacingDp = 0,
            keyHeightDp = 48,
            numberKeyFontSp = 24,
            commandKeyFontSp = 15,
            strongTextOnly = true,
            secondaryTextUsesInk = true,
            totalAboveKeypad = true,
        )

        else -> VentaKeypadLayoutContract(
            showStatsBadges = true,
            keySpacingDp = 2,
            keyHeightDp = 50,
            numberKeyFontSp = 26,
            commandKeyFontSp = 16,
            strongTextOnly = true,
            secondaryTextUsesInk = true,
            totalAboveKeypad = true,
        )
    }
}

internal data class SaleTicketSeller(
    val sellerId: String,
    val sellerUser: String,
    val role: UserRole,
    val displayLabel: String,
)

internal data class VentaSellerKeyVisualContract(
    val iconOnly: Boolean,
    val backgroundTone: String,
    val iconTone: String,
)

internal data class AdminSellerPickerRow(
    val account: UserAccount,
    val title: String,
    val subtitle: String,
    val enabled: Boolean,
    val selected: Boolean,
)

internal fun resolveSaleTicketSeller(
    session: com.lotterynet.pro.core.model.ActiveSession,
    selectedCashier: UserAccount?,
): SaleTicketSeller {
    return if (session.role == UserRole.ADMIN && selectedCashier != null) {
        SaleTicketSeller(
            sellerId = selectedCashier.id,
            sellerUser = selectedCashier.user,
            role = UserRole.CASHIER,
            displayLabel = selectedCashier.displayName ?: selectedCashier.user,
        )
    } else {
        SaleTicketSeller(
            sellerId = session.userId,
            sellerUser = session.username,
            role = session.role,
            displayLabel = session.username,
        )
    }
}

internal fun canUseAdminResultGraceForSale(profileRole: UserRole): Boolean {
    return profileRole == UserRole.ADMIN
}

internal fun resolvePostTicketAdminSellerId(
    role: UserRole,
    selectedSellerId: String?,
): String? = if (role == UserRole.ADMIN) null else selectedSellerId

internal fun resolveVentaSellerKeyVisualContract(active: Boolean): VentaSellerKeyVisualContract {
    return VentaSellerKeyVisualContract(
        iconOnly = true,
        backgroundTone = "blue",
        iconTone = "white",
    )
}

internal fun resolveAdminSellerPickerRows(
    cashiers: List<UserAccount>,
    selectedSellerId: String?,
): List<AdminSellerPickerRow> {
    return cashiers
        .map { cashier ->
            val number = resolveCashierNaturalNumber(cashier)
            val title = number?.let { "Cajero $it" }
                ?: cashier.displayName?.trim()?.takeIf { it.isNotBlank() }
                ?: cashier.user
            val customName = resolveCashierCustomName(cashier, number)
            val subtitleParts = listOfNotNull(
                customName,
                cashier.user.takeIf { it.isNotBlank() },
                if (cashier.active) null else "Bloqueado",
            )
            AdminSellerPickerRow(
                account = cashier,
                title = title,
                subtitle = subtitleParts.joinToString(" · "),
                enabled = cashier.active,
                selected = cashier.id == selectedSellerId,
            )
        }
        .sortedWith(
            compareBy<AdminSellerPickerRow> { resolveCashierNaturalNumber(it.account) ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.US) }
                .thenBy { it.account.user.lowercase(Locale.US) },
        )
}

private fun resolveCashierNaturalNumber(cashier: UserAccount): Int? {
    val candidates = listOfNotNull(
        cashier.displayName,
        cashier.user,
        cashier.cashierPrefix,
        cashier.createdLabel,
    )
    val regex = Regex("""cajero\D*0*(\d+)""", RegexOption.IGNORE_CASE)
    return candidates.firstNotNullOfOrNull { candidate ->
        regex.find(candidate)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}

private fun resolveCashierCustomName(cashier: UserAccount, cashierNumber: Int?): String? {
    val displayName = cashier.displayName?.trim()?.takeIf { it.isNotBlank() } ?: return cashier.banca
    if (cashierNumber == null) return displayName.takeIf { !it.equals(cashier.user, ignoreCase = true) }
    val prefix = Regex("""^\s*cajero\D*0*$cashierNumber\s*[-:·]?\s*""", RegexOption.IGNORE_CASE)
    return displayName
        .replace(prefix, "")
        .trim()
        .takeIf { it.isNotBlank() && !it.equals(cashier.user, ignoreCase = true) }
        ?: cashier.banca
}

internal fun resolveVentaKeyRows(
    role: UserRole = UserRole.CASHIER,
    pickKeypad: Boolean = false,
): List<List<String>> {
    if (pickKeypad) {
        return listOf(
            listOf("7", "8", "9", "⌫"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "/", "*"),
            listOf("OK", "PRINT"),
        )
    }
    return if (role == UserRole.ADMIN) {
        listOf(
            listOf("7", "8", "9", "⌫"),
            listOf("4", "5", "6", "PRINT"),
            listOf("1", "2", "3", "SELLER"),
            listOf("0", "00", "OK"),
        )
    } else {
        listOf(
            listOf("7", "8", "9", "⌫"),
            listOf("4", "5", "6", "PRINT"),
            listOf("1", "2", "3", "000"),
            listOf("0", "00", "OK"),
        )
    }
}

internal fun resolveVentaKeyWeight(key: String, pickKeypad: Boolean = false): Float {
    return when {
        key == "OK" -> 2f
        pickKeypad && key == "PRINT" -> 1.35f
        else -> 1f
    }
}

internal data class PickAssistedEntry(
    val digits: String,
    val lotteryType: String,
    val pickMode: PickPlayMode,
)

internal fun resolvePickAssistedEntry(raw: String): PickAssistedEntry? {
    val trimmed = raw.trim().uppercase(Locale.US)
    if (trimmed.length < 2) return null
    val suffix = trimmed.last()
    val mode = normalizePickModeSuffix(suffix) ?: return null
    val digits = trimmed.dropLast(1).filter(Char::isDigit)
    val lotteryType = when (digits.length) {
        3 -> "Pick3"
        4 -> "Pick4"
        else -> return null
    }
    return PickAssistedEntry(
        digits = digits,
        lotteryType = lotteryType,
        pickMode = mode,
    )
}

internal fun resolvePickAssistedEntry(
    raw: String,
    selectedLotteries: List<LotteryCatalogItem>,
    pickMode: PickPlayMode,
): PickAssistedEntry? {
    resolvePickAssistedEntry(raw)?.let { return it }
    if (selectedLotteries.none(::supportsPickModes)) return null
    val digits = raw.trim().filter(Char::isDigit)
    val lotteryType = when (digits.length) {
        3 -> "Pick3"
        4 -> "Pick4"
        else -> return null
    }
    return PickAssistedEntry(
        digits = digits,
        lotteryType = lotteryType,
        pickMode = pickMode,
    )
}

private fun normalizePickModeSuffix(suffix: Char): PickPlayMode? {
    return when (suffix.uppercaseChar()) {
        'B', '+' -> PickPlayMode.BOX
        'S', '-' -> PickPlayMode.STRAIGHT
        else -> null
    }
}

private fun normalizePickSuffixForInput(suffix: Char): Char? {
    return when (suffix.uppercaseChar()) {
        'B', 'S', '+', '-', '*', '/' -> suffix.uppercaseChar()
        else -> null
    }
}

internal fun applyPickModeKeyToNumber(
    number: String,
    pickMode: PickPlayMode,
): String {
    val digits = number.filter(Char::isDigit)
    val suffix = if (pickMode == PickPlayMode.BOX) "B" else "S"
    return digits + suffix
}

internal fun applyPickModeSymbolToNumber(
    number: String,
    symbol: String,
): String {
    val suffix = symbol.firstOrNull() ?: return number.filter(Char::isDigit)
    val normalized = normalizePickSuffixForInput(suffix) ?: return number.filter(Char::isDigit)
    val digits = number.filter(Char::isDigit)
    return digits + normalized
}

internal data class PickStraightBoxShortcut(
    val digits: String,
    val lotteryType: String,
)

internal fun resolvePickStraightBoxShortcut(raw: String): PickStraightBoxShortcut? {
    val trimmed = raw.trim().uppercase(Locale.US)
    if (!trimmed.endsWith("*")) return null
    val digits = trimmed.dropLast(1).filter(Char::isDigit)
    val lotteryType = when (digits.length) {
        3 -> "Pick3"
        4 -> "Pick4"
        else -> return null
    }
    return PickStraightBoxShortcut(digits = digits, lotteryType = lotteryType)
}

internal fun sanitizeSaleNumberInput(
    raw: String,
    supportsPickModes: Boolean,
): String {
    if (!supportsPickModes) return raw.filter(Char::isDigit)
    val trimmed = raw.trim().uppercase(Locale.US)
    val suffix = trimmed.lastOrNull()?.let(::normalizePickSuffixForInput)
    val digits = trimmed.filter(Char::isDigit)
    return digits + (suffix?.toString() ?: "")
}

internal fun resolvePickModeKeyNextInput(
    number: String,
    pickMode: PickPlayMode,
    activeInput: SaleInputTarget,
): SaleInputTarget {
    if (activeInput != SaleInputTarget.NUMBER) return activeInput
    val assisted = resolvePickAssistedEntry(applyPickModeKeyToNumber(number, pickMode))
    return if (assisted != null) SaleInputTarget.AMOUNT else SaleInputTarget.NUMBER
}

internal fun filterPickAssistedLotteries(
    lotteries: List<LotteryCatalogItem>,
    assistedEntry: PickAssistedEntry?,
): List<LotteryCatalogItem> {
    assistedEntry ?: return lotteries
    return lotteries.filter { normalizePickLotteryType(it.type) == assistedEntry.lotteryType }
}

internal fun filterVentaLotteryPickerForMode(
    lotteries: List<LotteryCatalogItem>,
    selectedLotteries: List<LotteryCatalogItem>,
    assistedEntry: PickAssistedEntry?,
): List<LotteryCatalogItem> {
    assistedEntry?.let { return dedupePickLotteryPickerRows(filterPickAssistedLotteries(lotteries, it)) }
    val selectedIsPickMode = selectedLotteries.isNotEmpty() && selectedLotteries.all(::supportsPickModes)
    return if (selectedIsPickMode) dedupePickLotteryPickerRows(lotteries.filter(::supportsPickModes)) else lotteries
}

internal fun dedupePickLotteryPickerRows(lotteries: List<LotteryCatalogItem>): List<LotteryCatalogItem> {
    val byCanonicalDraw = linkedMapOf<String, LotteryCatalogItem>()
    lotteries.forEach { lottery ->
        if (!supportsPickModes(lottery)) {
            byCanonicalDraw[lottery.id] = lottery
            return@forEach
        }
        val key = PickResultIdentityResolver.canonicalKeyForLottery(lottery)
        val current = byCanonicalDraw[key]
        if (current == null || pickLotteryPickerPriority(lottery) < pickLotteryPickerPriority(current)) {
            byCanonicalDraw[key] = lottery
        }
    }
    return byCanonicalDraw.values.toList()
}

private fun pickLotteryPickerPriority(lottery: LotteryCatalogItem): Int {
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

internal fun resolvePickAssistedLotterySelection(
    currentSelection: List<String>,
    lotteries: List<LotteryCatalogItem>,
    assistedEntry: PickAssistedEntry?,
): List<String> {
    assistedEntry ?: return currentSelection
    val compatibleLotteries = filterPickAssistedLotteries(lotteries, assistedEntry)
    if (compatibleLotteries.isEmpty()) return emptyList()
    val compatibleIds = compatibleLotteries.map { it.id }
    val retained = currentSelection.filter { it in compatibleIds }.distinct()
    if (retained.isNotEmpty()) return retained
    resolveEquivalentPickLotterySelection(currentSelection, lotteries, compatibleLotteries)?.let { equivalent ->
        return listOf(equivalent.id)
    }
    return listOf(compatibleIds.first())
}

private fun resolveEquivalentPickLotterySelection(
    currentSelection: List<String>,
    lotteries: List<LotteryCatalogItem>,
    compatibleLotteries: List<LotteryCatalogItem>,
): LotteryCatalogItem? {
    val byId = lotteries.associateBy { it.id }
    val currentIdentities = currentSelection
        .mapNotNull { selectedId -> byId[selectedId] }
        .mapNotNull(PickResultIdentityResolver::resolveLottery)
    if (currentIdentities.isEmpty()) return null
    return currentIdentities.firstNotNullOfOrNull { currentIdentity ->
        compatibleLotteries.firstOrNull { candidate ->
            val candidateIdentity = PickResultIdentityResolver.resolveLottery(candidate) ?: return@firstOrNull false
            candidateIdentity.stateCode == currentIdentity.stateCode &&
                candidateIdentity.canonicalKey.substringAfterLast("|") == currentIdentity.canonicalKey.substringAfterLast("|")
        }
    }
}

internal fun filterSaleLotteriesForSystemMode(
    lotteries: List<LotteryCatalogItem>,
    config: AdminSystemModeConfig,
): List<LotteryCatalogItem> {
    return when {
        config.lotteryModeEnabled && config.pickModeEnabled -> lotteries
        config.pickModeEnabled -> lotteries.filter(::supportsPickModes)
        else -> lotteries.filterNot(::supportsPickModes)
    }
}

internal fun resolveSaleRuntimeLotteriesForSystemMode(
    lotteries: List<LotteryCatalogItem>,
    config: AdminSystemModeConfig,
): List<LotteryCatalogItem> {
    return filterSaleLotteriesForSystemMode(lotteries, config)
}

private fun ventaKeyShape(
    rowIndex: Int,
    columnIndex: Int,
    rowCount: Int,
    columnCount: Int,
): RoundedCornerShape {
    return RoundedCornerShape(0.dp)
}

internal data class TicketPreviewAccessContract(
    val showAction: Boolean,
    val shouldSaveBeforeOpen: Boolean,
)

internal data class TicketPrintOpenContract(
    val showAction: Boolean,
    val stageCurrentPlayBeforeSave: Boolean,
    val saveBeforeOpen: Boolean,
    val openLatestTicket: Boolean,
    val fallbackMessage: String?,
)

internal fun resolveTicketPreviewAccessContract(
    stagedRowCount: Int,
    hasLatestTicket: Boolean,
): TicketPreviewAccessContract {
    return when {
        stagedRowCount > 0 -> TicketPreviewAccessContract(
            showAction = true,
            shouldSaveBeforeOpen = true,
        )

        hasLatestTicket -> TicketPreviewAccessContract(
            showAction = true,
            shouldSaveBeforeOpen = false,
        )

        else -> TicketPreviewAccessContract(
            showAction = false,
            shouldSaveBeforeOpen = false,
        )
    }
}

internal fun resolveTicketPrintOpenContract(
    stagedRowCount: Int,
    hasLatestTicket: Boolean,
    currentEntryValid: Boolean,
    number: String,
    amount: String,
    hasPendingConfirmation: Boolean,
): TicketPrintOpenContract {
    val hasCurrentEntry = number.isNotBlank() || amount.isNotBlank()
    return when {
        hasPendingConfirmation -> TicketPrintOpenContract(
            showAction = true,
            stageCurrentPlayBeforeSave = false,
            saveBeforeOpen = false,
            openLatestTicket = false,
            fallbackMessage = null,
        )

        stagedRowCount > 0 -> TicketPrintOpenContract(
            showAction = true,
            stageCurrentPlayBeforeSave = false,
            saveBeforeOpen = true,
            openLatestTicket = false,
            fallbackMessage = null,
        )

        currentEntryValid && number.isNotBlank() && amount.isNotBlank() -> TicketPrintOpenContract(
            showAction = true,
            stageCurrentPlayBeforeSave = true,
            saveBeforeOpen = true,
            openLatestTicket = false,
            fallbackMessage = null,
        )

        hasCurrentEntry -> TicketPrintOpenContract(
            showAction = true,
            stageCurrentPlayBeforeSave = false,
            saveBeforeOpen = false,
            openLatestTicket = false,
            fallbackMessage = "Completa la jugada y el monto antes de imprimir",
        )

        hasLatestTicket -> TicketPrintOpenContract(
            showAction = true,
            stageCurrentPlayBeforeSave = false,
            saveBeforeOpen = false,
            openLatestTicket = true,
            fallbackMessage = null,
        )

        else -> TicketPrintOpenContract(
            showAction = false,
            stageCurrentPlayBeforeSave = false,
            saveBeforeOpen = false,
            openLatestTicket = false,
            fallbackMessage = "No hay jugadas para imprimir",
        )
    }
}

internal fun resolvePostAddCarryState(amount: String): SaleEntryCarryState {
    return SaleEntryCarryState(
        activeInput = SaleInputTarget.NUMBER,
        number = "",
        amount = amount,
        replaceAmountOnNextDigit = amount.isNotBlank(),
    )
}

internal fun applySaleKeypadInput(
    activeInput: SaleInputTarget,
    key: String,
    number: String,
    amount: String,
    replaceAmountOnNextDigit: Boolean,
): SaleEntryCarryState {
    return when (activeInput) {
        SaleInputTarget.NUMBER -> when (key) {
            "CLR" -> SaleEntryCarryState(activeInput, "", amount, replaceAmountOnNextDigit)
            "⌫" -> SaleEntryCarryState(activeInput, number.dropLast(1), amount, replaceAmountOnNextDigit)
            "." -> SaleEntryCarryState(activeInput, number, amount, replaceAmountOnNextDigit)
            "00" -> SaleEntryCarryState(activeInput, number + "00", amount, replaceAmountOnNextDigit)
            else -> SaleEntryCarryState(activeInput, number + key, amount, replaceAmountOnNextDigit)
        }

        SaleInputTarget.AMOUNT -> when (key) {
            "CLR" -> SaleEntryCarryState(activeInput, number, "", false)
            "⌫" -> SaleEntryCarryState(activeInput, number, amount.dropLast(1), false)
            "." -> SaleEntryCarryState(activeInput, number, appendSaleAmountKey(amount, key, replaceAmountOnNextDigit), false)
            "00" -> {
                val nextAmount = if (replaceAmountOnNextDigit) "00" else amount + "00"
                SaleEntryCarryState(activeInput, number, nextAmount, false)
            }

            else -> {
                val nextAmount = appendSaleAmountKey(amount, key, replaceAmountOnNextDigit)
                SaleEntryCarryState(activeInput, number, nextAmount, false)
            }
        }
    }
}

private fun appendSaleAmountKey(
    amount: String,
    key: String,
    replaceAmountOnNextDigit: Boolean,
): String {
    val base = if (replaceAmountOnNextDigit) "" else amount
    if (key == ".") {
        return when {
            base.contains('.') -> base
            base.isBlank() -> "0."
            else -> "$base."
        }
    }
    if (base.substringAfter('.', missingDelimiterValue = "").length >= 2 && base.contains('.')) {
        return base
    }
    return base + key
}

private fun prioritizeNewestRows(
    previousRows: List<SaleStagedRow>,
    mergedRows: List<SaleStagedRow>,
): List<SaleStagedRow> {
    if (previousRows.isEmpty() || mergedRows.isEmpty()) return mergedRows
    val previousIds = previousRows.map { it.id }.toSet()
    val newRows = mergedRows.filterNot { it.id in previousIds }
    if (newRows.isEmpty()) return mergedRows
    val existingRows = mergedRows.filter { it.id in previousIds }
    return newRows + existingRows
}

@Composable
private fun SalesRoute(
    session: com.lotterynet.pro.core.model.ActiveSession?,
    role: UserRole,
    banca: String?,
    territory: LotteryTerritory,
    lotteries: List<LotteryCatalogItem>,
    trustedClockRepository: LocalTrustedClockRepository,
    closePolicy: LotteryClosePolicy,
    availabilityResolver: LotteryAvailabilityResolver,
    saleDraftRepository: LocalSaleDraftRepository,
    salesRepository: LocalSalesRepository,
    resultsRepository: LocalResultsRepository,
    resultsSaleGuardOrchestrator: ResultsScraperOrchestrator,
    manualClosedLotteryIds: Set<String>,
    initialSystemModeConfig: AdminSystemModeConfig,
    nativeSyncQueueRepository: NativeSyncQueueRepository,
    nativeTicketCloudSyncCoordinator: NativeTicketCloudSyncCoordinator,
    nativeOperationalSyncCoordinator: NativeOperationalSyncCoordinator,
    saleExposureEngine: SaleExposureEngine,
    cashierSalesLimitRepository: LocalCashierSalesLimitRepository,
    cashierLimitCloudSyncCoordinator: CashierLimitCloudSyncCoordinator,
    usersRepository: LocalUsersRepository,
    preloadMessage: String?,
    preselectLotteryId: String?,
    ticketReconciler: TicketPrizeReconciler,
) {
    val visual = rememberLotteryNetVisualSpec()
    val saleValidator = remember { SaleValidator() }
    val initialDraft = remember(session) { saleDraftRepository.load(session) }
    var selectedLotteryIds by remember {
        mutableStateOf(initialDraft?.draft?.selectedLotteryIds.orEmpty())
    }
    var preselectLotteryApplied by remember(preselectLotteryId) { mutableStateOf(false) }
    var number by remember { mutableStateOf(initialDraft?.draft?.numberInput.orEmpty()) }
    var amount by remember { mutableStateOf(initialDraft?.draft?.amountInput.orEmpty()) }
    var classicMode by remember { mutableStateOf(initialDraft?.draft?.classicMode ?: "Q") }
    var pickMode by remember { mutableStateOf(initialDraft?.draft?.pickMode ?: PickPlayMode.STRAIGHT) }
    var activeInput by remember { mutableStateOf(SaleInputTarget.NUMBER) }
    var validationMessage by remember {
        mutableStateOf(
            preloadMessage?.takeIf {
                initialDraft?.stagedRows?.isNotEmpty() == true
            },
        )
    }
    var lotteryPickerTarget by remember { mutableStateOf<LotteryPickerTarget?>(null) }
    var lastSavedTicketEpochMs by remember { mutableStateOf<Long?>(null) }
    var tickUtcMs by remember { mutableLongStateOf(trustedClockRepository.getTrustedUtcMs()) }
    var replaceAmountOnNextDigit by remember { mutableStateOf(false) }
    var printPreviewTicket by remember { mutableStateOf<TicketRecord?>(null) }
    var pendingDuplicatePlay by remember { mutableStateOf<PendingDuplicatePlay?>(null) }
    var pendingPublishedResultPlay by remember { mutableStateOf<PendingPublishedResultPlay?>(null) }
    var ticketSaveSyncStage by remember { mutableStateOf<TicketSaveSyncStage?>(null) }
    var ticketSaveSyncDetail by remember { mutableStateOf<String?>(null) }
    var exposureRefreshTick by remember { mutableLongStateOf(0L) }
    val salesActionScope = rememberCoroutineScope()
    var showAdminSellerPicker by remember { mutableStateOf(false) }
    var showLigarTargetDialog by remember { mutableStateOf(false) }
    var ligarAmountDraft by remember { mutableStateOf("") }
    var userDirectoryRefreshTick by remember { mutableLongStateOf(0L) }
    val futureSaleEnabled = false
    var selectedDrawDay by remember { mutableStateOf(SaleDrawDay.TODAY) }
    val limitOwnerId = session?.adminId ?: session?.userId
    var cashierSaleLimits by remember(limitOwnerId, session?.username) {
        mutableStateOf(cashierSalesLimitRepository.getUserLimits(limitOwnerId, session?.username))
    }
    val localContext = LocalContext.current
    val realtimeClient = remember { LotterynetRealtimeClient() }
    val realtimeEnabled = remember { realtimeClient.isConfigured() }
    var showQrLookupChoices by remember { mutableStateOf(false) }
    var showClearStagedRowsDialog by remember { mutableStateOf(false) }
    val crashReporter = remember(localContext) { NativeCrashReporter(localContext) }
    val startupSyncThrottle = remember {
        OperationalSyncThrottle(PosPerformanceBudget.SYNC_RESUME_THROTTLE_MS)
    }
    val liveTicketRemoteStamp = remember { AtomicReference<String?>(null) }
    val liveTicketSyncInFlight = remember { AtomicBoolean(false) }
    val usersRealtimeRefreshInFlight = remember { AtomicBoolean(false) }
    val resultsRealtimeRefreshInFlight = remember { AtomicBoolean(false) }
    val exportRepository = remember { StaticExportTemplateRepository() }
    val renderCache = remember(localContext) { LocalRenderCacheRepository(localContext) }
    val bancaLogoUri = remember(localContext) { LocalBrandingRepository(localContext).getBranding().logoUri }
    val stagedRows = remember {
        mutableStateListOf<SaleStagedRow>().apply {
            addAll(initialDraft?.stagedRows.orEmpty())
        }
    }
    val adminSellerOptions = remember(session, usersRepository, userDirectoryRefreshTick) {
        session?.let { activeSession ->
            filterCashiersForSession(activeSession, usersRepository.getCashiers())
        }.orEmpty()
    }
    var selectedAdminSellerId by remember(session?.userId, adminSellerOptions) {
        mutableStateOf<String?>(null)
    }
    val adminSellerRows = remember(adminSellerOptions, selectedAdminSellerId) {
        resolveAdminSellerPickerRows(adminSellerOptions, selectedAdminSellerId)
    }
    val selectedAdminSeller = remember(selectedAdminSellerId, adminSellerOptions) {
        adminSellerOptions.firstOrNull { it.id == selectedAdminSellerId }
    }
    val activeTicketSeller = remember(session, selectedAdminSeller) {
        session?.let { resolveSaleTicketSeller(it, selectedAdminSeller) }
    }
    LaunchedEffect(role, adminSellerOptions.map { it.id }, selectedAdminSellerId) {
        if (role != UserRole.ADMIN) {
            selectedAdminSellerId = null
            return@LaunchedEffect
        }
        if (selectedAdminSellerId != null && adminSellerOptions.none { it.id == selectedAdminSellerId }) {
            selectedAdminSellerId = null
        }
    }
    LaunchedEffect(role, selectedDrawDay) {
        val normalized = resolveSaleFutureModeAfterRole(selectedDrawDay, role, futureSaleEnabled)
        if (normalized != selectedDrawDay) {
            selectedDrawDay = normalized
        }
    }

    LaunchedEffect(session?.userId, session?.adminId, banca) {
        val activeSession = session ?: return@LaunchedEffect
        delay(300)
        val nowMs = System.currentTimeMillis()
        if (!startupSyncThrottle.shouldRun(nowMs = nowMs, force = false)) return@LaunchedEffect
        startupSyncThrottle.markRan(nowMs)
        thread(name = "native-ticket-hydrate") {
            runCatching {
                nativeOperationalSyncCoordinator.syncTicketsForSession(
                    session = activeSession,
                    lastRemoteUpdatedAt = liveTicketRemoteStamp.get(),
                    force = false,
                )
            }.onSuccess { state ->
                state.remoteUpdatedAt?.let(liveTicketRemoteStamp::set)
            }.onFailure { error ->
                if (shouldReportSalesBackgroundRefreshFailure(error)) {
                    crashReporter.recordHandled("SalesActivity.startupSync", error)
                }
            }
        }
        if (activeSession.role == UserRole.CASHIER) {
            thread(name = "cashier-limit-pull") {
                val ownerKey = activeSession.adminId ?: activeSession.userId
                cashierLimitCloudSyncCoordinator.pullOwner(ownerKey)
                val nextLimits = cashierSalesLimitRepository.getUserLimits(ownerKey, activeSession.username)
                (localContext as? android.app.Activity)?.runOnUiThread {
                    cashierSaleLimits = nextLimits
                }
            }
        }
    }

    LaunchedEffect(session?.userId, session?.adminId, session?.username, role) {
        val activeSession = session ?: return@LaunchedEffect
        if (activeSession.role != UserRole.CASHIER) return@LaunchedEffect
        if (realtimeEnabled) return@LaunchedEffect
        while (true) {
            delay(CASHIER_LIMIT_PULL_INTERVAL_MS)
            thread(name = "cashier-limit-pull-live") {
                val ownerKey = activeSession.adminId ?: activeSession.userId
                val pulled = cashierLimitCloudSyncCoordinator.pullOwner(ownerKey)
                if (pulled) {
                    val nextLimits = cashierSalesLimitRepository.getUserLimits(ownerKey, activeSession.username)
                    (localContext as? android.app.Activity)?.runOnUiThread {
                        cashierSaleLimits = nextLimits
                    }
                }
                if (shouldPollSalesServerAccountGuardInBackground(activeSession.role, realtimeEnabled)) {
                    val usersResult = NativeUsersBootstrapper(usersRepository).bootstrap(forceRemoteRefresh = true)
                    if (usersResult.ok) {
                        val guard = resolveSalesServerAccountGuard(
                            session = activeSession,
                            accounts = usersRepository.getAdmins() + usersRepository.getCashiers(),
                        )
                        if (guard != SalesServerAccountGuard.ALLOW) {
                            val activity = localContext as? android.app.Activity ?: return@thread
                            activity.runOnUiThread {
                                LocalSessionRepository(activity).saveActiveSession(null)
                                Toast.makeText(
                                    activity,
                                    guard.message ?: "Cuenta bloqueada.",
                                    Toast.LENGTH_LONG,
                                ).show()
                                activity.startActivity(Intent(activity, LoginActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    putExtra(LoginActivity.EXTRA_LOGIN_STATUS, guard.message ?: "Cuenta bloqueada.")
                                })
                                activity.finish()
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching {
                tickUtcMs = trustedClockRepository.getTrustedUtcMs()
            }.onFailure { error ->
                if (shouldReportSalesBackgroundRefreshFailure(error)) {
                    crashReporter.recordHandled("SalesActivity.clockTick", error)
                    validationMessage = "No se pudo leer el reloj seguro."
                }
            }
            delay(1000)
        }
    }

    LaunchedEffect(session?.userId, session?.adminId, role) {
        val activeSession = session ?: return@LaunchedEffect
        if (role == UserRole.MASTER) return@LaunchedEffect
        if (realtimeEnabled) return@LaunchedEffect
        while (true) {
            delay(SALES_EXPOSURE_REFRESH_INTERVAL_MS)
            if (!liveTicketSyncInFlight.compareAndSet(false, true)) continue
            thread(name = "sales-exposure-refresh") {
                runCatching {
                    nativeOperationalSyncCoordinator.syncTicketsForSession(
                        session = activeSession,
                        lastRemoteUpdatedAt = liveTicketRemoteStamp.get(),
                        force = false,
                    )
                }.onSuccess { state ->
                    state.remoteUpdatedAt?.let(liveTicketRemoteStamp::set)
                    (localContext as? android.app.Activity)?.runOnUiThread {
                        exposureRefreshTick = System.currentTimeMillis()
                    }
                }.onFailure { error ->
                    if (shouldReportSalesBackgroundRefreshFailure(error)) {
                        crashReporter.recordHandled("SalesActivity.exposureRefresh", error)
                    }
                }.also {
                    liveTicketSyncInFlight.set(false)
                }
            }
        }
    }

    DisposableEffect(session?.userId, session?.adminId, session?.banca, role, realtimeEnabled) {
        val activeSession = session
        if (!realtimeEnabled || activeSession == null || role == UserRole.MASTER) {
            onDispose { }
        } else {
            val subscriptions = mutableListOf<LotterynetRealtimeClient.SubscriptionHandle>()
            val ownerKey = resolveOperationalOwnerKey(activeSession)
            if (ownerKey.isNotBlank()) {
                subscriptions += realtimeClient.subscribe(LotterynetRealtimeSubscription.usersGlobal()) {
                    if (!usersRealtimeRefreshInFlight.compareAndSet(false, true)) return@subscribe
                    thread(name = "sales-realtime-users-guard") {
                        runCatching {
                            NativeUsersBootstrapper(usersRepository).bootstrap(forceRemoteRefresh = true)
                        }.onSuccess { result ->
                            if (!result.ok) return@onSuccess
                            val guard = resolveSalesServerAccountGuard(
                                session = activeSession,
                                accounts = usersRepository.getAdmins() + usersRepository.getCashiers(),
                            )
                            val activity = localContext as? android.app.Activity ?: return@onSuccess
                            activity.runOnUiThread {
                                userDirectoryRefreshTick += 1
                                if (guard != SalesServerAccountGuard.ALLOW) {
                                    LocalSessionRepository(activity).saveActiveSession(null)
                                    Toast.makeText(
                                        activity,
                                        guard.message ?: "Cuenta bloqueada.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    activity.startActivity(Intent(activity, LoginActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        putExtra(LoginActivity.EXTRA_LOGIN_STATUS, guard.message ?: "Cuenta bloqueada.")
                                    })
                                    activity.finish()
                                }
                            }
                        }.onFailure { error ->
                            if (shouldReportSalesBackgroundRefreshFailure(error)) {
                                crashReporter.recordHandled("SalesActivity.realtimeUsersGuard", error)
                            }
                        }.also {
                            usersRealtimeRefreshInFlight.set(false)
                        }
                    }
                }
                subscriptions += realtimeClient.subscribe(LotterynetRealtimeSubscription.ticketOwner(ownerKey)) {
                    if (!liveTicketSyncInFlight.compareAndSet(false, true)) return@subscribe
                    thread(name = "sales-realtime-ticket-hydrate") {
                        runCatching {
                            nativeOperationalSyncCoordinator.refreshOwnerFromRealtime(ownerKey, activeSession.banca)
                        }.onSuccess { state ->
                            state.remoteUpdatedAt?.let(liveTicketRemoteStamp::set)
                            (localContext as? android.app.Activity)?.runOnUiThread {
                                exposureRefreshTick = System.currentTimeMillis()
                            }
                        }.onFailure { error ->
                            if (shouldReportSalesBackgroundRefreshFailure(error)) {
                                crashReporter.recordHandled("SalesActivity.realtimeTicket", error)
                            }
                        }.also {
                            liveTicketSyncInFlight.set(false)
                        }
                    }
                }
                if (activeSession.role == UserRole.CASHIER) {
                    subscriptions += realtimeClient.subscribe(
                        LotterynetRealtimeSubscription.masterKey(cashierLimitRemoteKey(ownerKey)),
                    ) {
                        thread(name = "sales-realtime-cashier-limits") {
                            val pulled = cashierLimitCloudSyncCoordinator.pullOwner(ownerKey)
                            if (pulled) {
                                val nextLimits = cashierSalesLimitRepository.getUserLimits(ownerKey, activeSession.username)
                                (localContext as? android.app.Activity)?.runOnUiThread {
                                    cashierSaleLimits = nextLimits
                                }
                            }
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

    val selectedLotteries = remember(selectedLotteryIds, lotteries) {
        selectedLotteryIds.mapNotNull { selectedId -> lotteries.firstOrNull { it.id == selectedId } }
    }
    val systemAllowedLotteryIds = remember(lotteries, initialSystemModeConfig) {
        filterSaleLotteriesForSystemMode(lotteries, initialSystemModeConfig).map { it.id }.toSet()
    }
    val preferredPickLotteryIds = remember(lotteries, initialSystemModeConfig) {
        preferredPickLotteryIdsForSaleMode(lotteries, initialSystemModeConfig)
    }
    LaunchedEffect(systemAllowedLotteryIds, selectedLotteryIds) {
        val cleaned = selectedLotteryIds.filter { it in systemAllowedLotteryIds }
        if (cleaned != selectedLotteryIds) {
            selectedLotteryIds = cleaned
        }
    }
    val selectedLottery = selectedLotteries.firstOrNull()
    val pickAssistedEntry = remember(number, selectedLotteries, pickMode) {
        resolvePickAssistedEntry(
            raw = number,
            selectedLotteries = selectedLotteries,
            pickMode = pickMode,
        )
    }
    val pickStraightBoxShortcut = remember(number) {
        resolvePickStraightBoxShortcut(number)
    }
    val effectiveSelectedLotteries = remember(selectedLotteries, pickAssistedEntry) {
        filterPickAssistedLotteries(selectedLotteries, pickAssistedEntry)
    }
    val resolvedClassicMode = remember(classicMode, number) {
        resolveVisibleClassicMode(
            preferredMode = classicMode,
            number = number,
        )
    }
    val drawDayContract = remember(selectedDrawDay, tickUtcMs, territory, role, futureSaleEnabled) {
        resolveSaleDrawDayContract(
            mode = resolveSaleFutureModeAfterRole(selectedDrawDay, role, futureSaleEnabled),
            nowUtcMs = tickUtcMs,
            territory = territory,
        )
    }
    val dayKey = drawDayContract.dayKey
    var lastLotterySelectionDayKey by remember {
        mutableStateOf(initialDraft?.savedAtEpochMs?.let(::dominicanDayKey))
    }
    val saleDecisionUtcMs = remember(selectedDrawDay, tickUtcMs, territory, role, futureSaleEnabled) {
        futureSaleDecisionClockMs(
            mode = resolveSaleFutureModeAfterRole(selectedDrawDay, role, futureSaleEnabled),
            nowUtcMs = tickUtcMs,
            territory = territory,
        )
    }
    val resultsDayKey = drawDayContract.resultsDayKey
    DisposableEffect(resultsDayKey, session?.userId, session?.adminId, role, realtimeEnabled) {
        val activeSession = session
        if (!realtimeEnabled || activeSession == null || role == UserRole.MASTER) {
            onDispose { }
        } else {
            val subscriptions = listOf(
                LotterynetRealtimeSubscription.resultsCache("lot_results_cache_by_day:$resultsDayKey"),
                LotterynetRealtimeSubscription.resultsCache("pick_results_cache_by_day:$resultsDayKey"),
                LotterynetRealtimeSubscription.resultsCache("manual_results_overrides_by_day:$resultsDayKey"),
            ).map { subscription ->
                realtimeClient.subscribe(subscription) {
                    if (!resultsRealtimeRefreshInFlight.compareAndSet(false, true)) {
                        return@subscribe
                    }
                    thread(name = "sales-realtime-results-refresh") {
                        try {
                            runCatching {
                                val refreshedResults = resultsSaleGuardOrchestrator.refreshDate(
                                    resultsDayKey,
                                    forceRemote = false,
                                ).results
                                ticketReconciler.reconcileTicketsForDate(resultsDayKey, refreshedResults)
                            }.onSuccess {
                                (localContext as? android.app.Activity)?.runOnUiThread {
                                    exposureRefreshTick = System.currentTimeMillis()
                                }
                            }.onFailure { error ->
                                if (shouldReportSalesBackgroundRefreshFailure(error)) {
                                    crashReporter.recordHandled("SalesActivity.realtimeResultsRefresh", error)
                                }
                            }
                        } finally {
                            resultsRealtimeRefreshInFlight.set(false)
                        }
                    }
                }
            }
            onDispose { subscriptions.forEach { it.close() } }
        }
    }
    LaunchedEffect(session?.userId, session?.adminId, role, resultsDayKey) {
        if (session == null || role == UserRole.MASTER) return@LaunchedEffect
        if (!shouldPollSalesResultsWinnerRefreshInBackground(realtimeEnabled)) return@LaunchedEffect
        while (true) {
            delay(SALES_RESULTS_WINNER_REFRESH_INTERVAL_MS)
            if (!ProductionNetworkGuard.hasValidatedInternet(localContext)) continue
            runCatching {
                val refreshedResults = withContext(Dispatchers.IO) {
                    resultsSaleGuardOrchestrator.refreshDate(resultsDayKey, forceRemote = false).results
                }
                withContext(Dispatchers.IO) {
                    ticketReconciler.reconcileTicketsForDate(resultsDayKey, refreshedResults)
                }
                exposureRefreshTick = System.currentTimeMillis()
            }.onFailure { error ->
                if (shouldReportSalesBackgroundRefreshFailure(error)) {
                    crashReporter.recordHandled("SalesActivity.resultsWinnerRefresh", error)
                }
            }
        }
    }
    val rawPublishedResultLotteryIds = remember(resultsDayKey, exposureRefreshTick, drawDayContract.isFuture, saleDecisionUtcMs) {
        if (drawDayContract.isFuture) return@remember emptySet<String>()
        resolvePublishedResultLotteryIdsForSale(
            results = resultsRepository.getResultsForDate(resultsDayKey),
            resultDateKey = resultsDayKey,
            nowUtcMs = saleDecisionUtcMs,
        )
    }
    val draft = remember(selectedLotteryIds, number, amount, resolvedClassicMode, pickMode, pickAssistedEntry) {
        SaleDraft(
            selectedLotteryIds = selectedLotteryIds,
            secondaryLotteryId = if (resolvedClassicMode == "SP") selectedLotteryIds.getOrNull(1) else null,
            numberInput = pickAssistedEntry?.digits ?: number,
            amountInput = amount,
            classicMode = resolvedClassicMode,
            pickMode = pickAssistedEntry?.pickMode ?: pickMode,
            superPaleEnabled = resolvedClassicMode == "SP",
        )
    }
    val validation = remember(draft, effectiveSelectedLotteries) {
        saleValidator.validate(draft = draft, selectedLotteries = effectiveSelectedLotteries)
    }
    val detectedPlay = remember(draft, effectiveSelectedLotteries) {
        saleValidator.detectPlay(draft = draft, selectedLotteries = effectiveSelectedLotteries)
    }
    val partialHint = remember(draft, effectiveSelectedLotteries) {
        saleValidator.getPartialHint(draft = draft, selectedLotteries = effectiveSelectedLotteries)
    }
    val numberAdvanceState = remember(number, detectedPlay, partialHint, pickStraightBoxShortcut) {
        resolveNumberAdvanceState(
            number = number,
            hasDetectedPlay = detectedPlay != null || pickStraightBoxShortcut != null,
            hasPartialHint = partialHint != null,
        )
    }
    val liveFeedbackMessage = remember(validationMessage, number, amount, partialHint, detectedPlay, validation) {
        validationMessage ?: when {
            number.isBlank() -> null
            partialHint != null -> null
            detectedPlay != null && amount.isBlank() -> null
            amount.isNotBlank() && !validation.isValid -> validation.errorMessage
            else -> null
        }
    }
    val liveFeedbackIsError = remember(validationMessage, number, amount, partialHint, detectedPlay, validation) {
        when {
            validationMessage != null -> !validation.isValid
            number.isBlank() -> false
            partialHint != null -> true
            detectedPlay != null && amount.isBlank() -> false
            amount.isNotBlank() && !validation.isValid -> true
            else -> false
        }
    }
    LaunchedEffect(session, draft, stagedRows.toList()) {
        runCatching {
            saleDraftRepository.save(
                session = session,
                snapshot = SaleDraftSnapshot(
                    draft = draft,
                    stagedRows = stagedRows.toList(),
                    savedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }.onFailure { error ->
            crashReporter.recordHandled("SalesActivity.saveDraft", error)
            validationMessage = "No se pudo guardar el borrador local."
        }
    }
    val todayTicketCount = remember(dayKey, stagedRows.size, exposureRefreshTick) {
        salesRepository.getTicketsForDay(dayKey).size
    }
    val todayActorSold = remember(dayKey, stagedRows.size, session, exposureRefreshTick) {
        saleExposureEngine.getActorSoldTodayTotal(dayKey, session)
    }
    val latestTicket = remember(dayKey, lastSavedTicketEpochMs, stagedRows.size) {
        salesRepository.getTicketsForDay(dayKey).firstOrNull()
    }
    val saleLimitRemainingRows = remember(
        role,
        stagedRows.toList(),
        dayKey,
        session,
        cashierSaleLimits,
        lastSavedTicketEpochMs,
        exposureRefreshTick,
    ) {
        resolveSaleLimitRemainingRows(
            role = role,
            stagedRows = stagedRows.toList(),
            tickets = salesRepository.getTicketsForDay(dayKey),
            ownerKey = resolveExposureOwnerKey(session),
            cashierKeys = resolveExposureCashierKeys(session),
            limits = CashierLimits(
                daySale = cashierSaleLimits.daySale,
                q = cashierSaleLimits.quiniela,
                pale = cashierSaleLimits.pale,
                superPale = cashierSaleLimits.superPale,
                t = cashierSaleLimits.tripleta,
                pick3Straight = cashierSaleLimits.pick3Straight,
                pick3Box = cashierSaleLimits.pick3Box,
                pick4Straight = cashierSaleLimits.pick4Straight,
                pick4Box = cashierSaleLimits.pick4Box,
            ),
        )
    }
    var saleLimitBadgeTone by remember { mutableStateOf(SaleLimitBadgeTone.NEUTRAL) }
    val saleLimitBadgeMain = remember(
        role,
        selectedLottery,
        resolvedClassicMode,
        pickMode,
        cashierSaleLimits,
        validation.resolvedPlay,
        validation.normalizedAmount,
        detectedPlay,
        dayKey,
        stagedRows.toList(),
        saleLimitRemainingRows,
        lastSavedTicketEpochMs,
        exposureRefreshTick,
        session,
    ) {
        val play = validation.resolvedPlay ?: detectedPlay
        if (role == UserRole.CASHIER && play != null) {
            val bucket = resolveSaleExposureLimitBucket(play.playType, play.normalizedNumber)
            val matchingRow = saleLimitRemainingRows.firstOrNull { row ->
                row.playType == bucket.playType && row.number == bucket.number
            }
            val soldExposure = matchingRow?.sold ?: calculateGlobalLimitExposure(
                tickets = salesRepository.getTicketsForDay(dayKey),
                ownerKey = resolveExposureOwnerKey(session),
                bucket = bucket,
                cashierKeys = resolveExposureCashierKeys(session),
            )
            val pendingExposure = resolveSaleLimitPendingPreview(
                stagedPending = matchingRow?.pending ?: calculateGlobalStagedExposure(stagedRows.toList(), bucket),
                currentAmount = validation.normalizedAmount,
            )
            val limit = resolveSaleLimitValue(selectedLottery, resolvedClassicMode, pickMode, cashierSaleLimits)
            saleLimitBadgeTone = resolveSaleLimitBadgeTone(limit = limit, sold = soldExposure, pending = pendingExposure)
            resolveSaleLimitBadgeMain(
                role = role,
                lottery = selectedLottery,
                classicMode = resolvedClassicMode,
                pickMode = pickMode,
                limits = cashierSaleLimits,
                sold = soldExposure,
                pending = pendingExposure,
            )
        } else {
            val stagedLimitRow = saleLimitRemainingRows.firstOrNull()
            val stagedBadge = resolveSaleLimitBadgeMain(
                role = role,
                row = stagedLimitRow,
            )
            if (stagedBadge != null && stagedLimitRow != null) {
                saleLimitBadgeTone = resolveSaleLimitBadgeTone(
                    limit = stagedLimitRow.limit,
                    sold = stagedLimitRow.sold,
                    pending = stagedLimitRow.pending,
                )
                stagedBadge
            } else {
                saleLimitBadgeTone = resolveSaleLimitBadgeTone(
                    limit = if (role == UserRole.CASHIER) resolveSaleLimitValue(selectedLottery, resolvedClassicMode, pickMode, cashierSaleLimits) else 0.0,
                )
                resolveSaleLimitBadgeMain(role, selectedLottery, resolvedClassicMode, pickMode, cashierSaleLimits)
            }
        }
    }
    val calendarClosedLotteryIds = remember(lotteries, territory, saleDecisionUtcMs) {
        availabilityResolver.getRealNoDrawLotteryIds(lotteries, territory, saleDecisionUtcMs)
    }
    val lotteryDecisionsWithoutPublishedResultsById = remember(
        lotteries,
        territory,
        manualClosedLotteryIds,
        calendarClosedLotteryIds,
        role,
        saleDecisionUtcMs,
    ) {
        lotteries.associate { lottery ->
            lottery.id to closePolicy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = territory,
                manualClosedLotteryIds = manualClosedLotteryIds,
                calendarClosedLotteryIds = calendarClosedLotteryIds,
                publishedResultLotteryIds = emptySet(),
                allowAdminAfterCloseGrace = role == UserRole.ADMIN,
                nowUtcMs = saleDecisionUtcMs,
            )
        }
    }
    val publishedResultLotteryIds = remember(rawPublishedResultLotteryIds, lotteryDecisionsWithoutPublishedResultsById) {
        resolveSalePublishedResultBlockIds(
            publishedResultLotteryIds = rawPublishedResultLotteryIds,
            decisionsWithoutPublishedResults = lotteryDecisionsWithoutPublishedResultsById,
        )
    }
    val lotteryDecisionsById = remember(
        lotteries,
        territory,
        manualClosedLotteryIds,
        calendarClosedLotteryIds,
        publishedResultLotteryIds,
        role,
        saleDecisionUtcMs,
    ) {
        lotteries.associate { lottery ->
            lottery.id to closePolicy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = territory,
                manualClosedLotteryIds = manualClosedLotteryIds,
                calendarClosedLotteryIds = calendarClosedLotteryIds,
                publishedResultLotteryIds = publishedResultLotteryIds,
                allowAdminAfterCloseGrace = role == UserRole.ADMIN,
                nowUtcMs = saleDecisionUtcMs,
            )
        }
    }
    val pickerExcludedLotteryIds = remember(lotteryPickerTarget, selectedLotteryIds) {
        when (lotteryPickerTarget) {
            LotteryPickerTarget.SECONDARY -> selectedLotteryIds.firstOrNull()?.let(::setOf).orEmpty()
            else -> emptySet()
        }
    }
    val pickerLotteryIds = remember(lotteries, lotteryDecisionsById, pickerExcludedLotteryIds) {
        resolveAvailableLotteryIdsForPicker(
            lotteries = lotteries,
            decisionsByLotteryId = lotteryDecisionsById,
            excludedLotteryIds = pickerExcludedLotteryIds,
        )
    }
    val pickerLotteries = remember(pickerLotteryIds, lotteries, selectedLotteries, pickAssistedEntry, initialSystemModeConfig) {
        val systemFiltered = filterSaleLotteriesForSystemMode(
            lotteries = pickerLotteryIds.mapNotNull { pickerId -> lotteries.firstOrNull { it.id == pickerId } },
            config = initialSystemModeConfig,
        )
        filterVentaLotteryPickerForMode(
            lotteries = systemFiltered,
            selectedLotteries = selectedLotteries,
            assistedEntry = pickAssistedEntry,
        )
    }
    LaunchedEffect(pickAssistedEntry, pickerLotteries, selectedLotteryIds) {
        val nextSelection = resolvePickAssistedLotterySelection(
            currentSelection = selectedLotteryIds,
            lotteries = pickerLotteries,
            assistedEntry = pickAssistedEntry,
        )
        if (nextSelection != selectedLotteryIds) {
            selectedLotteryIds = nextSelection
            validationMessage = nextSelection.firstOrNull()
                ?.let { nextId -> lotteries.firstOrNull { it.id == nextId }?.name }
                ?.let { "Pick seleccionado: $it" }
                ?: "Selecciona una lotería Pick disponible"
        }
    }
    LaunchedEffect(initialDraft, selectedLotteryIds, lotteries, lotteryDecisionsById, initialSystemModeConfig) {
        if (initialDraft != null || selectedLotteryIds.isNotEmpty()) return@LaunchedEffect
        selectedLotteryIds = resolveInitialLotterySelection(
            lotteries = filterSaleLotteriesForSystemMode(lotteries, initialSystemModeConfig),
            decisionsByLotteryId = lotteryDecisionsById,
            preferredLotteryIds = preferredPickLotteryIds,
        )
    }
    LaunchedEffect(preselectLotteryId, pickerLotteryIds) {
        val targetId = preselectLotteryId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (preselectLotteryApplied || targetId !in pickerLotteryIds) return@LaunchedEffect
        selectedLotteryIds = listOf(targetId)
        preselectLotteryApplied = true
        validationMessage = lotteries.firstOrNull { it.id == targetId }?.name
            ?.let { "Loteria seleccionada por aviso: $it" }
            ?: "Loteria seleccionada por aviso"
    }
    LaunchedEffect(dayKey, lotteryDecisionsById, lotteries) {
        val alerts = buildLotteryClosingAlerts(
            lotteries = lotteries,
            decisionsByLotteryId = lotteryDecisionsById,
        )
        LotteryClosingNotifier.notifyIfNeeded(
            context = localContext,
            dateKey = dayKey,
            alerts = alerts,
        )
    }
    LaunchedEffect(dayKey, pickerLotteryIds, resolvedClassicMode, selectedLotteryIds) {
        val shouldResetSelectionForNewDay = lastLotterySelectionDayKey != null &&
            lastLotterySelectionDayKey != dayKey
        val nextSelection = resolveLiveLotterySelection(
            currentSelection = selectedLotteryIds,
            availableLotteryIds = pickerLotteryIds,
            classicMode = resolvedClassicMode,
            preferredLotteryIds = preferredPickLotteryIds,
            resetToFirstAvailable = shouldResetSelectionForNewDay,
        )
        lastLotterySelectionDayKey = dayKey
        if (nextSelection != selectedLotteryIds) {
            selectedLotteryIds = nextSelection
            validationMessage = nextSelection.firstOrNull()
                ?.let { nextId -> lotteries.firstOrNull { it.id == nextId }?.name }
                ?.let { "Lotería cambiada automáticamente a $it" }
                ?: "No hay loterías abiertas para vender"
        }
    }
    val saveTicket: suspend () -> TicketRecord? = saveTicket@{
        if (session == null) {
            validationMessage = "No hay sesión activa"
            null
        } else if (!ProductionNetworkGuard.hasValidatedInternet(localContext)) {
            validationMessage = ProductionNetworkGuard.NO_INTERNET_ACTION_MESSAGE
            ticketSaveSyncStage = TicketSaveSyncStage.PENDING
            ticketSaveSyncDetail = "Internet requerido para vender"
            null
        } else if (stagedRows.isEmpty()) {
            validationMessage = "No hay jugadas para guardar"
            null
        } else {
            val refreshedResults = withContext(Dispatchers.IO) {
                runCatching {
                    resultsSaleGuardOrchestrator.refreshDate(resultsDayKey, forceRemote = false).results
                }.getOrElse {
                    resultsRepository.getResultsForDate(resultsDayKey)
                }
            }
            val blockedResultLotteryIds = resolvePublishedResultSaleBlockLotteryIds(
                stagedRows = stagedRows,
                results = refreshedResults,
            )
            val sellBlockedResultLotteryIds = if (canUseAdminResultGraceForSale(role)) {
                blockedResultLotteryIds.filterNot { lotteryId ->
                    val lottery = lotteries.firstOrNull { it.id == lotteryId } ?: return@filterNot false
                    !closePolicy.resolveCloseDecision(
                        lottery = lottery,
                        operationTerritory = territory,
                        manualClosedLotteryIds = manualClosedLotteryIds,
                        calendarClosedLotteryIds = calendarClosedLotteryIds,
                        publishedResultLotteryIds = blockedResultLotteryIds,
                        allowAdminAfterCloseGrace = true,
                        nowUtcMs = saleDecisionUtcMs,
                    ).isClosed
                }.toSet()
            } else {
                blockedResultLotteryIds
            }
            if (sellBlockedResultLotteryIds.isNotEmpty()) {
                val blockedNames = lotteries
                    .filter { it.id in sellBlockedResultLotteryIds }
                    .joinToString(", ") { it.name }
                    .ifBlank { "lotería seleccionada" }
                exposureRefreshTick = System.currentTimeMillis()
                validationMessage = "Resultado publicado: $blockedNames. No se puede vender esa lotería."
                return@saveTicket null
            } else if (blockedResultLotteryIds.isNotEmpty() && role == UserRole.ADMIN) {
                val warningNames = lotteries
                    .filter { it.id in blockedResultLotteryIds }
                    .joinToString(", ") { it.name }
                    .ifBlank { "lotería seleccionada" }
                validationMessage = "Resultado publicado: $warningNames. Venta permitida por gracia de admin."
            }
            val total = stagedRows.sumOf { it.amount }
            val nowEpoch = tickUtcMs
            val ticketSeller = activeTicketSeller ?: resolveSaleTicketSeller(session, null)
            val ticket = TicketRecord(
                id = "native-$nowEpoch",
                serial = "NAT-${nowEpoch.toString().takeLast(8)}",
                sellerId = ticketSeller.sellerId,
                sellerUser = ticketSeller.sellerUser,
                adminId = session.adminId ?: session.userId,
                adminUser = session.adminUser ?: session.username,
                role = ticketSeller.role,
                createdAtEpochMs = nowEpoch,
                drawDateKey = dayKey,
                plays = stagedRows.map { row ->
                    PlayItem(
                        number = row.number,
                        playType = row.playType,
                        amount = row.amount,
                        lotteryId = row.lotteryId,
                        lotteryName = row.lotteryName,
                        secondaryLotteryId = row.secondaryLotteryId,
                        secondaryLotteryName = row.secondaryLotteryName,
                    )
                },
                subtotal = total,
                total = total,
            )
            val canonicalTicket = canonicalizeTicketOwnerForSession(
                ticket = ticket,
                session = session,
                users = usersRepository.getAdmins() + usersRepository.getSupervisors() + usersRepository.getCashiers(),
            )
            val preliminarySecuredTicket = canonicalTicket.copy(
                securityCode = TicketSecurity.issueTicketSecurityCode(canonicalTicket, banca.orEmpty()),
            )
            ticketSaveSyncStage = TicketSaveSyncStage.SYNCING
            ticketSaveSyncDetail = "Validando venta en servidor"
            validationMessage = "Validando venta en servidor..."
            val backendResponse = runCatching {
                withTimeout(SALES_SERVER_TICKET_VALIDATION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        val freshBearerToken = SupabaseSessionTokenProvider(
                            LocalSessionRepository(localContext),
                        ).freshAccessToken()
                        SupabaseTicketBackendClient(
                            edgeClient = SupabaseEdgeClient(
                                connectTimeoutMs = 3_000,
                                readTimeoutMs = SALES_SERVER_TICKET_VALIDATION_TIMEOUT_MS.toInt(),
                                callTimeoutMs = SALES_SERVER_TICKET_VALIDATION_TIMEOUT_MS.toInt(),
                            ),
                        ).createTicket(
                            request = BackendTicketRequest(
                                clientRequestId = preliminarySecuredTicket.id,
                                localTicketId = preliminarySecuredTicket.id,
                                adminKey = preliminarySecuredTicket.adminId ?: session.adminId ?: session.userId,
                                adminId = preliminarySecuredTicket.adminId ?: session.adminId ?: session.userId,
                                actorKey = session.username,
                                actorId = session.userId,
                                actorRole = session.role.name.lowercase(Locale.US),
                                cashierKey = preliminarySecuredTicket.sellerId ?: session.userId,
                                cashierId = preliminarySecuredTicket.sellerId ?: session.userId,
                                sorteoId = stagedRows.firstOrNull()?.lotteryId,
                                drawDate = dayKey,
                                dayKey = dayKey,
                                lotteryName = stagedRows.firstOrNull()?.lotteryName,
                                phoneTimeIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }.format(Date(nowEpoch)),
                                plays = stagedRows.map { row ->
                                    BackendTicketPlay(
                                        playType = row.playType,
                                        number = row.number,
                                        amount = row.amount,
                                        lotteryId = row.lotteryId,
                                        lotteryName = row.lotteryName,
                                        secondaryLotteryId = row.secondaryLotteryId,
                                        secondaryLotteryName = row.secondaryLotteryName,
                                    )
                                },
                            ),
                            bearerToken = freshBearerToken,
                        )
                    }
                }
            }.getOrElse { error ->
                if (shouldReportSupabaseTicketBackendFailure(error)) {
                    crashReporter.recordHandled("SalesActivity.createTicketBackend", error)
                }
                ticketSaveSyncStage = TicketSaveSyncStage.PENDING
                ticketSaveSyncDetail = "Servidor requerido para vender"
                validationMessage = ticketBackendUserMessage(error)
                return@saveTicket null
            }
            if (!backendResponse.optBoolean("ok", false)) {
                ticketSaveSyncStage = TicketSaveSyncStage.PENDING
                ticketSaveSyncDetail = "Venta rechazada por servidor"
                validationMessage = presentSupabaseTicketBackendMessage(backendResponse.optString("message"))
                return@saveTicket null
            }
            val officialTicket = backendResponse.optJSONObject("ticket")
            val officialCode = officialTicket?.optString("ticket_code").orEmpty().takeIf { it.isNotBlank() }
            val securedTicket = preliminarySecuredTicket.copy(
                serial = officialCode ?: preliminarySecuredTicket.serial,
            ).let { official ->
                official.copy(securityCode = TicketSecurity.issueTicketSecurityCode(official, banca.orEmpty()))
            }
            salesRepository.saveTicket(securedTicket)
            exposureRefreshTick = System.currentTimeMillis()
            ticketSaveSyncStage = TicketSaveSyncStage.LOCAL_SAVED
            ticketSaveSyncDetail = null
            thread(name = "native-ticket-flush") {
                (localContext as? android.app.Activity)?.runOnUiThread {
                    ticketSaveSyncStage = TicketSaveSyncStage.SYNCING
                    ticketSaveSyncDetail = null
                    validationMessage = "Ticket guardado local. Sincronizando servidor..."
                }
                val result = nativeOperationalSyncCoordinator.flushTicket(
                    ticket = securedTicket,
                    banca = banca,
                )
                runCatching {
                    val syncState = nativeOperationalSyncCoordinator.syncTicketsForSession(
                        session = session,
                        lastRemoteUpdatedAt = liveTicketRemoteStamp.get(),
                        force = true,
                    )
                    syncState.remoteUpdatedAt?.let(liveTicketRemoteStamp::set)
                }
                (localContext as? android.app.Activity)?.runOnUiThread {
                    exposureRefreshTick = System.currentTimeMillis()
                    ticketSaveSyncStage = if (result.ok) TicketSaveSyncStage.SYNCED else TicketSaveSyncStage.PENDING
                    ticketSaveSyncDetail = if (result.ok) null else result.message.removePrefix("Pendiente de sync:").trim()
                    validationMessage = if (result.ok) {
                        "Ticket guardado y sincronizado con servidor"
                    } else {
                        "Ticket guardado local. Sync pendiente: ${result.message}"
                    }
                }
            }
            stagedRows.clear()
            number = ""
            amount = ""
            selectedAdminSellerId = resolvePostTicketAdminSellerId(role, selectedAdminSellerId)
            val nextClassicMode = resolvePostTicketClassicMode(resolvedClassicMode)
            classicMode = nextClassicMode
            selectedLotteryIds = resolvePostTicketLotterySelection(
                currentSelection = selectedLotteryIds,
                availableLotteryIds = pickerLotteryIds,
                classicMode = nextClassicMode,
                preferredLotteryIds = preferredPickLotteryIds,
            )
            saleDraftRepository.clear(session)
            validationMessage = "Ticket guardado. Sincronizando servidor..."
            lastSavedTicketEpochMs = securedTicket.createdAtEpochMs
            securedTicket
        }
    }
    val onClassicModeChange: (String) -> Unit = {
        val transition = resolveClassicModeTransition(
            nextMode = it,
            currentSelection = selectedLotteryIds,
            fallbackLotteryId = selectedLottery?.id,
        )
        classicMode = it
        selectedLotteryIds = transition.selection
        number = ""
        validationMessage = transition.message
    }
    val onPickModeChange: (PickPlayMode) -> Unit = {
        pickMode = it
        number = ""
        validationMessage = null
    }
    val onOpenLots = {
        lotteryPickerTarget = resolveLotteryPickerTargetForLotsButton(
            classicMode = resolvedClassicMode,
            selectedLotteryIds = selectedLotteryIds,
        )
        validationMessage = null
    }
    val sharePreviewTicketImage: (TicketRecord, Boolean) -> Unit = { ticket, whatsappOnly ->
        val bancaName = resolveTicketOutputBancaName(
            ticket = ticket,
            defaultBancaName = banca.orEmpty().ifBlank { "LotteryNet" },
            accounts = usersRepository.getAdmins() + usersRepository.getSupervisors() + usersRepository.getCashiers(),
        )
        val envelope = exportRepository.buildTicketWhatsAppShare(ticket, bancaName)
        val title = envelope.title
        val fileName = envelope.fileName ?: "ticket-${ticket.id}.png"
        val renderKey = ticketRenderCacheKey(ticket, bancaName = bancaName, logoUri = bancaLogoUri.orEmpty())
        val cachedUri = renderCache.getUriIfPresent(renderKey)
        if (cachedUri != null) {
            validationMessage = NativeBitmapExport.shareImageUris(
                context = localContext,
                uris = listOf(cachedUri),
                title = title,
                whatsappOnly = whatsappOnly,
            ).message
            printPreviewTicket = null
        } else {
            validationMessage = "Preparando imagen del ticket..."
            thread(name = "native-ticket-share-render") {
                val bitmap = runCatching {
                    NativeBitmapExport.renderOfficialTicketBitmap(
                        context = localContext,
                        ticket = ticket,
                        bancaName = bancaName,
                        securityCode = TicketSecurity.resolveSecurityCode(ticket, bancaName),
                        bancaLogoUri = bancaLogoUri,
                    )
                }.getOrNull()
                if (bitmap == null) {
                    (localContext as? android.app.Activity)?.runOnUiThread {
                        validationMessage = "No se pudo preparar la imagen del ticket"
                    }
                    return@thread
                }
                val uri = renderCache.saveBitmap(renderKey, bitmap)
                (localContext as? android.app.Activity)?.runOnUiThread {
                    validationMessage = if (uri != null) {
                        NativeBitmapExport.shareImageUris(
                            context = localContext,
                            uris = listOf(uri),
                            title = title,
                            whatsappOnly = whatsappOnly,
                        ).message
                    } else {
                        NativeBitmapExport.shareBitmap(
                            context = localContext,
                            bitmap = bitmap,
                            fileName = fileName,
                            title = title,
                            text = "",
                            whatsappOnly = whatsappOnly,
                        ).message
                    }
                    printPreviewTicket = null
                }
            }
        }
    }
    fun applyLigar(target: LigarBuildTarget, confirmedAmount: String): Boolean {
        val ligarAmount = parseLigarAmountForConfirmation(confirmedAmount)
        if (ligarAmount == null) {
            validationMessage = "Escribe el monto para ligar"
            return false
        }
        val ligar = saleValidator.buildLigarRows(
            existing = stagedRows.toList(),
            amount = ligarAmount,
            target = target,
        )
        if (ligar.quinielaCount == 0 && ligar.paleCount == 0 && ligar.tripletaCount == 0) {
            validationMessage = if (ligar.blockedCount > 0) {
                "Ligar no agregó jugadas nuevas; ya existían o quedaron bloqueadas"
            } else {
                "Agrega al menos 2 quinielas por lotería para ligar"
            }
            return false
        }
        stagedRows.clear()
        stagedRows.addAll(ligar.rows)
        validationMessage = buildString {
            append("Ligar agregó ")
            append(ligar.quinielaCount + ligar.paleCount + ligar.tripletaCount)
            append(" jugadas")
            if (ligar.quinielaCount > 0) append(" · ${ligar.quinielaCount} quiniela")
            if (ligar.paleCount > 0) append(" · ${ligar.paleCount} pale")
            if (ligar.tripletaCount > 0) append(" · ${ligar.tripletaCount} tripleta")
            if (ligar.blockedCount > 0) append(" · ${ligar.blockedCount} omitidas")
        }
        return true
    }
    val onOpenLigar = {
        if (selectedLottery?.let { supportsPickModes(it) } == true) {
            val nextPickMode = if (pickMode == PickPlayMode.BOX) PickPlayMode.STRAIGHT else PickPlayMode.BOX
            pickMode = nextPickMode
            validationMessage = activeModeLabel(selectedLottery, resolvedClassicMode, nextPickMode)
        } else if (resolvedClassicMode != "Q") {
            validationMessage = "Esta acción solo aplica a Pick o a quinielas para ligar"
        } else {
            ligarAmountDraft = resolveLigarAmountPromptState(amount).initialAmount
            showLigarTargetDialog = true
        }
    }
    val canToggleSuperPale = selectedLottery?.let { lottery ->
        lottery.playCapabilities.supportsQuiniela ||
            lottery.playCapabilities.supportsPale ||
            lottery.playCapabilities.supportsTripleta
    } == true
    val onToggleOrConfigureSuperPale = {
        if (resolvedClassicMode == "SP") {
            classicMode = "Q"
            selectedLotteryIds = selectedLotteryIds.take(1)
            number = ""
            validationMessage = "Super Pale desactivado"
        } else {
            val activation = resolveSuperPaleActivationState(selectedLotteryIds)
            if (!activation.canActivate) {
                validationMessage = activation.message
                lotteryPickerTarget = LotteryPickerTarget.PRIMARY
            } else {
                classicMode = "SP"
                selectedLotteryIds = activation.selection
                number = ""
                validationMessage = activation.message
            }
        }
    }
    val superPaleSecondaryState = remember(selectedLotteryIds, lotteries, resolvedClassicMode) {
        resolveSuperPaleSecondaryState(
            selectedLotteryIds = selectedLotteryIds,
            availableLotteryIds = lotteries.map { it.id },
            classicMode = resolvedClassicMode,
        )
    }
    val supportsPickModes = selectedLottery?.let { supportsPickModes(it) } == true
    val quickActionContract = remember(
        supportsPickModes,
        resolvedClassicMode,
        pickMode,
        stagedRows.size,
        canToggleSuperPale,
    ) {
        resolveQuickActionContract(
            supportsPickModes = supportsPickModes,
            classicMode = resolvedClassicMode,
            pickMode = pickMode,
            canLigar = canRunSecondaryAction(
                lottery = selectedLottery,
                classicMode = resolvedClassicMode,
                stagedRows = stagedRows,
            ),
            canToggleSuperPale = canToggleSuperPale,
            superPaleEnabled = resolvedClassicMode == "SP",
        )
    }
    val ticketPrintOpenContract = remember(
        stagedRows.size,
        latestTicket?.id,
        validation.isValid,
        number,
        amount,
        pendingDuplicatePlay,
        pendingPublishedResultPlay,
    ) {
        resolveTicketPrintOpenContract(
            stagedRowCount = stagedRows.size,
            hasLatestTicket = latestTicket != null,
            currentEntryValid = validation.isValid,
            number = number,
            amount = amount,
            hasPendingConfirmation = pendingDuplicatePlay != null || pendingPublishedResultPlay != null,
        )
    }
    val trustedClockLabel = remember(tickUtcMs, territory) {
        formatTrustedTime(
            nowUtcMs = tickUtcMs,
            trustedClockRepository = trustedClockRepository,
            territory = territory,
        )
    }
    fun stageValidatedPlay(
        validResult: SaleValidationResult,
        lotteriesForPlay: List<LotteryCatalogItem>,
    ) {
        val previousRows = stagedRows.toList()
        val merged = saleValidator.mergeIntoRows(
            existing = previousRows,
            validation = validResult,
            selectedLotteries = lotteriesForPlay,
        )
        val prioritizedRows = prioritizeNewestRows(
            previousRows = previousRows,
            mergedRows = merged,
        )
        stagedRows.clear()
        stagedRows.addAll(prioritizedRows)
        val nextEntryState = resolvePostAddCarryState(amount)
        activeInput = nextEntryState.activeInput
        number = nextEntryState.number
        amount = nextEntryState.amount
        replaceAmountOnNextDigit = nextEntryState.replaceAmountOnNextDigit
        validationMessage = null
    }
    fun stageAfterSaleWarnings(
        checkedValidation: SaleValidationResult,
        checkedLotteries: List<LotteryCatalogItem>,
        checkedAmount: Double,
    ) {
        val duplicateRows = findDuplicateStagedRows(
            existingRows = stagedRows.toList(),
            validation = checkedValidation,
            selectedLotteries = checkedLotteries,
        )
        if (duplicateRows.isNotEmpty()) {
            pendingDuplicatePlay = PendingDuplicatePlay(
                validation = checkedValidation,
                selectedLotteries = checkedLotteries,
                existingRow = duplicateRows.first(),
            )
            validationMessage = resolveDuplicatePlayPromptText(duplicateRows.first(), checkedAmount)
        } else {
            stageValidatedPlay(checkedValidation, checkedLotteries)
        }
    }

    val onAddPlay = {
        val straightBoxShortcut = resolvePickStraightBoxShortcut(number)
        if (straightBoxShortcut != null && selectedLottery?.let { supportsPickModes(it) } == true) {
            val straightDraft = draft.copy(
                numberInput = straightBoxShortcut.digits + "S",
                pickMode = PickPlayMode.STRAIGHT,
            )
            val boxDraft = draft.copy(
                numberInput = straightBoxShortcut.digits + "B",
                pickMode = PickPlayMode.BOX,
            )
            val straightValidation = saleValidator.validate(straightDraft, effectiveSelectedLotteries)
            val boxValidation = saleValidator.validate(boxDraft, effectiveSelectedLotteries)
            val normalizedAmount = straightValidation.normalizedAmount ?: boxValidation.normalizedAmount
            if (!straightValidation.isValid || !boxValidation.isValid || normalizedAmount == null) {
                validationMessage = straightValidation.errorMessage ?: boxValidation.errorMessage ?: "Completa S+B antes de agregar"
            } else {
                val straightLimitError = straightValidation.resolvedPlay?.let { play ->
                    saleExposureEngine.resolveLimitError(
                        session = session,
                        dayKey = dayKey,
                        play = play,
                        amount = normalizedAmount,
                        lotteries = effectiveSelectedLotteries,
                        stagedRows = stagedRows.toList(),
                    )
                }
                val rowsAfterStraight = saleValidator.mergeIntoRows(
                    existing = stagedRows.toList(),
                    validation = straightValidation,
                    selectedLotteries = effectiveSelectedLotteries,
                )
                val boxLimitError = boxValidation.resolvedPlay?.let { play ->
                    saleExposureEngine.resolveLimitError(
                        session = session,
                        dayKey = dayKey,
                        play = play,
                        amount = normalizedAmount,
                        lotteries = effectiveSelectedLotteries,
                        stagedRows = rowsAfterStraight,
                    )
                }
                val duplicateStraight = findDuplicateStagedRows(stagedRows.toList(), straightValidation, effectiveSelectedLotteries)
                val duplicateBox = findDuplicateStagedRows(stagedRows.toList(), boxValidation, effectiveSelectedLotteries)
                when {
                    straightLimitError != null -> validationMessage = straightLimitError
                    boxLimitError != null -> validationMessage = boxLimitError
                    duplicateStraight.isNotEmpty() || duplicateBox.isNotEmpty() -> {
                        validationMessage = "S+B tiene una jugada repetida; agrega Straight o Box separado para confirmar"
                    }
                    role == UserRole.ADMIN && effectiveSelectedLotteries.any { it.id in publishedResultLotteryIds } -> {
                        validationMessage = "Resultado publicado: agrega Straight y Box separado para confirmar"
                    }
                    else -> {
                        val rowsAfterBoth = saleValidator.mergeIntoRows(
                            existing = rowsAfterStraight,
                            validation = boxValidation,
                            selectedLotteries = effectiveSelectedLotteries,
                        )
                        stagedRows.clear()
                        stagedRows.addAll(prioritizeNewestRows(stagedRows.toList(), rowsAfterBoth))
                        val nextEntryState = resolvePostAddCarryState(amount)
                        activeInput = nextEntryState.activeInput
                        number = nextEntryState.number
                        amount = nextEntryState.amount
                        replaceAmountOnNextDigit = nextEntryState.replaceAmountOnNextDigit
                        validationMessage = null
                    }
                }
            }
        } else if (validation.isValid) {
            val resolvedPlay = validation.resolvedPlay
            val normalizedAmount = validation.normalizedAmount
            if (resolvedPlay == null || normalizedAmount == null) {
                validationMessage = "La jugada no se pudo validar. Intenta de nuevo."
            } else {
                val limitError = saleExposureEngine.resolveLimitError(
                    session = session,
                    dayKey = dayKey,
                    play = resolvedPlay,
                    amount = normalizedAmount,
                    lotteries = effectiveSelectedLotteries,
                    stagedRows = stagedRows.toList(),
                )
                if (limitError != null) {
                    validationMessage = limitError
                } else {
                    val publishedLotteries = if (role == UserRole.ADMIN) {
                        effectiveSelectedLotteries.filter { it.id in publishedResultLotteryIds }
                    } else {
                        emptyList()
                    }
                    if (publishedLotteries.isNotEmpty()) {
                        pendingPublishedResultPlay = PendingPublishedResultPlay(
                            validation = validation,
                            selectedLotteries = effectiveSelectedLotteries,
                            normalizedAmount = normalizedAmount,
                            lotteryNames = publishedLotteries.map { it.name },
                        )
                        validationMessage = "Resultado publicado: confirma si deseas vender"
                    } else {
                        stageAfterSaleWarnings(validation, effectiveSelectedLotteries, normalizedAmount)
                    }
                }
            }
        } else {
            validationMessage = if (pickAssistedEntry != null && effectiveSelectedLotteries.isEmpty()) {
                "Selecciona loterías ${pickAssistedEntry.lotteryType.replace("Pick", "Pick ")} para esta jugada"
            } else {
                validation.errorMessage
            }
        }
    }
    val onApplyKey: (String) -> Unit = { key ->
        val isPickKey = selectedLottery?.let { supportsPickModes(it) } == true
        if (isPickKey && activeInput == SaleInputTarget.NUMBER && key in setOf("-", "+", "/", "*")) {
            val nextNumber = applyPickModeSymbolToNumber(number, key)
            number = nextNumber
            when (key) {
                "-" -> {
                    pickMode = PickPlayMode.STRAIGHT
                    activeInput = resolvePickModeKeyNextInput(nextNumber, PickPlayMode.STRAIGHT, activeInput)
                }
                "+" -> {
                    pickMode = PickPlayMode.BOX
                    activeInput = resolvePickModeKeyNextInput(nextNumber, PickPlayMode.BOX, activeInput)
                }
                "/" -> validationMessage = "Ligar Pick marcado con /"
                "*" -> validationMessage = "S+B listo: agrega monto y OK"
            }
        } else {
            val nextState = applySaleKeypadInput(
                activeInput = activeInput,
                key = key,
                number = number,
                amount = amount,
                replaceAmountOnNextDigit = replaceAmountOnNextDigit,
            )
            number = nextState.number
            amount = nextState.amount
            activeInput = nextState.activeInput
            replaceAmountOnNextDigit = nextState.replaceAmountOnNextDigit
        }
    }
    if (showLigarTargetDialog) {
        val ligarTargets = resolveLigarBuildTargets(stagedRows.toList())
        AlertDialog(
            onDismissRequest = { showLigarTargetDialog = false },
            title = { Text("Ligar jugadas") },
            text = {
                Column {
                    Text("Confirma el monto para la liga. Puedes dejar el actual o escribir uno nuevo.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ligarAmountDraft,
                        onValueChange = { input ->
                            ligarAmountDraft = input
                                .filter { char -> char.isDigit() || char == '.' || char == ',' }
                                .take(8)
                        },
                        label = { Text("Monto") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Elige qué tipo de liga quieres generar según las jugadas actuales.")
                    if (ligarTargets.isEmpty()) {
                        Text("Agrega palé, tripleta o por lo menos 2 quinielas.")
                    }
                    ligarTargets.forEach { target ->
                        TextButton(
                            onClick = {
                                if (applyLigar(target, ligarAmountDraft)) {
                                    showLigarTargetDialog = false
                                }
                            },
                        ) {
                            Text(
                                when (target) {
                                    LigarBuildTarget.QUINIELA -> "Quiniela"
                                    LigarBuildTarget.PALE -> "Pale"
                                    LigarBuildTarget.TRIPLETA -> "Tripleta"
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showLigarTargetDialog = false
                    },
                ) {
                    Text("Cancelar")
                }
            },
        )
    }
    if (showClearStagedRowsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStagedRowsDialog = false },
            title = { Text("Limpiar jugadas") },
            text = {
                Text("Se borraran ${stagedRows.size} jugada(s) cargadas de esta venta. Los tickets ya impresos o guardados no se tocan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val removedCount = stagedRows.size
                        stagedRows.clear()
                        showClearStagedRowsDialog = false
                        validationMessage = if (removedCount > 0) "Lista de jugadas limpiada" else null
                    },
                ) {
                    Text("Limpiar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearStagedRowsDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
    pendingPublishedResultPlay?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingPublishedResultPlay = null },
            title = { Text("Resultado publicado") },
            text = {
                Text(
                    "Ya hay resultado para ${pending.lotteryNames.joinToString(", ")}. " +
                        "Solo admin puede vender en esta gracia. ¿Quieres agregar la jugada?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val confirmed = pendingPublishedResultPlay ?: return@TextButton
                        pendingPublishedResultPlay = null
                        stageAfterSaleWarnings(
                            checkedValidation = confirmed.validation,
                            checkedLotteries = confirmed.selectedLotteries,
                            checkedAmount = confirmed.normalizedAmount,
                        )
                    },
                ) {
                    Text("Vender")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingPublishedResultPlay = null
                        validationMessage = "Jugada no agregada"
                    },
                ) {
                    Text("No")
                }
            },
        )
    }
    pendingDuplicatePlay?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDuplicatePlay = null },
            title = { Text("Jugada repetida") },
            text = {
                Text(
                    resolveDuplicatePlayPromptText(
                        existingRow = pending.existingRow,
                        amountToAdd = pending.validation.normalizedAmount ?: 0.0,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val confirmed = pendingDuplicatePlay ?: return@TextButton
                        pendingDuplicatePlay = null
                        stageValidatedPlay(confirmed.validation, confirmed.selectedLotteries)
                    },
                ) {
                    Text("Si, sumar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDuplicatePlay = null
                        validationMessage = "Jugada repetida no modificada"
                    },
                ) {
                    Text("No")
                }
            },
        )
    }
    val ticketSaveSyncContract = ticketSaveSyncStage?.let { stage ->
        resolveTicketSaveSyncUiContract(stage = stage, detail = ticketSaveSyncDetail)
    }
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
    ) {
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
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (showQrLookupChoices) {
                val choices = resolveVentaQrLookupChoices()
                AlertDialog(
                    onDismissRequest = { showQrLookupChoices = false },
                    title = { Text("Escanear QR") },
                    text = {
                        Text("Elige que quieres hacer con el ticket escaneado.")
                    },
                    confirmButton = {
                        val payChoice = choices.first { it.lookupMode == "pagar" }
                        TextButton(
                            onClick = {
                                showQrLookupChoices = false
                                localContext.startActivity(
                                    Intent(localContext, TicketLookupActivity::class.java).apply {
                                        putExtra(TicketLookupActivity.EXTRA_MODE, payChoice.lookupMode)
                                        putExtra(TicketLookupActivity.EXTRA_AUTO_SCAN, payChoice.autoScan)
                                    },
                                )
                            },
                        ) {
                            Text(payChoice.label)
                        }
                    },
                    dismissButton = {
                        val copyChoice = choices.first { it.lookupMode == "duplicar" }
                        TextButton(
                            onClick = {
                                showQrLookupChoices = false
                                localContext.startActivity(
                                    Intent(localContext, TicketLookupActivity::class.java).apply {
                                        putExtra(TicketLookupActivity.EXTRA_MODE, copyChoice.lookupMode)
                                        putExtra(TicketLookupActivity.EXTRA_AUTO_SCAN, copyChoice.autoScan)
                                    },
                                )
                            },
                        ) {
                            Text(copyChoice.label)
                        }
                    },
                )
            }
            val compact = maxWidth < 760.dp
            val saleModeContract = remember(visual.windowMode, initialSystemModeConfig.posLiteEnabled) {
                resolveVentaPosLiteContract(
                    windowMode = visual.windowMode,
                    posLiteEnabled = initialSystemModeConfig.posLiteEnabled,
                )
            }
            val saleWindowMode = saleModeContract.windowMode
            val stagedListMaxHeight = when {
                stagedRows.isEmpty() -> maxHeight * 0.18f
                saleModeContract.useTightSellingLayout -> maxHeight * 0.46f
                compact -> maxHeight * 0.42f
                else -> maxHeight * 0.46f
            }
            val stagedListMinHeight = when {
                saleModeContract.useTightSellingLayout -> 104.dp
                compact -> 96.dp
                else -> 118.dp
            }
            Column(
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                AppTopBar(
                    spec = ScreenChromeSpec(
                        title = "Venta",
                        subtitle = "${banca ?: session?.banca ?: "LotteryNet"} · ${session?.username ?: "Operador"}",
                        activeBottomTab = NativeBottomTab.SALE,
                        rightAction = ScreenChromeAction(
                            icon = Icons.Rounded.QrCodeScanner,
                            contentDescription = "Escanear ticket",
                            label = resolveVentaQrLookupActionContract().label,
                            onClick = { showQrLookupChoices = true },
                        ),
                    ),
                    onOpenMenu = { openShellMenu(localContext) },
                )
                val bodyModifier = Modifier
                    .padding(horizontal = visual.sizes.screenPaddingH)
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .heightIn(min = stagedListMinHeight, max = stagedListMaxHeight)
                VentaStagedList(
                    stagedRows = stagedRows,
                    modifier = bodyModifier,
                    windowMode = saleWindowMode,
                    onRemoveRow = { rowId -> stagedRows.removeAll { it.id == rowId } },
                    onClearRows = { showClearStagedRowsDialog = true },
                )
                VentaFixedComposer(
                    lottery = selectedLottery,
                    lotteryDecision = selectedLottery?.id?.let(lotteryDecisionsById::get),
                    selectedLotteries = selectedLotteries,
                    drawDayContract = drawDayContract,
                    selectedDrawDay = selectedDrawDay,
                    futureSaleEnabled = futureSaleEnabled,
                    canUseFutureSale = false,
                    number = number,
                    amount = amount,
                    classicMode = resolvedClassicMode,
                    pickMode = pickMode,
                    activeInput = activeInput,
                    validation = validation,
                    numberHasError = numberAdvanceState.showNumberError,
                    todayTicketCount = todayTicketCount,
                    todayActorSold = todayActorSold,
                    feedbackMessage = liveFeedbackMessage,
                    feedbackIsError = liveFeedbackIsError,
                    ticketSaveSyncContract = ticketSaveSyncContract,
                    quickActionContract = quickActionContract,
                    secondaryState = superPaleSecondaryState,
                    clockLabel = trustedClockLabel,
                    limitBadgeMain = saleLimitBadgeMain,
                    limitBadgeSub = if (role == UserRole.CASHIER) "tope" else "admin",
                    limitBadgeTone = saleLimitBadgeTone,
                    role = role,
                    sellerKeyLabel = activeTicketSeller?.displayLabel ?: "Cajero",
                    sellerDelegationActive = selectedAdminSeller != null,
                    canOpenOfficialTicket = ticketPrintOpenContract.showAction,
                    onActivateInput = { target ->
                        if (target == SaleInputTarget.AMOUNT && !numberAdvanceState.canAdvanceToAmount) {
                            validationMessage = if (number.isBlank()) "Digite la jugada primero." else validation.errorMessage
                        } else {
                            activeInput = target
                        }
                    },
                    onOpenLots = onOpenLots,
                    onToggleFutureSale = {
                        selectedDrawDay = SaleDrawDay.TODAY
                        validationMessage = "Venta futura fue retirada."
                    },
                    onSelectDrawDay = { nextMode ->
                        if (canUseFutureSaleControls(role, futureSaleEnabled)) {
                            if (nextMode != selectedDrawDay && stagedRows.isNotEmpty()) {
                                stagedRows.clear()
                                validationMessage = "Se limpiaron las jugadas al cambiar el día de venta."
                            }
                            selectedDrawDay = nextMode
                        }
                    },
                    onOpenLigar = onOpenLigar,
                    onToggleOrConfigureSuperPale = onToggleOrConfigureSuperPale,
                    onPickModeKey = { nextMode ->
                        val symbol = if (nextMode == PickPlayMode.BOX) "+" else "-"
                        val nextNumber = applyPickModeSymbolToNumber(number, symbol)
                        pickMode = nextMode
                        number = nextNumber
                        activeInput = resolvePickModeKeyNextInput(nextNumber, nextMode, activeInput)
                        validationMessage = null
                    },
                    onAddPlay = onAddPlay,
                    onOpenOfficialTicket = {
                        val currentMessage = validationMessage
                        if (ticketPrintOpenContract.stageCurrentPlayBeforeSave) {
                            onAddPlay()
                        }
                        val waitsForConfirmation = pendingDuplicatePlay != null || pendingPublishedResultPlay != null
                        val shouldSaveTicket = !waitsForConfirmation &&
                            (ticketPrintOpenContract.saveBeforeOpen || stagedRows.isNotEmpty())
                        if (shouldSaveTicket) {
                            salesActionScope.launch {
                                val ticket = saveTicket()
                                if (ticket != null) {
                                    printPreviewTicket = ticket
                                } else {
                                    val nextMessage = validationMessage
                                    validationMessage = when {
                                        waitsForConfirmation -> nextMessage
                                        !nextMessage.isNullOrBlank() && nextMessage != currentMessage -> nextMessage
                                        !ticketPrintOpenContract.fallbackMessage.isNullOrBlank() -> ticketPrintOpenContract.fallbackMessage
                                        !validation.errorMessage.isNullOrBlank() -> validation.errorMessage
                                        else -> "No hay ticket disponible para abrir"
                                    }
                                }
                            }
                        } else {
                            val ticket = if (ticketPrintOpenContract.openLatestTicket) latestTicket else null
                            if (ticket != null) {
                                printPreviewTicket = ticket
                            } else {
                                val nextMessage = validationMessage
                                validationMessage = when {
                                    waitsForConfirmation -> nextMessage
                                    !nextMessage.isNullOrBlank() && nextMessage != currentMessage -> nextMessage
                                    !ticketPrintOpenContract.fallbackMessage.isNullOrBlank() -> ticketPrintOpenContract.fallbackMessage
                                    !validation.errorMessage.isNullOrBlank() -> validation.errorMessage
                                    else -> "No hay ticket disponible para abrir"
                                }
                            }
                        }
                    },
                    onOpenSellerPicker = {
                        showAdminSellerPicker = true
                        thread(name = "native-admin-seller-refresh") {
                            val result = NativeUsersBootstrapper(usersRepository).bootstrap(forceRemoteRefresh = true)
                            (localContext as? android.app.Activity)?.runOnUiThread {
                                userDirectoryRefreshTick += 1
                                if (!result.ok) {
                                    validationMessage = "No se pudo actualizar cajeros desde servidor"
                                }
                            }
                        }
                    },
                    onApplyKey = onApplyKey,
                    total = stagedRows.sumOf { it.amount },
                    windowMode = saleWindowMode,
                )
            }
            if (showAdminSellerPicker) {
                AlertDialog(
                    onDismissRequest = { showAdminSellerPicker = false },
                    title = { Text("Vender como cajero") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (adminSellerRows.isEmpty()) {
                                Text("No hay cajeros para esta banca.")
                            } else {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 420.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    items(adminSellerRows) { row ->
                                        val cashier = row.account
                                        TextButton(
                                            onClick = {
                                                if (!row.enabled) return@TextButton
                                                selectedAdminSellerId = cashier.id
                                                showAdminSellerPicker = false
                                                validationMessage = "Vendedor: ${row.title}"
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp),
                                            enabled = row.enabled,
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(1.dp),
                                                ) {
                                                    Text(
                                                        text = row.title,
                                                        style = MaterialTheme.typography.titleSmall.copy(
                                                            fontWeight = if (row.selected) FontWeight.Black else FontWeight.ExtraBold,
                                                        ),
                                                        color = if (row.enabled) visual.colors.ink else visual.colors.muted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Text(
                                                        text = row.subtitle,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = visual.colors.muted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                if (row.selected) {
                                                    Text(
                                                        text = "Activo",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = Color(0xFF059669),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAdminSellerPicker = false }) {
                            Text("Cerrar")
                        }
                    },
                )
            }
            lotteryPickerTarget?.let { pickerTarget ->
                VentaLotteryPickerOverlay(
                    target = pickerTarget,
                    superPaleEnabled = resolvedClassicMode == "SP",
                    selectedLotteryIds = selectedLotteryIds,
                    lotteries = pickerLotteries,
                    decisionsByLotteryId = lotteryDecisionsById,
                    onDismiss = { lotteryPickerTarget = null },
                    onSelectTarget = { lotteryPickerTarget = it },
                    onSelectLottery = { lottery ->
                        val nextSelection = applyLotteryPickerSelection(
                            current = selectedLotteryIds,
                            selectedLotteryId = lottery.id,
                            selectedLotteryType = lottery.type,
                            currentSelectionTypes = selectedLotteries.map { it.type },
                            target = pickerTarget,
                            classicMode = resolvedClassicMode,
                        )
                        selectedLotteryIds = nextSelection
                        validationMessage = when {
                            resolvedClassicMode != "SP" && lottery.id in nextSelection ->
                                "${lottery.name} agregada"
                            resolvedClassicMode != "SP" ->
                                "${lottery.name} quitada"
                            resolvedClassicMode == "SP" && nextSelection.size < 2 -> "Super Pale activo: falta la 2da lotería"
                            resolvedClassicMode == "SP" && pickerTarget == LotteryPickerTarget.SECONDARY -> "Super Pale listo"
                            else -> "${lottery.name} seleccionada"
                        }
                        if (resolvedClassicMode == "SP") {
                            lotteryPickerTarget = null
                        }
                    },
                )
            }
            printPreviewTicket?.let { ticket ->
                val outputBancaName = resolveTicketOutputBancaName(
                    ticket = ticket,
                    defaultBancaName = banca.orEmpty().ifBlank { "LotteryNet" },
                    accounts = usersRepository.getAdmins() + usersRepository.getSupervisors() + usersRepository.getCashiers(),
                )
                SalePrintPreviewOverlay(
                    ticket = ticket,
                    bancaName = outputBancaName,
                    bancaLogoUri = bancaLogoUri,
                    onDismiss = { printPreviewTicket = null },
                    onQuickThermal = {
                        validationMessage = "Enviando a impresora..."
                        thread(name = "native-ticket-thermal-print") {
                            val prefs = LocalThermalPrinterRepository(localContext).getPrefs()
                            val content = ThermalTicketRenderer().renderTicket(
                                ticket = ticket,
                                bancaName = outputBancaName,
                                prefs = prefs,
                            )
                            val printTargets = resolveSaleThermalPrintTargets(
                                hasBluetoothPrinter = prefs.selectedPrinterAddress.isNotBlank(),
                                hasIntegratedPrinter = IntegratedThermalPrinter.isAvailable(localContext),
                            )
                            var printResult = BluetoothThermalPrinter.PrintResult(
                                success = false,
                                message = "No hay impresora conectada",
                            )
                            for (target in printTargets) {
                                printResult = when (target) {
                                    SaleThermalPrintTarget.BLUETOOTH -> BluetoothThermalPrinter.printText(
                                        context = localContext,
                                        content = content,
                                        prefs = prefs,
                                    )
                                    SaleThermalPrintTarget.INTEGRATED -> IntegratedThermalPrinter.printText(
                                        context = localContext,
                                        content = content,
                                    )
                                    SaleThermalPrintTarget.NONE -> BluetoothThermalPrinter.PrintResult(
                                        success = false,
                                        message = "No hay impresora conectada",
                                    )
                                }
                                if (printResult.success) break
                            }
                            (localContext as? android.app.Activity)?.runOnUiThread {
                                val flow = resolveSaleThermalPrintResult(
                                    success = printResult.success,
                                    message = printResult.message,
                                )
                                validationMessage = flow.message
                                if (flow.closePreview) {
                                    printPreviewTicket = null
                                }
                                if (flow.openPrinterSettings) {
                                    localContext.startActivity(Intent(localContext, PrinterActivity::class.java).apply {
                                        putExtra(PrinterActivity.EXTRA_TICKET_ID, ticket.id)
                                        putExtra(PrinterActivity.EXTRA_TICKET_EPOCH, ticket.createdAtEpochMs)
                                        putExtra(PrinterActivity.EXTRA_THERMAL_TITLE, "Ticket")
                                    })
                                }
                            }
                        }
                    },
                    onOpenOfficial = {
                        localContext.startActivity(Intent(localContext, TicketOfficialActivity::class.java).apply {
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_ID, ticket.id)
                            putExtra(TicketOfficialActivity.EXTRA_TICKET_EPOCH, ticket.createdAtEpochMs)
                            putExtra(TicketOfficialActivity.EXTRA_BANCA_NAME, outputBancaName)
                        })
                    },
                    onShareWhatsApp = {
                        sharePreviewTicketImage(ticket, true)
                    },
                    onShareImage = {
                        sharePreviewTicketImage(ticket, false)
                    },
                )
            }
        }
    }
    }
    }
}

@Composable
private fun VentaStagedList(
    stagedRows: List<SaleStagedRow>,
    modifier: Modifier = Modifier,
    windowMode: LotteryNetWindowMode,
    onRemoveRow: (String) -> Unit,
    onClearRows: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val listLayout = resolveVentaStagedListLayout(windowMode)
    val listState = rememberLazyListState()
    var previousCount by remember { mutableStateOf(stagedRows.size) }
    var previousFirstRowId by remember { mutableStateOf(stagedRows.firstOrNull()?.id) }
    val currentFirstRowId = stagedRows.firstOrNull()?.id
    LaunchedEffect(stagedRows.size, currentFirstRowId) {
        val action = resolveStagedPlayScrollAction(
            previousCount = previousCount,
            currentCount = stagedRows.size,
            previousFirstRowId = previousFirstRowId,
            currentFirstRowId = currentFirstRowId,
        )
        previousCount = stagedRows.size
        previousFirstRowId = currentFirstRowId
        if (action == StagedPlayScrollAction.ScrollToFirst && stagedRows.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFF8FBFF),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF2F7FC),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = listLayout.headerVerticalPaddingDp.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Lotería", modifier = Modifier.weight(1.05f), style = MaterialTheme.typography.labelSmall, color = visual.colors.muted)
                    Text("Tipo", modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, color = visual.colors.muted, textAlign = TextAlign.Center)
                    Text("Jugada", modifier = Modifier.weight(1.55f), style = MaterialTheme.typography.labelSmall, color = visual.colors.muted, textAlign = TextAlign.End)
                    Text("Monto", modifier = Modifier.weight(0.95f), style = MaterialTheme.typography.labelSmall, color = visual.colors.muted, textAlign = TextAlign.End)
                    if (stagedRows.isNotEmpty()) {
                        IconButton(
                            onClick = onClearRows,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RemoveCircleOutline,
                                contentDescription = "Limpiar lista de jugadas",
                                tint = lossColor(),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }
            }
            if (stagedRows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (windowMode == LotteryNetWindowMode.POS_TIGHT) 76.dp else 92.dp)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Sin jugadas",
                            style = MaterialTheme.typography.titleSmall,
                            color = visual.colors.ink,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Número -> monto -> Q/P/T",
                            style = MaterialTheme.typography.labelSmall,
                            color = visual.colors.muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    state = listState,
                    reverseLayout = shouldReverseVentaStagedListForLatestPlay(),
                ) {
                    items(stagedRows, key = { it.id }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(horizontal = 8.dp, vertical = listLayout.rowVerticalPaddingDp.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(row.lotteryName, modifier = Modifier.weight(1.05f), style = MaterialTheme.typography.labelMedium, color = visual.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.label, modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall, color = visual.colors.muted, textAlign = TextAlign.Center)
                            Text(
                                row.displayNumber,
                                modifier = Modifier.weight(1.55f),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight(listLayout.numberWeight),
                                ),
                                color = Color(0xFF020617),
                                textAlign = TextAlign.End,
                                maxLines = 1,
                            )
                            Text(formatMoney(row.amount), modifier = Modifier.weight(0.95f), style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace), color = gainColor(), textAlign = TextAlign.End)
                            Surface(
                                onClick = { onRemoveRow(row.id) },
                                modifier = Modifier.width(48.dp),
                                color = Color(0xFFFEF2F2),
                                shape = RoundedCornerShape(7.dp),
                            ) {
                                Box(modifier = Modifier.padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                                    Text("×", color = lossColor(), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        HorizontalDivider(color = visual.colors.border.copy(alpha = listLayout.separatorAlpha))
                    }
                }
            }
        }
    }
}

@Composable
private fun VentaFixedComposer(
    lottery: LotteryCatalogItem?,
    lotteryDecision: com.lotterynet.pro.core.model.LotteryCloseDecision?,
    selectedLotteries: List<LotteryCatalogItem>,
    drawDayContract: SaleDrawDayContract,
    selectedDrawDay: SaleDrawDay,
    futureSaleEnabled: Boolean,
    canUseFutureSale: Boolean,
    number: String,
    amount: String,
    classicMode: String,
    pickMode: PickPlayMode,
    activeInput: SaleInputTarget,
    validation: com.lotterynet.pro.core.model.SaleValidationResult,
    numberHasError: Boolean,
    todayTicketCount: Int,
    todayActorSold: Double,
    feedbackMessage: String?,
    feedbackIsError: Boolean,
    ticketSaveSyncContract: TicketSaveSyncUiContract?,
    quickActionContract: QuickActionContract,
    secondaryState: SuperPaleSecondaryState,
    clockLabel: String,
    limitBadgeMain: String,
    limitBadgeSub: String,
    limitBadgeTone: SaleLimitBadgeTone,
    role: UserRole,
    sellerKeyLabel: String,
    sellerDelegationActive: Boolean,
    canOpenOfficialTicket: Boolean,
    onActivateInput: (SaleInputTarget) -> Unit,
    onOpenLots: () -> Unit,
    onToggleFutureSale: () -> Unit,
    onSelectDrawDay: (SaleDrawDay) -> Unit,
    onOpenLigar: () -> Unit,
    onToggleOrConfigureSuperPale: () -> Unit,
    onPickModeKey: (PickPlayMode) -> Unit,
    onAddPlay: () -> Unit,
    onOpenOfficialTicket: () -> Unit,
    onOpenSellerPicker: () -> Unit,
    onApplyKey: (String) -> Unit,
    total: Double,
    windowMode: LotteryNetWindowMode,
) {
    val visual = rememberLotteryNetVisualSpec()
    val keypadLayout = resolveVentaKeypadLayout(windowMode)
    val entryLayout = resolveVentaEntryStripLayout(windowMode)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 0.dp,
    ) {
        Column {
            if (canUseFutureSale) {
                VentaDrawDaySelector(
                    selectedDrawDay = selectedDrawDay,
                    futureSaleEnabled = futureSaleEnabled,
                    contract = drawDayContract,
                    onToggleFutureSale = onToggleFutureSale,
                    onSelectDrawDay = onSelectDrawDay,
                )
            }
            VentaLotMetaBlock(
                lottery = lottery,
                lotteryDecision = lotteryDecision,
                selectedLotteries = selectedLotteries,
                classicMode = classicMode,
                pickMode = pickMode,
                validation = validation,
                secondaryState = secondaryState,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(entryLayout.itemSpacingDp.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VentaInputDisplay(
                    modifier = Modifier.weight(entryLayout.jugadaWeight),
                    label = "Jugada",
                    value = number,
                    active = activeInput == SaleInputTarget.NUMBER,
                    accent = visual.colors.tickets,
                    error = numberHasError,
                    valueAlign = TextAlign.Start,
                    onClick = { onActivateInput(SaleInputTarget.NUMBER) },
                )
                VentaMiniStatus(
                    title = "Límite",
                    main = limitBadgeMain,
                    sub = limitBadgeSub,
                    widthDp = entryLayout.limitWidthDp,
                    mainFontSp = entryLayout.limitFontSp,
                    tone = limitBadgeTone,
                )
                VentaInputDisplay(
                    modifier = Modifier.weight(entryLayout.montoWeight),
                    label = "Monto",
                    value = amount,
                    active = activeInput == SaleInputTarget.AMOUNT,
                    accent = gainColor(),
                    valueAlign = TextAlign.End,
                    onClick = { onActivateInput(SaleInputTarget.AMOUNT) },
                )
            }
            VentaQuickActions(
                contract = quickActionContract,
                onOpenLots = onOpenLots,
                onOpenLigar = onOpenLigar,
                onToggleOrConfigureSuperPale = onToggleOrConfigureSuperPale,
            )
            Spacer(modifier = Modifier.height(1.dp))
            if (shouldShowVentaInlineFeedbackBanner(feedbackMessage, feedbackIsError, numberHasError)) {
                Text(
                    text = feedbackMessage.orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (feedbackIsError) Color(0xFFFEF2F2) else Color(0xFFEAFBF2))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (feedbackIsError) lossColor() else gainColor(),
                )
            }
            if (keypadLayout.totalAboveKeypad) {
                CompactTotalBar(
                    total = total,
                    label = "Total jugada",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            VentaKeypad(
                activeInput = activeInput,
                todayTicketCount = todayTicketCount,
                todayActorSold = todayActorSold,
                windowMode = windowMode,
                role = role,
                sellerKeyLabel = sellerKeyLabel,
                sellerDelegationActive = sellerDelegationActive,
                canOpenOfficialTicket = canOpenOfficialTicket,
                pickKeypad = lottery?.let { supportsPickModes(it) } == true,
                pickMode = pickMode,
                onSelectTarget = onActivateInput,
                onApplyKey = onApplyKey,
                onPickModeKey = onPickModeKey,
                onAddPlay = onAddPlay,
                onOpenOfficialTicket = onOpenOfficialTicket,
                onOpenSellerPicker = onOpenSellerPicker,
            )
        }
    }
}

@Composable
private fun VentaDrawDaySelector(
    selectedDrawDay: SaleDrawDay,
    futureSaleEnabled: Boolean,
    contract: SaleDrawDayContract,
    onToggleFutureSale: () -> Unit,
    onSelectDrawDay: (SaleDrawDay) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (contract.isFuture) Color(0xFFFFFBEB) else Color(0xFFF8FAFC))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onToggleFutureSale,
            modifier = Modifier.height(34.dp),
        ) {
            Text(
                text = if (futureSaleEnabled) "Futura ON" else "Futura OFF",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                color = if (futureSaleEnabled) Color(0xFF92400E) else visual.colors.ink,
            )
        }
        if (futureSaleEnabled) {
            listOf(SaleDrawDay.TODAY to "Hoy", SaleDrawDay.TOMORROW to "Mañana").forEach { (mode, label) ->
                val active = selectedDrawDay == mode
                TextButton(
                    onClick = { onSelectDrawDay(mode) },
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                        color = if (active) gainColor() else visual.colors.ink,
                    )
                }
            }
            Text(
                text = contract.subtitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (contract.isFuture) Color(0xFF92400E) else visual.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun VentaLotMetaBlock(
    lottery: LotteryCatalogItem?,
    lotteryDecision: com.lotterynet.pro.core.model.LotteryCloseDecision?,
    selectedLotteries: List<LotteryCatalogItem>,
    classicMode: String,
    pickMode: PickPlayMode,
    validation: com.lotterynet.pro.core.model.SaleValidationResult,
    secondaryState: SuperPaleSecondaryState,
) {
    val visual = rememberLotteryNetVisualSpec()
    val modeLabel = lottery?.let { activeModeShortLabel(it, classicMode, pickMode) } ?: "Sin modo"
    val pillLabel = when {
        classicMode == "SP" && secondaryState.requiresSecondarySelection -> "Elige 2"
        validation.isValid -> "Lista"
        else -> "Abierta"
    }
    val pillTone = when {
        classicMode == "SP" && secondaryState.requiresSecondarySelection -> Color(0xFFC2410C)
        validation.isValid -> gainColor()
        else -> Color(0xFF166534)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFBFCFE))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedLotteries.joinToString(" · ") { it.name }.ifBlank { "Sin lotería" },
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CompactStatusBadge(
                label = pillLabel,
                tone = pillTone,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = visual.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            lottery?.let {
                Text(
                    text = formatLotteryClock12(lotteryDecision?.closeTime ?: it.baseCloseTime),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = visual.colors.neutral,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun VentaQuickActions(
    contract: QuickActionContract,
    onOpenLots: () -> Unit,
    onOpenLigar: () -> Unit,
    onToggleOrConfigureSuperPale: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CompactActionButton(
            label = contract.lotsLabel,
            onClick = onOpenLots,
            modifier = Modifier.weight(1f),
            tone = contract.lotsTone,
        )
        CompactActionButton(
            label = contract.ligarLabel,
            onClick = onOpenLigar,
            modifier = Modifier.weight(1f),
            enabled = contract.ligarEnabled,
            tone = contract.ligarTone,
        )
        if (contract.superPaleVisible) {
            CompactActionButton(
                label = contract.superPaleLabel,
                onClick = onToggleOrConfigureSuperPale,
                modifier = Modifier.weight(1f),
                active = contract.superPaleLabel == "SP Activo",
                tone = ActionTone.Success,
            )
        }
    }
}

@Composable
private fun VentaLotteryPickerOverlay(
    target: LotteryPickerTarget,
    superPaleEnabled: Boolean,
    selectedLotteryIds: List<String>,
    lotteries: List<LotteryCatalogItem>,
    decisionsByLotteryId: Map<String, com.lotterynet.pro.core.model.LotteryCloseDecision>,
    onDismiss: () -> Unit,
    onSelectTarget: (LotteryPickerTarget) -> Unit,
    onSelectLottery: (LotteryCatalogItem) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val overflow = resolveOverflowLayoutContract(visual.windowMode)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x7A101A24))
            .padding(horizontal = overflow.sheetHorizontalPadding, vertical = overflow.sheetVerticalPadding),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val maxSheetHeight = maxHeight * overflow.sheetMaxHeightFraction
        val maxListHeight = maxHeight * overflow.listMaxHeightFraction
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = maxSheetHeight),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, visual.colors.border),
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(overflow.compactSpacing),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (target == LotteryPickerTarget.SECONDARY) "Seleccionar 2da loteria" else "Seleccionar loteria",
                            style = MaterialTheme.typography.titleMedium,
                            color = visual.colors.ink,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "La que cierra primero sale arriba",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                        )
                    }
                    CompactActionButton(
                        label = "Cerrar",
                        onClick = onDismiss,
                        tone = ActionTone.Primary,
                    )
                }
                if (superPaleEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CompactActionButton(
                            label = "Principal",
                            onClick = { onSelectTarget(LotteryPickerTarget.PRIMARY) },
                            modifier = Modifier.weight(1f),
                            tone = ActionTone.Warning,
                            active = target == LotteryPickerTarget.PRIMARY,
                        )
                        CompactActionButton(
                            label = "2da loteria",
                            onClick = { onSelectTarget(LotteryPickerTarget.SECONDARY) },
                            modifier = Modifier.weight(1f),
                            active = target == LotteryPickerTarget.SECONDARY,
                        )
                    }
                }
                if (lotteries.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, visual.colors.border),
                    ) {
                        Text(
                            text = "No hay loterías disponibles para venta ahora mismo.",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = visual.colors.muted,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 0.dp, max = maxListHeight),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(lotteries) { lottery ->
                            val decision = decisionsByLotteryId[lottery.id]
                            val isSelected = lottery.id in selectedLotteryIds
                            val accent = when (decision?.state) {
                                CloseState.DANGER -> Color(0xFFC2410C)
                                CloseState.WARNING -> warningColor()
                                else -> gainColor()
                            }
                            Surface(
                                onClick = { onSelectLottery(lottery) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFFDC2626) else Color.White,
                                contentColor = if (isSelected) Color.White else visual.colors.ink,
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF991B1B) else visual.colors.border),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 9.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LotteryLogo(
                                        assetPath = lottery.logoAssetPath,
                                        fallback = lottery.name,
                                        modifier = Modifier.size(24.dp),
                                        tintColor = Color.Transparent,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = lottery.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) Color.White else visual.colors.ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = decision?.reason ?: "Disponible",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) Color.White.copy(alpha = 0.78f) else visual.colors.muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        if (isSelected) {
                                            CompactStatusBadge(
                                                label = if (selectedLotteryIds.firstOrNull() == lottery.id) "Activa" else "SP",
                                                tone = accent,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        Text(
                                            text = formatLotteryClock12(decision?.closeTime ?: lottery.baseCloseTime),
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                            color = if (isSelected) Color.White else accent,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VentaInputDisplay(
    modifier: Modifier,
    label: String,
    value: String,
    active: Boolean,
    accent: Color,
    error: Boolean = false,
    valueAlign: TextAlign,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val borderColor = when {
        error -> lossColor()
        active -> accent
        else -> visual.colors.border
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.muted,
            textAlign = valueAlign,
        )
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            color = when {
                error -> Color(0xFFFFECE8)
                active -> Color(0xFFEAF2FF)
                else -> visual.colors.panelAlt
            },
            border = BorderStroke(if (error) 2.dp else 1.5.dp, borderColor),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 28.dp else 34.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = visual.colors.ink,
                    ),
                    textAlign = valueAlign,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(if (visual.windowMode == com.lotterynet.pro.ui.common.LotteryNetWindowMode.POS_TIGHT) 14.dp else 18.dp)
                            .background(accent),
                    )
                }
            }
        }
    }
}

@Composable
private fun VentaMiniStatus(
    title: String,
    main: String,
    sub: String,
    widthDp: Int,
    mainFontSp: Int,
    tone: SaleLimitBadgeTone,
) {
    val visual = rememberLotteryNetVisualSpec()
    val mainColor = when (tone) {
        SaleLimitBadgeTone.GREEN -> visual.colors.gain
        SaleLimitBadgeTone.RED -> visual.colors.loss
        SaleLimitBadgeTone.NEUTRAL -> visual.colors.neutral
    }
    Column(
        modifier = Modifier.width(widthDp.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = visual.colors.muted, fontWeight = FontWeight.Bold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, visual.colors.border),
            shape = RoundedCornerShape(7.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 28.dp else 34.dp)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    main,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = mainFontSp.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = mainColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(sub, style = MaterialTheme.typography.labelSmall, color = visual.colors.muted, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VentaKeypad(
    activeInput: SaleInputTarget,
    todayTicketCount: Int,
    todayActorSold: Double,
    windowMode: LotteryNetWindowMode,
    role: UserRole,
    sellerKeyLabel: String,
    sellerDelegationActive: Boolean,
    canOpenOfficialTicket: Boolean,
    pickKeypad: Boolean,
    pickMode: PickPlayMode,
    onSelectTarget: (SaleInputTarget) -> Unit,
    onApplyKey: (String) -> Unit,
    onPickModeKey: (PickPlayMode) -> Unit,
    onAddPlay: () -> Unit,
    onOpenOfficialTicket: () -> Unit,
    onOpenSellerPicker: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val keypadLayout = resolveVentaKeypadLayout(windowMode)
    val keypadShape = RoundedCornerShape(10.dp)
    val keypadLine = Color(0xFFE5ECF4)
    val sellerKeyVisual = resolveVentaSellerKeyVisualContract(sellerDelegationActive)
    val keys = resolveVentaKeyRows(role, pickKeypad = pickKeypad)
    val keyHeightDp = if (pickKeypad && windowMode == LotteryNetWindowMode.POS_TIGHT) {
        (keypadLayout.keyHeightDp - 5).coerceAtLeast(39)
    } else if (pickKeypad) {
        (keypadLayout.keyHeightDp - 4).coerceAtLeast(42)
    } else {
        keypadLayout.keyHeightDp
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp),
        shape = keypadShape,
        color = keypadLine,
        border = BorderStroke(0.75.dp, keypadLine),
    ) {
        Column(
            modifier = Modifier.padding(0.dp),
            verticalArrangement = Arrangement.spacedBy(keypadLayout.keySpacingDp.dp),
        ) {
            if (keypadLayout.showStatsBadges) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    CompactStatusBadge(label = "$todayTicketCount hoy", tone = visual.colors.tickets)
                    CompactStatusBadge(label = "$${formatMoney(todayActorSold)}", tone = gainColor())
                }
            }
            keys.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(keypadLayout.keySpacingDp.dp),
                ) {
                    row.forEachIndexed { columnIndex, key ->
                        val keyVisual = resolveVentaKeyVisualContract(key)
                        val pickKeySelected = (key == "-" && pickMode == PickPlayMode.STRAIGHT) ||
                            (key == "+" && pickMode == PickPlayMode.BOX)
                        val pickCommandKey = pickKeypad && key in setOf("-", "+", "/", "*")
                        Surface(
                            onClick = {
                                when (key) {
                                    "PRINT" -> onOpenOfficialTicket()
                                    "SELLER" -> onOpenSellerPicker()
                                    "-" -> onPickModeKey(PickPlayMode.STRAIGHT)
                                    "+" -> onPickModeKey(PickPlayMode.BOX)
                                    "OK" -> if (activeInput == SaleInputTarget.NUMBER) {
                                        onSelectTarget(SaleInputTarget.AMOUNT)
                                    } else {
                                        onAddPlay()
                                    }
                                    else -> onApplyKey(key)
                                }
                            },
                            modifier = Modifier
                                .weight(resolveVentaKeyWeight(key, pickKeypad = pickKeypad))
                                .height(keyHeightDp.dp),
                            color = when (key) {
                                "OK" -> Color(0xFF1FC98B)
                                "PRINT" -> Color(0xFF0F172A)
                                "SELLER" -> if (sellerKeyVisual.backgroundTone == "blue") Color(0xFF2563EB) else Color(0xFF0F172A)
                                "-", "+" -> if (pickKeySelected) Color(0xFF2563EB) else Color(0xFFEFF6FF)
                                "/", "*" -> Color(0xFFEFF6FF)
                                "⌫" -> Color(0xFFDC2626)
                                else -> Color(0xFFFDFEFF)
                            },
                            border = BorderStroke(0.75.dp, keypadLine),
                            shape = ventaKeyShape(
                                rowIndex = rowIndex,
                                columnIndex = columnIndex,
                                rowCount = keys.size,
                                columnCount = row.size,
                            ),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (key == "PRINT") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Print,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(if (keypadLayout.showStatsBadges) 13.dp else 12.dp),
                                        )
                                        Text(
                                            text = "PRINT",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontSize = keypadLayout.commandKeyFontSp.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                            ),
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                } else if (key == "SELLER" && sellerKeyVisual.iconOnly) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                                        contentDescription = "Elegir cajero",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else if (keyVisual.useIcon) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                        contentDescription = keyVisual.contentDescription,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                } else if (pickCommandKey) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = (keypadLayout.numberKeyFontSp - 2).coerceAtLeast(17).sp,
                                                fontWeight = FontWeight.Black,
                                            ),
                                            color = if (pickKeySelected) Color.White else Color(0xFF1E40AF),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                        )
                                        Text(
                                            text = when (key) {
                                                "-" -> "STR"
                                                "+" -> "BOX"
                                                "/" -> "LIG"
                                                "*" -> "S+B"
                                                else -> ""
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = (keypadLayout.commandKeyFontSp - 2).coerceAtLeast(9).sp,
                                                fontWeight = FontWeight.ExtraBold,
                                            ),
                                            color = if (pickKeySelected) Color.White else Color(0xFF1E40AF),
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                        )
                                    }
                                } else {
                                    val isNumberKey = key.all { it.isDigit() }
                                    val label = when (key) {
                                        "OK" -> if (keypadLayout.showStatsBadges) "OK\nENTER" else "OK"
                                        "SELLER" -> sellerKeyLabel.ifBlank { "Cajero" }
                                        else -> key
                                    }
                                    Text(
                                        text = label,
                                        style = if (isNumberKey) {
                                            MaterialTheme.typography.headlineSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = keypadLayout.numberKeyFontSp.sp,
                                                fontWeight = FontWeight.Black,
                                            )
                                        } else {
                                            MaterialTheme.typography.labelMedium.copy(
                                                fontSize = keypadLayout.commandKeyFontSp.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                            )
                                        },
                                        color = when (key) {
                                            "OK" -> Color.White
                                            "SELLER" -> Color.White
                                            "⌫" -> Color.White
                                            else -> visual.colors.ink
                                        },
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LotteryChip(
    lottery: LotteryCatalogItem,
    selected: Boolean,
    decision: com.lotterynet.pro.core.model.LotteryCloseDecision,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val accent = when (decision.state) {
        CloseState.CLOSED -> lossColor()
        CloseState.DANGER -> Color(0xFFC2410C)
        CloseState.WARNING -> warningColor()
        else -> colorFromHex(lottery.colorHex)
    }
    val selectedBackground = Color(0xFF111111)
    val selectedContent = Color.White
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) selectedBackground else visual.colors.panel,
        contentColor = if (selected) selectedContent else visual.colors.ink,
        border = BorderStroke(1.dp, if (selected) accent else visual.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LotteryLogo(
                assetPath = lottery.logoAssetPath,
                fallback = lottery.name,
                modifier = Modifier.size(18.dp),
                tintColor = if (selected) selectedContent else Color.Transparent,
            )
            Column {
                Text(
                    lottery.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) selectedContent else visual.colors.ink,
                )
                Text(
                    text = decision.reason ?: "Disponible",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) selectedContent.copy(alpha = 0.72f) else visual.colors.muted,
                )
            }
            CompactStatusBadge(
                label = lotteryDecisionPill(decision),
                tone = accent,
            )
        }
    }
}

@Composable
private fun SelectedLotteryMetaSection(
    selectedLotteries: List<LotteryCatalogItem>,
    closePolicy: LotteryClosePolicy,
    territory: LotteryTerritory,
    manualClosedLotteryIds: Set<String>,
    calendarClosedLotteryIds: Set<String>,
    nowUtcMs: Long,
    allowAdminAfterCloseGrace: Boolean = false,
) {
    CompactPanel(alt = true) {
        SectionHeader(title = "Loterías activas", meta = "${selectedLotteries.size} seleccionadas")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            selectedLotteries.forEach { lottery ->
                val decision = closePolicy.resolveCloseDecision(
                    lottery = lottery,
                    operationTerritory = territory,
                    manualClosedLotteryIds = manualClosedLotteryIds,
                    calendarClosedLotteryIds = calendarClosedLotteryIds,
                    allowAdminAfterCloseGrace = allowAdminAfterCloseGrace,
                    nowUtcMs = nowUtcMs,
                )
                val tone = lotteryDecisionTone(lottery, decision)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LotteryLogo(
                        assetPath = lottery.logoAssetPath,
                        fallback = lottery.name,
                        modifier = Modifier.size(26.dp),
                        tintColor = Color.Transparent,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = lottery.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = rememberLotteryNetVisualSpec().colors.ink,
                        )
                        Text(
                            text = lotteryDecisionSubtitle(decision),
                            style = MaterialTheme.typography.bodySmall,
                            color = rememberLotteryNetVisualSpec().colors.muted,
                        )
                    }
                    CompactStatusBadge(label = lotteryDecisionPill(decision), tone = tone)
                }
            }
        }
    }
}

@Composable
private fun ModeStrip(
    lottery: LotteryCatalogItem,
    classicMode: String,
    pickMode: PickPlayMode,
    onClassicModeChange: (String) -> Unit,
    onPickModeChange: (PickPlayMode) -> Unit,
) {
    val classicModes = listOf(
        "Q" to "Quiniela",
        "P" to "Pale",
        "T" to "Tripleta",
        "SP" to "Super Pale",
    )
    val modeCount = if (supportsPickModes(lottery)) 2 else classicModes.size
    CompactAdaptiveGrid(
        itemCount = modeCount,
        columns = 2,
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
    ) { index, itemModifier ->
        if (lottery.playCapabilities.supportsStraight || lottery.playCapabilities.supportsBox) {
            if (index == 0) {
                CompactModeButton(
                    title = "Straight",
                    active = pickMode == PickPlayMode.STRAIGHT,
                    onClick = { onPickModeChange(PickPlayMode.STRAIGHT) },
                    modifier = itemModifier,
                )
            } else {
                CompactModeButton(
                    title = "Box",
                    active = pickMode == PickPlayMode.BOX,
                    onClick = { onPickModeChange(PickPlayMode.BOX) },
                    modifier = itemModifier,
                )
            }
        } else {
            val (key, label) = classicModes[index]
            CompactModeButton(
                title = label,
                active = classicMode == key,
                onClick = { onClassicModeChange(key) },
                modifier = itemModifier,
            )
        }
    }
}

@Composable
private fun CompactModeButton(title: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CompactActionButton(label = title, onClick = onClick, active = active, modifier = modifier)
}

@Composable
private fun EntryCard(
    number: String,
    amount: String,
    onNumberChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    activeInput: SaleInputTarget,
    onActivateInput: (SaleInputTarget) -> Unit,
    lottery: LotteryCatalogItem?,
    selectedLotteries: List<LotteryCatalogItem>,
    classicMode: String,
    pickMode: PickPlayMode,
    validation: com.lotterynet.pro.core.model.SaleValidationResult,
    todayTicketCount: Int,
    todayActorSold: Double,
    clockLabel: String,
    onClassicModeChange: (String) -> Unit,
    onPickModeChange: (PickPlayMode) -> Unit,
    onSecondaryAction: () -> Unit,
    canSecondaryAction: Boolean,
    canToggleSuperPale: Boolean,
    superPaleEnabled: Boolean,
    onToggleSuperPale: () -> Unit,
    onAddPlay: () -> Unit,
    onOpenOfficialTicket: () -> Unit,
    canOpenOfficialTicket: Boolean,
    feedbackMessage: String?,
    feedbackIsError: Boolean,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel {
        SectionHeader(
            title = "Entrada rápida",
            meta = selectedLotteries.joinToString(" · ") { it.name }.ifBlank { "Sin lotería" },
        )
        Column {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CompactStatusBadge(
                            label = activeTypeCode(lottery, classicMode, pickMode),
                            tone = visual.colors.tickets,
                        )
                        CompactStatusBadge(
                            label = "$todayTicketCount hoy",
                            tone = visual.colors.muted,
                        )
                        CompactStatusBadge(
                            label = clockLabel,
                            tone = visual.colors.ink,
                        )
                    }
                    CompactMetricBox(
                        title = "Vendido",
                        value = "$${formatMoney(todayActorSold)}",
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    CompactEntryField(
                        modifier = Modifier.weight(1.18f),
                        label = "Jugada",
                        value = number,
                        onValueChange = {
                            onNumberChange(
                                sanitizeSaleNumberInput(
                                    raw = it,
                                    supportsPickModes = lottery?.let(::supportsPickModes) == true,
                                ),
                            )
                        },
                        keyboardType = if (lottery?.let(::supportsPickModes) == true) KeyboardType.Text else KeyboardType.Number,
                        imeAction = ImeAction.Next,
                        active = activeInput == SaleInputTarget.NUMBER,
                        onActivate = { onActivateInput(SaleInputTarget.NUMBER) },
                        onSubmit = { onActivateInput(SaleInputTarget.AMOUNT) },
                    )
                    CompactMetricBox(
                        modifier = Modifier.width(64.dp),
                        title = "Modo",
                        value = activeTypeCode(lottery, classicMode, pickMode),
                        center = true,
                    )
                    CompactEntryField(
                        modifier = Modifier.weight(0.92f),
                        label = "Monto",
                        value = amount,
                        onValueChange = onAmountChange,
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                        active = activeInput == SaleInputTarget.AMOUNT,
                        onActivate = { onActivateInput(SaleInputTarget.AMOUNT) },
                        onSubmit = onAddPlay,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                lottery?.let {
                    ModeStrip(
                        lottery = it,
                        classicMode = classicMode,
                        pickMode = pickMode,
                        onClassicModeChange = onClassicModeChange,
                        onPickModeChange = onPickModeChange,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                QuickActionsRow(
                    canSecondaryAction = canSecondaryAction,
                    secondaryLabel = secondaryActionLabel(lottery, classicMode, pickMode),
                    canToggleSuperPale = canToggleSuperPale,
                    superPaleEnabled = superPaleEnabled,
                    onAddPlay = onAddPlay,
                    onSecondaryAction = onSecondaryAction,
                    onToggleSuperPale = onToggleSuperPale,
                    onOpenOfficialTicket = onOpenOfficialTicket,
                    canOpenOfficialTicket = canOpenOfficialTicket,
                )
            }
            if (shouldShowVentaInlineFeedbackBanner(feedbackMessage, feedbackIsError, numberHasError = false)) {
                Text(
                    text = feedbackMessage.orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (feedbackIsError) Color(0xFFFEF2F2) else Color(0xFFE7F7EF),
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (feedbackIsError) lossColor() else gainColor(),
                )
            }
        }
    }
}

@Composable
private fun StageListSection(
    stagedRows: List<SaleStagedRow>,
    onRemoveRow: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var previousCount by remember { mutableStateOf(stagedRows.size) }
    var previousFirstRowId by remember { mutableStateOf(stagedRows.firstOrNull()?.id) }
    val currentFirstRowId = stagedRows.firstOrNull()?.id
    LaunchedEffect(stagedRows.size, currentFirstRowId) {
        val action = resolveStagedPlayScrollAction(
            previousCount = previousCount,
            currentCount = stagedRows.size,
            previousFirstRowId = previousFirstRowId,
            currentFirstRowId = currentFirstRowId,
        )
        previousCount = stagedRows.size
        previousFirstRowId = currentFirstRowId
        if (action == StagedPlayScrollAction.ScrollToFirst && stagedRows.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }
    CompactPanel {
        SectionHeader(title = "Jugadas pendientes", meta = "${stagedRows.size} cargadas")
        StageHeaderRow()
        if (stagedRows.isEmpty()) {
            CompactEmptyState(message = "Sin jugadas cargadas")
        } else {
            LazyColumn(
                modifier = Modifier.height((stagedRows.size.coerceAtMost(4) * 46).dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                state = listState,
                reverseLayout = shouldReverseVentaStagedListForLatestPlay(),
            ) {
                items(stagedRows, key = { it.id }) { row ->
                    SaleRowCard(row = row, onRemove = { onRemoveRow(row.id) })
                }
            }
        }
        CompactTotalBar(total = stagedRows.sumOf { it.amount })
    }
}

@Composable
private fun SaleRowCard(
    row: SaleStagedRow,
    onRemove: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        alt = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.lotteryName,
                        style = MaterialTheme.typography.labelMedium,
                        color = visual.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CompactStatusBadge(label = row.label, tone = visual.colors.neutral)
                }
                Text(
                    text = row.displayNumber,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                    color = visual.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatMoney(row.amount),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                color = gainColor(),
                textAlign = TextAlign.End,
            )
            CompactActionButton(
                label = "Quitar",
                onClick = onRemove,
                icon = Icons.Rounded.RemoveCircleOutline,
                modifier = Modifier.width(78.dp),
            )
        }
    }
}

@Composable
private fun SalePrintPreviewOverlay(
    ticket: TicketRecord,
    bancaName: String,
    bancaLogoUri: String,
    onDismiss: () -> Unit,
    onQuickThermal: () -> Unit,
    onOpenOfficial: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onShareImage: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val localContext = LocalContext.current
    val securityCode = remember(ticket.id, bancaName) {
        TicketSecurity.resolveSecurityCode(ticket, bancaName)
    }
    val renderBitmapPreview = remember(ticket) { shouldRenderSaleDeliveryBitmapPreview(ticket) }
    val bitmap = remember(ticket.id, ticket.createdAtEpochMs, bancaName, securityCode, bancaLogoUri, localContext, renderBitmapPreview) {
        if (!renderBitmapPreview) {
            null
        } else {
            runCatching {
                NativeBitmapExport.renderOfficialTicketBitmap(
                    context = localContext,
                    ticket = ticket,
                    bancaName = bancaName,
                    securityCode = securityCode,
                    bancaLogoUri = bancaLogoUri,
                )
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        CompactPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            SectionHeader(title = "Entrega del ticket", meta = "Preview oficial")
            CompactAdaptiveGrid(itemCount = 4, columns = 2) { index, itemModifier ->
                when (index) {
                    0 -> CompactActionButton(
                        label = "Imprimir",
                        onClick = onQuickThermal,
                        modifier = itemModifier,
                        icon = Icons.Rounded.Print,
                        tone = ActionTone.Primary,
                    )
                    1 -> CompactActionButton(
                        label = "WhatsApp",
                        onClick = onShareWhatsApp,
                        modifier = itemModifier,
                        icon = Icons.Rounded.Share,
                        tone = ActionTone.Success,
                    )
                    2 -> CompactActionButton(
                        label = "Compartir",
                        onClick = onShareImage,
                        modifier = itemModifier,
                        icon = Icons.Rounded.Share,
                        tone = ActionTone.Secondary,
                    )
                    else -> CompactActionButton(
                        label = "Ticket oficial",
                        onClick = onOpenOfficial,
                        modifier = itemModifier,
                        icon = Icons.Rounded.PointOfSale,
                        tone = ActionTone.Warning,
                    )
                }
            }
            CompactPanel(alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Preview de ticket",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (visual.windowMode == LotteryNetWindowMode.POS_TIGHT) 230.dp else 300.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Ticket listo",
                            style = MaterialTheme.typography.titleSmall,
                            color = visual.colors.ink,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${ticket.plays.size} jugadas · ${ticket.plays.mapNotNull { it.lotteryName }.distinct().size} loterias · ${formatMoney(ticket.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                        )
                        Text(
                            text = "La imagen se prepara solo al compartir para abrir el preview al instante.",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                        )
                    }
                }
            }
            CompactActionButton(
                label = "Cancelar",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                tone = ActionTone.Secondary,
            )
        }
    }
}

@Composable
private fun SalePrintActionCard(
    label: String,
    supporting: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tone: ActionTone,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = when (tone) {
            ActionTone.Success -> Color(0xFFF2FCF6)
            ActionTone.Primary -> Color(0xFFFFF9EB)
            else -> Color.White
        },
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = visual.colors.panelAlt,
                border = BorderStroke(1.dp, visual.colors.border),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                )
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun CompactEntryField(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    active: Boolean,
    onActivate: () -> Unit,
    onSubmit: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged {
            if (it.isFocused) onActivate()
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onNext = { onSubmit() },
            onDone = { onSubmit() },
        ),
        shape = RoundedCornerShape(8.dp),
        textStyle = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = visual.colors.ink,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = visual.colors.panel,
            unfocusedContainerColor = visual.colors.panel,
            focusedIndicatorColor = if (active) visual.colors.tickets else visual.colors.border,
            unfocusedIndicatorColor = if (active) visual.colors.tickets else visual.colors.border,
            focusedLabelColor = if (active) visual.colors.tickets else visual.colors.muted,
        ),
    )
}

@Composable
private fun CompactMetricBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    center: Boolean = false,
) {
    val visual = rememberLotteryNetVisualSpec()
    Box(
        modifier = modifier
            .background(
                visual.colors.panelAlt,
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Column(
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.muted,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                color = visual.colors.ink,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    canSecondaryAction: Boolean,
    secondaryLabel: String,
    canToggleSuperPale: Boolean,
    superPaleEnabled: Boolean,
    onAddPlay: () -> Unit,
    onSecondaryAction: () -> Unit,
    onToggleSuperPale: () -> Unit,
    onOpenOfficialTicket: () -> Unit,
    canOpenOfficialTicket: Boolean,
) {
    val actionCount = 2 + (if (canToggleSuperPale) 1 else 0) + (if (canOpenOfficialTicket) 1 else 0)
    CompactAdaptiveGrid(
        itemCount = actionCount,
        modifier = Modifier.fillMaxWidth(),
        columns = actionCount.coerceAtMost(4),
        horizontalSpacing = 6.dp,
        verticalSpacing = 6.dp,
    ) { index, itemModifier ->
        when (index) {
            0 -> CompactActionButton(
                label = "Agregar",
                onClick = onAddPlay,
                modifier = itemModifier,
                icon = Icons.Rounded.PointOfSale,
                tone = ActionTone.Success,
            )
            1 -> CompactActionButton(
                label = secondaryLabel,
                onClick = onSecondaryAction,
                modifier = itemModifier,
                icon = Icons.Rounded.Link,
                enabled = canSecondaryAction,
            )
            2 -> {
                if (canToggleSuperPale) {
                    val label = if (superPaleEnabled) "SP Activo" else "Super Pale"
                    CompactActionButton(
                        label = label,
                        onClick = onToggleSuperPale,
                        modifier = itemModifier,
                        icon = Icons.Rounded.LocalFireDepartment,
                        active = superPaleEnabled,
                        tone = if (superPaleEnabled) ActionTone.Success else ActionTone.Secondary,
                    )
                } else {
                    CompactActionButton(
                        label = "Ticket",
                        onClick = onOpenOfficialTicket,
                        modifier = itemModifier,
                        icon = Icons.Rounded.PointOfSale,
                    )
                }
            }
            else -> CompactActionButton(
                label = "Ticket",
                onClick = onOpenOfficialTicket,
                modifier = itemModifier,
                icon = Icons.Rounded.PointOfSale,
            )
        }
    }
}

@Composable
private fun SaleKeypadPanel(
    activeInput: SaleInputTarget,
    onSelectTarget: (SaleInputTarget) -> Unit,
    onApplyKey: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val keys = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3", "M"),
        listOf("0", ".", "J"),
    )
    CompactPanel {
        SectionHeader(title = "Teclado", meta = if (activeInput == SaleInputTarget.NUMBER) "Jugada" else "Monto")
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    val isTargetToggle = key == "J" || key == "M"
                    val label = when (key) {
                        "J" -> "Jugada"
                        "M" -> "Monto"
                        else -> key
                    }
                    CompactActionButton(
                        label = label,
                        onClick = {
                            when (key) {
                                "J" -> onSelectTarget(SaleInputTarget.NUMBER)
                                "M" -> onSelectTarget(SaleInputTarget.AMOUNT)
                                else -> onApplyKey(key)
                            }
                        },
                        active = when (key) {
                            "J" -> activeInput == SaleInputTarget.NUMBER
                            "M" -> activeInput == SaleInputTarget.AMOUNT
                            else -> false
                        },
                        tone = when (key) {
                            "⌫" -> ActionTone.Danger
                            "J", "M" -> if ((key == "J" && activeInput == SaleInputTarget.NUMBER) || (key == "M" && activeInput == SaleInputTarget.AMOUNT)) ActionTone.Primary else ActionTone.Secondary
                            else -> ActionTone.Secondary
                        },
                        modifier = Modifier.weight(if (isTargetToggle) 1.3f else 1f),
                    )
                }
            }
        }
        Text(
            text = "Usa J para jugada y M para monto.",
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.muted,
        )
    }
}

@Composable
private fun CompactTotalBar(
    total: Double,
    label: String = "Total",
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        color = Color(0xFFF2FBF6),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFC7EFD6)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = visual.colors.muted,
            )
            Text(
                text = formatMoney(total),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = gainColor(),
            )
        }
    }
}

@Composable
private fun StageHeaderRow() {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panelAlt)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Lotería",
            modifier = Modifier.weight(1.05f),
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.muted,
        )
        Text(
            text = "Tipo",
            modifier = Modifier.width(42.dp),
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.muted,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Jugada",
            modifier = Modifier.weight(1.55f),
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.muted,
            textAlign = TextAlign.End,
        )
        Text(
            text = "Monto",
            modifier = Modifier.weight(0.95f),
            style = MaterialTheme.typography.labelSmall,
            color = visual.colors.muted,
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(24.dp))
    }
}

@Composable
private fun StatusPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private fun activeModeLabel(lottery: LotteryCatalogItem, classicMode: String, pickMode: PickPlayMode): String {
    return if (supportsPickModes(lottery)) {
        "${lottery.name} · modo ${if (pickMode == PickPlayMode.BOX) "Box" else "Straight"}"
    } else {
        "${lottery.name} · ${when (classicMode) {
            "P" -> "Pale"
            "T" -> "Tripleta"
            "SP" -> "Super Pale"
            else -> "Quiniela"
        }}"
    }
}

private fun activeModeShortLabel(lottery: LotteryCatalogItem, classicMode: String, pickMode: PickPlayMode): String {
    return if (supportsPickModes(lottery)) {
        if (pickMode == PickPlayMode.BOX) "Pick Box" else "Pick Straight"
    } else {
        when (classicMode) {
            "P" -> "Pale"
            "T" -> "Tripleta"
            "SP" -> "Super Pale"
            else -> "Quiniela"
        }
    }
}

internal fun activeTypeCode(lottery: LotteryCatalogItem?, classicMode: String, pickMode: PickPlayMode): String {
    lottery ?: return "-"
    return when (normalizePickLotteryType(lottery.type)) {
        "Pick3" -> if (pickMode == PickPlayMode.BOX) "P3BOX" else "P3"
        "Pick4" -> if (pickMode == PickPlayMode.BOX) "P4BOX" else "P4"
        else -> classicMode
    }
}

internal fun resolveSaleLimitBadgeMain(
    role: UserRole,
    classicMode: String,
    pickMode: PickPlayMode,
    limits: CashierSalesLimitInputs,
    sold: Double = 0.0,
    pending: Double = 0.0,
): String {
    if (role != UserRole.CASHIER) return "Sin tope"
    val limit = when (classicMode.uppercase(Locale.US)) {
        "Q" -> limits.quiniela
        "P" -> limits.pale
        "SP" -> limits.superPale
        "T" -> limits.tripleta
        else -> if (pickMode == PickPlayMode.BOX) limits.pick3Box else limits.pick3Straight
    }
    return formatSaleLimitBadge(limit, sold, pending)
}

private fun resolveSaleLimitBadgeMain(
    role: UserRole,
    lottery: LotteryCatalogItem?,
    classicMode: String,
    pickMode: PickPlayMode,
    limits: CashierSalesLimitInputs,
    sold: Double = 0.0,
    pending: Double = 0.0,
): String {
    if (role != UserRole.CASHIER) return "Sin tope"
    val limit = resolveSaleLimitValue(lottery, classicMode, pickMode, limits)
    return formatSaleLimitBadge(limit, sold, pending)
}

internal fun resolveSaleLimitBadgeMain(
    role: UserRole,
    row: SaleLimitRemainingRow?,
    currentAmount: Double? = null,
): String? {
    if (role != UserRole.CASHIER) return "Sin tope"
    if (row == null) return null
    val pending = resolveSaleLimitPendingPreview(
        stagedPending = row.pending,
        currentAmount = currentAmount,
    )
    return formatSaleLimitBadge(row.limit, row.sold, pending)
}

internal fun resolveSaleLimitBadgeTone(
    limit: Double,
    sold: Double = 0.0,
    pending: Double = 0.0,
): SaleLimitBadgeTone {
    if (limit <= 0.0) return SaleLimitBadgeTone.GREEN
    return if (sold > 0.0 || pending > 0.0) SaleLimitBadgeTone.RED else SaleLimitBadgeTone.GREEN
}

internal fun resolveSaleLimitPendingPreview(
    stagedPending: Double,
    currentAmount: Double?,
): Double = stagedPending + (currentAmount ?: 0.0).coerceAtLeast(0.0)

private fun resolveSaleLimitValue(
    lottery: LotteryCatalogItem?,
    classicMode: String,
    pickMode: PickPlayMode,
    limits: CashierSalesLimitInputs,
): Double {
    return when (lottery?.type?.let(::normalizePickLotteryType)) {
        "Pick4" -> if (pickMode == PickPlayMode.BOX) limits.pick4Box else limits.pick4Straight
        "Pick3" -> if (pickMode == PickPlayMode.BOX) limits.pick3Box else limits.pick3Straight
        else -> when (classicMode.uppercase(Locale.US)) {
            "Q" -> limits.quiniela
            "P" -> limits.pale
            "SP" -> limits.superPale
            "T" -> limits.tripleta
            else -> 0.0
        }
    }
}

private fun formatSaleLimitBadge(limit: Double, sold: Double = 0.0, pending: Double = 0.0): String {
    if (limit <= 0.0) return "Sin tope"
    return formatWholeAmount((limit - sold - pending).coerceAtLeast(0.0))
}

private fun secondaryActionLabel(
    lottery: LotteryCatalogItem?,
    classicMode: String,
    pickMode: PickPlayMode,
): String {
    lottery ?: return "Ligar"
    return if (supportsPickModes(lottery)) {
        if (pickMode == PickPlayMode.BOX) "Straight" else "Box"
    } else {
        when (classicMode) {
            "P" -> "Pale fijo"
            "T" -> "Tripleta fija"
            "SP" -> "Ligar"
            else -> "Ligar"
        }
    }
}

internal fun normalizePickLotteryType(type: String): String? {
    val normalized = type.filter(Char::isLetterOrDigit).lowercase(java.util.Locale.US)
    return when {
        normalized == "pick4" || normalized == "p4" || normalized.contains("pick4") -> "Pick4"
        normalized == "pick3" || normalized == "p3" || normalized.contains("pick3") -> "Pick3"
        else -> null
    }
}

internal fun supportsPickModes(lottery: LotteryCatalogItem): Boolean {
    return normalizePickLotteryType(lottery.type) != null ||
        lottery.playCapabilities.supportsStraight ||
        lottery.playCapabilities.supportsBox
}

private fun lotteryDecisionPill(decision: com.lotterynet.pro.core.model.LotteryCloseDecision): String {
    return presentLotteryDecisionPill(decision)
}

private fun lotteryDecisionSubtitle(decision: com.lotterynet.pro.core.model.LotteryCloseDecision): String {
    return presentLotteryDecisionSubtitle(decision)
}

private fun lotteryDecisionTone(
    lottery: LotteryCatalogItem,
    decision: com.lotterynet.pro.core.model.LotteryCloseDecision,
): Color {
    return when (decision.state) {
        CloseState.CLOSED -> lossColor()
        CloseState.DANGER -> Color(0xFFC2410C)
        CloseState.WARNING -> warningColor()
        CloseState.OPEN -> colorFromHex(lottery.colorHex)
    }
}

private fun formatSalesResultsDateKey(nowUtcMs: Long, territory: LotteryTerritory): String {
    val zone = when (territory) {
        LotteryTerritory.USA -> "America/New_York"
        LotteryTerritory.RD -> "America/Santo_Domingo"
    }
    return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(zone)
    }.format(Date(nowUtcMs))
}

private fun hasPublishedSaleResult(result: LotteryResult): Boolean {
    return !result.first.isNullOrBlank() ||
        !result.second.isNullOrBlank() ||
        !result.third.isNullOrBlank() ||
        !result.pick3.isNullOrBlank() ||
        !result.pick4.isNullOrBlank()
}

internal fun resolvePublishedResultSaleBlockLotteryIds(
    stagedRows: List<SaleStagedRow>,
    results: List<LotteryResult>,
): Set<String> {
    val stagedLotteryIds = stagedRows
        .flatMap { row -> listOfNotNull(row.lotteryId, row.secondaryLotteryId) }
        .toSet()
    if (stagedLotteryIds.isEmpty()) return emptySet()
    return resolvePublishedResultLotteryIdsForSale(results)
        .filter { it in stagedLotteryIds }
        .toSet()
}

internal fun resolvePublishedResultLotteryIdsForSale(
    results: List<LotteryResult>,
    resultDateKey: String? = null,
    nowUtcMs: Long? = null,
): Set<String> {
    return results
        .filter { result -> resultDateKey == null || resultBelongsToDate(result, resultDateKey) }
        .filter(::hasPublishedSaleResult)
        .flatMap { result ->
            saleBlockingIdsForPublishedResult(result)
                .filter { lotteryId -> saleResultCanBePublishedNow(lotteryId, resultDateKey, nowUtcMs) }
        }
        .toSet()
}

private fun saleBlockingIdsForPublishedResult(result: LotteryResult): Set<String> {
    val rawId = result.lotteryId.trim()
    val legacyNjPickId = remoteNjPickLegacySaleId(rawId, result.lotteryName)
    val canonicalPickKey = PickResultIdentityResolver.resolveResult(result)?.canonicalKey
    return buildSet {
        legacyNjPickId?.let(::add)
        if (legacyNjPickId == null) {
            rawId.takeIf { it.isNotBlank() }?.let(::add)
        }
        canonicalPickKey
            ?.let { saleCatalogIdsByPickCanonicalKey[it] }
            ?.let(::addAll)
    }
}

private val saleCatalogIdsByPickCanonicalKey: Map<String, Set<String>> by lazy {
    StaticLotteryCatalogRepository().getAllLotteries()
        .mapNotNull { lottery ->
            PickResultIdentityResolver.resolveLottery(lottery)?.canonicalKey?.let { key -> key to lottery.id }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, ids) -> ids.toSet() }
}

private val saleCatalogById: Map<String, LotteryCatalogItem> by lazy {
    StaticLotteryCatalogRepository().getAllLotteries().associateBy { it.id }
}

private fun saleResultCanBePublishedNow(
    lotteryId: String,
    resultDateKey: String?,
    nowUtcMs: Long?,
): Boolean {
    if (resultDateKey == null || nowUtcMs == null) return true
    val lottery = saleCatalogById[lotteryId] ?: return true
    val schedule = UsPickScheduleResolver.resolve(lottery.id, lottery.name)
    val zoneId = schedule?.timeZoneId ?: when (lottery.territory) {
        LotteryTerritory.USA -> "America/New_York"
        LotteryTerritory.RD -> "America/Santo_Domingo"
    }
    val drawTime = schedule?.drawTime ?: lottery.baseDrawTime
    val drawUtcMs = parseSaleResultDrawUtcMs(resultDateKey, drawTime, zoneId) ?: return true
    return nowUtcMs >= drawUtcMs
}

private fun parseSaleResultDrawUtcMs(
    dateKey: String,
    drawTime: String,
    zoneId: String,
): Long? {
    val normalizedDate = normalizeSaleResultDateKey(dateKey) ?: return null
    val date = runCatching {
        LocalDate.parse(normalizedDate, DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US))
    }.getOrNull() ?: return null
    val normalizedTime = drawTime.trim().uppercase(Locale.US)
    val time = listOf(
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        DateTimeFormatter.ofPattern("hh:mm a", Locale.US),
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
        DateTimeFormatter.ofPattern("HH:mm", Locale.US),
    ).firstNotNullOfOrNull { formatter ->
        runCatching { LocalTime.parse(normalizedTime, formatter) }.getOrNull()
    } ?: return null
    val zone = runCatching { ZoneId.of(zoneId) }.getOrNull() ?: return null
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

private fun normalizeSaleResultDateKey(dateKey: String): String? {
    val value = dateKey.trim()
    val iso = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(value)
    if (iso != null) {
        val (year, month, day) = iso.destructured
        return "$day-$month-$year"
    }
    val local = Regex("""^(\d{2})-(\d{2})-(\d{4})$""").matchEntire(value)
    return local?.value
}

private fun remoteNjPickLegacySaleId(rawId: String, lotteryName: String?): String? {
    val text = "$rawId ${lotteryName.orEmpty()}".uppercase(Locale.US)
    if (!text.contains("NJ") && !text.contains("NEW JERSEY")) return null
    val isPick3 = rawId.uppercase(Locale.US).startsWith("US-P3-NJ-") || text.contains("PICK-3") || text.contains("PICK 3")
    val isPick4 = rawId.uppercase(Locale.US).startsWith("US-P4-NJ-") || text.contains("PICK-4") || text.contains("PICK 4")
    val isMidday = "MIDDAY" in text || "DIA" in text || "DAY" in text
    val isEvening = "EVENING" in text || "NOCHE" in text || "NIGHT" in text
    return when {
        isPick3 && isMidday -> "19"
        isPick3 && isEvening -> "20"
        isPick4 && isMidday -> "21"
        isPick4 && isEvening -> "22"
        else -> null
    }
}

private fun canRunSecondaryAction(
    lottery: LotteryCatalogItem?,
    classicMode: String,
    stagedRows: List<SaleStagedRow>,
): Boolean {
    if (lottery?.let { supportsPickModes(it) } == true) {
        return true
    }
    if (classicMode != "Q") {
        return false
    }
    return stagedRows
        .filter { it.playType == "Q" && it.secondaryLotteryId == null }
        .groupBy { it.lotteryId }
        .any { (_, rows) -> rows.map { it.number }.distinct().size >= 2 }
}

private fun buildSaleDraftSnapshotFromTicket(ticket: TicketRecord): SaleDraftSnapshot {
    val rows = buildList {
        ticket.plays.forEach { play ->
            val lotteryId = play.lotteryId.orEmpty()
            val lotteryName = play.lotteryName.orEmpty()
            if (lotteryId.isBlank() || lotteryName.isBlank()) return@forEach
            add(
                SaleStagedRow(
                    lotteryId = lotteryId,
                    lotteryName = lotteryName,
                    secondaryLotteryId = play.secondaryLotteryId,
                    secondaryLotteryName = play.secondaryLotteryName,
                    playType = play.playType,
                    label = ticketPlayLabel(play.playType),
                    number = play.number,
                    displayNumber = formatPlayDisplayNumber(play.number, play.playType),
                    amount = play.amount,
                ),
            )
        }
    }.distinctBy { row ->
        listOf(
            row.lotteryId,
            row.secondaryLotteryId.orEmpty(),
            row.playType,
            row.number,
            row.amount.toString(),
        ).joinToString("|")
    }
    val selectedLotteryIds = rows
        .flatMap { row -> listOfNotNull(row.lotteryId, row.secondaryLotteryId) }
        .distinct()
    val classicMode = rows.firstOrNull()?.playType?.takeIf { it in setOf("Q", "P", "T", "SP") } ?: "Q"
    return SaleDraftSnapshot(
        draft = SaleDraft(
            selectedLotteryIds = selectedLotteryIds,
            secondaryLotteryId = rows.firstOrNull()?.secondaryLotteryId,
            classicMode = classicMode,
            superPaleEnabled = rows.any { it.playType == "SP" },
        ),
        stagedRows = rows,
        savedAtEpochMs = System.currentTimeMillis(),
    )
}

private fun ticketPlayLabel(playType: String): String {
    return when (playType) {
        "SP" -> "Super Pale"
        "P" -> "Pale"
        "T" -> "Tripleta"
        "Q" -> "Quiniela"
        else -> playType
    }
}

internal data class ClassicModeTransition(
    val selection: List<String>,
    val message: String?,
)

internal fun resolveClassicModeTransition(
    nextMode: String,
    currentSelection: List<String>,
    fallbackLotteryId: String?,
): ClassicModeTransition {
    val fallbackSelection = currentSelection.ifEmpty { fallbackLotteryId?.let(::listOf).orEmpty() }
    return when (nextMode) {
        "SP" -> {
            val selection = emptyList<String>()
            ClassicModeTransition(
                selection = selection,
                message = "Super Pale activo: elige 2 loterías",
            )
        }

        "P" -> ClassicModeTransition(
            selection = fallbackSelection.take(1),
            message = "Modo Pale activo: usa 4 dígitos",
        )

        "T" -> ClassicModeTransition(
            selection = fallbackSelection.take(1),
            message = "Modo Tripleta activo: usa 6 dígitos",
        )

        else -> ClassicModeTransition(
            selection = fallbackSelection.take(1),
            message = "Modo Quiniela activo: usa 2 dígitos",
        )
    }
}

private fun colorFromHex(raw: String): Color {
    return runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrDefault(Color(0xFF07111F))
}

private fun formatTrustedTime(
    nowUtcMs: Long,
    trustedClockRepository: LocalTrustedClockRepository,
    territory: LotteryTerritory,
): String {
    val zone = java.util.TimeZone.getTimeZone(trustedClockRepository.getOperationTimeZone(territory))
    val format = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.US)
    format.timeZone = zone
    return format.format(java.util.Date(nowUtcMs))
}

internal fun applyLotteryPickerSelection(
    current: List<String>,
    selectedLotteryId: String,
    selectedLotteryType: String,
    currentSelectionTypes: List<String>,
    target: LotteryPickerTarget,
    classicMode: String,
): List<String> {
    if (classicMode != "SP") {
        val selectedPickType = normalizePickLotteryType(selectedLotteryType)
        val selectedIsPick = selectedPickType != null
        val currentPickTypes = currentSelectionTypes.mapNotNull(::normalizePickLotteryType).distinct()
        val currentHasNormal = currentSelectionTypes.any { normalizePickLotteryType(it) == null }
        val currentHasPick = currentPickTypes.isNotEmpty()
        val shouldReplaceSelection = when {
            selectedIsPick && !currentHasPick -> true
            selectedIsPick && currentHasNormal -> true
            !selectedIsPick && currentHasPick -> true
            selectedIsPick && currentPickTypes.any { it != selectedPickType } -> true
            else -> false
        }
        if (shouldReplaceSelection) {
            return listOf(selectedLotteryId)
        }
        return if (selectedLotteryId in current) {
            current.filterNot { it == selectedLotteryId }
        } else {
            current + selectedLotteryId
        }
    }
    val currentPrimary = current.firstOrNull()
    val currentSecondary = current.getOrNull(1)
    return when (target) {
        LotteryPickerTarget.PRIMARY -> {
            val preservedSecondary = currentSecondary?.takeIf { it != selectedLotteryId }
            listOfNotNull(selectedLotteryId, preservedSecondary)
        }

        LotteryPickerTarget.SECONDARY -> {
            val primary = currentPrimary?.takeIf { it != selectedLotteryId }
            if (primary == null) {
                listOf(selectedLotteryId)
            } else {
                listOf(primary, selectedLotteryId)
            }
        }
    }
}

internal enum class StagedPlayScrollAction {
    KeepPosition,
    ScrollToFirst,
}

internal fun resolveStagedPlayScrollAction(
    previousCount: Int,
    currentCount: Int,
    previousFirstRowId: String? = null,
    currentFirstRowId: String? = null,
): StagedPlayScrollAction {
    val topRowChanged = currentCount > 0 &&
        previousFirstRowId != null &&
        currentFirstRowId != null &&
        previousFirstRowId != currentFirstRowId
    return if (currentCount > previousCount || topRowChanged) {
        StagedPlayScrollAction.ScrollToFirst
    } else {
        StagedPlayScrollAction.KeepPosition
    }
}

internal fun shouldReportSalesBackgroundRefreshFailure(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        if (current is CancellationException) return false
        if (current.message?.contains("left the composition", ignoreCase = true) == true) return false
        current = current.cause
    }
    return true
}

private fun formatMoney(amount: Double): String = formatWholeAmount(amount)

private fun formatRecentTicketTime(epochMs: Long): String {
    return java.text.SimpleDateFormat("dd/MM hh:mm a", java.util.Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(java.util.Date(epochMs))
}

private fun buildWebCompatibleTicketJson(
    ticket: TicketRecord,
    session: com.lotterynet.pro.core.model.ActiveSession?,
    banca: String?,
): JSONObject {
    return JSONObject().apply {
        put("id", ticket.id)
        put("type", "lot")
        put("lots", ticket.plays.joinToString(" / ") { it.lotteryName.orEmpty() }.ifBlank { banca.orEmpty() })
        put("lotteries", ticket.plays.joinToString(" / ") { it.lotteryName.orEmpty() }.ifBlank { banca.orEmpty() })
        put(
            "items",
            JSONArray(ticket.plays.map { play ->
                JSONObject().apply {
                    put("type", play.playType)
                    put("nums", play.number)
                    put("amt", play.amount)
                    put("lotId", play.lotteryId)
                    put("lotName", play.lotteryName)
                    put("lotId2", play.secondaryLotteryId)
                    put("lotName2", play.secondaryLotteryName)
                }
            }),
        )
        put("tot", ticket.total)
        put("total", ticket.total)
        put("bancaNombre", banca ?: session?.banca.orEmpty())
        put("bancaAddr", "")
        put("bancaTel", "")
        put("bancaOwn", "")
        put("adminId", ticket.adminId ?: session?.adminId ?: session?.userId.orEmpty())
        put("cajeroId", if (ticket.role == UserRole.CASHIER) ticket.sellerId.orEmpty() else JSONObject.NULL)
        put("vendedorId", ticket.sellerId ?: session?.userId.orEmpty())
        put("vendedorRol", ticket.role.name.lowercase(Locale.US))
        put("vendedorNombre", ticket.sellerUser ?: session?.username.orEmpty())
        put("saleMode", "online")
        put("offlineSale", false)
        put("createdAtMs", ticket.createdAtEpochMs)
        put("updatedAt", ticket.createdAtEpochMs)
        put("date", formatRdDate(ticket.createdAtEpochMs))
        put("time", formatRdTime(ticket.createdAtEpochMs))
        put("securityCode", ticket.securityCode ?: "")
        put("st", "active")
        put("status", "active")
    }
}

private fun formatRdDate(epochMs: Long): String {
    val format = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
    format.timeZone = java.util.TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(java.util.Date(epochMs))
}

private fun formatRdTime(epochMs: Long): String {
    val format = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.US)
    format.timeZone = java.util.TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(java.util.Date(epochMs))
}
