package com.lotterynet.pro.ui.finance

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.finance.FinanceActorPeriodRow
import com.lotterynet.pro.core.finance.CashierFinanceSummary
import com.lotterynet.pro.core.finance.FinanceAlert
import com.lotterynet.pro.core.finance.FinanceAlertTone
import com.lotterynet.pro.core.finance.FinanceHistoryEntry
import com.lotterynet.pro.core.finance.FinancePeriodPreset
import com.lotterynet.pro.core.finance.FinancePeriodReport
import com.lotterynet.pro.core.finance.FinanceScope
import com.lotterynet.pro.core.finance.FinanceScopeType
import com.lotterynet.pro.core.finance.FinanceSummary
import com.lotterynet.pro.core.finance.resolveFinanceRemoteRefreshDecision
import com.lotterynet.pro.core.finance.resolveOperationalReportNet
import com.lotterynet.pro.core.finance.LocalFinanceRepository
import com.lotterynet.pro.core.finance.TurnoFinanceSummary
import com.lotterynet.pro.core.format.formatWholeMoney
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.SessionSnapshot
import com.lotterynet.pro.core.storage.LocalFinanceHistoryRepository
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeRechargeCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.LocalSyncFreshnessRepository
import com.lotterynet.pro.core.sync.SyncFreshnessState
import com.lotterynet.pro.core.sync.SyncFreshnessType
import com.lotterynet.pro.core.sync.buildSyncFreshnessKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.ui.common.*
import com.lotterynet.pro.ui.printer.PrinterActivity
import com.lotterynet.pro.ui.master.MasterDashboardActivity
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.shell.ShellActivity
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.text.input.KeyboardType
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class FinanceHeaderActionId {
    WHATSAPP,
    SHARE,
    PRINT,
    THERMAL,
    SAVE,
    REPORTS,
}

internal data class FinanceLayoutContract(
    val compactHeader: Boolean,
    val useDenseRows: Boolean,
    val inlineTotals: Boolean,
    val prioritizeDeliveryActions: Boolean,
    val headerPaddingVerticalDp: Int,
    val actionColumns: Int,
)

internal data class FinanceExportMenuContract(
    val visibleButtonLabel: String,
    val visibleButtonCount: Int,
    val menuLabels: List<String>,
    val usesOverflowMenu: Boolean,
)

internal fun resolveFinanceExportMenuContract(actions: List<FinanceHeaderActionId>): FinanceExportMenuContract {
    return FinanceExportMenuContract(
        visibleButtonLabel = "Exportar",
        visibleButtonCount = 1,
        menuLabels = actions.mapNotNull(::financeHeaderActionLabel),
        usesOverflowMenu = true,
    )
}

private fun financeHeaderActionLabel(action: FinanceHeaderActionId): String? {
    return when (action) {
        FinanceHeaderActionId.WHATSAPP -> "WhatsApp"
        FinanceHeaderActionId.SHARE -> "Compartir"
        FinanceHeaderActionId.PRINT -> "Imprimir"
        FinanceHeaderActionId.THERMAL -> "Térmico"
        FinanceHeaderActionId.SAVE -> "Guardar"
        FinanceHeaderActionId.REPORTS -> null
    }
}

internal fun resolveFinanceLayout(windowMode: com.lotterynet.pro.ui.common.LotteryNetWindowMode): FinanceLayoutContract {
    return when (windowMode) {
        com.lotterynet.pro.ui.common.LotteryNetWindowMode.POS_TIGHT,
        com.lotterynet.pro.ui.common.LotteryNetWindowMode.POS -> FinanceLayoutContract(
            compactHeader = true,
            useDenseRows = true,
            inlineTotals = true,
            prioritizeDeliveryActions = true,
            headerPaddingVerticalDp = 6,
            actionColumns = 3,
        )

        else -> FinanceLayoutContract(
            compactHeader = false,
            useDenseRows = false,
            inlineTotals = true,
            prioritizeDeliveryActions = false,
            headerPaddingVerticalDp = 9,
            actionColumns = 3,
        )
    }
}

internal fun resolveFinanceHeaderActionOrder(prioritizeDeliveryActions: Boolean): List<FinanceHeaderActionId> {
    return if (prioritizeDeliveryActions) {
        listOf(
            FinanceHeaderActionId.WHATSAPP,
            FinanceHeaderActionId.SHARE,
            FinanceHeaderActionId.PRINT,
            FinanceHeaderActionId.THERMAL,
            FinanceHeaderActionId.SAVE,
        )
    } else {
        listOf(
            FinanceHeaderActionId.PRINT,
            FinanceHeaderActionId.THERMAL,
            FinanceHeaderActionId.WHATSAPP,
            FinanceHeaderActionId.SHARE,
            FinanceHeaderActionId.SAVE,
        )
    }
}

internal fun resolveFinanceHeaderActionRows(
    actions: List<FinanceHeaderActionId>,
    actionColumns: Int,
): List<List<FinanceHeaderActionId>> {
    return actions.chunked(actionColumns.coerceAtLeast(1))
}

internal fun resolveFinanceOperationSections(
    role: UserRole,
    hasActorSummary: Boolean,
): List<String> {
    return buildList {
        if (role == UserRole.MASTER) {
            add("MASTER_GLOBAL")
            add("BANKS")
            add("ALERTS")
        }
        if (role == UserRole.ADMIN) {
            add("GLOBAL")
            add("TEAM")
            add("ALERTS")
        }
        if (role == UserRole.SUPERVISOR) {
            add("SUPERVISOR_GROUP")
            add("TEAM")
            add("ALERTS")
        }
        if (hasActorSummary && role == UserRole.CASHIER) {
            add("PROFILE")
            add("ACTOR")
        }
    }
}

private enum class FinanceCompactSection(val id: String, val label: String) {
    SUMMARY("summary", "Resumen"),
    PERIOD("period", "Periodo"),
    DETAIL("detail", "Detalle"),
    CLOSE("close", "Cierre"),
}

private fun financeCompactSectionOptions(): List<QuickFilterChip> =
    FinanceCompactSection.entries.map { QuickFilterChip(it.id, it.label) }

internal fun canOpenFinanceForRole(role: UserRole): Boolean {
    return role == UserRole.MASTER || role == UserRole.ADMIN || role == UserRole.SUPERVISOR || role == UserRole.CASHIER
}

private data class FinanceDaySnapshot(
    val dayKey: String,
    val summary: FinanceSummary,
    val actorSummary: CashierFinanceSummary?,
    val turnoSummary: TurnoFinanceSummary?,
    val actorRows: List<FinanceActorPeriodRow>,
    val history: List<FinanceHistoryEntry>,
)

internal enum class FinanceStartupWork {
    LOAD_SESSION,
    LOAD_DAY_TOTALS,
    LOAD_ACTOR_SUMMARY,
    LOAD_TURNO_SUMMARY,
    LOAD_HISTORY,
    HYDRATE_REMOTE_DATA,
    RENDER_FINANCE_BITMAP,
    SHOW_TREND_BARS,
}

internal data class FinanceStartupPlan(
    val firstFrameWork: Set<FinanceStartupWork>,
    val afterFirstFrameWork: Set<FinanceStartupWork>,
)

internal fun resolveFinanceStartupPlan(): FinanceStartupPlan {
    return FinanceStartupPlan(
        firstFrameWork = setOf(
            FinanceStartupWork.LOAD_SESSION,
            FinanceStartupWork.LOAD_DAY_TOTALS,
        ),
        afterFirstFrameWork = setOf(
            FinanceStartupWork.LOAD_ACTOR_SUMMARY,
            FinanceStartupWork.LOAD_TURNO_SUMMARY,
            FinanceStartupWork.LOAD_HISTORY,
            FinanceStartupWork.HYDRATE_REMOTE_DATA,
        ),
    )
}

class FinanceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionRepository = LocalSessionRepository(this)
        val activeSession = sessionRepository.getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.FINANCE)) {
            return
        }
        val session = checkNotNull(activeSession)
        LocalUsersRepository(this).touchSession(session)
        val snapshot = sessionRepository.getSessionSnapshot()
        val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date())
        val salesRepository = LocalSalesRepository(this)
        val rechargeRepository = LocalRechargeRepository(this)
        val usersRepository = LocalUsersRepository(this)
        val financeRepository = LocalFinanceRepository(
            salesRepository = salesRepository,
            rechargeRepository = rechargeRepository,
            usersRepository = usersRepository,
        )
        val historyRepository = LocalFinanceHistoryRepository(this)
        val freshnessRepository = LocalSyncFreshnessRepository(this)
        val financeScope = financeRepository.resolveScope(session)
        fun loadDaySnapshot(selectedDayKey: String): FinanceDaySnapshot {
            val scopedSummary = financeRepository.getScopedDaySummary(selectedDayKey, financeScope)
            val scopedReport = financeRepository.getScopedPeriodReport(
                scope = financeScope,
                preset = FinancePeriodPreset.DAY,
                anchorDayKey = selectedDayKey,
            )
            val scopedActor = financeRepository.getActorSummary(
                dayKey = selectedDayKey,
                actorKey = session.userId,
                actorDisplay = session.username,
            )
            val scopedTurno = if (selectedDayKey == dayKey && snapshot?.turnoStartEpochMs != null) {
                financeRepository.getTurnoSummary(
                    dayKey = selectedDayKey,
                    session = session,
                    turnoStartEpochMs = snapshot.turnoStartEpochMs,
                )
            } else {
                null
            }
            return FinanceDaySnapshot(
                dayKey = selectedDayKey,
                summary = scopedSummary,
                actorSummary = scopedActor,
                turnoSummary = scopedTurno,
                actorRows = scopedReport.actorRows,
                history = loadFinanceHistory(historyRepository, session, selectedDayKey),
            )
        }
        val initialDaySnapshot = FinanceDaySnapshot(
            dayKey = dayKey,
            summary = financeRepository.getScopedDaySummary(dayKey, financeScope),
            actorSummary = null,
            turnoSummary = null,
            actorRows = emptyList(),
            history = emptyList(),
        )

        setContent {
            LotteryNetComposeTheme {
                FinanceRoute(
                    bancaName = session.banca ?: "LotteryNet",
                    activeSession = session,
                    dayKey = dayKey,
                    daySummary = initialDaySnapshot.summary,
                    actorSummary = initialDaySnapshot.actorSummary,
                    turnoSummary = initialDaySnapshot.turnoSummary,
                    initialHistory = initialDaySnapshot.history,
                    onLoadDaySnapshot = { selectedDayKey -> loadDaySnapshot(selectedDayKey) },
                    onShare = { selectedDayKey, summary, actorSummary, turnoSummary, cashEntered, turnoDiff ->
                        shareFinance(
                            bancaName = session.banca ?: "LotteryNet",
                            dayKey = selectedDayKey,
                            summary = summary,
                            actorLabel = actorSummary?.actorDisplay,
                            turnoSummary = turnoSummary,
                            cashEntered = cashEntered,
                            turnoDiff = turnoDiff,
                            whatsappOnly = false,
                        )
                    },
                    onWhatsApp = { selectedDayKey, summary, actorSummary, turnoSummary, cashEntered, turnoDiff ->
                        shareFinance(
                            bancaName = session.banca ?: "LotteryNet",
                            dayKey = selectedDayKey,
                            summary = summary,
                            actorLabel = actorSummary?.actorDisplay,
                            turnoSummary = turnoSummary,
                            cashEntered = cashEntered,
                            turnoDiff = turnoDiff,
                            whatsappOnly = true,
                        )
                    },
                    onSave = { selectedDayKey, summary, actorSummary, turnoSummary, cashEntered, turnoDiff ->
                        saveFinance(
                            bancaName = session.banca ?: "LotteryNet",
                            dayKey = selectedDayKey,
                            summary = summary,
                            actorLabel = actorSummary?.actorDisplay,
                            turnoSummary = turnoSummary,
                            cashEntered = cashEntered,
                            turnoDiff = turnoDiff,
                        )
                    },
                    onPrint = { selectedDayKey, summary, actorSummary, turnoSummary, cashEntered, turnoDiff ->
                        printFinance(
                            bancaName = session.banca ?: "LotteryNet",
                            dayKey = selectedDayKey,
                            summary = summary,
                            actorLabel = actorSummary?.actorDisplay,
                            turnoSummary = turnoSummary,
                            cashEntered = cashEntered,
                            turnoDiff = turnoDiff,
                        )
                    },
                    onThermal = { selectedDayKey, summary, actorSummary, turnoSummary, cashEntered, turnoDiff ->
                        openThermalFinance(
                            bancaName = session.banca ?: "LotteryNet",
                            dayKey = selectedDayKey,
                            summary = summary,
                            actorLabel = actorSummary?.actorDisplay,
                            turnoSummary = turnoSummary,
                            cashEntered = cashEntered,
                            turnoDiff = turnoDiff,
                        )
                    },
                    onLoadPeriodReport = { preset, anchorDay, fromDay, toDay ->
                        runCatching {
                            financeRepository.getScopedPeriodReport(
                                scope = financeScope ?: FinanceScope(type = FinanceScopeType.BANK),
                                preset = preset,
                                anchorDayKey = anchorDay ?: dayKey,
                                fromDayKey = fromDay,
                                toDayKey = toDay,
                            )
                        }.getOrNull()
                    },
                    onRefreshDaySummary = { selectedDayKey, callback ->
                        val active = session
                        run {
                            val ownerKey = resolveOperationalOwnerKey(active)
                            val cacheKey = buildSyncFreshnessKey(
                                type = SyncFreshnessType.FINANCE_DAY,
                                ownerKey = ownerKey,
                                banca = active.banca,
                                dateKey = selectedDayKey,
                            )
                            val currentSummary = financeRepository.getScopedDaySummary(selectedDayKey, financeScope)
                            val decision = resolveFinanceRemoteRefreshDecision(
                                hasLocalData = financeSummaryHasData(currentSummary),
                                forceRemote = false,
                                selectedDayKey = selectedDayKey,
                                todayDayKey = dayKey,
                                freshnessRecord = freshnessRepository.getRecord(cacheKey),
                                nowEpochMs = System.currentTimeMillis(),
                            )
                            if (!decision.shouldRefreshRemote) {
                                callback(currentSummary, decision.initialMessage)
                            } else {
                                thread(name = "finance-native-hydrate") {
                                    val ticketResult = runCatching {
                                        NativeOperationalSyncCoordinator(
                                            ticketGateway = NativeTicketCloudSyncCoordinator(
                                                salesRepository = salesRepository,
                                                queueRepository = NativeTicketSyncQueueRepository(this),
                                            ),
                                        ).syncTicketsForSession(active, force = true)
                                    }.getOrNull()
                                    val rechargeResult = runCatching {
                                        NativeRechargeCloudSyncCoordinator(rechargeRepository)
                                            .hydrateOwner(ownerKey)
                                    }.getOrNull()
                                    val refreshed = financeRepository.getScopedDaySummary(selectedDayKey, financeScope)
                                    val message = when {
                                        ticketResult?.ok == true && rechargeResult?.ok == true -> {
                                            freshnessRepository.mark(cacheKey, SyncFreshnessState.SERVER_UPDATED)
                                            "Actualizado desde servidor"
                                        }
                                        ticketResult == null && rechargeResult == null -> {
                                            if (financeSummaryHasData(refreshed)) {
                                                freshnessRepository.mark(cacheKey, SyncFreshnessState.SERVER_FAILED_USING_CACHE)
                                                "Sin conexión usando última copia"
                                            } else {
                                                "No se pudo cargar servidor"
                                            }
                                        }
                                        else -> listOfNotNull(ticketResult?.message, rechargeResult?.message)
                                            .joinToString(" · ")
                                            .ifBlank { "Datos locales listos" }
                                    }
                                    runOnUiThread { callback(refreshed, message) }
                                }
                            }
                        }
                    },
                    onSaveSnapshot = { selectedDayKey, summary, actorSummary, turnoSummary, cashEntered, turnoDiff ->
                        saveFinanceHistoryEntry(
                            historyRepository = historyRepository,
                            session = session,
                            dayKey = selectedDayKey,
                            summary = summary,
                            actorSummary = actorSummary,
                            turnoSummary = turnoSummary,
                            cashEntered = cashEntered,
                            turnoDiff = turnoDiff,
                            recordType = "snapshot",
                        )
                        loadFinanceHistory(historyRepository, session, selectedDayKey)
                    },
                    onCloseTurno = { selectedDayKey, summary, actorSummary, turnoSummary, cashEntered, turnoDiff ->
                        saveFinanceHistoryEntry(
                            historyRepository = historyRepository,
                            session = session,
                            dayKey = selectedDayKey,
                            summary = summary,
                            actorSummary = actorSummary,
                            turnoSummary = turnoSummary,
                            cashEntered = cashEntered,
                            turnoDiff = turnoDiff,
                            recordType = "close",
                        )
                        val currentSnapshot = sessionRepository.getSessionSnapshot()
                        if (selectedDayKey == dayKey && currentSnapshot != null) {
                            sessionRepository.saveSessionSnapshot(currentSnapshot.copy(turnoStartEpochMs = null))
                        } else if (selectedDayKey == dayKey) {
                            sessionRepository.saveSessionSnapshot(SessionSnapshot(activeSession = session, turnoStartEpochMs = null))
                        }
                        loadFinanceHistory(historyRepository, session, selectedDayKey)
                    },
                )
            }
        }
    }

    private fun shareFinance(
        bancaName: String,
        dayKey: String,
        summary: FinanceSummary,
        actorLabel: String?,
        turnoSummary: TurnoFinanceSummary?,
        cashEntered: Double?,
        turnoDiff: Double?,
        whatsappOnly: Boolean,
    ) {
        val bitmap = NativeBitmapExport.renderFinanceBitmap(
            bancaName = bancaName,
            dayKey = dayKey,
            summary = summary,
            actorLabel = actorLabel,
            turnoLabel = turnoSummary?.let { "${it.actorDisplay} · ${formatTurnoStamp(it.startedAtEpochMs)}" },
            turnoDiff = turnoDiff,
            cashEntered = cashEntered,
        )
        NativeBitmapExport.shareBitmap(
            context = this,
            bitmap = bitmap,
            fileName = "cuadre-$dayKey.png",
            title = "Cuadre $bancaName",
            text = buildFinanceShareText(bancaName, dayKey, summary, turnoDiff),
            whatsappOnly = whatsappOnly,
        )
    }

    private fun saveFinance(
        bancaName: String,
        dayKey: String,
        summary: FinanceSummary,
        actorLabel: String?,
        turnoSummary: TurnoFinanceSummary?,
        cashEntered: Double?,
        turnoDiff: Double?,
    ): Boolean {
        val bitmap = NativeBitmapExport.renderFinanceBitmap(
            bancaName = bancaName,
            dayKey = dayKey,
            summary = summary,
            actorLabel = actorLabel,
            turnoLabel = turnoSummary?.let { "${it.actorDisplay} · ${formatTurnoStamp(it.startedAtEpochMs)}" },
            turnoDiff = turnoDiff,
            cashEntered = cashEntered,
        )
        return NativeBitmapExport.saveBitmapToDownloads(this, bitmap, "cuadre-$dayKey.png")
    }

    private fun printFinance(
        bancaName: String,
        dayKey: String,
        summary: FinanceSummary,
        actorLabel: String?,
        turnoSummary: TurnoFinanceSummary?,
        cashEntered: Double?,
        turnoDiff: Double?,
    ): Boolean {
        val bitmap = NativeBitmapExport.renderFinanceBitmap(
            bancaName = bancaName,
            dayKey = dayKey,
            summary = summary,
            actorLabel = actorLabel,
            turnoLabel = turnoSummary?.let { "${it.actorDisplay} · ${formatTurnoStamp(it.startedAtEpochMs)}" },
            turnoDiff = turnoDiff,
            cashEntered = cashEntered,
        )
        return NativeBitmapExport.printBitmap(this, bitmap, "cuadre-$dayKey")
    }

    private fun openThermalFinance(
        bancaName: String,
        dayKey: String,
        summary: FinanceSummary,
        actorLabel: String?,
        turnoSummary: TurnoFinanceSummary?,
        cashEntered: Double?,
        turnoDiff: Double?,
    ) {
        val prefs = com.lotterynet.pro.core.storage.LocalThermalPrinterRepository(this).getPrefs()
        val renderer = com.lotterynet.pro.core.printing.ThermalTicketRenderer()
        val text = renderer.renderFinanceSummary(
            bancaName = bancaName,
            dayKey = dayKey,
            summary = summary,
            prefs = prefs,
            actorLabel = actorLabel,
            turnoLabel = turnoSummary?.let { "${it.actorDisplay} · ${formatTurnoStamp(it.startedAtEpochMs)}" },
            turnoDiff = turnoDiff,
            cashEntered = cashEntered,
        )
        startActivity(
            Intent(this, PrinterActivity::class.java).apply {
                putExtra(PrinterActivity.EXTRA_THERMAL_TITLE, "Cuadre térmico")
                putExtra(PrinterActivity.EXTRA_THERMAL_TEXT, text)
            },
        )
    }
}

@Composable
private fun FinanceRoute(
    bancaName: String,
    activeSession: ActiveSession?,
    dayKey: String,
    daySummary: FinanceSummary,
    actorSummary: CashierFinanceSummary?,
    turnoSummary: TurnoFinanceSummary?,
    initialHistory: List<FinanceHistoryEntry>,
    onLoadDaySnapshot: (String) -> FinanceDaySnapshot,
    onShare: (String, FinanceSummary, CashierFinanceSummary?, TurnoFinanceSummary?, Double?, Double?) -> Unit,
    onWhatsApp: (String, FinanceSummary, CashierFinanceSummary?, TurnoFinanceSummary?, Double?, Double?) -> Unit,
    onSave: (String, FinanceSummary, CashierFinanceSummary?, TurnoFinanceSummary?, Double?, Double?) -> Boolean,
    onPrint: (String, FinanceSummary, CashierFinanceSummary?, TurnoFinanceSummary?, Double?, Double?) -> Boolean,
    onThermal: (String, FinanceSummary, CashierFinanceSummary?, TurnoFinanceSummary?, Double?, Double?) -> Unit,
    onLoadPeriodReport: (FinancePeriodPreset, String?, String?, String?) -> FinancePeriodReport?,
    onRefreshDaySummary: (String, (FinanceSummary, String) -> Unit) -> Unit,
    onSaveSnapshot: (String, FinanceSummary, CashierFinanceSummary?, TurnoFinanceSummary?, Double?, Double?) -> List<FinanceHistoryEntry>,
    onCloseTurno: (String, FinanceSummary, CashierFinanceSummary?, TurnoFinanceSummary?, Double?, Double?) -> List<FinanceHistoryEntry>,
) {
    var cashInput by rememberSaveable { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var historyEntries by remember(initialHistory) { mutableStateOf(initialHistory) }
    var selectedDayKey by rememberSaveable { mutableStateOf(dayKey) }
    var selectedPeriodName by rememberSaveable { mutableStateOf(FinancePeriodPreset.DAY.name) }
    var selectedPeriodLabel by rememberSaveable { mutableStateOf(dayKey) }
    var rangeFromDayKey by rememberSaveable { mutableStateOf(dayKey) }
    var rangeToDayKey by rememberSaveable { mutableStateOf(dayKey) }
    var visibleSummary by remember(daySummary) { mutableStateOf(daySummary) }
    var visibleActorSummary by remember(actorSummary) { mutableStateOf(actorSummary) }
    var visibleTurnoSummary by remember(turnoSummary) { mutableStateOf(turnoSummary) }
    var visibleActorRows by remember { mutableStateOf(emptyList<FinanceActorPeriodRow>()) }
    var selectedFinanceSectionId by rememberSaveable { mutableStateOf(FinanceCompactSection.SUMMARY.id) }
    fun applyDaySelection(nextDayKey: String) {
        val snapshot = onLoadDaySnapshot(nextDayKey)
        selectedDayKey = snapshot.dayKey
        selectedPeriodName = FinancePeriodPreset.DAY.name
        selectedPeriodLabel = snapshot.dayKey
        rangeFromDayKey = snapshot.dayKey
        rangeToDayKey = snapshot.dayKey
        visibleSummary = snapshot.summary
        visibleActorSummary = snapshot.actorSummary
        visibleTurnoSummary = snapshot.turnoSummary
        visibleActorRows = snapshot.actorRows
        historyEntries = snapshot.history
        actionMessage = "Cuadre ${resolveFinanceSelectedDateLabel(snapshot.dayKey, dayKey)} cargado."
    }
    fun applyManualRange(nextFromDayKey: String, nextToDayKey: String) {
        val range = normalizeManualFinanceRange(nextFromDayKey, nextToDayKey)
        val report = onLoadPeriodReport(
            FinancePeriodPreset.CALENDAR,
            range.toDayKey,
            range.fromDayKey,
            range.toDayKey,
        )
        if (report != null) {
            selectedDayKey = range.toDayKey
            rangeFromDayKey = range.fromDayKey
            rangeToDayKey = range.toDayKey
            selectedPeriodName = FinancePeriodPreset.CALENDAR.name
            selectedPeriodLabel = formatManualFinanceRangeLabel(range.fromDayKey, range.toDayKey)
            visibleSummary = report.summary
            visibleActorSummary = null
            visibleTurnoSummary = null
            visibleActorRows = report.actorRows
            historyEntries = emptyList()
            actionMessage = "Rango $selectedPeriodLabel cargado."
        } else {
            actionMessage = "No se pudo cargar el rango manual."
        }
    }
    LaunchedEffect(selectedDayKey) {
        actionMessage = "Sincronizando cuadre..."
        val localSnapshot = withContext(Dispatchers.IO) {
            onLoadDaySnapshot(selectedDayKey)
        }
        visibleSummary = localSnapshot.summary
        visibleActorSummary = localSnapshot.actorSummary
        visibleTurnoSummary = localSnapshot.turnoSummary
        visibleActorRows = localSnapshot.actorRows
        historyEntries = localSnapshot.history
        onRefreshDaySummary(selectedDayKey) { summary, message ->
            visibleSummary = summary
            visibleActorRows = onLoadPeriodReport(FinancePeriodPreset.DAY, selectedDayKey, null, null)?.actorRows.orEmpty()
            actionMessage = if (message.contains("servidor", ignoreCase = true) && !message.contains("No se pudo", ignoreCase = true)) {
                resolveActionFeedbackMessage(ActionFeedbackKind.SERVER_REFRESH, success = true)
            } else {
                message
            }
        }
    }
    val selectedPeriod = remember(selectedPeriodName) {
        runCatching { FinancePeriodPreset.valueOf(selectedPeriodName) }.getOrDefault(FinancePeriodPreset.DAY)
    }
    val selectedPeriodOption = remember(selectedPeriod) {
        financePeriodOptions().firstOrNull { it.preset == selectedPeriod } ?: financePeriodOptions().first()
    }
    val cashValue = cashInput.toDoubleOrNull() ?: 0.0
    val turnoDiff = remember(cashValue, visibleTurnoSummary) {
        visibleTurnoSummary?.let { cashValue - it.summary.cajaDisponible }
    }

    val visual = rememberLotteryNetVisualSpec()
    val layout = resolveFinanceLayout(visual.windowMode)
    val operationSections = remember(activeSession?.role, visibleActorSummary) {
        resolveFinanceOperationSections(activeSession?.role ?: UserRole.UNKNOWN, visibleActorSummary != null)
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .then(if (shouldApplyFinanceSafeDrawingInsets()) Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()) else Modifier),
        color = visual.colors.background,
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                FinanceCompactHeader(
                    title = "Cuadre",
                    subtitle = "$bancaName · $selectedPeriodLabel",
                    onMenu = { com.lotterynet.pro.ui.common.openShellMenu(context) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CompactStatusBadge(
                        label = actionMessage?.takeIf { it.contains("Sincronizando", ignoreCase = true) }?.let { "Sincronizando" } ?: "Sincronizado",
                        tone = if (visibleSummary.alertas.isNotEmpty()) warningColor() else gainColor(),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                CompactSegmentedSelector(
                    options = financeCompactSectionOptions(),
                    selectedId = selectedFinanceSectionId,
                    onSelected = { selectedFinanceSectionId = it },
                    columns = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
                ) {
                    if (selectedFinanceSectionId == FinanceCompactSection.PERIOD.id) item {
                        FinanceHeaderCard(
                            roleLabel = activeSession?.role?.name?.lowercase(Locale.getDefault()) ?: "offline",
                            periodLabel = selectedPeriodLabel,
                            todayDayKey = dayKey,
                            selectedDayKey = selectedDayKey,
                            onSelectDay = ::applyDaySelection,
                            turnoOpen = visibleTurnoSummary != null,
                            actionOrder = resolveFinanceHeaderActionOrder(layout.prioritizeDeliveryActions),
                            actionColumns = layout.actionColumns,
                            compactHeader = layout.compactHeader,
                            selectedPeriod = selectedPeriodOption,
                            onSelectPeriod = { option ->
                                val report = onLoadPeriodReport(option.preset, null, null, null)
                                if (report != null) {
                                    selectedPeriodName = option.preset.name
                                    selectedPeriodLabel = report.range.label
                                    rangeFromDayKey = report.fromDayKey
                                    rangeToDayKey = report.toDayKey
                                    visibleSummary = report.summary
                                    visibleActorRows = report.actorRows
                                    actionMessage = "Periodo ${option.label.lowercase()} aplicado."
                                } else {
                                    actionMessage = "No se pudo cargar ${option.label.lowercase()}."
                                }
                            },
                            rangeFromDayKey = rangeFromDayKey,
                            rangeToDayKey = rangeToDayKey,
                            onSelectManualRange = ::applyManualRange,
                            onPrint = {
                                val opened = onPrint(resolveFinanceActionPeriodLabel(selectedDayKey, selectedPeriodLabel), visibleSummary, visibleActorSummary, visibleTurnoSummary, cashInput.toDoubleOrNull(), turnoDiff)
                                actionMessage = if (opened) "Impresión abierta." else "No se pudo actualizar."
                            },
                            onWhatsApp = {
                                onWhatsApp(resolveFinanceActionPeriodLabel(selectedDayKey, selectedPeriodLabel), visibleSummary, visibleActorSummary, visibleTurnoSummary, cashInput.toDoubleOrNull(), turnoDiff)
                                actionMessage = "WhatsApp abierto."
                            },
                            onShare = {
                                onShare(resolveFinanceActionPeriodLabel(selectedDayKey, selectedPeriodLabel), visibleSummary, visibleActorSummary, visibleTurnoSummary, cashInput.toDoubleOrNull(), turnoDiff)
                                actionMessage = "Compartir abierto."
                            },
                            onThermal = {
                                onThermal(resolveFinanceActionPeriodLabel(selectedDayKey, selectedPeriodLabel), visibleSummary, visibleActorSummary, visibleTurnoSummary, cashInput.toDoubleOrNull(), turnoDiff)
                                actionMessage = "Impresión térmica abierta."
                            },
                            onSave = {
                                val saved = onSave(resolveFinanceActionPeriodLabel(selectedDayKey, selectedPeriodLabel), visibleSummary, visibleActorSummary, visibleTurnoSummary, cashInput.toDoubleOrNull(), turnoDiff)
                                actionMessage = resolveActionFeedbackMessage(ActionFeedbackKind.SAVE, saved)
                            },
                            actionMessage = actionMessage,
                            anchorDayKey = selectedDayKey,
                            onSelectMonth = { month ->
                                val report = onLoadPeriodReport(
                                    FinancePeriodPreset.MONTH,
                                    month.anchorDayKey,
                                    month.fromDayKey,
                                    month.toDayKey,
                                )
                                if (report != null) {
                                    selectedPeriodName = FinancePeriodPreset.MONTH.name
                                    selectedPeriodLabel = report.range.label
                                    rangeFromDayKey = report.fromDayKey
                                    rangeToDayKey = report.toDayKey
                                    visibleSummary = report.summary
                                    visibleActorRows = report.actorRows
                                    actionMessage = "${month.label} aplicado completo."
                                } else {
                                    actionMessage = "No se pudo cargar ${month.label}."
                                }
                            },
                        )
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.SUMMARY.id) item {
                        FinancePanel {
                            OperationalListHeader(title = "Resumen del corte", meta = if (activeSession?.role == UserRole.ADMIN) "Mi banca" else "Vista actual")
                            MetricStrip(
                                items = listOf(
                                    MetricStripItem("Venta", money(visibleSummary.ventas), gainColor()),
                                    MetricStripItem("Caja final", money(visibleSummary.cajaDisponible), visual.colors.ink),
                                    MetricStripItem("Premios", money(visibleSummary.premiosPendientes), warningColor()),
                                ),
                            )
                            val visibleNet = resolveOperationalReportNet(visibleSummary)
                            FinanceLine(
                                financeResultLabel(visibleNet),
                                signedMoney(visibleNet),
                                valueColor = financeResultTone(visibleNet),
                            )
                            FinanceLine("Tickets", visibleSummary.ticketsCount.toString())
                            FinanceLine("Recargas", money(visibleSummary.recargas), valueColor = visual.colors.recharge)
                            FinanceLine("Comisión cajero", money(visibleSummary.comision), valueColor = visual.colors.loss)
                            if (visibleSummary.supervisorComision > 0.0) {
                                FinanceLine("Comisión supervisor", money(visibleSummary.supervisorComision), valueColor = visual.colors.loss)
                            }
                        }
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.SUMMARY.id && "GLOBAL" in operationSections) {
                        item {
                            FinanceSummaryCard(
                                title = "Operación global",
                                subtitle = when (activeSession?.role) {
                                    UserRole.ADMIN -> activeSession.banca ?: "Banca completa"
                                    else -> "Vista global"
                                },
                                summary = visibleSummary,
                                accent = visual.colors.finance,
                            )
                        }
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.SUMMARY.id && "MASTER_GLOBAL" in operationSections) {
                        item {
                            FinanceSummaryCard(
                                title = "Finanzas master",
                                subtitle = "Todas las bancas",
                                summary = visibleSummary,
                                accent = Color(0xFF2563EB),
                            )
                        }
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.SUMMARY.id && "SUPERVISOR_GROUP" in operationSections) {
                        item {
                            FinanceSummaryCard(
                                title = "Finanzas supervisor",
                                subtitle = "Cajeros asignados",
                                summary = visibleSummary,
                                accent = Color(0xFF0F766E),
                            )
                        }
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.DETAIL.id && ("TEAM" in operationSections || "BANKS" in operationSections)) {
                        item {
                            FinanceActorRankingCard(
                                title = if ("BANKS" in operationSections) "Ranking operativo" else "Cajeros y supervisores",
                                subtitle = if ("BANKS" in operationSections) "Todas las bancas" else "Producción del período",
                                rows = visibleActorRows,
                            )
                        }
                    }
                    val actorForUi = visibleActorSummary
                    val turnoForUi = visibleTurnoSummary
                    if (selectedFinanceSectionId == FinanceCompactSection.SUMMARY.id && "PROFILE" in operationSections && actorForUi != null && activeSession != null) {
                        item {
                            FinancePanel {
                                OperationalListHeader(title = "Perfil cajero", meta = activeSession.banca ?: "Banca")
                                CompactKeyValueRow("Usuario", actorForUi.actorDisplay)
                                CompactKeyValueRow("Rol", "Cajero")
                                CompactKeyValueRow("Caja", money(actorForUi.summary.cajaDisponible), emphasized = true, tone = visual.colors.finance)
                            }
                        }
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.SUMMARY.id && "ACTOR" in operationSections && actorForUi != null) {
                        item {
                            FinanceSummaryCard(
                                title = if (activeSession?.role == UserRole.CASHIER) "Cuadre del cajero" else "Operación del usuario",
                                subtitle = actorForUi.actorDisplay,
                                summary = actorForUi.summary,
                                accent = Color(0xFF1D4ED8),
                            )
                        }
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.CLOSE.id && turnoForUi != null) {
                        item {
                            TurnoCloseCard(
                                turnoSummary = turnoForUi,
                                cashInput = cashInput,
                                onCashInputChange = { cashInput = it },
                                turnoDiff = turnoDiff,
                                onSaveSnapshot = {
                                    historyEntries = onSaveSnapshot(selectedDayKey, visibleSummary, visibleActorSummary, turnoForUi, cashInput.toDoubleOrNull(), turnoDiff)
                                    actionMessage = resolveActionFeedbackMessage(ActionFeedbackKind.SAVE, success = true)
                                },
                                onCloseTurno = {
                                    historyEntries = onCloseTurno(selectedDayKey, visibleSummary, visibleActorSummary, turnoForUi, cashInput.toDoubleOrNull(), turnoDiff)
                                    visibleTurnoSummary = null
                                    actionMessage = "Cierre de turno guardado."
                                },
                            )
                        }
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.DETAIL.id || selectedFinanceSectionId == FinanceCompactSection.CLOSE.id) item {
                        FinancePanel {
                            SectionHeader(title = "Tickets", meta = "Estado del corte")
                            ClassificationCard(summary = visibleSummary)
                        }
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.DETAIL.id && visibleSummary.alertas.isNotEmpty()) {
                        item {
                        FinancePanel {
                            SectionHeader(title = "Alertas financieras", meta = "${visibleSummary.alertas.size} señales")
                            FinanceAlertsCard(alerts = visibleSummary.alertas)
                        }
                        }
                    }
                    if (selectedFinanceSectionId == FinanceCompactSection.CLOSE.id && historyEntries.isNotEmpty()) {
                        item {
                            FinancePanel {
                                SectionHeader(title = "Historial", meta = "${historyEntries.size} registros")
                                FinanceHistoryCard(entries = historyEntries)
                            }
                        }
                    }
                }
            }
            BottomNavBar(
                role = activeSession?.role ?: UserRole.UNKNOWN,
                active = NativeBottomTab.MENU,
                onSelected = { tab -> openBottomTab(context, activeSession?.role ?: UserRole.UNKNOWN, tab) },
            )
        }
    }
}

internal fun shouldApplyFinanceSafeDrawingInsets(): Boolean = true

@Composable
private fun FinanceCompactHeader(
    title: String,
    subtitle: String,
    onMenu: () -> Unit,
) {
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
            Text("☰", modifier = Modifier.clickable(onClick = onMenu), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
            }
            Text("⋮", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}

@Composable
private fun FinanceHeaderCard(
    roleLabel: String,
    periodLabel: String,
    todayDayKey: String,
    selectedDayKey: String,
    onSelectDay: (String) -> Unit,
    turnoOpen: Boolean,
    actionOrder: List<FinanceHeaderActionId>,
    actionColumns: Int,
    compactHeader: Boolean,
    selectedPeriod: FinancePeriodUiOption,
    onSelectPeriod: (FinancePeriodUiOption) -> Unit,
    rangeFromDayKey: String,
    rangeToDayKey: String,
    onSelectManualRange: (String, String) -> Unit,
    onPrint: () -> Unit,
    onWhatsApp: () -> Unit,
    onShare: () -> Unit,
    onThermal: () -> Unit,
    onSave: () -> Unit,
    actionMessage: String?,
    anchorDayKey: String,
    onSelectMonth: (FinanceMonthUiOption) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var exportMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val exportMenu = remember(actionOrder) { resolveFinanceExportMenuContract(actionOrder) }
    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = visual.sizes.panelContentGap + 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compactHeader) 8.dp else 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        roleLabel.replaceFirstChar { it.uppercase() },
                        color = visual.colors.ink,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = periodLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (turnoOpen) "Cuadre + turno" else "Cuadre",
                    style = if (compactHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    color = visual.colors.ink,
                )
            }
            FinanceDateSelector(
                todayDayKey = todayDayKey,
                selectedDayKey = selectedDayKey,
                onSelectDay = onSelectDay,
            )
            FinancePeriodSegmentedControl(
                selected = selectedPeriod,
                onSelect = onSelectPeriod,
            )
            FinanceManualRangeSelector(
                fromDayKey = rangeFromDayKey,
                toDayKey = rangeToDayKey,
                onSelectRange = onSelectManualRange,
            )
            FinanceMonthDropdown(
                anchorDayKey = anchorDayKey,
                onSelect = onSelectMonth,
            )
            actionMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                )
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
                    actionOrder.forEach { action ->
                        val label = financeHeaderActionLabel(action) ?: return@forEach
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (action) {
                                        FinanceHeaderActionId.WHATSAPP -> Icons.Rounded.Whatsapp
                                        FinanceHeaderActionId.SHARE -> Icons.Rounded.Share
                                        FinanceHeaderActionId.PRINT -> Icons.Rounded.Print
                                        FinanceHeaderActionId.THERMAL -> Icons.Rounded.PointOfSale
                                        FinanceHeaderActionId.SAVE -> Icons.Rounded.Download
                                        FinanceHeaderActionId.REPORTS -> Icons.Rounded.QueryStats
                                    },
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                exportMenuExpanded = false
                                when (action) {
                                    FinanceHeaderActionId.WHATSAPP -> onWhatsApp()
                                    FinanceHeaderActionId.SHARE -> onShare()
                                    FinanceHeaderActionId.PRINT -> onPrint()
                                    FinanceHeaderActionId.THERMAL -> onThermal()
                                    FinanceHeaderActionId.SAVE -> onSave()
                                    FinanceHeaderActionId.REPORTS -> Unit
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceDateSelector(
    todayDayKey: String,
    selectedDayKey: String,
    onSelectDay: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val quickOptions = remember(todayDayKey) { financeDateQuickOptions(todayDayKey) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            quickOptions.forEach { option ->
                CompactActionButton(
                    label = option.label,
                    onClick = { onSelectDay(option.dayKey) },
                    modifier = Modifier.weight(1f),
                    active = selectedDayKey == option.dayKey,
                    icon = Icons.Rounded.CalendarMonth,
                    tone = if (selectedDayKey == option.dayKey) ActionTone.Primary else ActionTone.Secondary,
                )
            }
        }
        Text(
            "Fecha: ${resolveFinanceSelectedDateLabel(selectedDayKey, todayDayKey)}",
            style = MaterialTheme.typography.labelMedium,
            color = visual.colors.ink,
        )
    }
}

@Composable
private fun FinanceCalendarGrid(
    anchorDayKey: String,
    selectedDayKey: String,
    onSelectDay: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val days = remember(anchorDayKey, selectedDayKey) {
        financeCalendarMonthDays(anchorDayKey, selectedDayKey)
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panelAlt, RoundedCornerShape(8.dp))
            .padding(7.dp),
    ) {
        listOf("D", "L", "M", "M", "J", "V", "S").chunked(7).forEach { labels ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                labels.forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = visual.colors.muted,
                    )
                }
            }
        }
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                        onClick = { day.dayKey?.let(onSelectDay) },
                        enabled = day.dayKey != null,
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            day.selected -> visual.colors.finance
                            day.inMonth -> visual.colors.panel
                            else -> Color.Transparent
                        },
                        border = BorderStroke(1.dp, if (day.selected) visual.colors.finance else visual.colors.border),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                day.dayNumber?.toString().orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (day.selected) Color.White else visual.colors.ink,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class FinanceRangePickerTarget {
    FROM,
    TO,
}

@Composable
private fun FinanceManualRangeSelector(
    fromDayKey: String,
    toDayKey: String,
    onSelectRange: (String, String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var pickerTarget by rememberSaveable { mutableStateOf<FinanceRangePickerTarget?>(null) }
    val range = remember(fromDayKey, toDayKey) { normalizeManualFinanceRange(fromDayKey, toDayKey) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CompactActionButton(
                label = "Desde ${range.fromDayKey.takeLast(2)}",
                onClick = { pickerTarget = FinanceRangePickerTarget.FROM },
                modifier = Modifier.weight(1f),
                active = pickerTarget == FinanceRangePickerTarget.FROM,
                icon = Icons.Rounded.CalendarMonth,
                tone = ActionTone.Secondary,
            )
            CompactActionButton(
                label = "Hasta ${range.toDayKey.takeLast(2)}",
                onClick = { pickerTarget = FinanceRangePickerTarget.TO },
                modifier = Modifier.weight(1f),
                active = pickerTarget == FinanceRangePickerTarget.TO,
                icon = Icons.Rounded.CalendarMonth,
                tone = ActionTone.Secondary,
            )
        }
        Text(
            "Rango manual: ${formatManualFinanceRangeLabel(range.fromDayKey, range.toDayKey)}",
            style = MaterialTheme.typography.labelMedium,
            color = visual.colors.ink,
        )
        pickerTarget?.let { target ->
            FinanceCalendarGrid(
                anchorDayKey = if (target == FinanceRangePickerTarget.FROM) range.fromDayKey else range.toDayKey,
                selectedDayKey = if (target == FinanceRangePickerTarget.FROM) range.fromDayKey else range.toDayKey,
                onSelectDay = { selected ->
                    if (target == FinanceRangePickerTarget.FROM) {
                        onSelectRange(selected, range.toDayKey)
                    } else {
                        onSelectRange(range.fromDayKey, selected)
                    }
                    pickerTarget = null
                },
            )
        }
    }
}

@Composable
private fun FinancePeriodSegmentedControl(
    selected: FinancePeriodUiOption,
    onSelect: (FinancePeriodUiOption) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val rows = remember(visual.windowMode) { financePeriodOptionRows(visual.windowMode) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    CompactActionButton(
                        label = option.label,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                        active = option.preset == selected.preset,
                        icon = Icons.Rounded.QueryStats,
                        tone = if (option.preset == selected.preset) ActionTone.Primary else option.tone,
                    )
                }
                repeat(row.size.coerceAtMost(3).let { 3 - it }.coerceAtLeast(0)) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FinanceMonthDropdown(
    anchorDayKey: String,
    onSelect: (FinanceMonthUiOption) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val dropdown = resolveFintechDropdownContract(visual.colors)
    val overflow = remember(visual.windowMode) { resolveOverflowLayoutContract(visual.windowMode) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val months = remember(anchorDayKey) { financeMonthOptions(anchorDayKey) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = dropdown.background,
            border = BorderStroke(1.dp, dropdown.border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = dropdown.foreground)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mes pasado", style = MaterialTheme.typography.labelSmall, color = dropdown.foreground.copy(alpha = 0.86f), fontWeight = FontWeight.Bold)
                    Text("Elegir mes completo", style = MaterialTheme.typography.labelLarge, color = dropdown.foreground, fontWeight = dropdown.valueWeight)
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = dropdown.foreground)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(max = overflow.dropdownMaxWidth)
                .heightIn(max = overflow.dropdownMaxHeight),
        ) {
            months.forEach { month ->
                DropdownMenuItem(
                    text = { Text(month.label, style = MaterialTheme.typography.labelLarge, color = visual.colors.ink) },
                    onClick = {
                        expanded = false
                        onSelect(month)
                    },
                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = visual.colors.finance) },
                )
            }
        }
    }
}

@Composable
private fun FinancePeriodDropdown(
    selected: FinancePeriodUiOption,
    onSelect: (FinancePeriodUiOption) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val dropdown = resolveFintechDropdownContract(visual.colors)
    val overflow = remember(visual.windowMode) { resolveOverflowLayoutContract(visual.windowMode) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = dropdown.background,
            border = BorderStroke(1.dp, dropdown.border),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.QueryStats, contentDescription = null, tint = dropdown.foreground)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Periodo del cuadre",
                        style = MaterialTheme.typography.labelSmall,
                        color = dropdown.foreground.copy(alpha = 0.86f),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = selected.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = dropdown.foreground,
                        fontWeight = dropdown.valueWeight,
                    )
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = dropdown.foreground)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(max = overflow.dropdownMaxWidth)
                .heightIn(max = overflow.dropdownMaxHeight),
        ) {
            financePeriodOptions().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = visual.colors.ink,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.QueryStats,
                            contentDescription = null,
                            tint = if (option.preset == selected.preset) Color(0xFF1D4ED8) else visual.colors.muted,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FinanceHeroGrid(summary: FinanceSummary) {
    val visual = rememberLotteryNetVisualSpec()
    val gain = gainColor()
    val neto = resolveOperationalReportNet(summary)
    val netoColor = financeResultTone(neto)
    FinancePanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resumen del corte", style = MaterialTheme.typography.labelLarge, color = visual.colors.ink)
            FinanceLine("Ventas lotería", money(summary.ventas), valueColor = gain)
            FinanceLine("Caja final", money(summary.cajaDisponible), valueColor = visual.colors.ink)
            FinanceLine("Premios pagados", money(summary.premiosPagados), valueColor = warningColor())
            if (summary.supervisorComision > 0.0) {
                FinanceLine("Comisión supervisor", money(summary.supervisorComision), valueColor = visual.colors.loss)
            }
            FinanceLine(
                financeResultLabel(neto),
                signedMoney(neto),
                valueColor = netoColor,
            )
        }
    }
}

@Composable
private fun FinanceSummaryCard(
    title: String,
    subtitle: String,
    summary: FinanceSummary,
    accent: Color,
) {
    val visual = rememberLotteryNetVisualSpec()
    FinancePanel {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = visual.colors.ink, fontWeight = FontWeight.Black)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "Tickets",
                    value = summary.ticketsCount.toString(),
                    accent = accent,
                )
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "Promedio",
                    value = money(summary.avgTicket),
                    accent = accent,
                )
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "Fuera",
                    value = summary.fueraDeFinanzaCount.toString(),
                    accent = accent,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val net = resolveOperationalReportNet(summary)
                FinanceLine("Activos", summary.activos.toString())
                FinanceLine("Ganadores", summary.ganadores.toString(), valueColor = warningColor())
                FinanceLine("Pagados", summary.pagados.toString())
                FinanceLine("Premios pendientes", money(summary.premiosPendientes), valueColor = warningColor())
                FinanceLine("Comisión cajero", money(summary.comision), valueColor = visual.colors.loss)
                if (summary.supervisorComision > 0.0) {
                    FinanceLine("Comisión supervisor", money(summary.supervisorComision), valueColor = visual.colors.loss)
                }
                FinanceLine(financeResultLabel(net), signedMoney(net), valueColor = financeResultTone(net))
                FinanceLine("Recargas", money(summary.recargas), valueColor = visual.colors.recharge)
                FinanceLine("Anulados", "${summary.anuladosCount} · ${money(summary.anuladosMonto)}")
                FinanceLine("Inválidos", "${summary.invalidosCount} · ${money(summary.invalidosMonto)}")
                FinanceLine("Borrados", "${summary.borradosCount} · ${money(summary.borradosMonto)}")
            }
        }
    }
}

@Composable
private fun FinanceActorRankingCard(
    title: String,
    subtitle: String,
    rows: List<FinanceActorPeriodRow>,
) {
    val visual = rememberLotteryNetVisualSpec()
    val sortedRows = remember(rows) {
        rows.sortedByDescending { it.summary.cajaDisponible }.take(12)
    }
    FinancePanel {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(title = title, meta = "${sortedRows.size} visibles")
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontWeight = FontWeight.Bold,
            )
            if (sortedRows.isEmpty()) {
                Text(
                    "Sin movimientos en este período.",
                    style = MaterialTheme.typography.labelMedium,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                sortedRows.forEachIndexed { index, row ->
                    FinanceActorRankingRow(position = index + 1, row = row)
                }
            }
        }
    }
}

@Composable
private fun FinanceActorRankingRow(
    position: Int,
    row: FinanceActorPeriodRow,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = visual.colors.panelAlt,
        border = BorderStroke(1.dp, visual.colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "#$position",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF2563EB),
                fontWeight = FontWeight.Black,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.actorDisplay,
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    "${row.summary.ticketsCount} tickets · ${money(row.summary.recargas)} recargas",
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    money(row.summary.ventas),
                    style = MaterialTheme.typography.labelLarge,
                    color = gainColor(),
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "Caja ${money(row.summary.cajaDisponible)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                )
                val net = resolveOperationalReportNet(row.summary)
                Text(
                    "${financeResultLabel(net)} ${signedMoney(net)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = financeResultTone(net),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TurnoCloseCard(
    turnoSummary: TurnoFinanceSummary,
    cashInput: String,
    onCashInputChange: (String) -> Unit,
    turnoDiff: Double?,
    onSaveSnapshot: () -> Unit,
    onCloseTurno: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val diffColor = when {
        turnoDiff == null -> visual.colors.ink
        turnoDiff >= 0 -> gainColor()
        else -> lossColor()
    }
    FinancePanel {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cierre de turno", style = MaterialTheme.typography.titleMedium, color = visual.colors.ink, fontWeight = FontWeight.Black)
                Text(
                    "${turnoSummary.actorDisplay} · Inicio ${formatTurnoStamp(turnoSummary.startedAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "Esperado",
                    value = money(turnoSummary.summary.cajaDisponible),
                    accent = visual.colors.ink,
                )
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "Ventas",
                    value = money(turnoSummary.summary.ventas),
                    accent = Color(0xFF166534),
                )
                SummaryPill(
                    modifier = Modifier.weight(1f),
                    label = "Tickets",
                    value = turnoSummary.summary.ticketsCount.toString(),
                    accent = Color(0xFF0F766E),
                )
            }
            OutlinedTextField(
                value = cashInput,
                onValueChange = onCashInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Efectivo real") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
            FinanceLine("Diferencia", turnoDiff?.let { signedMoney(it) } ?: "$ 0", valueColor = diffColor)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CompactActionButton(
                    label = "Guardar corte",
                    onClick = onSaveSnapshot,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Archive,
                )
                CompactActionButton(
                    label = "Cerrar turno",
                    onClick = onCloseTurno,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.EventBusy,
                    active = true,
                )
            }
        }
    }
}

@Composable
private fun FinanceHistoryCard(entries: List<FinanceHistoryEntry>) {
    val visual = rememberLotteryNetVisualSpec()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OperationalListHeader(title = "Historial reciente", meta = "${entries.size} registros")
        if (entries.isEmpty()) {
            CompactEmptyState(message = "Sin historial reciente.")
        } else {
            entries.take(6).forEach { entry ->
                val accent = if (entry.recordType.equals("close", true)) Color(0xFFB45309) else Color(0xFF475569)
                CompactRecordRow(
                    title = "${entry.targetName} · ${entry.periodLabel}",
                    subtitle = "${entry.recordType.uppercase(Locale.getDefault())} · ${formatHistoryStamp(entry.createdAtEpochMs)}",
                    meta = buildString {
                        append("Esperado ${money(entry.summary.cajaDisponible)}")
                        entry.closeCash?.let { append(" · Caja ${money(it)}") }
                        entry.closeDiff?.let { append(" · Diff ${signedMoney(it)}") }
                    },
                    badgeLabel = entry.recordType.uppercase(Locale.getDefault()),
                    badgeTone = accent,
                )
            }
        }
    }
}

@Composable
private fun ClassificationCard(summary: FinanceSummary) {
    val visual = rememberLotteryNetVisualSpec()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OperationalListHeader(title = "Clasificación de tickets", meta = "Resumen")
        FinanceLine("Validos", summary.ticketsCount.toString())
        FinanceLine("Ganadores", summary.ganadores.toString(), valueColor = warningColor())
        FinanceLine("Pagados", summary.pagados.toString(), valueColor = visual.colors.ink)
        FinanceLine("Nulos", summary.anuladosCount.toString(), valueColor = Color(0xFF64748B))
        FinanceLine("Invalidos", summary.invalidosCount.toString(), valueColor = lossColor())
        FinanceLine("Borrados", summary.borradosCount.toString(), valueColor = lossColor())
        FinanceLine("Total fuera de finanza", summary.fueraDeFinanzaCount.toString())
        FinanceLine("Monto fuera de finanza", money(summary.fueraDeFinanzaMonto))
    }
}

@Composable
private fun FinanceAlertsCard(alerts: List<FinanceAlert>) {
    val visual = rememberLotteryNetVisualSpec()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OperationalListHeader(title = "Alertas", meta = if (alerts.isEmpty()) "Sin alertas" else "${alerts.size} activas")
        if (alerts.isEmpty()) {
            CompactEmptyState(message = "Sin alertas operativas en este corte.")
        } else {
            alerts.forEach { alert ->
                AlertRow(alert = alert)
            }
        }
    }
}

@Composable
private fun AlertRow(alert: FinanceAlert) {
    val pair: Pair<Color, Color> = when (alert.tone) {
        FinanceAlertTone.DANGER -> Color(0xFFFEF2F2) to lossColor()
        FinanceAlertTone.WARNING -> Color(0xFFFFF7ED) to warningColor()
        FinanceAlertTone.NOTICE -> Color(0xFFFFFBEB) to warningColor()
    }
    val bg = pair.first
    val fg = pair.second
    CompactRecordRow(
        title = alert.label,
        subtitle = alert.text,
        meta = when (alert.tone) {
            FinanceAlertTone.DANGER -> "Critica"
            FinanceAlertTone.WARNING -> "Advertencia"
            FinanceAlertTone.NOTICE -> "Aviso"
        },
        badgeLabel = "!",
        badgeTone = fg,
        modifier = Modifier.background(bg, RoundedCornerShape(10.dp)),
    )
}

@Composable
private fun SummaryPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color,
) {
    val visual = rememberLotteryNetVisualSpec()
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            letterSpacing = 0.sp,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = visual.colors.ink,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun FinanceLine(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panelAlt, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = visual.colors.muted,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = valueColor,
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun FinancePanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompactPanel(modifier = modifier, content = { content() })
}

private fun money(value: Double): String = formatFinanceScreenMoney(value)

private fun financeSummaryHasData(summary: FinanceSummary): Boolean {
    return summary.ticketsCount > 0 ||
        summary.ventas > 0.0 ||
        summary.recargas > 0.0 ||
        summary.comision > 0.0 ||
        summary.premiosPagados > 0.0 ||
        summary.premiosPendientes > 0.0
}

private fun signedMoney(value: Double): String {
    return formatFinanceScreenSignedMoney(value)
}

@Composable
private fun financeResultTone(value: Double): Color {
    val visual = rememberLotteryNetVisualSpec()
    return when {
        value > 0.0 -> gainColor()
        value < 0.0 -> visual.colors.loss
        else -> visual.colors.ink
    }
}

private fun financeResultLabel(value: Double): String {
    return when {
        value > 0.0 -> "Beneficio"
        value < 0.0 -> "Pérdida"
        else -> "Neutro"
    }
}

private fun formatTurnoStamp(epochMs: Long): String {
    return SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date(epochMs))
}

private fun formatHistoryStamp(epochMs: Long): String {
    return SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(Date(epochMs))
}

private fun loadFinanceHistory(
    historyRepository: LocalFinanceHistoryRepository,
    session: ActiveSession?,
    dayKey: String,
): List<FinanceHistoryEntry> {
    val targetIds = buildSet {
        session?.userId?.takeIf { it.isNotBlank() }?.let(::add)
        session?.adminId?.takeIf { it.isNotBlank() }?.let(::add)
    }
    return historyRepository.getHistory().filter { entry ->
        entry.dayKey == dayKey && (
            targetIds.isEmpty() ||
                entry.targetId in targetIds ||
                entry.targetName.equals(session?.username, ignoreCase = true)
        )
    }
}

private fun saveFinanceHistoryEntry(
    historyRepository: LocalFinanceHistoryRepository,
    session: ActiveSession?,
    dayKey: String,
    summary: FinanceSummary,
    actorSummary: CashierFinanceSummary?,
    turnoSummary: TurnoFinanceSummary?,
    cashEntered: Double?,
    turnoDiff: Double?,
    recordType: String,
) {
    val targetId = turnoSummary?.actorKey
        ?: actorSummary?.actorKey
        ?: session?.userId
        ?: "unknown"
    val targetName = turnoSummary?.actorDisplay
        ?: actorSummary?.actorDisplay
        ?: session?.username
        ?: "Sin sesion"
    val scopedSummary = turnoSummary?.summary ?: actorSummary?.summary ?: summary
    val periodLabel = turnoSummary?.let { "Turno ${formatTurnoStamp(it.startedAtEpochMs)}" } ?: dayKey

    historyRepository.saveEntry(
        FinanceHistoryEntry(
            id = "$recordType-$targetId-${System.currentTimeMillis()}",
            createdAtEpochMs = System.currentTimeMillis(),
            dayKey = dayKey,
            recordType = recordType,
            scopeType = if (turnoSummary != null) "turno" else "day",
            targetId = targetId,
            targetName = targetName,
            periodLabel = periodLabel,
            summary = scopedSummary,
            closeCash = cashEntered,
            closeDiff = turnoDiff,
        ),
    )
}

internal fun buildFinanceShareText(
    bancaName: String,
    dayKey: String,
    summary: FinanceSummary,
    turnoDiff: Double?,
): String {
    return buildString {
        appendLine("Cuadre - $bancaName")
        appendLine("Periodo: $dayKey")
        appendLine()
        appendLine("Ingresos")
        appendLine("Ventas: ${financeShareMoney(summary.ventas)}")
        appendLine("Recargas: ${financeShareMoney(summary.recargas)}")
        appendLine()
        appendLine("Salidas")
        appendLine("Comisión: ${financeShareMoney(summary.comision)}")
        if (summary.supervisorComision > 0.0) {
            appendLine("Comisión supervisor: ${financeShareMoney(summary.supervisorComision)}")
        }
        appendLine("Premios pagados: ${financeShareMoney(summary.premiosPagados)}")
        appendLine("Premios pendientes: ${financeShareMoney(summary.premiosPendientes)}")
        appendLine()
        appendLine("Caja")
        appendLine("Caja disponible: ${financeShareMoney(summary.cajaDisponible)}")
        appendLine("Neto proyectado: ${financeShareMoney(summary.netoProyectado)}")
        appendLine("Tickets: ${summary.ticketsCount}")
        appendLine("Activos: ${summary.activos}")
        appendLine("Anulados/Borrados: ${summary.anuladosCount + summary.borradosCount}")
        turnoDiff?.let {
            appendLine("Diferencia turno: ${signedWholeMoney(it)}")
        }
    }.trim()
}

private fun signedWholeMoney(value: Double): String {
    val sign = if (value >= 0.0) "+" else "-"
    return sign + financeShareMoney(kotlin.math.abs(value))
}

private fun financeShareMoney(value: Double): String {
    return formatWholeMoney(value)
}
