package com.lotterynet.pro.core.finance

import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.DeletedTicketRef
import com.lotterynet.pro.core.model.RechargeRecord
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.operations.filterCashiersForSession
import com.lotterynet.pro.core.operations.sortCashierAccountsNatural
import com.lotterynet.pro.core.repository.UsersRepository
import com.lotterynet.pro.core.storage.LocalRechargeRepository
import com.lotterynet.pro.core.storage.LocalSalesRepository
import java.time.LocalDate
import java.util.Locale

internal fun resolveFinanceRange(
    preset: FinancePeriodPreset,
    anchorDayKey: String,
    fromDayKey: String? = null,
    toDayKey: String? = null,
): FinanceResolvedRange {
    val anchor = runCatching { LocalDate.parse(anchorDayKey) }.getOrElse { LocalDate.now() }
    return when (preset) {
        FinancePeriodPreset.DAY -> FinanceResolvedRange(
            preset = preset,
            anchorDayKey = anchor.toString(),
            fromDayKey = anchor.toString(),
            toDayKey = anchor.toString(),
            label = anchor.toString(),
        )
        FinancePeriodPreset.WEEK -> {
            val start = anchor.minusDays(6)
            val end = anchor
            FinanceResolvedRange(
                preset = preset,
                anchorDayKey = anchor.toString(),
                fromDayKey = start.toString(),
                toDayKey = end.toString(),
                label = "Semana ${start} a ${end}",
            )
        }
        FinancePeriodPreset.QUINCENA -> {
            val start = if (anchor.dayOfMonth <= 15) anchor.withDayOfMonth(1) else anchor.withDayOfMonth(16)
            val end = if (anchor.dayOfMonth <= 15) anchor.withDayOfMonth(15) else anchor.withDayOfMonth(anchor.lengthOfMonth())
            FinanceResolvedRange(
                preset = preset,
                anchorDayKey = anchor.toString(),
                fromDayKey = start.toString(),
                toDayKey = end.toString(),
                label = if (anchor.dayOfMonth <= 15) "Quincena 1 ${anchor.month.name.lowercase()}" else "Quincena 2 ${anchor.month.name.lowercase()}",
            )
        }
        FinancePeriodPreset.MONTH -> {
            val start = anchor.withDayOfMonth(1)
            val end = anchor.withDayOfMonth(anchor.lengthOfMonth())
            FinanceResolvedRange(
                preset = preset,
                anchorDayKey = anchor.toString(),
                fromDayKey = start.toString(),
                toDayKey = end.toString(),
                label = "Mes ${anchor.month.name.lowercase()} ${anchor.year}",
            )
        }
        FinancePeriodPreset.CALENDAR -> {
            val rawFrom = runCatching { LocalDate.parse(fromDayKey ?: anchor.toString()) }.getOrElse { anchor }
            val rawTo = runCatching { LocalDate.parse(toDayKey ?: anchor.toString()) }.getOrElse { anchor }
            val start = minOf(rawFrom, rawTo)
            val end = maxOf(rawFrom, rawTo)
            FinanceResolvedRange(
                preset = preset,
                anchorDayKey = anchor.toString(),
                fromDayKey = start.toString(),
                toDayKey = end.toString(),
                label = "${start} a ${end}",
            )
        }
    }
}

internal fun matchesBankScopeRecord(
    adminId: String?,
    adminUser: String?,
    bancaName: String?,
    recordAdminId: String?,
    recordAdminUser: String?,
    recordAdminBanca: String?,
): Boolean {
    if (adminId.isNullOrBlank() && adminUser.isNullOrBlank() && bancaName.isNullOrBlank()) return true
    if (!adminId.isNullOrBlank() && adminId.equals(recordAdminId, ignoreCase = true)) return true
    if (!adminUser.isNullOrBlank() && adminUser.equals(recordAdminUser, ignoreCase = true)) return true
    if (!bancaName.isNullOrBlank() && bancaName.equals(recordAdminBanca, ignoreCase = true)) return true
    return false
}

internal fun matchesFinanceActorTicket(
    ticket: TicketRecord,
    actorKey: String?,
    actorDisplay: String?,
    actorAccount: UserAccount? = null,
): Boolean {
    val actorTokens = buildActorTokens(actorKey, actorDisplay, actorAccount)
    if (actorTokens.isEmpty()) return false
    if (matchesAnyActorToken(ticket.sellerId, actorTokens) || matchesAnyActorToken(ticket.sellerUser, actorTokens)) {
        return true
    }
    val canFallbackToAdminOwner = ticket.role == UserRole.ADMIN ||
        (ticket.sellerId.isNullOrBlank() && ticket.sellerUser.isNullOrBlank())
    if (!canFallbackToAdminOwner) return false
    return matchesAnyActorToken(ticket.adminId, actorTokens) || matchesAnyActorToken(ticket.adminUser, actorTokens)
}

internal fun matchesFinanceActorRecharge(
    row: RechargeRecord,
    actorKey: String?,
    actorDisplay: String?,
    actorAccount: UserAccount? = null,
): Boolean {
    val actorTokens = buildActorTokens(actorKey, actorDisplay, actorAccount)
    if (actorTokens.isEmpty()) return false
    if (matchesAnyActorToken(row.userId, actorTokens) || matchesAnyActorToken(row.userName, actorTokens)) {
        return true
    }
    val canFallbackToAdminOwner = row.userId.isNullOrBlank() && row.userName.isNullOrBlank()
    if (!canFallbackToAdminOwner) return false
    return matchesAnyActorToken(row.adminId, actorTokens) || matchesAnyActorToken(row.adminUser, actorTokens)
}

internal fun matchesFinanceActorDeletedTicket(
    ref: DeletedTicketRef,
    actorKey: String?,
    actorDisplay: String?,
    actorAccount: UserAccount? = null,
): Boolean {
    val actorTokens = buildActorTokens(actorKey, actorDisplay, actorAccount)
    if (actorTokens.isEmpty()) return false
    if (matchesAnyActorToken(ref.sellerId, actorTokens) || matchesAnyActorToken(ref.sellerUser, actorTokens)) {
        return true
    }
    val canFallbackToAdminOwner = ref.role == UserRole.ADMIN ||
        (ref.sellerId.isNullOrBlank() && ref.sellerUser.isNullOrBlank())
    if (!canFallbackToAdminOwner) return false
    return matchesAnyActorToken(ref.adminId, actorTokens) || matchesAnyActorToken(ref.adminUser, actorTokens)
}

internal fun filterFinanceActorTickets(
    rows: List<TicketRecord>,
    actorKey: String?,
    actorDisplay: String?,
    actorAccount: UserAccount? = null,
): List<TicketRecord> {
    return rows.filter { ticket ->
        matchesFinanceActorTicket(ticket, actorKey, actorDisplay, actorAccount)
    }
}

internal fun filterFinanceActorRecharges(
    rows: List<RechargeRecord>,
    actorKey: String?,
    actorDisplay: String?,
    actorAccount: UserAccount? = null,
): List<RechargeRecord> {
    return rows.filter { row ->
        matchesFinanceActorRecharge(row, actorKey, actorDisplay, actorAccount)
    }
}

private fun buildActorTokens(
    actorKey: String?,
    actorDisplay: String?,
    actorAccount: UserAccount?,
): Set<String> {
    return buildSet {
        listOf(
            actorKey,
            actorDisplay,
            actorAccount?.id,
            actorAccount?.user,
            actorAccount?.displayName,
        ).forEach { value ->
            value?.trim()?.takeIf { it.isNotBlank() }?.let { add(it.lowercase(Locale.US)) }
        }
    }
}

private fun matchesAnyActorToken(
    value: String?,
    actorTokens: Set<String>,
): Boolean {
    return !value.isNullOrBlank() && actorTokens.contains(value.trim().lowercase(Locale.US))
}

class LocalFinanceRepository(
    private val salesRepository: LocalSalesRepository,
    private val rechargeRepository: LocalRechargeRepository,
    private val usersRepository: UsersRepository,
) {
    fun getAvailableDayKeys(): List<String> {
        return (salesRepository.getAvailableDayKeys() + rechargeRepository.getAvailableDayKeys())
            .distinct()
            .sorted()
    }

    fun getDaySummary(dayKey: String): FinanceSummary {
        return summarize(
            rows = salesRepository.getTicketsForDay(dayKey),
            rechargeRows = rechargeRepository.getRechargesForDay(dayKey),
            deletedRows = salesRepository.getDeletedTicketRefsForDay(dayKey),
        )
    }

    fun resolveScope(session: ActiveSession): FinanceScope {
        return when (session.role) {
            UserRole.MASTER -> FinanceScope(
                type = FinanceScopeType.BANK,
                actorDisplay = "Todas las bancas",
            )
            UserRole.ADMIN -> FinanceScope(
                type = FinanceScopeType.BANK,
                adminId = resolveSessionAdminAccount(session)?.id ?: session.adminId ?: session.userId,
                adminUser = resolveSessionAdminAccount(session)?.user ?: session.adminUser ?: session.username,
                bancaName = resolveSessionAdminAccount(session)?.banca ?: session.banca,
                actorDisplay = session.banca ?: session.username,
            )
            UserRole.SUPERVISOR -> {
                val assignedCashiers = filterCashiersForSession(session, usersRepository.getCashiers())
                FinanceScope(
                    type = FinanceScopeType.BANK,
                    adminId = session.adminId,
                    adminUser = session.adminUser,
                    bancaName = session.banca,
                    actorDisplay = "Supervisor ${session.username}",
                    supervisorId = session.userId,
                    supervisorUser = session.username,
                    allowedActorKeys = assignedCashiers.flatMapTo(mutableSetOf()) { cashier ->
                        listOfNotNull(
                            cashier.id.normalizedActorKey(),
                            cashier.user.normalizedActorKey(),
                            cashier.displayName.normalizedActorKey(),
                        )
                    },
                )
            }
            else -> FinanceScope(
                type = FinanceScopeType.ACTOR,
                adminId = session.adminId,
                adminUser = session.adminUser,
                bancaName = session.banca,
                actorKey = session.userId,
                actorDisplay = session.username,
            )
        }
    }

    fun resolveSupervisorScope(
        adminSession: ActiveSession,
        supervisorKey: String,
        supervisorDisplay: String,
    ): FinanceScope {
        val supervisor = usersRepository.findByIdOrUser(supervisorKey)
            ?: usersRepository.getSupervisors().firstOrNull { account ->
                account.id.equals(supervisorKey, ignoreCase = true) ||
                    account.user.equals(supervisorKey, ignoreCase = true) ||
                    account.displayName.equals(supervisorDisplay, ignoreCase = true)
            }
        val assignedCashiers = usersRepository.getCashiers().filter { cashier ->
            matchesFinanceCashierScope(
                cashier = cashier,
                scope = FinanceScope(
                    type = FinanceScopeType.BANK,
                    adminId = adminSession.userId,
                    adminUser = adminSession.username,
                    bancaName = adminSession.banca,
                ),
            ) && supervisor != null && (
                cashier.supervisorIds.any { it.equals(supervisor.id, ignoreCase = true) } ||
                    cashier.supervisorUsers.any { it.equals(supervisor.user, ignoreCase = true) }
                )
        }
        return FinanceScope(
            type = FinanceScopeType.BANK,
            adminId = adminSession.userId,
            adminUser = adminSession.username,
            bancaName = adminSession.banca,
            actorDisplay = "Supervisor ${supervisor?.displayName ?: supervisorDisplay}",
            supervisorId = supervisor?.id ?: supervisorKey,
            supervisorUser = supervisor?.user ?: supervisorDisplay,
            allowedActorKeys = assignedCashiers.flatMapTo(mutableSetOf()) { cashier ->
                listOfNotNull(
                    cashier.id.normalizedActorKey(),
                    cashier.user.normalizedActorKey(),
                    cashier.displayName.normalizedActorKey(),
                )
            },
        )
    }

    fun resolveRange(
        preset: FinancePeriodPreset,
        anchorDayKey: String,
        fromDayKey: String? = null,
        toDayKey: String? = null,
    ): FinanceResolvedRange {
        return resolveFinanceRange(preset, anchorDayKey, fromDayKey, toDayKey)
    }

    fun getScopedDaySummary(
        dayKey: String,
        scope: FinanceScope,
    ): FinanceSummary {
        return applySupervisorCommission(
            summary = summarize(
            rows = getScopedTicketsForDay(dayKey, scope),
            rechargeRows = getScopedRechargesForDay(dayKey, scope),
            deletedRows = getScopedDeletedTicketsForDay(dayKey, scope),
            ),
            supervisorCommission = calculateSupervisorCommissionForDays(listOf(dayKey), scope),
        )
    }

    fun getScopedPeriodReport(
        scope: FinanceScope,
        preset: FinancePeriodPreset,
        anchorDayKey: String,
        fromDayKey: String? = null,
        toDayKey: String? = null,
    ): FinancePeriodReport {
        val range = resolveRange(
            preset = preset,
            anchorDayKey = anchorDayKey,
            fromDayKey = fromDayKey,
            toDayKey = toDayKey,
        )
        val days = buildDayRange(range.fromDayKey, range.toDayKey)
        val rows = days.map { dayKey ->
            FinancePeriodRow(
                dayKey = dayKey,
                summary = getScopedDaySummary(dayKey, scope),
            )
        }.filter { row ->
            row.summary.ticketsCount > 0 ||
                row.summary.recargas > 0.0 ||
                row.summary.ventas > 0.0 ||
                row.summary.fueraDeFinanzaCount > 0 ||
                row.summary.borradosCount > 0
        }
        val aggregate = applySupervisorCommission(
            summary = summarize(
            rows = days.flatMap { dayKey -> getScopedTicketsForDay(dayKey, scope) },
            rechargeRows = days.flatMap { dayKey -> getScopedRechargesForDay(dayKey, scope) },
            deletedRows = days.flatMap { dayKey -> getScopedDeletedTicketsForDay(dayKey, scope) },
            ),
            supervisorCommission = calculateSupervisorCommissionForDays(days, scope),
        )
        return FinancePeriodReport(
            scope = scope,
            preset = preset,
            range = range,
            fromDayKey = range.fromDayKey,
            toDayKey = range.toDayKey,
            summary = aggregate,
            rows = rows.sortedByDescending { it.dayKey },
            actorRows = buildScopedActorRows(days, scope),
        )
    }

    fun getActorSummary(
        dayKey: String,
        actorKey: String,
        actorDisplay: String = actorKey,
    ): CashierFinanceSummary {
        val actorAccount = usersRepository.findByIdOrUser(actorKey)
        val rows = getTicketsForFinanceActor(dayKey, actorKey, actorDisplay, actorAccount)
        return CashierFinanceSummary(
            actorKey = actorKey,
            actorDisplay = actorDisplay,
            summary = summarize(
                rows = rows,
                rechargeRows = getRechargesForFinanceActor(dayKey, actorKey, actorDisplay, actorAccount),
                deletedRows = salesRepository.getDeletedTicketRefsForDay(dayKey).filter { ref ->
                    matchesFinanceActorDeletedTicket(ref, actorKey, actorDisplay, actorAccount)
                },
            ),
        )
    }

    fun getTurnoSummary(
        dayKey: String,
        session: ActiveSession,
        turnoStartEpochMs: Long,
    ): TurnoFinanceSummary {
        val actorKey = resolveActorKey(session)
        val actorDisplay = resolveActorDisplay(session)
        val ticketRows = salesRepository
            .getTicketsForDay(dayKey)
            .let { rows -> filterFinanceActorTickets(rows, actorKey, actorDisplay, usersRepository.findByIdOrUser(actorKey)) }
            .filter { it.createdAtEpochMs >= turnoStartEpochMs }
        val rechargeRows = rechargeRepository
            .getRechargesForDay(dayKey)
            .let { rows -> filterFinanceActorRecharges(rows, actorKey, actorDisplay, usersRepository.findByIdOrUser(actorKey)) }
            .filter { it.createdAtEpochMs >= turnoStartEpochMs }
        return TurnoFinanceSummary(
            actorKey = actorKey,
            actorDisplay = actorDisplay,
            startedAtEpochMs = turnoStartEpochMs,
            summary = summarize(
                rows = ticketRows,
                rechargeRows = rechargeRows,
                deletedRows = salesRepository.getDeletedTicketRefsForDay(dayKey).filter { ref ->
                    ref.deletedAtEpochMs >= turnoStartEpochMs &&
                        matchesFinanceActorDeletedTicket(ref, actorKey, actorDisplay, usersRepository.findByIdOrUser(actorKey))
                },
            ),
        )
    }

    fun getPeriodReport(
        fromDayKey: String,
        toDayKey: String,
        actorKey: String? = null,
    ): FinancePeriodReport {
        val scope = if (actorKey.isNullOrBlank()) {
            FinanceScope(type = FinanceScopeType.BANK, actorDisplay = "Global")
        } else {
            FinanceScope(type = FinanceScopeType.ACTOR, actorKey = actorKey, actorDisplay = actorKey)
        }
        return getScopedPeriodReport(
            scope = scope,
            preset = FinancePeriodPreset.CALENDAR,
            anchorDayKey = toDayKey,
            fromDayKey = fromDayKey,
            toDayKey = toDayKey,
        )
    }

    fun summarize(
        rows: List<TicketRecord>,
        rechargeRows: List<RechargeRecord> = emptyList(),
        deletedRows: List<DeletedTicketRef> = emptyList(),
    ): FinanceSummary {
        return summarizeFinanceRows(
            rows = rows,
            rechargeRows = rechargeRows,
            deletedRows = deletedRows,
            commissionRateResolver = ::resolveCommissionRate,
        )
    }

    private fun resolveCommissionRate(ticket: TicketRecord): Double {
        val sellerRole = ticket.role
        val cashierKey = ticket.sellerId ?: ticket.sellerUser
        if (!cashierKey.isNullOrBlank() && (sellerRole == UserRole.CASHIER || !ticket.sellerId.isNullOrBlank())) {
            usersRepository.findByIdOrUser(cashierKey)?.commissionRate?.let { return normalizeCommissionRate(it) }
        }
        val adminKey = ticket.adminId ?: ticket.adminUser
        if (!adminKey.isNullOrBlank()) {
            usersRepository.findByIdOrUser(adminKey)?.commissionRate?.let { return normalizeCommissionRate(it) }
        }
        if (!cashierKey.isNullOrBlank()) {
            usersRepository.findByIdOrUser(cashierKey)?.commissionRate?.let { return normalizeCommissionRate(it) }
        }
        return 0.05
    }

    private fun normalizeCommissionRate(value: Double): Double {
        var normalized = value
        if (normalized > 1.0 && normalized <= 100.0) normalized /= 100.0
        if (normalized < 0.0) normalized = 0.0
        if (normalized > 1.0) normalized = 1.0
        return normalized
    }

    private fun resolveActorKey(session: ActiveSession): String {
        return when (session.role) {
            UserRole.ADMIN -> session.adminId ?: session.userId
            else -> session.userId
        }
    }

    private fun resolveActorDisplay(session: ActiveSession): String {
        return when (session.role) {
            UserRole.ADMIN -> session.adminUser ?: session.username
            else -> session.username
        }
    }

    private fun getScopedTicketsForDay(
        dayKey: String,
        scope: FinanceScope,
    ): List<TicketRecord> {
        return when (scope.type) {
            FinanceScopeType.ACTOR -> {
                val actorKey = scope.actorKey
                if (actorKey.isNullOrBlank()) {
                    emptyList()
                } else {
                    val actorAccount = usersRepository.findByIdOrUser(actorKey)
                    salesRepository.getTicketsForDay(dayKey).filter { ticket ->
                        matchesFinanceActorTicket(
                            ticket = ticket,
                            actorKey = actorKey,
                            actorDisplay = scope.actorDisplay,
                            actorAccount = actorAccount,
                        )
                    }
                }
            }
            FinanceScopeType.BANK -> salesRepository.getTicketsForDay(dayKey).filter { ticket ->
                val bankMatches = matchesBankScope(
                    adminId = scope.adminId,
                    adminUser = scope.adminUser,
                    bancaName = scope.bancaName,
                    recordAdminId = ticket.adminId,
                    recordAdminUser = ticket.adminUser,
                ) || matchesTicketCashierBankScope(ticket, scope)
                bankMatches && matchesAllowedActorKeys(ticket.sellerId, ticket.sellerUser, scope)
            }
        }
    }

    private fun getScopedRechargesForDay(
        dayKey: String,
        scope: FinanceScope,
    ): List<RechargeRecord> {
        return when (scope.type) {
            FinanceScopeType.ACTOR -> {
                val actorKey = scope.actorKey
                if (actorKey.isNullOrBlank()) {
                    emptyList()
                } else {
                    val actorAccount = usersRepository.findByIdOrUser(actorKey)
                    rechargeRepository.getRechargesForDay(dayKey).filter { row ->
                        matchesFinanceActorRecharge(
                            row = row,
                            actorKey = actorKey,
                            actorDisplay = scope.actorDisplay,
                            actorAccount = actorAccount,
                        )
                    }
                }
            }
            FinanceScopeType.BANK -> rechargeRepository.getRechargesForDay(dayKey).filter { row ->
                val bankMatches = matchesBankScope(
                    adminId = scope.adminId,
                    adminUser = scope.adminUser,
                    bancaName = scope.bancaName,
                    recordAdminId = row.adminId,
                    recordAdminUser = row.adminUser,
                )
                bankMatches && matchesAllowedActorKeys(row.userId, row.userName, scope)
            }
        }
    }

    private fun matchesBankScope(
        adminId: String?,
        adminUser: String?,
        bancaName: String?,
        recordAdminId: String?,
        recordAdminUser: String?,
    ): Boolean {
        val adminAccount = when {
            !recordAdminId.isNullOrBlank() -> usersRepository.findByIdOrUser(recordAdminId)
            !recordAdminUser.isNullOrBlank() -> usersRepository.findByIdOrUser(recordAdminUser)
            else -> null
        }
        return matchesBankScopeRecord(
            adminId = adminId,
            adminUser = adminUser,
            bancaName = bancaName,
            recordAdminId = recordAdminId,
            recordAdminUser = recordAdminUser,
            recordAdminBanca = adminAccount?.banca,
        )
    }

    private fun buildScopedActorRows(
        days: List<String>,
        scope: FinanceScope,
    ): List<FinanceActorPeriodRow> {
        if (scope.type != FinanceScopeType.BANK) return emptyList()
        val cashiers = usersRepository.getCashiers()
            .filter { cashier -> matchesFinanceCashierScope(cashier, scope) }
            .let(::sortCashierAccountsNatural)
        return cashiers.map { cashier ->
            FinanceActorPeriodRow(
                actorKey = cashier.id,
                actorDisplay = cashier.displayName ?: cashier.user,
                summary = summarize(
                    rows = days.flatMap { dayKey -> getTicketsForFinanceActor(dayKey, cashier.id, cashier.displayName ?: cashier.user, cashier) },
                    rechargeRows = days.flatMap { dayKey -> getRechargesForFinanceActor(dayKey, cashier.id, cashier.displayName ?: cashier.user, cashier) },
                    deletedRows = days.flatMap { dayKey ->
                        salesRepository.getDeletedTicketRefsForDay(dayKey).filter { ref ->
                            matchesFinanceActorDeletedTicket(ref, cashier.id, cashier.displayName ?: cashier.user, cashier)
                        }
                    },
                ),
            )
        }.filter { row ->
            row.summary.ticketsCount > 0 ||
                row.summary.recargas > 0.0 ||
                row.summary.ventas > 0.0 ||
                row.summary.fueraDeFinanzaCount > 0
        }
    }

    private fun calculateSupervisorCommissionForDays(
        days: List<String>,
        scope: FinanceScope,
    ): Double {
        if (scope.type != FinanceScopeType.BANK) return 0.0
        val supervisors = resolveSupervisorsForScope(scope)
        if (supervisors.isEmpty()) return 0.0
        val cashiers = usersRepository.getCashiers()
            .filter { cashier -> matchesFinanceCashierScope(cashier, scope) }
        return supervisors.sumOf { supervisor ->
            val rate = supervisor.commissionRate?.let(::normalizeCommissionRate) ?: return@sumOf 0.0
            val assignedCashiers = cashiers.filter { cashier ->
                cashier.supervisorIds.any { it.equals(supervisor.id, ignoreCase = true) } ||
                    cashier.supervisorUsers.any { it.equals(supervisor.user, ignoreCase = true) }
            }
            if (assignedCashiers.isEmpty()) return@sumOf 0.0
            val summary = summarize(
                rows = assignedCashiers.flatMap { cashier ->
                    days.flatMap { dayKey -> getTicketsForFinanceActor(dayKey, cashier.id, cashier.displayName ?: cashier.user, cashier) }
                },
                rechargeRows = assignedCashiers.flatMap { cashier ->
                    days.flatMap { dayKey -> getRechargesForFinanceActor(dayKey, cashier.id, cashier.displayName ?: cashier.user, cashier) }
                },
            )
            calculateSupervisorCommission(summary, rate)
        }
    }

    private fun resolveSupervisorsForScope(scope: FinanceScope): List<UserAccount> {
        val supervisors = usersRepository.getSupervisors()
        if (!scope.supervisorId.isNullOrBlank() || !scope.supervisorUser.isNullOrBlank()) {
            return supervisors.filter { supervisor ->
                (!scope.supervisorId.isNullOrBlank() && supervisor.id.equals(scope.supervisorId, ignoreCase = true)) ||
                    (!scope.supervisorUser.isNullOrBlank() && supervisor.user.equals(scope.supervisorUser, ignoreCase = true))
            }
        }
        return supervisors.filter { supervisor ->
            (!scope.adminId.isNullOrBlank() && supervisor.adminId.equals(scope.adminId, ignoreCase = true)) ||
                (!scope.adminUser.isNullOrBlank() && supervisor.adminUser.equals(scope.adminUser, ignoreCase = true)) ||
                (!scope.bancaName.isNullOrBlank() && supervisor.banca.equals(scope.bancaName, ignoreCase = true))
        }
    }

    private fun matchesTicketCashierBankScope(
        ticket: TicketRecord,
        scope: FinanceScope,
    ): Boolean {
        if (scope.adminId.isNullOrBlank() && scope.adminUser.isNullOrBlank() && scope.bancaName.isNullOrBlank()) return true
        val sellerKey = ticket.sellerId ?: ticket.sellerUser ?: return false
        val cashier = usersRepository.findByIdOrUser(sellerKey) ?: return false
        return matchesFinanceCashierScope(cashier, scope)
    }

    private fun resolveSessionAdminAccount(session: ActiveSession): UserAccount? {
        return listOf(session.adminId, session.userId, session.adminUser, session.username)
            .firstNotNullOfOrNull { key ->
                key?.let(usersRepository::findByIdOrUser)?.takeIf { it.role == UserRole.ADMIN }
            }
    }

    private fun getTicketsForFinanceActor(
        dayKey: String,
        actorKey: String,
        actorDisplay: String,
        actorAccount: UserAccount?,
    ): List<TicketRecord> {
        return filterFinanceActorTickets(salesRepository.getTicketsForDay(dayKey), actorKey, actorDisplay, actorAccount)
    }

    private fun getRechargesForFinanceActor(
        dayKey: String,
        actorKey: String,
        actorDisplay: String,
        actorAccount: UserAccount?,
    ): List<RechargeRecord> {
        return filterFinanceActorRecharges(rechargeRepository.getRechargesForDay(dayKey), actorKey, actorDisplay, actorAccount)
    }

    private fun getScopedDeletedTicketsForDay(dayKey: String, scope: FinanceScope): List<DeletedTicketRef> {
        val refs = salesRepository.getDeletedTicketRefsForDay(dayKey)
        return when (scope.type) {
            FinanceScopeType.BANK -> refs.filter { ref -> matchesDeletedTicketBankScope(ref, scope) }
            FinanceScopeType.ACTOR -> refs.filter { ref ->
                matchesFinanceActorDeletedTicket(
                    ref = ref,
                    actorKey = scope.actorKey,
                    actorDisplay = scope.actorDisplay,
                    actorAccount = scope.actorKey?.let(usersRepository::findByIdOrUser),
                )
            }
        }
    }

    private fun matchesDeletedTicketBankScope(ref: DeletedTicketRef, scope: FinanceScope): Boolean {
        if (scope.adminId.isNullOrBlank() && scope.adminUser.isNullOrBlank() && scope.bancaName.isNullOrBlank()) return true
        val sellerKey = ref.sellerId ?: ref.sellerUser
        val cashier = sellerKey?.let(usersRepository::findByIdOrUser)
        if (cashier != null) return matchesFinanceCashierScope(cashier, scope)
        if (!matchesAllowedActorKeys(ref.sellerId, ref.sellerUser, scope)) return false
        return (!scope.adminId.isNullOrBlank() && scope.adminId.equals(ref.adminId, ignoreCase = true)) ||
            (!scope.adminUser.isNullOrBlank() && scope.adminUser.equals(ref.adminUser, ignoreCase = true))
    }

    private fun buildDayRange(fromDayKey: String, toDayKey: String): List<String> {
        return runCatching {
            val start = LocalDate.parse(fromDayKey)
            val end = LocalDate.parse(toDayKey)
            val first = if (start <= end) start else end
            val last = if (start <= end) end else start
            generateSequence(first) { current ->
                current.plusDays(1).takeIf { !it.isAfter(last) }
            }.map { it.toString() }.toList()
        }.getOrElse { listOf(fromDayKey) }
    }
}

internal fun matchesFinanceCashierScope(
    cashier: UserAccount,
    scope: FinanceScope,
): Boolean {
    if (scope.allowedActorKeys.isNotEmpty()) {
        val keys = listOf(cashier.id, cashier.user, cashier.displayName)
            .mapNotNull { it.normalizedActorKey() }
            .toSet()
        if (keys.none { it in scope.allowedActorKeys }) return false
    }
    if (scope.adminId.isNullOrBlank() && scope.adminUser.isNullOrBlank() && scope.bancaName.isNullOrBlank()) {
        return true
    }
    return (!scope.adminId.isNullOrBlank() && scope.adminId.equals(cashier.adminId, ignoreCase = true)) ||
        (!scope.adminUser.isNullOrBlank() && scope.adminUser.equals(cashier.adminUser, ignoreCase = true)) ||
        (!scope.bancaName.isNullOrBlank() && scope.bancaName.equals(cashier.banca, ignoreCase = true))
}

private fun matchesAllowedActorKeys(
    id: String?,
    user: String?,
    scope: FinanceScope,
): Boolean {
    if (scope.allowedActorKeys.isEmpty()) return true
    return listOf(id, user).mapNotNull { it.normalizedActorKey() }.any { it in scope.allowedActorKeys }
}

private fun String?.normalizedActorKey(): String? {
    return this?.trim()?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
}

internal fun summarizeFinanceRows(
    rows: List<TicketRecord>,
    rechargeRows: List<RechargeRecord> = emptyList(),
    deletedRows: List<DeletedTicketRef> = emptyList(),
    commissionRateResolver: (TicketRecord) -> Double,
): FinanceSummary {
    val nonVoidedRows = rows.filterNot(::isFinanceVoidStatus)
    val voidedRows = rows.filter(::isFinanceCancelledStatus)
    val invalidRows = rows.filter(::isFinanceInvalidStatus)
    val paidRows = nonVoidedRows.filter(::isFinancePaidStatus)
    val winnerRows = nonVoidedRows.filter(::isFinancePendingWinner)
    val ventas = nonVoidedRows.sumOf { it.total }
    val ticketsCount = nonVoidedRows.size
    val anuladosMonto = voidedRows.sumOf { it.total }
    val invalidosMonto = invalidRows.sumOf { it.total }
    val borradosMonto = deletedRows.sumOf { it.total }
    val premiosPagados = paidRows.sumOf(::resolveFinancePrizeAmount)
    val premiosPendientes = winnerRows.sumOf(::resolveFinancePrizeAmount)
    val recargas = rechargeRows.sumOf { it.amount }
    val comision = nonVoidedRows.sumOf { ticket -> ticket.total * commissionRateResolver(ticket) }
    val cajaDisponible = ventas + recargas - premiosPagados - premiosPendientes - comision
    val avgTicket = if (ticketsCount > 0) ventas / ticketsCount else 0.0

    return FinanceSummary(
        ventas = ventas,
        ticketsCount = ticketsCount,
        activos = nonVoidedRows.count { it.status.equals("active", true) },
        ganadores = winnerRows.size,
        pagados = paidRows.size,
        anuladosCount = voidedRows.size,
        anuladosMonto = anuladosMonto,
        invalidosCount = invalidRows.size,
        invalidosMonto = invalidosMonto,
        borradosCount = deletedRows.size,
        borradosMonto = borradosMonto,
        fueraDeFinanzaCount = voidedRows.size + invalidRows.size + deletedRows.size,
        fueraDeFinanzaMonto = anuladosMonto + invalidosMonto + borradosMonto,
        premiosPagados = premiosPagados,
        premiosPendientes = premiosPendientes,
        recargas = recargas,
        comision = comision,
        supervisorComision = 0.0,
        cajaDisponible = cajaDisponible,
        avgTicket = avgTicket,
        alertas = buildFinanceAlerts(
            premiosPendientes = premiosPendientes,
            cajaDisponible = cajaDisponible,
            fueraDeFinanzaMonto = anuladosMonto + invalidosMonto + borradosMonto,
            ventas = ventas,
            ticketsCount = ticketsCount,
            avgTicket = avgTicket,
        ),
    )
}

internal fun calculateSupervisorCommission(summary: FinanceSummary, commissionRate: Double?): Double {
    val rate = commissionRate ?: return 0.0
    val benefit = summary.ventas + summary.recargas - summary.comision - summary.premiosPagados - summary.premiosPendientes
    return benefit.coerceAtLeast(0.0) * rate
}

private fun applySupervisorCommission(
    summary: FinanceSummary,
    supervisorCommission: Double,
): FinanceSummary {
    if (supervisorCommission <= 0.0) return summary
    return summary.copy(
        supervisorComision = supervisorCommission,
        cajaDisponible = summary.cajaDisponible - supervisorCommission,
    )
}

private fun resolveFinancePrizeAmount(ticket: TicketRecord): Double {
    return normalizeLegacyPrizeAmount(ticket.totalPrize, ticket.total).coerceAtLeast(0.0)
}

private fun buildFinanceAlerts(
    premiosPendientes: Double,
    cajaDisponible: Double,
    fueraDeFinanzaMonto: Double,
    ventas: Double,
    ticketsCount: Int,
    avgTicket: Double,
): List<FinanceAlert> {
    val alerts = mutableListOf<FinanceAlert>()
    if (premiosPendientes > 0 && cajaDisponible < premiosPendientes) {
        alerts += FinanceAlert(
            label = "Riesgo",
            text = "Caja disponible no cubre premios pendientes.",
            tone = FinanceAlertTone.DANGER,
        )
    }
    if (fueraDeFinanzaMonto > 0 && ventas > 0 && (fueraDeFinanzaMonto / ventas) >= 0.2) {
        alerts += FinanceAlert(
            label = "Revision",
            text = "Tickets fuera de finanza altos para el periodo.",
            tone = FinanceAlertTone.WARNING,
        )
    }
    if (ticketsCount > 0 && avgTicket < 50.0) {
        alerts += FinanceAlert(
            label = "Ticket promedio",
            text = "El promedio vendido esta por debajo de $ 50.",
            tone = FinanceAlertTone.NOTICE,
        )
    }
    return alerts
}

internal fun isFinanceVoidStatus(ticket: TicketRecord): Boolean {
    return isFinanceCancelledStatus(ticket) || isFinanceInvalidStatus(ticket)
}

internal fun isFinancePendingWinner(ticket: TicketRecord): Boolean {
    return !isFinanceVoidStatus(ticket) &&
        !isFinancePaidStatus(ticket) &&
        (isFinanceWinnerStatus(ticket) || ticket.totalPrize > 0.0)
}

internal fun isFinanceCancelledStatus(ticket: TicketRecord): Boolean {
    val status = ticket.status.trim().lowercase(Locale.US)
    return status == "voided" ||
        status == "void" ||
        status == "nulled" ||
        status == "anulado" ||
        status == "annulled" ||
        status == "cancelled" ||
        status == "canceled" ||
        status == "cancelado" ||
        status == "deleted" ||
        status == "borrado" ||
        status == "removed"
}

internal fun isFinanceInvalidStatus(ticket: TicketRecord): Boolean {
    val status = ticket.status.trim().lowercase(Locale.US)
    return status == "invalid" || status == "invalido" || status == "inválido"
}

internal fun isFinancePaidStatus(ticket: TicketRecord): Boolean {
    val status = ticket.status.trim().lowercase(Locale.US)
    return status == "paid" ||
        status == "pagado" ||
        status == "paid_out" ||
        status == "payout" ||
        status == "cobrado" ||
        status == "premio_pagado"
}

internal fun isFinanceWinnerStatus(ticket: TicketRecord): Boolean {
    val status = ticket.status.trim().lowercase(Locale.US)
    return status == "winner" ||
        status == "winning" ||
        status == "ganador" ||
        status == "ganado" ||
        status == "pending_winner" ||
        status == "premiado"
}

internal fun normalizeLegacyPrizeAmount(prizeAmount: Double, ticketTotal: Double): Double {
    if (prizeAmount <= 0.0 || ticketTotal <= 0.0) return prizeAmount
    val multiplier = prizeAmount / ticketTotal
    return if (multiplier in 99_000.0..101_000.0) {
        prizeAmount / 100.0
    } else {
        prizeAmount
    }
}
