package com.lotterynet.pro.ui.admin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.sync.CashierLimitCloudSyncCoordinator
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.CompactKeyValueRow
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactSegmentedSelector
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.MetricStrip
import com.lotterynet.pro.ui.common.MetricStripItem
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
        val cashierLimitCloudSync = CashierLimitCloudSyncCoordinator(cashierSalesLimitRepository)
        val serviceStore = SupabaseMasterConfigRemoteStore()
        val ownerId = session.userId
        setContent {
            LotteryNetComposeTheme {
                AdminLimitsRoute(
                    initialAdminLimits = adminLimitRepository.getLimits(),
                    initialRechargeLimits = rechargeLimitRepository.getSettings(),
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
                    onSave = { adminLimits, rechargeLimits, salesLimits, adminSelfLimits ->
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

@Composable
private fun AdminLimitsRoute(
    initialAdminLimits: AdminOperationalLimits,
    initialRechargeLimits: com.lotterynet.pro.core.storage.RechargeLimitSettings,
    initialSalesLimits: CashierSalesLimitInputs,
    initialAdminSelfLimits: CashierSalesLimitInputs?,
    initialPosModeEnabled: Boolean,
    onBack: () -> Unit,
    onSavePosMode: (Boolean) -> Unit,
    onSave: (AdminOperationalLimits, com.lotterynet.pro.core.storage.RechargeLimitSettings, CashierSalesLimitInputs, CashierSalesLimitInputs) -> Unit,
) {
    val adminSelfInitial = initialAdminSelfLimits ?: emptyCashierSalesLimitInputs()
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
    var selectedSection by rememberSaveable { mutableStateOf("adminSelf") }
    val visual = rememberLotteryNetVisualSpec()
    val salesLimitCopy = resolveCashierSalesLimitVisibilityContract(initialSalesLimits)
    val adminSelfContract = resolveAdminLimitScopeContract(
        selectedScope = AdminLimitScope.ADMIN_SELF,
        adminHasSelfLimits = initialAdminSelfLimits != null,
        cashierDefaultsEnabled = initialSalesLimits.daySale > 0.0,
    )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdminLimitsCompactHeader(onBack = onBack)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CompactStatusBadge(label = "Sincronizado", tone = Color(0xFF059669))
            }
            CompactSegmentedSelector(
                options = adminLimitsSectionOptions(),
                selectedId = selectedSection,
                onSelected = { selectedSection = it },
                columns = 3,
            )
            CompactPanel {
                OperationalListHeader(title = "Resumen", meta = "Topes activos")
                MetricStrip(
                    items = listOf(
                        MetricStripItem("Mi venta", initialAdminSelfLimits?.daySale?.takeIf { it > 0.0 }?.let(::moneyLimit) ?: "Sin tope", MaterialTheme.colorScheme.primary),
                        MetricStripItem("Cajeros", if (initialSalesLimits.daySale > 0.0) moneyLimit(initialSalesLimits.daySale) else "Sin tope", MaterialTheme.colorScheme.primary),
                        MetricStripItem("Pago cajero", if (initialAdminLimits.cashierPayoutLimit > 0.0) moneyLimit(initialAdminLimits.cashierPayoutLimit) else "Sin tope", MaterialTheme.colorScheme.primary),
                    ),
                )
            }
            if (selectedSection == "adminSelf") {
            CompactPanel(alt = true) {
                OperationalListHeader(title = adminSelfContract.title, meta = adminSelfContract.emptyStateCopy)
                CompactKeyValueRow(
                    label = "Estado",
                    value = if (initialAdminSelfLimits == null) "Sin tope" else "Con tope propio",
                    tone = if (initialAdminSelfLimits == null) MaterialTheme.colorScheme.primary else null,
                )
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
            if (selectedSection == "sales") {
            CompactPanel(alt = true) {
                OperationalListHeader(title = salesLimitCopy.title, meta = salesLimitCopy.meta)
                Text(
                    salesLimitCopy.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                )
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
                    meta = "Por tipo",
                )
                Text(
                    "Controla cuánto puede vender el cajero por tipo de jugada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
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
                OutlinedTextField(
                    value = payoutSalesLimit,
                    onValueChange = { payoutSalesLimit = sanitizeLimit(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Tope de cobro por cajero") },
                    leadingIcon = { Icon(Icons.Rounded.Payments, contentDescription = null) },
                    supportingText = { Text("No cambia la tabla de premios; solo limita cuánto puede cobrar.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            }
            if (selectedSection == "payments") {
            CompactPanel {
                OperationalListHeader(title = "Pagos", meta = "Cajeros")
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
            }
            if (selectedSection == "recharges") {
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
                OperationalListHeader(title = "Sistema", meta = "Pantalla POS")
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

internal fun verifyPosModePassword(input: String): Boolean = input == "123"

internal fun posModeActionLabel(): String = "Modo POS"

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
            Text("☰", modifier = Modifier.clickable(onClick = onBack), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Column(modifier = Modifier.weight(1f)) {
                Text("Límites", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Admin · Configuración compacta", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
            }
            Text("⋮", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}

internal fun adminLimitsSectionOptions(): List<QuickFilterChip> = listOf(
    QuickFilterChip("adminSelf", "Admin"),
    QuickFilterChip("sales", "Cajeros"),
    QuickFilterChip("payments", "Pagos"),
    QuickFilterChip("recharges", "Recargas"),
    QuickFilterChip("system", "POS"),
)

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
