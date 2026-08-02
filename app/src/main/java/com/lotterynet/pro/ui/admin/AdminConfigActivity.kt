package com.lotterynet.pro.ui.admin

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
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
import com.lotterynet.pro.core.sync.NativeTicketRemoteStore
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.SyncGovernor
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKeys
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
import com.lotterynet.pro.ui.common.DangerConfirmSheet
import com.lotterynet.pro.ui.common.OperationalListHeader
import com.lotterynet.pro.ui.common.OperationalModalSheet
import com.lotterynet.pro.ui.common.OperationalSettingRow
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.sales.formatLotteryClock12
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
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
        val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        val ticketRemoteStore = NativeTicketRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
            bearerTokenRefresher = { sessionTokenProvider.forceFreshAccessToken() },
        )
        val ticketSync = NativeOperationalSyncCoordinator(
            NativeTicketCloudSyncCoordinator(
                salesRepository,
                NativeTicketSyncQueueRepository(this),
                remoteStore = ticketRemoteStore,
            ),
            remoteStampStore = ticketRemoteStore,
        )
        val trustedClockRepository = LocalTrustedClockRepository(this)
        val catalogRepository = StaticLotteryCatalogRepository()
        val calendarRule = catalogRepository.getCalendarRule()
        val brandingRemoteStore = SupabaseMasterConfigRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
        )
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
        val ownerKeys = resolveOperationalOwnerKeys(session).ifEmpty { listOf(ownerKey) }
        val localSystemModeConfig = adminLotteryRepository.getSystemModeConfig()
        val serverSystemModeConfig = firstAdminConfigRemoteValue(ownerKeys) { key ->
            brandingRemoteStore.fetchValue(systemModeRemoteKey(key))
        }?.toString()?.let(::decodeAdminSystemModeConfig)
        val systemModeConfig = resolveInitialAdminSystemModeConfig(
            localConfig = localSystemModeConfig,
            serverConfig = serverSystemModeConfig,
        ).also(adminLotteryRepository::saveSystemModeConfig)
        val initialManualDisabledLotteryIds = firstAdminConfigRemoteValue(ownerKeys) { key ->
            brandingRemoteStore.fetchValue(manualDisabledLotteriesRemoteKey(key))
        }?.toString()?.let(adminLotteryRepository::cacheManualDisabledLotteryConfig)
            ?: adminLotteryRepository.getManualDisabledLotteryIds()
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
                            ownerKeys = ownerKeys,
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
                    onDisableAllLotteries = { visibleLotteries ->
                        visibleLotteries.fold(emptySet<String>()) { _, lottery ->
                            adminLotteryRepository.setLotteryDisabled(lottery.id, true, permanent = false)
                        }.also {
                            syncManualDisabledLotteriesToServer(brandingRemoteStore, ownerKey, adminLotteryRepository)
                        }
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
        ownerKeys: List<String>,
        config: AdminSystemModeConfig,
        onDone: (Boolean) -> Unit,
    ) {
        val syncKeys = resolveAdminSystemModeSyncKeys(session, ownerKeys)
        thread(name = "system-mode-sync") {
            val ok = runCatching {
                val payload = encodeAdminSystemModeConfig(config)
                syncKeys.forEach { ownerKey ->
                    remoteStore.upsertJsonValue(systemModeRemoteKey(ownerKey), payload)
                }
            }.isSuccess && syncKeys.isNotEmpty()
            runOnUiThread { onDone(ok) }
        }
    }

}

internal fun resolveAdminSystemModeSyncKeys(session: ActiveSession, ownerKeys: List<String>): List<String> {
    val legacyOwnerKey = session.adminId?.takeIf { it.isNotBlank() }
        ?: session.userId.takeIf { it.isNotBlank() }
        ?: session.banca.orEmpty().ifBlank { "default" }
    return (ownerKeys + legacyOwnerKey)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
}

private fun firstAdminConfigRemoteValue(ownerKeys: List<String>, fetch: (String) -> Any?): Any? {
    ownerKeys.forEach { ownerKey ->
        val value = runCatching { fetch(ownerKey) }.getOrNull()
        if (value != null) return value
    }
    return null
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

internal fun adminConfigAreaActionText(area: String): String = when (area) {
    "sale" -> "Activa modos de venta y POS. Afecta la experiencia de admin y cajeros."
    "blocks" -> "Bloquea loterías o jugadas. No borra límites ni tickets existentes."
    "cash" -> "Organiza cajeros, logo, impresora y comprobantes."
    "system" -> "Revisa sincronización y estado remoto. No modifica ventas por sí solo."
    else -> "Selecciona una categoría para ver y guardar sus opciones."
}

internal fun adminLotteryBlockActionLabels(): List<String> = listOf(
    "Bloquear una lotería",
    "Bloquear todas las loterías",
)

internal fun resolveAdminLotteryScheduleText(lottery: LotteryCatalogItem): String {
    return "${lottery.type} · sorteo ${formatLotteryClock12(lottery.baseDrawTime)} · cierra ${formatLotteryClock12(lottery.baseCloseTime)}"
}

internal fun adminConfigSectionTitles(): List<String> = listOf(
    "Centro de ajustes",
    "Venta y POS",
    "Loterías y jugadas",
    "Caja y tickets",
    "Servidor y sincronización",
)

private enum class AdminConfigArea(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    SALE("sale", "Venta y POS", "Modos de venta, jugadas y pantalla POS."),
    BLOCKS("blocks", "Loterías y jugadas", "Bloqueos, calendario y tipos de jugada."),
    CASH("cash", "Caja y tickets", "Cajeros, logo, impresora y comprobantes."),
    SYSTEM("system", "Servidor y sincronización", "Estado remoto, sincronización y sistema."),
}

private const val ALL_ADMIN_CONFIG_GROUP = "ALL"

private fun adminConfigGroupFilterOptions(): List<QuickFilterChip> = listOf(
    QuickFilterChip(ALL_ADMIN_CONFIG_GROUP, "Todas"),
    QuickFilterChip("OPERACIÓN", "Operación"),
    QuickFilterChip("CAJA", "Caja"),
    QuickFilterChip("SISTEMA", "Sistema"),
)

private fun resolveAdminConfigArea(areaId: String): AdminConfigArea {
    return AdminConfigArea.values().firstOrNull { it.id == areaId } ?: AdminConfigArea.SALE
}

internal fun adminConfigSystemHubCardTitles(): List<String> = AdminConfigArea.values().map { it.title }

internal fun adminConfigDestinationIds(): List<String> = AdminConfigArea.values().map { it.id }

internal fun adminConfigAreaGroup(areaId: String): String = when (resolveAdminConfigArea(areaId)) {
    AdminConfigArea.SALE, AdminConfigArea.BLOCKS -> "OPERACIÓN"
    AdminConfigArea.CASH -> "CAJA"
    AdminConfigArea.SYSTEM -> "SISTEMA"
}

internal fun filterAdminConfigAreaTitles(query: String): List<String> {
    val normalized = query.trim().lowercase()
    val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    return AdminConfigArea.values()
        .filter { area ->
            tokens.isEmpty() || tokens.all { token ->
                listOf(area.title, area.subtitle).any { text ->
                    val normalizedText = text.lowercase()
                    val words = normalizedText
                        .split(Regex("[^\\p{L}\\p{N}]+"))
                        .filter { it.isNotBlank() }
                    words.any { it == token } || (token.length >= 4 && normalizedText.contains(token))
                }
            }
        }
        .map { it.title }
}

internal data class AdminConfigInteractionContract(
    val usesSummaryCards: Boolean,
    val usesSwitchForBooleanSetting: Boolean,
    val usesSegmentedChoicesForModes: Boolean,
    val usesBottomSheetForSecondarySelection: Boolean,
)

internal fun adminConfigInteractionContract(): AdminConfigInteractionContract {
    return AdminConfigInteractionContract(
        usesSummaryCards = true,
        usesSwitchForBooleanSetting = true,
        usesSegmentedChoicesForModes = true,
        usesBottomSheetForSecondarySelection = true,
    )
}

internal data class AdminSettingsNavigationContract(
    val usesOverviewAndDetail: Boolean,
    val systemBackReturnsToOverview: Boolean,
    val groupsDestinations: Boolean,
    val preservesBusinessCallbacks: Boolean,
)

internal fun adminSettingsNavigationContract(): AdminSettingsNavigationContract {
    return AdminSettingsNavigationContract(
        usesOverviewAndDetail = true,
        systemBackReturnsToOverview = true,
        groupsDestinations = true,
        preservesBusinessCallbacks = true,
    )
}

internal data class AdminBlockControlLayoutContract(
    val stacksDestructiveActions: Boolean,
    val showsSelectedDuration: Boolean,
    val keepsLotteryActionBelowIdentity: Boolean,
)

internal fun adminBlockControlLayoutContract(): AdminBlockControlLayoutContract {
    return AdminBlockControlLayoutContract(
        stacksDestructiveActions = true,
        showsSelectedDuration = true,
        keepsLotteryActionBelowIdentity = true,
    )
}

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

internal fun adminSystemGroupedSectionTitles(): List<String> = listOf("Pantalla", "Admin", "Cajeros", "Sync y servidor")

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

internal fun adminConfigModeLabel(mode: String): String {
    return when (mode) {
        "pick" -> "Solo Pick"
        "both" -> "Lotería + Pick"
        else -> "Solo Lotería"
    }
}

internal fun adminConfigModeShortLabel(mode: String): String {
    return when (mode) {
        "pick" -> "Pick"
        "both" -> "L+P"
        else -> "Lot."
    }
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

private data class PendingSystemModeChange(
    val title: String,
    val message: String,
    val nextConfig: AdminSystemModeConfig,
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
    onDisableAllLotteries: (List<LotteryCatalogItem>) -> Set<String>,
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
    var selectedConfigAreaId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedConfigGroupName by rememberSaveable { mutableStateOf(ALL_ADMIN_CONFIG_GROUP) }
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

    BackHandler(enabled = selectedConfigAreaId != null) {
        selectedConfigAreaId = null
    }

    pendingBlockedLottery?.let { lottery ->
        val prompt = resolveManualLotteryBlockPrompt(
            lotteryName = lottery.name,
            ticketCount = pendingBlockedTicketCount,
            drawAlreadyPassed = pendingBlockedLotteryPassed,
            dayKey = todayDayKey,
        )
        val durationLabel = if (pendingBlockedLotteryPermanent) "siempre" else "hasta mañana"
        OperationalModalSheet(
            title = prompt.title,
            subtitle = "El bloqueo afecta la venta; los tickets solo cambian si eliges una acción explícita.",
            onDismiss = { pendingBlockedLottery = null },
        ) {
            Text(
                prompt.body,
                style = MaterialTheme.typography.bodyMedium,
                color = visual.colors.ink,
            )
            OperationalListHeader(title = "Duración", meta = durationLabel.replaceFirstChar(Char::uppercase))
            CompactSegmentedSelector(
                options = listOf(
                    QuickFilterChip("day", "Hasta mañana"),
                    QuickFilterChip("permanent", "Siempre"),
                ),
                selectedId = if (pendingBlockedLotteryPermanent) "permanent" else "day",
                onSelected = { pendingBlockedLotteryPermanent = it == "permanent" },
                columns = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OperationalListHeader(title = "Acción sobre tickets", meta = "$pendingBlockedTicketCount encontrados")
            CompactActionButton(
                label = "Solo bloquear la lotería",
                onClick = {
                    manualDisabledLotteryIds = onSetLotteryDisabled(lottery.id, true, pendingBlockedLotteryPermanent)
                    statusMessage = "${lottery.name} bloqueada $durationLabel."
                    pendingBlockedLottery = null
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Lock,
                tone = ActionTone.Warning,
            )
            CompactActionButton(
                label = "Bloquear y anular tickets",
                onClick = {
                    manualDisabledLotteryIds = onSetLotteryDisabled(lottery.id, true, pendingBlockedLotteryPermanent)
                    val count = onResolveBlockedLotteryTickets(lottery.id, BlockedLotteryTicketAction.VOID)
                    statusMessage = "${lottery.name} bloqueada $durationLabel. $count ticket(s) anulados."
                    pendingBlockedLottery = null
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Lock,
                tone = ActionTone.Warning,
            )
            CompactActionButton(
                label = "Bloquear y borrar tickets",
                onClick = {
                    manualDisabledLotteryIds = onSetLotteryDisabled(lottery.id, true, pendingBlockedLotteryPermanent)
                    val count = onResolveBlockedLotteryTickets(lottery.id, BlockedLotteryTicketAction.DELETE)
                    statusMessage = "${lottery.name} bloqueada $durationLabel. $count ticket(s) borrados."
                    pendingBlockedLottery = null
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Lock,
                tone = ActionTone.Danger,
            )
            CompactActionButton(
                label = "Mover tickets al día siguiente",
                onClick = {
                    manualDisabledLotteryIds = onSetLotteryDisabled(lottery.id, true, pendingBlockedLotteryPermanent)
                    val count = onResolveBlockedLotteryTickets(lottery.id, BlockedLotteryTicketAction.MOVE_NEXT_DAY)
                    statusMessage = "${lottery.name} bloqueada $durationLabel. $count ticket(s) pasados al siguiente día."
                    pendingBlockedLottery = null
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                tone = ActionTone.Primary,
            )
        }
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
                val selectedArea = selectedConfigAreaId?.let(::resolveAdminConfigArea)
                ScreenHeaderPanel(
                    title = selectedArea?.title ?: "Configuración",
                    subtitle = if (selectedArea == null) {
                        "${session.banca ?: "LotteryNet"} · ${session.username}"
                    } else {
                        "Configuración operativa · ${session.banca ?: "LotteryNet"}"
                    },
                    onBack = { if (selectedArea == null) onBack() else selectedConfigAreaId = null },
                    badgeLabel = if (selectedArea == null) "Admin" else "Ajuste",
                    badgeTone = MaterialTheme.colorScheme.primary,
                )
            }
            if (selectedConfigAreaId == null) {
                item {
                    SystemConfigurationHub(
                        statusMessage = statusMessage,
                        config = systemModeConfig,
                        role = session.role,
                        lotteryCount = lotteries.size,
                        manualBlockedCount = manualDisabledLotteryIds.size,
                        calendarBlockedCount = initialCalendarDisabledLotteryIds.size,
                        brandingSyncStatus = brandingSyncStatus,
                        systemSyncStatus = systemSyncStatus,
                        onAreaSelected = { selectedConfigAreaId = resolveAdminConfigArea(it).id },
                        searchQuery = settingsSearchQuery,
                        onSearchQueryChange = { query -> settingsSearchQuery = query },
                        selectedGroup = selectedConfigGroupName,
                        onGroupChange = { selectedConfigGroupName = it },
                    )
                }
            } else {
                item {
                    AdminConfigAreaContextCard(resolveAdminConfigArea(selectedConfigAreaId!!))
                }
            }
            if (selectedConfigAreaId != null) when (resolveAdminConfigArea(selectedConfigAreaId!!)) {
                AdminConfigArea.SALE -> item {
                    CompactPanel {
                        OperationalListHeader(title = "Modo de venta", meta = "Admin, cajeros y sync")
                        Text(
                            "Define qué experiencias y modos de jugada estarán disponibles para admin y cajeros.",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                        )
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
                AdminConfigArea.BLOCKS -> {
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
                            onDisableAllLotteries = { visibleLotteries ->
                                manualDisabledLotteryIds = onDisableAllLotteries(visibleLotteries)
                                statusMessage = "Se bloquearon ${visibleLotteries.size} loterías visibles."
                                Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
                                manualDisabledLotteryIds
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
                            OperationalListHeader(title = "Bloqueo de jugadas", meta = "Aplica a todos")
                            Text(
                                "Los bloqueos afectan la disponibilidad de venta y no eliminan la configuración de límites.",
                                style = MaterialTheme.typography.bodySmall,
                                color = visual.colors.muted,
                            )
                            CompactStatusBadge(systemSyncStatus, tone = visual.colors.admin)
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
                }
                AdminConfigArea.CASH -> {
                    item {
                        CompactPanel {
                            OperationalListHeader(title = "Accesos operativos", meta = "Lo que afecta venta")
                            Text(
                                "Administra accesos relacionados con cajeros sin mezclarlo con la configuración de loterías.",
                                style = MaterialTheme.typography.bodySmall,
                                color = visual.colors.muted,
                            )
                            ConfigShortcut("Cajeros", adminConfigOperationShortcutDescriptions().first(), Icons.Rounded.ManageAccounts, onOpenUsers)
                        }
                    }
                    item {
                        CompactPanel {
                            OperationalListHeader(title = "Caja", meta = "Salidas y tickets")
                            Text(
                                "Logo, impresora y tickets pertenecen a la operación de caja.",
                                style = MaterialTheme.typography.bodySmall,
                                color = visual.colors.muted,
                            )
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
                }
                AdminConfigArea.SYSTEM -> item {
                    CompactPanel {
                        OperationalListHeader(title = "Estado del sistema", meta = "Diagnóstico")
                        Text(
                            "Consulta el estado de la configuración local y su sincronización. Las acciones de guardado permanecen dentro de cada categoría.",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                        )
                        OperationalSettingRow(
                            title = "Configuración de venta",
                            subtitle = "Modo admin, cajeros y POS Lite",
                            meta = systemSyncStatus,
                            icon = Icons.Rounded.Tune,
                            tone = visual.colors.sale,
                            onClick = { selectedConfigAreaId = AdminConfigArea.SALE.id },
                        )
                        OperationalSettingRow(
                            title = "Sincronización de ajustes",
                            subtitle = statusMessage,
                            meta = systemSyncStatus,
                            icon = Icons.Rounded.Sync,
                            tone = visual.colors.admin,
                            onClick = null,
                        )
                        OperationalSettingRow(
                            title = "Branding y caja",
                            subtitle = "Logo, impresora y tickets",
                            meta = brandingSyncStatus,
                            icon = Icons.Rounded.Print,
                            tone = visual.colors.printer,
                            onClick = { selectedConfigAreaId = AdminConfigArea.CASH.id },
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun AdminConfigAreaContextCard(area: AdminConfigArea) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        alt = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        OperationalListHeader(
            title = area.title,
            meta = adminConfigAreaGroup(area.id),
        )
        Text(
            area.subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = visual.colors.ink,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Text(
            adminConfigAreaActionText(area.id),
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.muted,
        )
    }
}

@Composable
private fun SystemConfigurationHub(
    statusMessage: String,
    config: AdminSystemModeConfig,
    role: UserRole,
    lotteryCount: Int,
    manualBlockedCount: Int,
    calendarBlockedCount: Int,
    brandingSyncStatus: String,
    systemSyncStatus: String,
    onAreaSelected: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedGroup: String,
    onGroupChange: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val adminMode = resolveAdminModeSegment(config)
    val cashierMode = resolveCashierDefaultModeSegment(config)
    val blockedPlayCount = config.blockedSalePlays.size
    val matchingAreas = filterAdminConfigAreaTitles(searchQuery)
        .mapNotNull { title -> AdminConfigArea.values().firstOrNull { it.title == title } }
        .filter { selectedGroup == ALL_ADMIN_CONFIG_GROUP || adminConfigAreaGroup(it.id) == selectedGroup }
    val hasActiveFilters = searchQuery.isNotBlank() || selectedGroup != ALL_ADMIN_CONFIG_GROUP
    Column(verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap)) {
        CompactPanel {
            OperationalListHeader(title = "Centro de ajustes", meta = "Configuración operativa")
            Text(
                "Abre una categoría para ver solo sus controles. Cada sección conserva su guardado actual.",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Buscar ajustes") },
                placeholder = { Text("Ej. POS, impresora, bloqueos") },
                trailingIcon = if (searchQuery.isBlank()) null else {
                    {
                        androidx.compose.material3.IconButton(
                            onClick = { onSearchQueryChange("") },
                        ) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                adminConfigGroupFilterOptions().forEach { option ->
                    FilterChip(
                        selected = selectedGroup == option.id,
                        onClick = { onGroupChange(option.id) },
                        label = { Text(option.label) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${matchingAreas.size} ${if (matchingAreas.size == 1) "categoría" else "categorías"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = visual.colors.muted,
                )
                if (hasActiveFilters) {
                    TextButton(
                        onClick = {
                            onSearchQueryChange("")
                            onGroupChange(ALL_ADMIN_CONFIG_GROUP)
                        },
                    ) {
                        Text("Limpiar filtros")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MetricStrip(
                items = listOf(
                    MetricStripItem("Venta", adminConfigModeShortLabel(adminMode), visual.colors.sale),
                    MetricStripItem("Bloq.", (manualBlockedCount + blockedPlayCount).toString(), if (manualBlockedCount + blockedPlayCount > 0) MaterialTheme.colorScheme.error else visual.colors.gain),
                    MetricStripItem("Loterías", lotteryCount.toString(), visual.colors.ink),
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactStatusBadge("Perfil ${adminConfigRoleLabel(role)}", tone = visual.colors.admin)
                CompactStatusBadge(systemSyncStatus, tone = visual.colors.admin)
            }
            Text(
                statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
            )
        }
        if (matchingAreas.isEmpty()) {
            CompactPanel(alt = true) {
                OperationalListHeader(title = "Sin resultados", meta = "Prueba otra palabra")
                Text(
                    "No hay una categoría que coincida con tu búsqueda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
            }
        } else {
            matchingAreas.groupBy { adminConfigAreaGroup(it.id) }.forEach { (group, areas) ->
                CompactPanel {
                    OperationalListHeader(
                        title = group.lowercase().replaceFirstChar(Char::uppercase),
                        meta = "${areas.size} ${if (areas.size == 1) "opción" else "opciones"}",
                    )
                    areas.forEach { area ->
                        AdminConfigCategoryCard(
                            area = area,
                            meta = when (area) {
                                AdminConfigArea.SALE -> "Admin ${adminConfigModeLabel(adminMode)}"
                                AdminConfigArea.BLOCKS -> "${manualBlockedCount + blockedPlayCount} activos"
                                AdminConfigArea.CASH -> brandingSyncStatus
                                AdminConfigArea.SYSTEM -> systemSyncStatus
                            },
                            onClick = { onAreaSelected(area.id) },
                        )
                    }
                }
            }
        }
        CompactPanel(
            alt = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Calendario: $calendarBlockedCount sin sorteo hoy · Cajeros: ${adminConfigModeLabel(cashierMode)}",
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.muted,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AdminConfigCategoryCard(
    area: AdminConfigArea,
    meta: String,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val compact = visual.windowMode == com.lotterynet.pro.ui.common.LotteryNetWindowMode.POS_TIGHT
    val tone = when (area) {
        AdminConfigArea.SALE -> visual.colors.sale
        AdminConfigArea.BLOCKS -> if (meta.startsWith("0")) visual.colors.gain else MaterialTheme.colorScheme.error
        AdminConfigArea.CASH -> visual.colors.printer
        AdminConfigArea.SYSTEM -> visual.colors.admin
    }
    val icon = when (area) {
        AdminConfigArea.SALE -> Icons.Rounded.Tune
        AdminConfigArea.BLOCKS -> Icons.Rounded.Lock
        AdminConfigArea.CASH -> Icons.Rounded.Print
        AdminConfigArea.SYSTEM -> Icons.Rounded.Sync
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        ListItem(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 8.dp else 10.dp,
            ),
            headlineContent = {
                Text(
                    area.title,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        area.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (compact) 1 else 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = tone)
                }
            },
            leadingContent = {
                Surface(
                    modifier = Modifier.size(if (compact) 38.dp else 44.dp),
                    shape = RoundedCornerShape(if (compact) 12.dp else 14.dp),
                    color = tone.copy(alpha = 0.14f),
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = tone,
                        modifier = Modifier.padding(if (compact) 9.dp else 11.dp),
                    )
                }
            },
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Abrir ${area.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

private fun adminConfigRoleLabel(role: UserRole): String {
    return when (role) {
        UserRole.MASTER -> "master"
        UserRole.ADMIN -> "admin"
        UserRole.SUPERVISOR -> "supervisor"
        UserRole.CASHIER -> "cajero"
        UserRole.UNKNOWN -> "sin rol"
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
    var showBlockSheet by rememberSaveable { mutableStateOf(false) }
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
    CompactActionButton(
        label = "Bloquear jugada",
        onClick = { showBlockSheet = true },
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
    if (showBlockSheet) {
        AdminConfigModalSheet(
            title = "Bloquear jugada",
            subtitle = "Elige el tipo y escribe el número exacto. Esto aplica a admin y cajeros.",
            onDismiss = { showBlockSheet = false },
        ) {
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
            CompactStatusBadge(
                label = candidate?.let { blockedSalePlayLabel(it) } ?: "Completa el número para activar",
                tone = if (candidate != null) MaterialTheme.colorScheme.error else visual.colors.neutral,
            )
            CompactActionButton(
                label = "Confirmar bloqueo",
                onClick = {
                    candidate?.let {
                        onChange(addBlockedSalePlay(config, it.playType, it.number))
                        numberInput = ""
                        showBlockSheet = false
                    }
                },
                enabled = candidate != null,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Lock,
                tone = ActionTone.Danger,
            )
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
    var pendingChange by remember { mutableStateOf<PendingSystemModeChange?>(null) }
    val modeOptions = listOf(
        QuickFilterChip("lottery", "Lotería"),
        QuickFilterChip("pick", "Pick"),
        QuickFilterChip("both", "Lotería + Pick"),
    )
    val adminMode = resolveAdminModeSegment(config)
    val cashierMode = resolveCashierDefaultModeSegment(config)
    Text(
        "Define qué aparece en Venta para admin y cuál modo reciben los cajeros al entrar. No cambia límites, comisiones ni premios.",
        style = MaterialTheme.typography.bodySmall,
        color = visual.colors.ink,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
    )
    MetricStrip(
        items = listOf(
            MetricStripItem("Admin", adminConfigModeShortLabel(adminMode), visual.colors.admin),
            MetricStripItem("Cajero", adminConfigModeShortLabel(cashierMode), visual.colors.sale),
            MetricStripItem("Pantalla", if (config.posLiteEnabled) "Lite" else "Normal", visual.colors.neutral),
        ),
    )
    CompactStatusBadge(
        label = when (role) {
            UserRole.MASTER -> "Perfil master"
            UserRole.ADMIN -> "Perfil admin"
            UserRole.SUPERVISOR -> "Perfil supervisor"
            UserRole.CASHIER -> "Perfil cajero"
            UserRole.UNKNOWN -> "Perfil sin rol"
        },
        tone = visual.colors.admin,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OperationalListHeader(title = "Pantalla", meta = "Equipos pequeños")
        CompactSwitchRow(
            title = "Modo POS Lite",
            subtitle = "Compacta pantallas operativas en equipos pequeños.",
            checked = config.posLiteEnabled,
            onCheckedChange = { onChange(normalizeAdminSystemModeConfig(config.copy(posLiteEnabled = it))) },
            tone = ActionTone.Primary,
        )

        OperationalListHeader(title = "Admin", meta = "Modo disponible")
        CompactSegmentedSelector(
            options = modeOptions,
            selectedId = adminMode,
            onSelected = { mode ->
                val next = applyAdminModeSegment(config, mode)
                if (next != config) {
                    val label = modeOptions.firstOrNull { it.id == mode }?.label ?: mode
                    pendingChange = PendingSystemModeChange(
                        title = "Cambiar modo admin",
                        message = "El admin venderá en modo $label. Confirma solo si este cambio corresponde a la operación actual.",
                        nextConfig = next,
                    )
                }
            },
            columns = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        OperationalListHeader(title = "Cajeros", meta = "Modo por defecto")
        CompactSegmentedSelector(
            options = modeOptions,
            selectedId = cashierMode,
            onSelected = { mode ->
                val next = applyCashierDefaultModeSegment(config, mode)
                if (next != config) {
                    val label = modeOptions.firstOrNull { it.id == mode }?.label ?: mode
                    pendingChange = PendingSystemModeChange(
                        title = "Cambiar modo cajero",
                        message = "Los cajeros entrarán por defecto en modo $label. Confirma para evitar cambiar el flujo de venta por accidente.",
                        nextConfig = next,
                    )
                }
            },
            columns = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        OperationalListHeader(title = "Sync y servidor", meta = "Guardar configuración")
        if (syncStatus.equals("Enviando", ignoreCase = true)) {
            CompactLoadingState(label = "Guardando modo de venta...")
        } else {
            CompactStatusBadge(syncStatus, tone = visual.colors.admin)
        }
        CompactActionButton(
            label = adminSystemModeSaveButtonLabel(),
            onClick = onSaveServer,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Save,
            tone = ActionTone.Primary,
        )
    }
    pendingChange?.let { change ->
        DangerConfirmSheet(
            title = change.title,
            message = change.message,
            confirmLabel = "Aplicar cambio",
            onConfirm = {
                onChange(change.nextConfig)
                pendingChange = null
            },
            onDismiss = { pendingChange = null },
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
    onDisableAllLotteries: (List<LotteryCatalogItem>) -> Set<String>,
    onToggleManualDisabled: (LotteryCatalogItem, Boolean) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var showLotterySheet by rememberSaveable { mutableStateOf(false) }
    var showDisableAllConfirm by rememberSaveable { mutableStateOf(false) }
    var lotteryQuery by rememberSaveable { mutableStateOf("") }
    var selectedLotteryId by rememberSaveable { mutableStateOf("") }
    val options = remember(lotteries, systemModeConfig) {
        filterAdminLotteryBlockOptions(lotteries, query = "", config = systemModeConfig)
    }
    val filteredOptions = remember(lotteries, systemModeConfig, lotteryQuery) {
        filterAdminLotteryBlockOptions(lotteries, query = lotteryQuery, config = systemModeConfig)
    }
    val selectedLottery = remember(options, selectedLotteryId) {
        resolveAdminLotteryBlockSelection(options, selectedLotteryId)
    }
    val blockableLotteries = remember(options, manualDisabledLotteryIds, calendarDisabledLotteryIds) {
        options.filter { lottery -> lottery.id !in manualDisabledLotteryIds && lottery.id !in calendarDisabledLotteryIds }
    }
    val blockLabels = remember { adminLotteryBlockActionLabels() }
    val openCount = options.count { it.id !in manualDisabledLotteryIds && it.id !in calendarDisabledLotteryIds }

    CompactPanel {
        OperationalListHeader(title = "Bloqueo de loterías", meta = "Una o todas a la vez")
        MetricStrip(
            items = listOf(
                MetricStripItem("Abiertas", openCount.toString(), visual.colors.gain),
                MetricStripItem("Bloq.", manualDisabledLotteryIds.size.toString(), if (manualDisabledLotteryIds.isNotEmpty()) MaterialTheme.colorScheme.error else visual.colors.neutral),
                MetricStripItem("Calendario", calendarDisabledLotteryIds.size.toString(), visual.colors.neutral),
            ),
        )
        CompactActionButton(
            label = blockLabels.first(),
            onClick = {
                lotteryQuery = ""
                showLotterySheet = true
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Tune,
            tone = ActionTone.Primary,
        )
        CompactActionButton(
            label = blockLabels.last(),
            onClick = { showDisableAllConfirm = true },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Lock,
            tone = ActionTone.Danger,
            enabled = blockableLotteries.isNotEmpty(),
        )
        CompactActionButton(
            label = "Habilitar disponibles hoy",
            onClick = onEnableAvailableLotteries,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.LockOpen,
            tone = ActionTone.Success,
        )
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
                    showLotterySheet = false
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
    if (showDisableAllConfirm) {
        DangerConfirmSheet(
            title = "Bloquear todas las loterías",
            message = if (blockableLotteries.isEmpty()) {
                "No hay loterías activas para bloquear en esta vista."
            } else {
                "Se bloquearán ${blockableLotteries.size} loterías visibles en esta sección. Podrás reabrirlas una por una después."
            },
            confirmLabel = "Bloquear todas",
            onConfirm = {
                if (blockableLotteries.isNotEmpty()) {
                    onDisableAllLotteries(blockableLotteries)
                }
                showDisableAllConfirm = false
            },
            onDismiss = { showDisableAllConfirm = false },
        )
    }
    if (showLotterySheet) {
        AdminConfigModalSheet(
            title = "Elegir lotería",
            subtitle = "Busca por nombre, tipo o ID. La selección no cambia nada hasta que toques bloquear o habilitar.",
            onDismiss = { showLotterySheet = false },
        ) {
            CompactTextInput(
                label = "Buscar",
                value = lotteryQuery,
                onValueChange = { lotteryQuery = it.take(60) },
                placeholder = "Ej: Anguilla, Pick 3, 19",
                leadingIcon = Icons.Rounded.Tune,
            )
            MetricStrip(
                items = listOf(
                    MetricStripItem("Coincidencias", filteredOptions.size.toString(), visual.colors.admin),
                    MetricStripItem("Bloq.", manualDisabledLotteryIds.size.toString(), if (manualDisabledLotteryIds.isNotEmpty()) MaterialTheme.colorScheme.error else visual.colors.neutral),
                ),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filteredOptions.isEmpty()) {
                    item {
                        Text(
                            "No hay loterías con ese filtro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                        )
                    }
                }
                items(filteredOptions.size, key = { index -> filteredOptions[index].id }) { index ->
                    val lottery = filteredOptions[index]
                    LotteryPickerRow(
                        lottery = lottery,
                        selected = lottery.id == selectedLotteryId,
                        isCalendarDisabled = lottery.id in calendarDisabledLotteryIds,
                        isManualDisabled = lottery.id in manualDisabledLotteryIds,
                        onClick = {
                            selectedLotteryId = lottery.id
                            showLotterySheet = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminConfigModalSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    OperationalModalSheet(
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        contentScrollable = false,
    ) {
        content()
    }
}

@Composable
private fun LotteryPickerRow(
    lottery: LotteryCatalogItem,
    selected: Boolean,
    isCalendarDisabled: Boolean,
    isManualDisabled: Boolean,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val status = when {
        isCalendarDisabled -> "Sin sorteo"
        isManualDisabled -> "Bloqueada"
        else -> "Activa"
    }
    val tone = when {
        isCalendarDisabled || isManualDisabled -> MaterialTheme.colorScheme.error
        selected -> visual.colors.admin
        else -> visual.colors.gain
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panelAlt, RoundedCornerShape(visual.sizes.panelRadius))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LotteryLogo(
            assetPath = lottery.logoAssetPath,
            fallback = lottery.name,
            modifier = Modifier.size(38.dp),
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
                "ID ${lottery.id} · ${resolveAdminLotteryScheduleText(lottery)}",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        CompactStatusBadge(
            label = if (selected) "Elegida" else status,
            tone = tone,
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(visual.colors.panelAlt, RoundedCornerShape(visual.sizes.panelRadius))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    resolveAdminLotteryScheduleText(lottery),
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 2,
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
        }
        if (!isCalendarDisabled) {
            CompactActionButton(
                label = if (isManualDisabled) "Habilitar" else "Bloquear",
                onClick = { onToggleManualDisabled(!isManualDisabled) },
                modifier = Modifier.fillMaxWidth(),
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
