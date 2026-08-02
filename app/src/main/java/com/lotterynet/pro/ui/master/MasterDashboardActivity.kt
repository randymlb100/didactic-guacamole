package com.lotterynet.pro.ui.master

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lotterynet.pro.core.master.IssuedCredential
import com.lotterynet.pro.core.master.IssuedCredentialServerVerifier
import com.lotterynet.pro.core.master.MasterBankManager
import com.lotterynet.pro.core.master.MasterCloudSyncCoordinator
import com.lotterynet.pro.core.master.MasterCloudSyncResult
import com.lotterynet.pro.core.master.MasterRechargeFundUpdateCoordinator
import com.lotterynet.pro.core.master.MasterRechargeFundUpdateResult
import com.lotterynet.pro.core.master.MasterRechargeBalanceUpdateCoordinator
import com.lotterynet.pro.core.master.MasterServerProbeResult
import com.lotterynet.pro.core.master.MasterServerStatusChecker
import com.lotterynet.pro.core.master.MasterUserManager
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.model.AuditEntry
import com.lotterynet.pro.core.model.SystemAlert
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.operations.cashierDisplayLabel
import com.lotterynet.pro.core.realtime.LotterynetRealtimeClient
import com.lotterynet.pro.core.recharge.recargasrapidas.RecargasRapidasBackendClient
import com.lotterynet.pro.core.recharge.recargasrapidas.RecargasRapidasCredentialScope
import com.lotterynet.pro.core.sync.TicketRefreshGovernor
import com.lotterynet.pro.core.sync.ticketRefreshGovernorKey
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
import com.lotterynet.pro.core.users.UserPasswordBackendClient
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactLoadingState
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactSegmentedSelector
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.CompactTextInput
import com.lotterynet.pro.ui.common.CurrentScopeDropdownCard
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.MetricStrip
import com.lotterynet.pro.ui.common.MetricStripItem
import com.lotterynet.pro.ui.common.QuickFilterChip
import com.lotterynet.pro.ui.common.ScreenHeaderPanel
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.lotteryNetTextFieldColors
import com.lotterynet.pro.ui.common.openMasterHome
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.admin.AdminAuditActivity
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.navigation.startSafeNativeDestination
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal data class MasterDashboardLayoutContract(
    val compactSummary: Boolean,
    val compactBanks: Boolean,
    val useCompactRows: Boolean,
    val showLargeCards: Boolean,
    val splitServerActions: Boolean,
    val shortBankActionLabels: Boolean,
)

internal fun resolveMasterDashboardLayout(windowMode: LotteryNetWindowMode): MasterDashboardLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS,
        LotteryNetWindowMode.POS_TIGHT -> MasterDashboardLayoutContract(
            compactSummary = true,
            compactBanks = true,
            useCompactRows = true,
            showLargeCards = false,
            splitServerActions = true,
            shortBankActionLabels = true,
        )
        LotteryNetWindowMode.TABLET,
        LotteryNetWindowMode.WIDE -> MasterDashboardLayoutContract(
            compactSummary = false,
            compactBanks = false,
            useCompactRows = false,
            showLargeCards = true,
            splitServerActions = false,
            shortBankActionLabels = false,
        )
    }
}

internal enum class MasterIssuedCredentialsMode(val title: String) {
    PASSWORD_CHANGED("Clave actualizada"),
    CREDENTIALS_REGENERATED("Claves nuevas generadas"),
}

internal fun masterCredentialResetActionLabel(short: Boolean): String {
    return if (short) "Gen." else "Generar claves"
}

internal fun masterCashierDropdownActionLabel(short: Boolean): String {
    return if (short) "Caj." else "Cajeros"
}

internal fun masterCashierGroupPasswordActionLabel(short: Boolean): String {
    return if (short) "Todos" else "Clave a todos"
}

internal enum class MasterBankFilter(val label: String) {
    ALL("Todas"),
    ACTIVE("Activas"),
    BLOCKED("Bloqueadas"),
    ISSUES("Con problemas"),
}

internal fun masterDashboardSectionTitles(): List<String> {
    return masterPrimaryDestinations().map(MasterDestination::label)
}

private fun masterDashboardSectionOptions(): List<QuickFilterChip> =
    masterPrimaryDestinations().map { QuickFilterChip(it.id, it.label) }

internal fun masterBankFilterOptions(): List<QuickFilterChip> {
    return MasterBankFilter.entries.map { filter -> QuickFilterChip(filter.name, filter.label) }
}

internal fun masterDangerActionLabels(short: Boolean): List<String> {
    return if (short) {
        listOf("Bloq.", "Borra", "Gen.")
    } else {
        listOf("Bloquear", "Borrar", "Generar claves")
    }
}

internal fun buildMasterIssuedCredentialsShareText(
    title: String,
    credentials: List<IssuedCredential>,
): String {
    return buildString {
        appendLine(title)
        appendLine("Total usuarios: ${credentials.size}")
        appendLine()
        credentials.forEachIndexed { index, credential ->
            appendLine("${credential.role.name}: ${credential.displayName}")
            appendLine("Usuario: ${credential.username}")
            appendLine("Clave: ${credential.password}")
            if (index < credentials.lastIndex) appendLine()
        }
    }.trim()
}

class MasterDashboardActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
        val activeSession = LocalSessionRepository(this).getActiveSession()
        if (redirectIfNativeDestinationBlocked(this, activeSession?.role, NativeDestination.MASTER_DASHBOARD)) return
        val session = activeSession ?: return
        val usersRepository = LocalUsersRepository(this)
        val auditRepository = LocalAuditRepository(this)
        val alertsRepository = LocalAlertsRepository(this)
        val presenceRepository = LocalPresenceRepository(this)
        val masterConfigRepository = LocalMasterConfigRepository(this)
        val rechargeLimitRepository = LocalRechargeLimitRepository(this)
        val cashierSalesLimitRepository = LocalCashierSalesLimitRepository(this)
        val deletedUsersRepository = LocalUsersDeletedRepository(this)
        val serverStatusChecker = MasterServerStatusChecker()
        val sessionTokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        val usersRemoteStore = SupabaseUsersRemoteStore(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
        )
        val masterCloudSyncCoordinator = MasterCloudSyncCoordinator(
            usersRepository = usersRepository,
            deletedRepository = deletedUsersRepository,
            auditRepository = auditRepository,
            alertsRepository = alertsRepository,
            presenceRepository = presenceRepository,
            masterConfigRepository = masterConfigRepository,
            rechargeLimitRepository = rechargeLimitRepository,
            cashierSalesLimitRepository = cashierSalesLimitRepository,
            backendStore = SupabaseMasterConfigRemoteStore(
                bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
            ),
            usersRemoteStore = usersRemoteStore,
        )
        val rechargeFundCoordinator = MasterRechargeFundUpdateCoordinator(
            writeLocal = usersRepository::updateAccount,
            writeRemote = usersRemoteStore::updateMasterRechargeFund,
        )
        val rechargeBalanceCoordinator = MasterRechargeBalanceUpdateCoordinator(
            writeLocal = usersRepository::updateAccount,
            writeRemote = usersRemoteStore::updateRechargeBalance,
        )
        val recargasRapidasBackend = RecargasRapidasBackendClient()
        val userPasswordBackendClient = UserPasswordBackendClient(
            bearerTokenProvider = { sessionTokenProvider.freshAccessToken() },
        )
        val credentialServerVerifier = IssuedCredentialServerVerifier(usersRepository)
        val manager = MasterBankManager(usersRepository, deletedUsersRepository)
        val userManager = MasterUserManager(usersRepository)
        usersRepository.touchSession(session)

        fun queueMasterAutoSync(reason: String) {
            Thread {
                val syncResult = masterCloudSyncCoordinator.sync()
                if (!syncResult.ok) {
                    val timestampLabel = auditTimestamp()
                    alertsRepository.saveAlerts(
                        listOf(
                            SystemAlert(
                                id = "alert-${System.currentTimeMillis()}",
                                timestampLabel = timestampLabel,
                                type = "master_autosync_failed",
                                message = "Falló autosync master tras $reason: ${syncResult.message}",
                                level = "warning",
                                read = false,
                            )
                        ) + alertsRepository.getAlerts()
                    )
                }
            }.start()
        }

        fun syncCredentialChangeOrRollback(previousUsersPayload: String, reason: String) {
            val syncResult = runBlocking {
                withContext(Dispatchers.IO) {
                    masterCloudSyncCoordinator.sync()
                }
            }
            if (!syncResult.ok) {
                usersRepository.cacheRawPayload(previousUsersPayload)
                throw IllegalStateException("No se emitieron credenciales: el servidor no confirmó $reason. ${syncResult.message}")
            }
        }

        fun ensureCredentialJwtOrRollback(previousUsersPayload: String, credentials: List<IssuedCredential>) {
            runCatching {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        credentialServerVerifier.ensureJwtReady(credentials)
                    }
                }
            }.onFailure { error ->
                usersRepository.cacheRawPayload(previousUsersPayload)
                throw IllegalStateException(
                    "No se emitieron credenciales: el servidor no genero JWT. ${error.message ?: "Servidor no disponible"}",
                    error,
                )
            }
        }

        setContent {
            LotteryNetComposeTheme {
                MasterDashboardRoute(
                    admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                    cashiers = usersRepository.getCashiers(),
                    initialMasterRecargaLimit = rechargeLimitRepository.getSettings().masterPerTx,
                    onBack = {
                        openMasterHome(this)
                        finish()
                    },
                    onOpenCreate = { startSafeNativeDestination(this, session.role, NativeDestination.MASTER_CREATE_BANK) },
                    onOpenAudit = { startSafeNativeDestination(this, session.role, NativeDestination.ADMIN_AUDIT) },
                    onOpenModules = { startSafeNativeDestination(this, session.role, NativeDestination.MASTER_SERVICES_GAMES) },
                    onSaveMasterRecargaLimit = { value ->
                        val current = rechargeLimitRepository.getSettings()
                        rechargeLimitRepository.saveSettings(current.copy(masterPerTx = value))
                        val timestampLabel = auditTimestamp()
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestampLabel,
                                    user = session.username,
                                    role = session.role.name.lowercase(Locale.US),
                                    action = "CAMBIAR_TOPE_MASTER_RECARGA",
                                    detail = "Tope master recarga: ${masterMoney(value)}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        alertsRepository.saveAlerts(
                            listOf(
                                SystemAlert(
                                    id = "alert-${System.currentTimeMillis()}",
                                    timestampLabel = timestampLabel,
                                    type = "master_recarga_limit",
                                    message = "Tope master de recarga actualizado a ${masterMoney(value)}.",
                                    level = "warning",
                                    read = false,
                                )
                            ) + alertsRepository.getAlerts()
                        )
                        queueMasterAutoSync("guardar tope master")
                        value
                    },
                    onProbeServer = { callback ->
                        Thread {
                            val result = serverStatusChecker.probe()
                            val timestampLabel = auditTimestamp()
                            auditRepository.saveEntries(
                                listOf(
                                    AuditEntry(
                                        timestampLabel = timestampLabel,
                                        user = session.username,
                                        role = session.role.name.lowercase(Locale.US),
                                        action = "REVISAR_SERVIDOR_MASTER",
                                        detail = "${result.message} · ${result.detail}",
                                    )
                                ) + auditRepository.getEntries()
                            )
                            if (!result.ok) {
                                alertsRepository.saveAlerts(
                                    listOf(
                                        SystemAlert(
                                            id = "alert-${System.currentTimeMillis()}",
                                            timestampLabel = timestampLabel,
                                            type = "master_server_check_failed",
                                            message = "Master no pudo validar servidor: ${result.message}",
                                            level = "warning",
                                            read = false,
                                        )
                                    ) + alertsRepository.getAlerts()
                                )
                            }
                            runOnUiThread { callback(result) }
                        }.start()
                    },
                    onCheckRecargasRapidasWallet = { callback ->
                        Thread {
                            val label = runCatching {
                                val balance = recargasRapidasBackend.getWalletBalance()
                                balance?.let { "Cartera RR real: ${masterMoney(it.amount)}" }
                                    ?: "Cartera RR real: no disponible en respuesta del servidor."
                            }.getOrElse { error ->
                                error.message ?: "No se pudo consultar cartera RR."
                            }
                            runOnUiThread { callback(label) }
                        }.start()
                    },
                    onSaveDefaultRecargasRapidasCredentials = { username, password ->
                        runCatching {
                            recargasRapidasBackend.saveCredentials(
                                scope = RecargasRapidasCredentialScope.Default,
                                username = username,
                                password = password,
                                updatedBy = session.username,
                            )
                        }.fold(
                            onSuccess = { "Cuenta default RR guardada en backend: ${it.toDisplayLabel()}" },
                            onFailure = { "No se guardó cuenta default RR: ${it.message ?: "servidor no disponible"}" },
                        )
                    },
                    onSyncCloud = { callback ->
                        Thread {
                            val result = masterCloudSyncCoordinator.sync()
                            val mutation = MasterDashboardMutation(
                                admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                                cashiers = usersRepository.getCashiers(),
                                status = if (result.ok) {
                                    "Sincronización master completada."
                                } else {
                                    "Falló la sincronización master."
                                },
                            )
                            val timestampLabel = auditTimestamp()
                            auditRepository.saveEntries(
                                listOf(
                                    AuditEntry(
                                        timestampLabel = timestampLabel,
                                        user = session.username,
                                        role = session.role.name.lowercase(Locale.US),
                                        action = "SYNC_MASTER_CLOUD_NATIVE",
                                        detail = "${result.message} · ${result.detail}",
                                    )
                                ) + auditRepository.getEntries()
                            )
                            if (!result.ok) {
                                alertsRepository.saveAlerts(
                                    listOf(
                                        SystemAlert(
                                            id = "alert-${System.currentTimeMillis()}",
                                            timestampLabel = timestampLabel,
                                            type = "master_cloud_sync_failed",
                                        message = "Falló sync master: ${result.message}",
                                            level = "warning",
                                            read = false,
                                        )
                                    ) + alertsRepository.getAlerts()
                                )
                            }
                            runOnUiThread { callback(result, mutation) }
                        }.start()
                    },
                    onRefreshRemote = { callback ->
                        Thread {
                            val result = masterCloudSyncCoordinator.refreshRemoteSnapshot()
                            val mutation = MasterDashboardMutation(
                                admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                                cashiers = usersRepository.getCashiers(),
                                status = if (result.ok) {
                                    "Snapshot remoto cargado en master."
                                } else {
                                    "Falló la carga remota de master."
                                },
                            )
                            val timestampLabel = auditTimestamp()
                            auditRepository.saveEntries(
                                listOf(
                                    AuditEntry(
                                        timestampLabel = timestampLabel,
                                        user = session.username,
                                        role = session.role.name.lowercase(Locale.US),
                                        action = "REFRESH_MASTER_REMOTE_NATIVE",
                                        detail = "${result.message} · ${result.detail}",
                                    )
                                ) + auditRepository.getEntries()
                            )
                            runOnUiThread { callback(result, mutation) }
                        }.start()
                    },
                    onToggleBank = { admin ->
                        val result = manager.toggleBank(admin.id)
                        val timestampLabel = auditTimestamp()
                        val action = if (result.admin.active) "ACTIVAR_ADMIN" else "BLOQUEAR_ADMIN"
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestampLabel,
                                    user = session.username,
                                    role = session.role.name.lowercase(Locale.US),
                                    action = action,
                                    detail = "Admin: ${result.admin.user} · Cajeros impactados: ${result.affectedCashiers}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        alertsRepository.saveAlerts(
                            listOf(
                                SystemAlert(
                                    id = "alert-${System.currentTimeMillis()}",
                                    timestampLabel = timestampLabel,
                                    type = if (result.admin.active) "admin_activo" else "admin_bloqueado",
                                    message = "${result.admin.banca ?: result.admin.user} ${if (result.admin.active) "activada" else "bloqueada"} con ${result.affectedCashiers} cajero(s).",
                                    level = "warning",
                                    read = false,
                                )
                            ) + alertsRepository.getAlerts()
                        )
                        MasterDashboardMutation(
                            admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                            cashiers = usersRepository.getCashiers(),
                            status = "${result.admin.banca ?: result.admin.user} ${if (result.admin.active) "activada" else "bloqueada"}.",
                        ).also {
                            queueMasterAutoSync(if (result.admin.active) "activar banca" else "bloquear banca")
                        }
                    },
                    onSaveBankRechargeAccess = { admin, enabled, amount, onComplete ->
                        Thread {
                            val result = rechargeFundCoordinator.update(admin, enabled, amount)
                            val mutation = when (result) {
                                is MasterRechargeFundUpdateResult.Confirmed -> {
                                    val updated = result.account
                                    val timestampLabel = auditTimestamp()
                                    auditRepository.saveEntries(
                                        listOf(
                                            AuditEntry(
                                                timestampLabel = timestampLabel,
                                                user = session.username,
                                                role = session.role.name.lowercase(Locale.US),
                                                action = if (enabled) "ACTIVAR_RECARGAS_ADMIN" else "ACTUALIZAR_FONDO_RECARGAS_ADMIN",
                                                detail = "${updated.banca ?: updated.user}: servidor confirmó ${masterMoney(result.receipt.persistedAmount)}",
                                            )
                                        ) + auditRepository.getEntries()
                                    )
                                    MasterDashboardMutation(
                                        admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                                        cashiers = usersRepository.getCashiers(),
                                        status = masterFundSaveStatusLabel(MasterFundSaveState.CONFIRMED, result.receipt.persistedAmount),
                                    )
                                }
                                is MasterRechargeFundUpdateResult.Rejected -> MasterDashboardMutation(
                                    admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                                    cashiers = usersRepository.getCashiers(),
                                    status = masterFundSaveStatusLabel(MasterFundSaveState.ROLLED_BACK, amount),
                                )
                                is MasterRechargeFundUpdateResult.RolledBack -> MasterDashboardMutation(
                                    admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                                    cashiers = usersRepository.getCashiers(),
                                    status = "${masterFundSaveStatusLabel(MasterFundSaveState.ROLLED_BACK, amount)}: ${result.error.message ?: "servidor no disponible"}",
                                )
                            }
                            runOnUiThread { onComplete(mutation) }
                        }.start()
                    },
                    onAddBankRechargeBalance = { admin, amount, onComplete ->
                        Thread {
                            val result = rechargeBalanceCoordinator.add(admin, amount)
                            val mutation = when (result) {
                                is MasterRechargeFundUpdateResult.Confirmed -> MasterDashboardMutation(
                                    admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                                    cashiers = usersRepository.getCashiers(),
                                    status = "Saldo agregado: ${masterMoney(result.receipt.persistedAmount)} disponibles.",
                                )
                                is MasterRechargeFundUpdateResult.Rejected -> MasterDashboardMutation(
                                    admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                                    cashiers = usersRepository.getCashiers(),
                                    status = "Saldo no confirmado; se restauró el valor anterior.",
                                )
                                is MasterRechargeFundUpdateResult.RolledBack -> MasterDashboardMutation(
                                    admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                                    cashiers = usersRepository.getCashiers(),
                                    status = "No se agregó saldo: ${result.error.message ?: "servidor no disponible"}",
                                )
                            }
                            runOnUiThread { onComplete(mutation) }
                        }.start()
                    },
                    onSaveBankRecargasRapidasCredentials = { admin, username, password ->
                        val backendStatus = runCatching {
                            recargasRapidasBackend.saveCredentials(
                                scope = RecargasRapidasCredentialScope.Admin(admin.id),
                                username = username,
                                password = password,
                                updatedBy = session.username,
                            )
                        }
                        val updated = updateMasterRecargasRapidasCredentialStatus(
                            admin = admin,
                            usernameHint = backendStatus.getOrNull()?.usernameHint ?: username,
                        )
                        usersRepository.updateAccount(updated)
                        val timestampLabel = auditTimestamp()
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestampLabel,
                                    user = session.username,
                                    role = session.role.name.lowercase(Locale.US),
                                    action = "GUARDAR_RECARGAS_RAPIDAS_ADMIN",
                                    detail = "${updated.banca ?: updated.user}: cuenta Recargas Rapidas ${if (backendStatus.isSuccess) "backend lista" else "pendiente"}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        MasterDashboardMutation(
                            admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                            cashiers = usersRepository.getCashiers(),
                            status = backendStatus.fold(
                                onSuccess = { "${updated.banca ?: updated.user}: cuenta RR guardada en backend." },
                                onFailure = { "${updated.banca ?: updated.user}: no se guardó RR: ${it.message ?: "servidor no disponible"}" },
                            ),
                        ).also {
                            queueMasterAutoSync("guardar estado cuenta recargas rapidas admin")
                        }
                    },
                    onDeleteBank = { admin ->
                        val result = manager.deleteBank(admin.id)
                        val timestampLabel = auditTimestamp()
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestampLabel,
                                    user = session.username,
                                    role = session.role.name.lowercase(Locale.US),
                                    action = "BORRAR_ADMIN",
                                    detail = "Admin: ${result.admin.user} · Cajeros eliminados: ${result.removedCashiers}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        alertsRepository.saveAlerts(
                            listOf(
                                SystemAlert(
                                    id = "alert-${System.currentTimeMillis()}",
                                    timestampLabel = timestampLabel,
                                    type = "admin_borrado",
                                    message = "${result.admin.banca ?: result.admin.user} eliminada del local con ${result.removedCashiers} cajero(s).",
                                    level = "warning",
                                    read = false,
                                )
                            ) + alertsRepository.getAlerts()
                        )
                        MasterDashboardMutation(
                            admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                            cashiers = usersRepository.getCashiers(),
                            status = "${result.admin.banca ?: result.admin.user} eliminada del dispositivo.",
                        ).also {
                            queueMasterAutoSync("borrar banca")
                        }
                    },
                    onRegenerateCredentials = { admin ->
                        val previousUsersPayload = usersRepository.exportPayloadJson()
                        val result = manager.regenerateCredentials(admin.id)
                        syncCredentialChangeOrRollback(previousUsersPayload, "regenerar credenciales")
                        ensureCredentialJwtOrRollback(previousUsersPayload, result.issuedCredentials)
                        val timestampLabel = auditTimestamp()
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestampLabel,
                                    user = session.username,
                                    role = session.role.name.lowercase(Locale.US),
                                    action = "REGENERAR_CREDENCIALES",
                                    detail = "Admin: ${result.admin.user} · Usuarios emitidos: ${result.issuedCredentials.size}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        alertsRepository.saveAlerts(
                            listOf(
                                SystemAlert(
                                    id = "alert-${System.currentTimeMillis()}",
                                    timestampLabel = timestampLabel,
                                    type = "credenciales_regeneradas",
                                    message = "Credenciales regeneradas para ${result.admin.banca ?: result.admin.user}.",
                                    level = "info",
                                    read = false,
                                )
                            ) + alertsRepository.getAlerts()
                        )
                        MasterDashboardMutation(
                            admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                            cashiers = usersRepository.getCashiers(),
                            status = "Credenciales regeneradas para ${result.admin.banca ?: result.admin.user}.",
                            issuedCredentials = result.issuedCredentials,
                            issuedCredentialsMode = MasterIssuedCredentialsMode.CREDENTIALS_REGENERATED,
                        )
                    },
                    onAddCashiers = { admin, count, prefix ->
                        val previousUsersPayload = usersRepository.exportPayloadJson()
                        val result = userManager.addCashiers(admin.id, count, prefix)
                        syncCredentialChangeOrRollback(previousUsersPayload, "agregar cajeros")
                        ensureCredentialJwtOrRollback(previousUsersPayload, result.issuedCredentials)
                        val timestampLabel = auditTimestamp()
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestampLabel,
                                    user = session.username,
                                    role = session.role.name.lowercase(Locale.US),
                                    action = "AGREGAR_CAJEROS_MASTER",
                                    detail = "Admin: ${result.admin.user} · Cajeros nuevos: ${result.cashiers.size}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        alertsRepository.saveAlerts(
                            listOf(
                                SystemAlert(
                                    id = "alert-${System.currentTimeMillis()}",
                                    timestampLabel = timestampLabel,
                                    type = "cajeros_agregados",
                                    message = "${result.cashiers.size} cajero(s) agregado(s) a ${result.admin.banca ?: result.admin.user}.",
                                    level = "info",
                                    read = false,
                                )
                            ) + alertsRepository.getAlerts()
                        )
                        MasterDashboardMutation(
                            admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                            cashiers = usersRepository.getCashiers(),
                            status = "${result.cashiers.size} cajero(s) agregado(s) a ${result.admin.banca ?: result.admin.user}.",
                            issuedCredentials = result.issuedCredentials,
                            issuedCredentialsMode = MasterIssuedCredentialsMode.CREDENTIALS_REGENERATED,
                        )
                    },
                    onChangePassword = { idOrUser, password ->
                        val target = usersRepository.findByIdOrUser(idOrUser)
                            ?: throw IllegalArgumentException("No se encontró el usuario.")
                        val previousUsersPayload = usersRepository.exportPayloadJson()
                        runBlocking {
                            withContext(Dispatchers.IO) {
                                userPasswordBackendClient.changePassword(
                                    session = session,
                                    target = target,
                                    newPassword = password,
                                )
                            }
                        }
                        val result = userManager.changePassword(idOrUser, password)
                        ensureCredentialJwtOrRollback(previousUsersPayload, listOf(result.credential))
                        val timestampLabel = auditTimestamp()
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestampLabel,
                                    user = session.username,
                                    role = session.role.name.lowercase(Locale.US),
                                    action = "CAMBIAR_CLAVE_MASTER",
                                    detail = "Usuario: ${result.account.user} · Rol: ${result.account.role.name.lowercase(Locale.US)}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        alertsRepository.saveAlerts(
                            listOf(
                                SystemAlert(
                                    id = "alert-${System.currentTimeMillis()}",
                                    timestampLabel = timestampLabel,
                                    type = "clave_actualizada",
                                    message = "Clave actualizada para ${result.account.user}.",
                                    level = "info",
                                    read = false,
                                )
                            ) + alertsRepository.getAlerts()
                        )
                        MasterDashboardMutation(
                            admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                            cashiers = usersRepository.getCashiers(),
                            status = "Clave actualizada para ${result.account.user}.",
                            issuedCredentials = listOf(result.credential),
                            issuedCredentialsMode = MasterIssuedCredentialsMode.PASSWORD_CHANGED,
                        ).also {
                            queueMasterAutoSync("cambiar clave")
                        }
                    },
                    onChangeCashierGroupPassword = { admin, password ->
                        val previousUsersPayload = usersRepository.exportPayloadJson()
                        val backendResult = runBlocking {
                            withContext(Dispatchers.IO) {
                                userPasswordBackendClient.changeCashierGroupPassword(
                                    session = session,
                                    admin = admin,
                                    newPassword = password,
                                )
                            }
                        }
                        if (!backendResult.payloadConfirmed) {
                            throw IllegalStateException("El servidor no confirmó el cambio grupal.")
                        }
                        val result = userManager.changeCashierGroupPassword(admin.id, password)
                        syncCredentialChangeOrRollback(previousUsersPayload, "cambiar clave de cajeros")
                        ensureCredentialJwtOrRollback(previousUsersPayload, result.credentials)
                        val timestampLabel = auditTimestamp()
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestampLabel,
                                    user = session.username,
                                    role = session.role.name.lowercase(Locale.US),
                                    action = "CAMBIAR_CLAVE_CAJEROS_GRUPO",
                                    detail = "Admin: ${result.admin.user} · Cajeros: ${result.credentials.size}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        alertsRepository.saveAlerts(
                            listOf(
                                SystemAlert(
                                    id = "alert-${System.currentTimeMillis()}",
                                    timestampLabel = timestampLabel,
                                    type = "clave_cajeros_actualizada",
                                    message = "Clave actualizada para ${result.credentials.size} cajero(s) de ${result.admin.banca ?: result.admin.user}.",
                                    level = "info",
                                    read = false,
                                )
                            ) + alertsRepository.getAlerts()
                        )
                        MasterDashboardMutation(
                            admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                            cashiers = usersRepository.getCashiers(),
                            status = "Clave actualizada para ${result.credentials.size} cajero(s) de ${result.admin.banca ?: result.admin.user}.",
                            issuedCredentials = result.credentials,
                            issuedCredentialsMode = MasterIssuedCredentialsMode.PASSWORD_CHANGED,
                        )
                    },
                )
            }
        }
        } catch (error: Throwable) {
            NativeCrashReporter(this).recordHandled("MasterDashboardActivity.onCreate", error)
            Toast.makeText(this, "Master fallo al abrir. Volviendo al menu.", Toast.LENGTH_LONG).show()
            openMasterHome(this)
            finish()
        }
    }
}

private fun auditTimestamp(): String = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).format(Date())

internal fun sortMasterAdminsByCreation(admins: List<UserAccount>): List<UserAccount> {
    return admins.sortedWith(
        compareBy<UserAccount> { masterCreatedEpoch(it.createdLabel) }
            .thenBy { (it.banca ?: it.displayName ?: it.user).lowercase(Locale.US) }
            .thenBy { it.user.lowercase(Locale.US) },
    )
}

internal fun filterMasterAdminsForSelector(
    admins: List<UserAccount>,
    query: String,
): List<UserAccount> {
    val ordered = sortMasterAdminsByCreation(admins)
    val needle = query.trim().lowercase(Locale.US)
    if (needle.isBlank()) return ordered
    return ordered.filter { admin ->
        listOf(admin.user, admin.displayName, admin.banca, admin.ownerName)
            .filterNotNull()
            .any { it.lowercase(Locale.US).contains(needle) }
    }
}

internal fun filterMasterBanksForDashboard(
    admins: List<UserAccount>,
    query: String,
    filter: MasterBankFilter,
): List<UserAccount> {
    val ordered = sortMasterAdminsByCreation(admins)
    val needle = query.trim().lowercase(Locale.US)
    return ordered.filter { admin ->
        val matchesFilter = when (filter) {
            MasterBankFilter.ALL -> true
            MasterBankFilter.ACTIVE -> admin.active &&
                admin.rechargesEnabled &&
                !admin.recargasRapidasUsername.isNullOrBlank()
            MasterBankFilter.BLOCKED -> !admin.active
            MasterBankFilter.ISSUES -> !admin.active ||
                !admin.rechargesEnabled ||
                admin.recargasRapidasUsername.isNullOrBlank()
        }
        val matchesQuery = needle.isBlank() ||
            listOf(admin.user, admin.displayName, admin.banca, admin.ownerName, admin.phone)
                .filterNotNull()
                .any { it.lowercase(Locale.US).contains(needle) }
        matchesFilter && matchesQuery
    }
}

private fun masterCreatedEpoch(label: String?): Long {
    val value = label?.trim().orEmpty()
    if (value.isBlank()) return Long.MAX_VALUE
    return runCatching {
        SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US).parse(value)?.time
    }.getOrNull() ?: Long.MAX_VALUE
}

private data class MasterDashboardMutation(
    val admins: List<UserAccount>,
    val cashiers: List<UserAccount>,
    val status: String,
    val issuedCredentials: List<IssuedCredential> = emptyList(),
    val issuedCredentialsMode: MasterIssuedCredentialsMode? = null,
)

@Composable
private fun MasterDashboardRoute(
    admins: List<UserAccount>,
    cashiers: List<UserAccount>,
    initialMasterRecargaLimit: Double,
    onBack: () -> Unit,
    onOpenCreate: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenModules: () -> Unit,
    onSaveMasterRecargaLimit: (Double) -> Double,
    onProbeServer: ((MasterServerProbeResult) -> Unit) -> Unit,
    onCheckRecargasRapidasWallet: ((String) -> Unit) -> Unit,
    onSaveDefaultRecargasRapidasCredentials: (String, String) -> String,
    onSyncCloud: ((MasterCloudSyncResult, MasterDashboardMutation) -> Unit) -> Unit,
    onRefreshRemote: ((MasterCloudSyncResult, MasterDashboardMutation) -> Unit) -> Unit,
    onToggleBank: (UserAccount) -> MasterDashboardMutation,
    onSaveBankRechargeAccess: (UserAccount, Boolean, Double, (MasterDashboardMutation) -> Unit) -> Unit,
    onAddBankRechargeBalance: (UserAccount, Double, (MasterDashboardMutation) -> Unit) -> Unit,
    onSaveBankRecargasRapidasCredentials: (UserAccount, String, String) -> MasterDashboardMutation,
    onDeleteBank: (UserAccount) -> MasterDashboardMutation,
    onRegenerateCredentials: (UserAccount) -> MasterDashboardMutation,
    onAddCashiers: (UserAccount, Int, String) -> MasterDashboardMutation,
    onChangePassword: (String, String) -> MasterDashboardMutation,
    onChangeCashierGroupPassword: (UserAccount, String) -> MasterDashboardMutation,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var adminState by remember { mutableStateOf(admins) }
    var cashierState by remember { mutableStateOf(cashiers) }
    var status by remember { mutableStateOf<String?>(null) }
    var issuedCredentials by remember { mutableStateOf<List<IssuedCredential>>(emptyList()) }
    var issuedCredentialsMode by remember { mutableStateOf<MasterIssuedCredentialsMode?>(null) }
    var pendingDelete by remember { mutableStateOf<UserAccount?>(null) }
    var pendingToggle by remember { mutableStateOf<UserAccount?>(null) }
    var pendingAddCashiers by remember { mutableStateOf<UserAccount?>(null) }
    var pendingPasswordChange by remember { mutableStateOf<UserAccount?>(null) }
    var pendingCashierGroupPasswordChange by remember { mutableStateOf<UserAccount?>(null) }
    var pendingCredentialReset by remember { mutableStateOf<UserAccount?>(null) }
    var masterActionBusy by remember { mutableStateOf<String?>(null) }
    var selectedAdminId by rememberSaveable { mutableStateOf("") }
    var selectedBankFilterName by rememberSaveable { mutableStateOf(MasterBankFilter.ALL.name) }
    var selectedMasterSectionId by rememberSaveable { mutableStateOf(MasterDestination.OVERVIEW.id) }
    var masterRecargaLimitDraft by rememberSaveable { mutableStateOf(formatPlainAmount(initialMasterRecargaLimit)) }
    var serverProbeStatus by remember { mutableStateOf("Sin validar servidor todavía.") }
    var serverProbeDetail by remember { mutableStateOf("El chequeo usa el servidor y la configuración remota.") }
    var rrWalletStatus by remember { mutableStateOf("Cartera RR real: pendiente de consulta.") }
    var defaultRrUsernameDraft by rememberSaveable { mutableStateOf("") }
    var defaultRrPasswordDraft by rememberSaveable { mutableStateOf("") }
    var serverProbeBusy by remember { mutableStateOf(false) }
    var rrWalletBusy by remember { mutableStateOf(false) }
    var cloudSyncBusy by remember { mutableStateOf(false) }
    var remoteRefreshBusy by remember { mutableStateOf(false) }
    var autoRemoteHydrated by rememberSaveable { mutableStateOf(false) }
    var savingFundAdminIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val realtimeClient = remember { LotterynetRealtimeClient() }
    val realtimeTokenProvider = remember(context) {
        SupabaseSessionTokenProvider(LocalSessionRepository(context))
    }
    val remoteRefreshGovernor = remember { TicketRefreshGovernor(requestCooldownMs = MASTER_DASHBOARD_REMOTE_REFRESH_DEDUP_MS) }

    val selectedBankFilter = remember(selectedBankFilterName) {
        MasterBankFilter.entries.firstOrNull { it.name == selectedBankFilterName } ?: MasterBankFilter.ALL
    }
    val filteredAdmins = remember(adminState, search, selectedBankFilter) {
        filterMasterBanksForDashboard(adminState, search, selectedBankFilter)
    }
    val selectedAdmin = remember(filteredAdmins, selectedAdminId) {
        filteredAdmins.firstOrNull { it.id == selectedAdminId } ?: filteredAdmins.firstOrNull()
    }
    val activeBanks = remember(adminState) { adminState.count { it.active } }
    val blockedBanks = remember(adminState) { adminState.count { !it.active } }
    val selectedDestination = remember(selectedMasterSectionId) {
        masterPrimaryDestinations().firstOrNull { it.id == selectedMasterSectionId }
            ?: MasterDestination.OVERVIEW
    }
    val dashboardBusy = cloudSyncBusy || remoteRefreshBusy || serverProbeBusy || rrWalletBusy
    val statusBadge = resolveMasterStatusBadge(
        busy = dashboardBusy,
        statusMessage = status,
    )
    val returnToOverviewOrExit = {
        if (selectedMasterSectionId == MasterDestination.OVERVIEW.id) {
            onBack()
        } else {
            selectedMasterSectionId = MasterDestination.OVERVIEW.id
        }
    }
    BackHandler(enabled = selectedMasterSectionId != MasterDestination.OVERVIEW.id) {
        selectedMasterSectionId = MasterDestination.OVERVIEW.id
    }

    fun applyMutation(mutation: MasterDashboardMutation) {
        adminState = mutation.admins
        cashierState = mutation.cashiers
        status = mutation.status
        issuedCredentials = mutation.issuedCredentials
        issuedCredentialsMode = mutation.issuedCredentialsMode
    }

    LaunchedEffect(selectedAdmin?.id) {
        val nextId = selectedAdmin?.id.orEmpty()
        if (nextId != selectedAdminId) selectedAdminId = nextId
    }

    val actionScope = rememberCoroutineScope()

    LaunchedEffect(autoRemoteHydrated) {
        if (!autoRemoteHydrated && !remoteRefreshBusy) {
            autoRemoteHydrated = true
            remoteRefreshBusy = true
            onRefreshRemote { result, mutation ->
                remoteRefreshBusy = false
                serverProbeStatus = result.message
                serverProbeDetail = result.detail
                applyMutation(mutation)
            }
        }
    }

    DisposableEffect(lifecycleOwner, autoRemoteHydrated) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && autoRemoteHydrated && !remoteRefreshBusy && !cloudSyncBusy) {
                if (shouldSkipMasterDashboardRemoteRefresh(
                        governor = remoteRefreshGovernor,
                        requestType = "master-dashboard:resume",
                        authScope = "master",
                        force = false,
                    )
                ) return@LifecycleEventObserver
                remoteRefreshBusy = true
                onRefreshRemote { result, mutation ->
                    remoteRefreshBusy = false
                    serverProbeStatus = result.message
                    serverProbeDetail = result.detail
                    applyMutation(mutation)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        val subscriptions = if (realtimeClient.isConfigured()) {
            realtimeClient.subscribeUsersStateSignals(
                bearerTokenProvider = { realtimeTokenProvider.freshAccessToken() },
            ) {
                if (shouldSkipMasterDashboardRemoteRefresh(
                        governor = remoteRefreshGovernor,
                        requestType = "master-dashboard:realtime",
                        authScope = "master",
                        force = false,
                    )
                ) return@subscribeUsersStateSignals
                onRefreshRemote { result, mutation ->
                    serverProbeStatus = result.message
                    serverProbeDetail = result.detail
                    applyMutation(mutation)
                }
            }
        } else {
            emptyList()
        }
        onDispose {
            subscriptions.forEach { it.close() }
            realtimeClient.shutdown()
        }
    }

    pendingDelete?.let { admin ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Borrar banca") },
            text = {
                Text("Se eliminará ${admin.banca ?: admin.user} con todos sus cajeros locales. Esta acción no guarda una copia visible de las credenciales.")
            },
            confirmButton = {
                CompactActionButton(
                    label = "Borrar",
                    onClick = {
                        applyMutation(onDeleteBank(admin))
                        pendingDelete = null
                    },
                    active = true,
                    icon = Icons.Rounded.DeleteForever,
                )
            },
            dismissButton = {
                CompactActionButton(label = "Cancelar", onClick = { pendingDelete = null })
            },
        )
    }

    pendingToggle?.let { admin ->
        val nextAction = if (admin.active) "bloquear" else "activar"
        AlertDialog(
            onDismissRequest = { pendingToggle = null },
            title = { Text("${nextAction.replaceFirstChar { it.uppercase(Locale.US) }} banca") },
            text = {
                Text("${admin.banca ?: admin.user} y sus cajeros quedarán ${if (admin.active) "bloqueados" else "activos"} en el local.")
            },
            confirmButton = {
                CompactActionButton(
                    label = nextAction.replaceFirstChar { it.uppercase(Locale.US) },
                    onClick = {
                        applyMutation(onToggleBank(admin))
                        pendingToggle = null
                    },
                    active = true,
                    icon = if (admin.active) Icons.Rounded.DeleteForever else Icons.Rounded.LockOpen,
                )
            },
            dismissButton = {
                CompactActionButton(label = "Cancelar", onClick = { pendingToggle = null })
            },
        )
    }

    pendingCredentialReset?.let { admin ->
        val actionKey = "regenerate:${admin.id}"
        AlertDialog(
            onDismissRequest = { pendingCredentialReset = null },
            title = { Text("Generar claves nuevas") },
            text = {
                Text("Esto reemplaza la clave del admin ${admin.user} y la de sus cajeros. Las claves viejas dejarán de funcionar.")
            },
            confirmButton = {
                CompactActionButton(
                    label = if (masterActionBusy == actionKey) "Generando..." else "Generar",
                    onClick = {
                        if (masterActionBusy == null) {
                            masterActionBusy = actionKey
                            actionScope.launch {
                                try {
                                    val mutation = withContext(Dispatchers.IO) {
                                        onRegenerateCredentials(admin)
                                    }
                                    applyMutation(mutation)
                                    pendingCredentialReset = null
                                } catch (error: Throwable) {
                                    status = error.message ?: "No se pudieron generar las claves."
                                } finally {
                                    masterActionBusy = null
                                }
                            }
                        }
                    },
                    active = masterActionBusy == null,
                    icon = Icons.Rounded.Key,
                )
            },
            dismissButton = {
                CompactActionButton(label = "Cancelar", onClick = { pendingCredentialReset = null })
            },
        )
    }

    pendingAddCashiers?.let { admin ->
        var countText by rememberSaveable(admin.id) { mutableStateOf("1") }
        var prefixText by rememberSaveable(admin.id) { mutableStateOf(admin.cashierPrefix.orEmpty()) }
        val actionKey = "add:${admin.id}"
        AlertDialog(
            onDismissRequest = { pendingAddCashiers = null },
            title = { Text("Agregar cajeros") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${admin.banca ?: admin.user} recibirá usuarios nuevos con clave real emitida por master.")
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter(Char::isDigit).take(2) },
                        label = { Text("Cantidad") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = prefixText,
                        onValueChange = { prefixText = it.lowercase(Locale.US).filter(Char::isLetterOrDigit).take(6) },
                        label = { Text("Prefijo") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                CompactActionButton(
                    label = if (masterActionBusy == actionKey) "Agregando..." else "Agregar",
                    onClick = {
                        if (masterActionBusy == null) {
                            masterActionBusy = actionKey
                            actionScope.launch {
                                try {
                                    val mutation = withContext(Dispatchers.IO) {
                                        onAddCashiers(admin, countText.toIntOrNull() ?: 1, prefixText)
                                    }
                                    applyMutation(mutation)
                                    pendingAddCashiers = null
                                } catch (error: Throwable) {
                                    status = error.message ?: "No se pudieron agregar cajeros."
                                } finally {
                                    masterActionBusy = null
                                }
                            }
                        }
                    },
                    active = masterActionBusy == null,
                    icon = Icons.Rounded.Groups,
                )
            },
            dismissButton = {
                CompactActionButton(label = "Cancelar", onClick = { pendingAddCashiers = null })
            },
        )
    }

    pendingPasswordChange?.let { account ->
        var passwordText by rememberSaveable(account.id) { mutableStateOf("") }
        val actionKey = "password:${account.id}"
        AlertDialog(
            onDismissRequest = { pendingPasswordChange = null },
            title = { Text("Cambiar clave") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se emitirá una clave nueva para ${account.user}. Esa será la clave verdadera para entrar.")
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it.take(24) },
                        label = { Text("Nueva clave") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                CompactActionButton(
                    label = if (masterActionBusy == actionKey) "Guardando..." else "Guardar",
                    onClick = {
                        if (masterActionBusy == null) {
                            masterActionBusy = actionKey
                            actionScope.launch {
                                try {
                                    val mutation = withContext(Dispatchers.IO) {
                                        onChangePassword(account.user, passwordText)
                                    }
                                    applyMutation(mutation)
                                    pendingPasswordChange = null
                                } catch (error: Throwable) {
                                    status = error.message ?: "No se pudo cambiar la clave."
                                } finally {
                                    masterActionBusy = null
                                }
                            }
                        }
                    },
                    active = masterActionBusy == null,
                    icon = Icons.Rounded.Key,
                )
            },
            dismissButton = {
                CompactActionButton(label = "Cancelar", onClick = { pendingPasswordChange = null })
            },
        )
    }

    pendingCashierGroupPasswordChange?.let { admin ->
        var passwordText by rememberSaveable(admin.id) { mutableStateOf("") }
        val actionKey = "group-password:${admin.id}"
        val cashierCount = cashierState.count {
            it.adminId == admin.id ||
                it.adminUser.equals(admin.user, true) ||
                (!admin.banca.isNullOrBlank() && it.banca.equals(admin.banca, true))
        }
        AlertDialog(
            onDismissRequest = { pendingCashierGroupPasswordChange = null },
            title = { Text("Clave para cajeros") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Se pondrá la misma clave a $cashierCount cajero(s) de ${admin.banca ?: admin.user}. El admin no cambia.")
                    OutlinedTextField(
                        value = passwordText,
                        onValueChange = { passwordText = it.take(24) },
                        label = { Text("Nueva clave") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                CompactActionButton(
                    label = if (masterActionBusy == actionKey) "Guardando..." else "Guardar",
                    onClick = {
                        if (masterActionBusy == null) {
                            masterActionBusy = actionKey
                            actionScope.launch {
                                try {
                                    val mutation = withContext(Dispatchers.IO) {
                                        onChangeCashierGroupPassword(admin, passwordText)
                                    }
                                    applyMutation(mutation)
                                    pendingCashierGroupPasswordChange = null
                                } catch (error: Throwable) {
                                    status = error.message ?: "No se pudo cambiar la clave de los cajeros."
                                } finally {
                                    masterActionBusy = null
                                }
                            }
                        }
                    },
                    active = masterActionBusy == null,
                    icon = Icons.Rounded.Key,
                )
            },
            dismissButton = {
                CompactActionButton(label = "Cancelar", onClick = { pendingCashierGroupPasswordChange = null })
            },
        )
    }

    val visual = rememberLotteryNetVisualSpec()
    val layout = remember(visual.windowMode) { resolveMasterDashboardLayout(visual.windowMode) }
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
                MasterCompactHeader(
                    title = "Centro Master",
                    subtitle = "${selectedDestination.label} · Administración",
                    onBack = returnToOverviewOrExit,
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CompactStatusBadge(
                        label = statusBadge.label,
                        tone = when (statusBadge.kind) {
                            MasterStatusKind.ERROR -> MaterialTheme.colorScheme.error
                            MasterStatusKind.WARNING -> MaterialTheme.colorScheme.tertiary
                            MasterStatusKind.INFO -> MaterialTheme.colorScheme.secondary
                            MasterStatusKind.SUCCESS -> MaterialTheme.colorScheme.primary
                            MasterStatusKind.NEUTRAL -> MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }
            item {
                CurrentScopeDropdownCard(
                    title = "Área de administración",
                    value = selectedDestination.label,
                    selectedId = selectedMasterSectionId,
                    options = masterDashboardSectionOptions().map { it.id to it.label },
                    onSelected = { selectedMasterSectionId = it },
                    subtitle = "Bancas, módulos, sistema y seguridad en un solo selector.",
                    actionLabel = "Cambiar",
                    tone = ActionTone.IntenseBlue,
                )
            }
            if (selectedMasterSectionId == MasterDestination.OVERVIEW.id) item {
                CompactPanel {
                    SectionHeader(title = "Resumen administrativo", meta = "Vista general del negocio")
                    val summaryMetrics = listOf(
                        MetricStripItem("Bancas", admins.size.toString(), visual.colors.admin),
                        MetricStripItem("Activas", activeBanks.toString(), visual.colors.gain),
                        MetricStripItem("Cajeros", cashiers.size.toString(), visual.colors.finance),
                        MetricStripItem("Bloqueadas", blockedBanks.toString(), if (blockedBanks > 0) MaterialTheme.colorScheme.error else visual.colors.neutral),
                    )
                    if (layout.compactSummary) {
                        MetricStrip(summaryMetrics.take(2))
                        MetricStrip(summaryMetrics.drop(2))
                    } else {
                        MetricStrip(summaryMetrics)
                    }
                    if (admins.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "Todavía no hay bancas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Crea la primera banca para comenzar a administrar admins, cajeros y módulos.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = visual.colors.muted,
                                )
                                CompactActionButton(
                                    label = "Crear primera banca",
                                    onClick = onOpenCreate,
                                    icon = Icons.Rounded.Add,
                                    modifier = Modifier.fillMaxWidth(),
                                    tone = ActionTone.Primary,
                                )
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CompactStatusBadge(
                            label = if (activeBanks > 0) "Bancas activas" else "Sin bancas activas",
                            tone = if (activeBanks > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        CompactStatusBadge(
                            label = if (blockedBanks > 0) "Revisar bloqueadas" else "Sin alertas",
                            tone = if (blockedBanks > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    SectionHeader(title = "Accesos rápidos", meta = "Administración por responsabilidad")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactActionButton("Bancas", onClick = { selectedMasterSectionId = MasterDestination.BANKS.id }, modifier = Modifier.weight(1f), icon = Icons.Rounded.Storefront, tone = ActionTone.Success)
                        CompactActionButton("Módulos", onClick = { selectedMasterSectionId = MasterDestination.MODULES.id }, modifier = Modifier.weight(1f), icon = Icons.Rounded.Extension, tone = ActionTone.Secondary)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactActionButton("Sistema", onClick = { selectedMasterSectionId = MasterDestination.SYSTEM.id }, modifier = Modifier.weight(1f), icon = Icons.Rounded.Wallet, tone = ActionTone.Warning)
                        CompactActionButton("Seguridad", onClick = { selectedMasterSectionId = MasterDestination.SECURITY.id }, modifier = Modifier.weight(1f), icon = Icons.Rounded.Key, tone = ActionTone.Secondary)
                    }
                    Text(
                        if (cloudSyncBusy || remoteRefreshBusy || serverProbeBusy) "Hay una operación técnica en curso." else "Los datos visibles corresponden al último estado confirmado local/remoto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                }
            }
            if (selectedMasterSectionId == MasterDestination.BANKS.id) item {
                CompactPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(title = "Bancas", meta = "${cashiers.size} cajeros", modifier = Modifier.weight(1f))
                        CompactStatusBadge(
                            label = if (blockedBanks > 0) "Atención" else "Operativo",
                            tone = if (blockedBanks > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    val masterMetrics = listOf(
                        MetricStripItem("Bancas", admins.size.toString(), visual.colors.admin),
                        MetricStripItem("Activas", activeBanks.toString(), visual.colors.gain),
                        MetricStripItem("Bloq.", blockedBanks.toString(), if (blockedBanks > 0) MaterialTheme.colorScheme.error else visual.colors.neutral),
                        MetricStripItem("Cajeros", cashiers.size.toString(), visual.colors.finance),
                    )
                    if (layout.compactSummary) {
                        MetricStrip(masterMetrics.take(2))
                        MetricStrip(masterMetrics.drop(2))
                    } else {
                        MetricStrip(masterMetrics)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactActionButton("Crear banca", onClick = onOpenCreate, modifier = Modifier.weight(1f), active = true, icon = Icons.Rounded.AdminPanelSettings)
                        CompactActionButton("Auditoría", onClick = onOpenAudit, modifier = Modifier.weight(1f), icon = Icons.Rounded.QueryStats)
                    }
                    CompactTextInput(
                        label = "Buscar banca",
                        value = search,
                        onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Buscar admin, banca o usuario",
                        leadingIcon = Icons.Rounded.QueryStats,
                    )
                    CompactSegmentedSelector(
                        options = masterBankFilterOptions(),
                        selectedId = selectedBankFilter.name,
                        onSelected = { selectedBankFilterName = it },
                        columns = 2,
                    )
                    MasterAdminSelector(
                        admins = filteredAdmins,
                        selectedAdmin = selectedAdmin,
                        onSelect = { selectedAdminId = it.id },
                    )
                    status?.let {
                        if (serverProbeBusy || rrWalletBusy || cloudSyncBusy || remoteRefreshBusy) {
                            CompactLoadingState(label = it)
                        } else {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                        }
                    }
                }
            }
            if (selectedMasterSectionId == MasterDestination.MODULES.id) item {
                CompactPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(
                            title = "Módulos",
                            meta = "Acceso por admin y cajero",
                            modifier = Modifier.weight(1f),
                        )
                        CompactStatusBadge(
                            label = "Control Master",
                            tone = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "Activa cada módulo y elige primero el admin; después aparecerán únicamente sus cajeros.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                    masterModuleEntries().forEach { module ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenModules),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Extension,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = module.label,
                                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Configurar",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    CompactActionButton(
                        label = "Administrar accesos",
                        onClick = onOpenModules,
                        icon = Icons.Rounded.Extension,
                        modifier = Modifier.fillMaxWidth(),
                        active = true,
                    )
                }
            }
            if (selectedMasterSectionId == MasterDestination.SYSTEM.id) item {
                CompactPanel(alt = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(title = "Recargas master", meta = masterRechargeProviderLabel(), modifier = Modifier.weight(1f))
                        CompactStatusBadge(
                            label = if ((masterRecargaLimitDraft.toDoubleOrNull() ?: 0.0) > 0.0) "Configurado" else "Sin tope",
                            tone = if ((masterRecargaLimitDraft.toDoubleOrNull() ?: 0.0) > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                    CompactTextInput(
                        label = "Tope master",
                        value = masterRecargaLimitDraft,
                        onValueChange = { next -> masterRecargaLimitDraft = next.filter { it.isDigit() || it == '.' } },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardType = KeyboardType.Decimal,
                    )
                    CompactActionButton(
                        "Guardar tope",
                        onClick = {
                            val saved = onSaveMasterRecargaLimit(masterRecargaLimitDraft.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0)
                            masterRecargaLimitDraft = formatPlainAmount(saved)
                            status = "Tope master de recarga guardado."
                        },
                        icon = Icons.Rounded.WarningAmber,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CompactTextInput(
                        label = "Usuario RR default",
                        value = defaultRrUsernameDraft,
                        onValueChange = { defaultRrUsernameDraft = it.trim().take(80) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CompactTextInput(
                        label = "Clave RR default",
                        value = defaultRrPasswordDraft,
                        onValueChange = { defaultRrPasswordDraft = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    CompactActionButton(
                        "Guardar cuenta default RR",
                        onClick = {
                            status = onSaveDefaultRecargasRapidasCredentials(defaultRrUsernameDraft, defaultRrPasswordDraft)
                            defaultRrPasswordDraft = ""
                        },
                        icon = Icons.Rounded.Key,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CompactActionButton(
                        if (rrWalletBusy) "Consultando..." else "Consultar cartera RR",
                        onClick = {
                            rrWalletBusy = true
                            onCheckRecargasRapidasWallet { label ->
                                rrWalletBusy = false
                                rrWalletStatus = label
                                status = label
                            }
                        },
                        icon = Icons.Rounded.Wallet,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(rrWalletStatus, style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace)
                    Text(masterRecargasRapidasCredentialHelpText(), style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                }
            }
            if (selectedMasterSectionId == MasterDestination.SYSTEM.id) item {
                CompactPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(title = "Servidor y nube", meta = if (cloudSyncBusy || remoteRefreshBusy || serverProbeBusy) "En curso" else "Listo", modifier = Modifier.weight(1f))
                        CompactStatusBadge(
                            label = if (cloudSyncBusy || remoteRefreshBusy || serverProbeBusy) "En curso" else "Listo",
                            tone = if (cloudSyncBusy || remoteRefreshBusy || serverProbeBusy) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (layout.splitServerActions) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            CompactActionButton(
                                if (serverProbeBusy) "Revisando..." else "Revisar servidor",
                                onClick = {
                                    serverProbeBusy = true
                                    onProbeServer { result ->
                                        serverProbeBusy = false
                                        serverProbeStatus = result.message
                                        serverProbeDetail = result.detail
                                        status = if (result.ok) "Servidor validado." else "Falló la validación del servidor."
                                    }
                                },
                                icon = Icons.Rounded.QueryStats,
                                modifier = Modifier.weight(1f),
                            )
                            CompactActionButton(
                                if (cloudSyncBusy) "Sincronizando..." else "Sincronizar nube",
                                onClick = {
                                    cloudSyncBusy = true
                                    onSyncCloud { result, mutation ->
                                        cloudSyncBusy = false
                                        serverProbeStatus = result.message
                                        serverProbeDetail = result.detail
                                        applyMutation(mutation)
                                    }
                                },
                                icon = Icons.Rounded.Storefront,
                                modifier = Modifier.weight(1f),
                                active = true,
                            )
                        }
                        CompactActionButton(
                            if (remoteRefreshBusy) "Cargando..." else "Snapshot remoto",
                            onClick = {
                                remoteRefreshBusy = true
                                onRefreshRemote { result, mutation ->
                                    remoteRefreshBusy = false
                                    serverProbeStatus = result.message
                                    serverProbeDetail = result.detail
                                    applyMutation(mutation)
                                }
                            },
                            icon = Icons.Rounded.Groups,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            CompactActionButton(
                                if (serverProbeBusy) "Revisando..." else "Revisar servidor",
                                onClick = {
                                    serverProbeBusy = true
                                    onProbeServer { result ->
                                        serverProbeBusy = false
                                        serverProbeStatus = result.message
                                        serverProbeDetail = result.detail
                                        status = if (result.ok) "Servidor validado." else "Falló la validación del servidor."
                                    }
                                },
                                icon = Icons.Rounded.QueryStats,
                                modifier = Modifier.weight(1f),
                            )
                            CompactActionButton(
                                if (cloudSyncBusy) "Sincronizando..." else "Sincronizar nube",
                                onClick = {
                                    cloudSyncBusy = true
                                    onSyncCloud { result, mutation ->
                                        cloudSyncBusy = false
                                        serverProbeStatus = result.message
                                        serverProbeDetail = result.detail
                                        applyMutation(mutation)
                                    }
                                },
                                icon = Icons.Rounded.Storefront,
                                modifier = Modifier.weight(1f),
                                active = true,
                            )
                            CompactActionButton(
                                if (remoteRefreshBusy) "Cargando..." else "Snapshot remoto",
                                onClick = {
                                    remoteRefreshBusy = true
                                    onRefreshRemote { result, mutation ->
                                        remoteRefreshBusy = false
                                        serverProbeStatus = result.message
                                        serverProbeDetail = result.detail
                                        applyMutation(mutation)
                                    }
                                },
                                icon = Icons.Rounded.Groups,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    SectionHeader(title = "Estado técnico", meta = if (cloudSyncBusy || remoteRefreshBusy || serverProbeBusy) "En curso" else "Listo")
                    Text(
                        serverProbeStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (serverProbeStatus == "Servidor disponible.") MaterialTheme.colorScheme.primary else visual.colors.muted,
                    )
                    Text(serverProbeDetail, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                }
            }
            if (selectedMasterSectionId == MasterDestination.SECURITY.id) item {
                CompactPanel {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        SectionHeader(title = "Auditoría", meta = "Cambios administrativos", modifier = Modifier.weight(1f))
                        CompactStatusBadge(label = "Registro", tone = MaterialTheme.colorScheme.tertiary)
                    }
                    Text(
                        "Consulta quién cambió bancas, credenciales, fondos y configuración, con fecha y resultado de la operación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                    )
                    CompactActionButton(
                        "Abrir historial de auditoría",
                        onClick = onOpenAudit,
                        icon = Icons.Rounded.QueryStats,
                        modifier = Modifier.fillMaxWidth(),
                        tone = ActionTone.Warning,
                    )
                }
            }
            if (selectedMasterSectionId == MasterDestination.SECURITY.id || issuedCredentials.isNotEmpty()) {
                item {
                    CompactPanel(alt = true) {
                        val credentialsTitle = issuedCredentialsMode?.title ?: "Credenciales emitidas"
                        SectionHeader(title = credentialsTitle, meta = "${issuedCredentials.size} usuarios")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            CompactActionButton(
                                "Compartir",
                                onClick = {
                                    val payload = buildMasterIssuedCredentialsShareText(credentialsTitle, issuedCredentials)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, credentialsTitle)
                                        putExtra(Intent.EXTRA_TEXT, payload)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Compartir credenciales"))
                                },
                                icon = Icons.Rounded.Share,
                                modifier = Modifier.weight(1f),
                            )
                            CompactActionButton(
                                "Cerrar listado",
                                onClick = {
                                    issuedCredentials = emptyList()
                                    issuedCredentialsMode = null
                                },
                                icon = Icons.Rounded.DeleteForever,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        issuedCredentials.forEach { credential ->
                            MasterIssuedCredentialRow(
                                displayName = credential.displayName,
                                username = credential.username,
                                password = credential.password,
                                role = credential.role,
                            )
                        }
                    }
                }
            }
            if (selectedMasterSectionId == MasterDestination.BANKS.id && filteredAdmins.isEmpty()) {
                item {
                    CompactPanel {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No hay bancas para este filtro.", color = visual.colors.muted)
                        }
                    }
                }
            } else if (selectedMasterSectionId == MasterDestination.BANKS.id) {
                selectedAdmin?.let { selected ->
                    item(key = "selected-admin-${selected.id}") {
                        val admin = selected
                    val bankCashiers = cashierState.filter {
                        it.adminId == admin.id ||
                            it.adminUser.equals(admin.user, true) ||
                            (!admin.banca.isNullOrBlank() && it.banca.equals(admin.banca, true))
                    }
                    MasterBankRow(
                        admin = admin,
                        cashierCount = bankCashiers.size,
                        onToggle = { pendingToggle = admin },
                        onSaveRechargeAccess = { enabled, amount ->
                            if (admin.id !in savingFundAdminIds) {
                                savingFundAdminIds = savingFundAdminIds + admin.id
                                status = masterFundSaveStatusLabel(MasterFundSaveState.SAVING, amount)
                                onSaveBankRechargeAccess(admin, enabled, amount) { mutation ->
                                    savingFundAdminIds = savingFundAdminIds - admin.id
                                    applyMutation(mutation)
                                }
                            }
                        },
                        onAddRechargeBalance = { amount ->
                            if (admin.id !in savingFundAdminIds) {
                                savingFundAdminIds = savingFundAdminIds + admin.id
                                status = "Agregando saldo…"
                                onAddBankRechargeBalance(admin, amount) { mutation ->
                                    savingFundAdminIds = savingFundAdminIds - admin.id
                                    applyMutation(mutation)
                                }
                            }
                        },
                        onSaveRecargasRapidasCredentials = { username, password ->
                            applyMutation(onSaveBankRecargasRapidasCredentials(admin, username, password))
                        },
                        onDelete = { pendingDelete = admin },
                        onRegenerate = { pendingCredentialReset = admin },
                        onAddCashiers = { pendingAddCashiers = admin },
                        onChangePassword = { pendingPasswordChange = admin },
                        cashiers = bankCashiers,
                        onChangeCashierPassword = { cashier -> pendingPasswordChange = cashier },
                        onChangeAllCashiersPassword = { pendingCashierGroupPasswordChange = admin },
                        compact = layout.compactBanks,
                        shortActionLabels = layout.shortBankActionLabels,
                        fundSaveBusy = admin.id in savingFundAdminIds,
                    )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun MasterCompactHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
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
                    contentDescription = "Volver al inicio Master",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MasterAdminSelector(
    admins: List<UserAccount>,
    selectedAdmin: UserAccount?,
    onSelect: (UserAccount) -> Unit,
) {
    val visual = rememberLotteryNetVisualSpec()
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        CompactActionButton(
            label = selectedAdmin?.let { admin ->
                "Admin: ${admin.banca ?: admin.displayName ?: admin.user}"
            } ?: "Seleccionar admin",
            onClick = { if (admins.isNotEmpty()) expanded = true },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.AdminPanelSettings,
            active = admins.isNotEmpty(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            if (admins.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "No hay admin con ese nombre",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.ink,
                        )
                    },
                    onClick = { expanded = false },
                )
            } else {
                admins.forEach { admin ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    admin.banca ?: admin.displayName ?: admin.user,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = visual.colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${admin.user} · ${admin.createdLabel ?: "sin fecha"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = visual.colors.ink,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        onClick = {
                            onSelect(admin)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
    Text(
        "Lista ordenada por creación",
        style = MaterialTheme.typography.labelSmall,
        color = visual.colors.ink,
    )
}

@Composable
private fun MasterMetric(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(modifier = modifier, alt = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = visual.colors.ink)
            Text(label, style = MaterialTheme.typography.labelMedium, color = visual.colors.muted)
            Text(value, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun MasterBankRow(
    admin: UserAccount,
    cashierCount: Int,
    onToggle: () -> Unit,
    onSaveRechargeAccess: (Boolean, Double) -> Unit,
    onAddRechargeBalance: (Double) -> Unit,
    onSaveRecargasRapidasCredentials: (String, String) -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onAddCashiers: () -> Unit,
    onChangePassword: () -> Unit,
    cashiers: List<UserAccount>,
    onChangeCashierPassword: (UserAccount) -> Unit,
    onChangeAllCashiersPassword: () -> Unit,
    compact: Boolean,
    shortActionLabels: Boolean,
    fundSaveBusy: Boolean,
) {
    val visual = rememberLotteryNetVisualSpec()
    var rechargeAmountDraft by rememberSaveable(admin.id, admin.rechargesAssignedBalance) {
        mutableStateOf(formatPlainAmount(admin.rechargesAssignedBalance))
    }
    var rechargeBalanceTopUpDraft by rememberSaveable(admin.id, admin.rechargesBalance) {
        mutableStateOf("0")
    }
    var rrUsernameDraft by rememberSaveable(admin.id, admin.recargasRapidasUsername) {
        mutableStateOf(admin.recargasRapidasUsername.orEmpty())
    }
    var rrPasswordDraft by rememberSaveable(admin.id, admin.recargasRapidasUsername) {
        mutableStateOf("")
    }
    var selectedDetailAreaName by rememberSaveable(admin.id) {
        mutableStateOf(MasterBankDetailArea.OVERVIEW.name)
    }
    val selectedDetailArea = MasterBankDetailArea.entries.firstOrNull {
        it.name == selectedDetailAreaName
    } ?: MasterBankDetailArea.OVERVIEW
    CompactPanel {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        admin.banca ?: admin.displayName ?: admin.user,
                        style = MaterialTheme.typography.titleSmall,
                        color = visual.colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${admin.user} · ${admin.ownerName ?: admin.displayName ?: "Sin responsable"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                CompactStatusBadge(
                    label = if (admin.active) "Activa" else "Bloqueada",
                    tone = if (admin.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "${admin.address ?: "Sin dirección"} · ${admin.phone ?: "Sin teléfono"}",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            CompactSegmentedSelector(
                options = MasterBankDetailArea.entries.map { area ->
                    QuickFilterChip(id = area.name, label = area.label)
                },
                selectedId = selectedDetailArea.name,
                onSelected = { selectedDetailAreaName = it },
                columns = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (selectedDetailArea == MasterBankDetailArea.OVERVIEW && compact) {
                Text(
                    "Cajeros $cashierCount · ${admin.territory ?: "RD"} · ${masterRechargeAccessLabel(admin.rechargesEnabled, admin.rechargesAssignedBalance, admin.rechargesBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.ink,
                    fontFamily = FontFamily.Monospace,
                )
            } else if (selectedDetailArea == MasterBankDetailArea.OVERVIEW) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Cajeros: $cashierCount", style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, modifier = Modifier.weight(1f))
                    Text("Territorio: ${admin.territory ?: "RD"}", style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, modifier = Modifier.weight(1f))
                    Text(masterRechargeAccessLabel(admin.rechargesEnabled, admin.rechargesAssignedBalance, admin.rechargesBalance), style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                }
            }
            if (selectedDetailArea == MasterBankDetailArea.FUNDS) {
                SectionHeader(
                    title = "Fondos y recargas",
                    meta = "Cambios confirmados por servidor",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactTextInput(
                    label = "Nuevo fondo asignado",
                    value = rechargeAmountDraft,
                    onValueChange = { next -> rechargeAmountDraft = next.filter { it.isDigit() || it == '.' } },
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Decimal,
                )
                CompactActionButton(
                    if (fundSaveBusy) "Guardando…" else if (admin.rechargesEnabled) "Bloq. rec." else "Act. rec.",
                    onClick = {
                        onSaveRechargeAccess(
                            !admin.rechargesEnabled,
                            rechargeAmountDraft.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    icon = if (admin.rechargesEnabled) Icons.Rounded.WarningAmber else Icons.Rounded.LockOpen,
                    tone = if (admin.rechargesEnabled) ActionTone.Warning else ActionTone.Success,
                    enabled = !fundSaveBusy,
                )
                }
                val fundSummary = masterRechargeFundSummary(admin.rechargesAssignedBalance, admin.rechargesBalance)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(fundSummary.assignedLabel, style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Text(fundSummary.availableLabel, style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Text(fundSummary.soldLabel, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                }
                CompactActionButton(
                if (fundSaveBusy) "Guardando fondo…" else "Reemplazar fondo",
                onClick = {
                    onSaveRechargeAccess(
                        admin.rechargesEnabled,
                        rechargeAmountDraft.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.AdminPanelSettings,
                enabled = !fundSaveBusy,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactTextInput(
                    label = "Agregar al saldo",
                    value = rechargeBalanceTopUpDraft,
                    onValueChange = { next -> rechargeBalanceTopUpDraft = next.filter { it.isDigit() || it == '.' } },
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Decimal,
                )
                CompactActionButton(
                    "Agregar",
                    onClick = {
                        onAddRechargeBalance(rechargeBalanceTopUpDraft.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0)
                        rechargeBalanceTopUpDraft = "0"
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Add,
                    enabled = !fundSaveBusy && (rechargeBalanceTopUpDraft.toDoubleOrNull() ?: 0.0) > 0.0,
                )
                }
                SectionHeader(
                    title = "Recargas Rapidas",
                    meta = masterRecargasRapidasCredentialLabel(admin),
                )
                CompactTextInput(
                label = "Usuario Recargas Rapidas",
                value = rrUsernameDraft,
                onValueChange = { rrUsernameDraft = it.trim().take(80) },
                modifier = Modifier.fillMaxWidth(),
                )
                CompactTextInput(
                label = if (admin.recargasRapidasUsername.isNullOrBlank()) "Clave Recargas Rapidas" else "Nueva clave opcional",
                value = rrPasswordDraft,
                onValueChange = { rrPasswordDraft = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                )
                CompactActionButton(
                "Guardar cuenta RR",
                onClick = {
                    onSaveRecargasRapidasCredentials(rrUsernameDraft, rrPasswordDraft)
                    rrPasswordDraft = ""
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Key,
                )
            }
            if (selectedDetailArea == MasterBankDetailArea.CASHIERS) {
                SectionHeader(
                    title = "Cajeros",
                    meta = "$cashierCount asignados",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactActionButton(
                        if (shortActionLabels) "+Caj." else "Agregar cajeros",
                        onClick = onAddCashiers,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Groups,
                        active = true,
                    )
                    CompactActionButton(
                        masterCashierGroupPasswordActionLabel(shortActionLabels),
                        onClick = onChangeAllCashiersPassword,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Key,
                        enabled = cashiers.isNotEmpty(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (cashiers.isEmpty()) {
                        Text("Esta banca no tiene cajeros.", style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                    } else {
                        cashiers.sortedBy { it.user }.forEach { cashier ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        cashierDisplayLabel(cashier),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = visual.colors.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        cashier.user,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = visual.colors.muted,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                                CompactActionButton(
                                    if (shortActionLabels) "Clave" else "Cambiar clave",
                                    onClick = { onChangeCashierPassword(cashier) },
                                    icon = Icons.Rounded.Key,
                                )
                            }
                        }
                    }
                }
            }
            if (selectedDetailArea == MasterBankDetailArea.SECURITY) {
                SectionHeader(
                    title = "Seguridad de la banca",
                    meta = "Claves y acciones sensibles",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CompactActionButton(
                        masterCredentialResetActionLabel(shortActionLabels),
                        onClick = onRegenerate,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Key,
                        tone = ActionTone.Warning,
                    )
                    CompactActionButton(
                        if (shortActionLabels) "Clave" else "Cambiar clave",
                        onClick = onChangePassword,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Key,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactActionButton(
                    if (admin.active) {
                        if (shortActionLabels) "Bloq." else "Bloquear"
                    } else {
                        "Activar"
                    },
                    onClick = onToggle,
                    modifier = Modifier.weight(1f),
                    icon = if (admin.active) Icons.Rounded.WarningAmber else Icons.Rounded.LockOpen,
                    tone = if (admin.active) ActionTone.Danger else ActionTone.Success,
                )
                CompactActionButton(if (shortActionLabels) "Borra" else "Borrar", onClick = onDelete, modifier = Modifier.weight(1f), icon = Icons.Rounded.DeleteForever, tone = ActionTone.Danger)
                }
            }
        }
    }
}

@Composable
private fun MasterIssuedCredentialRow(
    displayName: String,
    username: String,
    password: String,
    role: UserRole,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(alt = true) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(displayName, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink)
            Text(
                "${role.name.lowercase(Locale.US)} · $username",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                password,
                style = MaterialTheme.typography.bodyMedium,
                color = visual.colors.ink,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private const val MASTER_DASHBOARD_REMOTE_REFRESH_DEDUP_MS = 15_000L

internal fun shouldSkipMasterDashboardRemoteRefresh(
    governor: TicketRefreshGovernor,
    requestType: String,
    authScope: String,
    force: Boolean,
    nowEpochMs: Long = System.currentTimeMillis(),
): Boolean {
    if (force) return false
    return governor.shouldReuse(
        ticketRefreshGovernorKey(
            ownerKey = "master-dashboard",
            requestType = requestType,
            authScope = authScope,
        ),
        nowMs = nowEpochMs,
    )
}

private fun formatPlainAmount(value: Double): String {
    return if (value <= 0.0) "0" else com.lotterynet.pro.core.format.formatWholeAmount(value)
}

internal fun updateMasterRechargeAccess(
    admin: UserAccount,
    enabled: Boolean,
    amount: Double,
): UserAccount {
    val normalizedAmount = amount.coerceAtLeast(0.0)
    return admin.copy(
        rechargesEnabled = enabled,
        rechargesAssignedBalance = normalizedAmount,
        rechargesBalance = normalizedAmount,
        updatedAtEpochMs = System.currentTimeMillis(),
    )
}

internal fun updateMasterRecargasRapidasCredentialStatus(
    admin: UserAccount,
    usernameHint: String,
): UserAccount {
    val normalizedUsername = usernameHint.trim().takeIf { it.isNotBlank() }
    return admin.copy(
        recargasRapidasUsername = normalizedUsername,
        recargasRapidasPassword = null,
        updatedAtEpochMs = System.currentTimeMillis(),
    )
}

internal fun masterRechargeProviderLabel(): String = "Recargas Rapidas por admin"

internal enum class MasterFundSaveState {
    SAVING,
    CONFIRMED,
    ROLLED_BACK,
}

internal fun masterFundSaveStatusLabel(state: MasterFundSaveState, amount: Double): String {
    return when (state) {
        MasterFundSaveState.SAVING -> "Guardando fondo…"
        MasterFundSaveState.CONFIRMED -> "Servidor confirmó ${masterMoney(amount)}"
        MasterFundSaveState.ROLLED_BACK -> "No se guardó; se restauró el fondo anterior"
    }
}

internal fun shouldShowRechargeFundAmount(role: UserRole): Boolean {
    return role == UserRole.MASTER || role == UserRole.ADMIN
}

internal fun masterRecargasRapidasCredentialLabel(admin: UserAccount): String {
    return if (!admin.recargasRapidasUsername.isNullOrBlank()) {
        "Cuenta propia en backend"
    } else {
        "Usa cuenta default backend"
    }
}

internal fun masterRecargasRapidasCredentialHelpText(): String {
    return "Si el admin no tiene cuenta propia, vende con la default del backend. La clave no se guarda en la app ni viaja a cajeros."
}

internal fun masterRechargeAccessLabel(
    enabled: Boolean,
    balance: Double,
): String = masterRechargeAccessLabel(
    enabled = enabled,
    assigned = balance,
    available = balance,
)

internal data class MasterRechargeFundSummary(
    val assignedLabel: String,
    val availableLabel: String,
    val soldLabel: String,
)

internal fun masterRechargeFundSummary(
    assigned: Double,
    available: Double,
): MasterRechargeFundSummary {
    val normalizedAssigned = assigned.coerceAtLeast(0.0)
    val normalizedAvailable = available.coerceAtLeast(0.0)
    val sold = (normalizedAssigned - normalizedAvailable).coerceAtLeast(0.0)
    return MasterRechargeFundSummary(
        assignedLabel = "Base asignada ${masterMoney(normalizedAssigned)}",
        availableLabel = "Saldo restante ${masterMoney(normalizedAvailable)}",
        soldLabel = "Consumido ${masterMoney(sold)}",
    )
}

internal fun masterRechargeAccessLabel(
    enabled: Boolean,
    assigned: Double,
    available: Double,
): String {
    return if (enabled) {
        val summary = masterRechargeFundSummary(assigned, available)
        "${summary.assignedLabel} · ${summary.availableLabel}"
    } else {
        "Bloqueada"
    }
}

private fun masterMoney(value: Double): String = "$" + String.format(Locale.US, "%,.0f", value)
