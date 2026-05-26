package com.lotterynet.pro.core.export

import com.lotterynet.pro.core.model.TicketRecord
import java.util.Locale

object TicketSecurity {
    fun resolveSecurityCode(ticket: TicketRecord, bancaName: String): String {
        val raw = ticket.securityCode.orEmpty().filter { it.isLetterOrDigit() }.uppercase(Locale.getDefault())
        if (raw.isNotBlank()) return raw
        return issueTicketSecurityCode(ticket, bancaName)
    }

    fun issueTicketSecurityCode(ticket: TicketRecord, bancaName: String): String {
        return simpleTicketChecksum(buildTicketSecuritySeed(ticket, bancaName)).take(6)
    }

    private fun buildTicketSecuritySeed(ticket: TicketRecord, bancaName: String): String {
        val lots = ticket.plays.mapNotNull { it.lotteryName }.distinct().joinToString(" / ")
        return listOf(
            "LNPRO-S1",
            ticket.id,
            NativeBitmapExport.formatDateForTicket(ticket.createdAtEpochMs),
            NativeBitmapExport.formatTimeForTicket(ticket.createdAtEpochMs),
            ((ticket.total).toString()),
            lots,
            bancaName,
        ).joinToString("|")
    }

    private fun simpleTicketChecksum(input: String): String {
        var h1 = 0x811c9dc5.toInt()
        var h2 = 0x1b873593
        input.forEach { char ->
            val code = char.code
            h1 = int32Mul(h1 xor code, 16777619)
            h2 = int32Mul(h2 xor code, 2246822519L.toInt())
        }
        val p1 = Integer.toUnsignedString(h1, 36).uppercase(Locale.getDefault())
        val p2 = Integer.toUnsignedString(h2, 36).uppercase(Locale.getDefault())
        return (p1 + p2).replace(Regex("[^A-Z0-9]"), "")
    }

    private fun int32Mul(a: Int, b: Int): Int {
        return (a.toLong() * b.toLong()).toInt()
    }
}
