package com.lotterynet.pro.core.repository

import com.lotterynet.pro.core.model.OfficialTicketVisualSpec
import com.lotterynet.pro.core.model.ResultShareRow
import com.lotterynet.pro.core.model.ResultsSharePayload
import com.lotterynet.pro.core.model.ResultsVisualSpec
import com.lotterynet.pro.core.model.ShareEnvelope
import com.lotterynet.pro.core.model.TicketQrPayload
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.LotteryResult

interface ExportTemplateRepository {
    fun getOfficialTicketSpec(): OfficialTicketVisualSpec
    fun getResultsSpec(): ResultsVisualSpec
    fun buildTicketQrPayload(ticket: TicketRecord, bancaName: String, securityCode: String = ""): TicketQrPayload
    fun buildOfficialTicketPreviewHtml(ticket: TicketRecord, bancaName: String, securityCode: String = ""): String
    fun buildTicketWhatsAppShare(ticket: TicketRecord, bancaName: String): ShareEnvelope
    fun buildResultsShareText(payload: ResultsSharePayload): String
    fun buildResultsWhatsAppShare(payload: ResultsSharePayload): ShareEnvelope
    fun buildResultsShareRows(rows: List<ResultShareRow>): List<ResultShareRow>
    fun mapLotteryResultsToShareRows(results: List<LotteryResult>): List<ResultShareRow>
}
