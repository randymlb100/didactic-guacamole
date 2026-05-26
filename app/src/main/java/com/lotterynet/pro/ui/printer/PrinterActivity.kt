package com.lotterynet.pro.ui.printer

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SettingsBluetooth
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.model.ThermalPreviewPolicy
import com.lotterynet.pro.core.model.ThermalPrinterPrefs
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.printing.BluetoothThermalPrinter
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.core.printing.ThermalTicketRenderer
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalThermalPrinterRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.ui.common.*
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import com.lotterynet.pro.ui.tickets.TicketOfficialActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread

class PrinterActivity : AppCompatActivity() {
    private var bluetoothPermissionCallback: ((Boolean) -> Unit)? = null
    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        bluetoothPermissionCallback?.invoke(
            grants[Manifest.permission.BLUETOOTH_CONNECT] == true,
        )
        bluetoothPermissionCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, session?.role, NativeDestination.PRINTER)) {
            return
        }
        checkNotNull(session)
        LocalUsersRepository(this).touchSession(session)
        val role = session.role
        val ticketId = intent?.getStringExtra(EXTRA_TICKET_ID).orEmpty()
        val ticketEpoch = intent?.getLongExtra(EXTRA_TICKET_EPOCH, 0L) ?: 0L
        val thermalTitle = intent?.getStringExtra(EXTRA_THERMAL_TITLE)
        val thermalText = intent?.getStringExtra(EXTRA_THERMAL_TEXT)
        val salesRepository = LocalSalesRepository(this)
        val usersRepository = LocalUsersRepository(this)
        val previewTicket = ticketId.takeIf { it.isNotBlank() }?.let {
            val dayTickets = salesRepository.getTicketsForDay(buildDayKey(ticketEpoch))
            val scopedTickets = filterTicketsForOperationalScope(session, dayTickets, usersRepository.getCashiers())
            val visibleTickets = if (scopedTickets.any { ticket -> ticket.id == ticketId }) {
                scopedTickets
            } else {
                filterTicketsForOperationalScope(session, salesRepository.getAllTickets(), usersRepository.getCashiers())
            }
            visibleTickets.firstOrNull { ticket -> ticket.id == ticketId }
        }
        val repository = LocalThermalPrinterRepository(this)
        val initialPrefs = repository.getPrefs()
        val previewPolicy = ThermalPreviewPolicy(
            canPreview = role == UserRole.ADMIN || role == UserRole.MASTER || previewTicket != null || !thermalText.isNullOrBlank(),
            canEditPrinterPrefs = role == UserRole.ADMIN || role == UserRole.CASHIER,
        )
        val bluetoothEnabled = getBluetoothAdapterEnabled()
        val hasBluetoothPermission = hasBluetoothPermissions()
        val pairedPrinters = loadPairedPrinters()

        setContent {
            LotteryNetComposeTheme {
                PrinterRoute(
                    role = role,
                    bancaName = session.banca,
                    initialPrefs = initialPrefs,
                    previewTicket = previewTicket,
                    thermalTitle = thermalTitle,
                    thermalText = thermalText,
                    previewPolicy = previewPolicy,
                    pairedPrinters = pairedPrinters,
                    bluetoothEnabled = bluetoothEnabled,
                    hasBluetoothPermission = hasBluetoothPermission,
                    onBack = { finish() },
                    onSave = { repository.savePrefs(it) },
                    onApplyClassic = { repository.applyClassicPreset() },
                    onOpenBluetoothSettings = {
                        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    },
                    onRefreshBluetoothState = {
                        PrinterBluetoothState(
                            enabled = getBluetoothAdapterEnabled(),
                            hasPermission = hasBluetoothPermissions(),
                            pairedPrinters = loadPairedPrinters(),
                        )
                    },
                    onRequestBluetoothPermission = { callback ->
                        requestBluetoothPermission(callback)
                    },
                    onOpenOfficialTicket = previewTicket?.let {
                        {
                            startActivity(Intent(this, TicketOfficialActivity::class.java).apply {
                                putExtra(TicketOfficialActivity.EXTRA_TICKET_ID, it.id)
                                putExtra(TicketOfficialActivity.EXTRA_TICKET_EPOCH, it.createdAtEpochMs)
                                putExtra(TicketOfficialActivity.EXTRA_BANCA_NAME, session.banca.orEmpty())
                            })
                        }
                    },
                )
            }
        }
    }

    private fun buildDayKey(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        return format.format(Date(epochMs))
    }

    private fun getBluetoothAdapterEnabled(): Boolean {
        val manager = getSystemService(BluetoothManager::class.java)
        return manager?.adapter?.isEnabled == true
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothPermissions(): Boolean {
        return hasBluetoothConnectPermission()
    }

    private fun requestBluetoothPermission(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasBluetoothPermissions()) {
            callback(true)
            return
        }
        bluetoothPermissionCallback = callback
        bluetoothPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedPrinters(): List<PrinterDeviceOption> {
        if (!hasBluetoothPermissions()) return emptyList()
        val manager = getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices
                ?.toList()
                .orEmpty()
                .sortedBy { it.name ?: it.address }
                .map { device ->
                    PrinterDeviceOption(
                        value = device.address,
                        label = listOfNotNull(device.name?.takeIf { it.isNotBlank() }, device.address).joinToString(" · "),
                    )
                }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val EXTRA_TICKET_ID = "printer_ticket_id"
        const val EXTRA_TICKET_EPOCH = "printer_ticket_epoch"
        const val EXTRA_THERMAL_TITLE = "printer_thermal_title"
        const val EXTRA_THERMAL_TEXT = "printer_thermal_text"
    }
}

internal enum class PrinterSectionId {
    SUMMARY,
    PRINTER,
    CONNECTION,
    PREVIEW,
    ACTIONS,
}

internal data class PrinterLayoutContract(
    val contentColumns: Int,
    val compactControls: Boolean,
    val singleStatusLine: Boolean,
    val sections: List<PrinterSectionId>,
)

internal data class PrinterDeviceOption(
    val value: String,
    val label: String,
)

internal data class PrinterBluetoothState(
    val enabled: Boolean,
    val hasPermission: Boolean,
    val pairedPrinters: List<PrinterDeviceOption>,
)

internal data class PrinterConnectionSnapshot(
    val statusLabel: String,
    val detail: String,
    val selectedPrinterLabel: String?,
    val canManageConnection: Boolean,
)

internal enum class PrinterActionId {
    TEST_CONNECTION,
    SAVE,
    CLASSIC,
    PRINT,
    SHARE,
    SAVE_PREVIEW,
    BACK_TO_TICKET,
}

internal fun resolvePrinterLayoutContract(
    role: UserRole,
    canPreview: Boolean,
    canEditPrinterPrefs: Boolean,
): PrinterLayoutContract {
    val operationalRole = role == UserRole.ADMIN || role == UserRole.CASHIER
    val sections = buildList {
        add(PrinterSectionId.SUMMARY)
        if (operationalRole && canEditPrinterPrefs) {
            add(PrinterSectionId.CONNECTION)
        }
        add(PrinterSectionId.ACTIONS)
    }
    return PrinterLayoutContract(
        contentColumns = 1,
        compactControls = true,
        singleStatusLine = true,
        sections = sections,
    )
}

internal fun buildPrinterConnectionSnapshot(
    role: UserRole,
    bluetoothEnabled: Boolean,
    hasBluetoothPermission: Boolean,
    pairedPrinterCount: Int,
    selectedPrinterLabel: String?,
): PrinterConnectionSnapshot {
    val canManage = role == UserRole.ADMIN || role == UserRole.CASHIER
    if (!canManage) {
        return PrinterConnectionSnapshot(
            statusLabel = "Solo lectura",
            detail = "La conexión se administra desde caja o administración.",
            selectedPrinterLabel = selectedPrinterLabel,
            canManageConnection = false,
        )
    }
    if (!hasBluetoothPermission) {
        return PrinterConnectionSnapshot(
            statusLabel = "Permiso",
            detail = "Falta permiso Bluetooth para listar o conectar impresoras.",
            selectedPrinterLabel = selectedPrinterLabel,
            canManageConnection = true,
        )
    }
    if (!bluetoothEnabled) {
        return PrinterConnectionSnapshot(
            statusLabel = "Apagado",
            detail = "Activa Bluetooth para conectar la impresora térmica.",
            selectedPrinterLabel = selectedPrinterLabel,
            canManageConnection = true,
        )
    }
    if (pairedPrinterCount <= 0) {
        return PrinterConnectionSnapshot(
            statusLabel = "Sin pares",
            detail = "No hay impresoras enlazadas en este equipo.",
            selectedPrinterLabel = selectedPrinterLabel,
            canManageConnection = true,
        )
    }
    return PrinterConnectionSnapshot(
        statusLabel = "Lista",
        detail = "$pairedPrinterCount impresoras detectadas para prueba o conexión.",
        selectedPrinterLabel = selectedPrinterLabel,
        canManageConnection = true,
    )
}

internal fun resolvePrinterActionOrder(showBackToTicket: Boolean): List<PrinterActionId> {
    return buildList {
        add(PrinterActionId.TEST_CONNECTION)
        add(PrinterActionId.PRINT)
        if (showBackToTicket) add(PrinterActionId.BACK_TO_TICKET)
    }
}

internal fun resolveEffectiveBluetoothPrinterAddress(
    selectedPrinterAddress: String,
    pairedPrinters: List<PrinterDeviceOption>,
): String {
    return selectedPrinterAddress.ifBlank {
        pairedPrinters.firstOrNull()?.value.orEmpty()
    }
}

internal fun presentPrinterRoleLabel(role: UserRole): String {
    return when (role) {
        UserRole.ADMIN -> "Admin"
        UserRole.CASHIER -> "Caja"
        UserRole.MASTER -> "Master"
        else -> "Consulta"
    }
}

@Composable
private fun PrinterRoute(
    role: UserRole,
    bancaName: String?,
    initialPrefs: ThermalPrinterPrefs,
    previewTicket: TicketRecord?,
    thermalTitle: String?,
    thermalText: String?,
    previewPolicy: ThermalPreviewPolicy,
    pairedPrinters: List<PrinterDeviceOption>,
    bluetoothEnabled: Boolean,
    hasBluetoothPermission: Boolean,
    onBack: () -> Unit,
    onSave: (ThermalPrinterPrefs) -> Unit,
    onApplyClassic: () -> ThermalPrinterPrefs,
    onOpenBluetoothSettings: () -> Unit,
    onRefreshBluetoothState: () -> PrinterBluetoothState,
    onRequestBluetoothPermission: ((Boolean) -> Unit) -> Unit,
    onOpenOfficialTicket: (() -> Unit)?,
) {
    val actionOrder = remember(onOpenOfficialTicket) { resolvePrinterActionOrder(onOpenOfficialTicket != null) }
    var prefs by remember(initialPrefs) { mutableStateOf(initialPrefs) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var bluetoothEnabledState by remember(bluetoothEnabled) { mutableStateOf(bluetoothEnabled) }
    var hasBluetoothPermissionState by remember(hasBluetoothPermission) { mutableStateOf(hasBluetoothPermission) }
    var pairedPrintersState by remember(pairedPrinters) { mutableStateOf(pairedPrinters) }
    var selectedPrinterAddress by rememberSaveable {
        mutableStateOf(
            initialPrefs.selectedPrinterAddress.takeIf { it.isNotBlank() }
                ?: pairedPrinters.firstOrNull()?.value.orEmpty(),
        )
    }
    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    val renderer = remember { ThermalTicketRenderer() }
    val layout = resolvePrinterLayoutContract(
        role = role,
        canPreview = previewPolicy.canPreview,
        canEditPrinterPrefs = previewPolicy.canEditPrinterPrefs,
    )
    fun refreshBluetoothState(): PrinterBluetoothState {
        val state = onRefreshBluetoothState()
        bluetoothEnabledState = state.enabled
        hasBluetoothPermissionState = state.hasPermission
        pairedPrintersState = state.pairedPrinters
        if (selectedPrinterAddress.isBlank()) {
            selectedPrinterAddress = state.pairedPrinters.firstOrNull()?.value.orEmpty()
        }
        return state
    }
    val selectedPrinterLabel = pairedPrintersState.firstOrNull { it.value == selectedPrinterAddress }?.label
    val connectionSnapshot = buildPrinterConnectionSnapshot(
        role = role,
        bluetoothEnabled = bluetoothEnabledState,
        hasBluetoothPermission = hasBluetoothPermissionState,
        pairedPrinterCount = pairedPrintersState.size,
        selectedPrinterLabel = selectedPrinterLabel,
    )
    val previewText = remember(prefs, bancaName, previewTicket, thermalText) {
        previewTicket?.let {
            renderer.renderTicket(
                ticket = it,
                bancaName = bancaName ?: "LotteryNet",
                prefs = prefs,
            )
        } ?: thermalText.takeUnless { it.isNullOrBlank() } ?: renderer.renderPreview(bancaName ?: "LotteryNet", prefs)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            BottomNavBar(
                role = role,
                active = NativeBottomTab.MENU,
                onSelected = { tab -> openBottomTab(context, role, tab) },
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
                ScreenHeaderPanel(
                    title = "Impresora térmica",
                    subtitle = listOfNotNull(
                        role.name.lowercase(),
                        bancaName,
                        thermalTitle,
                        previewTicket?.serial?.let { "ticket $it" },
                    ).joinToString(" · "),
                    onBack = onBack,
                    badgeLabel = "Térmico",
                    badgeTone = MaterialTheme.colorScheme.primary,
                )
            }
            items(layout.sections) { section ->
                when (section) {
                    PrinterSectionId.SUMMARY -> PrinterSummarySection(
                            role = role,
                            bancaName = bancaName,
                            prefs = prefs,
                            previewTicket = previewTicket,
                            thermalTitle = thermalTitle,
                            actionMessage = actionMessage,
                        )

                    PrinterSectionId.PRINTER -> PrinterSettingsSection(
                            prefs = prefs.copy(selectedPrinterAddress = selectedPrinterAddress),
                            canEditPrinterPrefs = previewPolicy.canEditPrinterPrefs,
                            onPrefsChange = { prefs = it },
                        )

                    PrinterSectionId.CONNECTION -> PrinterConnectionSection(
                            snapshot = connectionSnapshot,
                            pairedPrinters = pairedPrintersState,
                            selectedPrinterAddress = selectedPrinterAddress,
                            onSelectedPrinter = {
                                selectedPrinterAddress = it
                                prefs = prefs.copy(selectedPrinterAddress = it)
                            },
                            onOpenBluetoothSettings = onOpenBluetoothSettings,
                            onRefresh = {
                                val state = refreshBluetoothState()
                                actionMessage = when {
                                    !state.hasPermission -> "Falta permiso Bluetooth"
                                    !state.enabled -> "Bluetooth apagado"
                                    state.pairedPrinters.isEmpty() -> "No hay impresoras enlazadas"
                                    else -> "${state.pairedPrinters.size} impresoras detectadas"
                                }
                            },
                        )

                    PrinterSectionId.PREVIEW -> Unit

                    PrinterSectionId.ACTIONS -> PrinterActionsSection(
                            actionOrder = actionOrder,
                            onApplyClassic = {
                                prefs = onApplyClassic().copy(selectedPrinterAddress = selectedPrinterAddress)
                                actionMessage = "Perfil clásico restaurado"
                            },
                            onSave = {
                                onSave(prefs.copy(selectedPrinterAddress = selectedPrinterAddress))
                                actionMessage = "Ajustes térmicos guardados"
                                Toast.makeText(context, "Ajustes guardados", Toast.LENGTH_SHORT).show()
                            },
                            onPrint = {
                                val address = resolveEffectiveBluetoothPrinterAddress(
                                    selectedPrinterAddress = selectedPrinterAddress,
                                    pairedPrinters = pairedPrintersState,
                                )
                                if (address.isBlank()) {
                                    actionMessage = "No hay impresora enlazada para imprimir"
                                    return@PrinterActionsSection
                                }
                                selectedPrinterAddress = address
                                val printPrefs = prefs.copy(selectedPrinterAddress = address)
                                prefs = printPrefs
                                onSave(printPrefs)
                                actionBusy = true
                                actionMessage = "Enviando a impresora Bluetooth..."
                                thread(name = "printer-preview-print") {
                                    val result = runCatching {
                                        BluetoothThermalPrinter.printText(
                                            context = context.applicationContext,
                                            content = previewText,
                                            prefs = printPrefs,
                                        )
                                    }.getOrElse { error ->
                                        BluetoothThermalPrinter.PrintResult(
                                            success = false,
                                            message = error.message?.takeIf { it.isNotBlank() }
                                                ?: "No se pudo imprimir en Bluetooth",
                                        )
                                    }
                                    (context as? AppCompatActivity)?.runOnUiThread {
                                        actionBusy = false
                                        actionMessage = result.message
                                    }
                                }
                            },
                            onTestConnection = {
                                fun runRealTest() {
                                    val state = refreshBluetoothState()
                                    if (!state.hasPermission) {
                                        actionMessage = "Falta permiso Bluetooth para probar la impresora"
                                        return
                                    }
                                    if (!state.enabled) {
                                        actionMessage = "Bluetooth está apagado"
                                        return
                                    }
                                    val address = resolveEffectiveBluetoothPrinterAddress(
                                        selectedPrinterAddress = selectedPrinterAddress,
                                        pairedPrinters = state.pairedPrinters,
                                    )
                                    if (address.isBlank()) {
                                        actionMessage = "No hay impresora enlazada para probar"
                                        return
                                    }
                                    selectedPrinterAddress = address
                                    val testPrefs = prefs.copy(selectedPrinterAddress = address)
                                    prefs = testPrefs
                                    onSave(testPrefs)
                                    actionBusy = true
                                    actionMessage = "Probando conexión Bluetooth..."
                                    thread(name = "printer-real-test") {
                                        val result = runCatching {
                                            BluetoothThermalPrinter.testConnection(
                                                context = context.applicationContext,
                                                prefs = testPrefs,
                                                bancaName = bancaName ?: "LotteryNet",
                                            )
                                        }.getOrElse { error ->
                                            BluetoothThermalPrinter.PrintResult(
                                                success = false,
                                                message = error.message?.takeIf { it.isNotBlank() }
                                                    ?: "No se pudo probar la impresora",
                                            )
                                        }
                                        (context as? AppCompatActivity)?.runOnUiThread {
                                            actionBusy = false
                                            actionMessage = if (result.success) {
                                                "Prueba enviada. Revisa que la impresora imprimió el comprobante."
                                            } else {
                                                result.message
                                            }
                                        }
                                    }
                                }
                                if (!hasBluetoothPermissionState) {
                                    actionMessage = "Solicitando permiso Bluetooth..."
                                    onRequestBluetoothPermission { granted ->
                                        hasBluetoothPermissionState = granted
                                        if (granted) {
                                            runRealTest()
                                        } else {
                                            actionMessage = "Permiso Bluetooth rechazado"
                                        }
                                    }
                                } else {
                                    runRealTest()
                                }
                            },
                            onSavePreview = {
                                actionMessage = "Vista de impresora desactivada"
                            },
                            onSharePreview = {
                                actionMessage = "Vista de impresora desactivada"
                            },
                            onWhatsApp = {
                                actionMessage = "Vista de impresora desactivada"
                            },
                            onOpenOfficialTicket = onOpenOfficialTicket,
                            actionMessage = actionMessage,
                            busy = actionBusy,
                        )
                }
            }
        }
    }
}

@Composable
private fun PrinterSummarySection(
    role: UserRole,
    bancaName: String?,
    prefs: ThermalPrinterPrefs,
    previewTicket: TicketRecord?,
    thermalTitle: String?,
    actionMessage: String?,
) {
    CompactPanel {
        SectionHeader(title = "Salida térmica", meta = "Resumen")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactKeyValueRow("Perfil", buildPrinterProfileLabel(prefs), modifier = Modifier.weight(1f))
            CompactKeyValueRow("Origen", previewTicket?.serial ?: thermalTitle ?: "Demo", modifier = Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactKeyValueRow("Banca", bancaName ?: "LotteryNet", modifier = Modifier.weight(1f))
            CompactKeyValueRow("Rol", presentPrinterRoleLabel(role), modifier = Modifier.weight(1f))
        }
        CompactStatusBadge("Listo para imprimir o compartir", tone = MaterialTheme.colorScheme.primary)
        actionMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrinterSettingsSection(
    prefs: ThermalPrinterPrefs,
    canEditPrinterPrefs: Boolean,
    onPrefsChange: (ThermalPrinterPrefs) -> Unit,
) {
    CompactPanel {
        SectionHeader(title = "Impresora", meta = if (canEditPrinterPrefs) "Perfil local" else "Solo lectura")
        ThermalSectionCard(title = "Diseño", meta = "Papel y densidad", defaultExpanded = true) {
            buildThermalDesignFields(prefs).forEach { field ->
                ThermalChoiceFieldRow(
                    label = field.label,
                    value = field.value,
                    options = field.options,
                    keyboardType = field.keyboardType,
                    hint = field.hint,
                    enabled = canEditPrinterPrefs,
                    onValueChange = { value -> onPrefsChange(field.applyChange(prefs, value)) },
                )
            }
        }
        ThermalSectionCard(title = "Tamaños", meta = "Por componente", defaultExpanded = true) {
            buildThermalSizeFields(prefs).forEach { field ->
                ThermalChoiceFieldRow(
                    label = field.label,
                    value = field.value,
                    options = field.options,
                    keyboardType = field.keyboardType,
                    hint = field.hint,
                    enabled = canEditPrinterPrefs,
                    onValueChange = { value -> onPrefsChange(field.applyChange(prefs, value)) },
                )
            }
        }
        ThermalSectionCard(title = "Contenido", meta = "Visible", defaultExpanded = false) {
            buildThermalToggleFields(prefs).forEach { field ->
                ThermalToggleRow(
                    label = field.label,
                    checked = field.checked,
                    enabled = canEditPrinterPrefs,
                    onCheckedChange = { checked -> onPrefsChange(field.applyChange(prefs, checked)) },
                )
            }
        }
    }
}

@Composable
private fun PrinterConnectionSection(
    snapshot: PrinterConnectionSnapshot,
    pairedPrinters: List<PrinterDeviceOption>,
    selectedPrinterAddress: String,
    onSelectedPrinter: (String) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    CompactPanel {
        SectionHeader(title = "Conexión y estado", meta = "Bluetooth")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CompactKeyValueRow("Estado", snapshot.statusLabel, modifier = Modifier.weight(1f))
            CompactKeyValueRow("Impresoras", pairedPrinters.size.toString(), modifier = Modifier.weight(1f))
        }
        Text(
            text = snapshot.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pairedPrinters.isNotEmpty()) {
            ThermalDropdownField(
                label = "Impresora detectada",
                selectedValue = selectedPrinterAddress,
                options = pairedPrinters,
                enabled = snapshot.canManageConnection,
                onValueSelected = onSelectedPrinter,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            CompactActionButton(
                label = "Actualizar",
                onClick = onRefresh,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Bluetooth,
                tone = ActionTone.Secondary,
            )
            CompactActionButton(
                label = "Bluetooth",
                onClick = onOpenBluetoothSettings,
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.SettingsBluetooth,
                tone = ActionTone.Primary,
            )
        }
        snapshot.selectedPrinterLabel?.let {
            CompactStatusBadge("Seleccionada: $it", tone = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PrinterActionsSection(
    actionOrder: List<PrinterActionId>,
    onApplyClassic: () -> Unit,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onTestConnection: () -> Unit,
    onSavePreview: () -> Unit,
    onSharePreview: () -> Unit,
    onWhatsApp: () -> Unit,
    onOpenOfficialTicket: (() -> Unit)?,
    actionMessage: String?,
    busy: Boolean,
) {
    CompactPanel {
        SectionHeader(title = "Acciones", meta = "Operación")
        actionMessage?.let {
            CompactStatusBadge(
                label = it,
                tone = if (busy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CompactAdaptiveGrid(itemCount = 2, columns = 2) { index, itemModifier ->
            when (index) {
                0 -> CompactActionButton(
                    label = "Probar",
                    onClick = onTestConnection,
                    modifier = itemModifier,
                    icon = Icons.Rounded.Bluetooth,
                    tone = ActionTone.Primary,
                )

                1 -> CompactActionButton(
                    label = "Imprimir",
                    onClick = onPrint,
                    modifier = itemModifier,
                    icon = Icons.Rounded.Print,
                    tone = ActionTone.Success,
                )

                else -> Unit
            }
        }
        val secondaryActions = actionOrder.filter {
            it !in setOf(PrinterActionId.TEST_CONNECTION, PrinterActionId.PRINT, PrinterActionId.SHARE)
        }
        CompactAdaptiveGrid(itemCount = secondaryActions.size, columns = 2) { index, itemModifier ->
            when (secondaryActions[index]) {
                PrinterActionId.TEST_CONNECTION -> CompactActionButton(
                    label = "Probar",
                    onClick = onTestConnection,
                    modifier = itemModifier,
                    icon = Icons.Rounded.Bluetooth,
                    tone = ActionTone.Primary,
                )

                PrinterActionId.SAVE,
                PrinterActionId.CLASSIC -> Unit

                PrinterActionId.PRINT -> CompactActionButton(
                    label = "Imprimir",
                    onClick = onPrint,
                    modifier = itemModifier,
                    icon = Icons.Rounded.Print,
                    tone = ActionTone.Success,
                )

                PrinterActionId.SHARE -> CompactActionButton(
                    label = "Compartir",
                    onClick = onSharePreview,
                    modifier = itemModifier,
                    icon = Icons.Rounded.Share,
                    tone = ActionTone.Secondary,
                )

                PrinterActionId.SAVE_PREVIEW -> CompactActionButton(
                    label = "Guardar imagen",
                    onClick = onSavePreview,
                    modifier = itemModifier,
                    icon = Icons.Rounded.Download,
                    tone = ActionTone.Secondary,
                )

                PrinterActionId.BACK_TO_TICKET -> CompactActionButton(
                    label = "Volver al ticket",
                    onClick = { onOpenOfficialTicket?.invoke() },
                    modifier = itemModifier,
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    tone = ActionTone.Secondary,
                )
            }
        }
    }
}

@Composable
private fun ThermalSectionCard(
    title: String,
    meta: String,
    defaultExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    CompactPanel(alt = true) {
        SectionHeader(title = title, meta = meta)
        content()
    }
}

@Composable
private fun ThermalToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    CompactPanel(alt = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

@Composable
private fun ThermalChoiceFieldRow(
    label: String,
    value: String,
    options: List<PrinterDeviceOption>?,
    keyboardType: KeyboardType,
    hint: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (options != null) {
            ThermalDropdownField(
                label = label,
                selectedValue = value,
                options = options,
                enabled = enabled,
                onValueSelected = onValueChange,
            )
        } else {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        }
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ThermalDropdownField(
    label: String,
    selectedValue: String,
    options: List<PrinterDeviceOption>,
    enabled: Boolean,
    onValueSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: options.firstOrNull()?.label.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            label = { Text(label) },
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onValueSelected(option.value)
                    },
                )
            }
        }
    }
}

internal fun buildThermalPreviewCaption(): String? = null

internal fun shouldShowThermalPrinterPreview(): Boolean = false

@Composable
private fun ThermalPreviewLockedCard() {
    CompactEmptyState(
        message = "La vista previa se habilita cuando hay ticket cargado o acceso de administración.",
        modifier = Modifier.fillMaxWidth(),
    )
}

internal sealed interface ThermalField {
    data class Toggle(
        val label: String,
        val checked: Boolean,
        val applyChange: (ThermalPrinterPrefs, Boolean) -> ThermalPrinterPrefs,
    ) : ThermalField

    data class TextChoice(
        val label: String,
        val value: String,
        val options: List<PrinterDeviceOption>? = null,
        val keyboardType: KeyboardType = KeyboardType.Text,
        val hint: String? = null,
        val applyChange: (ThermalPrinterPrefs, String) -> ThermalPrinterPrefs,
    ) : ThermalField
}

internal fun buildThermalChoiceFields(prefs: ThermalPrinterPrefs): List<ThermalField.TextChoice> =
    buildThermalDesignFields(prefs) + buildThermalSizeFields(prefs)

internal fun buildThermalDesignFields(prefs: ThermalPrinterPrefs): List<ThermalField.TextChoice> = listOf(
    ThermalField.TextChoice(
        label = "Ancho de papel",
        value = prefs.paperWidth,
        options = listOf(
            PrinterDeviceOption("58", "58 mm"),
            PrinterDeviceOption("80", "80 mm"),
        ),
        hint = "Elige el ancho físico del rollo.",
        applyChange = { p, v -> p.copy(paperWidth = v) },
    ),
    ThermalField.TextChoice(
        label = "Tipografía",
        value = prefs.fontFamily,
        options = listOf(
            PrinterDeviceOption("consolas", "Consolas"),
            PrinterDeviceOption("jetbrains", "JetBrains Mono"),
            PrinterDeviceOption("courier", "Courier"),
        ),
        hint = "Todas mantienen lectura térmica compacta.",
        applyChange = { p, v -> p.copy(fontFamily = v) },
    ),
    ThermalField.TextChoice(
        label = "Etiqueta de jugada",
        value = prefs.typeLabelMode,
        options = listOf(
            PrinterDeviceOption("single", "Corta"),
            PrinterDeviceOption("double", "Media"),
            PrinterDeviceOption("full", "Completa"),
        ),
        hint = "Define cuánto texto acompaña cada jugada.",
        applyChange = { p, v -> p.copy(typeLabelMode = v) },
    ),
    ThermalField.TextChoice(
        label = "Ancho visual",
        value = prefs.widthMode,
        options = listOf(
            PrinterDeviceOption("narrow", "Estrecho"),
            PrinterDeviceOption("standard", "Normal"),
            PrinterDeviceOption("wide", "Ancho"),
            PrinterDeviceOption("custom", "Personalizado"),
        ),
        hint = "Usa personalizado solo si ajustas caracteres por línea.",
        applyChange = { p, v -> p.copy(widthMode = v) },
    ),
    ThermalField.TextChoice(
        label = "Caracteres por línea",
        value = prefs.customChars,
        keyboardType = KeyboardType.Number,
        hint = "Rango operativo entre 24 y 60.",
        applyChange = { p, v -> p.copy(customChars = v) },
    ),
    ThermalField.TextChoice(
        label = "Densidad",
        value = prefs.density,
        options = listOf(
            PrinterDeviceOption("tight", "Compacta"),
            PrinterDeviceOption("balanced", "Balanceada"),
            PrinterDeviceOption("airy", "Amplia"),
        ),
        hint = "Afecta espacios entre líneas y bloques.",
        applyChange = { p, v -> p.copy(density = v) },
    ),
    ThermalField.TextChoice(
        label = "Separadores",
        value = prefs.separator,
        options = listOf(
            PrinterDeviceOption("short", "Corto"),
            PrinterDeviceOption("full", "Completo"),
            PrinterDeviceOption("minimal", "Mínimo"),
        ),
        hint = "Controla la presencia de líneas divisoras.",
        applyChange = { p, v -> p.copy(separator = v) },
    ),
)

internal fun buildThermalSizeFields(prefs: ThermalPrinterPrefs): List<ThermalField.TextChoice> = listOf(
    ThermalField.TextChoice(
        label = "Encabezado",
        value = prefs.headerScale,
        options = thermalScaleOptions(),
        applyChange = { p, v -> p.copy(headerScale = v) },
    ),
    ThermalField.TextChoice(
        label = "Serial",
        value = prefs.serialScale,
        options = thermalScaleOptions(),
        applyChange = { p, v -> p.copy(serialScale = v) },
    ),
    ThermalField.TextChoice(
        label = "Lotería",
        value = prefs.lotteryScale,
        options = thermalScaleOptions(),
        applyChange = { p, v -> p.copy(lotteryScale = v) },
    ),
    ThermalField.TextChoice(
        label = "Tipo",
        value = prefs.playTypeScale,
        options = thermalScaleOptions(),
        hint = "Quiniela, Pale, Tripleta o Pick.",
        applyChange = { p, v -> p.copy(playTypeScale = v, itemScale = v) },
    ),
    ThermalField.TextChoice(
        label = "Número jugado",
        value = prefs.playNumberScale,
        options = thermalScaleOptions(),
        hint = "Si está grande, sale destacado para no cortarse.",
        applyChange = { p, v -> p.copy(playNumberScale = v) },
    ),
    ThermalField.TextChoice(
        label = "Monto",
        value = prefs.amountScale,
        options = thermalScaleOptions(),
        hint = "Si está grande, baja a su propia línea.",
        applyChange = { p, v -> p.copy(amountScale = v) },
    ),
    ThermalField.TextChoice(
        label = "Total",
        value = prefs.totalScale,
        options = thermalScaleOptions(),
        applyChange = { p, v -> p.copy(totalScale = v) },
    ),
    ThermalField.TextChoice(
        label = "Código",
        value = prefs.securityScale,
        options = thermalScaleOptions(),
        applyChange = { p, v -> p.copy(securityScale = v) },
    ),
)

private fun thermalScaleOptions(): List<PrinterDeviceOption> = listOf(
    PrinterDeviceOption("compact", "Pequeño"),
    PrinterDeviceOption("normal", "Mediano"),
    PrinterDeviceOption("large", "Grande"),
)

private fun buildThermalToggleFields(prefs: ThermalPrinterPrefs): List<ThermalField.Toggle> = listOf(
    ThermalField.Toggle("Mostrar ORIGINAL", prefs.showOriginal, applyChange = { p, v -> p.copy(showOriginal = v) }),
    ThermalField.Toggle("Mostrar dirección", prefs.showAddress, applyChange = { p, v -> p.copy(showAddress = v) }),
    ThermalField.Toggle("Mostrar teléfono", prefs.showPhone, applyChange = { p, v -> p.copy(showPhone = v) }),
    ThermalField.Toggle("Mostrar fecha y hora", prefs.showDateTime, applyChange = { p, v -> p.copy(showDateTime = v) }),
    ThermalField.Toggle("Mostrar hora de sorteo", prefs.showDrawTime, applyChange = { p, v -> p.copy(showDrawTime = v) }),
    ThermalField.Toggle("Mostrar código", prefs.showSecurity, applyChange = { p, v -> p.copy(showSecurity = v) }),
    ThermalField.Toggle("Mostrar banca al final", prefs.showFooter, applyChange = { p, v -> p.copy(showFooter = v) }),
)

private fun buildPrinterProfileLabel(prefs: ThermalPrinterPrefs): String {
    val density = when (prefs.density) {
        "tight" -> "compacta"
        "balanced" -> "balanceada"
        "airy" -> "amplia"
        else -> prefs.density
    }
    return "${prefs.paperWidth} mm · ${prefs.fontFamily} · $density"
}
