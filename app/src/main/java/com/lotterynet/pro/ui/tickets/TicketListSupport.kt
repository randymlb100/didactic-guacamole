package com.lotterynet.pro.ui.tickets

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lotterynet.pro.core.model.ActiveSession
import com.lotterynet.pro.core.model.LotteryCatalogItem
import com.lotterynet.pro.core.model.PlayItem
import com.lotterynet.pro.core.model.TicketRecord
import com.lotterynet.pro.core.model.isPaidTicketStatus
import com.lotterynet.pro.core.model.isPendingWinnerTicketStatus
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.core.operations.buildUserActorLabelLookup
import com.lotterynet.pro.core.operations.filterCashiersForSession
import com.lotterynet.pro.core.operations.filterTicketsForOperationalScope
import com.lotterynet.pro.core.operations.resolveTicketActorLabel
import com.lotterynet.pro.core.sync.filterServerVisibleTickets
import com.lotterynet.pro.ui.common.ActionTone
import com.lotterynet.pro.ui.common.CompactActionButton
import com.lotterynet.pro.ui.common.CompactAdaptiveGrid
import com.lotterynet.pro.ui.common.LotteryNetWindowMode
import com.lotterynet.pro.ui.common.gainColor
import com.lotterynet.pro.ui.common.lossColor
import com.lotterynet.pro.ui.common.lotteryNetTextFieldColors
import com.lotterynet.pro.ui.common.rememberLotteryNetVisualSpec
import com.lotterynet.pro.ui.common.resolveOverflowLayoutContract
import com.lotterynet.pro.ui.common.warningColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal enum class TicketOwnerScope(val label: String) {
    ALL("Todos"),
    ADMIN("Admin"),
    CASHIER("Cajero"),
}

internal enum class TicketQuickDatePreset(val id: String, val label: String) {
    TODAY("today", "Hoy"),
    YESTERDAY("yesterday", "Ayer"),
    LAST_4_HOURS("last4h", "Ultimas 4 horas"),
}

internal enum class TicketSummaryPeriod(val id: String, val label: String) {
    TODAY("today", "Hoy"),
    YESTERDAY("yesterday", "Ayer"),
    WEEK("week", "Semana"),
    QUINZA("quinza", "Quincena"),
    MONTH("month", "Mes"),
    ALL("all", "Todo"),
}

internal enum class TicketStatusBucket(val id: String, val label: String) {
    ALL("all", "Todos"),
    PLAYED("played", "Jugados"),
    WINNER("winner", "Ganadores"),
    PAID("paid", "Pagados"),
    NULLED("nulled", "Anulados"),
}

internal data class TicketDetailRow(
    val id: String,
    val ticket: TicketRecord,
    val play: PlayItem,
)

internal data class TicketDetailGroup(
    val id: String,
    val ticket: TicketRecord,
    val plays: List<PlayItem>,
)

internal data class TicketDirectory(
    val session: ActiveSession,
    val tickets: List<TicketRecord>,
    val adminKeys: Set<String>,
    val cashierOptions: List<UserAccount>,
    val actorLabelsByKey: Map<String, String> = emptyMap(),
)

internal data class TicketSummaryMetrics(
    val visibleCount: Int,
    val visibleTotal: Double,
    val activeTotal: Double,
)

internal data class TicketSummaryInitialFilters(
    val ownerScope: TicketOwnerScope = TicketOwnerScope.ALL,
    val cashierKey: String = "",
)

internal data class TicketSummaryPrimaryAction(
    val label: String,
    val mode: String,
)

internal data class TicketCollectionLayoutContract(
    val filtersCollapsedByDefault: Boolean,
    val collapseSecondarySummaryFilters: Boolean,
    val useCompactRows: Boolean,
    val inlinePrimaryNumbers: Boolean,
    val minTouchTargetDp: Int,
    val metricColumns: Int,
    val listSpacingDp: Int,
    val filterRowSpacingDp: Int,
    val headerPaddingVerticalDp: Int,
    val rowPaddingVerticalDp: Int,
)

internal data class TicketOpenResolution(
    val ticket: TicketRecord?,
    val message: String?,
) {
    val canOpen: Boolean
        get() = ticket != null
}

internal data class CompactDropdownOption(
    val value: String,
    val label: String,
)

private val santoDomingoTimeZone: TimeZone = TimeZone.getTimeZone("America/Santo_Domingo")

private val playTypeLabels = linkedMapOf(
    "" to "Todos tipos",
    "Q" to "Quiniela",
    "P" to "Pale",
    "T" to "Tripleta",
    "SP" to "Super Pale",
    "P3" to "Pick 3 Straight",
    "P3BOX" to "Pick 3 Box",
    "P4" to "Pick 4 Straight",
    "P4BOX" to "Pick 4 Box",
    "P3B" to "Pick 3 Back Pair",
    "P4B" to "Pick 4 Back Pair",
)

internal fun resolveTicketCollectionLayout(windowMode: LotteryNetWindowMode): TicketCollectionLayoutContract {
    return when (windowMode) {
        LotteryNetWindowMode.POS_TIGHT -> TicketCollectionLayoutContract(
            filtersCollapsedByDefault = true,
            collapseSecondarySummaryFilters = true,
            useCompactRows = true,
            inlinePrimaryNumbers = true,
            minTouchTargetDp = 44,
            metricColumns = 2,
            listSpacingDp = 3,
            filterRowSpacingDp = 3,
            headerPaddingVerticalDp = 4,
            rowPaddingVerticalDp = 4,
        )

        LotteryNetWindowMode.POS -> TicketCollectionLayoutContract(
            filtersCollapsedByDefault = true,
            collapseSecondarySummaryFilters = true,
            useCompactRows = true,
            inlinePrimaryNumbers = true,
            minTouchTargetDp = 44,
            metricColumns = 3,
            listSpacingDp = 4,
            filterRowSpacingDp = 4,
            headerPaddingVerticalDp = 5,
            rowPaddingVerticalDp = 5,
        )

        else -> TicketCollectionLayoutContract(
            filtersCollapsedByDefault = false,
            collapseSecondarySummaryFilters = false,
            useCompactRows = false,
            inlinePrimaryNumbers = true,
            minTouchTargetDp = 44,
            metricColumns = 3,
            listSpacingDp = 6,
            filterRowSpacingDp = 7,
            headerPaddingVerticalDp = 9,
            rowPaddingVerticalDp = 8,
        )
    }
}

internal fun buildTicketDirectory(
    session: ActiveSession,
    allTickets: List<TicketRecord>,
    allCashiers: List<UserAccount>,
): TicketDirectory {
    val adminKeys = identityKeys(session.userId, session.username, session.adminId, session.adminUser)
    val visibleCashiers = when (session.role) {
        UserRole.ADMIN,
        UserRole.MASTER -> filterCashiersForSession(session, allCashiers)
        else -> emptyList()
    }
    val baseTickets = filterServerVisibleTickets(
        filterTicketsForOperationalScope(session, allTickets, allCashiers),
    )
    return TicketDirectory(
        session = session,
        tickets = baseTickets.sortedByDescending { it.createdAtEpochMs },
        adminKeys = adminKeys,
        cashierOptions = visibleCashiers,
        actorLabelsByKey = buildUserActorLabelLookup(allCashiers),
    )
}

internal fun resolveTicketSummaryInitialFilters(
    ownerScopeRaw: String?,
    cashierKeyRaw: String?,
): TicketSummaryInitialFilters {
    val scope = TicketOwnerScope.entries.firstOrNull {
        it.name.equals(ownerScopeRaw.orEmpty(), ignoreCase = true)
    } ?: TicketOwnerScope.ALL
    val cashierKey = if (scope == TicketOwnerScope.CASHIER) cashierKeyRaw.orEmpty().trim() else ""
    return TicketSummaryInitialFilters(ownerScope = scope, cashierKey = cashierKey)
}

internal fun buildTicketDetailRows(tickets: List<TicketRecord>): List<TicketDetailRow> {
    return tickets
        .flatMap { ticket ->
            ticket.plays.mapIndexed { index, play ->
                TicketDetailRow(
                    id = "${ticket.id}:$index:${play.playType}:${play.number}",
                    ticket = ticket,
                    play = play,
                )
            }
        }
        .sortedByDescending { it.ticket.createdAtEpochMs }
}

internal fun groupTicketDetailRows(rows: List<TicketDetailRow>): List<TicketDetailGroup> {
    return rows
        .groupBy { it.ticket.id }
        .values
        .map { ticketRows ->
            val first = ticketRows.first()
            TicketDetailGroup(
                id = first.ticket.id,
                ticket = first.ticket,
                plays = ticketRows.map { it.play },
            )
        }
        .sortedByDescending { it.ticket.createdAtEpochMs }
}

internal fun filterSummaryTickets(
    directory: TicketDirectory,
    statusBucket: String,
    lotteryName: String,
    ownerScope: TicketOwnerScope,
    cashierKey: String,
    query: String,
    fromDateTime: String,
    toDateTime: String,
): List<TicketRecord> {
    val normalizedBucket = statusBucket.trim().lowercase(Locale.getDefault())
    val normalizedLottery = lotteryName.trim().lowercase(Locale.getDefault())
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val fromEpoch = parseDateFilter(fromDateTime, endOfRange = false)
    val toEpoch = parseDateFilter(toDateTime, endOfRange = true)
    return directory.tickets.filter { ticket ->
        matchesSummaryScope(ticket, directory, ownerScope, cashierKey) &&
            matchesStatusBucket(ticket, normalizedBucket) &&
            matchesDateRange(ticket.createdAtEpochMs, fromEpoch, toEpoch) &&
            (normalizedLottery.isBlank() || ticket.plays.any { play ->
                play.lotteryName.orEmpty().lowercase(Locale.getDefault()) == normalizedLottery
            }) &&
            matchesTicketQuery(ticket, normalizedQuery)
    }
}

internal fun filterDetailRows(
    rows: List<TicketDetailRow>,
    directory: TicketDirectory,
    playType: String,
    lotteryName: String,
    ownerScope: TicketOwnerScope,
    cashierKey: String,
    query: String,
    fromDateTime: String,
    toDateTime: String,
): List<TicketDetailRow> {
    val normalizedType = playType.trim().lowercase(Locale.getDefault())
    val normalizedLottery = lotteryName.trim().lowercase(Locale.getDefault())
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val fromEpoch = parseDateFilter(fromDateTime, endOfRange = false)
    val toEpoch = parseDateFilter(toDateTime, endOfRange = true)
    return rows.filter { row ->
            matchesSummaryScope(row.ticket, directory, ownerScope, cashierKey) &&
            matchesDateRange(row.ticket.createdAtEpochMs, fromEpoch, toEpoch) &&
            (normalizedType.isBlank() || row.play.playType.lowercase(Locale.getDefault()) == normalizedType) &&
            (normalizedLottery.isBlank() || row.play.lotteryName.orEmpty().lowercase(Locale.getDefault()) == normalizedLottery) &&
            matchesDetailQuery(row, normalizedQuery)
    }
}

internal fun buildSummaryMetrics(tickets: List<TicketRecord>): TicketSummaryMetrics {
    val visibleTotal = tickets.filterNot(::ticketExcludedFromTotals).sumOf { it.total }
    val activeTotal = tickets.filter { ticketStatusBucket(it) == TicketStatusBucket.PLAYED.id }.sumOf { it.total }
    return TicketSummaryMetrics(
        visibleCount = tickets.size,
        visibleTotal = visibleTotal,
        activeTotal = activeTotal,
    )
}

internal fun buildLotteryOptions(tickets: List<TicketRecord>): List<CompactDropdownOption> {
    return buildLotteryOptions(tickets, emptyList())
}

internal fun buildLotteryOptions(
    tickets: List<TicketRecord>,
    catalogLotteries: List<LotteryCatalogItem>,
): List<CompactDropdownOption> {
    val names = buildList {
        addAll(catalogLotteries.map { it.name })
        addAll(tickets.flatMap { ticket -> ticket.plays.mapNotNull { it.lotteryName?.trim() } })
    }
    return listOf(CompactDropdownOption("", "Todas loterias")) +
        names
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
            .map { CompactDropdownOption(it, it) }
}

internal fun buildTicketMonthOptions(): List<CompactDropdownOption> {
    val labels = listOf(
        "Enero",
        "Febrero",
        "Marzo",
        "Abril",
        "Mayo",
        "Junio",
        "Julio",
        "Agosto",
        "Septiembre",
        "Octubre",
        "Noviembre",
        "Diciembre",
    )
    return labels.mapIndexed { index, label ->
        CompactDropdownOption((index + 1).toString().padStart(2, '0'), label)
    }
}

internal fun resolveTicketSummaryDateRange(
    periodId: String,
    monthValue: String,
    nowEpochMs: Long = System.currentTimeMillis(),
): Pair<String, String> {
    val now = santoDomingoCalendar(nowEpochMs)
    val from = santoDomingoCalendar(nowEpochMs)
    val to = santoDomingoCalendar(nowEpochMs)
    when (TicketSummaryPeriod.entries.firstOrNull { it.id == periodId } ?: TicketSummaryPeriod.TODAY) {
        TicketSummaryPeriod.TODAY -> startOfDay(from)
        TicketSummaryPeriod.YESTERDAY -> {
            from.add(Calendar.DAY_OF_YEAR, -1)
            startOfDay(from)
            to.timeInMillis = from.timeInMillis
            endOfDay(to)
        }
        TicketSummaryPeriod.WEEK -> {
            from.add(Calendar.DAY_OF_YEAR, -6)
            startOfDay(from)
        }
        TicketSummaryPeriod.QUINZA -> {
            from.add(Calendar.DAY_OF_YEAR, -14)
            startOfDay(from)
        }
        TicketSummaryPeriod.MONTH -> {
            val selectedMonth = monthValue.toIntOrNull()?.coerceIn(1, 12) ?: (now.get(Calendar.MONTH) + 1)
            from.set(Calendar.MONTH, selectedMonth - 1)
            from.set(Calendar.DAY_OF_MONTH, 1)
            startOfDay(from)
            to.timeInMillis = from.timeInMillis
            to.set(Calendar.DAY_OF_MONTH, to.getActualMaximum(Calendar.DAY_OF_MONTH))
            endOfDay(to)
        }
        TicketSummaryPeriod.ALL -> return "" to ""
    }
    return formatDateTimeFilter(from.timeInMillis) to formatDateTimeFilter(to.timeInMillis)
}

internal fun buildPlayTypeOptions(rows: List<TicketDetailRow>): List<CompactDropdownOption> {
    val available = rows.map { it.play.playType.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.uppercase(Locale.getDefault()) }
        .sortedBy { playTypeLabels[it] ?: it }
    return listOf(CompactDropdownOption("", playTypeLabels.getValue(""))) +
        available.map { type -> CompactDropdownOption(type, playTypeLabel(type)) }
}

internal fun buildCashierOptions(cashiers: List<UserAccount>): List<CompactDropdownOption> {
    return listOf(CompactDropdownOption("", "Todos los cajeros")) +
        cashiers
            .distinctBy { "${it.id.lowercase(Locale.getDefault())}|${it.user.lowercase(Locale.getDefault())}" }
            .sortedBy { cashierDisplayLabel(it).lowercase(Locale.getDefault()) }
            .map { account ->
                CompactDropdownOption(
                    value = account.id.takeIf { it.isNotBlank() } ?: account.user,
                    label = cashierDisplayLabel(account),
                )
            }
}

internal fun applyQuickPreset(preset: TicketQuickDatePreset, nowEpochMs: Long = System.currentTimeMillis()): Pair<String, String> {
    val now = santoDomingoCalendar(nowEpochMs)
    val from = santoDomingoCalendar(nowEpochMs)
    val to = santoDomingoCalendar(nowEpochMs)
    when (preset) {
        TicketQuickDatePreset.TODAY -> {
            from.set(Calendar.HOUR_OF_DAY, 0)
            from.set(Calendar.MINUTE, 0)
            from.set(Calendar.SECOND, 0)
            from.set(Calendar.MILLISECOND, 0)
        }

        TicketQuickDatePreset.YESTERDAY -> {
            from.add(Calendar.DAY_OF_YEAR, -1)
            from.set(Calendar.HOUR_OF_DAY, 0)
            from.set(Calendar.MINUTE, 0)
            from.set(Calendar.SECOND, 0)
            from.set(Calendar.MILLISECOND, 0)
            to.timeInMillis = from.timeInMillis
            to.set(Calendar.HOUR_OF_DAY, 23)
            to.set(Calendar.MINUTE, 59)
            to.set(Calendar.SECOND, 0)
            to.set(Calendar.MILLISECOND, 0)
        }

        TicketQuickDatePreset.LAST_4_HOURS -> {
            from.add(Calendar.HOUR_OF_DAY, -4)
        }
    }
    return formatDateTimeFilter(from.timeInMillis) to formatDateTimeFilter(
        when (preset) {
            TicketQuickDatePreset.YESTERDAY -> to.timeInMillis
            else -> now.timeInMillis
        }
    )
}

internal fun ticketListStatusLabel(status: String): String {
    if (isPaidTicketStatus(status)) return "Pagado"
    return when (status.lowercase(Locale.getDefault())) {
        "winner" -> "Ganador"
        "voided", "nulled" -> "Anulado"
        "invalid" -> "Invalido"
        "deleted", "borrado", "removed" -> "Borrado"
        else -> "Activo"
    }
}

internal fun ticketStatusTone(status: String): Color {
    if (isPaidTicketStatus(status)) return gainColor()
    return when (status.lowercase(Locale.getDefault())) {
        "winner" -> warningColor()
        "voided", "nulled", "invalid", "deleted", "borrado", "removed" -> lossColor()
        else -> Color(0xFF475569)
    }
}

internal fun ticketOwnerLabel(
    ticket: TicketRecord,
    actorLabelsByKey: Map<String, String> = emptyMap(),
): String {
    return resolveTicketActorLabel(ticket, actorLabelsByKey, fallback = "sin usuario")
}

internal fun ticketLotteriesLabel(ticket: TicketRecord): String {
    return ticket.plays
        .mapNotNull { it.lotteryName?.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.getDefault()) }
        .joinToString(" · ")
        .ifBlank { "Sin loteria" }
}

internal fun ticketNumbersLabel(ticket: TicketRecord): String {
    return ticket.plays.joinToString(" · ") { play ->
        buildString {
            append(play.playType.ifBlank { "-" })
            append(" ")
            append(play.number)
        }
    }
}

internal fun ticketDateTime(epochMs: Long): String {
    return santoDomingoFormatter("dd/MM/yyyy hh:mm a").format(Date(epochMs))
}

internal fun formatDateTimeFilter(epochMs: Long): String {
    return santoDomingoFormatter("yyyy-MM-dd'T'HH:mm").format(Date(epochMs))
}

internal fun formatTicketMoney(value: Double): String {
    return com.lotterynet.pro.core.format.formatWholeMoney(value)
}

internal fun playTypeLabel(playType: String): String {
    return playTypeLabels[playType.trim().uppercase(Locale.getDefault())] ?: playType.ifBlank { "-" }
}

internal fun ticketStatusBucket(status: String): String {
    return when (status.lowercase(Locale.getDefault())) {
        TicketStatusBucket.WINNER.id -> TicketStatusBucket.WINNER.id
        TicketStatusBucket.PAID.id -> TicketStatusBucket.PAID.id
        "voided", "invalid", "nulled", "deleted", "borrado", "removed" -> TicketStatusBucket.NULLED.id
        else -> TicketStatusBucket.PLAYED.id
    }
}

internal fun ticketStatusBucket(ticket: TicketRecord): String {
    if (isPaidTicketStatus(ticket.status)) return TicketStatusBucket.PAID.id
    if (ticketHasPendingPrize(ticket)) return TicketStatusBucket.WINNER.id
    return ticketStatusBucket(ticket.status)
}

internal fun resolveTicketSummaryPrimaryAction(ticket: TicketRecord): TicketSummaryPrimaryAction {
    return if (ticketHasPendingPrize(ticket)) {
        TicketSummaryPrimaryAction(label = "Cobrar", mode = "pagar")
    } else {
        TicketSummaryPrimaryAction(label = "Abrir", mode = "buscar")
    }
}

internal fun ticketExcludedFromTotals(ticket: TicketRecord): Boolean {
    return ticketStatusBucket(ticket) == TicketStatusBucket.NULLED.id
}

internal fun canRepeatTicket(ticket: TicketRecord, deletedTicketIds: Set<String> = emptySet()): Boolean {
    return !isDeletedTicket(ticket, deletedTicketIds) &&
        ticketStatusBucket(ticket.status) != TicketStatusBucket.NULLED.id
}

internal fun resolveTicketOpenRequest(
    requestedTicket: TicketRecord,
    currentTickets: List<TicketRecord>,
    deletedTicketIds: Set<String>,
): TicketOpenResolution {
    if (isDeletedTicket(requestedTicket, deletedTicketIds)) {
        return TicketOpenResolution(null, STALE_TICKET_MESSAGE)
    }
    val currentTicket = currentTickets.firstOrNull { it.id == requestedTicket.id }
        ?: return TicketOpenResolution(null, STALE_TICKET_MESSAGE)
    if (filterServerVisibleTickets(listOf(currentTicket)).isEmpty()) {
        return TicketOpenResolution(null, STALE_TICKET_MESSAGE)
    }
    if (isDeletedTicket(currentTicket, deletedTicketIds)) {
        return TicketOpenResolution(null, STALE_TICKET_MESSAGE)
    }
    return TicketOpenResolution(currentTicket, null)
}

internal const val STALE_TICKET_MESSAGE = "Ese ticket ya no existe o fue borrado."

private fun isDeletedTicket(ticket: TicketRecord, deletedTicketIds: Set<String>): Boolean {
    return ticket.id in deletedTicketIds
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CompactFilterDropdown(
    label: String,
    selectedValue: String,
    options: List<CompactDropdownOption>,
    onValueSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val visual = rememberLotteryNetVisualSpec()
    val overflow = resolveOverflowLayoutContract(visual.windowMode)
    val tight = visual.windowMode == LotteryNetWindowMode.POS_TIGHT
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label ?: options.firstOrNull()?.label.orEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = if (tight) 42.dp else 46.dp)
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = if (tight) null else {
                {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = visual.colors.muted,
                    )
                }
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    tint = visual.colors.muted,
                )
            },
            shape = RoundedCornerShape(visual.sizes.panelRadius),
            colors = lotteryNetTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = overflow.dropdownMaxHeight),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (option.value == selectedValue) {
                                Text(
                                    text = "Activo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = visual.colors.gain,
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onValueSelected(option.value)
                    },
                )
            }
        }
    }
}

@Composable
internal fun TicketDateTimeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val visual = rememberLotteryNetVisualSpec()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = visual.colors.panelAlt,
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = visual.colors.muted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value.ifBlank { "Seleccionar fecha y hora" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (value.isBlank()) visual.colors.muted else visual.colors.ink,
                    )
                    Icon(
                        imageVector = Icons.Rounded.Event,
                        contentDescription = null,
                        tint = visual.colors.neutral,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        val calendar = parseDateFilter(value, endOfRange = false)?.let(::santoDomingoCalendar)
                            ?: santoDomingoCalendar()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val dateCalendar = santoDomingoCalendar(calendar.timeInMillis).apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                }
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        dateCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                        dateCalendar.set(Calendar.MINUTE, minute)
                                        dateCalendar.set(Calendar.SECOND, 0)
                                        dateCalendar.set(Calendar.MILLISECOND, 0)
                                        onValueChange(formatDateTimeFilter(dateCalendar.timeInMillis))
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    false,
                                ).show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH),
                        ).show()
                    }
                    .background(Color.Transparent),
            )
        }
    }
}

@Composable
internal fun TicketQuickPresetRow(
    selectedPreset: String,
    onPresetSelected: (TicketQuickDatePreset) -> Unit,
    onClear: () -> Unit,
) {
    CompactAdaptiveGrid(
        itemCount = TicketQuickDatePreset.entries.size + 1,
        modifier = Modifier.fillMaxWidth(),
        columns = 2,
    ) { index, itemModifier ->
        if (index < TicketQuickDatePreset.entries.size) {
            val preset = TicketQuickDatePreset.entries[index]
            CompactActionButton(
                label = preset.label,
                onClick = { onPresetSelected(preset) },
                active = selectedPreset == preset.id,
                tone = if (selectedPreset == preset.id) ActionTone.Primary else ActionTone.Secondary,
                modifier = itemModifier,
            )
        } else {
            CompactActionButton(
                label = "Limpiar",
                onClick = onClear,
                tone = ActionTone.Secondary,
                modifier = itemModifier,
            )
        }
    }
}

@Composable
internal fun TicketOwnerScopeRow(
    ownerScope: TicketOwnerScope,
    onOwnerScopeChange: (TicketOwnerScope) -> Unit,
) {
    CompactAdaptiveGrid(
        itemCount = TicketOwnerScope.entries.size,
        modifier = Modifier.fillMaxWidth(),
        columns = TicketOwnerScope.entries.size,
    ) { index, itemModifier ->
        val scope = TicketOwnerScope.entries[index]
        CompactActionButton(
            label = scope.label,
            onClick = { onOwnerScopeChange(scope) },
            active = ownerScope == scope,
            tone = if (ownerScope == scope) ActionTone.Primary else ActionTone.Secondary,
            modifier = itemModifier,
        )
    }
}

private fun resolveAdminCashiers(session: ActiveSession, cashiers: List<UserAccount>): List<UserAccount> {
    return cashiers.filter { account ->
        account.role == UserRole.CASHIER && (
            equalsIgnoreCase(account.adminId, session.userId) ||
                equalsIgnoreCase(account.adminUser, session.username) ||
                (!session.banca.isNullOrBlank() && equalsIgnoreCase(account.banca, session.banca))
            )
    }
}

private fun matchesSummaryScope(
    ticket: TicketRecord,
    directory: TicketDirectory,
    ownerScope: TicketOwnerScope,
    cashierKey: String,
): Boolean {
    if (directory.session.role != UserRole.ADMIN) return true
    val cashierKeys = identityKeys(cashierKey)
    val allCashierKeys = directory.cashierOptions.flatMapTo(mutableSetOf()) { account ->
        identityKeys(account.id, account.user)
    }
    return when (ownerScope) {
        TicketOwnerScope.ALL -> true
        TicketOwnerScope.ADMIN -> matchesAdminOwnedTicket(ticket, directory.adminKeys, allCashierKeys)
        TicketOwnerScope.CASHIER -> {
            if (cashierKeys.isNotEmpty()) {
                matchesTicketSeller(ticket, cashierKeys)
            } else {
                ticket.role == UserRole.CASHIER || matchesTicketSeller(ticket, allCashierKeys)
            }
        }
    }
}

private fun matchesAdminOwnedTicket(
    ticket: TicketRecord,
    adminKeys: Set<String>,
    cashierKeys: Set<String>,
): Boolean {
    val directAdmin = ticket.role == UserRole.ADMIN ||
        matchesTicketSeller(ticket, adminKeys) ||
        matchesTicketAdmin(ticket, adminKeys)
    if (!directAdmin) return false
    return !matchesTicketSeller(ticket, cashierKeys)
}

private fun matchesTicketAdmin(ticket: TicketRecord, keys: Set<String>): Boolean {
    if (keys.isEmpty()) return false
    return normalizeKey(ticket.adminId) in keys || normalizeKey(ticket.adminUser) in keys
}

private fun matchesTicketSeller(ticket: TicketRecord, keys: Set<String>): Boolean {
    if (keys.isEmpty()) return false
    return normalizeKey(ticket.sellerId) in keys || normalizeKey(ticket.sellerUser) in keys
}

private fun matchesStatusBucket(ticket: TicketRecord, selectedBucket: String): Boolean {
    if (selectedBucket.isBlank() || selectedBucket == TicketStatusBucket.ALL.id) return true
    return ticketStatusBucket(ticket) == selectedBucket
}

private fun ticketHasPendingPrize(ticket: TicketRecord): Boolean {
    if (isPaidTicketStatus(ticket.status)) return false
    if (ticketStatusBucket(ticket.status) == TicketStatusBucket.NULLED.id) return false
    return isPendingWinnerTicketStatus(ticket.status) || ticket.totalPrize > 0.0
}

private fun matchesDateRange(epochMs: Long, fromEpoch: Long?, toEpoch: Long?): Boolean {
    if (fromEpoch != null && epochMs < fromEpoch) return false
    if (toEpoch != null && epochMs > toEpoch) return false
    return true
}

private fun matchesTicketQuery(ticket: TicketRecord, normalizedQuery: String): Boolean {
    if (normalizedQuery.isBlank()) return true
    val haystack = buildList {
        add(ticket.id)
        add(ticket.serial.orEmpty())
        add(ticketOwnerLabel(ticket))
        add(ticket.adminUser.orEmpty())
        add(ticket.sellerUser.orEmpty())
        add(ticketLotteriesLabel(ticket))
        add(ticketNumbersLabel(ticket))
    }.joinToString(" ").lowercase(Locale.getDefault())
    return normalizedQuery in haystack
}

private fun matchesDetailQuery(row: TicketDetailRow, normalizedQuery: String): Boolean {
    if (normalizedQuery.isBlank()) return true
    val haystack = buildList {
        add(row.ticket.id)
        add(row.ticket.serial.orEmpty())
        add(ticketOwnerLabel(row.ticket))
        add(row.ticket.adminUser.orEmpty())
        add(row.ticket.sellerUser.orEmpty())
        add(row.play.lotteryName.orEmpty())
        add(playTypeLabel(row.play.playType))
        add(row.play.playType)
        add(row.play.number)
    }.joinToString(" ").lowercase(Locale.getDefault())
    return normalizedQuery in haystack
}

private fun parseDateFilter(raw: String, endOfRange: Boolean): Long? {
    val value = raw.trim().takeIf { it.isNotBlank() } ?: return null
    val formatters = listOf("yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")
    return formatters.firstNotNullOfOrNull { pattern ->
        runCatching {
            val formatter = santoDomingoFormatter(pattern)
            val parsed = formatter.parse(value) ?: return@runCatching null
            if (pattern == "yyyy-MM-dd" && endOfRange) {
                parsed.time + (24L * 60L * 60L * 1000L) - 1L
            } else {
                parsed.time
            }
        }.getOrNull()
    }
}

private fun santoDomingoFormatter(pattern: String): SimpleDateFormat {
    return SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = santoDomingoTimeZone
    }
}

private fun santoDomingoCalendar(epochMs: Long = System.currentTimeMillis()): Calendar {
    return Calendar.getInstance(santoDomingoTimeZone, Locale.US).apply {
        timeInMillis = epochMs
    }
}

private fun startOfDay(calendar: Calendar) {
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
}

private fun endOfDay(calendar: Calendar) {
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
}

private fun cashierDisplayLabel(account: UserAccount): String {
    return account.displayName?.takeIf { it.isNotBlank() }
        ?: account.user
}

private fun identityKeys(vararg values: String?): Set<String> {
    return values.mapNotNullTo(mutableSetOf()) { normalizeKey(it) }
}

private fun normalizeKey(value: String?): String? {
    return value?.trim()?.lowercase(Locale.getDefault())?.takeIf { it.isNotBlank() }
}

private fun equalsIgnoreCase(left: String?, right: String?): Boolean {
    return left?.trim()?.equals(right?.trim(), ignoreCase = true) == true
}
