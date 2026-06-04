package com.lotterynet.pro.ui.admin

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.calendar.LotteryClosePolicy
import com.lotterynet.pro.core.calendar.LotteryAvailabilityResolver
import com.lotterynet.pro.core.calendar.StaticHolidayCalendarRepository
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.results.PickResultIdentityResolver
import com.lotterynet.pro.core.results.normalizeResultDateKey
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.storage.AdminBlockedSalePlay
import com.lotterynet.pro.core.storage.AdminSystemModeConfig
import com.lotterynet.pro.core.storage.LocalAdminLotteryConfigRepository
import com.lotterynet.pro.core.storage.BancaBranding
import com.lotterynet.pro.core.storage.decodeAdminSystemModeConfig
import com.lotterynet.pro.core.storage.encodeAdminSystemModeConfig
import com.lotterynet.pro.core.storage.blockedSalePlayLabel
import com.lotterynet.pro.core.storage.normalizeBlockedSalePlay
import com.lotterynet.pro.core.storage.normalizeAdminSystemModeConfig
import com.lotterynet.pro.core.storage.LocalBrandingRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalThermalPrinterRepository
import com.lotterynet.pro.core.storage.LocalTrustedClockRepository
import com.lotterynet.pro.core.storage.manualDisabledLotteriesRemoteKey
import com.lotterynet.pro.core.storage.systemModeRemoteKey
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.SyncGovernor
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.BottomNavBar
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactTextInput
import com.lotterynet.pro.ui.common.CompactLoadingState
import com.lotterynet.pro.ui.common.CompactRecordRow
import com.lotterynet.pro.ui.common.CompactSegmentedSelector
import com.lotterynet.pro.ui.common.CompactSwitchRow
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.MetricStrip
import com.lotterynet.pro.ui.common.MetricStripItem
import com.lotterynet.pro.ui.common.NativeBottomTab
import com.lotterynet.pro.ui.common.LotteryLogo
import com.lotterynet.pro.ui.common.OperationalListHeader
import com.lotterynet.pro.ui.common.OperationalSettingRow
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.openBottomTab
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.navigation.startSafeNativeDestination
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import kotlin.concurrent.thread
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class AdminManualResultsDateOption(
    val label: String,
    val dateKey: String,
)

internal fun filterManualResultEditableLotteries(
    lotteries: List<LotteryCatalogItem>,
    results: List<LotteryResult>,
    selectedDate: String,
    todayDate: String,
    nowUtcMs: Long,
    hasDrawPassed: (LotteryCatalogItem, Long) -> Boolean,
): List<LotteryCatalogItem> {
    val selectedIsPast = normalizeResultDateKey(selectedDate) < normalizeResultDateKey(todayDate)
    return lotteries.filter { lottery ->
        val result = results.firstOrNull { result -> manualResultMatchesLottery(result, lottery) }
        val hasNumber = result?.let(::hasManualEditableResultNumber) == true
        val isManualOverride = result?.isManualOverride == true
        val drawPassed = selectedIsPast || hasDrawPassed(lottery, nowUtcMs)
        isManualOverride || (!hasNumber && drawPassed)
    }
}

private fun manualResultMatchesLottery(
    result: LotteryResult,
    lottery: LotteryCatalogItem,
): Boolean {
    return result.lotteryId.equals(lottery.id, ignoreCase = true) ||
        PickResultIdentityResolver.canonicalKeyForResult(result)
            .equals(PickResultIdentityResolver.canonicalKeyForLottery(lottery), ignoreCase = true) ||
        result.lotteryName.equals(lottery.name, ignoreCase = true)
}

private fun hasManualEditableResultNumber(result: LotteryResult): Boolean {
    return !result.pick4.isNullOrBlank() ||
        !result.pick3.isNullOrBlank() ||
        listOf(result.first, result.second, result.third).any { !it.isNullOrBlank() }
}

class AdminConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.ADMIN_CONFIG)) return
        val session = activeSession ?: return
        val thermalRepository = LocalThermalPrinterRepository(this)
        val brandingRepository = LocalBrandingRepository(this)
        val adminLotteryRepository = LocalAdminLotteryConfigRepository(this)
        val salesRepository = LocalSalesRepository(this)
        val ticketSync = NativeOperationalSyncCoordinator(
            NativeTicketCloudSyncCoordinator(salesRepository, NativeTicketSyncQueueRepository(this)),
        )
        val trustedClockRepository = LocalTrustedClockRepository(this)
        val catalogRepository = StaticLotteryCatalogRepository()
        val calendarRule = catalogRepository.getCalendarRule()
        val brandingRemoteStore = SupabaseMasterConfigRemoteStore()
        val holidayRepository = StaticHolidayCalendarRepository(
            dominicanLotteryIds = calendarRule.dominicanLotteryIds,
            americanLotteryIds = calendarRule.americanLotteryIds,
        )
        val availabilityResolver = LotteryAvailabilityResolver(
            trustedClockRepository = trustedClockRepository,
            holidayCalendarRepository = holidayRepository,
            calendarRule = calendarRule,
        )
        val closePolicy = LotteryClosePolicy(
            trustedClockRepository = trustedClockRepository,
            holidayCalendarRepository = holidayRepository,
        )
        val territory = normalizeTerritory(session.territory)
        val lotteries = catalogRepository.getAllLotteries()
        val ownerKey = resolveOperationalOwnerKey(session)
        val localSystemModeConfig = adminLotteryRepository.getSystemModeConfig()
        val serverSystemModeConfig = runCatching {
            brandingRemoteStore.fetchValue(systemModeRemoteKey(ownerKey))
                ?.toString()
                ?.let(::decodeAdminSystemModeConfig)
        }.getOrNull()
        val systemModeConfig = resolveInitialAdminSystemModeConfig(
            localConfig = localSystemModeConfig,
            serverConfig = serverSystemModeConfig,
        ).also(adminLotteryRepository::saveSystemModeConfig)
        val initialManualDisabledLotteryIds = runCatching {
            brandingRemoteStore.fetchValue(manualDisabledLotteriesRemoteKey(ownerKey))
                ?.toString()
                ?.let(adminLotteryRepository::cacheManualDisabledLotteryConfig)
        }.getOrNull() ?: adminLotteryRepository.getManualDisabledLotteryIds()
        LocalUsersRepository(this).touchSession(session)
        setContent {
            LotteryNetComposeTheme {
                AdminConfigRoute(
                    session = session,
                    initialPrefs = thermalRepository.getPrefs(),
                    initialBranding = brandingRepository.getBranding(),
                    lotteries = lotteries,
                    initialManualDisabledLotteryIds = initialManualDisabledLotteryIds,
                    initialCalendarDisabledLotteryIds = availabilityResolver.getRealNoDrawLotteryIds(lotteries, territory),
                    initialSystemModeConfig = systemModeConfig,
                    onBack = { finish() },
                    onSavePrefs = { thermalRepository.savePrefs(it) },
                    onApplyClassic = { thermalRepository.applyClassicPreset() },
                    onSaveBancaLogo = { brandingRepository.saveLogoUri(it) },
                    onClearBancaLogo = { brandingRepository.clearLogo() },
                    onSyncBranding = { branding, onDone ->
                        syncBrandingToServer(
                            remoteStore = brandingRemoteStore,
                            session = session,
                            branding = branding,
                            onDone = onDone,
                        )
                    },
                    onSaveSystemModeConfig = { adminLotteryRepository.saveSystemModeConfig(it) },
                    onSyncSystemModeConfig = { config, onDone ->
                        syncSystemModeConfigToServer(
                            remoteStore = brandingRemoteStore,
                            session = session,
                            config = config,
                            onDone = onDone,
                        )
                    },
                    todayDayKey = buildAdminConfigDayKey(System.currentTimeMillis()),
                    onCountLotteryTickets = { lotteryId ->
                        salesRepository.getTicketsForDayAndLottery(buildAdminConfigDayKey(System.currentTimeMillis()), lotteryId).size
                    },
                    onHasLotteryDrawPassed = { lottery ->
                        val decision = closePolicy.resolveCloseDecision(
                            lottery = lottery,
                            operationTerritory = territory,
                            manualClosedLotteryIds = emptySet(),
                            calendarClosedLotteryIds = emptySet(),
                            nowUtcMs = trustedClockRepository.getTrustedUtcMs(),
                        )
                        hasManualLotteryDrawTimePassed(
                            drawTime = decision.drawTime ?: lottery.baseDrawTime,
                            nowUtcMs = trustedClockRepository.getTrustedUtcMs(),
                            operationTerritory = territory,
                        )
                    },
                    onSetLotteryDisabled = { lotteryId, disabled, permanent ->
                        adminLotteryRepository.setLotteryDisabled(lotteryId, disabled, permanent).also {
                            syncManualDisabledLotteriesToServer(brandingRemoteStore, ownerKey, adminLotteryRepository)
                        }
                    },
                    onResolveBlockedLotteryTickets = { lotteryId, action ->
                        val dayKey = buildAdminConfigDayKey(System.currentTimeMillis())
                        val note = "Esta jugada fue pasada al siguiente día por bloqueo de lotería."
                        val count = when (action) {
                            BlockedLotteryTicketAction.VOID -> salesRepository.voidTicketsForLottery(dayKey, lotteryId, "Ticket anulado por bloqueo de lotería.")
                            BlockedLotteryTicketAction.DELETE -> salesRepository.deleteTicketsForLottery(dayKey, lotteryId)
                            BlockedLotteryTicketAction.MOVE_NEXT_DAY -> salesRepository.moveTicketsForLotteryToNextDay(dayKey, lotteryId, note)
                        }
                        thread(name = "blocked-lottery-ticket-sync") {
                            ticketSync.flushOwnerLocalSnapshot(resolveOperationalOwnerKey(session), session.banca)
                        }
                        count
                    },
                    onEnableAvailableLotteries = {
                        adminLotteryRepository.clearManualDisabledLotteryIds().also {
                            syncManualDisabledLotteriesToServer(brandingRemoteStore, ownerKey, adminLotteryRepository)
                        }
                    },
                    onOpenPrinter = { startSafeNativeDestination(this, session.role, NativeDestination.PRINTER) },
                    onOpenUsers = { startSafeNativeDestination(this, session.role, NativeDestination.USER_ACCOUNTS) },
                )
            }
        }
    }

    private fun adminManualResultsDateOptions(): List<AdminManualResultsDateOption> {
        return listOf(
            AdminManualResultsDateOption("Hoy", buildManualResultsDayKey(0)),
            AdminManualResultsDateOption("Ayer", buildManualResultsDayKey(-1)),
            AdminManualResultsDateOption("Anteayer", buildManualResultsDayKey(-2)),
        )
    }

    private fun buildManualResultsDayKey(offsetDays: Int): String {
        return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date(System.currentTimeMillis() + offsetDays * 24L * 60L * 60L * 1000L))
    }

    private fun buildAdminConfigDayKey(epochMs: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        }.format(Date(epochMs))
    }

    private fun normalizeTerritory(raw: String?): LotteryTerritory {
        return if (raw.equals("USA", ignoreCase = true) || raw.equals("US", ignoreCase = true)) {
            LotteryTerritory.USA
        } else {
            LotteryTerritory.RD
        }
    }

    private fun syncBrandingToServer(
        remoteStore: SupabaseMasterConfigRemoteStore,
        session: ActiveSession,
        branding: BancaBranding,
        onDone: (Boolean) -> Unit,
    ) {
        val ownerKey = session.adminId?.takeIf { it.isNotBlank() }
            ?: session.userId.takeIf { it.isNotBlank() }
            ?: session.banca.orEmpty().ifBlank { "default" }
        thread(name = "branding-sync") {
            val ok = runCatching {
                val payload = JSONObject().apply {
                    put("logoUri", branding.logoUri)
                    put("banca", session.banca.orEmpty())
                    put("updatedBy", session.username)
                    put("updatedAt", System.currentTimeMillis())
                }.toString()
                remoteStore.upsertJsonValue("branding:$ownerKey", payload)
            }.isSuccess
            runOnUiThread { onDone(ok) }
        }
    }

    private fun syncSystemModeConfigToServer(
        remoteStore: SupabaseMasterConfigRemoteStore,
        session: ActiveSession,
        config: AdminSystemModeConfig,
        onDone: (Boolean) -> Unit,
    ) {
        val ownerKey = session.adminId?.takeIf { it.isNotBlank() }
            ?: session.userId.takeIf { it.isNotBlank() }
            ?: session.banca.orEmpty().ifBlank { "default" }
        thread(name = "system-mode-sync") {
            val ok = runCatching {
                remoteStore.upsertJsonValue("system_modes:$ownerKey", encodeAdminSystemModeConfig(config))
            }.isSuccess
            runOnUiThread { onDone(ok) }
        }
    }

}

private fun syncManualDisabledLotteriesToServer(
    remoteStore: SupabaseMasterConfigRemoteStore,
    ownerKey: String,
    repository: LocalAdminLotteryConfigRepository,
) {
    thread(name = "manual-lottery-block-sync") {
        runCatching {
            remoteStore.upsertJsonValue(
                manualDisabledLotteriesRemoteKey(ownerKey),
                repository.exportManualDisabledLotteryConfig(),
            )
        }
    }
}

internal fun adminConfigCajaShortcutTitles(): List<String> = listOf("Impresora")

internal fun adminConfigOperationShortcutTitles(): List<String> = listOf("Cajeros")

internal fun adminConfigOperationShortcutDescriptions(): List<String> = listOf("Bloqueo y límites. Premios se ajusta en Cajeros.")

internal fun adminConfigSectionTitles(): List<String> = listOf(
    "Ajustes rápidos",
    "Operación",
    "Caja",
    "Bloqueo de lotería",
    "Control de venta",
    "Sistema",
)

internal data class AdminManualResultDateSelectorContract(
    val optionCount: Int,
    val usesSegmentedChoice: Boolean,
    val countsAsPrimaryCommand: Boolean,
    val minTouchTargetDp: Int,
)

internal fun resolveAdminManualResultDateSelectorContract(optionCount: Int): AdminManualResultDateSelectorContract {
    return AdminManualResultDateSelectorContract(
        optionCount = optionCount,
        usesSegmentedChoice = optionCount in 2..4,
        countsAsPrimaryCommand = false,
        minTouchTargetDp = 44,
    )
}

internal fun adminSystemGroupedSectionTitles(): List<String> = listOf("Operación", "Cajeros", "Servidor")

internal data class AdminSaleTypeBlockOption(
    val id: String,
    val title: String,
    val subtitle: String,
)

internal fun adminSaleTypeBlockOptions(): List<AdminSaleTypeBlockOption> = listOf(
    AdminSaleTypeBlockOption("Q", "Quiniela", "2 dígitos. Ej: 03"),
    AdminSaleTypeBlockOption("P", "Palé", "4 dígitos. Ej: 0380"),
    AdminSaleTypeBlockOption("SP", "Super Palé", "4 dígitos. Ej: 0380"),
    AdminSaleTypeBlockOption("T", "Tripleta", "6 dígitos. Ej: 038025"),
    AdminSaleTypeBlockOption("P3", "Pick 3 S", "3 dígitos. Ej: 852"),
    AdminSaleTypeBlockOption("P3BOX", "Pick 3 B", "3 dígitos. Ej: 852"),
    AdminSaleTypeBlockOption("P4", "Pick 4 S", "4 dígitos. Ej: 1475"),
    AdminSaleTypeBlockOption("P4BOX", "Pick 4 B", "4 dígitos. Ej: 1475"),
)

internal fun addBlockedSalePlay(config: AdminSystemModeConfig, playType: String, number: String): AdminSystemModeConfig {
    val play = normalizeBlockedSalePlay(playType, number) ?: return normalizeAdminSystemModeConfig(config)
    return normalizeAdminSystemModeConfig(config.copy(blockedSalePlays = config.blockedSalePlays + play))
}

internal fun removeBlockedSalePlay(config: AdminSystemModeConfig, play: AdminBlockedSalePlay): AdminSystemModeConfig {
    return normalizeAdminSystemModeConfig(config.copy(blockedSalePlays = config.blockedSalePlays - play))
}

internal fun resolveInitialAdminSystemModeConfig(
    localConfig: AdminSystemModeConfig,
    serverConfig: AdminSystemModeConfig?,
): AdminSystemModeConfig {
    return normalizeAdminSystemModeConfig(serverConfig ?: localConfig)
}

internal fun resolveAdminModeSegment(config: AdminSystemModeConfig): String {
    return when {
        config.lotteryModeEnabled && config.pickModeEnabled -> "both"
        config.pickModeEnabled -> "pick"
        else -> "lottery"
    }
}

internal fun resolveCashierDefaultModeSegment(config: AdminSystemModeConfig): String {
    return when {
        config.cashierModeEnabled && config.cashierLotteryModeEnabled && config.cashierPickModeEnabled -> "both"
        config.cashierModeEnabled && config.cashierPickModeEnabled -> "pick"
        else -> "lottery"
    }
}

internal fun applyAdminModeSegment(config: AdminSystemModeConfig, mode: String): AdminSystemModeConfig {
    return normalizeAdminSystemModeConfig(
        when (mode) {
            "pick" -> config.copy(lotteryModeEnabled = false, pickModeEnabled = true)
            "both" -> config.copy(lotteryModeEnabled = true, pickModeEnabled = true)
            else -> config.copy(lotteryModeEnabled = true, pickModeEnabled = false)
        },
    )
}

internal fun applyCashierDefaultModeSegment(config: AdminSystemModeConfig, mode: String): AdminSystemModeConfig {
    return normalizeAdminSystemModeConfig(
        when (mode) {
            "pick" -> config.copy(cashierModeEnabled = true, cashierLotteryModeEnabled = false, cashierPickModeEnabled = true)
            "both" -> config.copy(cashierModeEnabled = true, cashierLotteryModeEnabled = true, cashierPickModeEnabled = true)
            else -> config.copy(cashierModeEnabled = false, cashierLotteryModeEnabled = true, cashierPickModeEnabled = false)
        },
    )
}

internal fun filterAdminLotteryBlockOptions(
    lotteries: List<LotteryCatalogItem>,
    query: String,
    limit: Int = Int.MAX_VALUE,
    config: AdminSystemModeConfig? = null,
): List<LotteryCatalogItem> {
    val clean = query.trim().lowercase(Locale.US)
    val modeFiltered = when {
        config == null -> lotteries
        config.lotteryModeEnabled && config.pickModeEnabled -> lotteries
        config.pickModeEnabled -> lotteries.filter(::isAdminBlockPickLottery)
        else -> lotteries.filterNot(::isAdminBlockPickLottery)
    }
    val filtered = if (clean.isBlank()) {
        modeFiltered
    } else {
        modeFiltered.filter { lottery ->
            lottery.id.lowercase(Locale.US).contains(clean) ||
                lottery.name.lowercase(Locale.US).contains(clean) ||
                lottery.type.lowercase(Locale.US).contains(clean)
        }
    }
    return filtered.sortedWith(
        compareBy<LotteryCatalogItem> { parseManualLotteryClockMinutes(it.baseCloseTime) }
            .thenBy { it.name.lowercase(Locale.US) }
            .thenBy { it.id },
    ).take(limit)
}

private fun isAdminBlockPickLottery(lottery: LotteryCatalogItem): Boolean {
    val id = lottery.id.uppercase(Locale.US)
    return lottery.playCapabilities.supportsStraight ||
        lottery.playCapabilities.supportsBox ||
        lottery.type.contains("pick", ignoreCase = true) ||
        id.startsWith("US-P3-") ||
        id.startsWith("US-P4-")
}

internal fun resolveAdminLotteryBlockSelection(
    lotteries: List<LotteryCatalogItem>,
    selectedLotteryId: String,
): LotteryCatalogItem? {
    return selectedLotteryId.takeIf { it.isNotBlank() }?.let { selectedId ->
        lotteries.firstOrNull { it.id == selectedId }
    }
}

internal data class AdminSystemModeRow(
    val label: String,
    val enabled: Boolean,
    val available: Boolean = true,
)

internal fun adminSystemModeRows(
    config: AdminSystemModeConfig,
    role: UserRole,
): List<AdminSystemModeRow> {
    val normalized = normalizeAdminSystemModeConfig(config)
    return listOf(
        AdminSystemModeRow("Modo POS Lite", normalized.posLiteEnabled),
        AdminSystemModeRow("Admin: Solo Lotería", normalized.lotteryModeEnabled && !normalized.pickModeEnabled),
        AdminSystemModeRow("Admin: Solo Pick", normalized.pickModeEnabled && !normalized.lotteryModeEnabled),
        AdminSystemModeRow("Admin: Lotería + Pick", normalized.pickAndLotteryEnabled),
        AdminSystemModeRow("Cajero: Solo Lotería", !normalized.cashierModeEnabled || (normalized.cashierLotteryModeEnabled && !normalized.cashierPickModeEnabled)),
        AdminSystemModeRow("Cajero: Solo Pick", normalized.cashierModeEnabled && normalized.cashierPickModeEnabled && !normalized.cashierLotteryModeEnabled),
        AdminSystemModeRow("Cajero: Lotería + Pick", normalized.cashierModeEnabled && normalized.cashierPickAndLotteryEnabled),
    )
}

internal fun adminSystemModeSaveButtonLabel(): String = "Reintentar guardar"

internal data class SystemModeSelectionCommitMessage(
    val syncStatus: String,
    val statusMessage: String,
)

internal fun resolveSystemModeSelectionCommitMessage(serverSyncStarted: Boolean): SystemModeSelectionCommitMessage {
    return if (serverSyncStarted) {
        SystemModeSelectionCommitMessage(
            syncStatus = "Enviando",
            statusMessage = "Modo de sistema guardado y enviando al servidor...",
        )
    } else {
        SystemModeSelectionCommitMessage(
            syncStatus = "Guardado local",
            statusMessage = "Modo de sistema guardado localmente.",
        )
    }
}

internal enum class BlockedLotteryTicketAction {
    VOID,
    DELETE,
    MOVE_NEXT_DAY,
}

internal data class ManualLotteryBlockPrompt(
    val title: String,
    val body: String,
)

internal fun resolveManualLotteryBlockPrompt(
    lotteryName: String,
    ticketCount: Int,
    drawAlreadyPassed: Boolean,
    dayKey: String,
): ManualLotteryBlockPrompt {
    return if (drawAlreadyPassed) {
        ManualLotteryBlockPrompt(
            title = "$lotteryName ya pasó",
            body = "Esta lotería ya pasó. Hay $ticketCount ticket(s) de hoy ($dayKey). Puedes pasarlos al siguiente día antes de dejarla bloqueada.",
        )
    } else {
        ManualLotteryBlockPrompt(
            title = "Bloquear $lotteryName",
            body = "Esta lotería todavía no ha pasado. Hay $ticketCount ticket(s) de hoy ($dayKey). Elige si quieres pasar esos tickets al siguiente día o anularlos.",
        )
    }
}

internal fun hasManualLotteryDrawTimePassed(
    drawTime: String,
    nowUtcMs: Long,
    operationTerritory: LotteryTerritory,
): Boolean {
    val drawMinutes = parseManualLotteryClockMinutes(drawTime)
    val timeZoneId = when (operationTerritory) {
        LotteryTerritory.USA -> "America/New_York"
        LotteryTerritory.RD -> "America/Santo_Domingo"
    }
    val calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZoneId)).apply {
        timeInMillis = nowUtcMs
    }
    val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    return nowMinutes >= drawMinutes
}

private fun parseManualLotteryClockMinutes(raw: String): Int {
    val text = raw.trim().uppercase(Locale.US)
    val match = Regex("""(\d{1,2}):(\d{2})(?:\s*(AM|PM))?""").find(text) ?: return 23 * 60 + 59
    var hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    val meridiem = match.groupValues.getOrNull(3).orEmpty()
    if (meridiem == "AM" && hour == 12) hour = 0
    if (meridiem == "PM" && hour < 12) hour += 12
    return hour * 60 + minute
}

@Composable
private fun AdminConfigRoute(
    session: ActiveSession,
    initialPrefs: ThermalPrinterPrefs,
    initialBranding: BancaBranding,
    lotteries: List<LotteryCatalogItem>,
    initialManualDisabledLotteryIds: Set<String>,
    initialCalendarDisabledLotteryIds: Set<String>,
    initialSystemModeConfig: AdminSystemModeConfig,
    onBack: () -> Unit,
    onSavePrefs: (ThermalPrinterPrefs) -> Unit,
    onApplyClassic: () -> ThermalPrinterPrefs,
    onSaveBancaLogo: (String) -> BancaBranding,
    onClearBancaLogo: () -> BancaBranding,
    onSyncBranding: (BancaBranding, (Boolean) -> Unit) -> Unit,
    onSaveSystemModeConfig: (AdminSystemModeConfig) -> AdminSystemModeConfig,
    onSyncSystemModeConfig: (AdminSystemModeConfig, (Boolean) -> Unit) -> Unit,
    todayDayKey: String,
    onCountLotteryTickets: (String) -> Int,
    onHasLotteryDrawPassed: (LotteryCatalogItem) -> Boolean,
    onSetLotteryDisabled: (String, Boolean, Boolean) -> Set<String>,
    onResolveBlockedLotteryTickets: (String, BlockedLotteryTicketAction) -> Int,
    onEnableAvailableLotteries: () -> Set<String>,
    onOpenPrinter: () -> Unit,
    onOpenUsers: () -> Unit,
) {
    var prefs by remember(initialPrefs) { mutableStateOf(initialPrefs) }
    var branding by remember(initialBranding) { mutableStateOf(initialBranding) }
    var systemModeConfig by remember(initialSystemModeConfig) { mutableStateOf(initialSystemModeConfig) }
    var manualDisabledLotteryIds by remember(initialManualDisabledLotteryIds) { mutableStateOf(initialManualDisabledLotteryIds) }
    var statusMessage by rememberSaveable { mutableStateOf("Configuración operativa local para banca, usuarios y loterías.") }
    var brandingSyncStatus by rememberSaveable { mutableStateOf("Guardado local") }
    var systemSyncStatus by rememberSaveable { mutableStateOf("Guardado local") }
    var pendingBlockedLottery by remember { mutableStateOf<LotteryCatalogItem?>(null) }
    var pendingBlockedTicketCount by rememberSaveable { mutableStateOf(0) }
    var pendingBlockedLotteryPassed by rememberSaveable { mutableStateOf(false) }
    var pendingBlockedLotteryPermanent by rememberSaveable { mutableStateOf(false) }
    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            branding = onSaveBancaLogo(uri.toString())
            brandingSyncStatus = "Guardado local"
            statusMessage = "Logo de banca guardado localmente."
            onSyncBranding(branding) { ok ->
                brandingSyncStatus = if (ok) "Sincronizado" else "Pendiente"
                statusMessage = if (ok) "Logo sincronizado con el servidor." else "Logo local pendiente de sync."
            }
            Toast.makeText(context, "Logo guardado", Toast.LENGTH_SHORT).show()
        }
    }

    pendingBlockedLottery?.let { lottery ->
        val prompt = resolveManualLotteryBlockPrompt(
            lotteryName = lottery.name,
            ticketCount = pendingBlockedTicketCount,
            drawAlreadyPassed = pendingBlockedLotteryPassed,
            dayKey = todayDayKey,
        )
        val durationLabel = if (pendingBlockedLotteryPermanent) "siempre" else "hasta mañana"
        AlertDialog(
            onDismissRequest = { pendingBlockedLottery = null },
            title = { Text(prompt.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        prompt.body,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Admin, elige duración del bloqueo: $durationLabel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pendingBlockedLotteryPermanent = false }) {
                            Text("Hasta mañana")
                        }
                        TextButton(onClick = { pendingBlockedLotteryPermanent = true }) {
                            Text("Siempre")
                        }
                    }
                    TextButton(
                        onClick = {
                            manualDisabledLotteryIds = onSetLotteryDisabled(lottery.id, true, pendingBlockedLotteryPermanent)
                            statusMessage = "${lottery.name} bloqueada $durationLabel."
                            pendingBlockedLottery = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Bloquear sin tocar tickets") }
                    TextButton(
                        onClick = {
                            manualDisabledLotteryIds = onSetLotteryDisabled(lottery.id, true, pendingBlockedLotteryPermanent)
                            val count = onResolveBlockedLotteryTickets(lottery.id, BlockedLotteryTicketAction.VOID)
                            statusMessage = "${lottery.name} bloqueada $durationLabel. $count ticket(s) anulados."
                            pendingBlockedLottery = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Anular tickets") }
                    TextButton(
                        onClick = {
                            manualDisabledLotteryIds = onSetLotteryDisabled(lottery.id, true, pendingBlockedLotteryPermanent)
                            val count = onResolveBlockedLotteryTickets(lottery.id, BlockedLotteryTicketAction.DELETE)
                            statusMessage = "${lottery.name} bloqueada $durationLabel. $count ticket(s) borrados."
                            pendingBlockedLottery = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Borrar tickets") }
                    TextButton(
                        onClick = {
                            manualDisabledLotteryIds = onSetLotteryDisabled(lottery.id, true, pendingBlockedLotteryPermanent)
                            val count = onResolveBlockedLotteryTickets(lottery.id, BlockedLotteryTicketAction.MOVE_NEXT_DAY)
                            statusMessage = "${lottery.name} bloqueada $durationLabel. $count ticket(s) pasados al siguiente día."
                            pendingBlockedLottery = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pasar al siguiente día") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingBlockedLottery = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

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
                    title = "Configuración",
                    subtitle = "${session.banca ?: "LotteryNet"} · ${session.username}",
                    onBack = onBack,
                    badgeLabel = "Admin",
                    badgeTone = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                CompactPanel {
                    OperationalListHeader(title = "Ajustes rápidos", meta = "Orden operativo")
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                    MetricStrip(
                        items = listOf(
                            MetricStripItem("Loterías", lotteries.size.toString(), visual.colors.ink),
                            MetricStripItem("Manual", manualDisabledLotteryIds.size.toString(), if (manualDisabledLotteryIds.isNotEmpty()) MaterialTheme.colorScheme.error else visual.colors.neutral),
                            MetricStripItem("Calendario", initialCalendarDisabledLotteryIds.size.toString(), visual.colors.neutral),
                        ),
                    )
                }
            }
            item {
                CompactPanel {
                    OperationalListHeader(title = "Operación", meta = "Lo que afecta venta")
                    ConfigShortcut("Cajeros", adminConfigOperationShortcutDescriptions().first(), Icons.Rounded.ManageAccounts, onOpenUsers)
                    ConfigShortcut("Loterías", "Bloquear o habilitar sorteos abajo.", Icons.Rounded.LockOpen, null)
                }
            }
            item {
                CompactPanel {
                    OperationalListHeader(title = "Caja", meta = "Salidas y tickets")
                    BancaLogoSetting(
                        bancaName = session.banca ?: session.username,
                        branding = branding,
                        onSelectLogo = { logoPicker.launch(arrayOf("image/*")) },
                        onClearLogo = {
                            branding = onClearBancaLogo()
                            brandingSyncStatus = "Guardado local"
                            statusMessage = "Logo de banca quitado localmente."
                            onSyncBranding(branding) { ok ->
                                brandingSyncStatus = if (ok) "Sincronizado" else "Pendiente"
                                statusMessage = if (ok) "Cambio de logo sincronizado." else "Cambio local pendiente de sync."
                            }
                            Toast.makeText(context, "Logo quitado", Toast.LENGTH_SHORT).show()
                        },
                    )
                    CompactStatusBadge(brandingSyncStatus, tone = visual.colors.tickets)
                    adminConfigCajaShortcutTitles().forEach { title ->
                        ConfigShortcut(title, "Bluetooth y ajustes.", Icons.Rounded.Print, onOpenPrinter)
                    }
                }
            }
            item {
                LotteryBlockControlSection(
                    lotteries = lotteries,
                    systemModeConfig = systemModeConfig,
                    manualDisabledLotteryIds = manualDisabledLotteryIds,
                    calendarDisabledLotteryIds = initialCalendarDisabledLotteryIds,
                    onEnableAvailableLotteries = {
                        manualDisabledLotteryIds = onEnableAvailableLotteries()
                        statusMessage = "Se habilitaron las loterías con sorteo disponible hoy."
                        Toast.makeText(context, "Loterías habilitadas", Toast.LENGTH_SHORT).show()
                    },
                    onToggleManualDisabled = { lottery, disabled ->
                        if (disabled) {
                            val affected = onCountLotteryTickets(lottery.id)
                            pendingBlockedTicketCount = affected
                            pendingBlockedLotteryPassed = onHasLotteryDrawPassed(lottery)
                            pendingBlockedLotteryPermanent = false
                            pendingBlockedLottery = lottery
                        } else {
                            manualDisabledLotteryIds = onSetLotteryDisabled(lottery.id, false, false)
                            statusMessage = "${lottery.name} volvió a estar disponible."
                            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
            item {
                CompactPanel {
                    OperationalListHeader(title = "Control de venta", meta = "Aplica a todos")
                    SaleTypeBlockControlSection(
                        config = systemModeConfig,
                        onChange = { next ->
                            val saved = onSaveSystemModeConfig(next)
                            systemModeConfig = saved
                            systemSyncStatus = "Enviando"
                            statusMessage = "Guardando bloqueo de jugadas..."
                            onSyncSystemModeConfig(saved) { ok ->
                                systemSyncStatus = if (ok) "Sincronizado" else "Pendiente"
                                statusMessage = if (ok) "Bloqueo de jugadas sincronizado." else "Bloqueo local pendiente de sync."
                            }
                        },
                    )
                }
            }
            item {
                CompactPanel {
                    OperationalListHeader(title = "Sistema", meta = "Modo y servidor")
                    SystemModeConfigSection(
                        config = systemModeConfig,
                        role = session.role,
                        syncStatus = systemSyncStatus,
                        onChange = { next ->
                            val saved = onSaveSystemModeConfig(next)
                            systemModeConfig = saved
                            val commit = resolveSystemModeSelectionCommitMessage(serverSyncStarted = true)
                            systemSyncStatus = commit.syncStatus
                            statusMessage = commit.statusMessage
                            onSyncSystemModeConfig(saved) { ok ->
                                systemSyncStatus = if (ok) "Sincronizado" else "Pendiente"
                                statusMessage = if (ok) "Modo de sistema sincronizado." else "Modo local pendiente de sync."
                            }
                        },
                        onSaveServer = {
                            systemSyncStatus = "Enviando"
                            statusMessage = "Guardando modo de sistema en servidor..."
                            onSyncSystemModeConfig(systemModeConfig) { ok ->
                                systemSyncStatus = if (ok) "Sincronizado" else "Pendiente"
                                statusMessage = if (ok) "Modo de sistema sincronizado." else "Modo local pendiente de sync."
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
private fun BancaLogoSetting(
    bancaName: String,
    branding: BancaBranding,
    onSelectLogo: () -> Unit,
    onClearLogo: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    val logoBitmap = remember(branding.logoUri) {
        branding.logoUri.takeIf { it.isNotBlank() }?.let { rawUri ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(rawUri))?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = visual.colors.panelAlt,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(58.dp),
                shape = RoundedCornerShape(16.dp),
                color = visual.colors.tickets.copy(alpha = 0.12f),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                if (logoBitmap != null) {
                    Image(
                        bitmap = logoBitmap.asImageBitmap(),
                        contentDescription = "Logo actual",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = bancaName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = visual.colors.tickets,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Logo ticket oficial",
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                )
                Text(
                    text = if (branding.logoUri.isBlank()) {
                        "Sin logo: se usará el nombre de la banca."
                    } else {
                        "Activo: reemplaza el nombre grande en el ticket."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactActionButton(
                        label = if (branding.logoUri.isBlank()) "Agregar logo" else "Cambiar",
                        onClick = onSelectLogo,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Save,
                        tone = ActionTone.Primary,
                    )
                    CompactActionButton(
                        label = "Quitar",
                        onClick = onClearLogo,
                        modifier = Modifier.weight(1f),
                        enabled = branding.logoUri.isNotBlank(),
                        tone = ActionTone.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigShortcut(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)?,
) {
    val visual = rememberLotteryNetVisualSpec()
    OperationalSettingRow(
        title = title,
        subtitle = subtitle,
        meta = if (onClick != null) "Abrir" else "Abajo",
        icon = icon,
        tone = when (title) {
            "Cajeros" -> visual.colors.admin
            "Usuarios" -> visual.colors.admin
            "Límites" -> visual.colors.finance
            "Loterías" -> visual.colors.sale
            "Impresora" -> visual.colors.printer
            "Premios" -> visual.colors.results
            else -> visual.colors.admin
        },
        onClick = onClick,
    )
}

@Composable
private fun SaleTypeBlockControlSection(
    config: AdminSystemModeConfig,
    onChange: (AdminSystemModeConfig) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var selectedType by rememberSaveable { mutableStateOf("Q") }
    var numberInput by rememberSaveable { mutableStateOf("") }
    val selectedOption = adminSaleTypeBlockOptions().firstOrNull { it.id == selectedType } ?: adminSaleTypeBlockOptions().first()
    val blocked = config.blockedSalePlays
    val candidate = remember(selectedType, numberInput) { normalizeBlockedSalePlay(selectedType, numberInput) }
    Text(
        "Bloquea una jugada exacta para todos. Ejemplo: Quiniela 03 bloquea solo 03; 04 sigue disponible.",
        style = MaterialTheme.typography.bodySmall,
        color = visual.colors.muted,
    )
    MetricStrip(
        items = listOf(
            MetricStripItem("Bloq.", blocked.size.toString(), if (blocked.isNotEmpty()) MaterialTheme.colorScheme.error else visual.colors.neutral),
            MetricStripItem("Estado", if (blocked.isEmpty()) "Libre" else "Activo", if (blocked.isEmpty()) visual.colors.gain else MaterialTheme.colorScheme.error),
        ),
    )
    CompactSegmentedSelector(
        options = adminSaleTypeBlockOptions().map { QuickFilterChip(it.id, it.title) },
        selectedId = selectedType,
        onSelected = {
            selectedType = it
            numberInput = ""
        },
        columns = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    CompactTextInput(
        label = selectedOption.title,
        value = numberInput,
        onValueChange = { numberInput = it.filter(Char::isDigit).take(6) },
        placeholder = selectedOption.subtitle,
        keyboardType = KeyboardType.Number,
    )
    CompactActionButton(
        label = "Bloquear jugada",
        onClick = {
            candidate?.let {
                onChange(addBlockedSalePlay(config, it.playType, it.number))
                numberInput = ""
            }
        },
        enabled = candidate != null,
        modifier = Modifier.fillMaxWidth(),
        icon = Icons.Rounded.Lock,
        tone = ActionTone.Danger,
    )
    if (blocked.isEmpty()) {
        CompactStatusBadge("Sin jugadas bloqueadas", tone = visual.colors.gain)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocked.sortedWith(compareBy<AdminBlockedSalePlay> { it.playType }.thenBy { it.number }).forEach { play ->
                OperationalSettingRow(
                    title = blockedSalePlayLabel(play),
                    subtitle = "Bloqueada para todos los cajeros y admin.",
                    meta = "Quitar",
                    icon = Icons.Rounded.Lock,
                    tone = MaterialTheme.colorScheme.error,
                    onClick = { onChange(removeBlockedSalePlay(config, play)) },
                )
            }
        }
    }
}

@Composable
private fun SystemModeConfigSection(
    config: AdminSystemModeConfig,
    role: UserRole,
    syncStatus: String,
    onChange: (AdminSystemModeConfig) -> Unit,
    onSaveServer: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val modeOptions = listOf(
        QuickFilterChip("lottery", "Lotería"),
        QuickFilterChip("pick", "Pick"),
        QuickFilterChip("both", "Lotería + Pick"),
    )
    Text(
        "Activa funciones por banca. Cajero usa Solo Lotería por defecto; puedes cambiarlo a Solo Pick o Lotería + Pick.",
        style = MaterialTheme.typography.bodySmall,
        color = visual.colors.ink,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    )
    if (syncStatus.equals("Enviando", ignoreCase = true)) {
        CompactLoadingState(label = "Guardando modo de sistema...")
    } else {
        CompactStatusBadge(syncStatus, tone = visual.colors.admin)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OperationalListHeader(title = "Operación", meta = "Modo disponible para admin")
        CompactSwitchRow(
            title = "Modo POS Lite",
            subtitle = "Compacta pantallas operativas en equipos pequeños.",
            checked = config.posLiteEnabled,
            onCheckedChange = { onChange(normalizeAdminSystemModeConfig(config.copy(posLiteEnabled = it))) },
            tone = ActionTone.Primary,
        )
        CompactSegmentedSelector(
            options = modeOptions,
            selectedId = resolveAdminModeSegment(config),
            onSelected = { onChange(applyAdminModeSegment(config, it)) },
            columns = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        OperationalListHeader(title = "Cajeros", meta = "Modo por defecto al entrar")
        CompactSegmentedSelector(
            options = modeOptions,
            selectedId = resolveCashierDefaultModeSegment(config),
            onSelected = { onChange(applyCashierDefaultModeSegment(config, it)) },
            columns = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        OperationalListHeader(title = "Servidor", meta = "Auto al tocar modo")
        CompactActionButton(
            label = adminSystemModeSaveButtonLabel(),
            onClick = onSaveServer,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Save,
            tone = ActionTone.Primary,
        )
    }
}

@Composable
private fun LotteryBlockControlSection(
    lotteries: List<LotteryCatalogItem>,
    systemModeConfig: AdminSystemModeConfig,
    manualDisabledLotteryIds: Set<String>,
    calendarDisabledLotteryIds: Set<String>,
    onEnableAvailableLotteries: () -> Unit,
    onToggleManualDisabled: (LotteryCatalogItem, Boolean) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var selectedLotteryId by rememberSaveable { mutableStateOf("") }
    val options = remember(lotteries, systemModeConfig) {
        filterAdminLotteryBlockOptions(lotteries, query = "", config = systemModeConfig)
    }
    val selectedLottery = remember(options, selectedLotteryId) {
        resolveAdminLotteryBlockSelection(options, selectedLotteryId)
    }
    val openCount = options.count { it.id !in manualDisabledLotteryIds && it.id !in calendarDisabledLotteryIds }

    CompactPanel {
        OperationalListHeader(title = "Bloqueo de lotería", meta = "Buscar y cambiar estado")
        MetricStrip(
            items = listOf(
                MetricStripItem("Abiertas", openCount.toString(), visual.colors.gain),
                MetricStripItem("Bloq.", manualDisabledLotteryIds.size.toString(), if (manualDisabledLotteryIds.isNotEmpty()) MaterialTheme.colorScheme.error else visual.colors.neutral),
                MetricStripItem("Calendario", calendarDisabledLotteryIds.size.toString(), visual.colors.neutral),
            ),
        )
        CompactActionButton(
            label = "Habilitar disponibles hoy",
            onClick = onEnableAvailableLotteries,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.LockOpen,
            tone = ActionTone.Success,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            CompactActionButton(
                label = selectedLottery?.name ?: "Elegir lotería",
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Tune,
                tone = ActionTone.Primary,
            )
            DropdownMenu(
                expanded = expanded && options.isNotEmpty(),
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.92f),
            ) {
                options.forEach { lottery ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(lottery.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                            "ID ${lottery.id} · ${lottery.type} · ${lottery.baseDrawTime}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = visual.colors.muted,
                                )
                            }
                        },
                        onClick = {
                            selectedLotteryId = lottery.id
                            expanded = false
                        },
                    )
                }
            }
        }
        selectedLottery?.let { lottery ->
            LotteryAvailabilityRow(
                lottery = lottery,
                isCalendarDisabled = lottery.id in calendarDisabledLotteryIds,
                isManualDisabled = lottery.id in manualDisabledLotteryIds,
                onToggleManualDisabled = { disabled -> onToggleManualDisabled(lottery, disabled) },
            )
            CompactActionButton(
                label = "Limpiar selección",
                onClick = {
                    selectedLotteryId = ""
                    expanded = false
                },
                modifier = Modifier.fillMaxWidth(),
                tone = ActionTone.Secondary,
            )
        } ?: Text(
            text = "Elige una lotería del listado para bloquear o habilitar.",
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.ink,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        if (manualDisabledLotteryIds.isNotEmpty()) {
            Text(
                "Bloqueadas: " + lotteries
                    .filter { lottery -> options.any { it.id == lottery.id } }
                    .filter { it.id in manualDisabledLotteryIds }
                    .joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@Composable
private fun LotteryAvailabilityRow(
    lottery: LotteryCatalogItem,
    isCalendarDisabled: Boolean,
    isManualDisabled: Boolean,
    onToggleManualDisabled: (Boolean) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val stateLabel = when {
        isCalendarDisabled -> "Sin sorteo hoy"
        isManualDisabled -> "Bloqueada por banca"
        else -> "Activa"
    }
    val stateTone = if (isCalendarDisabled || isManualDisabled) {
        MaterialTheme.colorScheme.error
    } else {
        visual.colors.gain
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panelAlt, RoundedCornerShape(visual.sizes.panelRadius))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LotteryLogo(
            assetPath = lottery.logoAssetPath,
            fallback = lottery.name,
            modifier = Modifier.size(42.dp),
            tintColor = visual.colors.panel,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                lottery.name,
                style = MaterialTheme.typography.titleSmall,
                color = visual.colors.ink,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                "${lottery.type} · sorteo ${lottery.baseDrawTime}",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                stateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = stateTone,
                fontFamily = FontFamily.Monospace,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
        if (!isCalendarDisabled) {
            CompactActionButton(
                label = if (isManualDisabled) "Habilitar" else "Bloquear",
                onClick = { onToggleManualDisabled(!isManualDisabled) },
                icon = if (isManualDisabled) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                tone = if (isManualDisabled) ActionTone.Success else ActionTone.Danger,
            )
        }
    }
}

@Composable
private fun ToggleLine(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun scaleLabel(scale: String): String {
    return when (scale) {
        "large" -> "grande"
        "compact" -> "pequeño"
        else -> "medio"
    }
}
