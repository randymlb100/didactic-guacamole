package com.lotterynet.pro.ui.tickets

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Whatsapp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import com.lotterynet.pro.core.calendar.LotteryClosePolicy
import com.lotterynet.pro.core.calendar.StaticHolidayCalendarRepository
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.diagnostics.NativeCrashReporter
import com.lotterynet.pro.core.delivery.TicketDeliveryPolicy
import com.lotterynet.pro.core.export.NativeBitmapExport
import com.lotterynet.pro.core.export.StaticExportTemplateRepository
import com.lotterynet.pro.core.export.TicketSecurity
import com.lotterynet.pro.core.model.SaleDraft
import com.lotterynet.pro.core.model.SaleDraftSnapshot
import com.lotterynet.pro.core.model.SaleStagedRow
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.effectiveDrawDateKey
import com.lotterynet.pro.core.model.formatPlayDisplayNumber
import com.lotterynet.pro.core.model.isPaidStatus
import com.lotterynet.pro.core.model.isPendingWinnerStatus
import com.lotterynet.pro.core.network.ProductionNetworkGuard
import com.lotterynet.pro.core.operations.buildUserActorLabelLookup
import com.lotterynet.pro.core.operations.canonicalizeTicketOwnerForSession
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.core.operations.resolveTicketActorLabel
import com.lotterynet.pro.core.permissions.RoleCapability
import com.lotterynet.pro.core.permissions.canRolePerform
import com.lotterynet.pro.core.printing.BluetoothThermalPrinter
import com.lotterynet.pro.core.printing.IntegratedThermalPrinter
import com.lotterynet.pro.core.printing.ThermalTicketRenderer
import com.lotterynet.pro.core.printing.TicketPrintMark
import com.lotterynet.pro.core.render.LocalRenderCacheRepository
import com.lotterynet.pro.core.render.ticketRenderCacheKey
import com.lotterynet.pro.core.results.PrizeValidationEngine
import com.lotterynet.pro.core.results.PrizeValidationOutcome
import com.lotterynet.pro.core.results.TicketPrizeReconciler
import com.lotterynet.pro.core.sales.BackendTicketActionRequest
import com.lotterynet.pro.core.sales.SupabaseTicketBackendClient
import com.lotterynet.pro.core.sync.NativeOperationalSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketCloudSyncCoordinator
import com.lotterynet.pro.core.sync.NativeTicketRemoteStore
import com.lotterynet.pro.core.sync.NativeTicketSyncQueueRepository
import com.lotterynet.pro.core.sync.isTerminalCancelTicketStatus
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKey
import com.lotterynet.pro.core.sync.resolveOperationalOwnerKeys
import com.lotterynet.pro.core.storage.LocalAdminLimitRepository
import com.lotterynet.pro.core.storage.LocalBrandingRepository
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import com.lotterynet.pro.core.storage.LocalCashierPrizePayoutRepository
import com.lotterynet.pro.core.storage.LocalPrizeConfigRepository
import com.lotterynet.pro.core.storage.LocalResultsRepository
import com.lotterynet.pro.core.storage.LocalSaleDraftRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalAdminLotteryConfigRepository
import com.lotterynet.pro.core.storage.LocalTrustedClockRepository
import com.lotterynet.pro.core.storage.LocalThermalPrinterRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactAdaptiveGrid
import com.lotterynet.pro.ui.common.CompactEmptyState
import com.lotterynet.pro.ui.common.CompactKeyValueRow
import com.lotterynet.pro.ui.common.CompactPanel
import com.lotterynet.pro.ui.common.CompactStatusBadge
import com.lotterynet.pro.ui.common.LotteryLogo
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.SectionHeader
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.plus
import com.lotterynet.pro.ui.navigation.NativeDestination
import com.lotterynet.pro.ui.navigation.redirectIfNativeDestinationBlocked
import com.lotterynet.pro.ui.sales.SalesActivity
import com.lotterynet.pro.ui.sales.resolveSaleRuntimeLotteriesForSystemMode
import com.lotterynet.pro.ui.sales.resolveSalesStartupSystemModeConfig
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class TicketOfficialActivity : AppCompatActivity() {
    private var ticketState by mutableStateOf<TicketRecord?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ticketId = intent?.getStringExtra(EXTRA_TICKET_ID).orEmpty()
        val ticketEpoch = intent?.getLongExtra(EXTRA_TICKET_EPOCH, 0L) ?: 0L
        val bancaName = intent?.getStringExtra(EXTRA_BANCA_NAME).orEmpty().ifBlank { "LotteryNet" }
        val mode = TicketOfficialMode.from(intent?.getStringExtra(EXTRA_ACTION_MODE))
        val snapshotTicket = decodeTicketRecordSnapshot(intent?.getStringExtra(EXTRA_TICKET_SNAPSHOT_JSON))
        val repo = LocalSalesRepository(this)
        val ticketCloudSync = NativeTicketCloudSyncCoordinator(
            salesRepository = repo,
            queueRepository = NativeTicketSyncQueueRepository(this),
        )
        val operationalSync = NativeOperationalSyncCoordinator(ticketGateway = ticketCloudSync)
        val resultsRepository = LocalResultsRepository(this)
        val prizeRepository = LocalPrizeConfigRepository(this)
        val cashierPrizePayoutRepository = LocalCashierPrizePayoutRepository(this)
        val catalogRepository = StaticLotteryCatalogRepository()
        val validationEngine = PrizeValidationEngine(catalogRepository)
        val session = LocalSessionRepository(this).getActiveSession()
        val dateReconciler = TicketPrizeReconciler(
            salesRepository = repo,
            prizeRepository = prizeRepository,
            validationEngine = validationEngine,
            prizeConfigResolver = { ticket ->
                cashierPrizePayoutRepository.resolveForTicket(
                    ownerId = ticket.adminId ?: session?.adminId ?: session?.userId,
                    sellerUser = ticket.sellerUser,
                )
            },
        )
        if (redirectIfNativeDestinationBlocked(this, session?.role, NativeDestination.TICKET_OFFICIAL)) {
            return
        }
        val activeSession = session ?: return
        val usersRepository = LocalUsersRepository(this)
        usersRepository.touchSession(activeSession)
        val cashiers = usersRepository.getCashiers()
        val ticketOutputAccounts = usersRepository.getAdmins() + usersRepository.getSupervisors() + cashiers
        val actorLabelsByKey = buildUserActorLabelLookup(ticketOutputAccounts)
        val systemModeConfig = resolveSalesStartupSystemModeConfig(
            session = activeSession,
            usersRepository = usersRepository,
            adminLotteryConfigRepository = LocalAdminLotteryConfigRepository(this),
        )
        val trustedClockRepository = LocalTrustedClockRepository(this)
        val holidayRepository = StaticHolidayCalendarRepository(
            dominicanLotteryIds = catalogRepository.getCalendarRule().dominicanLotteryIds,
            americanLotteryIds = catalogRepository.getCalendarRule().americanLotteryIds,
        )
        val closePolicy = LotteryClosePolicy(trustedClockRepository, holidayRepository)
        val operationTerritory = normalizeTerritory(activeSession.territory)
        val duplicateModeLotteries = resolveSaleRuntimeLotteriesForSystemMode(
            catalogRepository.getAllLotteries(),
            systemModeConfig,
        )
        val duplicateLotteryOptions = duplicateModeLotteries.map { lottery ->
            val decision = closePolicy.resolveCloseDecision(
                lottery = lottery,
                operationTerritory = operationTerritory,
                allowAdminAfterCloseGrace = activeSession.role == UserRole.ADMIN,
                nowUtcMs = trustedClockRepository.getTrustedUtcMs(),
            )
            DuplicateLotteryOption(
                id = lottery.id,
                name = lottery.name,
                logoAssetPath = lottery.logoAssetPath,
                drawTimeLabel = decision.drawTime ?: lottery.baseDrawTime,
                isClosed = decision.isClosed,
                statusLabel = decision.reason,
                adminGrace = activeSession.role == UserRole.ADMIN &&
                    !decision.isClosed &&
                    decision.reason.contains("Admin extra", ignoreCase = true),
            )
        }
        val duplicateLotteriesById = duplicateModeLotteries.associateBy { it.id }
        val adminLimits = LocalAdminLimitRepository(this).getLimits()
        val cashierSalesLimits = LocalCashierSalesLimitRepository(this)
        val cashierPayoutLimit = if (activeSession.role == UserRole.CASHIER) {
            val userLimit = cashierSalesLimits.getUserLimits(activeSession.adminId ?: activeSession.userId, activeSession.username).payout
            userLimit.takeIf { it > 0.0 } ?: adminLimits.cashierPayoutLimit
        } else {
            0.0
        }
        val ticketDayKey = buildDayKey(ticketEpoch)
        val dayTickets = repo.getTicketsForDay(ticketDayKey)
        val scopedTickets = filterTicketsForOperationalScope(activeSession, dayTickets, cashiers)
        val refreshedTicket = findOfficialTicketCandidate(scopedTickets, ticketId, snapshotTicket)
            ?: findOfficialTicketCandidate(
                filterTicketsForOperationalScope(activeSession, repo.getAllTickets(), usersRepository.getCashiers()),
                ticketId,
                snapshotTicket,
            )
        ticketState = resolveInitialOfficialTicket(snapshotTicket, refreshedTicket)
        val exportRepo = StaticExportTemplateRepository()
        val saleDraftRepository = LocalSaleDraftRepository(this)
        val brandingRepository = LocalBrandingRepository(this)
        val branding = brandingRepository.getBranding()
        val ticketHasClosedLottery = ticketHasClosedLottery(
            ticket = ticketState,
            lotteries = catalogRepository.getAllLotteries(),
            closePolicy = closePolicy,
            operationTerritory = operationTerritory,
            nowUtcMs = trustedClockRepository.getTrustedUtcMs(),
        )

        setContent {
            LotteryNetComposeTheme {
                TicketOfficialRouteCompact(
                    bancaName = resolveTicketOutputBancaName(ticketState, bancaName, ticketOutputAccounts),
                    mode = mode,
                    ticket = ticketState,
                    bancaLogoUri = branding.logoUri,
                    actorLabelsByKey = actorLabelsByKey,
                    exportRepository = exportRepo,
                    role = activeSession.role,
                    onBack = { finish() },
                    onShare = { record, bitmap, whatsappOnly ->
                        val outputBancaName = resolveTicketOutputBancaName(record, bancaName, ticketOutputAccounts)
                        val envelope = exportRepo.buildTicketWhatsAppShare(record, outputBancaName)
                        val renderCache = LocalRenderCacheRepository(this)
                        val renderKey = resolveOfficialTicketRenderCacheKey(record, outputBancaName, branding.logoUri)
                        val uri = renderCache.getUriIfPresent(renderKey) ?: renderCache.saveBitmap(renderKey, bitmap)
                        if (uri != null) {
                            NativeBitmapExport.shareImageUris(
                                context = this,
                                uris = listOf(uri),
                                title = envelope.title,
                                text = "",
                                whatsappOnly = whatsappOnly,
                            )
                        } else {
                            NativeBitmapExport.shareBitmap(
                                context = this,
                                bitmap = bitmap,
                                fileName = envelope.fileName ?: "ticket-${record.id}.png",
                                title = envelope.title,
                                text = "",
                                whatsappOnly = whatsappOnly,
                            )
                        }
                    },
                    onSave = { record, bitmap ->
                        LocalRenderCacheRepository(this).saveBitmap(
                            resolveOfficialTicketRenderCacheKey(
                                record,
                                resolveTicketOutputBancaName(record, bancaName, ticketOutputAccounts),
                                branding.logoUri,
                            ),
                            bitmap,
                        )
                        NativeBitmapExport.saveBitmapToDownloads(this, bitmap, "ticket-${record.id}.png")
                    },
                    onOpenThermal = { record ->
                        thread(name = "official-ticket-thermal-print") {
                            val outputBancaName = resolveTicketOutputBancaName(record, bancaName, ticketOutputAccounts)
                            val prefs = LocalThermalPrinterRepository(this).getPrefs()
                            val chunks = ThermalTicketRenderer().renderTicketChunks(
                                ticket = record,
                                bancaName = outputBancaName,
                                prefs = prefs,
                                printMark = TicketPrintMark.COPIA,
                            )
                            val target = resolveOfficialTicketPrintTarget(
                                integratedAvailable = IntegratedThermalPrinter.isAvailable(this),
                                selectedBluetoothAddress = prefs.selectedPrinterAddress,
                            )
                            var result = BluetoothThermalPrinter.PrintResult(false, "No hay impresora conectada")
                            for (chunk in chunks) {
                                result = when (target) {
                                    OfficialTicketPrintTarget.INTEGRATED -> IntegratedThermalPrinter.printText(this, chunk)
                                    OfficialTicketPrintTarget.BLUETOOTH -> BluetoothThermalPrinter.printText(
                                        context = this,
                                        content = chunk,
                                        prefs = prefs,
                                    )
                                    OfficialTicketPrintTarget.NONE -> BluetoothThermalPrinter.PrintResult(
                                        success = false,
                                        message = "No hay impresora conectada",
                                    )
                                }
                                if (!result.success) break
                                Thread.sleep(250L)
                            }
                            if (result.success && chunks.size > 1) {
                                result = result.copy(message = "Ticket impreso en ${chunks.size} partes")
                            }
                            runOnUiThread {
                                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onPrintPayoutReceipt = { record ->
                        printTicketPayoutReceipt(record, resolveTicketOutputBancaName(record, bancaName, ticketOutputAccounts))
                    },
                    onSharePayoutVoucher = { record ->
                        shareTicketPayoutVoucher(record, resolveTicketOutputBancaName(record, bancaName, ticketOutputAccounts))
                    },
                    onDuplicate = { record ->
                        val snapshot = buildDuplicateSaleDraftFromTicket(record, duplicateLotteriesById.values.toList())
                        saleDraftRepository.save(activeSession, snapshot)
                        startActivity(Intent(this, SalesActivity::class.java).apply {
                            putExtra(
                                SalesActivity.EXTRA_PRELOAD_MESSAGE,
                                "Jugadas del ticket cargadas para nueva venta",
                            )
                        })
                    },
                    onDuplicateWithLotteries = { record, selectedLotteries ->
                        val snapshot = buildDuplicateSaleDraftFromTicket(record, selectedLotteries)
                        saleDraftRepository.save(activeSession, snapshot)
                        startActivity(Intent(this, SalesActivity::class.java).apply {
                            putExtra(
                                SalesActivity.EXTRA_PRELOAD_MESSAGE,
                                "Ticket duplicado con loteria seleccionada",
                            )
                        })
                    },
                    duplicateLotteryOptions = duplicateLotteryOptions,
                    duplicateLotteriesById = duplicateLotteriesById,
                    onMarkPaid = { record ->
                        val canonicalRecord = canonicalizeTicketOwnerForSession(
                            ticket = record,
                            session = activeSession,
                            users = usersRepository.getAdmins() + cashiers,
                        )
                        val locallyConfirmedWinner = canonicalRecord.isPendingWinnerStatus() || canonicalRecord.totalPrize > 0.0
                        val payableRecord = if (locallyConfirmedWinner) {
                            canonicalRecord
                        } else {
                            val ticketResults = dateReconciler.dateAliases(canonicalRecord.effectiveDrawDateKey())
                                .flatMap(resultsRepository::getResultsForDate)
                                .distinctBy { it.lotteryId.ifBlank { it.lotteryName.orEmpty() } }
                            val validation = validationEngine.validate(
                                ticket = canonicalRecord,
                                results = ticketResults,
                                prizeConfig = cashierPrizePayoutRepository.resolveForTicket(
                                    ownerId = canonicalRecord.adminId ?: activeSession.adminId ?: activeSession.userId,
                                    sellerUser = canonicalRecord.sellerUser,
                                ),
                            )
                            if (!validation.didValidate) {
                                Toast.makeText(
                                    this,
                                    "No se pudo confirmar el resultado. No se marco pagado.",
                                    Toast.LENGTH_LONG,
                                ).show()
                                null
                            } else if (validation.totalPrize <= 0.0) {
                                repo.replaceTicket(validation.ticket)
                                Toast.makeText(
                                    this,
                                    "El ticket no tiene premio confirmado. No se marco pagado.",
                                    Toast.LENGTH_LONG,
                                ).show()
                                null
                            } else {
                                validation.ticket
                            }
                        }
                        if (payableRecord == null) {
                            canonicalRecord
                        } else {
                            Toast.makeText(this, "Confirmando pago en servidor...", Toast.LENGTH_SHORT).show()
                            val hasConfirmedPrizeAmount = payableRecord.totalPrize > 0.0
                            val locallyPaid = payableRecord.copy(status = "paid")
                            if (hasConfirmedPrizeAmount) {
                                repo.replaceTicket(locallyPaid)
                                operationalSync.flushTicket(locallyPaid, bancaName)
                            }
                            thread(name = "ticket-paid-server") {
                                val serverResult = runCatching {
                                    val freshBearerToken = SupabaseSessionTokenProvider(
                                        LocalSessionRepository(this),
                                    ).freshAccessToken()
                                        SupabaseTicketBackendClient().payTicket(
                                            request = resolveTicketPayoutBackendRequest(activeSession, locallyPaid),
                                        bearerToken = freshBearerToken,
                                    )
                                }
                                serverResult.onSuccess { response ->
                                    val paidPrize = response.optDouble("amount", locallyPaid.totalPrize)
                                    val paid = locallyPaid.copy(status = "paid", totalPrize = paidPrize)
                                    repo.replaceTicket(paid)
                                    operationalSync.flushTicket(paid, bancaName)
                                    runOnUiThread {
                                        ticketState = paid
                                        Toast.makeText(this, "Pago confirmado en servidor.", Toast.LENGTH_SHORT).show()
                                        if (!hasConfirmedPrizeAmount) {
                                            printTicketPayoutReceipt(paid, bancaName)
                                            shareTicketPayoutVoucher(paid, bancaName)
                                        }
                                    }
                                }.onFailure { error ->
                                    NativeCrashReporter(this).recordHandled("TicketOfficial.payTicketBackend", error)
                                    runOnUiThread {
                                        val message = if (hasConfirmedPrizeAmount) {
                                            "Pago guardado localmente. Servidor pendiente de sincronizar."
                                        } else {
                                            "No se marco pagado: el servidor no confirmo el premio."
                                        }
                                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                            if (hasConfirmedPrizeAmount) locallyPaid else payableRecord
                        }
                    },
                    onVoid = { record ->
                        if (canVoidTicket(
                                role = activeSession.role,
                                ticket = record,
                                actorId = activeSession.userId,
                                actorUser = activeSession.username,
                                nowEpochMs = System.currentTimeMillis(),
                                hasClosedLottery = ticketHasClosedLottery,
                            )
                        ) {
                            Toast.makeText(this, "Confirmando anulacion en servidor...", Toast.LENGTH_SHORT).show()
                            val serverResult = runCatching {
                                runBlocking {
                                    withContext(Dispatchers.IO) {
                                        val freshBearerToken = SupabaseSessionTokenProvider(
                                            LocalSessionRepository(this@TicketOfficialActivity),
                                        ).freshAccessToken()
                                        SupabaseTicketBackendClient().voidTicket(
                                            request = BackendTicketActionRequest(
                                                actorKey = resolveTicketBackendActorKey(activeSession),
                                                adminKey = record.adminUser ?: activeSession.adminUser ?: record.adminId ?: activeSession.username,
                                                ownerKey = record.adminId ?: record.adminUser ?: activeSession.adminUser ?: activeSession.username,
                                                cashierKey = record.sellerId ?: record.sellerUser,
                                                localTicketId = record.id,
                                                clientRequestId = record.id,
                                            ),
                                            bearerToken = freshBearerToken,
                                        )
                                    }
                                }
                            }
                            serverResult.fold(
                                onSuccess = {
                                    val voided = repo.updateTicketStatus(record, "voided")
                                    thread(name = "ticket-void-sync") {
                                        operationalSync.flushTicket(voided, bancaName)
                                        resolveTicketRealtimeSyncOwnerKeys(activeSession, voided).forEach { ownerKey ->
                                            operationalSync.flushOwnerLocalSnapshot(ownerKey, bancaName)
                                        }
                                    }
                                    Toast.makeText(this, "Anulacion confirmada en servidor.", Toast.LENGTH_SHORT).show()
                                    voided
                                },
                                onFailure = { error ->
                                    NativeCrashReporter(this).recordHandled("TicketOfficial.voidTicketBackend", error)
                                    Toast.makeText(
                                        this,
                                        "Servidor no confirmo la anulacion. No se cambio el ticket.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    record
                                },
                            )
                        } else {
                            record
                        }
                    },
                    onDelete = { record, returnLimit ->
                        val canDeleteRecord = canDeleteTicket(
                            role = activeSession.role,
                            ticket = record,
                            actorId = activeSession.userId,
                            actorUser = activeSession.username,
                            nowEpochMs = System.currentTimeMillis(),
                            hasClosedLottery = ticketHasClosedLottery,
                        )
                        if (canDeleteRecord) {
                            Toast.makeText(this, "Confirmando eliminacion en servidor...", Toast.LENGTH_SHORT).show()
                            thread(name = "ticket-delete-backend") {
                                val serverResult = runCatching {
                                    val freshBearerToken = SupabaseSessionTokenProvider(
                                        LocalSessionRepository(this@TicketOfficialActivity),
                                    ).freshAccessToken()
                                    SupabaseTicketBackendClient().deleteTicket(
                                        request = BackendTicketActionRequest(
                                            actorKey = resolveTicketBackendActorKey(activeSession),
                                            adminKey = record.adminUser ?: activeSession.adminUser ?: record.adminId ?: activeSession.username,
                                            ownerKey = record.adminId ?: record.adminUser ?: activeSession.adminUser ?: activeSession.username,
                                            cashierKey = record.sellerId ?: record.sellerUser,
                                            localTicketId = record.id,
                                            clientRequestId = record.id,
                                            returnLimit = returnLimit,
                                        ),
                                        bearerToken = freshBearerToken,
                                    )
                                }
                                serverResult.onSuccess {
                                    thread(name = "ticket-delete-sync") {
                                        resolveTicketRealtimeSyncOwnerKeys(activeSession, record).forEach { ownerKey ->
                                            operationalSync.flushOwnerLocalSnapshot(ownerKey, bancaName)
                                        }
                                    }
                                    runOnUiThread {
                                        repo.deleteTicket(record)
                                        Toast.makeText(this, "Ticket eliminado en servidor.", Toast.LENGTH_SHORT).show()
                                        finish()
                                    }
                                }.onFailure { error ->
                                    NativeCrashReporter(this).recordHandled("TicketOfficial.deleteTicketBackend", error)
                                    runOnUiThread {
                                        Toast.makeText(
                                            this,
                                            "Servidor no confirmo la eliminacion. No se cambio el ticket.",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            }
                        } else {
                            Toast.makeText(
                                this,
                                resolveTicketDeleteDeniedMessage(activeSession.role),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    canVoid = canVoidTicket(
                        role = activeSession.role,
                        ticket = ticketState,
                        actorId = activeSession.userId,
                        actorUser = activeSession.username,
                        nowEpochMs = System.currentTimeMillis(),
                        hasClosedLottery = ticketHasClosedLottery,
                    ),
                    cashierPayoutLimit = cashierPayoutLimit,
                    hasClosedLottery = ticketHasClosedLottery,
                    permissions = ticketState?.let { currentTicket ->
                        resolveOfficialTicketPermissions(
                            role = activeSession.role,
                            mode = mode,
                            ticket = currentTicket,
                            actorId = activeSession.userId,
                            actorUser = activeSession.username,
                            hasClosedLottery = ticketHasClosedLottery,
                        )
                    } ?: OfficialTicketPermissions(
                        showPay = false,
                        showVoid = false,
                        showDuplicate = false,
                        showDelete = false,
                    ),
                )
            }
        }
        if (refreshedTicket != null) {
            thread(name = "official-ticket-hydrate") {
                val canonicalRefreshedTicket = canonicalizeTicketOwnerForSession(
                    ticket = refreshedTicket,
                    session = activeSession,
                    users = usersRepository.getAdmins() + cashiers,
                )
                val drawDayKey = canonicalRefreshedTicket.effectiveDrawDateKey()
                val ticketResults = dateReconciler.dateAliases(drawDayKey)
                    .flatMap(resultsRepository::getResultsForDate)
                    .distinctBy { it.lotteryId.ifBlank { it.lotteryName.orEmpty() } }
                val validation = validationEngine.validate(
                    ticket = canonicalRefreshedTicket,
                    results = ticketResults,
                    prizeConfig = cashierPrizePayoutRepository.resolveForTicket(
                        ownerId = canonicalRefreshedTicket.adminId ?: activeSession.adminId ?: activeSession.userId,
                        sellerUser = canonicalRefreshedTicket.sellerUser,
                    ),
                )
                val protectedTicket = resolveOfficialTicketAfterLocalValidation(
                    current = ticketState ?: canonicalRefreshedTicket,
                    canonical = canonicalRefreshedTicket,
                    validation = validation,
                )
                if (protectedTicket != refreshedTicket) {
                    repo.replaceTicket(protectedTicket)
                }
                runOnUiThread {
                    ticketState = protectedTicket
                }
            }
        }
        thread(name = "official-ticket-remote-catch-up") {
            runCatching {
                val catchUpTicket = ticketState ?: snapshotTicket ?: refreshedTicket
                val ownerKeys = if (catchUpTicket != null) {
                    resolveTicketRealtimeSyncOwnerKeys(activeSession, catchUpTicket)
                } else {
                    listOf(resolveOperationalOwnerKey(activeSession))
                }
                val remoteStore = NativeTicketRemoteStore()
                val remoteRefreshedTicket = ownerKeys.asSequence()
                    .mapNotNull { ownerKey ->
                        ownerKey.takeIf { it.isNotBlank() }?.let { remoteStore.fetchSnapshot(it).tickets }
                    }
                    .mapNotNull { tickets -> findOfficialTicketCandidate(tickets, ticketId, snapshotTicket) }
                    .firstOrNull()
                    ?: return@runCatching
                val nextTicket = resolveInitialOfficialTicket(ticketState ?: snapshotTicket, remoteRefreshedTicket)
                    ?: remoteRefreshedTicket
                if (nextTicket != ticketState) {
                    repo.replaceTicket(nextTicket)
                    runOnUiThread {
                        ticketState = nextTicket
                    }
                }
            }.onFailure { error ->
                NativeCrashReporter(this).recordHandled("TicketOfficial.remoteCatchUp", error)
            }
        }
    }

    private fun buildDayKey(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
        return format.format(Date(epochMs))
    }

    private fun normalizeTerritory(raw: String?): LotteryTerritory {
        return if (raw.equals("USA", ignoreCase = true) || raw.equals("US", ignoreCase = true)) {
            LotteryTerritory.USA
        } else {
            LotteryTerritory.RD
        }
    }

    private fun ticketHasClosedLottery(
        ticket: TicketRecord?,
        lotteries: List<LotteryCatalogItem>,
        closePolicy: LotteryClosePolicy,
        operationTerritory: LotteryTerritory,
        nowUtcMs: Long,
    ): Boolean {
        ticket ?: return false
        val byId = lotteries.associateBy { it.id }
        val byName = lotteries.associateBy { it.name.trim().lowercase(Locale.getDefault()) }
        return ticket.plays
            .flatMap { play ->
                listOfNotNull(
                    play.lotteryId?.let(byId::get),
                    play.secondaryLotteryId?.let(byId::get),
                    play.lotteryName?.trim()?.lowercase(Locale.getDefault())?.let(byName::get),
                    play.secondaryLotteryName?.trim()?.lowercase(Locale.getDefault())?.let(byName::get),
                )
            }
            .distinctBy { it.id }
            .any { lottery ->
                closePolicy.resolveCloseDecision(
                    lottery = lottery,
                    operationTerritory = operationTerritory,
                    nowUtcMs = nowUtcMs,
                ).isClosed
            }
    }

    private fun shareTicketPayoutVoucher(ticket: TicketRecord, bancaName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Voucher de pago ${ticket.serial ?: ticket.id}")
            putExtra(Intent.EXTRA_TEXT, buildTicketPayoutVoucherShareText(ticket, bancaName))
        }
        startActivity(Intent.createChooser(intent, "Compartir voucher de pago").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun printTicketPayoutReceipt(ticket: TicketRecord, bancaName: String) {
        thread(name = "official-ticket-payout-receipt") {
            val prefs = LocalThermalPrinterRepository(this).getPrefs()
            val content = ThermalTicketRenderer().renderPayoutReceipt(
                ticket = ticket,
                bancaName = bancaName,
                prefs = prefs,
            )
            val target = resolveOfficialTicketPrintTarget(
                integratedAvailable = IntegratedThermalPrinter.isAvailable(this),
                selectedBluetoothAddress = prefs.selectedPrinterAddress,
            )
            val result = when (target) {
                OfficialTicketPrintTarget.INTEGRATED -> IntegratedThermalPrinter.printText(this, content)
                OfficialTicketPrintTarget.BLUETOOTH -> BluetoothThermalPrinter.printText(
                    context = this,
                    content = content,
                    prefs = prefs,
                )
                OfficialTicketPrintTarget.NONE -> BluetoothThermalPrinter.PrintResult(
                    success = false,
                    message = "No hay impresora conectada",
                )
            }
            runOnUiThread {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_TICKET_ID = "ticket_id"
        const val EXTRA_TICKET_EPOCH = "ticket_epoch"
        const val EXTRA_BANCA_NAME = "ticket_banca_name"
        const val EXTRA_ACTION_MODE = "ticket_action_mode"
        const val EXTRA_TICKET_SNAPSHOT_JSON = "ticket_snapshot_json"
    }
}

internal enum class TicketOfficialMode(
    val title: String,
    val subtitlePrefix: String,
) {
    SEARCH("Ticket oficial", "Vista oficial"),
    PAY("Cobro de ticket", "Cobro"),
    VOID("Eliminar ticket", "Eliminacion"),
    DUPLICATE("Duplicar ticket", "Duplicado");

    companion object {
        fun from(raw: String?): TicketOfficialMode {
            return when (raw?.trim()?.lowercase(Locale.US)) {
                "pagar" -> PAY
                "anular" -> VOID
                "duplicar" -> DUPLICATE
                else -> SEARCH
            }
        }
    }
}

internal enum class TicketPreviewAction {
    THERMAL,
    WHATSAPP,
    SHARE,
    SAVE,
    DUPLICATE,
    PAY,
    VOID,
    DELETE,
}

internal const val TICKET_ELIMINATE_ACTION_LABEL = "Eliminar"
internal const val TICKET_ELIMINATE_CONFIRM_TITLE = "Eliminar ticket"
internal const val TICKET_ELIMINATE_CONFIRM_TEXT = "En verdad eliminarlo? No se puede revertir."
internal const val TICKET_ELIMINATE_ADMIN_RETURN_LIMIT_TEXT = "Esta loteria ya cerro o tiene resultado. Quieres devolver el monto jugado al tope? Solo Admin puede hacerlo."

internal enum class TicketPreviewSection {
    PRINTING,
    OPERATIONS,
    SECONDARY,
}

internal enum class OfficialTicketPrintTarget {
    INTEGRATED,
    BLUETOOTH,
    NONE,
}

internal fun resolveOfficialTicketPrintTarget(
    integratedAvailable: Boolean,
    selectedBluetoothAddress: String?,
): OfficialTicketPrintTarget {
    return when {
        !selectedBluetoothAddress.isNullOrBlank() -> OfficialTicketPrintTarget.BLUETOOTH
        integratedAvailable -> OfficialTicketPrintTarget.INTEGRATED
        else -> OfficialTicketPrintTarget.NONE
    }
}

internal data class TicketPreviewActionGroup(
    val section: TicketPreviewSection,
    val title: String,
    val meta: String,
    val actions: List<TicketPreviewAction>,
)

internal data class OfficialTicketActionMenuContract(
    val primaryAction: TicketPreviewAction?,
    val visiblePrimaryCount: Int,
    val overflowActions: List<TicketPreviewAction>,
)

internal fun resolveOfficialTicketActionMenuContract(actions: List<TicketPreviewAction>): OfficialTicketActionMenuContract {
    val primary = actions.firstOrNull { it in setOf(TicketPreviewAction.PAY, TicketPreviewAction.DUPLICATE, TicketPreviewAction.DELETE) }
        ?: actions.firstOrNull()
    return OfficialTicketActionMenuContract(
        primaryAction = primary,
        visiblePrimaryCount = if (primary == null) 0 else 1,
        overflowActions = actions.filterNot { it == primary },
    )
}

internal fun resolveOfficialTicketVisibleActions(actions: List<TicketPreviewAction>): List<TicketPreviewAction> = actions

internal data class OfficialTicketPermissions(
    val showPay: Boolean,
    val showVoid: Boolean,
    val showDuplicate: Boolean,
    val showDelete: Boolean,
)

internal data class TicketPayoutContract(
    val canPay: Boolean,
    val alreadyPaid: Boolean,
    val blockedByLimit: Boolean,
    val requiresWinner: Boolean,
    val message: String,
)

internal data class OfficialTicketPlayVisualContract(
    val ballRadiusPx: Float,
    val rowHeightPx: Int,
)

internal data class OfficialTicketViewState(
    val title: String,
    val bancaName: String,
    val serial: String,
    val statusLabel: String,
    val createdAtLabel: String,
    val drawValidLabel: String,
    val operatorLabel: String,
    val securityCode: String,
    val totalLabel: String,
    val primaryAmountLabel: String,
    val primaryAmountSupporting: String,
    val prizeLabel: String,
    val logoUri: String,
    val lotteryGroups: List<OfficialTicketLotteryGroup>,
) {
    val usesLocalLogo: Boolean = logoUri.isNotBlank()
}

internal data class OfficialTicketLotteryGroup(
    val lotteryName: String,
    val playCount: Int,
    val totalLabel: String,
    val plays: List<PlayItem>,
)

internal data class TicketLotteryBadgeGrid(
    val columns: Int,
    val maxVisible: Int,
)

internal data class DuplicateLotteryOption(
    val id: String,
    val name: String,
    val logoAssetPath: String?,
    val drawTimeLabel: String,
    val isClosed: Boolean,
    val statusLabel: String = "",
    val adminGrace: Boolean = false,
)

internal fun resolveDuplicateSelectableLotteries(options: List<DuplicateLotteryOption>): List<DuplicateLotteryOption> {
    return options
        .filterNot { it.isClosed }
        .sortedWith(
            compareBy<DuplicateLotteryOption>(
                { parseDuplicateDrawMinutes(it.drawTimeLabel) },
                { it.name.lowercase(Locale.US) },
            ),
        )
}

private fun parseDuplicateDrawMinutes(value: String): Int {
    val normalized = value.trim().uppercase(Locale.US)
        .replace(".", ":")
        .replace(Regex("\\s+"), "")
    val match = Regex("""^(\d{1,2})(?::(\d{2}))?(AM|PM)?$""").find(normalized) ?: return Int.MAX_VALUE
    var hour = match.groupValues[1].toIntOrNull() ?: return Int.MAX_VALUE
    val minute = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
    val period = match.groupValues.getOrNull(3).orEmpty()
    if (period == "PM" && hour < 12) hour += 12
    if (period == "AM" && hour == 12) hour = 0
    return hour * 60 + minute
}

internal fun buildDuplicateSaleDraftFromTicket(
    ticket: TicketRecord,
    selectedLotteries: List<LotteryCatalogItem>,
): SaleDraftSnapshot {
    val rows = buildList {
        selectedLotteries.distinctBy { it.id }.forEach { lottery ->
            ticket.plays.forEach { play ->
                add(
                    SaleStagedRow(
                        lotteryId = lottery.id,
                        lotteryName = lottery.name,
                        secondaryLotteryId = null,
                        secondaryLotteryName = null,
                        playType = play.playType,
                        label = duplicatePlayLabel(play.playType),
                        number = play.number,
                        displayNumber = formatPlayDisplayNumber(play.number, play.playType),
                        amount = play.amount,
                    ),
                )
            }
        }
    }.distinctBy { row ->
        listOf(row.lotteryId, row.playType, row.number, row.amount.toString()).joinToString("|")
    }
    val defaultMode = rows.firstOrNull()?.playType?.takeIf {
        it in setOf("Q", "P", "T", "SP")
    } ?: "Q"
    return SaleDraftSnapshot(
        draft = SaleDraft(
            selectedLotteryIds = rows.map { it.lotteryId }.distinct(),
            classicMode = defaultMode,
            superPaleEnabled = rows.any { it.playType == "SP" },
        ),
        stagedRows = rows,
        savedAtEpochMs = System.currentTimeMillis(),
    )
}

private fun duplicatePlayLabel(playType: String): String {
    return when (playType) {
        "SP" -> "Super Pale"
        "P" -> "Pale"
        "T" -> "Tripleta"
        "Q" -> "Quiniela"
        else -> playType
    }
}

internal fun resolveOfficialTicketPlayVisualContract(
    partCount: Int,
    hasLongPart: Boolean,
): OfficialTicketPlayVisualContract {
    return OfficialTicketPlayVisualContract(
        ballRadiusPx = when {
            hasLongPart -> 36f
            partCount <= 2 -> 40f
            else -> 38f
        },
        rowHeightPx = 196,
    )
}

internal fun buildOfficialTicketViewState(
    ticket: TicketRecord,
    bancaName: String,
    mode: TicketOfficialMode,
    securityCode: String,
    logoUri: String,
    actorLabelsByKey: Map<String, String> = emptyMap(),
): OfficialTicketViewState {
    val groups = ticket.plays
        .groupBy { play -> play.lotteryName?.takeIf { it.isNotBlank() } ?: "Sin lotería" }
        .map { (lotteryName, plays) ->
            OfficialTicketLotteryGroup(
                lotteryName = lotteryName,
                playCount = plays.size,
                totalLabel = formatMoney(plays.sumOf { it.amount }),
                plays = plays,
            )
        }
    val hasConfirmedPrize = ticket.totalPrize > 0.0
    return OfficialTicketViewState(
        title = mode.title,
        bancaName = bancaName,
        serial = ticket.serial ?: ticket.id,
        statusLabel = ticketStatusLabel(ticket.status),
        createdAtLabel = formatTicketDateTime(ticket.createdAtEpochMs),
        drawValidLabel = "Este ticket es válido para el sorteo ${NativeBitmapExport.formatDrawDateForTicket(ticket.effectiveDrawDateKey(), resolveTicketDrawTimeLabel(ticket))}",
        operatorLabel = resolveTicketActorLabel(ticket, actorLabelsByKey, fallback = "Sin usuario"),
        securityCode = securityCode.ifBlank { ticket.serial ?: ticket.id },
        totalLabel = formatMoney(if (hasConfirmedPrize) ticket.totalPrize else ticket.total),
        primaryAmountLabel = if (hasConfirmedPrize) "Premio" else "Total",
        primaryAmountSupporting = if (hasConfirmedPrize) "Venta ${formatMoney(ticket.total)}" else "${ticket.plays.size} jugadas",
        prizeLabel = formatMoney(ticket.totalPrize),
        logoUri = logoUri,
        lotteryGroups = groups,
    )
}

internal fun resolveTicketAfterPayoutValidation(ticket: TicketRecord): TicketRecord {
    val hasPrize = ticket.isPendingWinnerStatus() || ticket.totalPrize > 0.0
    if (!hasPrize || ticket.isPaidStatus()) return ticket
    return ticket.copy(status = "paid")
}

internal fun resolveOfficialTicketAfterLocalValidation(
    current: TicketRecord,
    canonical: TicketRecord,
    validation: PrizeValidationOutcome,
): TicketRecord {
    val validated = if (validation.didValidate) validation.ticket else canonical
    val protectedSource = resolveInitialOfficialTicket(current, canonical) ?: current
    val currentHasPendingPrize = protectedSource.isOfficialPayRelevant() && !protectedSource.isPaidStatus()
    val validationRemovedPrize = !validated.isOfficialPayRelevant() || validated.totalPrize <= 0.0
    return if (currentHasPendingPrize && validationRemovedPrize) protectedSource else validated
}

internal fun resolveTicketLotteryBadgeGrid(
    lotteryCount: Int,
    windowMode: LotteryNetWindowMode,
): TicketLotteryBadgeGrid {
    val columns = when {
        lotteryCount <= 1 -> 1
        windowMode == LotteryNetWindowMode.POS_TIGHT -> 2
        lotteryCount <= 3 -> lotteryCount
        else -> 2
    }
    return TicketLotteryBadgeGrid(
        columns = columns,
        maxVisible = 6,
    )
}

internal fun resolveTicketPreviewActionGroups(
    showPay: Boolean,
    showVoid: Boolean,
    showDuplicate: Boolean,
    showDelete: Boolean = false,
) : List<TicketPreviewActionGroup> {
    val operations = buildList {
        if (showPay) add(TicketPreviewAction.PAY)
        if (showDuplicate) add(TicketPreviewAction.DUPLICATE)
        if (showVoid || showDelete) add(TicketPreviewAction.DELETE)
    }
    return buildList {
        add(
            TicketPreviewActionGroup(
                section = TicketPreviewSection.PRINTING,
                title = "Impresión y envío",
                meta = "Ticket",
                actions = listOf(
                    TicketPreviewAction.THERMAL,
                    TicketPreviewAction.WHATSAPP,
                    TicketPreviewAction.SHARE,
                ),
            ),
        )
        if (operations.isNotEmpty()) {
            add(
                TicketPreviewActionGroup(
                    section = TicketPreviewSection.OPERATIONS,
                    title = "Operación",
                    meta = "Caja",
                    actions = operations,
                ),
            )
        }
        add(
            TicketPreviewActionGroup(
                section = TicketPreviewSection.SECONDARY,
                title = "Secundarias",
                meta = "Archivo",
                actions = listOf(TicketPreviewAction.SAVE),
            ),
        )
    }
}

internal fun resolveTicketPreviewActionGroups(
    permissions: OfficialTicketPermissions,
): List<TicketPreviewActionGroup> = resolveTicketPreviewActionGroups(
    showPay = permissions.showPay,
    showVoid = permissions.showVoid,
    showDuplicate = permissions.showDuplicate,
    showDelete = permissions.showDelete,
)

internal fun shouldRenderOfficialTicketBitmapDirectlyInComposition(): Boolean = false

internal fun shouldShowOfficialTicketVisualPreview(): Boolean = false

internal fun officialTicketSnapshotPlayTypeLabel(playType: String): String {
    return when (playType.uppercase(Locale.US)) {
        "P3" -> "P3STRAIGHT"
        "P4" -> "P4STRAIGHT"
        "P3BOX" -> "P3BOX"
        "P4BOX" -> "P4BOX"
        else -> playType.ifBlank { "-" }
    }
}

private fun ticketPreviewActionLabel(action: TicketPreviewAction): String {
    return when (action) {
        TicketPreviewAction.THERMAL -> "Imprimir"
        TicketPreviewAction.WHATSAPP -> "WhatsApp"
        TicketPreviewAction.SHARE -> "Compartir"
        TicketPreviewAction.SAVE -> "Guardar"
        TicketPreviewAction.DUPLICATE -> "Duplicar"
        TicketPreviewAction.PAY -> "Cobrar"
        TicketPreviewAction.VOID,
        TicketPreviewAction.DELETE -> TICKET_ELIMINATE_ACTION_LABEL
    }
}

internal data class OfficialTicketBitmapPreviewPolicy(
    val renderInComposition: Boolean,
    val maxHeightDp: Int,
)

internal fun resolveOfficialTicketBitmapPreviewPolicy(
    windowMode: LotteryNetWindowMode,
): OfficialTicketBitmapPreviewPolicy {
    val maxHeight = when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> 440
        LotteryNetWindowMode.POS -> 520
        else -> 620
    }
    return OfficialTicketBitmapPreviewPolicy(
        renderInComposition = shouldRenderOfficialTicketBitmapDirectlyInComposition(),
        maxHeightDp = maxHeight,
    )
}

internal fun resolveOfficialTicketRenderCacheKey(
    ticket: TicketRecord,
    bancaName: String,
    logoUri: String,
): String = ticketRenderCacheKey(ticket, bancaName, logoUri)

internal fun resolveOfficialTicketPermissions(
    role: UserRole,
    mode: TicketOfficialMode,
    ticket: TicketRecord,
    actorId: String? = null,
    actorUser: String? = null,
    nowEpochMs: Long = System.currentTimeMillis(),
    hasClosedLottery: Boolean = false,
): OfficialTicketPermissions {
    val isVoided = isTerminalCancelTicketStatus(ticket.status)
    val isPaid = ticket.isPaidStatus()
    val isPayable = !isVoided && !isPaid && (
        ticket.isPendingWinnerStatus() ||
        ticket.totalPrize > 0.0
    )
    val canPay = canRolePerform(role, RoleCapability.PAY_TICKETS)
    val canDuplicate = canRolePerform(role, RoleCapability.DUPLICATE_TICKETS)
    if (role == UserRole.CASHIER) {
        val canDelete = canDeleteTicket(
            role = role,
            ticket = ticket,
            actorId = actorId,
            actorUser = actorUser,
            nowEpochMs = nowEpochMs,
            hasClosedLottery = hasClosedLottery,
        )
        return OfficialTicketPermissions(
            showPay = canPay && isPayable,
            showVoid = false,
            showDuplicate = canDuplicate && mode == TicketOfficialMode.DUPLICATE && !isVoided,
            showDelete = canDelete,
        )
    }
    return OfficialTicketPermissions(
        showPay = canPay && isPayable,
        showVoid = false,
        showDuplicate = canDuplicate && mode != TicketOfficialMode.PAY && !isVoided,
        showDelete = canDeleteTicket(
            role = role,
            ticket = ticket,
            actorId = actorId,
            actorUser = actorUser,
            nowEpochMs = nowEpochMs,
            hasClosedLottery = hasClosedLottery,
        ),
    )
}

internal fun canVoidTicket(
    role: UserRole,
    ticket: TicketRecord?,
    actorId: String? = null,
    actorUser: String? = null,
    nowEpochMs: Long = System.currentTimeMillis(),
    hasClosedLottery: Boolean = false,
): Boolean {
    ticket ?: return false
    val isVoided = isTerminalCancelTicketStatus(ticket.status)
    if (isVoided || ticket.isPaidStatus()) return false
    if (canRolePerform(role, RoleCapability.DELETE_TICKETS)) return true
    if (role != UserRole.CASHIER) return false
    if (!canRolePerform(role, RoleCapability.DELETE_OWN_RECENT_TICKET)) return false
    if (hasClosedLottery) return false
    val isOwner = (!actorId.isNullOrBlank() && ticket.sellerId.equals(actorId, ignoreCase = true)) ||
        (!actorUser.isNullOrBlank() && ticket.sellerUser.equals(actorUser, ignoreCase = true))
    if (!isOwner) return false
    return nowEpochMs - ticket.createdAtEpochMs <= CASHIER_VOID_WINDOW_MS
}

internal fun canDeleteTicket(
    role: UserRole,
    ticket: TicketRecord?,
    actorId: String? = null,
    actorUser: String? = null,
    nowEpochMs: Long = System.currentTimeMillis(),
    hasClosedLottery: Boolean = false,
): Boolean {
    ticket ?: return false
    if (canRolePerform(role, RoleCapability.DELETE_TICKETS)) return true
    if (ticket.isPaidStatus()) return false
    if (role != UserRole.CASHIER) return false
    if (!canRolePerform(role, RoleCapability.DELETE_OWN_RECENT_TICKET)) return false
    if (hasClosedLottery) return false
    if (isTerminalCancelTicketStatus(ticket.status)) return false
    val isOwner = (!actorId.isNullOrBlank() && ticket.sellerId.equals(actorId, ignoreCase = true)) ||
        (!actorUser.isNullOrBlank() && ticket.sellerUser.equals(actorUser, ignoreCase = true))
    if (!isOwner) return false
    return nowEpochMs - ticket.createdAtEpochMs <= CASHIER_VOID_WINDOW_MS
}

internal fun shouldShowOfficialTicketDeleteAction(
    basePermissions: OfficialTicketPermissions,
    currentPermissions: OfficialTicketPermissions,
    currentTicketVoided: Boolean,
): Boolean {
    return basePermissions.showDelete || currentPermissions.showDelete
}

internal fun resolveTicketDeleteDeniedMessage(role: UserRole): String {
    return if (role == UserRole.CASHIER) {
        "El cajero solo puede eliminar su propio ticket dentro de 2 minutos"
    } else {
        "No tienes permiso para eliminar este ticket"
    }
}

internal fun resolveTicketDeleteSyncOwnerKey(session: ActiveSession, ticket: TicketRecord): String {
    return ticket.adminId?.takeIf { it.isNotBlank() } ?: resolveOperationalOwnerKey(session)
}

internal fun resolveTicketRealtimeSyncOwnerKeys(session: ActiveSession, ticket: TicketRecord): List<String> {
    return (
        listOf(
            ticket.adminId,
            ticket.sellerId,
            session.adminId,
            session.userId,
            ticket.adminUser,
            ticket.sellerUser,
            session.adminUser,
            session.username,
        ) + resolveOperationalOwnerKeys(session) + listOf(resolveOperationalOwnerKey(session))
        )
        .mapNotNull { value -> value?.trim()?.takeIf { it.isNotBlank() } }
        .distinctBy { it.lowercase() }
}

internal fun resolveTicketPayoutSyncOwnerKey(session: ActiveSession, ticket: TicketRecord): String {
    return ticket.adminId?.takeIf { it.isNotBlank() } ?: resolveOperationalOwnerKey(session)
}

internal fun resolveTicketBackendActorKey(session: ActiveSession): String {
    return session.username.takeIf { it.isNotBlank() }
        ?: session.adminUser?.takeIf { it.isNotBlank() }
        ?: session.adminId?.takeIf { it.isNotBlank() }
        ?: session.userId
}

internal fun resolveTicketPayoutBackendRequest(
    session: ActiveSession,
    ticket: TicketRecord,
): BackendTicketActionRequest {
    val adminKey = ticket.adminId?.takeIf { it.isNotBlank() }
        ?: session.adminId?.takeIf { it.isNotBlank() }
        ?: ticket.adminUser?.takeIf { it.isNotBlank() }
        ?: session.adminUser?.takeIf { it.isNotBlank() }
        ?: session.userId
    val cashierKey = ticket.sellerId?.takeIf { it.isNotBlank() }
        ?: session.userId.takeIf { it.isNotBlank() }
        ?: ticket.sellerUser?.takeIf { it.isNotBlank() }
        ?: session.username
    return BackendTicketActionRequest(
        actorKey = resolveTicketBackendActorKey(session),
        adminKey = adminKey,
        ownerKey = adminKey,
        cashierKey = cashierKey,
        localTicketId = ticket.id,
        clientRequestId = ticket.id,
    )
}

internal const val CASHIER_VOID_WINDOW_MS: Long = 120_000L

internal fun resolveTicketPayoutContract(
    ticket: TicketRecord,
    cashierPayoutLimit: Double,
): TicketPayoutContract {
    val alreadyPaid = ticket.isPaidStatus()
    val hasPrize = ticket.isPendingWinnerStatus() || ticket.totalPrize > 0.0
    val confirmedPrizeAmount = ticket.totalPrize.takeIf { it > 0.0 }
    val blockedByLimit = cashierPayoutLimit > 0.0 &&
        confirmedPrizeAmount != null &&
        confirmedPrizeAmount > cashierPayoutLimit
    val canPay = hasPrize && !alreadyPaid && !blockedByLimit
    val message = when {
        alreadyPaid -> "Este ticket ya fue pagado"
        !hasPrize -> "El ticket no tiene premio pendiente para pagar"
        blockedByLimit -> "Pago bloqueado por tope del cajero"
        confirmedPrizeAmount == null -> "Ticket listo para confirmar pago en servidor"
        else -> "Ticket listo para pagar"
    }
    return TicketPayoutContract(
        canPay = canPay,
        alreadyPaid = alreadyPaid,
        blockedByLimit = blockedByLimit,
        requiresWinner = !hasPrize,
        message = message,
    )
}

@Composable
private fun TicketOfficialRouteCompact(
    bancaName: String,
    mode: TicketOfficialMode,
    ticket: TicketRecord?,
    bancaLogoUri: String,
    actorLabelsByKey: Map<String, String>,
    exportRepository: StaticExportTemplateRepository,
    role: UserRole,
    onBack: () -> Unit,
    onShare: (TicketRecord, Bitmap, Boolean) -> NativeBitmapExport.ExportActionResult,
    onSave: (TicketRecord, Bitmap) -> Boolean,
    onOpenThermal: (TicketRecord) -> Unit,
    onPrintPayoutReceipt: (TicketRecord) -> Unit,
    onSharePayoutVoucher: (TicketRecord) -> Unit,
    onDuplicate: (TicketRecord) -> Unit,
    onDuplicateWithLotteries: (TicketRecord, List<LotteryCatalogItem>) -> Unit,
    duplicateLotteryOptions: List<DuplicateLotteryOption>,
    duplicateLotteriesById: Map<String, LotteryCatalogItem>,
    onMarkPaid: (TicketRecord) -> TicketRecord,
    onVoid: (TicketRecord) -> TicketRecord,
    onDelete: (TicketRecord, Boolean) -> Unit,
    canVoid: Boolean,
    cashierPayoutLimit: Double,
    hasClosedLottery: Boolean,
    permissions: OfficialTicketPermissions,
) {
    val visual = rememberLotteryNetVisualSpec()
    val localContext = LocalContext.current
    if (ticket == null) {
        Surface(modifier = Modifier.fillMaxSize(), color = visual.colors.background) {
            CompactEmptyState("No se encontro el ticket oficial", modifier = Modifier.padding(20.dp))
        }
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = visual.colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        var currentTicket by remember(ticket.id) { mutableStateOf(ticket) }
        LaunchedEffect(ticket) {
            val merged = resolveInitialOfficialTicket(currentTicket, ticket) ?: ticket
            if (merged != currentTicket) {
                currentTicket = merged
            }
        }
        var actionMessage by remember(currentTicket.id) { mutableStateOf<String?>(null) }
        var showDuplicateMenu by remember(currentTicket.id, mode) { mutableStateOf(mode == TicketOfficialMode.DUPLICATE) }
        var showPayoutConfirm by remember(currentTicket.id, mode) { mutableStateOf(mode == TicketOfficialMode.PAY) }
        var showEliminateConfirm by remember(currentTicket.id) { mutableStateOf(false) }
        var selectedDuplicateLotteryIds by remember(currentTicket.id) { mutableStateOf(emptySet<String>()) }
        var actionBusy by remember(currentTicket.id) { mutableStateOf(false) }
        val actionScope = rememberCoroutineScope()
        val selectableDuplicateLotteries = remember(duplicateLotteryOptions) {
            resolveDuplicateSelectableLotteries(duplicateLotteryOptions)
        }
        val securityCode = TicketSecurity.resolveSecurityCode(currentTicket, bancaName)
        suspend fun renderBitmapsForAction(record: TicketRecord): List<Bitmap> {
            return withContext(Dispatchers.IO) {
                runCatching {
                    val security = TicketSecurity.resolveSecurityCode(record, bancaName)
                    val estimatedHeight = NativeBitmapExport.estimateOfficialTicketBitmapHeight(record, security)
                    if (TicketDeliveryPolicy.shouldRenderPreviewBitmap(record, estimatedHeight)) {
                        listOf(
                            NativeBitmapExport.renderOfficialTicketBitmap(
                                context = localContext,
                                ticket = record,
                                bancaName = bancaName,
                                securityCode = security,
                                bancaLogoUri = bancaLogoUri,
                            ),
                        )
                    } else {
                        listOf(
                            ThermalTicketRenderer().renderCompactShareBitmap(
                                ticket = record,
                                bancaName = bancaName,
                            ),
                        )
                    }
                }.getOrDefault(emptyList())
            }
        }
        fun preparingTicketMessage(record: TicketRecord): String {
            val lotteryCount = record.plays
                .map { play -> listOf(play.lotteryName, play.secondaryLotteryName).filterNotNull().joinToString("/") }
                .distinct()
                .size
            return if (record.plays.size >= 70 || lotteryCount >= 4) {
                "Ticket grande: preparando plantilla compacta para que WhatsApp no lo corte."
            } else {
                "Preparando imagen del ticket..."
            }
        }
        suspend fun shareTicketImages(record: TicketRecord, whatsappOnly: Boolean): String {
            val bitmaps = renderBitmapsForAction(record)
            if (bitmaps.isEmpty()) return "No se pudo preparar la imagen del ticket"
            val envelope = exportRepository.buildTicketWhatsAppShare(record, bancaName)
            val renderCache = LocalRenderCacheRepository(localContext)
            val estimatedHeight = NativeBitmapExport.estimateOfficialTicketBitmapHeight(
                record,
                TicketSecurity.resolveSecurityCode(record, bancaName),
            )
            val renderKey = ticketRenderCacheKey(record, bancaName = bancaName, logoUri = bancaLogoUri).let {
                if (TicketDeliveryPolicy.shouldRenderPreviewBitmap(record, estimatedHeight)) it else "$it|compact-thermal-share"
            }
            val uris = withContext(Dispatchers.IO) {
                renderCache.saveBitmaps(renderKey, bitmaps)
            }
            return if (uris.isNotEmpty()) {
                NativeBitmapExport.shareImageUris(
                    context = localContext,
                    uris = uris,
                    title = envelope.title,
                    text = envelope.text,
                    whatsappOnly = whatsappOnly,
                ).message
            } else {
                "No se pudo preparar el lote de imagenes"
            }
        }
        val verificationCode = securityCode.ifBlank { currentTicket.serial ?: currentTicket.id }
        val ticketViewState = remember(currentTicket, bancaName, securityCode, bancaLogoUri, mode) {
            buildOfficialTicketViewState(
                ticket = currentTicket,
                bancaName = bancaName,
                mode = mode,
                securityCode = securityCode,
                logoUri = bancaLogoUri,
                actorLabelsByKey = actorLabelsByKey,
            )
        }
        val payoutContract = resolveTicketPayoutContract(currentTicket, cashierPayoutLimit)
        val payoutBlocked = payoutContract.blockedByLimit
        val currentTicketVoided = isTerminalCancelTicketStatus(currentTicket.status)
        val currentPermissions = resolveOfficialTicketPermissions(
            role = if (canVoid) UserRole.ADMIN else UserRole.CASHIER,
            mode = mode,
            ticket = currentTicket,
            hasClosedLottery = hasClosedLottery,
        )
        val showDuplicate = permissions.showDuplicate && currentPermissions.showDuplicate && !currentTicketVoided
        val showPay = permissions.showPay && currentPermissions.showPay
        val showVoid = permissions.showVoid
        val showDelete = shouldShowOfficialTicketDeleteAction(
            basePermissions = permissions,
            currentPermissions = currentPermissions,
            currentTicketVoided = currentTicketVoided,
        )
        val statusTone = when {
            currentTicket.isPaidStatus() -> MaterialTheme.colorScheme.primary
            currentTicket.isPendingWinnerStatus() -> MaterialTheme.colorScheme.tertiary
            else -> visual.colors.neutral
        }
        fun canRunCriticalTicketOperation(): Boolean {
            return ProductionNetworkGuard.canRunCriticalOperation(
                ProductionNetworkGuard.hasValidatedInternet(localContext),
            )
        }
        fun markCurrentTicketPaidAndPrintReceipt(): String {
            if (!canRunCriticalTicketOperation()) {
                return ProductionNetworkGuard.NO_INTERNET_ACTION_MESSAGE
            }
            return if (payoutContract.canPay) {
                val awaitingServerPrize = currentTicket.totalPrize <= 0.0 && currentTicket.isPendingWinnerStatus()
                currentTicket = onMarkPaid(currentTicket)
                if (currentTicket.isPaidStatus()) {
                    onPrintPayoutReceipt(currentTicket)
                    onSharePayoutVoucher(currentTicket)
                    "Ticket pagado. Voucher listo para compartir."
                } else if (awaitingServerPrize) {
                    "Confirmando premio real en servidor..."
                } else {
                    "El ticket no tiene premio pendiente para pagar"
                }
            } else {
                payoutContract.message
            }
        }
        fun eliminateCurrentTicket(returnLimit: Boolean = false): String {
            if (!canRunCriticalTicketOperation()) {
                return ProductionNetworkGuard.NO_INTERNET_ACTION_MESSAGE
            }
            if (!showDelete) {
                return "No tienes permiso para eliminar este ticket"
            }
            onDelete(currentTicket, returnLimit)
            return "Validando eliminacion en servidor..."
        }
        val actionGroups: List<Pair<TicketPreviewActionGroup, List<TicketQuickActionSpec>>> =
            resolveTicketPreviewActionGroups(
                showPay = showPay,
                showVoid = showVoid && canVoid,
                showDuplicate = showDuplicate,
                showDelete = showDelete,
            ).map { group ->
                group to group.actions.map { action ->
                    when (action) {
                        TicketPreviewAction.THERMAL -> TicketQuickActionSpec("Imprimir", Icons.Rounded.Print, ActionTone.Warning, emphasized = mode == TicketOfficialMode.SEARCH) {
                            onOpenThermal(currentTicket)
                            actionMessage = "Enviando a impresora"
                        }
                        TicketPreviewAction.WHATSAPP -> TicketQuickActionSpec("WhatsApp", Icons.Rounded.Whatsapp, ActionTone.Success) {
                            if (!actionBusy) {
                                actionBusy = true
                                actionMessage = preparingTicketMessage(currentTicket)
                                actionScope.launch {
                                    actionMessage = shareTicketImages(currentTicket, true)
                                    actionBusy = false
                                }
                            }
                        }
                        TicketPreviewAction.SHARE -> TicketQuickActionSpec("Compartir", Icons.Rounded.Share, ActionTone.Secondary) {
                            if (!actionBusy) {
                                actionBusy = true
                                actionMessage = preparingTicketMessage(currentTicket)
                                actionScope.launch {
                                    actionMessage = shareTicketImages(currentTicket, false)
                                    actionBusy = false
                                }
                            }
                        }
                        TicketPreviewAction.SAVE -> TicketQuickActionSpec("Guardar", Icons.Rounded.Download, ActionTone.Secondary) {
                            if (!actionBusy) {
                                actionBusy = true
                                actionMessage = preparingTicketMessage(currentTicket)
                                actionScope.launch {
                                    val readyBitmap = renderBitmapsForAction(currentTicket).firstOrNull()
                                    actionMessage = if (readyBitmap == null) {
                                        "No se pudo preparar la imagen del ticket"
                                    } else {
                                        val saved = onSave(currentTicket, readyBitmap)
                                        if (saved) "Ticket exportado en Descargas" else "No se pudo guardar el ticket"
                                    }
                                    actionBusy = false
                                }
                            }
                        }
                        TicketPreviewAction.DUPLICATE -> TicketQuickActionSpec("Duplicar", Icons.Rounded.ContentCopy, ActionTone.Secondary, emphasized = mode == TicketOfficialMode.DUPLICATE) {
                            showDuplicateMenu = true
                            actionMessage = "Elige la loteria abierta para duplicar"
                        }
                        TicketPreviewAction.PAY -> TicketQuickActionSpec("Cobrar", Icons.Rounded.Paid, ActionTone.Success, emphasized = mode == TicketOfficialMode.PAY) {
                            actionMessage = markCurrentTicketPaidAndPrintReceipt()
                        }
                        TicketPreviewAction.VOID,
                        TicketPreviewAction.DELETE -> TicketQuickActionSpec(
                            TICKET_ELIMINATE_ACTION_LABEL,
                            Icons.Rounded.DeleteForever,
                            ActionTone.Danger,
                            emphasized = mode == TicketOfficialMode.VOID,
                        ) {
                            if (canRunCriticalTicketOperation()) {
                                showEliminateConfirm = true
                            } else {
                                actionMessage = ProductionNetworkGuard.NO_INTERNET_ACTION_MESSAGE
                            }
                        }
                    }
                }
        }
        val flatActions = actionGroups.flatMap { it.second }
        val visibleActions = remember(flatActions) {
            resolveOfficialTicketVisibleActions(actionGroups.flatMap { it.first.actions })
                .mapNotNull { action -> flatActions.firstOrNull { it.label == ticketPreviewActionLabel(action) } }
        }
        val lotteryCatalog = remember(currentTicket.id) { StaticLotteryCatalogRepository() }
        val lotteryBadges = remember(currentTicket.plays) {
            currentTicket.plays
                .mapNotNull { play ->
                    val name = play.lotteryName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val logo = lotteryCatalog.getLotteryById(play.lotteryId.orEmpty())
                        ?: lotteryCatalog.getLotteryByName(name)
                    Triple(name, logo?.logoAssetPath, play.lotteryId.orEmpty())
                }
                .distinctBy { it.first }
                .take(6)
        }
        val lotteryBadgeGrid = resolveTicketLotteryBadgeGrid(lotteryBadges.size, visual.windowMode)

        if (showEliminateConfirm) {
            val adminClosedLottery = role == UserRole.ADMIN && hasClosedLottery
            AlertDialog(
                onDismissRequest = { showEliminateConfirm = false },
                title = { Text(TICKET_ELIMINATE_CONFIRM_TITLE) },
                text = {
                    Text(
                        if (adminClosedLottery) {
                            TICKET_ELIMINATE_ADMIN_RETURN_LIMIT_TEXT
                        } else {
                            TICKET_ELIMINATE_CONFIRM_TEXT
                        },
                    )
                },
                confirmButton = {
                    if (adminClosedLottery) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    showEliminateConfirm = false
                                    actionMessage = eliminateCurrentTicket(returnLimit = false)
                                },
                            ) {
                                Text("Sin devolver")
                            }
                            Button(
                                onClick = {
                                    showEliminateConfirm = false
                                    actionMessage = eliminateCurrentTicket(returnLimit = true)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("Devolver tope")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                showEliminateConfirm = false
                                actionMessage = eliminateCurrentTicket()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White,
                            ),
                        ) {
                            Text("Si, eliminar")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEliminateConfirm = false }) {
                        Text("No")
                    }
                },
            )
        }

        if (showPayoutConfirm) {
            AlertDialog(
                onDismissRequest = { showPayoutConfirm = false },
                title = {
                    Text(if (payoutContract.canPay) "Ticket ganador" else "Ticket sin pago disponible")
                },
                text = {
                    Text(
                        if (payoutContract.canPay) {
                            "Premio detectado: ${formatMoney(currentTicket.totalPrize)}. Quieres pagarlo ahora e imprimir el voucher?"
                        } else {
                            payoutContract.message
                        },
                    )
                },
                confirmButton = {
                    if (payoutContract.canPay) {
                        TextButton(
                            onClick = {
                                showPayoutConfirm = false
                                actionMessage = markCurrentTicketPaidAndPrintReceipt()
                            },
                        ) {
                            Text("Pagar e imprimir")
                        }
                    } else {
                        TextButton(onClick = { showPayoutConfirm = false }) {
                            Text("Entendido")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPayoutConfirm = false }) {
                        Text("Cerrar")
                    }
                },
            )
        }

        if (showDuplicateMenu) {
            DuplicateLotteryPickerDialog(
                options = selectableDuplicateLotteries,
                selectedIds = selectedDuplicateLotteryIds,
                onToggle = { id ->
                    selectedDuplicateLotteryIds = if (id in selectedDuplicateLotteryIds) {
                        selectedDuplicateLotteryIds - id
                    } else {
                        selectedDuplicateLotteryIds + id
                    }
                },
                onDismiss = { showDuplicateMenu = false },
                onConfirm = {
                    val selected = selectedDuplicateLotteryIds.mapNotNull { duplicateLotteriesById[it] }
                    if (selected.isNotEmpty()) {
                        onDuplicateWithLotteries(currentTicket, selected)
                        showDuplicateMenu = false
                        actionMessage = "Ticket duplicado con loteria seleccionada"
                    } else {
                        actionMessage = "Selecciona una loteria abierta"
                    }
                },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = visual.sizes.screenPaddingH,
                end = visual.sizes.screenPaddingH,
                top = visual.sizes.screenPaddingV,
                bottom = visual.sizes.screenPaddingV + 12.dp,
            ) + innerPadding,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                CompactPanel(
                    alt = true,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = onBack,
                            shape = RoundedCornerShape(visual.sizes.panelRadius),
                            color = visual.colors.panelAlt,
                            border = BorderStroke(1.dp, visual.colors.border),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            IconButton(onClick = onBack, modifier = Modifier.size(visual.sizes.actionHeight)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = ticketViewState.title, style = MaterialTheme.typography.titleSmall, color = visual.colors.ink)
                            Text(
                                text = "${ticketViewState.serial} · ${mode.subtitlePrefix}",
                                style = MaterialTheme.typography.bodySmall,
                                color = visual.colors.muted,
                                maxLines = if (visual.windowMode in setOf(LotteryNetWindowMode.POS_TIGHT, LotteryNetWindowMode.POS)) 2 else 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                        CompactStatusBadge(ticketStatusLabel(currentTicket.status), tone = statusTone)
                    }
                    Spacer(modifier = Modifier.size(visual.sizes.panelContentGap))
                    BancaTicketIdentity(
                        bancaName = bancaName,
                        logoUri = bancaLogoUri,
                    )
                    SectionHeader(title = "Ticket oficial", meta = verificationCode)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TicketMetaCard(
                            modifier = Modifier.weight(1f),
                            label = "Serial",
                            value = ticketViewState.serial,
                            supporting = ticketViewState.statusLabel,
                            emphasized = true,
                        )
                        TicketMetaCard(
                            modifier = Modifier.weight(1f),
                            label = ticketViewState.primaryAmountLabel,
                            value = ticketViewState.totalLabel,
                            supporting = ticketViewState.primaryAmountSupporting,
                            emphasized = true,
                        )
                    }
                    if (lotteryBadges.isNotEmpty()) {
                        CompactAdaptiveGrid(
                            itemCount = lotteryBadges.size,
                            columns = lotteryBadgeGrid.columns,
                        ) { badgeIndex, itemModifier ->
                            val (name, logo, _) = lotteryBadges[badgeIndex]
                                TicketLotteryBadge(
                                    name = name,
                                    logoAssetPath = logo,
                                    modifier = itemModifier,
                                )
                        }
                    }
                    SectionHeader(title = "Acciones", meta = "Enviar / imprimir")
                    if (visibleActions.isNotEmpty()) {
                        CompactAdaptiveGrid(
                            itemCount = visibleActions.size,
                            columns = if (visual.windowMode in setOf(LotteryNetWindowMode.TABLET, LotteryNetWindowMode.WIDE)) 3 else 2,
                        ) { actionIndex, itemModifier ->
                            val action = visibleActions[actionIndex]
                            CompactActionButton(
                                label = if (actionBusy && action.label in setOf("WhatsApp", "Compartir", "Guardar")) {
                                    "Preparando"
                                } else {
                                    action.label
                                },
                                onClick = action.onClick,
                                modifier = itemModifier,
                                icon = action.icon,
                                tone = action.tone,
                                active = action.emphasized,
                                enabled = !actionBusy || action.label !in setOf("WhatsApp", "Compartir", "Guardar"),
                            )
                        }
                    }
                    val ticketNote = currentTicket.note?.takeIf { it.isNotBlank() }
                    if (actionMessage != null || ticketNote != null || payoutBlocked || currentTicket.totalPrize > 0.0) {
                        SectionHeader(title = "Estado y avisos", meta = currentTicket.serial ?: currentTicket.id)
                        actionMessage?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = visual.colors.muted)
                        }
                        ticketNote?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = visual.colors.warning, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        if (payoutBlocked) {
                            CompactStatusBadge("Pago bloqueado: ${formatMoney(cashierPayoutLimit)}", tone = MaterialTheme.colorScheme.error)
                        }
                        if (payoutContract.alreadyPaid) {
                            CompactStatusBadge("Ticket ya pagado", tone = MaterialTheme.colorScheme.primary)
                        }
                        if (currentTicket.totalPrize > 0.0) {
                            CompactStatusBadge("Premio detectado: ${formatMoney(currentTicket.totalPrize)}", tone = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item {
                OfficialTicketSnapshotCard(
                    ticket = currentTicket,
                    viewState = ticketViewState,
                    securityCode = verificationCode,
                )
            }
            item {
                CompactPanel(
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                ) {
                        SectionHeader(title = "Verificación y detalle", meta = verificationCode)
                    CompactKeyValueRow(
                        label = "Fecha / hora",
                        value = ticketViewState.createdAtLabel,
                        emphasized = true,
                    )
                    CompactKeyValueRow(
                        label = "Validez",
                        value = ticketViewState.drawValidLabel,
                        emphasized = true,
                    )
                    CompactKeyValueRow(
                        label = "Estado",
                        value = ticketViewState.statusLabel,
                        tone = statusTone,
                        emphasized = true,
                    )
                    CompactKeyValueRow(
                        label = "Banca",
                        value = bancaName,
                    )
                    CompactKeyValueRow(
                        label = "Loterías",
                        value = currentTicket.plays.mapNotNull { it.lotteryName }.distinct().joinToString(" · ").ifBlank { "Sin loterías" },
                    )
                    CompactKeyValueRow(
                        label = "Vendedor",
                        value = ticketViewState.operatorLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun DuplicateLotteryPickerDialog(
    options: List<DuplicateLotteryOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Duplicar ticket", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Elige manualmente una o varias loterias abiertas.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (options.isEmpty()) {
                    CompactEmptyState("No hay loterias abiertas en este momento.")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(options, key = { it.id }) { option ->
                            Surface(
                                onClick = { onToggle(option.id) },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (option.id in selectedIds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                ),
                                color = if (option.id in selectedIds) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    LotteryLogo(
                                        assetPath = option.logoAssetPath,
                                        fallback = option.name,
                                        modifier = Modifier.size(34.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = option.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        )
                                        Text(
                                            text = duplicateLotteryOptionSubtitle(option),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (option.adminGrace) {
                                                MaterialTheme.colorScheme.tertiary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            fontWeight = if (option.adminGrace) {
                                                androidx.compose.ui.text.font.FontWeight.Bold
                                            } else {
                                                androidx.compose.ui.text.font.FontWeight.Normal
                                            },
                                        )
                                    }
                                    Checkbox(
                                        checked = option.id in selectedIds,
                                        onCheckedChange = { onToggle(option.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedIds.isNotEmpty(),
            ) {
                Text("Duplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

private fun duplicateLotteryOptionSubtitle(option: DuplicateLotteryOption): String {
    val draw = option.drawTimeLabel.ifBlank { "--" }
    return if (option.adminGrace) {
        "Sorteo $draw - gracia admin"
    } else {
        "Sorteo $draw"
    }
}

private data class TicketQuickActionSpec(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tone: ActionTone,
    val emphasized: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun OfficialTicketSnapshotCard(
    ticket: TicketRecord,
    viewState: OfficialTicketViewState,
    securityCode: String,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(
        alt = true,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
    ) {
        SectionHeader(title = "Snapshot del ticket", meta = "Vista rápida")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactKeyValueRow(
                label = "Código",
                value = securityCode,
                modifier = Modifier.weight(1f),
                emphasized = true,
            )
            CompactKeyValueRow(
                label = "Vendedor",
                value = viewState.operatorLabel,
                modifier = Modifier.weight(1f),
                emphasized = true,
            )
        }
        viewState.lotteryGroups.forEach { group ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(visual.sizes.panelRadius),
                color = visual.colors.panel,
                border = BorderStroke(1.dp, visual.colors.border),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.lotteryName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = visual.colors.ink,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${group.playCount} jugadas",
                            style = MaterialTheme.typography.bodySmall,
                            color = visual.colors.muted,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = group.totalLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = visual.colors.gain,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }
            }
        }
        val winnerGroups = NativeBitmapExport.groupOfficialTicketWinnerDetails(ticket)
        if (winnerGroups.isNotEmpty()) {
            SectionHeader(title = "Premios del ticket", meta = NativeBitmapExport.winnerDetailsMeta(ticket))
            WinnerPrizeTotalBlock(ticket = ticket)
            winnerGroups.forEach { group ->
                WinningGroupBlock(group = group)
            }
        }
        SectionHeader(title = "Jugadas", meta = "${ticket.plays.size} total")
        viewState.lotteryGroups.forEach { group ->
            SnapshotPlayGroupBlock(group = group)
        }
    }
}

@Composable
private fun SnapshotPlayGroupBlock(group: OfficialTicketLotteryGroup) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = group.lotteryName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = visual.colors.ink,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = group.totalLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.gain,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                text = "${group.playCount} jugadas · Monto lotería",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            group.plays.forEach { play ->
                SnapshotPlayRow(play = play)
            }
        }
    }
}

@Composable
private fun SnapshotPlayRow(play: PlayItem) {
    val visual = rememberLotteryNetVisualSpec()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = officialTicketSnapshotPlayTypeLabel(play.playType),
            modifier = Modifier.weight(0.55f),
            style = MaterialTheme.typography.labelLarge,
            color = visual.colors.tickets,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = formatPlayDisplayNumber(play.number, play.playType),
            modifier = Modifier.weight(1.2f),
            style = MaterialTheme.typography.bodyMedium,
            color = visual.colors.ink,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Text(
            text = formatMoney(play.amount),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = visual.colors.gain,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun WinnerPrizeTotalBlock(ticket: TicketRecord) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total premio",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = NativeBitmapExport.winnerDetailsMeta(ticket),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatMoney(NativeBitmapExport.winnerPrizeTotalAmount(ticket)),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WinningGroupBlock(group: NativeBitmapExport.OfficialTicketWinnerGroup) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.lotteryName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = visual.colors.ink,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Ganador ${group.resultNumber.ifBlank { "-" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = visual.colors.muted,
                        maxLines = 1,
                    )
                }
                if (NativeBitmapExport.shouldShowWinnerGroupSubtotal(group)) {
                    Text(
                        text = formatMoney(group.totalPayout),
                        style = MaterialTheme.typography.titleSmall,
                        color = visual.colors.gain,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            group.details.forEach { detail ->
                WinningDetailRow(detail = detail)
            }
        }
    }
}

@Composable
private fun WinningDetailRow(detail: com.lotterynet.pro.core.model.WinningPlayDetail) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = visual.colors.panel,
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactStatusBadge(officialTicketSnapshotPlayTypeLabel(detail.playType), tone = visual.colors.tickets)
                Text(
                    text = formatPlayDisplayNumber(detail.playedNumber, detail.playType),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = visual.colors.ink,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = formatMoney(detail.payoutAmount),
                    style = MaterialTheme.typography.titleSmall,
                    color = visual.colors.gain,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                text = "Acierto ${winningHitLabel(detail.hitPosition)} · Apostado ${formatMoney(detail.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

private fun winningHitLabel(raw: String): String {
    return when (raw.trim().lowercase(Locale.US)) {
        "1" -> "primera"
        "2" -> "segunda"
        "3" -> "tercera"
        "1-2" -> "pale 1-2"
        "1-3" -> "pale 1-3"
        "2-3" -> "pale 2-3"
        "sp" -> "super pale"
        "straight" -> "straight"
        "back" -> "ultimo par"
        "" -> "ganadora"
        else -> raw
    }
}

@Composable
private fun BancaTicketIdentity(
    bancaName: String,
    logoUri: String,
) {
    val visual = rememberLotteryNetVisualSpec()
    val context = LocalContext.current
    val logoBitmap = remember(logoUri) {
        logoUri.takeIf { it.isNotBlank() }?.let { rawUri ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(rawUri))?.use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = visual.colors.tickets.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = "Logo de banca",
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(74.dp),
                )
                Text(
                    text = "TICKET OFICIAL",
                    style = MaterialTheme.typography.labelLarge,
                    color = visual.colors.tickets,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            } else {
                Text(
                    text = bancaName,
                    style = MaterialTheme.typography.titleLarge,
                    color = visual.colors.tickets,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = "Banca de lotería",
                    style = MaterialTheme.typography.labelMedium,
                    color = visual.colors.muted,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun TicketLotteryBadge(
    name: String,
    logoAssetPath: String?,
    modifier: Modifier = Modifier,
) {
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(visual.sizes.panelRadius),
        color = visual.colors.panelAlt,
        border = BorderStroke(1.dp, visual.colors.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LotteryLogo(
                assetPath = logoAssetPath,
                fallback = name,
                modifier = Modifier.size(28.dp),
                tintColor = visual.colors.panel,
            )
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = visual.colors.ink,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TicketMetaCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    supporting: String,
    emphasized: Boolean = false,
) {
    val visual = rememberLotteryNetVisualSpec()
    CompactPanel(modifier = modifier, alt = emphasized) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (emphasized) visual.colors.tickets else visual.colors.muted,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(modifier = Modifier.size(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = if (emphasized) visual.colors.tickets else visual.colors.ink,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Spacer(modifier = Modifier.size(1.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = visual.colors.muted,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
    }
}

private fun formatTicketDateTime(epochMs: Long): String {
    val format = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US)
    format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(Date(epochMs))
}

private fun formatTicketDrawDate(dayKey: String): String {
    val input = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }
    val date = runCatching { input.parse(dayKey) }.getOrNull() ?: return dayKey
    val output = SimpleDateFormat("EEEE dd/MM/yyyy", Locale.forLanguageTag("es-DO")).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }
    return output.format(date)
}

private fun resolveTicketDrawTimeLabel(ticket: TicketRecord): String? {
    val lotteries = StaticLotteryCatalogRepository().getAllLotteries()
    val times = ticket.plays.mapNotNull { play ->
        val lottery = lotteries.firstOrNull { it.id == play.lotteryId } ?: lotteries.firstOrNull {
            it.name.equals(play.lotteryName.orEmpty(), ignoreCase = true)
        }
        lottery?.baseDrawTime
    }.distinct()
    return when (times.size) {
        0 -> null
        1 -> times.first()
        else -> "varios sorteos"
    }
}

private fun ticketStatusLabel(status: String): String {
    if (com.lotterynet.pro.core.model.isPaidTicketStatus(status)) return "Pagado"
    return when (status.lowercase(Locale.getDefault())) {
        "winner" -> "Ganador"
        "voided", "invalid" -> "Anulado"
        else -> "Activo"
    }
}

internal fun buildTicketPayoutVoucherShareText(ticket: TicketRecord, bancaName: String): String {
    val serial = ticket.serial?.takeIf { it.isNotBlank() } ?: ticket.id
    val seller = ticket.sellerUser?.takeIf { it.isNotBlank() }
        ?: ticket.sellerId?.takeIf { it.isNotBlank() }
        ?: ticket.adminUser?.takeIf { it.isNotBlank() }
        ?: "Sin cajero"
    val prize = ticket.totalPrize.takeIf { it > 0.0 }
    return buildString {
        appendLine("Voucher de pago - ${bancaName.ifBlank { "LotteryNet" }}")
        appendLine("Ticket: $serial")
        appendLine("Estado: Pagado")
        appendLine("Cajero: $seller")
        appendLine("Premio: ${prize?.let(::formatMoney) ?: "Pendiente de confirmar"}")
    }.trim()
}

private fun formatMoney(amount: Double): String {
    return com.lotterynet.pro.core.format.formatWholeMoney(amount)
}
