package com.lotterynet.pro.ui.master

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.servicesgames.ServicesGamesModule
import com.lotterynet.pro.core.storage.LocalMasterConfigRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.MasterServicesGamesSettings
import com.lotterynet.pro.core.storage.encodeMasterServicesGamesSettings
import com.lotterynet.pro.core.storage.servicesGamesRemoteKey
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Master-only scope editor. It does not mutate recharge, lottery, sports, or report data. */
class MasterServicesGamesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = LocalSessionRepository(this).getActiveSession()
        if (session?.role != UserRole.MASTER) {
            finish()
            return
        }
        val configRepository = LocalMasterConfigRepository(this)
        val users = LocalUsersRepository(this)
        val tokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        val remoteStore = SupabaseMasterConfigRemoteStore(
            bearerTokenProvider = { tokenProvider.freshAccessToken() },
        )
        setContent {
            LotteryNetComposeTheme {
                var activeAdmins by remember {
                    mutableStateOf(users.getAdmins().filter(UserAccount::active))
                }
                var activeCashiers by remember {
                    mutableStateOf(users.getCashiers().filter(UserAccount::active))
                }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            activeAdmins = users.getAdmins().filter(UserAccount::active)
                            activeCashiers = users.getCashiers().filter(UserAccount::active)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                MasterServicesGamesRoute(
                    admins = activeAdmins,
                    cashiers = activeCashiers,
                    initialServices = remember { configRepository.getServicesGamesSettings(ServicesGamesModule.SERVICES) },
                    initialGames = remember { configRepository.getServicesGamesSettings(ServicesGamesModule.VIDEO_GAMES) },
                    onBack = { finish() },
                    onSave = { next ->
                        val normalized = next.copy(
                            updatedAtEpochMs = System.currentTimeMillis(),
                            updatedBy = session.username,
                        )
                        configRepository.saveServicesGamesSettings(normalized)
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                remoteStore.upsertJsonValue(
                                    servicesGamesRemoteKey(normalized.module),
                                    encodeMasterServicesGamesSettings(normalized),
                                )
                            }.isSuccess
                        }
                        Toast.makeText(
                            this@MasterServicesGamesActivity,
                            if (ok) "${normalized.module.label} guardado en servidor." else "Guardado local; servidor no respondió.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        ok
                    },
                    onLoadRemote = { module ->
                        withContext(Dispatchers.IO) {
                            runCatching {
                                remoteStore.fetchValue(servicesGamesRemoteKey(module))
                                    ?.toString()
                                    ?.let { raw ->
                                        com.lotterynet.pro.core.storage.decodeMasterServicesGamesSettings(module, raw)
                                    }
                            }.getOrNull()
                        }
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MasterServicesGamesRoute(
    admins: List<UserAccount>,
    cashiers: List<UserAccount>,
    initialServices: MasterServicesGamesSettings,
    initialGames: MasterServicesGamesSettings,
    onBack: () -> Unit,
    onSave: suspend (MasterServicesGamesSettings) -> Boolean,
    onLoadRemote: suspend (ServicesGamesModule) -> MasterServicesGamesSettings?,
) {
    var services by remember { mutableStateOf(initialServices) }
    var games by remember { mutableStateOf(initialGames) }
    var selectedModule by remember { mutableStateOf(ServicesGamesModule.SERVICES) }
    var selectedAdminKey by remember { mutableStateOf(admins.firstOrNull()?.primaryAccountKey().orEmpty()) }
    var adminMenuExpanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Control local listo.") }
    var remotelyLoadedModules by remember { mutableStateOf<Set<ServicesGamesModule>>(emptySet()) }
    var dirtyModules by remember { mutableStateOf<Set<ServicesGamesModule>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val selected = if (selectedModule == ServicesGamesModule.SERVICES) services else games
    val selectedAdmin = admins.firstOrNull { it.primaryAccountKey().equals(selectedAdminKey, ignoreCase = true) }
    val selectedAdminCashiers = selectedAdmin?.let { admin ->
        cashiers.filter { it.belongsToAdmin(admin) }
    }.orEmpty()

    fun update(next: MasterServicesGamesSettings) {
        if (next.module == ServicesGamesModule.SERVICES) services = next else games = next
        dirtyModules = dirtyModules + next.module
    }

    LaunchedEffect(selectedModule) {
        if (selectedModule in remotelyLoadedModules) return@LaunchedEffect
        val remote = onLoadRemote(selectedModule)
        if (remote != null) {
            if (remote.module == ServicesGamesModule.SERVICES) services = remote else games = remote
            remotelyLoadedModules = remotelyLoadedModules + selectedModule
            dirtyModules = dirtyModules - selectedModule
            status = "Control remoto actualizado."
        } else {
            status = "Sin confirmación remota; se conserva la configuración local."
        }
    }

    LaunchedEffect(admins, selectedAdminKey) {
        if (admins.none { it.primaryAccountKey().equals(selectedAdminKey, ignoreCase = true) }) {
            selectedAdminKey = admins.firstOrNull()?.primaryAccountKey().orEmpty()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Servicios y videojuegos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Acceso por usuario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Master habilita cada módulo y decide qué admin o cajero puede verlo. No se modifica la cartera de Recargas ni el reporte existente.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                PrimaryTabRow(
                    selectedTabIndex = if (selectedModule == ServicesGamesModule.SERVICES) 0 else 1,
                ) {
                    Tab(
                        selected = selectedModule == ServicesGamesModule.SERVICES,
                        onClick = { selectedModule = ServicesGamesModule.SERVICES },
                        text = { Text("Servicios") },
                        icon = { Icon(Icons.Rounded.Extension, contentDescription = null) },
                    )
                    Tab(
                        selected = selectedModule == ServicesGamesModule.VIDEO_GAMES,
                        onClick = { selectedModule = ServicesGamesModule.VIDEO_GAMES },
                        text = { Text("Videojuegos") },
                        icon = { Icon(Icons.Rounded.SportsEsports, contentDescription = null) },
                    )
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(selectedModule.label, fontWeight = FontWeight.Bold)
                            Text(if (selected.enabled) "Disponible para los usuarios seleccionados" else "Desactivado", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = selected.enabled,
                            onCheckedChange = { update(selected.copy(enabled = it)) },
                        )
                    }
                }
            }
            item {
                Text("Admin autorizado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = adminMenuExpanded,
                    onExpandedChange = { adminMenuExpanded = !adminMenuExpanded },
                ) {
                    OutlinedTextField(
                        value = selectedAdmin?.adminDisplayName() ?: "Sin admins disponibles",
                        onValueChange = {},
                        readOnly = true,
                        enabled = admins.isNotEmpty(),
                        label = { Text("Seleccionar admin") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = adminMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                    )
                    ExposedDropdownMenu(
                        expanded = adminMenuExpanded,
                        onDismissRequest = { adminMenuExpanded = false },
                    ) {
                        admins.forEach { admin ->
                            DropdownMenuItem(
                                text = { Text(admin.adminDisplayName()) },
                                onClick = {
                                    selectedAdminKey = admin.primaryAccountKey()
                                    adminMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            item {
                val admin = selectedAdmin
                if (admin == null) {
                    Text("No hay admins activos para configurar.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val adminKey = admin.primaryAccountKey()
                    Text("Permisos de ${admin.adminDisplayName()}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ScopeRow(
                        label = "Habilitar para este admin",
                        checked = adminKey in selected.allowedAdminKeys,
                        onCheckedChange = { checked ->
                            update(selected.copy(allowedAdminKeys = selected.allowedAdminKeys.toggle(adminKey, checked)))
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Cajeros de este admin", style = MaterialTheme.typography.bodySmall)
                    if (selectedAdminCashiers.isEmpty()) {
                        Text("Este admin no tiene cajeros activos asignados.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        ScopeRow(
                            label = "Todos los cajeros de ${admin.adminDisplayName()}",
                            checked = adminKey in selected.cashierAdminKeys,
                            onCheckedChange = { checked ->
                                update(
                                    updateCashierAdminScope(
                                        settings = selected,
                                        adminKey = adminKey,
                                        cashierKeysForAdmin = selectedAdminCashiers
                                            .map { it.primaryAccountKey() }
                                            .toSet(),
                                        enabled = checked,
                                    ),
                                )
                            },
                        )
                        selectedAdminCashiers.forEach { account ->
                            val key = account.primaryAccountKey()
                            val includedByAdmin = adminKey in selected.cashierAdminKeys
                            ScopeRow(
                                label = account.adminDisplayName(),
                                checked = key in selected.allowedCashierKeys || includedByAdmin,
                                enabled = !includedByAdmin,
                                onCheckedChange = { checked ->
                                    update(selected.copy(allowedCashierKeys = selected.allowedCashierKeys.toggle(key, checked)))
                                },
                            )
                        }
                    }
                }
            }
            item {
                Text("Agregar fondo es una operación exclusiva de Admin y no aparece como permiso de cajero.", style = MaterialTheme.typography.bodySmall)
                Text(
                    status,
                    color = if (status.contains("servidor", ignoreCase = true) || status.contains("actualizado", ignoreCase = true)) {
                        MaterialTheme.colorScheme.primary
                    } else if (status.contains("pendiente", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (selected.module in dirtyModules) {
                        "Hay cambios sin confirmar para ${selected.module.label}."
                    } else {
                        "No hay cambios pendientes en ${selected.module.label}."
                    },
                    color = if (selected.module in dirtyModules) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        enabled = !saving && selected.module in dirtyModules,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        onClick = {
                        scope.launch {
                            if (saving) return@launch
                            val moduleToSave = selected.module
                            val settingsToSave = selected
                            saving = true
                            status = "Guardando ${moduleToSave.label}..."
                            try {
                                val savedRemotely = onSave(settingsToSave)
                                status = if (savedRemotely) {
                                    dirtyModules = dirtyModules - moduleToSave
                                    "Cambios guardados localmente y confirmados en servidor."
                                } else {
                                    "Guardado local; servidor pendiente de conexión."
                                }
                            } finally {
                                saving = false
                            }
                        }
                    }) { Text(if (saving) "Guardando..." else "Guardar cambios") }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun ScopeRow(label: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                    disabledCheckedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                ),
            )
            Text(label, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun UserAccount.primaryAccountKey(): String = id.ifBlank { user }.trim()

private fun UserAccount.adminDisplayName(): String = displayName?.takeIf { it.isNotBlank() } ?: user

private fun UserAccount.belongsToAdmin(admin: UserAccount): Boolean {
    val adminKeys = listOf(admin.id, admin.user, admin.banca, admin.ownerName)
        .mapNotNull { it?.trim() }
        .filter(String::isNotBlank)
        .map(String::lowercase)
        .toSet()
    val ownerKeys = listOf(adminId, adminUser, banca)
        .mapNotNull { it?.trim() }
        .filter(String::isNotBlank)
        .map(String::lowercase)
        .toSet()
    return ownerKeys.any { it in adminKeys }
}

private fun Set<String>.toggle(value: String, checked: Boolean): Set<String> {
    if (value.isBlank()) return this
    return if (checked) this + value else this - value
}

internal fun updateCashierAdminScope(
    settings: MasterServicesGamesSettings,
    adminKey: String,
    cashierKeysForAdmin: Set<String>,
    enabled: Boolean,
): MasterServicesGamesSettings {
    if (adminKey.isBlank()) return settings
    return if (enabled) {
        settings.copy(cashierAdminKeys = settings.cashierAdminKeys + adminKey)
    } else {
        settings.copy(
            cashierAdminKeys = settings.cashierAdminKeys - adminKey,
            allowedCashierKeys = settings.allowedCashierKeys - cashierKeysForAdmin,
        )
    }
}
