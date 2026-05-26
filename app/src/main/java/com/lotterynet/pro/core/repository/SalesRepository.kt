package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.ExposureSummary
import com.lotterynet.pro.core.model.TicketRecord

interface SalesRepository {
    fun saveTicket(ticket: TicketRecord)
    fun getTicketsForDay(dayKey: String): List<TicketRecord>
    fun getTicketsForActor(dayKey: String, actorKey: String): List<TicketRecord>
    fun getExposureSummary(dayKey: String, lotteryId: String?, playType: String, number: String): ExposureSummary
}
