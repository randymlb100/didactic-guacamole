package com.lotterynet.pro.ui.servicesgames

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lotterynet.pro.core.auth.SupabaseSessionTokenProvider
import com.lotterynet.pro.core.servicesgames.ServicesGamesBackendClient
import com.lotterynet.pro.core.storage.LocalSessionRepository
import com.lotterynet.pro.ui.theme.LotteryNetComposeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class ServicesGamesReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = LocalSessionRepository(this).getActiveSession()
        if (session == null) {
            finish()
            return
        }
        val tokenProvider = SupabaseSessionTokenProvider(LocalSessionRepository(this))
        setContent {
            LotteryNetComposeTheme {
                ServicesGamesReportRoute(
                    onLoad = {
                        withContext(Dispatchers.IO) {
                            ServicesGamesBackendClient().report(
                                module = it.module,
                                from = it.from,
                                to = it.to,
                                bearerToken = tokenProvider.freshAccessToken(),
                            )
                        }
                    },
                    onBack = { finish() },
                    onError = { Toast.makeText(this@ServicesGamesReportActivity, it, Toast.LENGTH_LONG).show() },
                )
            }
        }
    }
}

private data class ReportRow(
    val id: String,
    val module: String,
    val provider: String,
    val product: String,
    val amount: String,
    val commission: String,
    val status: String,
    val createdAt: String,
    val actorLabel: String,
    val actorRole: String,
)

private data class ReportFilter(
    val module: com.lotterynet.pro.core.servicesgames.ServicesGamesModule?,
    val from: String,
    val to: String,
)

@androidx.compose.runtime.Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ServicesGamesReportRoute(
    onLoad: suspend (ReportFilter) -> JSONObject,
    onBack: () -> Unit,
    onError: (String) -> Unit,
) {
    var status by remember { mutableStateOf("Cargando reporte separado...") }
    var rows by remember { mutableStateOf(emptyList<ReportRow>()) }
    var selectedDateKey by rememberSaveable { mutableStateOf(LocalDate.now(ZoneId.of("America/Santo_Domingo")).toString()) }
    var selectedModule by rememberSaveable { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val selectedDate = remember(selectedDateKey) { runCatching { LocalDate.parse(selectedDateKey) }.getOrElse { LocalDate.now() } }
    val filter = remember(selectedDate, selectedModule) { reportFilter(selectedDate, selectedModule) }
    LaunchedEffect(filter) {
        status = "Cargando historial..."
        runCatching { onLoad(filter) }
            .onSuccess { response ->
                rows = parseReport(response)
                status = "${rows.size} operación(es) del ${formatDate(selectedDate)}."
            }
            .onFailure {
                status = it.message ?: "No se pudo cargar el reporte."
                onError(status)
            }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reporte de servicios") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Historial por día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Event, contentDescription = null)
                        Text("  ${formatDate(selectedDate)}")
                    }
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = selectedModule == null,
                            onClick = { selectedModule = null },
                            label = { Text("Todo") },
                        )
                        FilterChip(
                            selected = selectedModule == "services",
                            onClick = { selectedModule = "services" },
                            label = { Text("Servicios") },
                        )
                        FilterChip(
                            selected = selectedModule == "video_games",
                            onClick = { selectedModule = "video_games" },
                            label = { Text("Juegos") },
                        )
                    }
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(rows, key = { it.id }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("${formatModule(row.module)} · ${row.provider}", fontWeight = FontWeight.Bold)
                        Text(row.product, style = MaterialTheme.typography.bodyMedium)
                        Text("Monto: RD$ ${row.amount} · Comisión: RD$ ${row.commission}", style = MaterialTheme.typography.bodySmall)
                        Text("Realizado por: ${row.actorLabel}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("Fecha: ${formatTimestamp(row.createdAt)}", style = MaterialTheme.typography.bodySmall)
                        Text("Estado: ${row.status}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty-report") {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("Sin operaciones en esta fecha", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Prueba con otro día o cambia el módulo seleccionado.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selectedDateKey = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    showDatePicker = false
                }) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}

private fun reportFilter(date: LocalDate, module: String?): ReportFilter {
    val zone = ZoneId.of("America/Santo_Domingo")
    val from = date.atStartOfDay(zone).toInstant().toString()
    val to = date.plusDays(1).atStartOfDay(zone).toInstant().toString()
    return ReportFilter(
        module = com.lotterynet.pro.core.servicesgames.ServicesGamesModule.entries.firstOrNull { it.wireValue == module },
        from = from,
        to = to,
    )
}

private fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es-DO")))

private fun formatModule(value: String): String = when (value) {
    "video_games" -> "Videojuegos"
    "services" -> "Servicios"
    else -> value.ifBlank { "Operación" }
}

private fun formatTimestamp(value: String): String = runCatching {
    java.time.Instant.parse(value).atZone(ZoneId.of("America/Santo_Domingo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-DO")))
}.getOrElse { value }

private fun parseReport(response: JSONObject): List<ReportRow> {
    val array = response.optJSONArray("rows") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            add(
                ReportRow(
                    id = row.optString("id").ifBlank { "row-$index" },
                    module = row.optString("module"),
                    provider = row.optString("provider_id"),
                    product = row.optString("product_id"),
                    amount = row.optString("amount"),
                    commission = row.optString("commission"),
                    status = row.optString("status"),
                    createdAt = row.optString("created_at"),
                    actorLabel = row.optString("actor_label").ifBlank { "Sin identificar" },
                    actorRole = row.optString("actor_role"),
                ),
            )
        }
    }
}
