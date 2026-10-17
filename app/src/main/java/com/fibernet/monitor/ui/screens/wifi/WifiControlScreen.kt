package com.fibernet.monitor.ui.screens.wifi

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fibernet.monitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiControlScreen(
    viewModel: WifiControlViewModel,
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

    // Dialog Edit SSID
    if (state.showEditSsidDialog) {
        val freqLabel = if (state.editingIs5G) "5 GHz" else "2.4 GHz"
        AlertDialog(
            onDismissRequest = { viewModel.dismissEditSsid() },
            title = { Text("Ubah Nama Wi-Fi ($freqLabel)", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = state.inputSsid,
                    onValueChange = { viewModel.onInputSsidChanged(it) },
                    label = { Text("Nama Wi-Fi (SSID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        cursorColor = CrimsonPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveSsid() },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Simpan ke Router")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEditSsid() }) { Text("Batal") }
            }
        )
    }

    // Dialog Edit Password
    if (state.showEditPasswordDialog) {
        val freqLabel = if (state.editingIs5G) "5 GHz" else "2.4 GHz"
        var showPasswordText by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissEditPassword() },
            title = { Text("Ubah Password Wi-Fi ($freqLabel)", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = state.inputPassword,
                    onValueChange = { viewModel.onInputPasswordChanged(it) },
                    label = { Text("Kata Sandi Baru (Min. 8 karakter)") },
                    singleLine = true,
                    visualTransformation = if (showPasswordText) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPasswordText = !showPasswordText }) {
                            Icon(
                                imageVector = if (showPasswordText) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CrimsonPrimary,
                        cursorColor = CrimsonPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.savePassword() },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Perbarui Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEditPassword() }) { Text("Batal") }
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
                        text = "Manajemen & Kontrol Wi-Fi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.loadConfig() }) {
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
            // Radio Card 2.4 GHz
            WifiRadioCard(
                title = "Wi-Fi 2.4 GHz",
                subtitle = "Standar 802.11 b/g/n • Jangkauan Luas",
                ssid = state.config.ssid24,
                password = state.config.password24,
                bssid = state.config.bssid24,
                channel = state.config.channel24,
                currentTxPower = state.config.txPower24,
                bandColor = Band24GHz,
                onEditSsid = { viewModel.showEditSsid(is5G = false) },
                onEditPassword = { viewModel.showEditPassword(is5G = false) },
                onSetPower = { viewModel.setTxPower(is5G = false, percentage = it) }
            )

            // Radio Card 5 GHz (Wi-Fi 6 AX)
            WifiRadioCard(
                title = "Wi-Fi 5 GHz (Wi-Fi 6)",
                subtitle = "Standar 802.11ax • Kecepatan Tinggi AX3000",
                ssid = state.config.ssid5G,
                password = state.config.password5G,
                bssid = state.config.bssid5G,
                channel = state.config.channel5G,
                currentTxPower = state.config.txPower5G,
                bandColor = Band5GHz,
                onEditSsid = { viewModel.showEditSsid(is5G = true) },
                onEditPassword = { viewModel.showEditPassword(is5G = true) },
                onSetPower = { viewModel.setTxPower(is5G = true, percentage = it) }
            )

            // WPS Quick Trigger Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CrimsonPrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiProtectedSetup,
                            contentDescription = null,
                            tint = CrimsonPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wi-Fi Protected Setup (WPS)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Sambungkan perangkat klien instan tanpa perlu ketik sandi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Button(
                        onClick = { viewModel.triggerWps() },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Mulai WPS")
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiRadioCard(
    title: String,
    subtitle: String,
    ssid: String,
    password: String,
    bssid: String,
    channel: String,
    currentTxPower: Int,
    bandColor: Color,
    onEditSsid: () -> Unit,
    onEditPassword: () -> Unit,
    onSetPower: (Int) -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }

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
            // Header Radio
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bandColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Wifi,
                            contentDescription = null,
                            tint = bandColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Surface(
                    color = SignalExcellent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Aktif",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SignalExcellent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Info Tambahan: BSSID & Saluran
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "BSSID: ${bssid.ifBlank { "D4:01:68:4E:9D:C8" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "Saluran: ${if (channel.isNotBlank() && channel != "0") channel else "Auto (149)"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

            // Nama Wi-Fi (SSID)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NAMA WI-FI (SSID)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = ssid.ifBlank { "Memuat dari router..." },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = onEditSsid) {
                    Text("Ganti Nama", color = CrimsonPrimary)
                }
            }

            // Kata Sandi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "KATA SANDI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (showPassword) password.ifBlank { "-" } else "••••••••••••",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    TextButton(onClick = onEditPassword) {
                        Text("Ubah Sandi", color = CrimsonPrimary)
                    }
                }
            }

            // Daya Transmisi (Tx Power)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DAYA TRANSMISI (TX POWER)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Saat ini: $currentTxPower%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = bandColor
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(20, 60, 80, 100).forEach { pwr ->
                        val isSelected = currentTxPower == pwr
                        if (isSelected) {
                            Button(
                                onClick = { onSetPower(pwr) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = bandColor),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("$pwr%", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onSetPower(pwr) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("$pwr%")
                            }
                        }
                    }
                }
            }
        }
    }
}
