package com.lotterynet.pro.core.export

import com.lotterynet.pro.core.catalog.StaticLotteryCatalogRepository
import com.lotterynet.pro.core.model.ResultShareRow
import com.lotterynet.pro.core.model.LotteryResult
import com.lotterynet.pro.core.model.ResultsSharePayload
import com.lotterynet.pro.core.model.ResultsVisualSpec
import com.lotterynet.pro.core.model.ShareEnvelope
import com.lotterynet.pro.core.model.TicketQrPayload
import com.lotterynet.pro.core.model.TicketQrPlayPayload
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.OfficialTicketVisualSpec
import com.lotterynet.pro.core.model.effectiveDrawDateKey
import com.lotterynet.pro.core.model.isPaidTicketStatus
import com.lotterynet.pro.core.repository.ExportTemplateRepository

class StaticExportTemplateRepository : ExportTemplateRepository {
    private val catalogRepository = StaticLotteryCatalogRepository()

    override fun getOfficialTicketSpec(): OfficialTicketVisualSpec {
        return OfficialTicketVisualSpec(
            templateVersion = "oficial-v10",
            canvasWidthPx = 420,
            renderScale = 3,
            headerHeightPx = 156,
            metaHeightPx = 72,
            totalBarHeightPx = 76,
            qrSectionHeightPx = 116,
            footerHeightPx = 48,
            primaryNavy = "#0f172a",
            secondaryNavy = "#1e293b",
            accentGold = "#f59e0b",
            accentGoldSoft = "#c8a84b",
            surface = "#ffffff",
            surfaceAlt = "#f8fafc",
            outline = "#e2e8f0",
        )
    }

    override fun getResultsSpec(): ResultsVisualSpec {
        return ResultsVisualSpec(
            templateVersion = "local-v2",
            pageWidthPx = 420,
            primaryBackground = "#ffffff",
            cardBackground = "#f8fafc",
            titleColor = "#0f172a",
            accentColor = "#f59e0b",
            outlineColor = "#e2e8f0",
        )
    }

    override fun buildTicketQrPayload(ticket: TicketRecord, bancaName: String, securityCode: String): TicketQrPayload {
        val lots = ticket.plays.mapNotNull { it.lotteryName }.distinct().joinToString(" / ")
        return TicketQrPayload(
            id = ticket.id,
            banca = bancaName,
            lots = lots,
            date = formatDate(ticket.createdAtEpochMs),
            total = ticket.total,
            securityCode = securityCode,
            plays = ticket.plays.map { play ->
                TicketQrPlayPayload(
                    type = play.playType,
                    number = play.number,
                    amount = play.amount,
                    lotteryName = play.lotteryName.orEmpty(),
                    secondaryLotteryName = "",
                )
            },
        )
    }

    override fun buildOfficialTicketPreviewHtml(ticket: TicketRecord, bancaName: String, securityCode: String): String {
        val spec = getOfficialTicketSpec()
        val formattedDate = formatDate(ticket.createdAtEpochMs)
        val formattedDateTime = formatDateTime(ticket.createdAtEpochMs)
        val drawDate = NativeBitmapExport.formatDrawDateForTicket(ticket.effectiveDrawDateKey(), resolveTicketDrawTimeLabel(ticket))
        val sellerLabel = resolveSellerLabel(ticket)
        val statusLabel = ticketStatusLabel(ticket.status)
        val ticketCode = ticket.serial?.takeIf { it.isNotBlank() } ?: ticket.id
        val summaryItems = listOf(
            "Fecha" to formattedDate,
            "Válido" to drawDate,
            "Hora" to formattedDateTime.substringAfter(' '),
            "Estado" to statusLabel,
            "Cajero" to sellerLabel,
        )
        val grouped = ticket.plays.groupBy { it.lotteryName.orEmpty().ifBlank { "Lotería" } }
        val body = grouped.entries.joinToString("") { (lotteryName, plays) ->
            """
            <div class="ticket-lot">
              <div class="ticket-lot-head">
                <span>${escapeHtml(lotteryName)}</span>
                <strong>${plays.size} jugadas</strong>
              </div>
              ${plays.joinToString("") { play ->
                """
                <div class="ticket-row">
                  <div class="ticket-row-main">
                    <div class="ticket-number">${escapeHtml(play.number)}</div>
                    <div class="ticket-play-meta">
                      <div class="ticket-type">${escapeHtml(play.playType)}</div>
                      <div class="ticket-play-label">${escapeHtml(play.lotteryName.orEmpty().ifBlank { lotteryName })}</div>
                    </div>
                  </div>
                  <div class="ticket-amount">$ ${formatMoney(play.amount)}</div>
                </div>
                """.trimIndent()
              }}
            </div>
            """.trimIndent()
        }
        val summaryHtml = summaryItems.joinToString("") { (label, value) ->
            """
            <div class="ticket-stat">
              <div class="ticket-stat-k">${escapeHtml(label)}</div>
              <div class="ticket-stat-v">${escapeHtml(value)}</div>
            </div>
            """.trimIndent()
        }
        return """
            <div class="ticket-share">
              <style>
                .ticket-share{width:${spec.canvasWidthPx}px;min-height:100%;background:${spec.surface};font-family:'SF Pro Display','Segoe UI',sans-serif;color:#0f172a}
                .ticket-card{margin:0 auto;background:#fff;border:1px solid ${spec.accentGoldSoft};border-radius:10px;overflow:hidden;box-shadow:0 8px 24px rgba(15,23,42,.12)}
                .ticket-head{background:linear-gradient(180deg,${spec.primaryNavy} 0%,#14532d 100%);padding:18px 16px 14px;border-bottom:3px solid ${spec.accentGoldSoft}}
                .ticket-head-top{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}
                .ticket-mark{font-size:10px;font-weight:900;color:#f8df8c;letter-spacing:1.4px;text-transform:uppercase}
                .ticket-name{margin-top:4px;font-size:24px;font-weight:900;line-height:1;color:#fff;text-transform:uppercase}
                .ticket-copy{display:inline-block;font-size:12px;font-weight:900;color:#f8df8c;letter-spacing:1.6px;text-transform:uppercase;border:1px solid rgba(248,223,140,.5);padding:6px 10px;border-radius:8px}
                .ticket-code{margin-top:12px;display:flex;justify-content:space-between;align-items:flex-end;gap:12px}
                .ticket-code-k{font-size:10px;font-weight:900;color:#dbeafe;text-transform:uppercase;letter-spacing:1px}
                .ticket-code-v{font-size:22px;font-weight:900;color:#fff;font-family:'JetBrains Mono','Courier New',monospace}
                .ticket-body{padding:14px}
                .ticket-summary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-bottom:12px}
                .ticket-stat{background:${spec.surfaceAlt};border:1px solid ${spec.outline};border-radius:8px;padding:9px 10px}
                .ticket-stat-k{font-size:10px;font-weight:900;color:#475569;text-transform:uppercase;letter-spacing:.8px}
                .ticket-stat-v{margin-top:4px;font-size:13px;font-weight:900;color:#0f172a}
                .ticket-security{margin-bottom:12px;padding:10px 12px;background:#fefce8;border:1px solid #fcd34d;border-radius:8px}
                .ticket-security-k{font-size:10px;font-weight:900;color:#854d0e;text-transform:uppercase;letter-spacing:.8px}
                .ticket-security-v{margin-top:4px;font-size:18px;font-weight:900;color:#713f12;font-family:'JetBrains Mono','Courier New',monospace}
                .ticket-lot{border-top:1px solid #e2e8f0;padding:12px 0}
                .ticket-lot:first-of-type{border-top:none;padding-top:0}
                .ticket-lot-head{display:flex;justify-content:space-between;align-items:center;gap:10px;font-size:14px;font-weight:900;color:#0f172a;margin-bottom:8px}
                .ticket-lot-head span{display:flex;align-items:center;gap:8px}
                .ticket-lot-head span:before{content:"";display:inline-block;width:12px;height:12px;border-radius:50%;background:#16a34a;box-shadow:0 0 0 2px #dcfce7}
                .ticket-lot-head strong{font-size:11px;font-weight:900;color:#166534;text-transform:uppercase}
                .ticket-row{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:9px 0;border-top:1px solid #f1f5f9}
                .ticket-row:first-of-type{border-top:none;padding-top:0}
                .ticket-row-main{display:flex;align-items:center;gap:10px;min-width:0}
                .ticket-play-meta{min-width:0}
                .ticket-type{font-size:11px;font-weight:900;color:#166534;text-transform:uppercase}
                .ticket-play-label{font-size:11px;font-weight:900;color:#64748b;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
                .ticket-number{display:inline-flex;align-items:center;justify-content:center;min-width:38px;height:38px;padding:0 12px;border-radius:999px;background:#16a34a;color:#fff;font-size:16px;font-weight:900;font-family:'JetBrains Mono','Courier New',monospace;border:2px solid #dcfce7}
                .ticket-amount{font-size:18px;font-weight:900;color:#b7791f;font-family:'JetBrains Mono','Courier New',monospace;white-space:nowrap}
                .ticket-total{display:flex;justify-content:space-between;align-items:center;padding:14px 16px;background:${spec.primaryNavy};color:#fff;border-top:3px solid ${spec.accentGold}}
                .ticket-total-k{font-size:11px;font-weight:900;letter-spacing:1.4px;text-transform:uppercase;color:#f8df8c}
                .ticket-total-v{font-size:30px;font-weight:900;color:#f8df8c;font-family:'JetBrains Mono','Courier New',monospace}
                .ticket-foot{padding:12px 16px 16px;border-top:1px solid #e2e8f0;background:#f8fafc}
                .ticket-foot-line{font-size:11px;font-weight:900;line-height:1.5;color:#334155}
              </style>
              <div class="ticket-card">
                <div class="ticket-head">
                  <div class="ticket-head-top">
                    <div>
                      <div class="ticket-mark">Ticket oficial</div>
                      <div class="ticket-name">${escapeHtml(bancaName)}</div>
                    </div>
                    <div class="ticket-copy">Original</div>
                  </div>
                  <div class="ticket-code">
                    <div>
                      <div class="ticket-code-k">Serial</div>
                      <div class="ticket-code-v">${escapeHtml(ticketCode)}</div>
                    </div>
                    <div>
                      <div class="ticket-code-k">Total</div>
                      <div class="ticket-code-v">$ ${formatMoney(ticket.total)}</div>
                    </div>
                  </div>
                </div>
                <div class="ticket-body">
                  <div class="ticket-summary">$summaryHtml</div>
                  ${
                    if (securityCode.isNotBlank()) {
                        """<div class="ticket-security"><div class="ticket-security-k">Código de seguridad</div><div class="ticket-security-v">${escapeHtml(securityCode)}</div></div>"""
                    } else {
                        ""
                    }
                }
                  $body
                </div>
                <div class="ticket-total">
                  <span class="ticket-total-k">TOTAL</span>
                  <span class="ticket-total-v">$ ${formatMoney(ticket.total)}</span>
                </div>
                <div class="ticket-foot">
                  <div class="ticket-foot-line">Este ticket es válido para el sorteo ${escapeHtml(drawDate)}. Presentar este ticket para cobrar premio.</div>
                  <div class="ticket-foot-line">${escapeHtml(bancaName)} · ${escapeHtml(statusLabel)} · ${ticket.plays.size} jugadas</div>
                </div>
              </div>
            </div>
        """.trimIndent()
    }

    override fun buildTicketWhatsAppShare(ticket: TicketRecord, bancaName: String): ShareEnvelope {
        return ShareEnvelope(
            title = "Ticket ${ticket.id}",
            fileName = "ticket-${ticket.id}.png",
            text = "",
        )
    }

    private fun resolveTicketDrawTimeLabel(ticket: TicketRecord): String? {
        val lotteries = catalogRepository.getAllLotteries()
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

    override fun buildResultsShareText(payload: ResultsSharePayload): String {
        if (payload.rows.isEmpty()) return ""
        val builder = StringBuilder()
        builder.append('*').append(payload.bancaName.ifBlank { "LotteryNet" }).append('*').append('\n')
        builder.append("Resultados - ").append(payload.dateLabel).append('\n')
        payload.rows.forEach { row ->
            val hasPrimaryResults = row.first.isNotBlank() || row.second.isNotBlank() || row.third.isNotBlank()
            builder.append('*').append(row.displayName).append('*')
            if (hasPrimaryResults) {
                builder.append(" - ")
                    .append(row.first.ifBlank { "-" })
                    .append(" | ")
                    .append(row.second.ifBlank { "-" })
                    .append(" | ")
                    .append(row.third.ifBlank { "-" })
            }
            if (!row.pick3.isNullOrBlank()) {
                builder.append(if (hasPrimaryResults) " · " else " - ").append("P3 ").append(row.pick3)
            }
            if (!row.pick4.isNullOrBlank()) {
                builder.append(if (hasPrimaryResults || !row.pick3.isNullOrBlank()) " · " else " - ").append("P4 ").append(row.pick4)
            }
            row.drawTimeLabel?.takeIf { it.isNotBlank() }?.let {
                builder.append(" · Sorteo ").append(it)
            }
            row.stateLabel?.takeIf { it.isNotBlank() }?.let {
                builder.append(" · ").append(it)
            }
            row.source?.takeIf { it.isNotBlank() }?.let {
                builder.append(" · ").append(it)
            }
            builder.append('\n')
        }
        return builder.toString().trimEnd()
    }

    override fun buildResultsWhatsAppShare(payload: ResultsSharePayload): ShareEnvelope {
        val slug = payload.dateLabel.replace(Regex("[^\\dA-Za-z]+"), "-").trim('-').lowercase()
        return ShareEnvelope(
            title = "Resultados ${payload.bancaName.ifBlank { "LotteryNet" }}",
            fileName = "resultados-${if (slug.isBlank()) "hoy" else slug}.png",
            text = "Resultados ${payload.bancaName.ifBlank { "LotteryNet" }}",
        )
    }

    override fun buildResultsShareRows(rows: List<ResultShareRow>): List<ResultShareRow> {
        return rows
    }

    override fun mapLotteryResultsToShareRows(results: List<LotteryResult>): List<ResultShareRow> {
        return results.map { result ->
            val lottery = catalogRepository.getLotteryById(result.lotteryId)
                ?: result.lotteryName?.let { catalogRepository.getLotteryByName(it) }
            ResultShareRow(
                displayName = result.lotteryName?.ifBlank { result.lotteryId } ?: result.lotteryId,
                first = result.first.orEmpty(),
                second = result.second.orEmpty(),
                third = result.third.orEmpty(),
                pick3 = result.pick3?.takeIf { it.isNotBlank() },
                pick4 = result.pick4?.takeIf { it.isNotBlank() },
                source = result.source?.takeIf { it.isNotBlank() },
                accentColor = resultAccentColor(result.lotteryName ?: result.lotteryId),
                logoAssetPath = lottery?.logoAssetPath,
                drawTimeLabel = null,
                stateLabel = null,
            )
        }
    }

    private fun formatDate(epochMs: Long): String {
        val format = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("America/Santo_Domingo")
        return format.format(java.util.Date(epochMs))
    }

    private fun formatDateTime(epochMs: Long): String {
        val format = java.text.SimpleDateFormat("dd-MM-yyyy hh:mm a", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("America/Santo_Domingo")
        return format.format(java.util.Date(epochMs))
    }

    private fun formatMoney(amount: Double): String {
        return com.lotterynet.pro.core.format.formatWholeAmount(amount)
    }

    private fun resolveSellerLabel(ticket: TicketRecord): String {
        return ticket.sellerUser?.takeIf { it.isNotBlank() }
            ?: ticket.adminUser?.takeIf { it.isNotBlank() }
            ?: "Sin usuario"
    }

    private fun ticketStatusLabel(status: String): String {
        if (isPaidTicketStatus(status)) return "Pagado"
        return when (status.lowercase(java.util.Locale.getDefault())) {
            "winner" -> "Ganador"
            "voided", "invalid" -> "Anulado"
            else -> "Activo"
        }
    }

    private fun escapeHtml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun resultAccentColor(seed: String): String {
        val palette = listOf(
            "#1747d1",
            "#0d9488",
            "#16a34a",
            "#ca8a04",
            "#9333ea",
            "#ea580c",
        )
        val index = (seed.lowercase().hashCode() and Int.MAX_VALUE) % palette.size
        return palette[index]
    }
}
