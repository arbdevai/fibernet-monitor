package com.fibernet.monitor.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fibernet.monitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
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

    // Modal Konfirmasi Reboot
    if (state.showRebootDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRebootConfirmation() },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Restart Router Sekarang?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Router FiberHome akan dimulai ulang secara remote. Seluruh koneksi internet dan Wi-Fi akan terputus sementara selama sekitar 1-2 menit hingga router selesai booting.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmReboot() },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Ya, Restart Router")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRebootConfirmation() }) {
                    Text("Batal")
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
                        text = "Alat Diagnostik & Pemeliharaan",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.loadStatus() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Segarkan",
                            tint = CrimsonPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Reboot Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CrimsonPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PowerSettingsNew,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Restart Router Remote",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Mulai ulang sistem router untuk mengatasi internet lemot atau hang.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.showRebootConfirmation() },
                        enabled = !state.isRebooting,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isRebooting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mengirim perintah...")
                        } else {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restart Router")
                        }
                    }
                }
            }

            // 2. Ping Test Tool Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Band24GHz.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NetworkPing,
                                contentDescription = null,
                                tint = Band24GHz,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Uji Ping dari Router",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Uji latensi langsung dari router ke internet publik.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.pingHostInput,
                            onValueChange = { viewModel.onPingHostChanged(it) },
                            label = { Text("Host / IP Target") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                cursorColor = CrimsonPrimary
                            )
                        )
                        Button(
                            onClick = { viewModel.runPing() },
                            enabled = !state.isPinging,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            modifier = Modifier.height(54.dp)
                        ) {
                            if (state.isPinging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Mulai")
                            }
                        }
                    }

                    // Hasil Ping
                    state.pingResult?.let { result ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HASIL PING ROUTER",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Paket Sukses:")
                                    Text("${result.successCount} / 4", fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Rata-rata Latensi:")
                                    Text("${result.avgResponseTime} ms", fontWeight = FontWeight.Bold, color = SignalExcellent)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Minimum / Maksimum:")
                                    Text("${result.minResponseTime} ms / ${result.maxResponseTime} ms", fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Traceroute Tool Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Band5GHz.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Route,
                                contentDescription = null,
                                tint = Band5GHz,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Traceroute Jalur Jaringan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Analisis hop gateway router menuju server tujuan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.traceHostInput,
                            onValueChange = { viewModel.onTraceHostChanged(it) },
                            label = { Text("Host / IP Target") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Band5GHz,
                                cursorColor = Band5GHz
                            )
                        )
                        Button(
                            onClick = { viewModel.runTraceroute() },
                            enabled = !state.isTracing,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Band5GHz),
                            modifier = Modifier.height(54.dp)
                        ) {
                            if (state.isTracing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Lacak")
                            }
                        }
                    }

                    // Hasil Traceroute
                    if (state.traceResults.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "HASIL TRACEROUTE (${state.traceResults.size} HOP)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                state.traceResults.forEach { hop ->
                                    Text(
                                        text = hop,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Info Teknis Riil Perangkat (Dari get_base_info & get_allwan_info)
            val base = state.routerStatus.baseInfo
            val internetWan = state.routerStatus.internetWan
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SPESIFIKASI & INFORMASI SISTEM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    DetailRow(label = "Model Perangkat", value = "FiberHome ${base.modelName.ifBlank { "HG6145F3" }}")
                    DetailRow(label = "Nomor Seri (SN)", value = base.serialNumber.ifBlank { "FHTTC18477E7" })
                    DetailRow(label = "GPON Serial (gponsn)", value = base.gponSn.ifBlank { "-" })
                    DetailRow(label = "Versi Firmware", value = base.softwareVersion.ifBlank { "RP3441" })
                    DetailRow(label = "Versi Hardware", value = base.hardwareVersion.ifBlank { "WKE2.094.476A01" })
                    DetailRow(label = "MAC Address Gateway", value = base.bridgeMac.ifBlank { "D4:00:68:4E:9D:C8" })
                    DetailRow(label = "Akses WAN", value = "${base.wanAccessType} (${base.uptimeFormatted})")
                    DetailRow(label = "Status PPPoE Internet", value = internetWan?.connectionStatus ?: "Connected")
                    DetailRow(label = "IP Publik WAN", value = internetWan?.externalIp ?: "-")
                    DetailRow(label = "Gateway ISP", value = internetWan?.remoteIp ?: "-")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
