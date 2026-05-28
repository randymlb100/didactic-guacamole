package com.lotterynet.pro.ui.master

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.DeleteForever
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.lotterynet.pro.core.master.MasterServerProbeResult
import com.lotterynet.pro.core.master.MasterServerStatusChecker
import com.lotterynet.pro.core.master.MasterUserManager
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.model.AuditEntry
import com.lotterynet.pro.core.model.SystemAlert
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.recharge.recargasrapidas.RecargasRapidasBackendClient
import com.lotterynet.pro.core.recharge.recargasrapidas.RecargasRapidasCredentialScope
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
    return listOf("Bancas", "Credenciales", "Servidor/Nube", "Recargas Master", "Auditoría")
}

private enum class MasterDashboardSection(val id: String, val label: String) {
    BANKS("banks", "Bancas"),
    RECHARGES("recharges", "Recargas"),
    SERVER("server", "Servidor"),
    CREDENTIALS("credentials", "Claves"),
    AUDIT("audit", "Auditoría"),
}

private fun masterDashboardSectionOptions(): List<QuickFilterChip> =
    MasterDashboardSection.entries.map { QuickFilterChip(it.id, it.label) }

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
        val masterCloudSyncCoordinator = MasterCloudSyncCoordinator(
            usersRepository = usersRepository,
            deletedRepository = deletedUsersRepository,
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
        val recargasRapidasBackend = RecargasRapidasBackendClient()
        val userPasswordBackendClient = UserPasswordBackendClient()
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
                    onSaveBankRechargeAccess = { admin, enabled, amount ->
                        val updated = updateMasterRechargeAccess(admin, enabled = enabled, amount = amount)
                        usersRepository.updateAccount(updated)
                        val timestampLabel = auditTimestamp()
                        auditRepository.saveEntries(
                            listOf(
                                AuditEntry(
                                    timestampLabel = timestampLabel,
                                    user = session.username,
                                    role = session.role.name.lowercase(Locale.US),
                                    action = if (enabled) "ACTIVAR_RECARGAS_ADMIN" else "BLOQUEAR_RECARGAS_ADMIN",
                                    detail = "${updated.banca ?: updated.user}: ${masterRechargeAccessLabel(updated.rechargesEnabled, updated.rechargesAssignedBalance, updated.rechargesBalance)}",
                                )
                            ) + auditRepository.getEntries()
                        )
                        alertsRepository.saveAlerts(
                            listOf(
                                SystemAlert(
                                    id = "alert-${System.currentTimeMillis()}",
                                    timestampLabel = timestampLabel,
                                    type = "master_admin_recargas",
                                    message = "${updated.banca ?: updated.user}: ${masterRechargeAccessLabel(updated.rechargesEnabled, updated.rechargesAssignedBalance, updated.rechargesBalance)}.",
                                    level = if (enabled) "info" else "warning",
                                    read = false,
                                )
                            ) + alertsRepository.getAlerts()
                        )
                        MasterDashboardMutation(
                            admins = sortMasterAdminsByCreation(usersRepository.getAdmins()),
                            cashiers = usersRepository.getCashiers(),
                            status = "${updated.banca ?: updated.user}: ${masterRechargeAccessLabel(updated.rechargesEnabled, updated.rechargesAssignedBalance, updated.rechargesBalance)}.",
                        ).also {
                            queueMasterAutoSync("guardar acceso recargas admin")
                        }
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
                        ensureCredentialJwtOrRollback(usersRepository.exportPayloadJson(), listOf(result.credential))
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
    onSaveMasterRecargaLimit: (Double) -> Double,
    onProbeServer: ((MasterServerProbeResult) -> Unit) -> Unit,
    onCheckRecargasRapidasWallet: ((String) -> Unit) -> Unit,
    onSaveDefaultRecargasRapidasCredentials: (String, String) -> String,
    onSyncCloud: ((MasterCloudSyncResult, MasterDashboardMutation) -> Unit) -> Unit,
    onRefreshRemote: ((MasterCloudSyncResult, MasterDashboardMutation) -> Unit) -> Unit,
    onToggleBank: (UserAccount) -> MasterDashboardMutation,
    onSaveBankRechargeAccess: (UserAccount, Boolean, Double) -> MasterDashboardMutation,
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
    var expandedBankIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedAdminId by rememberSaveable { mutableStateOf("") }
    var selectedBankFilterName by rememberSaveable { mutableStateOf(MasterBankFilter.ALL.name) }
    var selectedMasterSectionId by rememberSaveable { mutableStateOf(MasterDashboardSection.BANKS.id) }
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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    LaunchedEffect(adminState.isEmpty(), autoRemoteHydrated) {
        if (adminState.isEmpty() && !autoRemoteHydrated && !remoteRefreshBusy) {
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
        AlertDialog(
            onDismissRequest = { pendingCredentialReset = null },
            title = { Text("Generar claves nuevas") },
            text = {
                Text("Esto reemplaza la clave del admin ${admin.user} y la de sus cajeros. Las claves viejas dejarán de funcionar.")
            },
            confirmButton = {
                CompactActionButton(
                    label = "Generar",
                    onClick = {
                        applyMutation(onRegenerateCredentials(admin))
                        pendingCredentialReset = null
                    },
                    active = true,
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
                    label = "Agregar",
                    onClick = {
                        runCatching {
                            onAddCashiers(admin, countText.toIntOrNull() ?: 1, prefixText)
                        }.onSuccess(::applyMutation)
                            .onFailure { status = it.message ?: "No se pudieron agregar cajeros." }
                        pendingAddCashiers = null
                    },
                    active = true,
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
                    label = "Guardar",
                    onClick = {
                        runCatching {
                            onChangePassword(account.user, passwordText)
                        }.onSuccess(::applyMutation)
                            .onFailure { status = it.message ?: "No se pudo cambiar la clave." }
                        pendingPasswordChange = null
                    },
                    active = true,
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
                    label = "Guardar",
                    onClick = {
                        runCatching {
                            onChangeCashierGroupPassword(admin, passwordText)
                        }.onSuccess(::applyMutation)
                            .onFailure { status = it.message ?: "No se pudo cambiar la clave de los cajeros." }
                        pendingCashierGroupPasswordChange = null
                    },
                    active = true,
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
                    title = "Panel Master",
                    subtitle = "Bancas · Administración compacta",
                    onBack = onBack,
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CompactStatusBadge(
                        label = if (cloudSyncBusy || remoteRefreshBusy || serverProbeBusy) "Sincronizando" else "Sincronizado",
                        tone = if (blockedBanks > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                CompactSegmentedSelector(
                    options = masterDashboardSectionOptions(),
                    selectedId = selectedMasterSectionId,
                    onSelected = { selectedMasterSectionId = it },
                    columns = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (selectedMasterSectionId == MasterDashboardSection.BANKS.id) item {
                CompactPanel {
                    SectionHeader(title = "Bancas", meta = "${cashiers.size} cajeros")
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
            if (selectedMasterSectionId == MasterDashboardSection.RECHARGES.id) item {
                CompactPanel(alt = true) {
                    SectionHeader(title = "Recargas master", meta = masterRechargeProviderLabel())
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
            if (selectedMasterSectionId == MasterDashboardSection.SERVER.id || selectedMasterSectionId == MasterDashboardSection.AUDIT.id) item {
                CompactPanel {
                    SectionHeader(title = "Servidor y nube", meta = if (cloudSyncBusy || remoteRefreshBusy || serverProbeBusy) "En curso" else "Listo")
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
                    if (selectedMasterSectionId == MasterDashboardSection.AUDIT.id) {
                        CompactActionButton(
                            "Abrir auditoría",
                            onClick = onOpenAudit,
                            icon = Icons.Rounded.QueryStats,
                            modifier = Modifier.fillMaxWidth(),
                            tone = ActionTone.Warning,
                        )
                    }
                }
            }
            if (selectedMasterSectionId == MasterDashboardSection.CREDENTIALS.id || issuedCredentials.isNotEmpty()) {
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
            if (selectedMasterSectionId == MasterDashboardSection.BANKS.id && filteredAdmins.isEmpty()) {
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
            } else if (selectedMasterSectionId == MasterDashboardSection.BANKS.id) {
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
                            applyMutation(onSaveBankRechargeAccess(admin, enabled, amount))
                        },
                        onSaveRecargasRapidasCredentials = { username, password ->
                            applyMutation(onSaveBankRecargasRapidasCredentials(admin, username, password))
                        },
                        onDelete = { pendingDelete = admin },
                        onRegenerate = { pendingCredentialReset = admin },
                        onAddCashiers = { pendingAddCashiers = admin },
                        onChangePassword = { pendingPasswordChange = admin },
                        cashiers = bankCashiers,
                        cashiersExpanded = expandedBankIds.contains(admin.id),
                        onToggleCashiers = {
                            expandedBankIds = if (expandedBankIds.contains(admin.id)) {
                                expandedBankIds - admin.id
                            } else {
                                expandedBankIds + admin.id
                            }
                        },
                        onChangeCashierPassword = { cashier -> pendingPasswordChange = cashier },
                        onChangeAllCashiersPassword = { pendingCashierGroupPasswordChange = admin },
                        compact = layout.compactBanks,
                        shortActionLabels = layout.shortBankActionLabels,
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
                Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f), fontWeight = FontWeight.Bold)
            }
            Text("⋮", style = MaterialTheme.typography.headlineSmall, color = Color.White)
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
    onSaveRecargasRapidasCredentials: (String, String) -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onAddCashiers: () -> Unit,
    onChangePassword: () -> Unit,
    cashiers: List<UserAccount>,
    cashiersExpanded: Boolean,
    onToggleCashiers: () -> Unit,
    onChangeCashierPassword: (UserAccount) -> Unit,
    onChangeAllCashiersPassword: () -> Unit,
    compact: Boolean,
    shortActionLabels: Boolean,
) {
    val visual = rememberLotteryNetVisualSpec()
    var rechargeAmountDraft by rememberSaveable(admin.id, admin.rechargesBalance) {
        mutableStateOf(formatPlainAmount(admin.rechargesBalance))
    }
    var rrUsernameDraft by rememberSaveable(admin.id, admin.recargasRapidasUsername) {
        mutableStateOf(admin.recargasRapidasUsername.orEmpty())
    }
    var rrPasswordDraft by rememberSaveable(admin.id, admin.recargasRapidasUsername) {
        mutableStateOf("")
    }
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
            if (compact) {
                Text(
                    "Cajeros $cashierCount · ${admin.territory ?: "RD"} · ${masterRechargeAccessLabel(admin.rechargesEnabled, admin.rechargesAssignedBalance, admin.rechargesBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = visual.colors.ink,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Cajeros: $cashierCount", style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, modifier = Modifier.weight(1f))
                    Text("Territorio: ${admin.territory ?: "RD"}", style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, modifier = Modifier.weight(1f))
                    Text(masterRechargeAccessLabel(admin.rechargesEnabled, admin.rechargesAssignedBalance, admin.rechargesBalance), style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactTextInput(
                    label = "Fondo cartera",
                    value = rechargeAmountDraft,
                    onValueChange = { next -> rechargeAmountDraft = next.filter { it.isDigit() || it == '.' } },
                    modifier = Modifier.weight(1f),
                    keyboardType = KeyboardType.Decimal,
                )
                CompactActionButton(
                    if (admin.rechargesEnabled) "Bloq. rec." else "Act. rec.",
                    onClick = {
                        onSaveRechargeAccess(
                            !admin.rechargesEnabled,
                            rechargeAmountDraft.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    icon = if (admin.rechargesEnabled) Icons.Rounded.WarningAmber else Icons.Rounded.LockOpen,
                    tone = if (admin.rechargesEnabled) ActionTone.Warning else ActionTone.Success,
                )
            }
            val fundSummary = masterRechargeFundSummary(admin.rechargesAssignedBalance, admin.rechargesBalance)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(fundSummary.assignedLabel, style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text(fundSummary.availableLabel, style = MaterialTheme.typography.bodySmall, color = visual.colors.ink, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                Text(fundSummary.soldLabel, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            }
            CompactActionButton(
                "Guardar fondo",
                onClick = {
                    onSaveRechargeAccess(
                        admin.rechargesEnabled,
                        rechargeAmountDraft.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.AdminPanelSettings,
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactActionButton(masterCredentialResetActionLabel(shortActionLabels), onClick = onRegenerate, modifier = Modifier.weight(1f), icon = Icons.Rounded.Key, tone = ActionTone.Warning)
                CompactActionButton(if (shortActionLabels) "+Caj." else "Agregar cajeros", onClick = onAddCashiers, modifier = Modifier.weight(1f), icon = Icons.Rounded.Groups)
                CompactActionButton(if (shortActionLabels) "Clave" else "Cambiar clave", onClick = onChangePassword, modifier = Modifier.weight(1f), icon = Icons.Rounded.Key)
            }
            CompactActionButton(
                "${masterCashierDropdownActionLabel(shortActionLabels)} $cashierCount",
                onClick = onToggleCashiers,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Groups,
            )
            if (cashiersExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompactActionButton(
                        masterCashierGroupPasswordActionLabel(shortActionLabels),
                        onClick = onChangeAllCashiersPassword,
                        modifier = Modifier.fillMaxWidth(),
                        active = cashiers.isNotEmpty(),
                        icon = Icons.Rounded.Key,
                    )
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
                                        cashier.displayName ?: cashier.user,
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
        assignedLabel = "Fondo ${masterMoney(normalizedAssigned)}",
        availableLabel = "Disponible ${masterMoney(normalizedAvailable)}",
        soldLabel = "Vendido ${masterMoney(sold)}",
    )
}

internal fun masterRechargeAccessLabel(
    enabled: Boolean,
    assigned: Double,
    available: Double,
): String {
    return if (enabled) {
        val summary = masterRechargeFundSummary(assigned, available)
        "${summary.assignedLabel} · queda ${masterMoney(available.coerceAtLeast(0.0))}"
    } else {
        "Bloqueada"
    }
}

private fun masterMoney(value: Double): String = "$" + String.format(Locale.US, "%,.0f", value)
