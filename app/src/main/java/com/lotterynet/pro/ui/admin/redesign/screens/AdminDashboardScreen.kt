package com.lotterynet.pro.ui.admin.redesign.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.ui.admin.redesign.components.*
import com.lotterynet.pro.ui.admin.redesign.theme.*
import com.lotterynet.pro.ui.admin.redesign.uiState.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminDashboardScreen(
    state: AdminDashboardState,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onUpdateCashierLimit: (UserAccount, Double) -> Unit,
    onToggleCashierActive: (UserAccount) -> Unit
) {
    var selectedRoute by remember { mutableStateOf("dashboard") }
    var searchQuery by remember { mutableStateOf("") }
    var showEditLimitDialog by remember { mutableStateOf(false) }
    var selectedCashierForEdit by remember { mutableStateOf<UserAccount?>(null) }
    var newLimitValue by remember { mutableStateOf("") }

    AdminScaffold(
        role = UserRole.ADMIN,
        currentRoute = selectedRoute,
        onRouteSelected = {
            selectedRoute = it
            onNavigate(it)
        },
        title = "Panel Administrativo",
        subtitle = "Gestión local de cajeros y finanzas bancarias",
        onBack = onBack
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Fintech metrics strips (Sales today, Gains, pending prizes, active cajeros)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Venta Hoy",
                            value = "$${String.format("%,.2f", state.salesToday)}",
                            modifier = Modifier.weight(1f),
                            trendPercent = 8.7,
                            sparklinePoints = listOf(62f, 74f, 68f, 80f, 85f)
                        )
                        MetricCard(
                            label = "Ganancia Caja",
                            value = "$${String.format("%,.2f", state.gainsToday)}",
                            modifier = Modifier.weight(1f),
                            trendPercent = 12.1,
                            sparklinePoints = listOf(40f, 48f, 45f, 51f, 53f),
                            color = SuccessGreen
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Premios Pendientes",
                            value = "$${String.format("%,.2f", state.pendingPrizes)}",
                            modifier = Modifier.weight(1f),
                            color = WarningOrange
                        )
                        MetricCard(
                            label = "Cajeros Activos",
                            value = state.activeCashiers.toString(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Section header and cashier search bar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MIS CAJEROS",
                            style = FintechTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = SlateTextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        
                        SearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Buscar cajero...",
                            modifier = Modifier.width(160.dp),
                            onClear = { searchQuery = "" }
                        )
                    }
                }

                // Cashiers list
                val filteredCashiers = state.cashiersList.filter {
                    it.displayName?.contains(searchQuery, ignoreCase = true) == true ||
                    it.user.contains(searchQuery, ignoreCase = true)
                }

                if (filteredCashiers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No se encontraron cajeros.", style = FintechTypography.bodyMedium, color = SlateTextMuted)
                        }
                    }
                } else {
                    items(filteredCashiers, key = { it.id }) { cashier ->
                        UserCard(
                            userAccount = cashier,
                            onEditClick = {
                                selectedCashierForEdit = cashier
                                newLimitValue = cashier.recargaTxLimit?.toString() ?: ""
                                showEditLimitDialog = true
                            },
                            onToggleActiveClick = { onToggleCashierActive(cashier) }
                        )
                    }
                }

                // Activity logs header
                item {
                    Text(
                        text = "ACTIVIDAD OPERATIVA RECIENTE",
                        style = FintechTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = SlateTextMuted,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                // Activity logs rows (live feed style)
                if (state.recentActivity.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Sin actividad registrada hoy.", style = FintechTypography.bodyMedium, color = SlateTextMuted)
                        }
                    }
                } else {
                    items(state.recentActivity, key = { it.id }) { log ->
                        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(log.timestampMs))
                        val borderCol = if (log.isAlert) DangerRed.copy(alpha = 0.3f) else SlateBorder
                        val bgCol = if (log.isAlert) DangerRedBg.copy(alpha = 0.4f) else SlateCard
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(bgCol, RoundedCornerShape(6.dp))
                                .border(1.dp, borderCol, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (log.isAlert) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = "Alerta",
                                        tint = DangerRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.detail,
                                        style = FintechTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = SlateTextInk
                                    )
                                    Text(
                                        text = "${log.cashierName} · $timeStr",
                                        style = FintechTypography.bodyMedium,
                                        color = SlateTextMuted
                                    )
                                }
                                
                                Text(
                                    text = log.type.uppercase(),
                                    style = FintechTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (log.isAlert) DangerRed else FintechGold
                                )
                            }
                        }
                    }
                }

                // Space at bottom
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Edit limits Dialog
            if (showEditLimitDialog && selectedCashierForEdit != null) {
                AlertDialog(
                    onDismissRequest = { showEditLimitDialog = false },
                    title = {
                        Text(
                            "Editar Límites de Cajero",
                            style = FintechTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = SlateTextInk
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Configurando límites para ${selectedCashierForEdit!!.displayName ?: selectedCashierForEdit!!.user}",
                                style = FintechTypography.bodyMedium,
                                color = SlateTextMuted
                            )
                            
                            OutlinedTextField(
                                value = newLimitValue,
                                onValueChange = { newLimitValue = it },
                                label = { Text("Límite de Venta Diario ($)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FintechGold,
                                    unfocusedBorderColor = SlateBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        PrimaryButton(
                            text = "Guardar",
                            onClick = {
                                val limit = newLimitValue.toDoubleOrNull() ?: 0.0
                                onUpdateCashierLimit(selectedCashierForEdit!!, limit)
                                showEditLimitDialog = false
                                selectedCashierForEdit = null
                            }
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditLimitDialog = false }) {
                            Text("Cancelar", color = SlateTextMuted)
                        }
                    },
                    containerColor = SlateCard
                )
            }
        }
    }
}
