package com.lotterynet.pro.ui.admin.redesign.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lotterynet.pro.core.model.UserAccount
import com.lotterynet.pro.core.model.UserRole
import com.lotterynet.pro.ui.admin.redesign.components.*
import com.lotterynet.pro.ui.admin.redesign.theme.*
import com.lotterynet.pro.ui.admin.redesign.uiState.*

@Composable
fun MasterDashboardScreen(
    state: MasterDashboardState,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onCreateAdmin: (name: String, user: String) -> Unit,
    onCreateBanca: (name: String, adminId: String) -> Unit,
    onToggleAdminActive: (UserAccount) -> Unit,
    onToggleBancaActive: (BancaDetailState) -> Unit
) {
    var selectedRoute by remember { mutableStateOf("dashboard") }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateAdminDialog by remember { mutableStateOf(false) }
    var showCreateBancaDialog by remember { mutableStateOf(false) }

    // Dialog state controllers
    var newAdminName by remember { mutableStateOf("") }
    var newAdminUser by remember { mutableStateOf("") }
    var newBancaName by remember { mutableStateOf("") }
    var selectedBancaAdminId by remember { mutableStateOf("") }

    AdminScaffold(
        role = UserRole.MASTER,
        currentRoute = selectedRoute,
        onRouteSelected = {
            selectedRoute = it
            onNavigate(it)
        },
        title = "Consola Master",
        subtitle = "Control global de bancas y administradores",
        onBack = onBack,
        rightAction = {
            IconButton(onClick = { showCreateAdminDialog = true }) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = "Nuevo Admin",
                    tint = FintechGold
                )
            }
        }
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
                // KPIs Strip
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Venta Global",
                            value = "$${String.format("%,.2f", state.globalSalesToday)}",
                            modifier = Modifier.weight(1.3f),
                            trendPercent = 14.2,
                            sparklinePoints = listOf(180f, 210f, 205f, 230f, 245f)
                        )
                        MetricCard(
                            label = "Bancas",
                            value = state.activeBancas.toString(),
                            modifier = Modifier.weight(0.8f)
                        )
                        MetricCard(
                            label = "Admins",
                            value = state.totalAdmins.toString(),
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                }

                // Search query and section header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ADMINISTRADORES ACTIVOS",
                            style = FintechTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = SlateTextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        
                        SearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Buscar admin...",
                            modifier = Modifier.width(160.dp),
                            onClear = { searchQuery = "" }
                        )
                    }
                }

                // Admins list
                val filteredAdmins = state.activeAdminsList.filter {
                    it.displayName?.contains(searchQuery, ignoreCase = true) == true ||
                    it.user.contains(searchQuery, ignoreCase = true)
                }

                if (filteredAdmins.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No se encontraron administradores.", style = FintechTypography.bodyMedium, color = SlateTextMuted)
                        }
                    }
                } else {
                    items(filteredAdmins, key = { it.id }) { admin ->
                        UserCard(
                            userAccount = admin,
                            onEditClick = { /* Abrir edición de límites master */ },
                            onToggleActiveClick = { onToggleAdminActive(admin) }
                        )
                    }
                }

                // Bancas Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RESUMEN DE BANCAS",
                            style = FintechTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = SlateTextMuted
                        )
                        
                        PrimaryButton(
                            text = "Nueva Banca",
                            onClick = { showCreateBancaDialog = true }
                        )
                    }
                }

                // Bancas list
                if (state.bancasSummary.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hay bancas registradas.", style = FintechTypography.bodyMedium, color = SlateTextMuted)
                        }
                    }
                } else {
                    items(state.bancasSummary, key = { it.id }) { banca ->
                        BancaCard(
                            banca = banca,
                            onEditClick = { /* Clic editar banca */ },
                            onToggleActiveClick = { onToggleBancaActive(banca) }
                        )
                    }
                }

                // Bottom spacer for scrolling
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Create Admin Dialog
            if (showCreateAdminDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateAdminDialog = false },
                    title = {
                        Text(
                            "Crear Nuevo Administrador",
                            style = FintechTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = SlateTextInk
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newAdminName,
                                onValueChange = { newAdminName = it },
                                label = { Text("Nombre Completo") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FintechGold,
                                    unfocusedBorderColor = SlateBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = newAdminUser,
                                onValueChange = { newAdminUser = it },
                                label = { Text("Usuario (Ej: admin_norte)") },
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
                                if (newAdminName.isNotBlank() && newAdminUser.isNotBlank()) {
                                    onCreateAdmin(newAdminName, newAdminUser)
                                    newAdminName = ""
                                    newAdminUser = ""
                                    showCreateAdminDialog = false
                                }
                            }
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateAdminDialog = false }) {
                            Text("Cancelar", color = SlateTextMuted)
                        }
                    },
                    containerColor = SlateCard
                )
            }

            // Create Banca Dialog
            if (showCreateBancaDialog) {
                AlertDialog(
                    onDismissRequest = { showCreateBancaDialog = false },
                    title = {
                        Text(
                            "Crear Nueva Banca",
                            style = FintechTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = SlateTextInk
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newBancaName,
                                onValueChange = { newBancaName = it },
                                label = { Text("Nombre de la Banca") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FintechGold,
                                    unfocusedBorderColor = SlateBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = "Asignar Administrador",
                                style = FintechTypography.labelLarge,
                                color = SlateTextMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            
                            val adminOptions = state.activeAdminsList.map { it.displayName ?: it.user }
                            if (adminOptions.isNotEmpty()) {
                                var selectedIndex by remember { mutableStateOf(0) }
                                selectedBancaAdminId = state.activeAdminsList[selectedIndex].id
                                
                                DropdownSelector(
                                    options = adminOptions,
                                    selectedOption = adminOptions[selectedIndex],
                                    onOptionSelected = { selected ->
                                        selectedIndex = adminOptions.indexOf(selected)
                                        selectedBancaAdminId = state.activeAdminsList[selectedIndex].id
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text("Crea un administrador primero.", color = DangerRed, style = FintechTypography.bodyMedium)
                            }
                        }
                    },
                    confirmButton = {
                        PrimaryButton(
                            text = "Crear",
                            onClick = {
                                if (newBancaName.isNotBlank() && selectedBancaAdminId.isNotBlank()) {
                                    onCreateBanca(newBancaName, selectedBancaAdminId)
                                    newBancaName = ""
                                    selectedBancaAdminId = ""
                                    showCreateBancaDialog = false
                                }
                            }
                        )
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreateBancaDialog = false }) {
                            Text("Cancelar", color = SlateTextMuted)
                        }
                    },
                    containerColor = SlateCard
                )
            }
        }
    }
}
