package com.lotterynet.pro.ui.servicesgames

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import android.util.Base64
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.servicesgames.ServicesGamesBackendClient
import com.lotterynet.pro.core.servicesgames.ServicesGamesConfirmRequest
import com.lotterynet.pro.core.servicesgames.ServicesGamesModule
import com.lotterynet.pro.core.servicesgames.ServicesGamesQueryRequest
import com.lotterynet.pro.core.servicesgames.ServicesGamesProviderContracts
import com.lotterynet.pro.core.storage.LocalMasterConfigRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.toFeatureConfig
import com.lotterynet.pro.ui.common.AppTopBar
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactSegmentedSelector
import com.lotterynet.pro.ui.common.LotteryLogo
import com.lotterynet.pro.ui.common.OperationalListHeader
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.ScreenChromeAction
import com.lotterynet.pro.ui.common.ScreenChromeSpec
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import coil3.compose.AsyncImage

class ServicesGamesActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_MODULE = "services_games_module"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = LocalSessionRepository(this).getActiveSession()
        val module = ServicesGamesModule.fromWire(intent?.getStringExtra(EXTRA_MODULE))
            ?: ServicesGamesModule.SERVICES
        if (session == null) {
            finish()
            return
        }
        val configRepository = LocalMasterConfigRepository(this)
        val tokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        val backend = ServicesGamesBackendClient()
        setContent {
            LotteryNetComposeTheme {
                ServicesGamesRoute(
                    session = session,
                    initialModule = module,
                    initialEnabled = configRepository.getServicesGamesSettings(module).toFeatureConfig().canOpen(
                        role = session.role,
                        actorKey = session.userId.ifBlank { session.username },
                        adminKey = session.adminId ?: session.adminUser,
                    ),
                    onLoadConfig = { nextModule ->
                        val remote = withContext(Dispatchers.IO) {
                            runCatching {
                                com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore(
                                    bearerTokenProvider = { tokenProvider.freshAccessToken() },
                                ).fetchValue(com.lotterynet.pro.core.storage.servicesGamesRemoteKey(nextModule))
                                    ?.toString()
                                    ?.let { raw ->
                                        com.lotterynet.pro.core.storage.decodeMasterServicesGamesSettings(nextModule, raw)
                                    }
                            }.getOrNull()
                        }
                        remote?.toFeatureConfig()?.canOpen(
                            role = session.role,
                            actorKey = session.userId.ifBlank { session.username },
                            adminKey = session.adminId ?: session.adminUser,
                        ) ?: configRepository.getServicesGamesSettings(nextModule).toFeatureConfig().canOpen(
                            role = session.role,
                            actorKey = session.userId.ifBlank { session.username },
                            adminKey = session.adminId ?: session.adminUser,
                        )
                    },
                    onLoadCatalog = { nextModule ->
                        withContext(Dispatchers.IO) {
                            backend.catalog(
                                module = nextModule,
                                adminKey = session.adminId ?: session.adminUser ?: "",
                                cashierKey = session.userId.ifBlank { session.username },
                                clientRequestId = UUID.randomUUID().toString(),
                                bearerToken = tokenProvider.freshAccessToken(),
                            )
                        }
                    },
                    onConfirm = { request ->
                        withContext(Dispatchers.IO) {
                            backend.confirm(request, tokenProvider.freshAccessToken())
                        }
                    },
                    onQuery = { request ->
                        withContext(Dispatchers.IO) {
                            backend.query(request, tokenProvider.freshAccessToken())
                        }
                    },
                    onOpenReport = {
                        startActivity(Intent(this@ServicesGamesActivity, ServicesGamesReportActivity::class.java))
                    },
                    onBack = { finish() },
                    onError = { message ->
                        Toast.makeText(this@ServicesGamesActivity, message, Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }
}

private data class CatalogRow(
    val providerId: String,
    val productId: String,
    val name: String,
    val categoryId: String,
    val price: Double,
    val serviceType: String,
    val logoUrl: String,
    val logoAssetKey: String,
    val secondaryLogoAssetKey: String,
    val categoryKey: String,
    val categoryLabel: String,
)

private data class CustomerIdentifierSpec(
    val label: String,
    val supportingText: String,
    val keyboardType: KeyboardType = KeyboardType.Number,
)

private data class GameAccountSpec(
    val playerLabel: String,
    val playerSupportingText: String,
    val zoneLabel: String,
    val zoneSupportingText: String,
)

@androidx.compose.runtime.Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServicesGamesRoute(
    session: ActiveSession,
    initialModule: ServicesGamesModule,
    initialEnabled: Boolean,
    onLoadConfig: suspend (ServicesGamesModule) -> Boolean,
    onLoadCatalog: suspend (ServicesGamesModule) -> JSONObject,
    onQuery: suspend (ServicesGamesQueryRequest) -> JSONObject,
    onConfirm: suspend (ServicesGamesConfirmRequest) -> JSONObject,
    onOpenReport: () -> Unit,
    onBack: () -> Unit,
    onError: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var module by remember { mutableStateOf(initialModule) }
    var enabled by remember { mutableStateOf(initialEnabled) }
    var status by remember { mutableStateOf("Preparando catálogo...") }
    var rows by remember { mutableStateOf(emptyList<CatalogRow>()) }
    var selected by remember { mutableStateOf<CatalogRow?>(null) }
    var playerId by remember { mutableStateOf("") }
    var zoneId by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var remittanceService by remember { mutableStateOf("MONCASH") }
    var remittanceTypeId by remember { mutableStateOf("") }
    var senderName by remember { mutableStateOf("") }
    var senderPhone by remember { mutableStateOf("") }
    var senderAddress by remember { mutableStateOf("") }
    var recipientName by remember { mutableStateOf("") }
    var recipientPhone by remember { mutableStateOf("") }
    var recipientAddress by remember { mutableStateOf("") }
    var remittanceCalculated by remember { mutableStateOf(false) }
    var insuranceName by remember { mutableStateOf("") }
    var insuranceLastname by remember { mutableStateOf("") }
    var insuranceAddress by remember { mutableStateOf("") }
    var insuranceDocumentId by remember { mutableStateOf("") }
    var insurancePhone by remember { mutableStateOf("") }
    var insuranceCellphone by remember { mutableStateOf("") }
    var insuranceMake by remember { mutableStateOf("") }
    var insuranceModel by remember { mutableStateOf("") }
    var insuranceYear by remember { mutableStateOf("") }
    var insuranceDuration by remember { mutableStateOf("12") }
    var insurancePaymentType by remember { mutableStateOf("1") }
    var insuranceCompany by remember { mutableStateOf("Seguro") }
    var insuranceType by remember { mutableStateOf("") }
    var insuranceChasis by remember { mutableStateOf("") }
    var insuranceCedulaBase64 by remember { mutableStateOf("") }
    var insuranceMatriculaBase64 by remember { mutableStateOf("") }
    var simCompany by remember { mutableStateOf("") }
    var simName by remember { mutableStateOf("") }
    var simDocumentId by remember { mutableStateOf("") }
    var simBirthday by remember { mutableStateOf("") }
    var simFathersName by remember { mutableStateOf("") }
    var simMothersName by remember { mutableStateOf("") }
    var simIccid by remember { mutableStateOf("") }
    val context = LocalContext.current
    val cedulaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        insuranceCedulaBase64 = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP) } }.orEmpty()
    }
    val matriculaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        insuranceMatriculaBase64 = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> Base64.encodeToString(stream.readBytes(), Base64.NO_WRAP) } }.orEmpty()
    }
    var billLookupDone by remember { mutableStateOf(false) }
    var billResponse by remember { mutableStateOf<JSONObject?>(null) }
    var operationRequestId by remember { mutableStateOf("") }
    var lastOperationSummary by remember { mutableStateOf<String?>(null) }
    var selectedServiceCategory by remember { mutableStateOf("all") }
    var catalogSearch by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val operationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun switchModule(next: ServicesGamesModule) {
        if (module == next) return
        module = next
        enabled = false
        rows = emptyList()
        status = "Cargando ${next.label.lowercase()}..."
        selected = null
        playerId = ""
        zoneId = ""
        clientName = ""
        notes = ""
        amountText = ""
        remittanceService = "MONCASH"
        remittanceTypeId = ""
        senderName = ""
        senderPhone = ""
        senderAddress = ""
        recipientName = ""
        recipientPhone = ""
        recipientAddress = ""
        remittanceCalculated = false
        insuranceName = ""
        insuranceLastname = ""
        insuranceAddress = ""
        insuranceDocumentId = ""
        insurancePhone = ""
        insuranceCellphone = ""
        insuranceMake = ""
        insuranceModel = ""
        insuranceYear = ""
        insuranceDuration = "12"
        insurancePaymentType = "1"
        insuranceCompany = "Seguro"
        insuranceType = ""
        insuranceChasis = ""
        insuranceCedulaBase64 = ""
        insuranceMatriculaBase64 = ""
        simCompany = ""
        simName = ""
        simDocumentId = ""
        simBirthday = ""
        simFathersName = ""
        simMothersName = ""
        simIccid = ""
        billLookupDone = false
        billResponse = null
        operationRequestId = ""
        lastOperationSummary = null
        selectedServiceCategory = "all"
        catalogSearch = ""
    }

    fun selectRow(row: CatalogRow) {
        selected = row
        playerId = ""
        zoneId = ""
        clientName = ""
        notes = ""
        amountText = ""
        remittanceService = "MONCASH"
        remittanceTypeId = ""
        senderName = ""
        senderPhone = ""
        senderAddress = ""
        recipientName = ""
        recipientPhone = ""
        recipientAddress = ""
        remittanceCalculated = false
        insuranceName = ""
        insuranceLastname = ""
        insuranceAddress = ""
        insuranceDocumentId = ""
        insurancePhone = ""
        insuranceCellphone = ""
        insuranceMake = ""
        insuranceModel = ""
        insuranceYear = ""
        insuranceDuration = "12"
        insurancePaymentType = "1"
        insuranceCompany = "Seguro"
        insuranceType = ""
        insuranceChasis = ""
        insuranceCedulaBase64 = ""
        insuranceMatriculaBase64 = ""
        simCompany = ""
        simName = ""
        simDocumentId = ""
        simBirthday = ""
        simFathersName = ""
        simMothersName = ""
        simIccid = ""
        billLookupDone = false
        billResponse = null
        operationRequestId = ""
        lastOperationSummary = null
        catalogSearch = ""
    }

    fun load(nextModule: ServicesGamesModule) {
        scope.launch {
            enabled = onLoadConfig(nextModule)
            if (!enabled) {
                rows = emptyList()
                selected = null
                status = "Este módulo no está habilitado para tu usuario."
                return@launch
            }
            status = "Cargando catálogo..."
            runCatching { onLoadCatalog(nextModule) }
                .onSuccess { response ->
                    rows = parseCatalog(response, nextModule)
                    status = if (rows.isEmpty()) "No hay productos disponibles." else "${rows.size} producto(s) disponible(s)."
                }
                .onFailure {
                    status = it.message ?: "No se pudo cargar el catálogo."
                    onError(status)
                }
        }
    }

    LaunchedEffect(module) {
        billLookupDone = false
        load(module)
    }

    val normalizedSearch = catalogSearch.trim().lowercase()
    val visibleRows = rows.filter { row ->
        val matchesCategory = module != ServicesGamesModule.SERVICES ||
            selectedServiceCategory == "all" || row.categoryKey == selectedServiceCategory
        val matchesSearch = normalizedSearch.isBlank() || listOf(
            row.name,
            row.categoryLabel,
            row.serviceType,
            row.providerId,
        ).any { it.lowercase().contains(normalizedSearch) }
        matchesCategory && matchesSearch
    }

    LaunchedEffect(selected, visibleRows.size) {
        if (selected != null) {
            listState.animateScrollToItem(1 + visibleRows.size)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                spec = ScreenChromeSpec(
                    title = module.label,
                    subtitle = "Servicios y videojuegos",
                    showBottomNav = false,
                    rightAction = ScreenChromeAction(
                        icon = Icons.Rounded.QueryStats,
                        contentDescription = "Abrir reporte",
                        onClick = onOpenReport,
                    ),
                ),
                onOpenMenu = onBack,
                applyStatusBarInsets = true,
            )
        },
        containerColor = visual.colors.background,
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = visual.sizes.screenPaddingH),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ServicesGamesCatalogToolbar(
                    module = module,
                    onModuleSelected = { wire ->
                        ServicesGamesModule.fromWire(wire)?.let { next -> switchModule(next) }
                    },
                    catalogSearch = catalogSearch,
                    onSearchChange = { catalogSearch = it },
                    status = status,
                    visibleCount = visibleRows.size,
                    rows = rows,
                    selectedServiceCategory = selectedServiceCategory,
                    onCategorySelected = { key ->
                        selectedServiceCategory = key
                        selected = null
                        billLookupDone = false
                    },
                )
            }
            lastOperationSummary?.let { summary ->
                item(key = "last-operation-summary") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Operación confirmada",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(summary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (enabled) {
                items(
                    items = visibleRows,
                    key = { "${it.providerId}:${it.productId}" },
                    contentType = { "catalog" },
                ) { row ->
                    ServicesGamesCatalogCard(
                        row = row,
                        module = module,
                        onClick = { selectRow(row) },
                    )
                }
                if (visibleRows.isEmpty()) {
                    item(key = "empty-catalog") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    if (catalogSearch.isBlank()) "No hay opciones disponibles" else "No encontramos coincidencias",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    if (catalogSearch.isBlank()) "El catálogo no tiene productos activos para esta sección." else "Prueba con otro nombre o limpia la búsqueda.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (catalogSearch.isNotBlank()) {
                                    TextButton(onClick = { catalogSearch = "" }) { Text("Limpiar búsqueda") }
                                }
                            }
                        }
                    }
                }
            }
            selected?.let { row ->
                item(key = "operation") {
                    ModalBottomSheet(
                        onDismissRequest = { selected = null },
                        sheetState = operationSheetState,
                    ) {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Column(
                                Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Completa la operación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(row.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { selected = null }) { Text("Cambiar") }
                            }
                            val isBill = module == ServicesGamesModule.SERVICES && row.serviceType == "bills_lookup"
                            val isRemittance = module == ServicesGamesModule.SERVICES &&
                                (row.serviceType == "remittance_calculate" || row.serviceType == "remittance_send")
                            val isInsurance = module == ServicesGamesModule.SERVICES && row.serviceType == "insurance_sale"
                            val isSimActivation = module == ServicesGamesModule.SERVICES && row.serviceType == "sim_activation"
                            val hasOperationContract = module == ServicesGamesModule.VIDEO_GAMES ||
                                isBill || isRemittance || isInsurance || isSimActivation
                            val customerIdentifierSpec = serviceCustomerIdentifierSpec(row)
                            val gameAccountSpec = gameAccountSpec(row)
                            Text(
                                when {
                                    module == ServicesGamesModule.VIDEO_GAMES -> "Datos del jugador"
                                    isBill -> "Consulta antes de pagar"
                                    isRemittance -> "Datos de la remesa"
                                    isInsurance -> "Datos del seguro"
                                    isSimActivation -> "Datos de activación"
                                    else -> "Datos del cliente"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (module == ServicesGamesModule.VIDEO_GAMES) {
                                IdentifierField(
                                    value = playerId,
                                    onValueChange = { playerId = it },
                                    label = { Text(gameAccountSpec.playerLabel) },
                                    supportingText = { Text(gameAccountSpec.playerSupportingText) },
                                )
                                IdentifierField(
                                    value = zoneId,
                                    onValueChange = { zoneId = it },
                                    label = { Text(gameAccountSpec.zoneLabel) },
                                    supportingText = { Text(gameAccountSpec.zoneSupportingText) },
                                )
                                OutlinedTextField(
                                    value = clientName,
                                    onValueChange = { clientName = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Nombre para el comprobante") },
                                )
                                OutlinedTextField(
                                    value = notes,
                                    onValueChange = { notes = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Nota opcional") },
                                    maxLines = 2,
                                )
                            } else if (isRemittance) {
                                OutlinedTextField(remittanceService, { remittanceService = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Servicio: MONCASH o NATCASH") })
                                OutlinedTextField(remittanceTypeId, { remittanceTypeId = it }, Modifier.fillMaxWidth(), label = { Text("Tipo de remesa") })
                                OutlinedTextField(amountText, { amountText = it }, Modifier.fillMaxWidth(), label = { Text("Monto a enviar") })
                                OutlinedTextField(senderName, { senderName = it }, Modifier.fillMaxWidth(), label = { Text("Nombre del remitente") })
                                OutlinedTextField(senderPhone, { senderPhone = it }, Modifier.fillMaxWidth(), label = { Text("Teléfono del remitente") })
                                OutlinedTextField(senderAddress, { senderAddress = it }, Modifier.fillMaxWidth(), label = { Text("Dirección del remitente") })
                                OutlinedTextField(recipientName, { recipientName = it }, Modifier.fillMaxWidth(), label = { Text("Nombre del destinatario") })
                                OutlinedTextField(recipientPhone, { recipientPhone = it }, Modifier.fillMaxWidth(), label = { Text("Teléfono del destinatario") })
                                OutlinedTextField(recipientAddress, { recipientAddress = it }, Modifier.fillMaxWidth(), label = { Text("Ciudad / provincia del destinatario") })
                            } else if (isInsurance) {
                                OutlinedTextField(insuranceName, { insuranceName = it }, Modifier.fillMaxWidth(), label = { Text("Nombres del propietario") })
                                OutlinedTextField(insuranceLastname, { insuranceLastname = it }, Modifier.fillMaxWidth(), label = { Text("Apellidos del propietario") })
                                OutlinedTextField(insuranceAddress, { insuranceAddress = it }, Modifier.fillMaxWidth(), label = { Text("Dirección") })
                                OutlinedTextField(insuranceDocumentId, { insuranceDocumentId = it }, Modifier.fillMaxWidth(), label = { Text("Cédula") })
                                OutlinedTextField(insurancePhone, { insurancePhone = it }, Modifier.fillMaxWidth(), label = { Text("Teléfono") })
                                OutlinedTextField(insuranceCellphone, { insuranceCellphone = it }, Modifier.fillMaxWidth(), label = { Text("Celular") })
                                OutlinedTextField(insuranceMake, { insuranceMake = it }, Modifier.fillMaxWidth(), label = { Text("Marca del vehículo") })
                                OutlinedTextField(insuranceModel, { insuranceModel = it }, Modifier.fillMaxWidth(), label = { Text("Modelo") })
                                OutlinedTextField(insuranceYear, { insuranceYear = it }, Modifier.fillMaxWidth(), label = { Text("Año") })
                                OutlinedTextField(insuranceType, { insuranceType = it }, Modifier.fillMaxWidth(), label = { Text("Tipo de vehículo") })
                                OutlinedTextField(insuranceChasis, { insuranceChasis = it }, Modifier.fillMaxWidth(), label = { Text("Chasis") })
                                OutlinedTextField(insuranceDuration, { insuranceDuration = it }, Modifier.fillMaxWidth(), label = { Text("Duración en meses") })
                                OutlinedTextField(insurancePaymentType, { insurancePaymentType = it }, Modifier.fillMaxWidth(), label = { Text("Cantidad de cuotas") })
                                OutlinedTextField(insuranceCompany, { insuranceCompany = it }, Modifier.fillMaxWidth(), label = { Text("Compañía") })
                                OutlinedTextField(amountText, { amountText = it }, Modifier.fillMaxWidth(), label = { Text("Monto") })
                                Button(onClick = { cedulaPicker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (insuranceCedulaBase64.isBlank()) "Seleccionar cédula" else "Cédula seleccionada")
                                }
                                Button(onClick = { matriculaPicker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (insuranceMatriculaBase64.isBlank()) "Seleccionar matrícula" else "Matrícula seleccionada")
                                }
                            } else if (isSimActivation) {
                                OutlinedTextField(simCompany, { simCompany = it }, Modifier.fillMaxWidth(), label = { Text("Compañía") })
                                OutlinedTextField(simName, { simName = it }, Modifier.fillMaxWidth(), label = { Text("Nombre del cliente") })
                                OutlinedTextField(simDocumentId, { simDocumentId = it }, Modifier.fillMaxWidth(), label = { Text("Cédula del cliente") })
                                OutlinedTextField(simBirthday, { simBirthday = it }, Modifier.fillMaxWidth(), label = { Text("Fecha de nacimiento (YYYY-MM-DD)") })
                                OutlinedTextField(simFathersName, { simFathersName = it }, Modifier.fillMaxWidth(), label = { Text("Nombre del padre") })
                                OutlinedTextField(simMothersName, { simMothersName = it }, Modifier.fillMaxWidth(), label = { Text("Nombre de la madre") })
                                OutlinedTextField(simIccid, { simIccid = it }, Modifier.fillMaxWidth(), label = { Text("ICCID") })
                                OutlinedTextField(amountText, { amountText = it }, Modifier.fillMaxWidth(), label = { Text("Costo de activación") })
                            } else if (isBill) {
                                IdentifierField(
                                    value = playerId,
                                    onValueChange = {
                                        playerId = it
                                        billLookupDone = false
                                        billResponse = null
                                        amountText = ""
                                        operationRequestId = ""
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = customerIdentifierSpec.keyboardType),
                                    label = { Text(customerIdentifierSpec.label) },
                                    supportingText = { Text(customerIdentifierSpec.supportingText) },
                                )
                            } else {
                                Text(
                                    "Este servicio todavía no tiene un contrato de operación disponible.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            if (isBill && !billLookupDone) {
                                Button(
                                    enabled = !busy && playerId.isNotBlank(),
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            status = "Consultando factura..."
                                            val payload = ServicesGamesProviderContracts.billLookup(playerId, row.providerId)
                                            runCatching {
                                                onQuery(
                                                    ServicesGamesQueryRequest(
                                                        clientRequestId = UUID.randomUUID().toString(),
                                                        module = module,
                                                        providerId = row.providerId,
                                                        productId = row.productId,
                                                        adminKey = session.adminId ?: session.adminUser ?: "",
                                                        cashierKey = session.userId.ifBlank { session.username },
                                                        customerInput = JSONObject().put("value", playerId.trim()),
                                                        serviceType = "bills_lookup",
                                                        providerPayload = payload,
                                                    ),
                                                )
                                            }.onSuccess { response ->
                                                billLookupDone = true
                                                billResponse = response
                                                val invoiceAmount = providerInvoiceAmount(response)
                                                if (invoiceAmount != null) amountText = invoiceAmount.toString()
                                                status = if (invoiceAmount != null) {
                                                    "Factura consultada. Monto pendiente: RD$ $invoiceAmount"
                                                } else {
                                                    "Factura consultada. Revisa el detalle antes de pagar."
                                                }
                                            }.onFailure {
                                                status = it.message ?: "No se pudo consultar la factura."
                                                onError(status)
                                            }
                                            busy = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (busy) "Consultando..." else "Consultar factura") }
                            } else {
                                if (isBill && billLookupDone) {
                                    BillSummary(response = billResponse)
                                }
                                if (isRemittance && remittanceCalculated) {
                                    Text("Cotización confirmada. Revisa los datos y envía la remesa.", style = MaterialTheme.typography.bodySmall)
                                }
                                Button(
                                enabled = !busy &&
                                    hasOperationContract &&
                                    if (isRemittance) {
                                        remittanceService.isNotBlank() && remittanceTypeId.isNotBlank() &&
                                            amountText.toDoubleOrNull()?.let { it >= 115.0 } == true &&
                                            senderName.isNotBlank() && senderPhone.isNotBlank() && senderAddress.isNotBlank() &&
                                            recipientName.isNotBlank() && recipientPhone.isNotBlank() && recipientAddress.isNotBlank()
                                    } else if (isInsurance) {
                                        insuranceName.isNotBlank() && insuranceLastname.isNotBlank() && insuranceAddress.isNotBlank() &&
                                            insuranceDocumentId.isNotBlank() && insurancePhone.isNotBlank() && insuranceCellphone.isNotBlank() &&
                                            insuranceMake.isNotBlank() && insuranceModel.isNotBlank() && insuranceYear.isNotBlank() &&
                                            insuranceType.isNotBlank() && insuranceChasis.isNotBlank() && insuranceCedulaBase64.isNotBlank() &&
                                            insuranceMatriculaBase64.isNotBlank() && amountText.toDoubleOrNull()?.let { it > 0.0 } == true
                                    } else if (isSimActivation) {
                                        simCompany.isNotBlank() && simName.isNotBlank() && simDocumentId.isNotBlank() &&
                                            simBirthday.isNotBlank() && simFathersName.isNotBlank() && simMothersName.isNotBlank() &&
                                            simIccid.isNotBlank() && amountText.toDoubleOrNull()?.let { it > 0.0 } == true
                                    } else {
                                        playerId.isNotBlank() && (module == ServicesGamesModule.VIDEO_GAMES || amountText.toDoubleOrNull()?.let { it > 0.0 } == true) &&
                                            (module != ServicesGamesModule.VIDEO_GAMES || (row.price > 0.0 && zoneId.isNotBlank() && clientName.isNotBlank())) &&
                                            (!isBill || billLookupDone)
                                    },
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        status = if (isRemittance && !remittanceCalculated) "Calculando remesa..." else "Enviando operación..."
                                        if (operationRequestId.isBlank()) {
                                            operationRequestId = UUID.randomUUID().toString()
                                        }
                                        val providerPayload = if (module == ServicesGamesModule.VIDEO_GAMES) {
                                            ServicesGamesProviderContracts.videoGame(
                                                categoryId = row.categoryId,
                                                productId = row.productId,
                                                playerId = playerId,
                                                zoneId = zoneId,
                                                clientName = clientName,
                                                notes = notes,
                                            )
                                        } else if (isBill) {
                                            ServicesGamesProviderContracts.billPaymentAmount(amountText.toDoubleOrNull() ?: 0.0)
                                        } else if (isRemittance) {
                                            val base = JSONObject()
                                                .put("serviceName", remittanceService.trim())
                                                .put("amountSent", amountText.toDoubleOrNull() ?: 0.0)
                                                .put("remittanceType", JSONObject().put("id", remittanceTypeId.trim()))
                                            if (remittanceCalculated) {
                                                ServicesGamesProviderContracts.remittanceSend(base
                                                    .put("senderName", senderName.trim())
                                                    .put("senderPhone", senderPhone.trim())
                                                    .put("senderAddress", senderAddress.trim())
                                                    .put("recipientName", recipientName.trim())
                                                    .put("recipientPhone", recipientPhone.trim())
                                                    .put("recipientAddress", recipientAddress.trim()))
                                            } else {
                                                ServicesGamesProviderContracts.remittanceCalculation(base)
                                            }
                                        } else if (isInsurance) {
                                            ServicesGamesProviderContracts.insurance(JSONObject()
                                                .put("name", insuranceName.trim())
                                                .put("lastname", insuranceLastname.trim())
                                                .put("address", insuranceAddress.trim())
                                                .put("documentId", insuranceDocumentId.trim())
                                                .put("phone", insurancePhone.trim())
                                                .put("cellphone", insuranceCellphone.trim())
                                                .put("make", insuranceMake.trim())
                                                .put("model", insuranceModel.trim())
                                                .put("year", insuranceYear.toIntOrNull() ?: 0)
                                                .put("duration", insuranceDuration.toIntOrNull() ?: 12)
                                                .put("amount", amountText.toDoubleOrNull() ?: 0.0)
                                                .put("paymentType", insurancePaymentType.toIntOrNull() ?: 1)
                                                .put("company", insuranceCompany.trim())
                                                .put("type", insuranceType.trim())
                                                .put("chasis", insuranceChasis.trim())
                                                .put("crane", false)
                                                .put("driverHouse", false)
                                                .put("craneValue", 0)
                                                .put("driverHouseValue", 0)
                                                .put("cedulaImageBase64", insuranceCedulaBase64)
                                                .put("matriculaImageBase64", insuranceMatriculaBase64))
                                        } else if (isSimActivation) {
                                            ServicesGamesProviderContracts.simActivation(JSONObject()
                                                .put("company", simCompany.trim())
                                                .put("name", simName.trim())
                                                .put("documentId", simDocumentId.trim())
                                                .put("birthday", simBirthday.trim())
                                                .put("fathersName", simFathersName.trim())
                                                .put("mothersName", simMothersName.trim())
                                                .put("ICCID", simIccid.trim())
                                                .put("balanceDependsOnCreatedBy", false))
                                        } else {
                                            JSONObject()
                                        }
                                        runCatching {
                                            if (isRemittance && !remittanceCalculated) {
                                                onQuery(
                                                    ServicesGamesQueryRequest(
                                                        clientRequestId = UUID.randomUUID().toString(),
                                                        module = module,
                                                        providerId = row.providerId,
                                                        productId = row.productId,
                                                        adminKey = session.adminId ?: session.adminUser ?: "",
                                                        cashierKey = session.userId.ifBlank { session.username },
                                                        customerInput = JSONObject(),
                                                        serviceType = "remittance_calculate",
                                                        providerPayload = providerPayload,
                                                    ),
                                                )
                                            } else {
                                                onConfirm(
                                                ServicesGamesConfirmRequest(
                                                    clientRequestId = operationRequestId,
                                                    module = module,
                                            providerId = row.providerId,
                                                    productId = row.productId,
                                                    adminKey = session.adminId ?: session.adminUser ?: "",
                                                    cashierKey = session.userId.ifBlank { session.username },
                                                    quotedPrice = row.price.takeIf { it > 0.0 } ?: amountText.toDoubleOrNull() ?: 0.0,
                                                    customerInput = JSONObject().put("value", playerId.trim()),
                                                    serviceType = when {
                                                        isBill -> "bills_pay"
                                                        isRemittance -> "remittance_send"
                                                        else -> row.serviceType
                                                    },
                                                    amount = amountText.toDoubleOrNull(),
                                                    providerPayload = providerPayload,
                                                ),
                                                )
                                            }
                                        }.onSuccess { response ->
                                            if (isRemittance && !remittanceCalculated) {
                                                remittanceCalculated = true
                                                status = "Cotización recibida. Confirma el envío."
                                                operationRequestId = ""
                                            } else {
                                                status = "Operación enviada al proveedor."
                                                lastOperationSummary = providerSummary(response).ifBlank {
                                                    "${row.name} procesado correctamente."
                                                }
                                                onError("Operación enviada correctamente.")
                                                operationRequestId = ""
                                            }
                                        }.onFailure {
                                            status = it.message ?: "No se pudo completar la operación."
                                            onError(status)
                                        }
                                        busy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (busy) "Procesando..." else if (isBill) "Pagar factura" else if (isRemittance && !remittanceCalculated) "Calcular remesa" else if (isRemittance) "Enviar remesa" else "Confirmar operación") }
                            }
                        }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ServicesGamesCatalogToolbar(
    module: ServicesGamesModule,
    onModuleSelected: (String) -> Unit,
    catalogSearch: String,
    onSearchChange: (String) -> Unit,
    status: String,
    visibleCount: Int,
    rows: List<CatalogRow>,
    selectedServiceCategory: String,
    onCategorySelected: (String) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        alt = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 9.dp),
    ) {
        OperationalListHeader(
            title = module.label,
            meta = "$visibleCount visibles",
        )
        CompactSegmentedSelector(
            options = listOf(
                QuickFilterChip(ServicesGamesModule.SERVICES.wireValue, "Servicios"),
                QuickFilterChip(ServicesGamesModule.VIDEO_GAMES.wireValue, "Videojuegos"),
            ),
            selectedId = module.wireValue,
            onSelected = onModuleSelected,
            columns = 2,
        )
        OutlinedTextField(
            value = catalogSearch,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Buscar") },
            label = { Text(if (module == ServicesGamesModule.SERVICES) "Buscar servicio" else "Buscar videojuego") },
            placeholder = { Text("Nombre o categoría") },
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = visual.colors.muted,
            maxLines = 2,
        )
        if (module == ServicesGamesModule.SERVICES) {
            val categories = listOf("all" to "Todos") + rows
                .map { it.categoryKey to it.categoryLabel }
                .filter { it.first.isNotBlank() }
                .distinctBy { it.first }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedServiceCategory == key,
                        onClick = { onCategorySelected(key) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ServicesGamesCatalogCard(
    row: CatalogRow,
    module: ServicesGamesModule,
    onClick: () -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.logoAssetKey.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LotteryLogo(
                        assetPath = row.logoAssetKey,
                        fallback = row.name.take(2).uppercase(),
                        modifier = Modifier.size(44.dp),
                        fillBounds = true,
                    )
                    if (row.secondaryLogoAssetKey.isNotBlank()) {
                        LotteryLogo(
                            assetPath = row.secondaryLogoAssetKey,
                            fallback = "",
                            modifier = Modifier.size(44.dp),
                            fillBounds = true,
                        )
                    }
                }
            } else if (row.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = row.logoUrl,
                    contentDescription = row.name,
                    modifier = Modifier.size(44.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    if (module == ServicesGamesModule.VIDEO_GAMES) Icons.Rounded.SportsEsports else Icons.Rounded.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = visual.colors.actionPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.ink,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                if (row.categoryLabel.isNotBlank()) {
                    Text(row.categoryLabel, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                }
                if (row.price > 0) {
                    Text(
                        "RD$ ${row.price}",
                        style = MaterialTheme.typography.labelLarge,
                        color = visual.colors.ink,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = visual.colors.actionPrimarySurface,
                contentColor = visual.colors.actionPrimary,
            ) {
                Text(
                    if (module == ServicesGamesModule.VIDEO_GAMES) "Vender" else serviceActionLabel(row.serviceType),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun providerSummary(response: JSONObject): String {
    val provider = response.optJSONObject("provider") ?: response
    val amount = provider.optDouble("amount", Double.NaN)
    return if (!amount.isNaN() && amount > 0) "Monto informado: RD$ $amount" else ""
}

private fun serviceActionLabel(serviceType: String): String = when (serviceType) {
    "bills_lookup" -> "Consultar"
    "insurance_sale" -> "Cotizar"
    "remittance_calculate", "remittance_send" -> "Enviar remesa"
    "sim_activation" -> "Activar SIM"
    else -> "Procesar"
}

@Composable
private fun IdentifierField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    supportingText: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        label = label,
        supportingText = supportingText,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

private fun serviceCustomerIdentifierSpec(row: CatalogRow): CustomerIdentifierSpec {
    return when (row.categoryKey) {
        "electricity" -> CustomerIdentifierSpec(
            label = when (row.providerId) {
                "edenorte", "edesur" -> "NIC del contrato"
                "edeeste" -> "NIC o número de contrato"
                else -> "Número de contrato eléctrico"
            },
            supportingText = "Escribe el identificador tal como aparece en la factura de ${row.name}.",
        )
        "water" -> CustomerIdentifierSpec(
            label = "Número de contrato",
            supportingText = "Escribe el contrato del servicio de agua sin espacios ni guiones.",
        )
        "telecom" -> CustomerIdentifierSpec(
            label = "Teléfono o número de cuenta",
            supportingText = "Usa el identificador mostrado en la factura de ${row.name}.",
            keyboardType = KeyboardType.Phone,
        )
        "telecable" -> CustomerIdentifierSpec(
            label = "Número de contrato o abonado",
            supportingText = "Escribe el código de cliente que aparece en la factura de ${row.name}.",
        )
        else -> CustomerIdentifierSpec(
            label = "Número de contrato",
            supportingText = "Escribe el identificador solicitado por ${row.name}.",
        )
    }
}

private fun gameAccountSpec(row: CatalogRow): GameAccountSpec {
    val key = "${row.name} ${row.categoryId}".lowercase()
    return when {
        "free fire" in key || "free_fire" in key -> GameAccountSpec(
            playerLabel = "ID de jugador de Free Fire",
            playerSupportingText = "Copia el ID numérico desde el perfil del jugador.",
            zoneLabel = "ID de zona o servidor",
            zoneSupportingText = "Confirma la zona antes de realizar la compra.",
        )
        "roblox" in key -> GameAccountSpec(
            playerLabel = "Usuario o ID de Roblox",
            playerSupportingText = "Verifica la cuenta que recibirá el producto.",
            zoneLabel = "Región o servidor",
            zoneSupportingText = "Escribe la región indicada en la cuenta.",
        )
        "minecraft" in key -> GameAccountSpec(
            playerLabel = "ID de la cuenta de Minecraft",
            playerSupportingText = "Usa el identificador de la cuenta que recibirá el producto.",
            zoneLabel = "Plataforma o región",
            zoneSupportingText = "Indica la plataforma o región correspondiente.",
        )
        "delta force" in key || "delta_force" in key -> GameAccountSpec(
            playerLabel = "ID de jugador de Delta Force",
            playerSupportingText = "Copia el identificador desde el perfil del juego.",
            zoneLabel = "Servidor o región",
            zoneSupportingText = "Selecciona el servidor correcto de la cuenta.",
        )
        else -> GameAccountSpec(
            playerLabel = "ID del jugador",
            playerSupportingText = "Verifica el identificador antes de confirmar.",
            zoneLabel = "Zona o servidor",
            zoneSupportingText = "Escribe la zona indicada por el juego.",
        )
    }
}

private fun providerInvoiceAmount(response: JSONObject): Double? {
    val provider = response.optJSONObject("provider") ?: response
    val invoices = provider.optJSONArray("invoices") ?: return null
    if (invoices.length() == 0) return null
    val invoice = invoices.optJSONObject(0) ?: return null
    return readMoney(invoice, "pago_atraso")
        ?: readMoney(invoice, "pago_total")
}

private fun readMoney(json: JSONObject, key: String): Double? {
    val raw = json.opt(key) ?: return null
    return raw.toString().replace(",", "").toDoubleOrNull()?.takeIf { it > 0.0 }
}

@androidx.compose.runtime.Composable
private fun BillSummary(response: JSONObject?) {
    val provider = response?.optJSONObject("provider") ?: response ?: return
    val customer = provider.optJSONObject("customer")
    val invoices = provider.optJSONArray("invoices")
    val pendingAmount = providerInvoiceAmount(provider)
    val customerName = listOf(
        customer?.optString("first_name").orEmpty(),
        customer?.optString("last_name").orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Detalle de factura", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (customerName.isNotBlank()) Text(customerName)
            customer?.optString("address")?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val count = invoices?.length() ?: 0
            if (count == 0) {
                Text("No hay facturas pendientes.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("$count factura(s) pendiente(s)", style = MaterialTheme.typography.bodySmall)
                pendingAmount?.let { amount ->
                    Text(
                        "Monto pendiente: ${com.lotterynet.pro.core.format.formatWholeMoney(amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "El pago utilizará exactamente el monto confirmado por el proveedor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun parseCatalog(response: JSONObject, module: ServicesGamesModule): List<CatalogRow> {
    val array = response.optJSONArray("items") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                CatalogRow(
                    providerId = item.optString("providerId"),
                    productId = item.optString("productId"),
                    name = item.optString("name").ifBlank { "Producto" },
                    categoryId = item.optString("categoryId"),
                    price = item.optDouble("price", 0.0),
                    serviceType = item.optString("serviceType"),
                    logoUrl = item.optString("logoUrl"),
                    logoAssetKey = item.optString("logoAssetKey").let { key ->
                        key.trim().let { normalized ->
                            val normalizedAsset = when {
                                normalized.isBlank() -> ""
                                normalized.substringAfterLast('.', "").lowercase() in
                                    setOf("svg", "png", "jpg", "jpeg", "webp") -> normalized
                                else -> "$normalized.svg"
                            }
                            normalizedAsset.ifBlank {
                                localCatalogLogo(
                                    module = module.wireValue,
                                    providerId = item.optString("providerId"),
                                    name = item.optString("name"),
                                )
                            }
                        }
                    },
                    secondaryLogoAssetKey = item.optString("secondaryLogoAssetKey").let { key ->
                        key.trim().let { normalized ->
                            when {
                                normalized.isBlank() -> ""
                                normalized.substringAfterLast('.', "").lowercase() in
                                    setOf("svg", "png", "jpg", "jpeg", "webp") -> normalized
                                else -> "$normalized.svg"
                            }
                        }
                    },
                    categoryKey = item.optString("categoryKey"),
                    categoryLabel = item.optString("categoryLabel"),
                ),
            )
        }
    }
}

private fun localCatalogLogo(module: String, providerId: String, name: String): String {
    val key = listOf(providerId, name)
        .joinToString(" ")
        .lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
    return if (module == ServicesGamesModule.VIDEO_GAMES.wireValue) {
        when {
            "delta_force" in key || "deltaforce" in key -> "video-games/delta_force.png"
            "free_fire" in key || "freefire" in key -> "video-games/free_fire.png"
            "minecraft" in key -> "video-games/minecraft.svg"
            "roblox" in key -> "video-games/roblox.png"
            else -> ""
        }
    } else {
        when {
            "luz_y_fuerza" in key -> "services/luz_y_fuerza.jpg"
            "ceb" in key -> "services/ceb.png"
            "starcable" in key || "star_cable" in key -> "services/starcable.png"
            "skymax" in key -> "services/skymax.png"
            "aster" in key -> "services/aster.png"
            "coaarom" in key -> "services/coaarom.jpg"
            else -> ""
        }
    }
}
