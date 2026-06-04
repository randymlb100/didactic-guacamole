package com.lotterynet.pro.core.storage

import android.content.Context
import androidx.core.content.edit
import com.lotterynet.pro.core.model.DeletedTicketRef
import com.lotterynet.pro.core.model.ExposureSummary
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.WinningPlayDetail
import com.lotterynet.pro.core.model.effectiveDrawDateKey
import com.lotterynet.pro.core.notification.WinningTicketNotifier
import com.lotterynet.pro.core.operations.normalizeActorLabelKey
import com.lotterynet.pro.core.repository.SalesRepository
import com.lotterynet.pro.core.sync.filterDeletedTickets
import com.lotterynet.pro.core.sync.mergeTicketsPreferImported
import org.json.JSONArray
import org.json.JSONObject

class LocalSalesRepository(
    context: Context,
) : SalesRepository {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences(SalesStorageKeys.PREFS_NAME, Context.MODE_PRIVATE)
    private val dayTicketCache = SalesDayTicketCache()

    fun getAvailableDayKeys(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith(SalesStorageKeys.TICKETS_PREFIX) }
            .map { it.removePrefix(SalesStorageKeys.TICKETS_PREFIX) }
            .sorted()
    }

    fun getAllTickets(): List<TicketRecord> {
        return getAvailableDayKeys()
            .flatMap(::getTicketsForDay)
            .sortedByDescending { it.createdAtEpochMs }
    }

    override fun saveTicket(ticket: TicketRecord) {
        if (!ticket.isSafeToPersistLocally()) return
        synchronized(STORAGE_LOCK) {
            val dayKey = ticket.effectiveDrawDateKey()
            val tickets = getTicketsForDay(dayKey).toMutableList()
            tickets.add(ticket)
            saveTicketsForDay(dayKey, tickets)
        }
    }

    fun replaceTicket(ticket: TicketRecord) {
        if (!ticket.isSafeToPersistLocally()) return
        synchronized(STORAGE_LOCK) {
            val dayKey = ticket.effectiveDrawDateKey()
            val currentTickets = getTicketsForDay(dayKey)
            val previous = currentTickets.firstOrNull { existing -> sameTicketRecordIdentity(existing, ticket) }
            val tickets = if (previous == null) {
                currentTickets + ticket
            } else {
                currentTickets.map { existing ->
                    if (sameTicketRecordIdentity(existing, ticket)) ticket else existing
                }
            }
            saveTicketsForDay(dayKey, tickets)
            WinningTicketNotifier.notifyIfNewPendingWinner(
                context = appContext,
                previous = previous,
                current = ticket,
                activeSession = LocalSessionRepository(appContext).getActiveSession(),
            )
        }
    }

    fun replaceScopedImportedTickets(
        ownerKey: String?,
        tickets: List<TicketRecord>,
    ) {
        synchronized(STORAGE_LOCK) {
            val normalizedOwner = ownerKey?.trim().orEmpty()
            if (normalizedOwner.isBlank() && tickets.isEmpty()) return
            val merged = reconcileScopedImportedTickets(
                existing = getAllTickets(),
                ownerKey = normalizedOwner,
                imported = tickets,
                deletedIds = getDeletedTicketIds(),
            )
            saveAllTickets(merged)
        }
    }

    fun duplicateTicket(source: TicketRecord, newTicket: TicketRecord) {
        if (!newTicket.isSafeToPersistLocally()) return
        synchronized(STORAGE_LOCK) {
            val dayKey = newTicket.effectiveDrawDateKey()
            val tickets = getTicketsForDay(dayKey).toMutableList()
            tickets.add(newTicket)
            saveTicketsForDay(dayKey, tickets)
        }
    }

    fun updateTicketStatus(ticket: TicketRecord, status: String): TicketRecord {
        val updated = ticket.copy(status = status)
        replaceTicket(updated)
        return updated
    }

    fun deleteTicket(ticket: TicketRecord) {
        synchronized(STORAGE_LOCK) {
            markTicketDeleted(ticket)
            val dayKey = ticket.effectiveDrawDateKey()
            val tickets = getTicketsForDay(dayKey).filterNot { existing -> existing.id == ticket.id }
            saveTicketsForDay(dayKey, tickets)
        }
    }

    fun getTicketsForDayAndLottery(dayKey: String, lotteryId: String): List<TicketRecord> {
        val normalizedLotteryId = lotteryId.trim()
        if (normalizedLotteryId.isBlank()) return emptyList()
        return getTicketsForDay(dayKey).filter { ticket ->
            ticket.plays.any { play ->
                play.lotteryId == normalizedLotteryId || play.secondaryLotteryId == normalizedLotteryId
            }
        }
    }

    fun voidTicketsForLottery(dayKey: String, lotteryId: String, note: String): Int {
        synchronized(STORAGE_LOCK) {
            val affectedIds = getTicketsForDayAndLottery(dayKey, lotteryId).map { it.id }.toSet()
            if (affectedIds.isEmpty()) return 0
            val updated = getTicketsForDay(dayKey).map { ticket ->
                if (ticket.id in affectedIds) ticket.copy(status = "voided", note = note) else ticket
            }
            saveTicketsForDay(dayKey, updated)
            return affectedIds.size
        }
    }

    fun deleteTicketsForLottery(dayKey: String, lotteryId: String): Int {
        val affected = getTicketsForDayAndLottery(dayKey, lotteryId)
        affected.forEach(::deleteTicket)
        return affected.size
    }

    fun moveTicketsForLotteryToNextDay(dayKey: String, lotteryId: String, note: String): Int {
        synchronized(STORAGE_LOCK) {
            val affected = getTicketsForDayAndLottery(dayKey, lotteryId)
            if (affected.isEmpty()) return 0
            val affectedIds = affected.map { it.id }.toSet()
            val currentRows = getTicketsForDay(dayKey).filterNot { it.id in affectedIds }
            saveTicketsForDay(dayKey, currentRows)
            val movedRows = affected.map { ticket ->
                ticket.copy(
                    createdAtEpochMs = addDominicanDays(ticket.createdAtEpochMs, 1),
                    drawDateKey = addDayKey(dayKey, 1),
                    status = "active",
                    note = note,
                )
            }
            movedRows
                .groupBy { it.effectiveDrawDateKey() }
                .forEach { (targetDayKey, rows) ->
                    val movedIds = rows.map { it.id }.toSet()
                    saveTicketsForDay(targetDayKey, getTicketsForDay(targetDayKey).filterNot { it.id in movedIds } + rows)
                }
            return affected.size
        }
    }

    fun getDeletedTicketIds(): Set<String> {
        val raw = prefs.getString(SalesStorageKeys.DELETED_TICKETS_KEY, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (index in 0 until array.length()) {
                    val id = array.optString(index).trim()
                    if (id.isNotBlank()) add(id)
                }
            }
        }.getOrDefault(emptySet())
    }

    fun getDeletedTicketRefsForDay(dayKey: String): List<DeletedTicketRef> {
        return getDeletedTicketRefs().filter { it.dayKey == dayKey }
    }

    private fun markTicketDeleted(ticket: TicketRecord) {
        val dayKey = ticket.effectiveDrawDateKey()
        markTicketDeleted(ticket.id)
        val refs = getDeletedTicketRefs()
            .filterNot { it.id == ticket.id }
            .plus(
                DeletedTicketRef(
                    id = ticket.id,
                    dayKey = dayKey,
                    deletedAtEpochMs = System.currentTimeMillis(),
                    total = ticket.total,
                    sellerId = ticket.sellerId,
                    sellerUser = ticket.sellerUser,
                    adminId = ticket.adminId,
                    adminUser = ticket.adminUser,
                    role = ticket.role,
                ),
            )
        prefs.edit(commit = true) {
            putString(SalesStorageKeys.DELETED_TICKET_REFS_KEY, JSONArray(refs.map(::deletedTicketRefToJson)).toString())
        }
    }

    private fun markTicketDeleted(ticketId: String) {
        val id = ticketId.trim()
        if (id.isBlank()) return
        val deletedIds = getDeletedTicketIds() + id
        prefs.edit(commit = true) {
            putString(SalesStorageKeys.DELETED_TICKETS_KEY, JSONArray(deletedIds.toList()).toString())
        }
    }

    fun getDeletedTicketRefs(): List<DeletedTicketRef> {
        val raw = prefs.getString(SalesStorageKeys.DELETED_TICKET_REFS_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val dayKey = item.optString("dayKey").trim()
                    if (id.isNotBlank() && dayKey.isNotBlank()) {
                        add(
                            DeletedTicketRef(
                                id = id,
                                dayKey = dayKey,
                                deletedAtEpochMs = item.optLong("deletedAtEpochMs", 0L),
                                total = item.optDouble("total", 0.0),
                                sellerId = item.optString("sellerId").takeIf { it.isNotBlank() },
                                sellerUser = item.optString("sellerUser").takeIf { it.isNotBlank() },
                                adminId = item.optString("adminId").takeIf { it.isNotBlank() },
                                adminUser = item.optString("adminUser").takeIf { it.isNotBlank() },
                                role = runCatching { UserRole.valueOf(item.optString("role")) }.getOrDefault(UserRole.UNKNOWN),
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveTicketsForDay(dayKey: String, tickets: List<TicketRecord>) {
        prefs.edit(commit = true) {
            putString(
                SalesStorageKeys.TICKETS_PREFIX + dayKey,
                JSONArray(tickets.map(::ticketToJson)).toString(),
            )
        }
        dayTicketCache.invalidate(dayKey)
    }

    private fun saveAllTickets(tickets: List<TicketRecord>) {
        val grouped = tickets.groupBy { it.effectiveDrawDateKey() }
        prefs.edit(commit = true) {
            prefs.all.keys
                .filter { it.startsWith(SalesStorageKeys.TICKETS_PREFIX) }
                .forEach(::remove)
            grouped.forEach { (dayKey, rows) ->
                putString(
                    SalesStorageKeys.TICKETS_PREFIX + dayKey,
                    JSONArray(rows.map(::ticketToJson)).toString(),
                )
            }
        }
        dayTicketCache.clear()
    }

    override fun getTicketsForDay(dayKey: String): List<TicketRecord> {
        val raw = prefs.getString(SalesStorageKeys.TICKETS_PREFIX + dayKey, null)
        return filterDeletedStoredTickets(dayTicketCache.getOrParse(dayKey, raw), getDeletedTicketIds())
    }

    override fun getTicketsForActor(dayKey: String, actorKey: String): List<TicketRecord> {
        if (actorKey.isBlank()) return emptyList()
        return getTicketsForDay(dayKey).filter { ticket -> matchesSalesActorTicket(ticket, actorKey) }
    }

    override fun getExposureSummary(dayKey: String, lotteryId: String?, playType: String, number: String): ExposureSummary {
        if (lotteryId.isNullOrBlank() || playType.isBlank() || number.isBlank()) return ExposureSummary()
        val sold = getTicketsForDay(dayKey).sumOf { ticket ->
            ticket.plays.sumOf { play ->
                if (play.lotteryId == lotteryId && play.playType == playType && play.number == number) {
                    play.amount
                } else {
                    0.0
                }
            }
        }
        return ExposureSummary(sold = sold, soldByActor = 0.0)
    }

    private fun ticketToJson(ticket: TicketRecord): JSONObject {
        return JSONObject().apply {
            put("id", ticket.id)
            put("serial", ticket.serial)
            put("securityCode", ticket.securityCode)
            put("sellerId", ticket.sellerId)
            put("sellerUser", ticket.sellerUser)
            put("adminId", ticket.adminId)
            put("adminUser", ticket.adminUser)
            put("role", ticket.role.name)
            put("createdAtEpochMs", ticket.createdAtEpochMs)
            put("drawDateKey", ticket.drawDateKey)
            put("drawDate", ticket.effectiveDrawDateKey())
            put("dayKey", ticket.effectiveDrawDateKey())
            put("subtotal", ticket.subtotal)
            put("discount", ticket.discount)
            put("total", ticket.total)
            put("totalPrize", ticket.totalPrize)
            put(
                "winningDetails",
                JSONArray(ticket.winningDetails.map { detail ->
                    JSONObject().apply {
                        put("lotteryName", detail.lotteryName)
                        put("playType", detail.playType)
                        put("playedNumber", detail.playedNumber)
                        put("resultNumber", detail.resultNumber)
                        put("hitPosition", detail.hitPosition)
                        put("amount", detail.amount)
                        put("payoutAmount", detail.payoutAmount)
                    }
                }),
            )
            put("status", ticket.status)
            put("note", ticket.note)
            put(
                "plays",
                JSONArray(ticket.plays.map { play ->
                    JSONObject().apply {
                        put("number", play.number)
                        put("playType", play.playType)
                        put("amount", play.amount)
                        put("lotteryId", play.lotteryId)
                        put("lotteryName", play.lotteryName)
                        put("secondaryLotteryId", play.secondaryLotteryId)
                        put("secondaryLotteryName", play.secondaryLotteryName)
                        put("straightDigits", play.straightDigits)
                        put("boxDigits", play.boxDigits)
                    }
                }),
            )
        }
    }

    private fun ticketFromJson(json: JSONObject): TicketRecord {
        val playsArray = json.optJSONArray("plays") ?: JSONArray()
        val winningDetailsArray = json.optJSONArray("winningDetails") ?: JSONArray()
        return TicketRecord(
            id = json.optString("id"),
            serial = json.optString("serial").takeIf { it.isNotBlank() },
            securityCode = json.optString("securityCode").takeIf { it.isNotBlank() },
            sellerId = json.optString("sellerId").takeIf { it.isNotBlank() },
            sellerUser = json.optString("sellerUser").takeIf { it.isNotBlank() },
            adminId = json.optString("adminId").takeIf { it.isNotBlank() },
            adminUser = json.optString("adminUser").takeIf { it.isNotBlank() },
            role = runCatching { UserRole.valueOf(json.optString("role")) }.getOrDefault(UserRole.UNKNOWN),
            createdAtEpochMs = json.optLong("createdAtEpochMs", System.currentTimeMillis()),
            drawDateKey = json.optString("drawDateKey")
                .ifBlank { json.optString("drawDate") }
                .ifBlank { json.optString("dayKey") }
                .takeIf { it.isNotBlank() },
            plays = buildList {
                for (index in 0 until playsArray.length()) {
                    val item = playsArray.optJSONObject(index) ?: continue
                    add(
                        PlayItem(
                            number = item.optString("number"),
                            playType = item.optString("playType"),
                            amount = item.optDouble("amount", 0.0),
                            lotteryId = item.optString("lotteryId").takeIf { it.isNotBlank() },
                            lotteryName = item.optString("lotteryName").takeIf { it.isNotBlank() },
                            secondaryLotteryId = item.optString("secondaryLotteryId").takeIf { it.isNotBlank() },
                            secondaryLotteryName = item.optString("secondaryLotteryName").takeIf { it.isNotBlank() },
                            straightDigits = item.optString("straightDigits").takeIf { it.isNotBlank() },
                            boxDigits = item.optString("boxDigits").takeIf { it.isNotBlank() },
                        ),
                    )
                }
            },
            subtotal = json.optDouble("subtotal", 0.0),
            discount = json.optDouble("discount", 0.0),
            total = json.optDouble("total", 0.0),
            totalPrize = json.optDouble("totalPrize", 0.0),
            winningDetails = buildList {
                for (index in 0 until winningDetailsArray.length()) {
                    val item = winningDetailsArray.optJSONObject(index) ?: continue
                    add(
                        WinningPlayDetail(
                            lotteryName = item.optString("lotteryName"),
                            playType = item.optString("playType"),
                            playedNumber = item.optString("playedNumber"),
                            resultNumber = item.optString("resultNumber"),
                            hitPosition = item.optString("hitPosition"),
                            amount = item.optDouble("amount", 0.0),
                            payoutAmount = item.optDouble("payoutAmount", 0.0),
                        ),
                    )
                }
            },
            status = json.optString("status", "active"),
            note = json.optString("note").takeIf { it.isNotBlank() },
        )
    }

    private fun deletedTicketRefToJson(ref: DeletedTicketRef): JSONObject {
        return JSONObject().apply {
            put("id", ref.id)
            put("dayKey", ref.dayKey)
            put("deletedAtEpochMs", ref.deletedAtEpochMs)
            put("total", ref.total)
            put("sellerId", ref.sellerId)
            put("sellerUser", ref.sellerUser)
            put("adminId", ref.adminId)
            put("adminUser", ref.adminUser)
            put("role", ref.role.name)
        }
    }

    private fun addDominicanDays(epochMs: Long, days: Int): Long {
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Santo_Domingo"))
        calendar.timeInMillis = epochMs
        calendar.add(java.util.Calendar.DAY_OF_YEAR, days)
        return calendar.timeInMillis
    }

    private fun buildDayKeyFromEpoch(epochMs: Long): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("America/Santo_Domingo")
        return format.format(java.util.Date(epochMs))
    }

    private fun addDayKey(dayKey: String, days: Int): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("America/Santo_Domingo")
        val date = runCatching { format.parse(dayKey) }.getOrNull() ?: return dayKey
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("America/Santo_Domingo"))
        calendar.time = date
        calendar.add(java.util.Calendar.DAY_OF_YEAR, days)
        return format.format(calendar.time)
    }

    private companion object {
        private val STORAGE_LOCK = Any()
    }
}

private fun sameTicketRecordIdentity(left: TicketRecord, right: TicketRecord): Boolean {
    if (left.id.isNotBlank() && right.id.isNotBlank() && left.id.equals(right.id, ignoreCase = true)) {
        return true
    }
    val leftSerial = left.serial?.trim().orEmpty()
    val rightSerial = right.serial?.trim().orEmpty()
    return leftSerial.isNotBlank() && rightSerial.isNotBlank() && leftSerial.equals(rightSerial, ignoreCase = true)
}

internal fun reconcileScopedImportedTickets(
    existing: List<TicketRecord>,
    ownerKey: String?,
    imported: List<TicketRecord>,
    deletedIds: Set<String>,
): List<TicketRecord> {
    val normalizedOwner = ownerKey?.trim().orEmpty()
    val adminOwnerAliases = buildAdminOwnerAliases(normalizedOwner, imported)
    val preserved = existing.filterNot { ticket ->
        normalizedOwner.isNotBlank() && matchesScopedImportedTicketOwner(ticket, normalizedOwner, adminOwnerAliases)
    }
    return filterDeletedTickets(
        tickets = mergeTicketsPreferImported(preserved, imported),
        deletedIds = deletedIds,
    )
}

private fun buildAdminOwnerAliases(ownerKey: String, imported: List<TicketRecord>): Set<String> {
    return buildSet {
        normalizeActorLabelKey(ownerKey)?.let(::add)
        imported.forEach { ticket ->
            normalizeActorLabelKey(ticket.adminId)?.let(::add)
            normalizeActorLabelKey(ticket.adminUser)?.let(::add)
        }
    }
}

private fun matchesScopedImportedTicketOwner(
    ticket: TicketRecord,
    ownerKey: String,
    adminOwnerAliases: Set<String>,
): Boolean {
    val adminMatches = listOf(ticket.adminId, ticket.adminUser)
        .any { key -> normalizeActorLabelKey(key)?.let(adminOwnerAliases::contains) == true }
    if (adminMatches) return true

    return ticket.sellerId.equals(ownerKey, ignoreCase = true) ||
        ticket.sellerUser.equals(ownerKey, ignoreCase = true)
}

internal fun TicketRecord.isSafeToPersistLocally(): Boolean {
    if (status.trim().lowercase(java.util.Locale.US) in setOf("deleted", "borrado", "removed")) return true
    if (total > 0.0 && plays.isEmpty()) return false
    return plays.all { play ->
        play.number.isNotBlank() &&
            play.playType.isNotBlank() &&
            !play.lotteryId.isNullOrBlank() &&
            !play.lotteryName.isNullOrBlank() &&
            play.amount > 0.0
    }
}

internal fun matchesSalesActorTicket(ticket: TicketRecord, actorKey: String): Boolean {
    if (actorKey.isBlank()) return false
    if (ticket.sellerId == actorKey || ticket.sellerUser == actorKey) return true
    val canFallbackToAdminOwner = ticket.role == UserRole.ADMIN ||
        (ticket.sellerId.isNullOrBlank() && ticket.sellerUser.isNullOrBlank())
    if (!canFallbackToAdminOwner) return false
    return ticket.adminId == actorKey || ticket.adminUser == actorKey
}

internal fun filterDeletedStoredTickets(
    tickets: List<TicketRecord>,
    deletedTicketIds: Set<String>,
): List<TicketRecord> {
    return filterDeletedTickets(tickets, deletedTicketIds)
}
