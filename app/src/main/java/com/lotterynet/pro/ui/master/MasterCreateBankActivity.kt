package com.lotterynet.pro.ui.master

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.master.CreateBankRequest
import com.lotterynet.pro.core.master.CreateBankResult
import com.lotterynet.pro.core.master.IssuedCredentialServerVerifier
import com.lotterynet.pro.core.master.MasterCloudSyncCoordinator
import com.lotterynet.pro.core.master.MasterBankProvisioner
import com.lotterynet.pro.core.master.buildIssuedCredentialsShareText
import com.lotterynet.pro.core.master.resolveCashierCountFromProfileTotal
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.model.AuditEntry
import com.lotterynet.pro.core.model.SystemAlert
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.storage.LocalAlertsRepository
import com.lotterynet.pro.core.storage.LocalAuditRepository
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import com.lotterynet.pro.core.storage.LocalMasterConfigRepository
import com.lotterynet.pro.core.storage.LocalPresenceRepository
import com.lotterynet.pro.core.storage.LocalRechargeLimitRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersDeletedRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.users.SupabaseUsersRemoteStore
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactSegmentedSelector
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.CompactTextInput
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.openMasterHome
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal data class MasterCreateBankLayoutContract(
    val compactCredentials: Boolean,
    val shortenSectionMeta: Boolean,
)

internal fun resolveMasterCreateBankLayout(windowMode: LotteryNetWindowMode): MasterCreateBankLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS,
        LotteryNetWindowMode.POS_TIGHT -> MasterCreateBankLayoutContract(
            compactCredentials = true,
            shortenSectionMeta = true,
        )
        LotteryNetWindowMode.TABLET,
        LotteryNetWindowMode.WIDE -> MasterCreateBankLayoutContract(
            compactCredentials = false,
            shortenSectionMeta = false,
        )
    }
}

internal enum class MasterCreateBankSegment(val label: String) {
    BANK("Banca"),
    USERS("Usuarios"),
    SUMMARY("Resumen"),
}

internal fun masterCreateBankSegmentOptions(): List<QuickFilterChip> {
    return MasterCreateBankSegment.entries.map { QuickFilterChip(it.name, it.label) }
}

internal fun masterCreateBankTerritoryOptions(): List<QuickFilterChip> {
    return listOf(
        QuickFilterChip("RD", "RD"),
        QuickFilterChip("USA", "USA"),
    )
}

internal fun masterCreateBankStatusLabel(created: Boolean): String {
    return if (created) "Credenciales listas" else "Pendiente"
}

class MasterCreateBankActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.MASTER_CREATE_BANK)) return
        val session = activeSession ?: return
        val usersRepository = LocalUsersRepository(this)
        val auditRepository = LocalAuditRepository(this)
        val alertsRepository = LocalAlertsRepository(this)
        val deletedRepository = LocalUsersDeletedRepository(this)
        val presenceRepository = LocalPresenceRepository(this)
        val masterConfigRepository = LocalMasterConfigRepository(this)
        val rechargeLimitRepository = LocalRechargeLimitRepository(this)
        val cashierSalesLimitRepository = LocalCashierSalesLimitRepository(this)
        val provisioner = MasterBankProvisioner(usersRepository)
        val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        val cloudSyncCoordinator = MasterCloudSyncCoordinator(
            usersRepository = usersRepository,
            deletedRepository = deletedRepository,
            auditRepository = auditRepository,
            alertsRepository = alertsRepository,
            presenceRepository = presenceRepository,
            masterConfigRepository = masterConfigRepository,
            rechargeLimitRepository = rechargeLimitRepository,
            cashierSalesLimitRepository = cashierSalesLimitRepository,
            usersRemoteStore = SupabaseUsersRemoteStore(
                bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
            ),
        )
        usersRepository.touchSession(session)
        val credentialServerVerifier = IssuedCredentialServerVerifier(usersRepository)

        setContent {
            LotteryNetComposeTheme {
                MasterCreateBankRoute(
                    onBack = { finish() },
                    onCreate = { request ->
                        val previousUsersPayload = usersRepository.exportPayloadJson()
                        val result = provisioner.createBank(request)
                        val syncResult = runBlocking {
                            withContext(Dispatchers.IO) {
                                cloudSyncCoordinator.sync()
                            }
                        }
                        if (!syncResult.ok) {
                            usersRepository.cacheRawPayload(previousUsersPayload)
                            throw IllegalStateException("No se creó la banca: el servidor no confirmó guardado. ${syncResult.message}")
                        }
                        runBlocking {
                            withContext(Dispatchers.IO) {
                                credentialServerVerifier.ensureJwtReady(result.issuedCredentials)
                            }
                        }
                        val timestamp = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date())
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestamp,
                                    user = session.username,
                                    role = session.role.name.lowercase(),
                                    action = "CREAR_ADMIN",
                                    detail = "Admin: ${result.admin.user} Banca: ${result.admin.banca}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        alertsRepository.saveAlerts(
                            listOf(
                                SystemAlert(
                                    id = "alert-${System.currentTimeMillis()}",
                                    timestampLabel = timestamp,
                                    type = "nuevo_admin",
                                    message = "Nuevo admin creado: ${result.admin.banca ?: result.admin.user}",
                                    level = "info",
                                    read = false,
                                )
                            ) + alertsRepository.getAlerts()
                        )
                        result
                    },
                )
            }
        }
        } catch (error: Throwable) {
            NativeCrashReporter(this).recordHandled("MasterCreateBankActivity.onCreate", error)
            Toast.makeText(this, "Crear banca fallo al abrir. Volviendo al menu.", Toast.LENGTH_LONG).show()
            openMasterHome(this)
            finish()
        }
    }
}

@Composable
private fun MasterCreateBankRoute(
    onBack: () -> Unit,
    onCreate: (CreateBankRequest) -> CreateBankResult,
) {
    var ownerName by rememberSaveable { mutableStateOf("") }
    var bankName by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var cashierPrefix by rememberSaveable { mutableStateOf("") }
    var profileCountText by rememberSaveable { mutableStateOf("20") }
    var territory by rememberSaveable { mutableStateOf("RD") }
    var baseBalanceText by rememberSaveable { mutableStateOf("10000") }
    var selectedSegmentName by rememberSaveable { mutableStateOf(MasterCreateBankSegment.BANK.name) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var issued by remember { mutableStateOf<CreateBankResult?>(null) }

    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    val layout = remember(visual.windowMode) { resolveMasterCreateBankLayout(visual.windowMode) }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = visual.sizes.screenPaddingH, vertical = visual.sizes.screenPaddingV),
            verticalArrangement = Arrangement.spacedBy(visual.sizes.sectionGap),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = visual.sizes.screenPaddingV),
        ) {
            item {
                ScreenHeaderPanel(
                    title = "Crear banca",
                    subtitle = "Provisiona admin y cajeros desde master",
                    onBack = onBack,
                    badgeLabel = territory,
                    badgeTone = MaterialTheme.colorScheme.primary,
                )
            }
            item {
                CompactPanel {
                    val profileTotal = profileCountText.toIntOrNull() ?: 20
                    SectionHeader(title = "Organización", meta = masterCreateBankStatusLabel(issued != null))
                    CompactStatusBadge(
                        label = masterCreateBankStatusLabel(issued != null),
                        tone = if (issued != null) visual.colors.gain else visual.colors.warning,
                    )
                    CompactSegmentedSelector(
                        options = masterCreateBankSegmentOptions(),
                        selectedId = selectedSegmentName,
                        onSelected = { selectedSegmentName = it },
                        columns = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SectionHeader(
                        title = "Datos principales",
                        meta = if (layout.shortenSectionMeta) {
                            "$profileTotal perf."
                        } else {
                            "$profileTotal perfiles: 1 admin + ${resolveCashierCountFromProfileTotal(profileTotal)} cajeros"
                        },
                    )
                    CompactTextInput(value = ownerName, onValueChange = { ownerName = it }, modifier = Modifier.fillMaxWidth(), label = "Responsable principal")
                    CompactTextInput(value = bankName, onValueChange = { bankName = it }, modifier = Modifier.fillMaxWidth(), label = "Nombre de la banca")
                    CompactTextInput(value = address, onValueChange = { address = it }, modifier = Modifier.fillMaxWidth(), label = "Dirección")
                    CompactTextInput(value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), label = "Teléfono")
                }
            }
            item {
                CompactPanel(alt = true) {
                    SectionHeader(title = "Credenciales y caja", meta = if (layout.shortenSectionMeta) "$territory · ${baseBalanceText.ifBlank { "0" }}" else "$territory · caja ${baseBalanceText.ifBlank { "0" }}")
                    CompactTextInput(value = cashierPrefix, onValueChange = { cashierPrefix = it.lowercase(Locale.getDefault()) }, modifier = Modifier.fillMaxWidth(), label = "Prefijo de cajeros")
                    CompactTextInput(
                        label = "Total perfiles (admin incluido)",
                        value = profileCountText,
                        onValueChange = { profileCountText = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Number,
                    )
                    CompactSegmentedSelector(
                        options = masterCreateBankTerritoryOptions(),
                        selectedId = territory,
                        onSelected = { territory = it },
                        columns = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CompactTextInput(
                        label = "Caja base del equipo",
                        value = baseBalanceText,
                        onValueChange = { next -> baseBalanceText = next.filter { it.isDigit() || it == '.' } },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Decimal,
                    )
                    CompactActionButton(
                        label = "Crear banca y cajeros",
                        onClick = {
                            runCatching {
                                onCreate(
                                    CreateBankRequest(
                                        ownerName = ownerName,
                                        bankName = bankName,
                                        address = address,
                                        phone = phone,
                                        cashierPrefix = cashierPrefix,
                                        cashierCount = resolveCashierCountFromProfileTotal(profileCountText.toIntOrNull() ?: 20),
                                        territory = territory,
                                        baseBalance = baseBalanceText.toDoubleOrNull() ?: 0.0,
                                    )
                                )
                            }.onSuccess {
                                issued = it
                                status = "Banca creada y guardada en servidor."
                            }.onFailure {
                                status = it.message ?: "No se pudo crear la banca."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        active = true,
                        icon = Icons.Rounded.AdminPanelSettings,
                    )
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                    }
                }
            }
            issued?.let { result ->
                item {
                    CompactPanel {
                        SectionHeader(title = "Credenciales emitidas", meta = "${result.cashiers.size} cajeros")
                        Text(
                            result.admin.banca ?: result.admin.user,
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                        )
                        CompactActionButton(
                            label = "Compartir credenciales",
                            onClick = {
                                val payload = buildIssuedCredentialsShareText(result)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Credenciales ${result.admin.banca ?: result.admin.user}")
                                    putExtra(Intent.EXTRA_TEXT, payload)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Compartir credenciales"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            active = true,
                            icon = Icons.Rounded.Share,
                        )
                    }
                }
                items(result.issuedCredentials, key = { it.role.name + it.username }) { credential ->
                    CredentialRow(credential.displayName, credential.username, credential.password, credential.role, compact = layout.compactCredentials)
                }
            }
        }
    }
    }
}

@Composable
private fun CredentialRow(
    displayName: String,
    username: String,
    password: String,
    role: UserRole,
    compact: Boolean,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(alt = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(displayName, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink)
            if (compact) {
                Text(
                    "${role.name.lowercase(Locale.getDefault())} · $username",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.muted,
                    fontFamily = FontFamily.Monospace,
                )
                Text("Clave $password", style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace)
            } else {
                Text(role.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                Text("Usuario: $username", style = MaterialTheme.typography.bodySmall, color = visual.colors.muted, fontFamily = FontFamily.Monospace)
                Text("Clave: $password", style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
