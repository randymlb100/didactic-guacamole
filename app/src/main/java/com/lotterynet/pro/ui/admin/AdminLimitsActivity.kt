@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.lotterynet.pro.ui.admin

// Material 3 section navigation uses FilterChip and ModalBottomSheet.
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.rememberModalBottomSheetState

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.storage.AdminOperationalLimits
import com.lotterynet.pro.core.storage.CashierSalesLimitInputs
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import com.lotterynet.pro.core.storage.LocalAdminLimitRepository
import com.lotterynet.pro.core.storage.LocalPosModeRepository
import com.lotterynet.pro.core.storage.LocalRechargeLimitRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.decodeRechargeLimitSettingsPayload
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.sync.CashierLimitCloudSyncCoordinator
import com.lotterynet.pro.core.sync.cashierLimitRemoteKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKeys
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.CompactKeyValueRow
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.OperationalListHeader
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import org.json.JSONObject

class AdminLimitsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.ADMIN_LIMITS)) return
        val session = activeSession ?: return
        LocalUsersRepository(this).touchSession(session)
        val adminLimitRepository = LocalAdminLimitRepository(this)
        val rechargeLimitRepository = LocalRechargeLimitRepository(this)
        val cashierSalesLimitRepository = LocalCashierSalesLimitRepository(this)
        val posModeRepository = LocalPosModeRepository(this)
        val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        val serviceStore = SupabaseMasterConfigRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
        )
        val cashierLimitCloudSync = CashierLimitCloudSyncCoordinator(
            cashierSalesLimitRepository,
            remoteStore = serviceStore,
        )
        val ownerId = session.adminId ?: session.userId
        val ownerKeys = resolveOperationalOwnerKeys(session).ifEmpty { listOf(ownerId) }
        hydrateAdminLimitsFromServer(
            remoteStore = serviceStore,
            ownerId = ownerId,
            ownerKeys = ownerKeys,
            adminLimitRepository = adminLimitRepository,
            rechargeLimitRepository = rechargeLimitRepository,
            cashierSalesLimitRepository = cashierSalesLimitRepository,
        )
        setContent {
            LotteryNetComposeTheme {
                AdminLimitsRoute(
                    initialAdminLimits = adminLimitRepository.getLimits(),
                    initialRechargeLimits = rechargeLimitRepository.getSettings(),
                    initialPoolLimits = cashierSalesLimitRepository.getPoolLimits(ownerId),
                    initialSalesLimits = cashierSalesLimitRepository.getDefaultLimits(ownerId),
                    initialAdminSelfLimits = cashierSalesLimitRepository.getAdminSelfLimits(ownerId),
                    initialPosModeEnabled = posModeRepository.isEnabled(),
                    onBack = { finish() },
                    onSavePosMode = { enabled ->
                        posModeRepository.setEnabled(enabled)
                        Toast.makeText(
                            this,
                            if (enabled) "Modo POS activo" else "Modo POS desactivado",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onSave = { adminLimits, rechargeLimits, poolLimits, salesLimits, adminSelfLimits ->
                        Thread {
                            val ok = runCatching {
                                serviceStore.upsertJsonValue(
                                    "admin_operational_limits:$ownerId",
                                    JSONObject().apply {
                                        put("cashierPayoutLimit", adminLimits.cashierPayoutLimit.coerceAtLeast(0.0))
                                    }.toString(),
                                )
                                serviceStore.upsertJsonValue(
                                    "recharge_limits:$ownerId",
                                    JSONObject().apply {
                                        put("globalPerTx", rechargeLimits.globalPerTx.coerceAtLeast(0.0))
                                        put("masterPerTx", rechargeLimits.masterPerTx.coerceAtLeast(0.0))
                                    }.toString(),
                                )
                                serviceStore.upsertJsonValue(
                                    "sys_master_limits_v1",
                                    JSONObject().apply {
                                        put("recarga", rechargeLimits.masterPerTx.coerceAtLeast(0.0))
                                    }.toString(),
                                )
                                check(cashierLimitCloudSync.pushPoolLimitsServiceFirst(ownerId, poolLimits))
                                check(
                                    cashierLimitCloudSync.pushDefaultLimitsServiceFirst(
                                        ownerId,
                                        resolveDefaultSalesLimitsForServer(salesLimits, adminLimits),
                                    ),
                                )
                                check(cashierLimitCloudSync.pushAdminSelfLimitsServiceFirst(ownerId, adminSelfLimits))
                                adminLimitRepository.saveLimits(adminLimits)
                                rechargeLimitRepository.saveSettings(rechargeLimits)
                                true
                            }.getOrDefault(false)
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (ok) "Límites guardados en servidor" else "No se guardó: servidor no disponible",
                                    if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }.start()
                    },
                )
            }
        }
    }
}

private fun hydrateAdminLimitsFromServer(
    remoteStore: SupabaseMasterConfigRemoteStore,
    ownerId: String,
    ownerKeys: List<String>,
    adminLimitRepository: LocalAdminLimitRepository,
    rechargeLimitRepository: LocalRechargeLimitRepository,
    cashierSalesLimitRepository: LocalCashierSalesLimitRepository,
) {
    runCatching {
        firstRemoteValue(ownerKeys) { key -> remoteStore.fetchValue("admin_operational_limits:$key") }
            ?.let(::decodeAdminOperationalLimitsPayload)
            ?.let(adminLimitRepository::saveLimits)

        firstRemoteValue(ownerKeys) { key -> remoteStore.fetchValue("recharge_limits:$key") }
            ?.let(::decodeRechargeLimitSettingsPayload)
            ?.let(rechargeLimitRepository::saveSettings)

        firstRemoteValue(ownerKeys) { key -> remoteStore.fetchValue(cashierLimitRemoteKey(key)) }
            ?.let { cashierSalesLimitRepository.cachePayload(ownerId, it.toString()) }
    }
}

private fun firstRemoteValue(ownerKeys: List<String>, fetch: (String) -> Any?): Any? {
    ownerKeys.forEach { ownerKey ->
        val value = runCatching { fetch(ownerKey) }.getOrNull()
        if (value != null) return value
    }
    return null
}

private fun decodeAdminOperationalLimitsPayload(payload: Any?): AdminOperationalLimits? {
    val root = when (payload) {
        is JSONObject -> payload
        is String -> runCatching { JSONObject(payload) }.getOrNull()
        else -> null
    } ?: return null
    val payout = root.optDouble("cashierPayoutLimit", Double.NaN)
    if (!payout.isFinite()) return null
    return AdminOperationalLimits(cashierPayoutLimit = payout.coerceAtLeast(0.0))
}

@Composable
private fun AdminLimitsRoute(
    initialAdminLimits: AdminOperationalLimits,
    initialRechargeLimits: com.lotterynet.pro.core.storage.RechargeLimitSettings,
    initialPoolLimits: CashierSalesLimitInputs,
    initialSalesLimits: CashierSalesLimitInputs,
    initialAdminSelfLimits: CashierSalesLimitInputs?,
    initialPosModeEnabled: Boolean,
    onBack: () -> Unit,
    onSavePosMode: (Boolean) -> Unit,
    onSave: (AdminOperationalLimits, com.lotterynet.pro.core.storage.RechargeLimitSettings, CashierSalesLimitInputs, CashierSalesLimitInputs, CashierSalesLimitInputs) -> Unit,
) {
    val adminSelfInitial = initialAdminSelfLimits ?: emptyCashierSalesLimitInputs()
    var poolQuinielaLimit by rememberSaveable { mutableStateOf(formatLimit(initialPoolLimits.quiniela)) }
    var poolPaleLimit by rememberSaveable { mutableStateOf(formatLimit(initialPoolLimits.pale)) }
    var poolSuperPaleLimit by rememberSaveable { mutableStateOf(formatLimit(initialPoolLimits.superPale)) }
    var poolTripletaLimit by rememberSaveable { mutableStateOf(formatLimit(initialPoolLimits.tripleta)) }
    var poolPick3StraightLimit by rememberSaveable { mutableStateOf(formatLimit(initialPoolLimits.pick3Straight)) }
    var poolPick3BoxLimit by rememberSaveable { mutableStateOf(formatLimit(initialPoolLimits.pick3Box)) }
    var poolPick4StraightLimit by rememberSaveable { mutableStateOf(formatLimit(initialPoolLimits.pick4Straight)) }
    var poolPick4BoxLimit by rememberSaveable { mutableStateOf(formatLimit(initialPoolLimits.pick4Box)) }
    var daySaleLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.daySale)) }
    var payoutSalesLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.payout)) }
    var quinielaLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.quiniela)) }
    var paleLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.pale)) }
    var superPaleLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.superPale)) }
    var tripletaLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.tripleta)) }
    var pick3StraightLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.pick3Straight)) }
    var pick3BoxLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.pick3Box)) }
    var pick4StraightLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.pick4Straight)) }
    var pick4BoxLimit by rememberSaveable { mutableStateOf(formatLimit(initialSalesLimits.pick4Box)) }
    var adminDaySaleLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.daySale)) }
    var adminPayoutSalesLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.payout)) }
    var adminQuinielaLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.quiniela)) }
    var adminPaleLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.pale)) }
    var adminSuperPaleLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.superPale)) }
    var adminTripletaLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.tripleta)) }
    var adminPick3StraightLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.pick3Straight)) }
    var adminPick3BoxLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.pick3Box)) }
    var adminPick4StraightLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.pick4Straight)) }
    var adminPick4BoxLimit by rememberSaveable { mutableStateOf(formatLimit(adminSelfInitial.pick4Box)) }
    var payoutLimit by rememberSaveable { mutableStateOf(formatLimit(initialAdminLimits.cashierPayoutLimit)) }
    var globalRecharge by rememberSaveable { mutableStateOf(formatLimit(initialRechargeLimits.globalPerTx)) }
    var masterRecharge by rememberSaveable { mutableStateOf(formatLimit(initialRechargeLimits.masterPerTx)) }
    var posModeEnabled by rememberSaveable { mutableStateOf(initialPosModeEnabled) }
    var showPosModePassword by rememberSaveable { mutableStateOf(false) }
    var posModePassword by rememberSaveable { mutableStateOf("") }
    var posModePasswordError by rememberSaveable { mutableStateOf(false) }
    var selectedSection by rememberSaveable { mutableStateOf("overview") }
    val visual = rememberLotteryNetVisualSpec()
    val sectionOption = adminLimitsSectionOptions().firstOrNull { it.id == selectedSection }
        ?: adminLimitsSectionOptions().first()
    var showSectionPicker by rememberSaveable { mutableStateOf(false) }
    var showSectionMenu by rememberSaveable { mutableStateOf(false) }
    var showPoolEditor by rememberSaveable { mutableStateOf(false) }
    var poolEditorPlay by rememberSaveable { mutableStateOf("quiniela") }
    var poolEditorValue by rememberSaveable { mutableStateOf(poolQuinielaLimit) }
    var showAdminEditor by rememberSaveable { mutableStateOf(false) }
    var adminEditorKey by rememberSaveable { mutableStateOf("daySale") }
    var adminEditorValue by rememberSaveable { mutableStateOf(adminDaySaleLimit) }
    var showCashierEditor by rememberSaveable { mutableStateOf(false) }
    var cashierEditorKey by rememberSaveable { mutableStateOf("daySale") }
    var cashierEditorValue by rememberSaveable { mutableStateOf(daySaleLimit) }
    var showOperationEditor by rememberSaveable { mutableStateOf(false) }
    var operationEditorKey by rememberSaveable { mutableStateOf("payout") }
    var operationEditorValue by rememberSaveable { mutableStateOf(payoutLimit) }
    val sectionOptions = adminLimitsSectionOptions()
    LaunchedEffect(Unit) {
        if (adminLimitsSectionOptions().none { it.id == selectedSection }) {
            selectedSection = "overview"
        }
    }
    val salesLimitCopy = resolveCashierSalesLimitVisibilityContract(initialSalesLimits)
    val adminSelfContract = resolveAdminLimitScopeContract(
        selectedScope = AdminLimitScope.ADMIN_SELF,
        adminHasSelfLimits = initialAdminSelfLimits != null,
        cashierDefaultsEnabled = initialSalesLimits.daySale > 0.0,
    )
    fun saveCurrentLimits() {
        onSave(
            AdminOperationalLimits(
                cashierPayoutLimit = payoutLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            ),
            com.lotterynet.pro.core.storage.RechargeLimitSettings(
                globalPerTx = globalRecharge.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                masterPerTx = masterRecharge.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            ),
            CashierSalesLimitInputs(
                daySale = 0.0,
                payout = 0.0,
                quiniela = poolQuinielaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pale = poolPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                superPale = poolSuperPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                tripleta = poolTripletaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick3Straight = poolPick3StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick3Box = poolPick3BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick4Straight = poolPick4StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick4Box = poolPick4BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            ),
            CashierSalesLimitInputs(
                daySale = daySaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                payout = payoutSalesLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                quiniela = quinielaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pale = paleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                superPale = superPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                tripleta = tripletaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick3Straight = pick3StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick3Box = pick3BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick4Straight = pick4StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick4Box = pick4BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            ),
            CashierSalesLimitInputs(
                daySale = adminDaySaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                payout = adminPayoutSalesLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                quiniela = adminQuinielaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pale = adminPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                superPale = adminSuperPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                tripleta = adminTripletaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick3Straight = adminPick3StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick3Box = adminPick3BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick4Straight = adminPick4StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                pick4Box = adminPick4BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            ),
        )
    }
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
        if (showSectionPicker) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSectionPicker = false },
                sheetState = sheetState,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Cambiar área de límites",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Selecciona el alcance que quieres revisar o editar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = rememberLotteryNetVisualSpec().colors.muted,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(sectionOptions, key = { it.id }) { option ->
                            val selected = option.id == selectedSection
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedSection = option.id
                                    showSectionPicker = false
                                },
                                label = { Text(option.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (selected) Icons.Rounded.Check else Icons.Rounded.FilterList,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showSectionMenu = true },
                            label = { Text("Abrir lista desplegable") },
                            leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
                        )
                        DropdownMenu(
                            expanded = showSectionMenu,
                            onDismissRequest = { showSectionMenu = false },
                        ) {
                            sectionOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        selectedSection = option.id
                                        showSectionMenu = false
                                        showSectionPicker = false
                                    },
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { showSectionPicker = false },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("Cerrar") }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdminLimitsCompactHeader(onBack = onBack)
            AdminLimitsSectionToolbar(
                selectedSection = selectedSection,
                selectedLabel = sectionOption.label,
                onOpenPicker = { showSectionPicker = true },
            )
            if (selectedSection == "overview") {
                AdminLimitsConfigurationProgress(
                    configuredPoolRules = listOf(
                        initialPoolLimits.quiniela,
                        initialPoolLimits.pale,
                        initialPoolLimits.superPale,
                        initialPoolLimits.tripleta,
                        initialPoolLimits.pick3Straight,
                        initialPoolLimits.pick3Box,
                        initialPoolLimits.pick4Straight,
                        initialPoolLimits.pick4Box,
                    ).count { it > 0.0 },
                    totalPoolRules = 8,
                )
                AdminLimitsOverview(
                    items = adminLimitsOverviewItems(
                        adminLimits = initialAdminLimits,
                        rechargeLimits = initialRechargeLimits,
                        poolLimits = initialPoolLimits,
                        cashierLimits = initialSalesLimits,
                        adminSelfLimits = initialAdminSelfLimits,
                        posModeEnabled = posModeEnabled,
                    ),
                    onOpen = { destination -> selectedSection = destination.toSectionId() },
                )
            } else {
                AdminLimitsDetailNavigation(
                    title = sectionOption.label,
                    scopeLabel = adminLimitsSectionScope(selectedSection),
                    tone = adminLimitsSectionColor(selectedSection),
                    onBackToOverview = { selectedSection = "overview" },
                )
                AdminLimitsScopeContext(
                    title = sectionOption.label,
                    summary = adminLimitsSectionSummary(selectedSection),
                    scopeLabel = adminLimitsSectionScope(selectedSection),
                    tone = adminLimitsSectionColor(selectedSection),
                    destination = adminLimitsSectionDestination(selectedSection),
                )
            }
            if (selectedSection == "adminSelf") {
                CompactPanel(alt = true) {
                    OperationalListHeader(title = "Mis límites de venta", meta = adminSelfContract.emptyStateCopy)
                    Text(
                        "Si queda vacío, el admin vende sin tope propio. No se mezcla con los cajeros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                    CompactKeyValueRow(
                        label = "Estado",
                        value = if (initialAdminSelfLimits == null) "Sin tope" else "Con tope propio",
                        tone = if (initialAdminSelfLimits == null) MaterialTheme.colorScheme.primary else null,
                    )
                    CompactActionButton(
                        label = "Editar un límite",
                        onClick = {
                            adminEditorKey = "daySale"
                            adminEditorValue = adminDaySaleLimit
                            showAdminEditor = true
                        },
                        icon = Icons.Rounded.FilterList,
                        tone = ActionTone.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showAdminEditor) {
                        LimitEditorSheet(
                            title = "Editar límite propio del admin",
                            options = adminLimitEditorOptions(),
                            selectedKey = adminEditorKey,
                            value = adminEditorValue,
                            onSelectedKeyChange = { key ->
                                adminEditorKey = key
                                adminEditorValue = readAdminLimitEditorValue(
                                    key,
                                    adminDaySaleLimit,
                                    adminPayoutSalesLimit,
                                    adminQuinielaLimit,
                                    adminPaleLimit,
                                    adminSuperPaleLimit,
                                    adminTripletaLimit,
                                    adminPick3StraightLimit,
                                    adminPick3BoxLimit,
                                    adminPick4StraightLimit,
                                    adminPick4BoxLimit,
                                )
                            },
                            onValueChange = { adminEditorValue = sanitizeLimit(it) },
                            onDismiss = { showAdminEditor = false },
                            onSave = {
                                writeAdminLimitEditorValue(adminEditorKey, adminEditorValue) { key, next ->
                                    when (key) {
                                        "daySale" -> adminDaySaleLimit = next
                                        "payout" -> adminPayoutSalesLimit = next
                                        "quiniela" -> adminQuinielaLimit = next
                                        "pale" -> adminPaleLimit = next
                                        "superPale" -> adminSuperPaleLimit = next
                                        "tripleta" -> adminTripletaLimit = next
                                        "pick3Straight" -> adminPick3StraightLimit = next
                                        "pick3Box" -> adminPick3BoxLimit = next
                                        "pick4Straight" -> adminPick4StraightLimit = next
                                        "pick4Box" -> adminPick4BoxLimit = next
                                    }
                                }
                                showAdminEditor = false
                            },
                        )
                    }
                    OutlinedTextField(
                        value = adminDaySaleLimit,
                        onValueChange = { adminDaySaleLimit = sanitizeLimit(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Mi venta diaria") },
                        leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                        supportingText = { Text("0 o vacío deja al admin sin tope diario.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    SectionHeader(title = "Mis jugadas", meta = "Admin")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = adminQuinielaLimit,
                            onValueChange = { adminQuinielaLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Quiniela") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = adminPaleLimit,
                            onValueChange = { adminPaleLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pale") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = adminSuperPaleLimit,
                            onValueChange = { adminSuperPaleLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Super Pale") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = adminTripletaLimit,
                            onValueChange = { adminTripletaLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Tripleta") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = adminPick3StraightLimit,
                            onValueChange = { adminPick3StraightLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 3 Straight") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = adminPick3BoxLimit,
                            onValueChange = { adminPick3BoxLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 3 Box") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = adminPick4StraightLimit,
                            onValueChange = { adminPick4StraightLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 4 Straight") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = adminPick4BoxLimit,
                            onValueChange = { adminPick4BoxLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 4 Box") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    OutlinedTextField(
                        value = adminPayoutSalesLimit,
                        onValueChange = { adminPayoutSalesLimit = sanitizeLimit(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Mi tope de cobro") },
                        leadingIcon = { Icon(Icons.Rounded.Payments, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            if (selectedSection == "pool") {
                CompactPanel(alt = true) {
                    OperationalListHeader(title = "Pool de exposición por jugada", meta = "Global · servidor")
                    CompactKeyValueRow(label = "Alcance", value = "Lotería + número + tipo de jugada")
                    Text(
                        "Estos valores limitan la exposición acumulada de cada número dentro de una lotería. Suman las ventas de todos los cajeros; no son límites de usuario.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                    Text("0 = sin tope de pool", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    CompactActionButton(
                        label = "Editar una jugada",
                        onClick = {
                            poolEditorPlay = "quiniela"
                            poolEditorValue = poolQuinielaLimit
                            showPoolEditor = true
                        },
                        icon = Icons.Rounded.FilterList,
                        tone = ActionTone.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showPoolEditor) {
                        LimitEditorSheet(
                            title = "Editar límite del pool",
                            options = listOf(
                                "quiniela" to "Quiniela",
                                "pale" to "Pale",
                                "superPale" to "Super Pale",
                                "tripleta" to "Tripleta",
                                "pick3Straight" to "Pick 3 Straight",
                                "pick3Box" to "Pick 3 Box",
                                "pick4Straight" to "Pick 4 Straight",
                                "pick4Box" to "Pick 4 Box",
                            ),
                            selectedKey = poolEditorPlay,
                            value = poolEditorValue,
                            onSelectedKeyChange = { key ->
                                poolEditorPlay = key
                                poolEditorValue = when (key) {
                                    "pale" -> poolPaleLimit
                                    "superPale" -> poolSuperPaleLimit
                                    "tripleta" -> poolTripletaLimit
                                    "pick3Straight" -> poolPick3StraightLimit
                                    "pick3Box" -> poolPick3BoxLimit
                                    "pick4Straight" -> poolPick4StraightLimit
                                    "pick4Box" -> poolPick4BoxLimit
                                    else -> poolQuinielaLimit
                                }
                            },
                            onValueChange = { poolEditorValue = sanitizeLimit(it) },
                            onDismiss = { showPoolEditor = false },
                            onSave = {
                                when (poolEditorPlay) {
                                    "pale" -> poolPaleLimit = poolEditorValue
                                    "superPale" -> poolSuperPaleLimit = poolEditorValue
                                    "tripleta" -> poolTripletaLimit = poolEditorValue
                                    "pick3Straight" -> poolPick3StraightLimit = poolEditorValue
                                    "pick3Box" -> poolPick3BoxLimit = poolEditorValue
                                    "pick4Straight" -> poolPick4StraightLimit = poolEditorValue
                                    "pick4Box" -> poolPick4BoxLimit = poolEditorValue
                                    else -> poolQuinielaLimit = poolEditorValue
                                }
                                showPoolEditor = false
                            },
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = poolQuinielaLimit,
                            onValueChange = { poolQuinielaLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Quiniela") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = poolPaleLimit,
                            onValueChange = { poolPaleLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pale") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = poolSuperPaleLimit,
                            onValueChange = { poolSuperPaleLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Super Pale") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = poolTripletaLimit,
                            onValueChange = { poolTripletaLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Tripleta") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = poolPick3StraightLimit,
                            onValueChange = { poolPick3StraightLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 3 Straight") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = poolPick3BoxLimit,
                            onValueChange = { poolPick3BoxLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 3 Box") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = poolPick4StraightLimit,
                            onValueChange = { poolPick4StraightLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 4 Straight") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = poolPick4BoxLimit,
                            onValueChange = { poolPick4BoxLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 4 Box") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
            }
            if (selectedSection == "cashiers") {
                CompactPanel(alt = true) {
                    OperationalListHeader(title = "Límites base del cajero", meta = "Fallback por usuario")
                    CompactKeyValueRow(
                        label = "Alcance",
                        value = "Cada cajero, si no tiene un límite propio",
                    )
                    Text(
                        "Estos valores no controlan el pool. Definen cuánto puede vender cada cajero por día y por tipo de jugada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                    Text("0 = sin límite de cajero", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    CompactActionButton(
                        label = "Editar un límite base",
                        onClick = {
                            cashierEditorKey = "daySale"
                            cashierEditorValue = daySaleLimit
                            showCashierEditor = true
                        },
                        icon = Icons.Rounded.FilterList,
                        tone = ActionTone.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showCashierEditor) {
                        LimitEditorSheet(
                            title = "Editar límite base de cajeros",
                            options = salesLimitEditorOptions(),
                            selectedKey = cashierEditorKey,
                            value = cashierEditorValue,
                            onSelectedKeyChange = { key ->
                                cashierEditorKey = key
                                cashierEditorValue = readSalesLimitEditorValue(
                                    key,
                                    daySaleLimit,
                                    payoutSalesLimit,
                                    quinielaLimit,
                                    paleLimit,
                                    superPaleLimit,
                                    tripletaLimit,
                                    pick3StraightLimit,
                                    pick3BoxLimit,
                                    pick4StraightLimit,
                                    pick4BoxLimit,
                                )
                            },
                            onValueChange = { cashierEditorValue = sanitizeLimit(it) },
                            onDismiss = { showCashierEditor = false },
                            onSave = {
                                writeSalesLimitEditorValue(cashierEditorKey, cashierEditorValue) { key, next ->
                                    when (key) {
                                        "daySale" -> daySaleLimit = next
                                        "payout" -> payoutSalesLimit = next
                                        "quiniela" -> quinielaLimit = next
                                        "pale" -> paleLimit = next
                                        "superPale" -> superPaleLimit = next
                                        "tripleta" -> tripletaLimit = next
                                        "pick3Straight" -> pick3StraightLimit = next
                                        "pick3Box" -> pick3BoxLimit = next
                                        "pick4Straight" -> pick4StraightLimit = next
                                        "pick4Box" -> pick4BoxLimit = next
                                    }
                                }
                                showCashierEditor = false
                            },
                        )
                    }
                    CompactKeyValueRow(
                        label = salesLimitCopy.currentDaySaleLabel,
                        value = salesLimitCopy.currentDaySaleValue,
                        tone = if (initialSalesLimits.daySale > 0.0) null else MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = daySaleLimit,
                        onValueChange = { daySaleLimit = sanitizeLimit(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(salesLimitCopy.daySaleLabel) },
                        leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                        supportingText = { Text(salesLimitCopy.daySaleHelp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    SectionHeader(
                        title = "Límites por jugada",
                        meta = "Base",
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = quinielaLimit,
                            onValueChange = { quinielaLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Quiniela") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = paleLimit,
                            onValueChange = { paleLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pale") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = superPaleLimit,
                            onValueChange = { superPaleLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Super Pale") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = tripletaLimit,
                            onValueChange = { tripletaLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Tripleta") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = pick3StraightLimit,
                            onValueChange = { pick3StraightLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 3 Straight") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = pick3BoxLimit,
                            onValueChange = { pick3BoxLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 3 Box") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = pick4StraightLimit,
                            onValueChange = { pick4StraightLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 4 Straight") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            value = pick4BoxLimit,
                            onValueChange = { pick4BoxLimit = sanitizeLimit(it) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("Pick 4 Box") },
                            leadingIcon = { Icon(Icons.Rounded.Casino, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
            }
            if (selectedSection == "cash") {
                CompactPanel {
                    OperationalListHeader(title = "Caja y recargas", meta = "Operación")
                    Text(
                        "No afecta el ranking de números ni los límites de venta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                    CompactActionButton(
                        label = "Editar operación",
                        onClick = {
                            operationEditorKey = "payout"
                            operationEditorValue = payoutLimit
                            showOperationEditor = true
                        },
                        icon = Icons.Rounded.FilterList,
                        tone = ActionTone.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showOperationEditor) {
                        LimitEditorSheet(
                            title = "Editar cobro o recarga",
                            options = listOf(
                                "payout" to "Tope de cobro por cajero",
                                "globalRecharge" to "Tope global de recarga",
                                "masterRecharge" to "Tope master de recarga",
                            ),
                            selectedKey = operationEditorKey,
                            value = operationEditorValue,
                            onSelectedKeyChange = { key ->
                                operationEditorKey = key
                                operationEditorValue = when (key) {
                                    "globalRecharge" -> globalRecharge
                                    "masterRecharge" -> masterRecharge
                                    else -> payoutLimit
                                }
                            },
                            onValueChange = { operationEditorValue = sanitizeLimit(it) },
                            onDismiss = { showOperationEditor = false },
                            onSave = {
                                when (operationEditorKey) {
                                    "globalRecharge" -> globalRecharge = operationEditorValue
                                    "masterRecharge" -> masterRecharge = operationEditorValue
                                    else -> payoutLimit = operationEditorValue
                                }
                                showOperationEditor = false
                            },
                        )
                    }
                    SectionHeader(title = "Límite de cobro", meta = "Por cajero")
                    CompactKeyValueRow(
                        label = "Tope actual",
                        value = if (initialAdminLimits.cashierPayoutLimit > 0.0) moneyLimit(initialAdminLimits.cashierPayoutLimit) else "Sin tope",
                        tone = if (initialAdminLimits.cashierPayoutLimit > 0.0) null else MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = payoutLimit,
                        onValueChange = { payoutLimit = sanitizeLimit(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Tope pago por cajero") },
                        leadingIcon = { Icon(Icons.Rounded.Payments, contentDescription = null) },
                        supportingText = { Text("0 deja al cajero sin tope local de pago.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                CompactPanel(alt = true) {
                    OperationalListHeader(title = "Recargas", meta = "Global y master")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CompactKeyValueRow(
                            label = "Global actual",
                            value = if (initialRechargeLimits.globalPerTx > 0.0) moneyLimit(initialRechargeLimits.globalPerTx) else "Sin tope",
                            tone = if (initialRechargeLimits.globalPerTx > 0.0) null else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        CompactKeyValueRow(
                            label = "Master actual",
                            value = if (initialRechargeLimits.masterPerTx > 0.0) moneyLimit(initialRechargeLimits.masterPerTx) else "Sin tope",
                            tone = if (initialRechargeLimits.masterPerTx > 0.0) null else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = globalRecharge,
                        onValueChange = { globalRecharge = sanitizeLimit(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Tope global recarga") },
                        leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                        supportingText = { Text("0 deja la banca sin tope global por recarga.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = masterRecharge,
                        onValueChange = { masterRecharge = sanitizeLimit(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Tope master recarga") },
                        leadingIcon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
                        supportingText = { Text("0 deja el control master sin tope local.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            if (selectedSection == "system") {
            CompactPanel {
                OperationalListHeader(title = "Sistema POS", meta = "Pantalla compacta")
                Text(
                    "Este modo cambia la experiencia visual, no los límites de venta ni sus reglas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
                CompactKeyValueRow(
                    label = "Modo POS",
                    value = if (posModeEnabled) "Activo" else "Inactivo",
                )
                CompactActionButton(
                    label = posModeActionLabel(),
                    onClick = {
                        posModePassword = ""
                        posModePasswordError = false
                        showPosModePassword = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PointOfSale,
                    tone = if (posModeEnabled) ActionTone.Secondary else ActionTone.Primary,
                    active = true,
                )
                Text(
                    "Compacta venta, jugadas e impresión para equipos 5.5 con impresora integrada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
            }
            CompactPanel {
                OperationalListHeader(title = "Aplicar", meta = "Guardado local")
                CompactActionButton(
                    label = "Guardar límites",
                    onClick = {
                        onSave(
                            AdminOperationalLimits(
                                cashierPayoutLimit = payoutLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            ),
                            com.lotterynet.pro.core.storage.RechargeLimitSettings(
                                globalPerTx = globalRecharge.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                masterPerTx = masterRecharge.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            ),
                            CashierSalesLimitInputs(
                                daySale = 0.0,
                                payout = 0.0,
                                quiniela = poolQuinielaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pale = poolPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                superPale = poolSuperPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                tripleta = poolTripletaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick3Straight = poolPick3StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick3Box = poolPick3BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick4Straight = poolPick4StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick4Box = poolPick4BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            ),
                            CashierSalesLimitInputs(
                                daySale = daySaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                payout = payoutSalesLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                quiniela = quinielaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pale = paleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                superPale = superPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                tripleta = tripletaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick3Straight = pick3StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick3Box = pick3BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick4Straight = pick4StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick4Box = pick4BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            ),
                            CashierSalesLimitInputs(
                                daySale = adminDaySaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                payout = adminPayoutSalesLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                quiniela = adminQuinielaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pale = adminPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                superPale = adminSuperPaleLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                tripleta = adminTripletaLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick3Straight = adminPick3StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick3Box = adminPick3BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick4Straight = adminPick4StraightLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                                pick4Box = adminPick4BoxLimit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Payments,
                    tone = ActionTone.Primary,
                )
                Text(
                    "Venta ${daySaleLimit.ifBlank { "0" }} · Q ${quinielaLimit.ifBlank { "0" }} · P ${paleLimit.ifBlank { "0" }} · T ${tripletaLimit.ifBlank { "0" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
            }
        }
        if (selectedSection != "overview" && selectedSection != "system") {
            CompactPanel {
                OperationalListHeader(title = "Guardar cambios", meta = sectionOption.label)
                Text(
                    "Solo se está editando este bloque. Los demás valores se conservan al enviar el mismo contrato actual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
                CompactActionButton(
                    label = "Guardar ${sectionOption.label.lowercase()}",
                    onClick = ::saveCurrentLimits,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Payments,
                    tone = ActionTone.Primary,
                )
            }
        }
        if (showPosModePassword) {
            AlertDialog(
                onDismissRequest = { showPosModePassword = false },
                title = { Text("Modo POS") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (posModeEnabled) {
                                "Escribe la contraseña para desactivar el modo compacto."
                            } else {
                                "Escribe la contraseña para activar el modo compacto."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                        )
                        OutlinedTextField(
                            value = posModePassword,
                            onValueChange = {
                                posModePassword = it.filter(Char::isDigit).take(6)
                                posModePasswordError = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Contraseña") },
                            isError = posModePasswordError,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            supportingText = {
                                if (posModePasswordError) Text("Contraseña incorrecta")
                            },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (verifyPosModePassword(posModePassword)) {
                                val next = !posModeEnabled
                                posModeEnabled = next
                                onSavePosMode(next)
                                showPosModePassword = false
                            } else {
                                posModePasswordError = true
                            }
                        },
                    ) {
                        Text(if (posModeEnabled) "Desactivar" else "Activar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPosModePassword = false }) {
                        Text("Cancelar")
                    }
                },
            )
        }
    }
}
}
}

internal fun verifyPosModePassword(input: String): Boolean = input == "123"

@Composable
private fun AdminLimitsSectionToolbar(
    selectedSection: String,
    selectedLabel: String,
    onOpenPicker: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Área activa", style = MaterialTheme.typography.labelMedium, color = visual.colors.muted)
                Text(
                    if (selectedSection == "overview") "Resumen general" else selectedLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            FilterChip(
                selected = selectedSection != "overview",
                onClick = onOpenPicker,
                label = { Text("Cambiar") },
                leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun LimitEditorSheet(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    value: String,
    onSelectedKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Box {
                FilterChip(
                    selected = true,
                    onClick = { menuExpanded = true },
                    label = { Text(options.firstOrNull { it.first == selectedKey }?.second ?: "Seleccionar jugada") },
                    leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    options.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSelectedKeyChange(key)
                                menuExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Límite máximo") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                CompactActionButton(
                    label = "Guardar",
                    onClick = onSave,
                    tone = ActionTone.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun adminLimitEditorOptions(): List<Pair<String, String>> = listOf(
    "daySale" to "Venta diaria",
    "payout" to "Cobro de premios",
    "quiniela" to "Quiniela",
    "pale" to "Pale",
    "superPale" to "Super Pale",
    "tripleta" to "Tripleta",
    "pick3Straight" to "Pick 3 Straight",
    "pick3Box" to "Pick 3 Box",
    "pick4Straight" to "Pick 4 Straight",
    "pick4Box" to "Pick 4 Box",
)

private fun salesLimitEditorOptions(): List<Pair<String, String>> = adminLimitEditorOptions()

private fun readAdminLimitEditorValue(key: String, vararg values: String): String {
    val keys = adminLimitEditorOptions().map { it.first }
    return values.getOrNull(keys.indexOf(key)).orEmpty()
}

private fun readSalesLimitEditorValue(key: String, vararg values: String): String =
    readAdminLimitEditorValue(key, *values)

private fun writeAdminLimitEditorValue(key: String, value: String, onValue: (String, String) -> Unit) {
    onValue(key, value)
}

private fun writeSalesLimitEditorValue(key: String, value: String, onValue: (String, String) -> Unit) {
    onValue(key, value)
}

internal fun posModeActionLabel(): String = "Modo POS"

@Composable
private fun AdminLimitsDetailNavigation(
    title: String,
    scopeLabel: String,
    tone: Color,
    onBackToOverview: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBackToOverview)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = visual.colors.actionPrimarySurface,
            contentColor = visual.colors.actionPrimary,
        ) {
            Box(modifier = Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver al resumen de límites",
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Volver al resumen",
                style = MaterialTheme.typography.labelMedium,
                color = visual.colors.actionPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = visual.colors.ink,
                fontWeight = FontWeight.Bold,
            )
        }
        CompactStatusBadge(label = scopeLabel, tone = tone)
    }
}

@Composable
private fun AdminLimitsConfigurationProgress(
    configuredPoolRules: Int,
    totalPoolRules: Int,
) {
    val visual = rememberLotteryNetVisualSpec()
    val progress = (configuredPoolRules.toFloat() / totalPoolRules.coerceAtLeast(1)).coerceIn(0f, 1f)
    CompactPanel(alt = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Configuración del pool", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "$configuredPoolRules de $totalPoolRules jugadas con límite configurado",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CompactStatusBadge(
                label = "${(progress * 100).toInt()}%",
                tone = visual.colors.actionPrimary,
            )
        }
    }
}

@Composable
private fun AdminLimitsOverview(
    items: List<AdminLimitsOverviewItem>,
    onOpen: (AdminLimitsDestination) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel {
        OperationalListHeader(title = "Áreas disponibles", meta = "Resumen de reglas")
        Text(
            "Cada bloque controla un alcance distinto. Separa topes por jugada, por cajero y por caja para que no se mezclen.",
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.muted,
        )
        Text(
            "Pool = exposición global por lotería, número y jugada. Cajero = límite individual. Caja = cobros y recargas.",
            style = MaterialTheme.typography.labelMedium,
            color = visual.colors.ink,
        )
        Column {
            items.forEachIndexed { index, item ->
                val tone = when (item.tone) {
                    AdminLimitsTone.SUCCESS -> Color(0xFF059669)
                    AdminLimitsTone.WARNING -> Color(0xFFD97706)
                    AdminLimitsTone.NEUTRAL -> Color(0xFF64748B)
                    AdminLimitsTone.PRIMARY -> Color(0xFF2154D6)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(item.destination) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tone.copy(alpha = 0.12f),
                    ) {
                        Box(modifier = Modifier.padding(9.dp)) {
                            Icon(
                                imageVector = adminLimitsDestinationIcon(item.destination),
                                contentDescription = null,
                                tint = tone,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                item.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            CompactStatusBadge(label = item.scopeLabel, tone = tone)
                        }
                        Text(item.summary, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                        Text(item.effectiveValue, style = MaterialTheme.typography.labelMedium, color = tone)
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = "Abrir ${item.title}",
                        tint = visual.colors.muted,
                    )
                }
                if (index < items.lastIndex) {
                    HorizontalDivider(color = visual.colors.border.copy(alpha = 0.65f))
                }
            }
        }
    }
}

@Composable
private fun AdminLimitsScopeContext(
    title: String,
    summary: String,
    scopeLabel: String,
    tone: Color,
    destination: AdminLimitsDestination,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = tone.copy(alpha = 0.12f),
        ) {
            Box(modifier = Modifier.padding(9.dp)) {
                Icon(
                    imageVector = adminLimitsDestinationIcon(destination),
                    contentDescription = null,
                    tint = tone,
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                CompactStatusBadge(label = scopeLabel, tone = tone)
            }
            Text(summary, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
        }
    }
}

private fun adminLimitsSectionDestination(section: String): AdminLimitsDestination = when (section) {
    "pool" -> AdminLimitsDestination.POOL
    "cashiers" -> AdminLimitsDestination.CASHIERS
    "adminSelf" -> AdminLimitsDestination.ADMIN_SELF
    "cash" -> AdminLimitsDestination.CASH_AND_RECHARGES
    "system" -> AdminLimitsDestination.POS
    else -> AdminLimitsDestination.OVERVIEW
}

private fun adminLimitsDestinationIcon(destination: AdminLimitsDestination) = when (destination) {
    AdminLimitsDestination.OVERVIEW -> Icons.Rounded.Casino
    AdminLimitsDestination.POOL -> Icons.Rounded.Casino
    AdminLimitsDestination.CASHIERS -> Icons.Rounded.PointOfSale
    AdminLimitsDestination.ADMIN_SELF -> Icons.Rounded.Person
    AdminLimitsDestination.CASH_AND_RECHARGES -> Icons.Rounded.Payments
    AdminLimitsDestination.POS -> Icons.Rounded.PhoneAndroid
}

private fun AdminLimitsDestination.toSectionId(): String = when (this) {
    AdminLimitsDestination.OVERVIEW -> "overview"
    AdminLimitsDestination.POOL -> "pool"
    AdminLimitsDestination.CASHIERS -> "cashiers"
    AdminLimitsDestination.ADMIN_SELF -> "adminSelf"
    AdminLimitsDestination.CASH_AND_RECHARGES -> "cash"
    AdminLimitsDestination.POS -> "system"
}

@Composable
private fun AdminLimitsCompactHeader(onBack: () -> Unit) {
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
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Centro de límites", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Pool · usuarios · operación", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

internal fun adminLimitsSectionOptions(): List<QuickFilterChip> = listOf(
    QuickFilterChip("overview", "Resumen"),
    QuickFilterChip("pool", "Pool de banca"),
    QuickFilterChip("cashiers", "Límites de cajeros"),
    QuickFilterChip("adminSelf", "Límite propio del admin"),
    QuickFilterChip("cash", "Cobros y recargas"),
    QuickFilterChip("system", "Modo POS"),
)

private fun adminLimitsSectionSummary(section: String): String = when (section) {
    "adminSelf" -> "Topes propios del admin cuando vende."
    "pool" -> "Pool compartido por lotería, jugada y número; separado del cajero."
    "cashiers" -> "Venta diaria y límites por jugada para cada cajero."
    "cash" -> "Topes de cobro y recarga de la operación."
    "system" -> "Modo compacto de interfaz para equipos POS."
    else -> "Vista general de reglas activas."
}

private fun adminLimitsSectionScope(section: String): String = when (section) {
    "pool" -> "GLOBAL"
    "cashiers" -> "POR USUARIO"
    "adminSelf" -> "SOLO ADMIN"
    "cash" -> "OPERACIÓN"
    "system" -> "INTERFAZ"
    else -> "RESUMEN"
}

private fun adminLimitsSectionColor(section: String): Color = when (section) {
    "adminSelf" -> Color(0xFF2154D6)
    "pool" -> Color(0xFF059669)
    "cashiers" -> Color(0xFF2154D6)
    "cash" -> Color(0xFFD97706)
    "system" -> Color(0xFF64748B)
    else -> Color(0xFF2154D6)
}

private fun emptyCashierSalesLimitInputs(): CashierSalesLimitInputs = CashierSalesLimitInputs(
    daySale = 0.0,
    payout = 0.0,
    quiniela = 0.0,
    pale = 0.0,
    superPale = 0.0,
    tripleta = 0.0,
    pick3Straight = 0.0,
    pick3Box = 0.0,
    pick4Straight = 0.0,
    pick4Box = 0.0,
)

private fun formatLimit(value: Double): String {
    if (value <= 0.0) return ""
    return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}

private fun sanitizeLimit(value: String): String {
    var dotSeen = false
    return buildString {
        value.forEach { char ->
            when {
                char.isDigit() -> append(char)
                char == '.' && !dotSeen -> {
                    dotSeen = true
                    append(char)
                }
            }
        }
    }
}

private fun moneyLimit(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)

internal fun resolveDefaultSalesLimitsForServer(
    salesLimits: CashierSalesLimitInputs,
    adminLimits: AdminOperationalLimits,
): CashierSalesLimitInputs {
    val payout = salesLimits.payout.takeIf { it > 0.0 }
        ?: adminLimits.cashierPayoutLimit.takeIf { it > 0.0 }
        ?: 0.0
    return salesLimits.copy(payout = payout)
}
