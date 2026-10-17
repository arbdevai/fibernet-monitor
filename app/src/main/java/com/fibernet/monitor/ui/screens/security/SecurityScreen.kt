package com.fibernet.monitor.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fibernet.monitor.data.model.MacFilterRule
import com.fibernet.monitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    viewModel: SecurityViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage, state.successMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Dialog Tambah Blokir Manual
    if (state.showAddDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAddBlockDialog() },
            title = {
                Text(
                    text = "Add to Blocklist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enter the MAC address of the device to block from the router Wi-Fi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = state.inputMac,
                        onValueChange = { viewModel.onInputMacChanged(it) },
                        label = { Text("MAC Address") },
                        placeholder = { Text("e.g. 5C:66:6C:CD:55:69") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            cursorColor = CrimsonPrimary
                        )
                    )
                    OutlinedTextField(
                        value = state.inputName,
                        onValueChange = { viewModel.onInputNameChanged(it) },
                        label = { Text("Device Name / Note") },
                        placeholder = { Text("e.g. Unknown Device") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            cursorColor = CrimsonPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.submitManualBlock() },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Block Device")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAddBlockDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal Konfirmasi Buka Blokir
    state.macToUnblock?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissUnblockConfirmation() },
            title = {
                Text(
                    text = "Unblock Device?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Device '${target.name}' (${target.mac}) will be allowed to reconnect to the Wi-Fi network.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmUnblock() },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalExcellent)
                ) {
                    Text("Unblock")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUnblockConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MAC Filtering & Security",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.loadFilterList() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = CrimsonPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddBlockDialog() },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add Block Rule") },
                containerColor = CrimsonPrimary,
                contentColor = Color.White
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Kartu Status Proteksi
                item {
                    val isFilterEnabled = state.filterInfo.enable == "1"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFilterEnabled) CrimsonContainerLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isFilterEnabled) CrimsonPrimary else MaterialTheme.colorScheme.outline),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isFilterEnabled) Icons.Rounded.Shield else Icons.Rounded.ShieldMoon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isFilterEnabled) "MAC Filter: Active (Blacklist)" else "MAC Filter: Disabled",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFilterEnabled) OnCrimsonContainerLight else MaterialTheme.colorScheme.onSurface
                                 )
                                 Text(
                                     text = if (isFilterEnabled) "Blacklisted devices are immediately blocked by router firewall." else "No active blocking rules applied.",
                                     style = MaterialTheme.typography.bodySmall,
                                     color = if (isFilterEnabled) OnCrimsonContainerLight.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                                 )
                            }
                        }
                    }
                }

                // 2. Section Header: Blocked Devices
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BLOCKED DEVICES (${state.filterInfo.filterList.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // 3. Blocked List Items
                val list = state.filterInfo.filterList
                if (list.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = SignalExcellent,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "No Blocked Devices",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "All network devices have normal access.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    items(list, key = { it.mac }) { rule ->
                        BlockedRuleCard(
                            rule = rule,
                            onUnblock = { viewModel.showUnblockConfirmation(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedRuleCard(
    rule: MacFilterRule,
    onUnblock: (MacFilterRule) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(CrimsonPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.name.ifBlank { "Blocked Device" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = rule.mac,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            OutlinedButton(
                onClick = { onUnblock(rule) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalExcellent)
            ) {
                Text("Unblock", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
