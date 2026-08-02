package com.lotterynet.pro.core.sync

import android.content.Context
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.master.SupabaseMasterConfigRemoteStore
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.results.ResultsScraperOrchestrator
import com.lotterynet.pro.core.results.ResultsSupabaseStore
import com.lotterynet.pro.core.results.TicketPrizeReconciler
import com.lotterynet.pro.core.storage.LocalAdminLotteryConfigRepository
import com.lotterynet.pro.core.storage.LocalCashierPrizePayoutRepository
import com.lotterynet.pro.core.storage.LocalCashierSalesLimitRepository
import com.lotterynet.pro.core.storage.LocalPrizeConfigRepository
import com.lotterynet.pro.core.storage.LocalResultsRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.core.storage.LocalUsersRepository
import com.lotterynet.pro.core.storage.decodeAdminSystemModeConfig
import com.lotterynet.pro.core.storage.manualDisabledLotteriesRemoteKey
import com.lotterynet.pro.core.storage.systemModeRemoteKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

data class LotteryNetCatchUpResult(
    val ok: Boolean,
    val message: String,
    val reason: SyncReason = SyncReason.PERIODIC,
    val ticketsPulled: Int = 0,
    val resultDatesChecked: Int = 0,
    val prizeTicketsUpdated: Int = 0,
    val modules: List<SyncModuleResult> = emptyList(),
)

data class SyncModuleResult(
    val name: String,
    val reason: SyncReason = SyncReason.PERIODIC,
    val changed: Boolean,
    val remoteUpdatedAt: String? = null,
    val error: String? = null,
    val durationMs: Long = 0L,
)

class LotteryNetCatchUpCoordinator(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun catchUp(
        reason: SyncReason = SyncReason.PERIODIC,
        forceTickets: Boolean = false,
        forceResults: Boolean = false,
        forceFinance: Boolean = false,
        forceConfig: Boolean = false,
        forceSports: Boolean = false,
    ): LotteryNetCatchUpResult {
        if (!catchUpInFlight.compareAndSet(false, true)) {
            return LotteryNetCatchUpResult(
                ok = true,
                reason = reason,
                message = "Sincronizacion ya en curso.",
            )
        }
        return try {
            runCatchUp(
                reason = reason,
                forceTickets = forceTickets,
                forceResults = forceResults,
                forceFinance = forceFinance,
                forceConfig = forceConfig,
                forceSports = forceSports,
            )
        } finally {
            catchUpInFlight.set(false)
        }
    }

    private fun runCatchUp(
        reason: SyncReason,
        forceTickets: Boolean,
        forceResults: Boolean,
        forceFinance: Boolean,
        forceConfig: Boolean,
        forceSports: Boolean,
    ): LotteryNetCatchUpResult {
        val modules = mutableListOf<SyncModuleResult>()
        val sessionRepository = LocalSessionRepository(appContext)
        val session = sessionRepository.getActiveSession()
            ?: return LotteryNetCatchUpResult(ok = true, reason = reason, message = "Sin sesion activa.")
        val tokenProvider = SupabaseSessionTokenProvider(sessionRepository)
        if (tokenProvider.freshAccessToken().isNullOrBlank()) {
            return LotteryNetCatchUpResult(ok = false, reason = reason, message = "Token no disponible para sincronizar.")
        }

        val usersRepository = LocalUsersRepository(appContext)
        modules += measureSyncModule("users", reason) {
            NativeUsersBootstrapper(usersRepository).bootstrap(forceRemoteRefresh = forceConfig)
            false
        }
        modules += measureSyncModule("config", reason) {
            hydrateAdminConfiguration(session, usersRepository, tokenProvider)
            forceConfig
        }

        val ticketRemoteStore = NativeTicketRemoteStore(
            bearerTokenProvider = { tokenProvider.freshAccessToken() },
            bearerTokenRefresher = { tokenProvider.forceFreshAccessToken() },
        )
        val ticketCoordinator = NativeOperationalSyncCoordinator(
            ticketGateway = NativeTicketCloudSyncCoordinator(
                salesRepository = LocalSalesRepository(appContext),
                queueRepository = NativeTicketSyncQueueRepository(appContext),
                remoteStore = ticketRemoteStore,
            ),
            remoteStampStore = ticketRemoteStore,
        )
        var ticketState: NativeOperationalSyncState? = null
        modules += measureSyncModule("tickets", reason) {
            ticketState = ticketCoordinator.syncTicketsForSession(session, force = forceTickets)
            val state = ticketState ?: return@measureSyncModule false
            state.pulledCount > 0 || state.pushedCount > 0
        }

        val resultSummary = hydrateResultsAndReconcileTickets(session, tokenProvider, reason, forceResults)
        modules += resultSummary.modules
        modules += measureSyncModule("sports", reason) { forceSports }
        if (forceFinance && modules.none { it.name == "finance" && it.changed }) {
            modules += SyncModuleResult(name = "finance", reason = reason, changed = true, durationMs = 0L)
        }
        val allModules = modules.toList()
        val resolvedTicketState = ticketState
        return LotteryNetCatchUpResult(
            ok = (resolvedTicketState?.ok ?: false) && resultSummary.ok,
            reason = reason,
            message = listOf(resolvedTicketState?.message.orEmpty(), resultSummary.message)
                .filter { it.isNotBlank() }
                .joinToString(" "),
            ticketsPulled = resolvedTicketState?.pulledCount ?: 0,
            resultDatesChecked = resultSummary.resultDatesChecked,
            prizeTicketsUpdated = resultSummary.prizeTicketsUpdated,
            modules = allModules,
        )
    }

    private fun hydrateAdminConfiguration(
        session: ActiveSession,
        usersRepository: LocalUsersRepository,
        tokenProvider: SupabaseSessionTokenProvider,
    ) {
        val ownerKeys = resolveOperationalOwnerKeys(session)
        if (ownerKeys.isEmpty()) return

        val remoteStore = SupabaseMasterConfigRemoteStore(
            bearerTokenProvider = { tokenProvider.freshAccessToken() },
        )
        val adminLotteryRepository = LocalAdminLotteryConfigRepository(appContext)
        val cashierLimitSync = CashierLimitCloudSyncCoordinator(
            repository = LocalCashierSalesLimitRepository(appContext),
            remoteStore = remoteStore,
        )
        val cashierPrizeSync = CashierPrizePayoutCloudSyncCoordinator(
            repository = LocalCashierPrizePayoutRepository(appContext),
            remoteStore = remoteStore,
        )

        ownerKeys.firstNotNullOfOrNull { ownerKey ->
            runCatching {
                remoteStore.fetchValue(systemModeRemoteKey(ownerKey))
                    ?.toString()
                    ?.let(::decodeAdminSystemModeConfig)
            }.getOrNull()
        }?.let(adminLotteryRepository::saveSystemModeConfig)

        ownerKeys.firstNotNullOfOrNull { ownerKey ->
            runCatching {
                remoteStore.fetchValue(manualDisabledLotteriesRemoteKey(ownerKey))
                    ?.toString()
                    ?.let(adminLotteryRepository::cacheManualDisabledLotteryConfig)
            }.getOrNull()
        }

        ownerKeys.forEach { ownerKey ->
            runCatching { cashierLimitSync.pullOwner(ownerKey) }
            runCatching { cashierPrizeSync.pullOwner(ownerKey) }
        }

        runCatching { usersRepository.touchSession(session) }
    }

    private fun hydrateResultsAndReconcileTickets(
        session: ActiveSession,
        tokenProvider: SupabaseSessionTokenProvider,
        reason: SyncReason,
        forceResults: Boolean,
    ): LotteryNetCatchUpResult {
        val salesRepository = LocalSalesRepository(appContext)
        val cashierPrizePayoutRepository = LocalCashierPrizePayoutRepository(appContext)
        val ownerKey = resolveOperationalOwnerKey(session)
        val ticketReconciler = TicketPrizeReconciler(
            salesRepository = salesRepository,
            prizeRepository = LocalPrizeConfigRepository(appContext),
            prizeConfigResolver = { ticket ->
                cashierPrizePayoutRepository.resolveForTicket(
                    ownerId = ticket.adminId ?: ownerKey,
                    sellerUser = ticket.sellerUser,
                )
            },
            onTicketsUpdated = { tickets ->
                runCatching {
                    val coordinator = NativeOperationalSyncCoordinator(
                        ticketGateway = NativeTicketCloudSyncCoordinator(
                            salesRepository = salesRepository,
                            queueRepository = NativeTicketSyncQueueRepository(appContext),
                            remoteStore = NativeTicketRemoteStore(
                                bearerTokenProvider = { tokenProvider.freshAccessToken() },
                                bearerTokenRefresher = { tokenProvider.forceFreshAccessToken() },
                            ),
                        ),
                    )
                    resolveTicketSyncOwnerKeys(tickets).forEach { ownerKey ->
                        coordinator.flushOwner(ownerKey, session.banca)
                    }
                }
            },
        )
        val orchestrator = ResultsScraperOrchestrator(
            remoteStore = ResultsSupabaseStore(
                bearerTokenProvider = { tokenProvider.freshAccessToken() },
            ),
            localResultsRepository = LocalResultsRepository(appContext),
        )

        var datesChecked = 0
        var ticketsUpdated = 0
        val modules = mutableListOf<SyncModuleResult>()
        catchUpResultDates().forEach { dateKey ->
            var refreshResultsCount = 0
            val refreshResult = measureSyncModule("results:$dateKey", reason) {
                val refresh = orchestrator.refreshDate(dateKey, forceRemote = forceResults)
                refreshResultsCount = refresh.results.size
                datesChecked += 1
                val reconcile = ticketReconciler.reconcileTicketsForDate(dateKey, refresh.results)
                ticketsUpdated += reconcile.updated
                refresh.results.isNotEmpty() || reconcile.updated > 0
            }
            modules += refreshResult.copy(changed = refreshResult.changed || refreshResultsCount > 0)
        }
        modules += SyncModuleResult(
            name = "finance",
            reason = reason,
            changed = ticketsUpdated > 0,
            durationMs = 0L,
        )
        return LotteryNetCatchUpResult(
            ok = true,
            reason = reason,
            message = "Catch-up de resultados listo.",
            resultDatesChecked = datesChecked,
            prizeTicketsUpdated = ticketsUpdated,
            modules = modules,
        )
    }

    companion object {
        private val catchUpInFlight = AtomicBoolean(false)
    }
}

private fun measureSyncModule(
    name: String,
    reason: SyncReason,
    block: () -> Boolean,
): SyncModuleResult {
    val start = System.currentTimeMillis()
    return runCatching {
        SyncModuleResult(
            name = name,
            reason = reason,
            changed = block(),
            durationMs = System.currentTimeMillis() - start,
        )
    }.getOrElse { error ->
        SyncModuleResult(
            name = name,
            reason = reason,
            changed = false,
            error = error.message ?: error::class.java.simpleName,
            durationMs = System.currentTimeMillis() - start,
        )
    }
}

internal fun catchUpResultDates(): List<String> {
    return listOf(catchUpDateOffset(0), catchUpDateOffset(-1)).distinct()
}

private fun catchUpDateOffset(offsetDays: Int): String {
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Santo_Domingo"))
    calendar.time = Date()
    calendar.add(Calendar.DAY_OF_YEAR, offsetDays)
    return SimpleDateFormat("dd-MM-yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    }.format(calendar.time)
}
