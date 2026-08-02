package com.lotterynet.pro.core.sales

import android.content.Context
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.LotteryTerritory
import com.lotterynet.pro.core.model.SaleResolvedPlay
import com.lotterynet.pro.core.model.SaleStagedRow
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.repository.SalesRepository
import com.lotterynet.pro.core.storage.SalesStorageKeys
import com.lotterynet.pro.core.format.formatWholeAmount
import org.json.JSONObject
import java.util.Locale

class SaleExposureEngine(
    private val context: Context,
    private val salesRepository: SalesRepository,
) {
    fun buildDayKey(nowUtcMs: Long, territory: LotteryTerritory): String {
        val zone = when (territory) {
            LotteryTerritory.USA -> "America/New_York"
            LotteryTerritory.RD -> "America/Santo_Domingo"
        }
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone(zone)
        return format.format(java.util.Date(nowUtcMs))
    }

    fun getActorSoldTodayTotal(dayKey: String, session: ActiveSession?): Double {
        val actorKey = resolveActorKey(session)
        if (actorKey.isBlank()) return 0.0
        return calculateActorDailySaleTotal(salesRepository.getTicketsForActor(dayKey, actorKey), actorKey)
    }

    fun resolveLimitError(
        session: ActiveSession?,
        dayKey: String,
        play: SaleResolvedPlay,
        amount: Double,
        lotteries: List<LotteryCatalogItem>,
        stagedRows: List<SaleStagedRow>,
    ): String? {
        val cashierLimits = getCashierLimits(session)
        val poolLimits = getPoolLimits(session)
        val actorDaySold = getActorSoldTodayTotal(dayKey, session)
        val stagedTotal = stagedRows.sumOf { it.amount }
        if (session?.role == UserRole.CASHIER || session?.role == UserRole.ADMIN) {
            if (cashierLimits.daySale > 0.0 && actorDaySold + stagedTotal + amount > cashierLimits.daySale) {
                return "Tope diario del cajero alcanzado"
            }
            val limitErrors = mutableListOf<String>()
            for (lottery in lotteries) {
                val bucket = resolveSaleExposureLimitBucket(play.playType, play.normalizedNumber, lottery.id)
                val pending = calculateGlobalStagedExposure(stagedRows, bucket)
                val ownLimit = cashierLimits.typeLimitFor(bucket.playType)
                if (ownLimit > 0.0) {
                    val soldOwn = getGlobalLimitExposure(
                        dayKey = dayKey,
                        ownerKey = resolveExposureOwnerKey(session),
                        ownerKeys = resolveExposureOwnerKeys(session),
                        bucket = bucket,
                        cashierKeys = resolveExposureCashierKeys(session),
                        cashierPoolOnly = false,
                    )
                    if (soldOwn + pending + amount > ownLimit) {
                        limitErrors += buildLimitViolationMessage(
                            scope = "Límite",
                            lotteryName = lottery.name,
                            bucket = bucket,
                            limit = ownLimit,
                            sold = soldOwn,
                            pending = pending,
                        )
                    }
                }
                if (session?.role == UserRole.CASHIER) {
                    val poolLimit = poolLimits.typeLimitFor(bucket.playType)
                    if (poolLimit > 0.0) {
                        val soldPool = getGlobalLimitExposure(
                            dayKey = dayKey,
                            ownerKey = resolveExposureOwnerKey(session),
                            ownerKeys = resolveExposureOwnerKeys(session),
                            bucket = bucket,
                            cashierKeys = emptySet(),
                            cashierPoolOnly = true,
                        )
                        if (soldPool + pending + amount > poolLimit) {
                            limitErrors += buildLimitViolationMessage(
                                scope = "Límite de pool",
                                lotteryName = lottery.name,
                                bucket = bucket,
                                limit = poolLimit,
                                sold = soldPool,
                                pending = pending,
                            )
                        }
                    }
                }
            }
            return limitErrors.distinct().joinToString(" · ").takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun buildLimitViolationMessage(
        scope: String,
        lotteryName: String,
        bucket: SaleExposureLimitBucket,
        limit: Double,
        sold: Double,
        pending: Double,
    ): String {
        val available = (limit - sold - pending).coerceAtLeast(0.0)
        val location = "$lotteryName · ${bucket.displayLabel()}"
        return if (available <= 0.0) {
            "$scope agotado · $location"
        } else {
            "$scope: quedan ${formatWholeAmount(available)} · $location"
        }
    }

    private fun getGlobalLimitExposure(
        dayKey: String,
        ownerKey: String,
        ownerKeys: Set<String>,
        bucket: SaleExposureLimitBucket,
        cashierKeys: Set<String>,
        cashierPoolOnly: Boolean,
    ): Double {
        return calculateGlobalLimitExposure(
            tickets = salesRepository.getTicketsForDay(dayKey),
            ownerKey = ownerKey,
            ownerKeys = ownerKeys,
            bucket = bucket,
            cashierKeys = cashierKeys,
            cashierPoolOnly = cashierPoolOnly,
        )
    }

    private fun resolveActorKey(session: ActiveSession?): String {
        return session?.username?.ifBlank { session.userId } ?: ""
    }

    private fun getCashierLimits(session: ActiveSession?): CashierLimits {
        val ownerId = session?.adminId ?: session?.userId ?: return CashierLimits()
        val prefs = context.getSharedPreferences(SalesStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + ownerId, null)
            ?: return if (session?.role == UserRole.ADMIN) CashierLimits.noLimit() else CashierLimits()
        return decodeCashierLimitsForSession(raw, session)
    }

    private fun getPoolLimits(session: ActiveSession?): CashierLimits {
        val ownerId = session?.adminId ?: session?.userId ?: return CashierLimits()
        val prefs = context.getSharedPreferences(SalesStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(SalesStorageKeys.CASHIER_LIMITS_PREFIX + ownerId, null)
            ?: return CashierLimits()
        return decodeCashierPoolLimitsForSession(raw)
    }

}

internal fun decodeCashierLimitsForSession(raw: String?, session: ActiveSession?): CashierLimits {
    val role = session?.role
    return runCatching {
        val json = raw?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
        val defaults = json.optJSONObject("defaults") ?: JSONObject()
        val row = resolveCashierLimitRowForSession(json.optJSONObject("byUser"), session)
        val adminSelf = json.optJSONObject("adminSelf")
        if (role == UserRole.ADMIN) {
            val adminLimitRow = when {
                adminSelf.hasPositiveSaleLimit() -> adminSelf
                row.hasPositiveSaleLimit() -> row
                else -> return CashierLimits.noLimit()
            }
            return decodeCashierLimitRow(adminLimitRow, CashierLimits.noLimit())
        }

        val base = decodeCashierLimitRow(defaults, CashierLimits())
        decodeCashierLimitRow(row, base)
    }.getOrDefault(if (role == UserRole.ADMIN) CashierLimits.noLimit() else CashierLimits())
}

internal fun decodeCashierPoolLimitsForSession(raw: String?): CashierLimits {
    return runCatching {
        val json = raw?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
        val pool = json.optJSONObject("pool")
        decodeCashierLimitRow(pool, CashierLimits())
    }.getOrDefault(CashierLimits())
}

private fun resolveCashierLimitRowForSession(
    byUser: JSONObject?,
    session: ActiveSession?,
): JSONObject? {
    byUser ?: return null
    val keys = resolveCashierLimitLookupKeys(session)
    if (keys.isEmpty()) return null
    keys.forEach { key ->
        byUser.optJSONObject(key)?.let { return it }
    }
    val normalizedKeys = keys.map { it.lowercase(Locale.US) }.toSet()
    val iterator = byUser.keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        if (key.trim().lowercase(Locale.US) in normalizedKeys) {
            byUser.optJSONObject(key)?.let { return it }
        }
    }
    return null
}

private fun resolveCashierLimitLookupKeys(session: ActiveSession?): List<String> {
    if (session == null) return emptyList()
    return listOfNotNull(
        session.username.takeIf { it.isNotBlank() },
        session.userId.takeIf { it.isNotBlank() },
        session.adminId?.takeIf { it.isNotBlank() },
        session.adminUser?.takeIf { it.isNotBlank() },
        session.authUserId?.takeIf { it.isNotBlank() },
    ).distinct()
}

private fun JSONObject?.hasPositiveSaleLimit(): Boolean {
    if (this == null) return false
    return optDouble("daySale", optDouble("day_sale", 0.0)) > 0.0 ||
        optDouble("q", optDouble("quiniela", 0.0)) > 0.0 ||
        optDouble("pale", optDouble("p", 0.0)) > 0.0 ||
        optDouble("sp", optDouble("superPale", optDouble("super_pale", optDouble("p", 0.0)))) > 0.0 ||
        optDouble("t", optDouble("tripleta", 0.0)) > 0.0 ||
        optDouble("p3", optDouble("pick3Straight", optDouble("pick3_straight", optDouble("p", 0.0)))) > 0.0 ||
        optDouble("p3box", optDouble("pick3Box", optDouble("pick3_box", optDouble("p3", optDouble("p", 0.0))))) > 0.0 ||
        optDouble("p4", optDouble("pick4Straight", optDouble("pick4_straight", optDouble("p", 0.0)))) > 0.0 ||
        optDouble("p4box", optDouble("pick4Box", optDouble("pick4_box", optDouble("p4", optDouble("p", 0.0))))) > 0.0
}

private fun decodeCashierLimitRow(row: JSONObject?, base: CashierLimits): CashierLimits {
    val rowPaleFallback = row?.optDouble("p", base.pale) ?: base.pale
    val rowSuperPaleFallback = row?.optDouble("p", base.superPale) ?: base.superPale
    val rowPick3StraightFallback = row?.optDouble("p", base.pick3Straight) ?: base.pick3Straight
    val rowPick3BoxFallback = row?.optDouble("p", base.pick3Box) ?: base.pick3Box
    val rowPick4StraightFallback = row?.optDouble("p", base.pick4Straight) ?: base.pick4Straight
    val rowPick4BoxFallback = row?.optDouble("p", base.pick4Box) ?: base.pick4Box
    return CashierLimits(
        daySale = row?.optDouble("daySale", base.daySale) ?: base.daySale,
        q = row?.optDouble("q", base.q) ?: base.q,
        pale = row?.optDouble("pale", rowPaleFallback) ?: base.pale,
        superPale = row?.optDouble("sp", rowSuperPaleFallback) ?: base.superPale,
        t = row?.optDouble("t", base.t) ?: base.t,
        pick3Straight = row?.optDouble("p3", rowPick3StraightFallback) ?: base.pick3Straight,
        pick3Box = row?.optDouble("p3box", rowPick3BoxFallback) ?: base.pick3Box,
        pick4Straight = row?.optDouble("p4", rowPick4StraightFallback) ?: base.pick4Straight,
        pick4Box = row?.optDouble("p4box", rowPick4BoxFallback) ?: base.pick4Box,
    )
}

internal fun resolveExposureOwnerKey(session: ActiveSession?): String {
    return session?.adminId?.takeIf { it.isNotBlank() }
        ?: session?.adminUser?.takeIf { it.isNotBlank() }
        ?: session?.userId?.takeIf { it.isNotBlank() }
        ?: session?.username.orEmpty()
}

internal fun resolveExposureOwnerKeys(session: ActiveSession?): Set<String> {
    if (session == null) return emptySet()
    return setOfNotNull(
        session.adminId?.takeIf { it.isNotBlank() },
        session.adminUser?.takeIf { it.isNotBlank() },
        session.userId.takeIf { it.isNotBlank() },
        session.username.takeIf { it.isNotBlank() },
    )
}

internal fun resolveExposureCashierKeys(session: ActiveSession?): Set<String> {
    if (session?.role != UserRole.CASHIER && session?.role != UserRole.ADMIN) return emptySet()
    return setOfNotNull(
        session.userId.takeIf { it.isNotBlank() },
        session.username.takeIf { it.isNotBlank() },
    )
}

internal fun resolvePlayLimitCashierKeys(session: ActiveSession?): Set<String> {
    return if (session?.role == UserRole.ADMIN) resolveExposureCashierKeys(session) else emptySet()
}

internal fun calculateGlobalLimitExposure(
    tickets: List<TicketRecord>,
    ownerKey: String,
    bucket: SaleExposureLimitBucket,
    cashierKeys: Set<String> = emptySet(),
    cashierPoolOnly: Boolean = false,
    ownerKeys: Set<String> = emptySet(),
): Double {
    val resolvedOwnerKeys = normalizeExposureKeys(ownerKeys + ownerKey)
    return tickets
        .asSequence()
        .filter { ticket ->
            matchesExposureOwner(ticket, resolvedOwnerKeys) &&
                matchesExposureCashier(ticket, cashierKeys) &&
                matchesExposureCashierPool(ticket, cashierPoolOnly) &&
                !isExposureVoidStatus(ticket.status)
        }
        .sumOf { ticket ->
            ticket.plays.sumOf { play ->
                val playBucket = resolveSaleExposureLimitBucket(play.playType, play.number, play.lotteryId)
                if (playBucket.matchesLimitBucket(bucket)) play.amount else 0.0
            }
        }
}

internal fun calculateGlobalStagedExposure(
    stagedRows: List<SaleStagedRow>,
    bucket: SaleExposureLimitBucket,
): Double {
    return stagedRows.sumOf { row ->
        val rowBucket = resolveSaleExposureLimitBucket(row.playType, row.number, row.lotteryId)
        if (rowBucket.matchesLimitBucket(bucket)) row.amount else 0.0
    }
}

internal fun calculateActorDailySaleTotal(
    tickets: List<TicketRecord>,
    actorKey: String,
): Double {
    return tickets
        .asSequence()
        .filter { ticket -> matchesSalesActorTicketForDailyLimit(ticket, actorKey) && !isExposureVoidStatus(ticket.status) }
        .sumOf { it.total }
}

private fun matchesSalesActorTicketForDailyLimit(ticket: TicketRecord, actorKey: String): Boolean {
    if (actorKey.isBlank()) return false
    if (ticket.sellerId == actorKey || ticket.sellerUser == actorKey) return true
    val canFallbackToAdminOwner = ticket.role == UserRole.ADMIN ||
        (ticket.sellerId.isNullOrBlank() && ticket.sellerUser.isNullOrBlank())
    if (!canFallbackToAdminOwner) return false
    return ticket.adminId == actorKey || ticket.adminUser == actorKey
}

private fun matchesExposureOwner(ticket: TicketRecord, ownerKeys: Set<String>): Boolean {
    if (ownerKeys.isEmpty()) return true
    return normalizeExposureKey(ticket.adminId) in ownerKeys ||
        normalizeExposureKey(ticket.adminUser) in ownerKeys
}

private fun matchesExposureCashier(ticket: TicketRecord, cashierKeys: Set<String>): Boolean {
    if (cashierKeys.isEmpty()) return true
    return cashierKeys.any { key ->
        ticket.sellerId.equals(key, ignoreCase = true) ||
            ticket.sellerUser.equals(key, ignoreCase = true)
    }
}

private fun matchesExposureCashierPool(ticket: TicketRecord, enabled: Boolean): Boolean {
    if (!enabled) return true
    if (ticket.role == UserRole.CASHIER) return true
    val sellerKeys = listOfNotNull(
        ticket.sellerId?.takeIf { it.isNotBlank() },
        ticket.sellerUser?.takeIf { it.isNotBlank() },
    )
    if (sellerKeys.isEmpty()) return false
    val adminKeys = listOfNotNull(
        ticket.adminId?.takeIf { it.isNotBlank() },
        ticket.adminUser?.takeIf { it.isNotBlank() },
    )
    return sellerKeys.none { seller ->
        adminKeys.any { admin -> seller.equals(admin, ignoreCase = true) }
    }
}

private fun isExposureVoidStatus(status: String): Boolean {
    return status.equals("voided", true) ||
        status.equals("nulled", true) ||
        status.equals("invalid", true)
}

data class CashierLimits(
    val daySale: Double = 10000.0,
    val q: Double = 10000.0,
    val pale: Double = 500.0,
    val superPale: Double = 500.0,
    val t: Double = 75.0,
    val pick3Straight: Double = 500.0,
    val pick3Box: Double = 500.0,
    val pick4Straight: Double = 500.0,
    val pick4Box: Double = 500.0,
) {
    companion object {
        fun noLimit(): CashierLimits = CashierLimits(
            daySale = 0.0,
            q = 0.0,
            pale = 0.0,
            superPale = 0.0,
            t = 0.0,
            pick3Straight = 0.0,
            pick3Box = 0.0,
            pick4Straight = 0.0,
            pick4Box = 0.0,
        )
    }

    fun typeLimitFor(playType: String): Double {
        return when (playType) {
            "Q" -> q
            "P" -> pale
            "SP" -> superPale
            "T" -> t
            "P3" -> pick3Straight
            "P3BOX", "P3B" -> pick3Box
            "P4" -> pick4Straight
            "P4BOX", "P4B" -> pick4Box
            else -> 0.0
        }
    }
}

data class SaleExposureLimitBucket(
    val playType: String,
    val number: String,
    val lotteryId: String = "",
)

data class SaleLimitRemainingRow(
    val playType: String,
    val number: String,
    val lotteryId: String = "",
    val limit: Double,
    val sold: Double,
    val pending: Double,
) {
    val remaining: Double
        get() = (limit - sold - pending).coerceAtLeast(0.0)
    val overLimit: Boolean
        get() = limit > 0.0 && sold + pending >= limit
    val label: String
        get() = listOf(playType, number).filter { it.isNotBlank() }.joinToString(" ")
}

fun resolveSaleLimitRemainingRows(
    role: UserRole,
    stagedRows: List<SaleStagedRow>,
    tickets: List<TicketRecord>,
    ownerKey: String,
    cashierKeys: Set<String>,
    limits: CashierLimits,
    ownerKeys: Set<String> = emptySet(),
): List<SaleLimitRemainingRow> {
    if (role != UserRole.CASHIER && role != UserRole.ADMIN) return emptyList()
    return stagedRows
        .map { row -> resolveSaleExposureLimitBucket(row.playType, row.number, row.lotteryId) }
        .distinct()
        .mapNotNull { bucket ->
            val limit = limits.typeLimitFor(bucket.playType)
            if (limit <= 0.0) return@mapNotNull null
            SaleLimitRemainingRow(
                playType = bucket.playType,
                number = bucket.number,
                lotteryId = bucket.lotteryId,
                limit = limit,
                sold = calculateGlobalLimitExposure(
                    tickets = tickets,
                    ownerKey = ownerKey,
                    bucket = bucket,
                    cashierKeys = saleLimitExposureCashierKeys(role, cashierKeys),
                    cashierPoolOnly = saleLimitExposureCashierPoolOnly(role),
                    ownerKeys = ownerKeys,
                ),
                pending = calculateGlobalStagedExposure(stagedRows, bucket),
            )
        }
        .sortedWith(compareBy<SaleLimitRemainingRow> { it.playType }.thenBy { it.number }.thenBy { it.lotteryId })
}

internal fun calculateSaleLimitSoldExposureForRole(
    role: UserRole,
    tickets: List<TicketRecord>,
    ownerKey: String,
    ownerKeys: Set<String>,
    bucket: SaleExposureLimitBucket,
    cashierKeys: Set<String>,
): Double {
    return calculateGlobalLimitExposure(
        tickets = tickets,
        ownerKey = ownerKey,
        ownerKeys = ownerKeys,
        bucket = bucket,
        cashierKeys = saleLimitExposureCashierKeys(role, cashierKeys),
        cashierPoolOnly = saleLimitExposureCashierPoolOnly(role),
    )
}

private fun saleLimitExposureCashierKeys(role: UserRole, cashierKeys: Set<String>): Set<String> {
    return if (role == UserRole.ADMIN) cashierKeys else emptySet()
}

private fun saleLimitExposureCashierPoolOnly(role: UserRole): Boolean {
    return role == UserRole.CASHIER
}

fun resolveSaleExposureLimitBucket(playType: String, number: String, lotteryId: String? = null): SaleExposureLimitBucket {
    val normalizedType = playType.uppercase()
    val digits = number.filter(Char::isDigit)
    val bucketNumber = when (normalizedType) {
        "Q" -> digits.padStart(2, '0').takeLast(2)
        "P", "SP", "T" -> digits
        "P3", "P4" -> digits
        "P3BOX", "P3B", "P4BOX", "P4B" -> digits.toCharArray().sorted().joinToString("")
        else -> number
    }
    return SaleExposureLimitBucket(normalizedType, bucketNumber, lotteryId.orEmpty())
}

private fun SaleExposureLimitBucket.displayLabel(): String {
    return if (number.isBlank()) playType else "Nº $number"
}

private fun SaleExposureLimitBucket.matchesLimitBucket(target: SaleExposureLimitBucket): Boolean {
    return playType == target.playType &&
        number == target.number &&
        (target.lotteryId.isBlank() || lotteryId == target.lotteryId)
}

private fun normalizeExposureKeys(values: Iterable<String?>): Set<String> {
    return values.mapNotNullTo(mutableSetOf()) { normalizeExposureKey(it) }
}

private fun normalizeExposureKey(value: String?): String? {
    return value?.trim()?.lowercase(java.util.Locale.US)?.takeIf { it.isNotBlank() }
}
