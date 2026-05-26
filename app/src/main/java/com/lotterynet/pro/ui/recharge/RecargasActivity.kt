package com.lotterynet.pro.ui.recharge

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.OperationalFeedback
import com.lotterynet.pro.core.model.RechargeRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.remote.SupabaseEdgeException
import com.lotterynet.pro.core.recharge.recargasrapidas.RecargasRapidasBackendClient
import com.lotterynet.pro.core.recharge.recargasrapidas.RecargasRapidasPaqueticoInfo
import com.lotterynet.pro.core.recharge.recargasrapidas.RecargasRapidasProvider
import com.lotterynet.pro.core.recharge.recargasrapidas.RecargasRapidasPaqueticoPlan
import com.lotterynet.pro.core.recharge.recargasrapidas.recargasRapidasPaqueticoProviders
import com.lotterynet.pro.core.recharge.recargasrapidas.recargasRapidasProviders
import com.lotterynet.pro.core.recharge.recargasrapidas.sanitizeRecargasRapidasPhone
import com.lotterynet.pro.core.recharge.recargasrapidas.validateRecargasRapidasPhoneForProvider
import com.lotterynet.pro.core.printing.BluetoothThermalPrinter
import com.lotterynet.pro.core.printing.IntegratedThermalPrinter
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalRechargeLimitRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalThermalPrinterRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.RechargeLimitSettings
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.sync.NativeRechargeCloudSyncCoordinator
import com.lotterynet.pro.ui.common.*
import com.lotterynet.pro.ui.master.MasterDashboardActivity
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.navigation.startSafeNativeDestination
import com.lotterynet.pro.ui.shell.ShellActivity
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class RechargeLayoutContract(
    val showHeaderMetrics: Boolean,
    val mergeStatusIntoHeader: Boolean,
    val useDenseRows: Boolean,
    val inlineTotals: Boolean,
    val quickAmountRows: Int,
    val showSummaryCard: Boolean,
    val showInlineLimitSettings: Boolean,
    val formPaddingVerticalDp: Int,
    val providerCardPaddingVerticalDp: Int,
    val historyRowSpacingDp: Int,
)

internal enum class RechargeHistoryLayout {
    EMBEDDED_ROWS,
}

internal fun resolveRechargeHistoryLayout(): RechargeHistoryLayout = RechargeHistoryLayout.EMBEDDED_ROWS

internal data class RechargePaqueticoPlanContract(
    val id: Int,
    val description: String,
    val price: Double,
)

internal data class RechargeVoucherState(
    val record: RechargeRecord,
    val bancaName: String,
    val operatorName: String,
    val saleLabel: String,
    val providerNewBalance: Double?,
    val providerBillNumber: String?,
    val printerAvailable: Boolean,
)

internal data class RechargeFormState(
    val mode: RechargeSellMode,
    val phone: String,
    val amountText: String,
    val selectedPaqueticoPlan: RechargePaqueticoPlanContract?,
)

private data class PendingRechargeSale(
    val provider: RechargeProvider,
    val mode: RechargeSellMode,
    val phone: String,
    val amount: Double,
    val paqueticoPlan: RechargePaqueticoPlanContract?,
)

internal fun resolveRechargeLayout(windowMode: com.lotterynet.pro.ui.common.LotteryNetWindowMode): RechargeLayoutContract {
    return when (windowMode) {
        com.lotterynet.pro.ui.common.LotteryNetWindowMode.POS_TIGHT -> RechargeLayoutContract(
            showHeaderMetrics = false,
            mergeStatusIntoHeader = true,
            useDenseRows = true,
            inlineTotals = true,
            quickAmountRows = 1,
            showSummaryCard = false,
            showInlineLimitSettings = false,
            formPaddingVerticalDp = 7,
            providerCardPaddingVerticalDp = 7,
            historyRowSpacingDp = 5,
        )

        com.lotterynet.pro.ui.common.LotteryNetWindowMode.POS -> RechargeLayoutContract(
            showHeaderMetrics = false,
            mergeStatusIntoHeader = true,
            useDenseRows = true,
            inlineTotals = true,
            quickAmountRows = 1,
            showSummaryCard = false,
            showInlineLimitSettings = false,
            formPaddingVerticalDp = 8,
            providerCardPaddingVerticalDp = 8,
            historyRowSpacingDp = 6,
        )

        else -> RechargeLayoutContract(
            showHeaderMetrics = true,
            mergeStatusIntoHeader = false,
            useDenseRows = false,
            inlineTotals = true,
            quickAmountRows = 2,
            showSummaryCard = true,
            showInlineLimitSettings = false,
            formPaddingVerticalDp = 13,
            providerCardPaddingVerticalDp = 11,
            historyRowSpacingDp = 10,
        )
    }
}

internal fun canOpenRechargeForRole(role: UserRole): Boolean {
    return role == UserRole.ADMIN || role == UserRole.CASHIER
}

internal const val RECHARGE_LIMIT_PULL_INTERVAL_MS: Long = 60_000L

class RecargasActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, session?.role, NativeDestination.RECHARGE)) {
            return
        }
        checkNotNull(session)
        LocalUsersRepository(this).touchSession(session)
        val rechargeRepository = LocalRechargeRepository(this)
        val usersRepository = LocalUsersRepository(this)
        val rechargeLimitRepository = LocalRechargeLimitRepository(this)
        val rechargeCloudSyncCoordinator = NativeRechargeCloudSyncCoordinator(rechargeRepository)
        val serviceStore = SupabaseMasterConfigRemoteStore()
        val recargasRapidasBackend = RecargasRapidasBackendClient()
        if (!canShowRechargeAccess(resolveRechargeOwnerAccount(session, usersRepository))) {
            Toast.makeText(this, "Recargas bloqueadas por Master.", Toast.LENGTH_SHORT).show()
            startSafeNativeDestination(this, session.role, NativeDestination.SHELL_MENU)
            finish()
            return
        }
        val ownerKey = resolveAdminId(session) ?: session.userId
        thread(name = "native-recharge-hydrate") {
            rechargeCloudSyncCoordinator.hydrateOwner(ownerKey)
        }
        val dayKey = santoDomingoDayKey(System.currentTimeMillis())
        val initialRows = rechargeRepository
            .getRechargesForActor(dayKey, session.userId)
            .sortedByDescending { it.createdAtEpochMs }

        setContent {
            LotteryNetComposeTheme {
                RecargasRoute(
                    session = session,
                    initialRows = initialRows,
                    initialBalanceState = resolveRechargeBalanceState(session, usersRepository),
                    initialLimitSettings = rechargeLimitRepository.getSettings(),
                    onShare = { rows ->
                        shareRecharges(
                            session = session,
                            rows = rows,
                            whatsappOnly = false,
                        )
                    },
                    onWhatsApp = { rows ->
                        shareRecharges(
                            session = session,
                            rows = rows,
                            whatsappOnly = true,
                        )
                    },
                    onPrintVoucher = { voucher ->
                        printRechargeVoucher(voucher)
                    },
                    onShareVoucher = { voucher ->
                        shareRechargeVoucher(voucher)
                    },
                    onConsultPaqueticos = { provider, phone ->
                        val freshBearerToken = SupabaseSessionTokenProvider(
                            LocalSessionRepository(this),
                        ).freshAccessToken()
                        recargasRapidasBackend.getPaqueticosInfo(
                            JSONObject()
                                .put("userId", session.userId)
                                .put("username", session.username)
                                .put("role", session.role.name.lowercase(Locale.US))
                                .put("adminId", resolveAdminId(session))
                                .put("adminUser", resolveAdminUser(session))
                                .put("ownerAccountId", resolveRechargeBalanceState(session, usersRepository).ownerAccountId)
                                .put("providerId", provider.id)
                                .put("phone", sanitizeRecargasRapidasPhone(phone)),
                            bearerToken = freshBearerToken,
                        )
                    },
                    onSubmit = { provider, mode, phone, amount, paqueticoPlan, onRowsChanged, onSyncResult, onVoucherReady ->
                        val balanceState = resolveRechargeBalanceState(session, usersRepository)
                        val validation = validateRechargeSubmission(
                            amount = amount,
                            balanceState = balanceState,
                            limitSettings = rechargeLimitRepository.getSettings(),
                        )
                        if (validation != null) {
                            Toast.makeText(this, validation, Toast.LENGTH_SHORT).show()
                            onSyncResult(OperationalFeedback.error(validation))
                            return@RecargasRoute
                        }
                        onSyncResult(OperationalFeedback.syncPending("Procesando ${mode.label.lowercase(Locale.US)} en Recargas Rapidas..."))
                        thread(name = "native-recargas-rapidas-sale") {
                            val clientRequestId = "rec-${UUID.randomUUID()}"
                            val purchase = runCatching {
                                val freshBearerToken = SupabaseSessionTokenProvider(
                                    LocalSessionRepository(this),
                                ).freshAccessToken()
                                recargasRapidasBackend.executeSale(
                                    buildRecargasRapidasSaleRequestJson(
                                        session = session,
                                        ownerAccountId = balanceState.ownerAccountId,
                                        provider = provider.toContract(),
                                        mode = mode,
                                        phone = phone,
                                        amount = amount,
                                        paqueticoPlan = paqueticoPlan,
                                        clientRequestId = clientRequestId,
                                    ),
                                    bearerToken = freshBearerToken,
                                )
                            }
                            val error = purchase.exceptionOrNull()
                            if (error != null) {
                                runOnUiThread {
                                    val message = resolveRechargeSaleErrorMessage(error)
                                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                                    onSyncResult(OperationalFeedback.error(message))
                                }
                                return@thread
                            }
                            val now = System.currentTimeMillis()
                            val purchaseResponse = purchase.getOrThrow()
                            val rejection = validateRecargasRapidasSaleResponse(purchaseResponse)
                            if (rejection != null) {
                                runOnUiThread {
                                    Toast.makeText(this, rejection, Toast.LENGTH_LONG).show()
                                    onSyncResult(OperationalFeedback.error(rejection))
                                }
                                return@thread
                            }
                            val record = buildRechargeSaleRecord(
                                provider = provider.toContract(),
                                mode = mode,
                                phone = phone,
                                amount = amount,
                                userId = session.userId,
                                userName = session.username,
                                adminId = resolveAdminId(session),
                                adminUser = resolveAdminUser(session),
                                now = now,
                                id = purchaseResponse.optString("localId").ifBlank { clientRequestId },
                                status = "completed",
                                providerReference = extractRecargasRapidasReference(purchaseResponse),
                            )
                            discountRechargeBalance(session, usersRepository, amount)
                            rechargeRepository.saveRecharge(record)
                            val nextRows = rechargeRepository
                                .getRechargesForActor(dayKey, session.userId)
                                .sortedByDescending { it.createdAtEpochMs }
                            rechargeCloudSyncCoordinator.hydrateOwner(ownerKey)
                            val voucher = RechargeVoucherState(
                                record = record,
                                bancaName = session.banca.orEmpty(),
                                operatorName = session.username,
                                saleLabel = mode.label,
                                providerNewBalance = extractRecargasRapidasNewBalance(purchaseResponse),
                                providerBillNumber = extractRecargasRapidasBillNumber(purchaseResponse),
                                printerAvailable = resolveRechargeVoucherPrintTarget(
                                    integratedAvailable = IntegratedThermalPrinter.isAvailable(this),
                                    selectedBluetoothAddress = LocalThermalPrinterRepository(this).getPrefs().selectedPrinterAddress,
                                ) != RechargeVoucherPrintTarget.NONE,
                            )
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    "${mode.label} completado: ${provider.label} ${formatMoney(amount)}",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onRowsChanged(nextRows)
                                onSyncResult(OperationalFeedback.saved("${mode.label} completado por servidor."))
                                onVoucherReady(voucher)
                            }
                        }
                    },
                    onSaveLimits = { settings ->
                        thread(name = "recharge-limits-service-save") {
                            val ok = runCatching {
                                serviceStore.upsertJsonValue(
                                    "recharge_limits:$ownerKey",
                                    JSONObject().apply {
                                        put("globalPerTx", settings.globalPerTx.coerceAtLeast(0.0))
                                        put("masterPerTx", settings.masterPerTx.coerceAtLeast(0.0))
                                    }.toString(),
                                )
                                serviceStore.upsertJsonValue(
                                    "sys_master_limits_v1",
                                    JSONObject().apply {
                                        put("recarga", settings.masterPerTx.coerceAtLeast(0.0))
                                    }.toString(),
                                )
                                rechargeLimitRepository.saveSettings(settings)
                                true
                            }.getOrDefault(false)
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    if (ok) "Límites de recarga guardados en servidor" else "No se guardó: servidor no disponible",
                                    if (ok) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                        settings
                    },
                    onRefreshLimits = { onApplied ->
                        thread(name = "recharge-limits-live-pull") {
                            val latest = runCatching {
                                serviceStore.fetchValue("recharge_limits:$ownerKey")?.let { payload ->
                                    rechargeLimitRepository.cacheRemotePayload(payload)
                                }
                                rechargeLimitRepository.getSettings()
                            }.getOrDefault(rechargeLimitRepository.getSettings())
                            runOnUiThread { onApplied(latest) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RecargasRoute(
    session: ActiveSession,
    initialRows: List<RechargeRecord>,
    initialBalanceState: RechargeBalanceState,
    initialLimitSettings: RechargeLimitSettings,
    onShare: (List<RechargeRecord>) -> Unit,
    onWhatsApp: (List<RechargeRecord>) -> Unit,
    onPrintVoucher: (RechargeVoucherState) -> Unit,
    onShareVoucher: (RechargeVoucherState) -> Unit,
    onConsultPaqueticos: (RechargeProviderContract, String) -> RecargasRapidasPaqueticoInfo,
    onSubmit: (RechargeProvider, RechargeSellMode, String, Double, RechargePaqueticoPlanContract?, (List<RechargeRecord>) -> Unit, (OperationalFeedback) -> Unit, (RechargeVoucherState) -> Unit) -> Unit,
    onSaveLimits: (RechargeLimitSettings) -> RechargeLimitSettings,
    onRefreshLimits: ((RechargeLimitSettings) -> Unit) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val layout = resolveRechargeLayout(visual.windowMode)
    var selectedProvider by rememberSaveable { mutableStateOf(RECHARGE_PROVIDERS.first().id) }
    var selectedMode by rememberSaveable { mutableStateOf(RechargeSellMode.RECARGA) }
    var phone by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var paqueticoPlans by remember { mutableStateOf<List<RechargePaqueticoPlanContract>>(emptyList()) }
    var selectedPaqueticoPlanId by rememberSaveable { mutableStateOf<Int?>(null) }
    var rows by remember { mutableStateOf(initialRows) }
    var balanceState by remember { mutableStateOf(initialBalanceState) }
    var limitSettings by remember { mutableStateOf(initialLimitSettings) }
    var feedback by remember { mutableStateOf(OperationalFeedback.idle("Lista para registrar una recarga.")) }
    var consultingPaqueticos by remember { mutableStateOf(false) }
    var pendingVoucher by remember { mutableStateOf<RechargeVoucherState?>(null) }
    var pendingRechargeSale by remember { mutableStateOf<PendingRechargeSale?>(null) }
    val statusMessage = feedback.message
    val activeProviders = providersForMode(selectedMode)
    val scope = rememberCoroutineScope()

    LaunchedEffect(session.userId, session.adminId) {
        while (true) {
            onRefreshLimits { latest ->
                limitSettings = latest
            }
            delay(RECHARGE_LIMIT_PULL_INTERVAL_MS)
        }
    }

    LaunchedEffect(selectedMode) {
        if (activeProviders.none { it.id == selectedProvider }) {
            selectedProvider = activeProviders.first().id
        }
        amountText = ""
        paqueticoPlans = emptyList()
        selectedPaqueticoPlanId = null
    }

    val provider = activeProviders.firstOrNull { it.id == selectedProvider } ?: activeProviders.first()
    val selectedPaqueticoPlan = paqueticoPlans.firstOrNull { it.id == selectedPaqueticoPlanId }
    val formState = RechargeFormState(
        mode = selectedMode,
        phone = phone,
        amountText = amountText,
        selectedPaqueticoPlan = selectedPaqueticoPlan,
    )
    val parsedAmount = resolveRechargeFormAmount(formState)
    val actorTotal = rows.sumOf { it.amount }
    val actorCount = rows.size

    val context = androidx.compose.ui.platform.LocalContext.current
    fun submitRechargeSale(sale: PendingRechargeSale) {
        feedback = OperationalFeedback.syncPending("${sale.mode.label} procesando en Recargas Rapidas...")
        onSubmit(
            sale.provider,
            sale.mode,
            sale.phone,
            sale.amount,
            sale.paqueticoPlan,
            { nextRows ->
                rows = nextRows
                val updatedBalance = (balanceState.availableBalance - sale.amount).coerceAtLeast(0.0)
                balanceState = balanceState.copy(availableBalance = updatedBalance)
                phone = ""
                amountText = ""
                paqueticoPlans = emptyList()
                selectedPaqueticoPlanId = null
            },
            { result ->
                feedback = result
            },
        ) { voucher ->
            pendingVoucher = voucher
            feedback = OperationalFeedback.saved(
                if (voucher.printerAvailable) {
                    "Voucher listo. Puedes imprimirlo o compartirlo."
                } else {
                    "Voucher listo para compartir. No hay impresora configurada."
                },
            )
        }
    }
    pendingRechargeSale?.let { sale ->
        RechargeConfirmDialog(
            sale = sale,
            onConfirm = {
                pendingRechargeSale = null
                submitRechargeSale(sale)
            },
            onDismiss = {
                pendingRechargeSale = null
            },
        )
    }
    pendingVoucher?.let { voucher ->
        RechargeVoucherDialog(
            voucher = voucher,
            onPrint = {
                onPrintVoucher(voucher)
                pendingVoucher = null
            },
            onShare = {
                onShareVoucher(voucher)
                pendingVoucher = null
            },
            onDismiss = {
                pendingVoucher = null
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = visual.sizes.screenPaddingH,
                end = visual.sizes.screenPaddingH,
                top = visual.sizes.screenPaddingV,
                bottom = visual.sizes.screenPaddingV + 10.dp,
            ) + innerPadding,
            verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
        ) {
            item {
                AppTopBar(
                    spec = ScreenChromeSpec(
                        title = "Recargas Móviles",
                        subtitle = "Registro diario por operador",
                        activeBottomTab = NativeBottomTab.MENU,
                    ),
                    onOpenMenu = { com.lotterynet.pro.ui.common.openShellMenu(context) },
                )
            }
            item {
                RecargasHeader(
                    session = session,
                    actorCount = actorCount,
                    actorTotal = actorTotal,
                    balanceState = balanceState,
                    statusMessage = if (layout.mergeStatusIntoHeader) statusMessage else null,
                    showMetrics = layout.showHeaderMetrics,
                    onShare = { onShare(rows) },
                    onWhatsApp = { onWhatsApp(rows) },
                )
            }
            if (!layout.mergeStatusIntoHeader) {
                item {
                    StatusStrip(text = statusMessage)
                }
            }
            item {
                ProviderPicker(
                    layout = layout,
                    providers = activeProviders,
                    selectedProvider = selectedProvider,
                    onSelect = {
                        selectedProvider = it
                        feedback = OperationalFeedback.idle("Proveedor listo: ${activeProviders.first { row -> row.id == it }.label}")
                    },
                )
            }
            item {
                CompactPanel(contentPadding = PaddingValues(horizontal = 10.dp, vertical = layout.formPaddingVerticalDp.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProviderLogo(
                            providerId = provider.id,
                            fallback = provider.label,
                            modifier = Modifier.size(34.dp),
                            tintColor = Color.Transparent,
                        )
                        SectionHeader(title = selectedMode.formTitle, meta = provider.label)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rechargeSellModes().forEach { mode ->
                            CompactActionButton(
                                label = mode.label,
                                onClick = {
                                    selectedMode = mode
                                    feedback = OperationalFeedback.idle("${mode.label} listo: ${provider.label}")
                                },
                                modifier = Modifier.weight(1f),
                                active = selectedMode == mode,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { raw ->
                            phone = raw.filter(Char::isDigit).take(15)
                            paqueticoPlans = emptyList()
                            selectedPaqueticoPlanId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Teléfono") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    if (selectedMode == RechargeSellMode.RECARGA) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { raw ->
                                amountText = raw.filterIndexed { index, char ->
                                    char.isDigit() || (char == '.' && index > 0 && !raw.take(index).contains('.'))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Monto") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        QuickAmountRow(
                            values = resolveRechargeQuickAmounts(provider.id),
                            onSelect = {
                                amountText = stripDecimal(it)
                                feedback = OperationalFeedback.idle("Monto rápido: ${formatMoney(it)}")
                            },
                        )
                        if (layout.quickAmountRows > 1) {
                            QuickAmountRow(
                                values = listOf(300.0, 500.0, 1000.0, 1500.0),
                                onSelect = {
                                    amountText = stripDecimal(it)
                                    feedback = OperationalFeedback.idle("Monto rápido: ${formatMoney(it)}")
                                },
                            )
                        }
                    } else {
                        CompactActionButton(
                            label = if (consultingPaqueticos) "Consultando..." else "Consultar paqueticos",
                            onClick = onConsult@{
                                val rrProvider = recargasRapidasPaqueticoProviders().first { it.id == provider.id }
                                val validation = validateRecargasRapidasPhoneForProvider(rrProvider, phone)
                                if (validation != null) {
                                    feedback = OperationalFeedback.error(validation)
                                    return@onConsult
                                }
                                consultingPaqueticos = true
                                feedback = OperationalFeedback.syncPending("Consultando paqueticos y verificando numero...")
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            onConsultPaqueticos(provider.toContract(), phone)
                                        }
                                    }
                                    consultingPaqueticos = false
                                    result.onSuccess { info ->
                                        paqueticoPlans = info.plans.map { it.toContract() }
                                        selectedPaqueticoPlanId = paqueticoPlans.firstOrNull()?.id
                                        feedback = if (info.error) {
                                            OperationalFeedback.error(info.message)
                                        } else {
                                            OperationalFeedback.saved("Numero verificado. ${paqueticoPlans.size} paqueticos disponibles.")
                                        }
                                    }.onFailure { error ->
                                        paqueticoPlans = emptyList()
                                        selectedPaqueticoPlanId = null
                                        feedback = OperationalFeedback.error(error.message ?: "No se pudo consultar Recargas Rapidas.")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            active = true,
                            icon = Icons.Rounded.PhoneAndroid,
                        )
                        PaqueticoPlanList(
                            plans = paqueticoPlans,
                            selectedPlanId = selectedPaqueticoPlanId,
                            onSelect = { plan ->
                                selectedPaqueticoPlanId = plan.id
                                feedback = OperationalFeedback.idle("Paquetico elegido: ${plan.description} ${formatMoney(plan.price)}")
                            },
                        )
                    }
                }
            }
            if (layout.showSummaryCard) {
                item {
                    SummaryCard(
                        provider = provider,
                        phone = phone,
                        amountText = amountText,
                        parsedAmount = parsedAmount,
                    )
                }
            }
            item {
                CompactActionButton(
                    label = "Registrar ${selectedMode.label.lowercase(Locale.US)}",
                    onClick = {
                        val cleanPhone = phone.trim()
                        val amount = parsedAmount
                        val validation = validateRechargeForm(formState) ?: validateRecharge(cleanPhone, amount)
                        if (validation != null) {
                            feedback = OperationalFeedback.error(validation)
                        } else {
                            pendingRechargeSale = PendingRechargeSale(
                                provider,
                                selectedMode,
                                cleanPhone,
                                amount,
                                selectedPaqueticoPlan,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    active = true,
                    icon = Icons.Rounded.SyncAlt,
                )
            }
            item {
                CompactPanel(contentPadding = PaddingValues(0.dp)) {
                    Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp)) {
                        SectionHeader(title = "Historial del día", meta = "$actorCount recargas · ${formatMoney(actorTotal)}")
                    }
                    if (rows.isEmpty()) {
                        CompactEmptyState("Sin recargas locales del operador hoy.")
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(layout.historyRowSpacingDp.dp),
                        ) {
                            when (resolveRechargeHistoryLayout()) {
                                RechargeHistoryLayout.EMBEDDED_ROWS -> rows.forEach { row ->
                                    RechargeHistoryRow(row = row, layout = layout)
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
private fun ProviderPicker(
    layout: RechargeLayoutContract,
    providers: List<RechargeProvider>,
    selectedProvider: String,
    onSelect: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val columns = when (visual.windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> 2
        LotteryNetWindowMode.POS -> 3
        else -> 3
    }
    CompactPanel {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionHeader(title = "Compañía", meta = selectedProvider.uppercase(Locale.getDefault()))
            CompactAdaptiveGrid(
                itemCount = providers.size,
                columns = columns,
                horizontalSpacing = 6.dp,
                verticalSpacing = 6.dp,
            ) { index, itemModifier ->
                val provider = providers[index]
                val selected = provider.id == selectedProvider
                RechargeProviderCard(
                    provider = provider,
                    selected = selected,
                    layout = layout,
                    modifier = itemModifier,
                    onClick = { onSelect(provider.id) },
                )
            }
        }
    }
}

@Composable
private fun RechargeProviderCard(
    provider: RechargeProvider,
    selected: Boolean,
    layout: RechargeLayoutContract,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    val borderColor = if (selected) provider.tint else visual.colors.border
    val backgroundColor = if (selected) provider.tint.copy(alpha = 0.10f) else visual.colors.panelAlt
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = layout.providerCardPaddingVerticalDp.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderLogo(
                providerId = provider.id,
                fallback = provider.label,
                modifier = Modifier.size(24.dp),
                tintColor = Color.Transparent,
            )
            Text(
                text = provider.label,
                style = MaterialTheme.typography.bodyMedium,
                color = visual.colors.ink,
            )
        }
    }
}

@Composable
private fun SummaryCard(
    provider: RechargeProvider,
    phone: String,
    amountText: String,
    parsedAmount: Double?,
) {
    CompactPanel(alt = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ProviderLogo(providerId = provider.id, fallback = provider.label, modifier = Modifier.size(20.dp), tintColor = Color.Transparent)
                SectionHeader(title = "Resumen", meta = provider.label)
            }
            CompactKeyValueRow(label = "Teléfono", value = phone.ifBlank { "Pendiente" })
            CompactKeyValueRow(label = "Monto", value = amountText.takeIf { it.isNotBlank() }?.let { formatMoney(parsedAmount ?: 0.0) } ?: "Pendiente")
        }
    }
}

@Composable
private fun PaqueticoPlanList(
    plans: List<RechargePaqueticoPlanContract>,
    selectedPlanId: Int?,
    onSelect: (RechargePaqueticoPlanContract) -> Unit,
) {
    if (plans.isEmpty()) {
        CompactEmptyState("Consulta el numero para ver paqueticos disponibles.")
        return
    }
    val visual = rememberLotteryNetVisualSpec()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        plans.forEach { plan ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = if (plan.id == selectedPlanId) gainColor().copy(alpha = 0.10f) else visual.colors.panelAlt,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (plan.id == selectedPlanId) gainColor() else visual.colors.border,
                ),
                onClick = { onSelect(plan) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = plan.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = visual.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatMoney(plan.price),
                        style = MaterialTheme.typography.labelLarge,
                        color = gainColor(),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun RechargeHistoryRow(row: RechargeRecord, layout: RechargeLayoutContract) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        modifier = Modifier.padding(horizontal = 12.dp),
        alt = true,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = layout.formPaddingVerticalDp.dp),
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
                ProviderLogo(
                    providerId = row.providerId,
                    fallback = row.providerName ?: "RG",
                    modifier = Modifier.size(28.dp),
                    tintColor = Color.Transparent,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = row.providerName ?: "Recarga",
                        style = MaterialTheme.typography.titleSmall,
                        color = visual.colors.ink,
                    )
                    Text(
                        text = "${row.phoneNumber ?: "-"} · ${formatStamp(row.createdAtEpochMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                }
            }
            Text(
                text = formatMoney(row.amount),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                color = visual.colors.gain,
            )
        }
    }
}

@Composable
private fun RechargeConfirmDialog(
    sale: PendingRechargeSale,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val planLabel = sale.paqueticoPlan?.description?.takeIf { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirmar ${sale.mode.label.lowercase(Locale.US)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Revisa bien antes de comprar.")
                CompactKeyValueRow(label = "Compañía", value = sale.provider.label)
                CompactKeyValueRow(label = "Número", value = sale.phone)
                CompactKeyValueRow(label = "Monto", value = formatMoney(sale.amount))
                if (planLabel != null) {
                    CompactKeyValueRow(label = "Paquetico", value = planLabel)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Sí, comprar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun RechargeVoucherDialog(
    voucher: RechargeVoucherState,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voucher de recarga") },
        text = {
            Text(
                text = if (voucher.printerAvailable) {
                    "${voucher.saleLabel} completado. Quieres imprimir el voucher de la venta?"
                } else {
                    "${voucher.saleLabel} completado. No hay impresora configurada; puedes compartir el voucher al cliente."
                },
            )
        },
        confirmButton = {
            if (voucher.printerAvailable) {
                TextButton(onClick = onPrint) {
                    Text("Imprimir")
                }
            } else {
                TextButton(onClick = onShare) {
                    Text("Compartir")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (voucher.printerAvailable) {
                    TextButton(onClick = onShare) {
                        Text("Compartir")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        },
    )
}

@Composable
private fun RecargasHeader(
    session: ActiveSession,
    actorCount: Int,
    actorTotal: Double,
    balanceState: RechargeBalanceState,
    statusMessage: String?,
    showMetrics: Boolean,
    onShare: () -> Unit,
    onWhatsApp: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 8.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = session.username,
                        style = MaterialTheme.typography.labelLarge,
                        color = visual.colors.ink,
                    )
                    Text(
                        text = buildString {
                            session.banca?.takeIf { it.isNotBlank() }?.let {
                                append(it)
                                append(" · ")
                            }
                            append("$actorCount recargas")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                }
                Text(
                    text = formatCompactMoney(balanceState.availableBalance),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (balanceState.availableBalance > 0) gainColor() else lossColor(),
                )
            }
            Text(
                text = "Queda ${formatCompactMoney(balanceState.availableBalance)} de ${formatCompactMoney(balanceState.assignedBalance)} asignado por Master",
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.muted,
            )
            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = visual.colors.muted,
                )
            }
            if (showMetrics) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    HeaderMetric(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Wallet,
                        label = "Monto hoy",
                        value = formatMoney(actorTotal),
                    )
                    HeaderMetric(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.PhoneAndroid,
                        label = "Operaciones",
                        value = actorCount.toString(),
                    )
                    HeaderMetric(
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Wallet,
                        label = "Disponible",
                        value = formatCompactMoney(balanceState.availableBalance),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactActionButton(label = "WhatsApp", onClick = onWhatsApp, icon = Icons.Rounded.Whatsapp, modifier = Modifier.weight(1f))
                CompactActionButton(label = "Compartir", onClick = onShare, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f), active = true)
            }
        }
    }
}

@Composable
private fun HeaderMetric(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = modifier
            .background(visual.colors.panelAlt, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = visual.colors.ink,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.muted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = visual.colors.ink,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun StatusStrip(text: String) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(alt = true) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = visual.colors.ink,
        )
    }
}

@Composable
private fun RechargeLimitsCard(
    settings: RechargeLimitSettings,
    onSave: (RechargeLimitSettings) -> Unit,
) {
    var globalValue by rememberSaveable(settings.globalPerTx) { mutableStateOf(formatLimitInput(settings.globalPerTx)) }
    CompactPanel(alt = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = "Tope de recarga", meta = "Un solo control")
            OutlinedTextField(
                value = globalValue,
                onValueChange = { globalValue = sanitizeDecimal(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Tope por recarga") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            CompactActionButton(
                label = "Guardar tope",
                onClick = {
                    val value = globalValue.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    onSave(
                        RechargeLimitSettings(
                            globalPerTx = value,
                            masterPerTx = value,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Wallet,
            )
        }
    }
}

@Composable
private fun QuickAmountRow(
    values: List<Double>,
    onSelect: (Double) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { amount ->
            CompactActionButton(
                label = formatCompactMoney(amount),
                onClick = { onSelect(amount) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private data class RechargeProvider(
    val id: String,
    val label: String,
    val tint: Color,
    val minimumAmount: Double,
    val logoAsset: String,
)

internal data class RechargeProviderContract(
    val id: String,
    val label: String,
    val minimumAmount: Double,
    val logoAsset: String,
)

internal enum class RechargeSellMode {
    RECARGA,
    PAQUETICO,
}

internal val RechargeSellMode.label: String
    get() = when (this) {
        RechargeSellMode.RECARGA -> "Recarga"
        RechargeSellMode.PAQUETICO -> "Paquetico"
    }

internal val RechargeSellMode.productType: String
    get() = when (this) {
        RechargeSellMode.RECARGA -> "recarga"
        RechargeSellMode.PAQUETICO -> "paquetico"
    }

internal val RechargeSellMode.formTitle: String
    get() = when (this) {
        RechargeSellMode.RECARGA -> "Nueva recarga"
        RechargeSellMode.PAQUETICO -> "Nuevo paquetico"
    }

internal fun rechargeSellModes(): List<RechargeSellMode> {
    return listOf(RechargeSellMode.RECARGA, RechargeSellMode.PAQUETICO)
}

internal fun rechargeProviderContracts(): List<RechargeProviderContract> {
    return RECHARGE_PROVIDERS.map { provider ->
        provider.toContract()
    }
}

internal fun paqueticoProviderContracts(): List<RechargeProviderContract> {
    return PAQUETICO_PROVIDERS.map { provider ->
        provider.toContract()
    }
}

private fun providersForMode(mode: RechargeSellMode): List<RechargeProvider> {
    return when (mode) {
        RechargeSellMode.RECARGA -> RECHARGE_PROVIDERS
        RechargeSellMode.PAQUETICO -> PAQUETICO_PROVIDERS
    }
}

private fun RechargeProvider.toContract(): RechargeProviderContract {
    return RechargeProviderContract(
        id = id,
        label = label,
        minimumAmount = minimumAmount,
        logoAsset = logoAsset,
    )
}

internal fun buildRechargeSaleRecord(
    provider: RechargeProviderContract,
    mode: RechargeSellMode,
    phone: String,
    amount: Double,
    userId: String?,
    userName: String?,
    adminId: String?,
    adminUser: String?,
    now: Long,
    id: String,
    status: String = "pending",
    providerReference: String? = null,
): RechargeRecord {
    return RechargeRecord(
        id = id,
        providerId = provider.id,
        providerName = provider.label,
        phoneNumber = sanitizeRecargasRapidasPhone(phone),
        amount = amount,
        productType = mode.productType,
        status = status,
        providerReference = providerReference,
        userId = userId,
        userName = userName,
        adminId = adminId,
        adminUser = adminUser,
        createdAtEpochMs = now,
    )
}

internal fun validateRechargeForm(state: RechargeFormState): String? {
    val cleanPhone = sanitizeRecargasRapidasPhone(state.phone)
    if (cleanPhone.length < 8) return "Telefono invalido. Debe tener al menos 8 digitos."
    return when (state.mode) {
        RechargeSellMode.RECARGA -> {
            if ((state.amountText.toDoubleOrNull() ?: 0.0) <= 0.0) {
                "Monto invalido. Escribe o elige un monto mayor que cero."
            } else {
                null
            }
        }
        RechargeSellMode.PAQUETICO -> {
            if (state.selectedPaqueticoPlan == null) {
                "Consulta y elige un paquetico primero."
            } else {
                null
            }
        }
    }
}

internal fun resolveRechargeFormAmount(state: RechargeFormState): Double {
    return when (state.mode) {
        RechargeSellMode.RECARGA -> state.amountText.toDoubleOrNull() ?: 0.0
        RechargeSellMode.PAQUETICO -> state.selectedPaqueticoPlan?.price ?: 0.0
    }
}

internal fun resolveRechargeQuickAmounts(providerId: String): List<Double> {
    val minimum = RECHARGE_PROVIDERS
        .firstOrNull { it.id == providerId }
        ?.minimumAmount
        ?: 0.0
    val base = listOf(50.0, 100.0, 200.0, 500.0)
        .filter { it >= minimum }
    val amounts = if (minimum > 0.0 && minimum < 50.0) {
        listOf(minimum) + base
    } else {
        base
    }
    return amounts.distinct().take(4)
}

private fun RecargasRapidasPaqueticoPlan.toContract(): RechargePaqueticoPlanContract {
    return RechargePaqueticoPlanContract(
        id = id,
        description = description,
        price = price,
    )
}

internal data class RechargeBalanceState(
    val enabled: Boolean,
    val ownerAccountId: String?,
    val ownerLabel: String,
    val assignedBalance: Double,
    val availableBalance: Double,
    val cashierTxLimit: Double?,
    val blockedByMaster: Boolean = false,
)

private val RECHARGE_PROVIDERS = recargasRapidasProviders().map { provider ->
    provider.toRechargeProvider()
}

private val PAQUETICO_PROVIDERS = recargasRapidasPaqueticoProviders().map { provider ->
    provider.toRechargeProvider()
}

private fun RecargasRapidasProvider.toRechargeProvider(): RechargeProvider {
    return RechargeProvider(
        id = id,
        label = label,
        tint = when (id) {
            "claro" -> Color(0xFFD32F2F)
            "altice" -> Color(0xFFFF7A00)
            "viva" -> Color(0xFF7B1FA2)
            "moun" -> Color(0xFF00897B)
            "wind" -> Color(0xFF00838F)
            "digicel" -> Color(0xFFC62828)
            "natcom" -> Color(0xFF1565C0)
            else -> Color(0xFF455A64)
        },
        minimumAmount = minimumAmount,
        logoAsset = logoAsset,
    )
}

private fun resolveAdminId(session: ActiveSession): String? {
    return when (session.role) {
        UserRole.ADMIN -> session.userId
        UserRole.CASHIER -> session.adminId
        else -> session.adminId
    }
}

private fun resolveAdminUser(session: ActiveSession): String? {
    return when (session.role) {
        UserRole.ADMIN -> session.username
        UserRole.CASHIER -> session.adminUser
        else -> session.adminUser
    }
}

internal fun resolveRechargeOwnerAccount(
    session: ActiveSession,
    usersRepository: LocalUsersRepository,
): UserAccount? {
    return when (session.role) {
        UserRole.ADMIN -> usersRepository.findByIdOrUser(session.userId)
            ?: usersRepository.findByIdOrUser(session.username)
        UserRole.CASHIER -> session.adminId?.let(usersRepository::findByIdOrUser)
            ?: session.adminUser?.let(usersRepository::findByIdOrUser)
        else -> null
    }
}

internal fun canShowRechargeAccess(ownerAccount: UserAccount?): Boolean {
    return ownerAccount?.active != false && ownerAccount?.rechargesEnabled == true
}

internal fun buildRecargasRapidasSaleRequestJson(
    session: ActiveSession,
    ownerAccountId: String?,
    provider: RechargeProviderContract,
    mode: RechargeSellMode,
    phone: String,
    amount: Double,
    paqueticoPlan: RechargePaqueticoPlanContract?,
    clientRequestId: String,
): JSONObject {
    return JSONObject().apply {
        put("clientRequestId", clientRequestId)
        put("userId", session.userId)
        put("username", session.username)
        put("role", session.role.name.lowercase(Locale.US))
        put("adminId", resolveAdminId(session))
        put("adminUser", resolveAdminUser(session))
        put("ownerAccountId", ownerAccountId)
        put("banca", session.banca)
        put("providerId", provider.id)
        put("providerLabel", provider.label)
        put("mode", mode.name.lowercase(Locale.US))
        put("phone", sanitizeRecargasRapidasPhone(phone))
        put("amount", amount.coerceAtLeast(0.0))
        paqueticoPlan?.let {
            put("paqueticoPlanId", it.id)
            put("paqueticoDescription", it.description)
            put("paqueticoPrice", it.price)
        }
    }
}

internal fun extractRecargasRapidasReference(response: JSONObject): String? {
    return listOf("reference", "transactionId", "refillId", "id", "orderId")
        .firstNotNullOfOrNull { key -> response.optString(key).takeIf { it.isNotBlank() } }
}

internal fun extractRecargasRapidasNewBalance(response: JSONObject): Double? {
    if (!response.has("newBalance") || response.isNull("newBalance")) return null
    return response.optDouble("newBalance").takeIf { !it.isNaN() }
}

internal fun extractRecargasRapidasBillNumber(response: JSONObject): String? {
    if (!response.has("billNumber") || response.isNull("billNumber")) return null
    return response.optString("billNumber").takeIf { it.isNotBlank() }
}

internal fun validateRecargasRapidasSaleResponse(response: JSONObject): String? {
    val explicitFailure = listOf("ok", "success", "confirmed")
        .any { key -> response.has(key) && !response.optBoolean(key, true) }
    val status = response.optString("status").lowercase(Locale.US)
    val failedStatus = status in setOf("failed", "error", "rejected", "declined", "cancelled", "canceled")
    if (!explicitFailure && !failedStatus) return null
    return response.optString("message")
        .ifBlank { response.optString("error") }
        .ifBlank { "Recarga no confirmada por proveedor" }
}

internal fun resolveRechargeSaleErrorMessage(error: Throwable): String {
    return when (error) {
        is SupabaseEdgeException -> error.userMessage
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Error procesando recarga"
    }
}

internal enum class RechargeVoucherPrintTarget {
    BLUETOOTH,
    INTEGRATED,
    NONE,
}

internal fun resolveRechargeVoucherPrintTarget(
    integratedAvailable: Boolean,
    selectedBluetoothAddress: String?,
): RechargeVoucherPrintTarget {
    return when {
        !selectedBluetoothAddress.isNullOrBlank() -> RechargeVoucherPrintTarget.BLUETOOTH
        integratedAvailable -> RechargeVoucherPrintTarget.INTEGRATED
        else -> RechargeVoucherPrintTarget.NONE
    }
}

private fun resolveRechargeBalanceState(
    session: ActiveSession,
    usersRepository: LocalUsersRepository,
): RechargeBalanceState {
    val ownerAccount = resolveRechargeOwnerAccount(session, usersRepository)
    val cashierAccount = if (session.role == UserRole.CASHIER) {
        usersRepository.findByIdOrUser(session.userId) ?: usersRepository.findByIdOrUser(session.username)
    } else {
        null
    }
    return resolveRechargeAccessState(
        ownerAccount = ownerAccount,
        cashierAccount = cashierAccount,
        fallbackLabel = session.username,
    )
}

internal fun resolveRechargeAccessState(
    ownerAccount: UserAccount?,
    cashierAccount: UserAccount?,
    fallbackLabel: String,
): RechargeBalanceState {
    return RechargeBalanceState(
        enabled = canShowRechargeAccess(ownerAccount),
        ownerAccountId = ownerAccount?.id,
        ownerLabel = ownerAccount?.displayName ?: ownerAccount?.user ?: fallbackLabel,
        assignedBalance = ownerAccount?.let { account ->
            if (account.rechargesAssignedBalance > 0.0) account.rechargesAssignedBalance else account.rechargesBalance
        } ?: 0.0,
        availableBalance = ownerAccount?.rechargesBalance ?: 0.0,
        cashierTxLimit = cashierAccount?.recargaTxLimit,
        blockedByMaster = ownerAccount?.active == false,
    )
}

internal fun validateRechargeSubmission(
    amount: Double,
    balanceState: RechargeBalanceState,
    limitSettings: RechargeLimitSettings,
): String? {
    val fundDebitAmount = resolveRechargeFundDebitAmount(amount)
    if (!balanceState.enabled) {
        return "Recargas bloqueadas por Master para ${balanceState.ownerLabel}."
    }
    val masterLimit = limitSettings.masterPerTx
    if (masterLimit > 0.0 && amount > masterLimit) {
        return "Tope master de recarga: ${formatMoney(masterLimit)}"
    }
    val globalLimit = limitSettings.globalPerTx
    if (globalLimit > 0.0 && amount > globalLimit) {
        return "Límite global de recarga: ${formatMoney(globalLimit)}"
    }
    if (balanceState.availableBalance <= 0.0) {
        return "Saldo de recargas agotado para ${balanceState.ownerLabel}."
    }
    if (fundDebitAmount > balanceState.availableBalance) {
        return "Saldo insuficiente. Disponible: ${formatMoney(balanceState.availableBalance)}"
    }
    val txLimit = balanceState.cashierTxLimit ?: 0.0
    if (txLimit > 0.0 && amount > txLimit) {
        return "Tope de recarga del cajero: ${formatMoney(txLimit)}"
    }
    return null
}

internal fun debitRechargeOwnerBalance(
    ownerAccount: UserAccount,
    amount: Double,
): UserAccount {
    return ownerAccount.copy(
        rechargesBalance = (ownerAccount.rechargesBalance - resolveRechargeFundDebitAmount(amount)).coerceAtLeast(0.0),
        updatedAtEpochMs = System.currentTimeMillis(),
    )
}

internal fun resolveRechargeFundDebitAmount(amount: Double): Double {
    return (amount.coerceAtLeast(0.0) * RECHARGE_FUND_DEBIT_RATE)
}

private const val RECHARGE_FUND_DEBIT_RATE: Double = 0.95

internal fun publishRechargeBalancePayload(
    exportPayload: () -> String,
    publishPayload: (String) -> Unit,
): Boolean {
    return runCatching {
        publishPayload(exportPayload())
    }.isSuccess
}

private fun discountRechargeBalance(
    session: ActiveSession,
    usersRepository: LocalUsersRepository,
    amount: Double,
) {
    val ownerAccount = when (session.role) {
        UserRole.ADMIN -> usersRepository.findByIdOrUser(session.userId)
            ?: usersRepository.findByIdOrUser(session.username)
        UserRole.CASHIER -> session.adminId?.let(usersRepository::findByIdOrUser)
            ?: session.adminUser?.let(usersRepository::findByIdOrUser)
        else -> null
    } ?: return
    usersRepository.updateAccount(
        debitRechargeOwnerBalance(ownerAccount, amount),
    )
}

private fun validateRecharge(phone: String, amount: Double): String? {
    if (phone.length < 8) return "Telefono invalido. Debe tener al menos 8 digitos."
    if (amount <= 0.0) return "Monto invalido. Escribe o elige un monto mayor que cero."
    return null
}

private fun formatMoney(value: Double): String = com.lotterynet.pro.core.format.formatWholeMoney(value)

private fun formatCompactMoney(value: Double): String = "$ ${stripDecimal(value)}"

private fun stripDecimal(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        value.toString()
    }
}

private fun formatStamp(epochMs: Long): String {
    val format = SimpleDateFormat("hh:mm a", Locale.US)
    format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(Date(epochMs))
}

private fun formatVoucherStamp(epochMs: Long): String {
    val format = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US)
    format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(Date(epochMs))
}

private fun santoDomingoDayKey(epochMs: Long): String {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(Date(epochMs))
}

private fun formatLimitInput(value: Double): String {
    if (value <= 0.0) return ""
    return stripDecimal(value)
}

private fun sanitizeDecimal(value: String): String {
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

private fun RecargasActivity.printRechargeVoucher(voucher: RechargeVoucherState) {
    thread(name = "native-recharge-voucher-print") {
        val prefs = LocalThermalPrinterRepository(this).getPrefs()
        val content = buildRechargeVoucherText(voucher)
        val result = when (
            resolveRechargeVoucherPrintTarget(
                integratedAvailable = IntegratedThermalPrinter.isAvailable(this),
                selectedBluetoothAddress = prefs.selectedPrinterAddress,
            )
        ) {
            RechargeVoucherPrintTarget.BLUETOOTH -> BluetoothThermalPrinter.printText(
                context = this,
                content = content,
                prefs = prefs,
            )
            RechargeVoucherPrintTarget.INTEGRATED -> IntegratedThermalPrinter.printText(
                context = this,
                content = content,
            )
            RechargeVoucherPrintTarget.NONE -> BluetoothThermalPrinter.PrintResult(false, "No hay impresora conectada")
        }
        runOnUiThread {
            Toast.makeText(this, result.message, if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
        }
    }
}

private fun RecargasActivity.shareRechargeVoucher(voucher: RechargeVoucherState) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, buildRechargeVoucherShareText(voucher))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        startActivity(Intent.createChooser(intent, "Compartir voucher").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(this, "No se pudo abrir el menu de compartir.", Toast.LENGTH_SHORT).show()
    }
}

internal fun buildRechargeVoucherShareText(voucher: RechargeVoucherState): String {
    val record = voucher.record
    return buildString {
        appendLine("Recarga Rapida - ${voucher.bancaName.ifBlank { "Comprobante" }}")
        appendLine("Tipo: ${voucher.saleLabel}")
        appendLine("Compania: ${record.providerName ?: "Recarga"}")
        appendLine("Telefono: ${record.phoneNumber ?: "-"}")
        appendLine("Monto: ${formatMoney(record.amount)}")
        appendLine("Estado: ${rechargeVoucherCustomerStatus(record.status)}")
        appendLine("Fecha: ${formatVoucherStamp(record.createdAtEpochMs)}")
        record.providerReference?.takeIf { it.isNotBlank() }?.let {
            appendLine("Referencia: $it")
        }
        appendLine()
        appendLine("Gracias por su compra")
    }.trim()
}

private fun rechargeVoucherCustomerStatus(status: String): String {
    return when (status.lowercase(Locale.US)) {
        "completed", "success", "paid" -> "Completada"
        "pending" -> "Pendiente"
        "failed", "error" -> "Fallida"
        else -> status.ifBlank { "Registrada" }
    }
}

internal fun buildRechargeVoucherText(voucher: RechargeVoucherState): String {
    val record = voucher.record
    return buildString {
        appendLine("[[TITLE|normal]]RECARGA RAPIDA")
        voucher.bancaName.takeIf { it.isNotBlank() }?.let {
            appendLine("[[CENTER|normal]]$it")
        }
        appendLine("------------------------------")
        appendLine("Estado: ${record.status.uppercase(Locale.US)}")
        appendLine("Tipo: ${voucher.saleLabel}")
        appendLine("Compania: ${record.providerName ?: "Recarga"}")
        appendLine("Telefono: ${record.phoneNumber ?: "-"}")
        appendLine("[[TOTAL|normal]]Monto ${formatMoney(record.amount)}")
        appendLine("Fecha: ${formatVoucherStamp(record.createdAtEpochMs)}")
        record.providerReference?.takeIf { it.isNotBlank() }?.let {
            appendLine("Referencia: $it")
        }
        appendLine("------------------------------")
        appendLine("[[CENTER|normal]]Comprobante de venta")
        appendLine("[[FOOTER|normal]]Gracias por su compra")
    }
}

private fun RecargasActivity.shareRecharges(
    session: ActiveSession,
    rows: List<RechargeRecord>,
    whatsappOnly: Boolean,
) {
    val shareText = buildRechargesShareText(session, rows)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
        if (whatsappOnly) {
            `package` = "com.whatsapp"
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        startActivity(
            if (whatsappOnly) {
                intent
            } else {
                Intent.createChooser(intent, "Compartir recargas").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }.recoverCatching {
        if (whatsappOnly) {
            startActivity(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    `package` = "com.whatsapp.w4b"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } else {
            throw it
        }
    }.onFailure {
        Toast.makeText(
            this,
            if (whatsappOnly) "WhatsApp no esta disponible." else "No se pudo abrir el menu de compartir.",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun buildRechargesShareText(
    session: ActiveSession,
    rows: List<RechargeRecord>,
): String {
    val total = rows.sumOf { it.amount }
    val header = buildString {
        append("Recargas del dia")
        session.banca?.takeIf { it.isNotBlank() }?.let {
            append(" · ")
            append(it)
        }
        append('\n')
        append("Operador: ")
        append(session.username)
        append('\n')
        append("Total: ")
        append(formatMoney(total))
        append(" · Operaciones: ")
        append(rows.size)
    }
    if (rows.isEmpty()) return "$header\n\nSin recargas registradas todavia."
    val lines = rows.take(15).mapIndexed { index, row ->
        "${index + 1}. ${row.providerName ?: "Recarga"} · ${row.phoneNumber ?: "-"} · ${formatMoney(row.amount)} · ${formatStamp(row.createdAtEpochMs)}"
    }
    val overflow = if (rows.size > 15) "\n... y ${rows.size - 15} mas" else ""
    return buildString {
        append(header)
        append("\n\n")
        append(lines.joinToString("\n"))
        append(overflow)
    }
}
