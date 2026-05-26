package com.lotterynet.pro.core.sync

import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.RechargeRecord
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.model.effectiveDrawDateKey
import com.lotterynet.pro.core.model.isPendingWinnerStatus
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class WebTicketRemotePayload(
    val tickets: List<TicketRecord>,
    val deletedIds: Set<String>,
)

internal fun parseWebTicketsPayload(payloadJson: String?): List<TicketRecord> {
    return parseWebTicketRemotePayload(payloadJson).tickets
}

internal fun parseWebTicketRemotePayload(payloadJson: String?): WebTicketRemotePayload {
    val raw = payloadJson?.trim()?.takeIf { it.isNotBlank() }
        ?: return WebTicketRemotePayload(emptyList(), emptySet())
    if (raw.startsWith("[")) {
        return WebTicketRemotePayload(parseWebTicketArray(raw), emptySet())
    }
    val root = runCatching { JSONObject(raw) }.getOrNull()
        ?: return WebTicketRemotePayload(emptyList(), emptySet())
    val ticketArray = root.optJSONArray("tickets")
        ?: root.optJSONArray("items")
        ?: root.optJSONArray("data")
        ?: JSONArray()
    return WebTicketRemotePayload(
        tickets = parseWebTicketArray(ticketArray),
        deletedIds = root.collectDeletedTicketIds(),
    )
}

internal fun buildWebTicketRemotePayload(
    tickets: List<TicketRecord>,
    deletedIds: Set<String>,
    banca: String? = null,
): String {
    return JSONObject().apply {
        put("schemaVersion", 2)
        put("updatedAtMs", System.currentTimeMillis())
        put("tickets", JSONArray(tickets.map { ticket -> ticketRecordToWebCompatibleJson(ticket, banca) }))
        put("deletedIds", JSONArray(deletedIds.filter { it.isNotBlank() }.sorted()))
    }.toString()
}

private fun parseWebTicketArray(raw: String): List<TicketRecord> {
    val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
    return parseWebTicketArray(array)
}

private fun parseWebTicketArray(array: JSONArray): List<TicketRecord> {
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            webTicketToRecord(item)?.let(::add)
        }
    }
}

private fun JSONObject.collectDeletedTicketIds(): Set<String> {
    val ids = linkedSetOf<String>()
    listOf("deletedIds", "deletedTicketIds", "removedIds").forEach { key ->
        optJSONArray(key)?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index, "").trim().takeIf { it.isNotBlank() }?.let(ids::add)
            }
        }
    }
    listOf("deleted", "deletedTickets", "removed").forEach { key ->
        optJSONArray(key)?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                val id = item?.optString("id", "")?.trim()
                    ?: array.optString(index, "").trim()
                id.takeIf { it.isNotBlank() }?.let(ids::add)
            }
        }
    }
    return ids
}

internal fun ticketRecordToWebCompatibleJson(
    ticket: TicketRecord,
    banca: String? = null,
): JSONObject {
    val status = ticket.status.ifBlank { "active" }
    return JSONObject().apply {
        put("id", ticket.id)
        put("type", "lot")
        put("serial", ticket.serial ?: ticket.id)
        put("lots", ticket.plays.joinToString(" / ") { it.lotteryName.orEmpty() }.ifBlank { banca.orEmpty() })
        put("lotteries", ticket.plays.joinToString(" / ") { it.lotteryName.orEmpty() }.ifBlank { banca.orEmpty() })
        put(
            "items",
            JSONArray(ticket.plays.map { play ->
                JSONObject().apply {
                    put("type", play.playType)
                    put("playType", play.playType)
                    put("nums", play.number)
                    put("number", play.number)
                    put("amt", play.amount)
                    put("amount", play.amount)
                    put("lotId", play.lotteryId)
                    put("lotName", play.lotteryName)
                    put("lotId2", play.secondaryLotteryId)
                    put("lotName2", play.secondaryLotteryName)
                    put("lotteryId", play.lotteryId)
                    put("lotteryName", play.lotteryName)
                    put("secondaryLotteryId", play.secondaryLotteryId)
                    put("secondaryLotteryName", play.secondaryLotteryName)
                }
            }),
        )
        put("subtotal", ticket.subtotal)
        put("discount", ticket.discount)
        put("tot", ticket.total)
        put("total", ticket.total)
        put("totalPrize", ticket.totalPrize)
        put("totalPremio", ticket.totalPrize)
        put("bancaNombre", banca.orEmpty())
        put("adminId", ticket.adminId.orEmpty())
        put("adminUser", ticket.adminUser.orEmpty())
        put("cajeroId", if (ticket.role == UserRole.CASHIER) ticket.sellerId.orEmpty() else JSONObject.NULL)
        put("vendedorId", ticket.sellerId.orEmpty())
        put("vendedorRol", ticket.role.name.lowercase(Locale.US))
        put("vendedorNombre", ticket.sellerUser.orEmpty())
        put("saleMode", "native")
        put("offlineSale", false)
        put("createdAtMs", ticket.createdAtEpochMs)
        put("createdAtEpochMs", ticket.createdAtEpochMs)
        put("updatedAt", ticket.createdAtEpochMs)
        put("drawDateKey", ticket.effectiveDrawDateKey())
        put("drawDate", ticket.effectiveDrawDateKey())
        put("dayKey", ticket.effectiveDrawDateKey())
        put("date", formatDominicanDate(ticket.createdAtEpochMs))
        put("time", formatDominicanTime(ticket.createdAtEpochMs))
        put("securityCode", ticket.securityCode.orEmpty())
        put("note", ticket.note.orEmpty())
        put("st", status)
        put("status", status)
    }
}

internal fun parseWebRechargesPayload(payloadJson: String?): List<RechargeRecord> {
    val raw = payloadJson?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
    val array = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            webRechargeToRecord(item)?.let(::add)
        }
    }
}

internal fun rechargeRecordToWebCompatibleJson(
    record: RechargeRecord,
): JSONObject {
    return JSONObject().apply {
        put("id", record.id)
        put("providerId", record.providerId.orEmpty())
        put("providerName", record.providerName.orEmpty())
        put("prov", record.providerId ?: record.providerName.orEmpty())
        put("phoneNumber", record.phoneNumber.orEmpty())
        put("phone", record.phoneNumber.orEmpty())
        put("amount", record.amount)
        put("amt", record.amount)
        put("productType", record.productType)
        put("status", record.status)
        put("providerReference", record.providerReference.orEmpty())
        put("userId", record.userId.orEmpty())
        put("userName", record.userName.orEmpty())
        put("adminId", record.adminId.orEmpty())
        put("adminUser", record.adminUser.orEmpty())
        put("createdAtMs", record.createdAtEpochMs)
        put("createdAtEpochMs", record.createdAtEpochMs)
        put("date", formatDominicanDate(record.createdAtEpochMs))
        put("time", formatDominicanTime(record.createdAtEpochMs))
    }
}

internal fun mergeTicketsPreferImported(
    existing: List<TicketRecord>,
    imported: List<TicketRecord>,
): List<TicketRecord> {
    val byId = linkedMapOf<String, TicketRecord>()
    existing.forEach { ticket ->
        if (ticket.id.isNotBlank()) byId[ticket.id] = ticket
    }
    imported.forEach { ticket ->
        if (ticket.id.isNotBlank()) byId[ticket.id] = resolveTicketMergeWinner(byId[ticket.id], ticket)
    }
    return byId.values.sortedByDescending { it.createdAtEpochMs }
}

private fun resolveTicketMergeWinner(existing: TicketRecord?, imported: TicketRecord): TicketRecord {
    existing ?: return imported
    if (existing.hasTerminalCancelStatus()) return existing.withoutPrizeIfCancelled()
    if (imported.hasTerminalCancelStatus()) return imported.withoutPrizeIfCancelled()
    if (imported.hasPaidStatus()) return imported.withPreservedPrizeFrom(existing)
    if (existing.hasPaidStatus()) return existing.withPreservedPrizeFrom(imported)
    val existingWinner = existing.isPendingWinner()
    val importedWinner = imported.isPendingWinner()
    return when {
        existingWinner && !importedWinner -> existing
        importedWinner && !existingWinner -> imported
        existingWinner && importedWinner && existing.totalPrize > imported.totalPrize -> existing
        else -> imported
    }
}

private fun TicketRecord.isPendingWinner(): Boolean {
    if (hasTerminalCancelStatus()) return false
    if (hasPaidStatus()) return false
    return isPendingWinnerStatus() || totalPrize > 0.0
}

private fun TicketRecord.hasTerminalCancelStatus(): Boolean {
    return isTerminalCancelTicketStatus(status)
}

private fun TicketRecord.hasPaidStatus(): Boolean {
    return isTerminalPaidTicketStatus(status)
}

private fun TicketRecord.withPreservedPrizeFrom(other: TicketRecord): TicketRecord {
    return if (totalPrize <= 0.0 && other.totalPrize > 0.0) copy(totalPrize = other.totalPrize) else this
}

private fun TicketRecord.withoutPrizeIfCancelled(): TicketRecord {
    return if (hasTerminalCancelStatus() && totalPrize != 0.0) copy(totalPrize = 0.0) else this
}

internal fun isTerminalPaidTicketStatus(status: String): Boolean {
    return when (status.trim().lowercase(Locale.US)) {
        "paid",
        "pagado",
        "paid_out",
        "payout",
        "cobrado",
        "premio_pagado" -> true
        else -> false
    }
}

internal fun isTerminalCancelTicketStatus(status: String): Boolean {
    return when (status.trim().lowercase(Locale.US)) {
        "voided",
        "void",
        "nulled",
        "anulado",
        "annulled",
        "cancelled",
        "canceled",
        "cancelado",
        "invalid",
        "invalido",
        "inválido",
        "deleted",
        "borrado",
        "removed" -> true
        else -> false
    }
}

internal fun filterDeletedTickets(
    tickets: List<TicketRecord>,
    deletedIds: Set<String>,
): List<TicketRecord> {
    if (deletedIds.isEmpty()) return tickets
    return tickets.filterNot { ticket -> ticket.id in deletedIds }
}

internal fun filterServerVisibleTickets(
    tickets: List<TicketRecord>,
    deletedIds: Set<String> = emptySet(),
): List<TicketRecord> {
    return tickets.filterNot { ticket ->
        ticket.id in deletedIds || ticket.hasRemoteDeletedStatus()
    }
}

private fun TicketRecord.hasRemoteDeletedStatus(): Boolean {
    return when (status.trim().lowercase(Locale.US)) {
        "deleted", "borrado", "removed" -> true
        else -> false
    }
}

internal fun mergeRechargesPreferImported(
    existing: List<RechargeRecord>,
    imported: List<RechargeRecord>,
): List<RechargeRecord> {
    val byId = linkedMapOf<String, RechargeRecord>()
    existing.forEach { record ->
        if (record.id.isNotBlank()) byId[record.id] = record
    }
    imported.forEach { record ->
        if (record.id.isNotBlank()) byId[record.id] = record
    }
    return byId.values.sortedByDescending { it.createdAtEpochMs }
}

private fun webTicketToRecord(json: JSONObject): TicketRecord? {
    val id = json.stringOrNull("id") ?: return null
    val plays = json.optJSONArray("items")?.toPlayItems().orEmpty()
    val createdAtMs = json.resolveEpochMs()
    val total = json.numberValue("total")
        ?: json.numberValue("tot")
        ?: plays.sumOf { it.amount }
    val sellerId = json.stringOrNull("vendedorId") ?: json.stringOrNull("cajeroId")
    val sellerUser = json.stringOrNull("vendedorNombre")
    val role = UserRole.fromRaw(json.stringOrNull("vendedorRol"))
    return TicketRecord(
        id = id,
        serial = json.stringOrNull("serial") ?: id,
        securityCode = json.stringOrNull("securityCode"),
        sellerId = sellerId,
        sellerUser = sellerUser,
        adminId = json.stringOrNull("adminId"),
        adminUser = json.stringOrNull("adminUser"),
        role = if (role == UserRole.UNKNOWN && sellerId != null) UserRole.CASHIER else role,
        createdAtEpochMs = createdAtMs,
        drawDateKey = json.stringOrNull("drawDateKey")
            ?: json.stringOrNull("drawDate")
            ?: json.stringOrNull("dayKey"),
        plays = plays,
        subtotal = json.numberValue("subtotal") ?: total,
        discount = json.numberValue("discount") ?: 0.0,
        total = total,
        totalPrize = json.numberValue("totalPremio") ?: json.numberValue("totalPrize") ?: 0.0,
        note = json.stringOrNull("note"),
        status = json.stringOrNull("status")
            ?: json.stringOrNull("st")
            ?: "active",
    )
}

private fun webRechargeToRecord(json: JSONObject): RechargeRecord? {
    val id = json.stringOrNull("id") ?: return null
    val amount = json.numberValue("amount")
        ?: json.numberValue("amt")
        ?: 0.0
    return RechargeRecord(
        id = id,
        providerId = json.stringOrNull("providerId") ?: json.stringOrNull("prov"),
        providerName = json.stringOrNull("providerName") ?: json.stringOrNull("prov"),
        phoneNumber = json.stringOrNull("phoneNumber") ?: json.stringOrNull("phone"),
        amount = amount,
        productType = json.stringOrNull("productType") ?: "recarga",
        status = json.stringOrNull("status") ?: "pending",
        providerReference = json.stringOrNull("providerReference"),
        userId = json.stringOrNull("userId"),
        userName = json.stringOrNull("userName"),
        adminId = json.stringOrNull("adminId"),
        adminUser = json.stringOrNull("adminUser"),
        createdAtEpochMs = json.resolveEpochMs(),
    )
}

private fun JSONArray.toPlayItems(): List<PlayItem> {
    return buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: continue
            add(
                PlayItem(
                    number = item.stringOrNull("nums") ?: item.stringOrNull("number") ?: "",
                    playType = item.stringOrNull("localPlayType")
                        ?: item.stringOrNull("type")
                        ?: item.stringOrNull("playType")
                        ?: "",
                    amount = item.numberValue("amt") ?: item.numberValue("amount") ?: 0.0,
                    lotteryId = item.stringOrNull("lotId") ?: item.stringOrNull("lotteryId"),
                    lotteryName = item.stringOrNull("lotName") ?: item.stringOrNull("lotteryName"),
                    secondaryLotteryId = item.stringOrNull("lotId2") ?: item.stringOrNull("secondaryLotteryId"),
                    secondaryLotteryName = item.stringOrNull("lotName2") ?: item.stringOrNull("secondaryLotteryName"),
                ),
            )
        }
    }
}

private fun JSONObject.stringOrNull(key: String): String? {
    val value = optString(key, "").trim()
    return value.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

private fun JSONObject.numberValue(key: String): Double? {
    val value = opt(key) ?: return null
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }
}

private fun JSONObject.resolveEpochMs(): Long {
    listOf("createdAtMs", "createdAtEpochMs", "updatedAt", "tsMs")
        .firstNotNullOfOrNull { key ->
            numberValue(key)?.toLong()?.takeIf { it > 0L }
        }
        ?.let { return it }

    val dateValue = stringOrNull("date") ?: return System.currentTimeMillis()
    val timeValue = stringOrNull("time").orEmpty()
    val raw = listOf(dateValue, timeValue).filter { it.isNotBlank() }.joinToString(" ").trim()
    return parseDominicanDateTime(raw) ?: System.currentTimeMillis()
}

private fun parseDominicanDateTime(raw: String): Long? {
    if (raw.isBlank()) return null
    val zone = TimeZone.getTimeZone("America/Santo_Domingo")
    val patterns = listOf(
        "dd-MM-yyyy hh:mm:ss a",
        "dd-MM-yyyy hh:mm a",
        "dd-MM-yyyy HH:mm:ss",
        "dd-MM-yyyy HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = zone
                isLenient = false
            }.parse(raw)?.time
        }.getOrNull()
    }
}

private fun formatDominicanDate(epochMs: Long): String {
    val format = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(Date(epochMs))
}

private fun formatDominicanTime(epochMs: Long): String {
    val format = SimpleDateFormat("hh:mm:ss a", Locale.US)
    format.timeZone = TimeZone.getTimeZone("America/Santo_Domingo")
    return format.format(Date(epochMs))
}
