package com.fibernet.monitor.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fibernet.monitor.data.model.BaseInfoResponse
import com.fibernet.monitor.data.model.WanItem
import com.fibernet.monitor.data.repository.CombinedRouterStatus
import com.fibernet.monitor.ui.components.DeviceCard
import com.fibernet.monitor.ui.components.TrafficMonitorCard
import com.fibernet.monitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
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

    // Modal Konfirmasi Blokir
    state.deviceToBlock?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissBlockConfirmation() },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    tint = CrimsonPrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Konfirmasi Pemblokiran",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin memblokir akses Wi-Fi untuk '${target.displayName}' (${target.macAddress})? Perangkat ini akan segera diputus dari jaringan oleh router.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmBlockDevice() },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Text("Ya, Blokir")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBlockConfirmation() }) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CrimsonPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Router,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "FiberNet Monitor",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val model = state.routerStatus.baseInfo.modelName.ifBlank { "HG6145F3" }
                            Text(
                                text = "FiberHome $model • 192.168.1.1",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadData(isInitial = false) },
                        enabled = !state.isRefreshing
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CrimsonPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Segarkan",
                                tint = CrimsonPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading && state.devices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = CrimsonPrimary)
                        Text(
                            text = "Menghubungi router FiberHome...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Header Banner Status Router
                    item {
                        RouterStatusBanner(
                            status = state.routerStatus,
                            totalDevices = state.devices.size,
                            upSpeedKbps = state.totalUpKbps,
                            downSpeedKbps = state.totalDownKbps
                        )
                    }

                    // 2. Kartu Trafik Live & Akumulasi Kuota (GB)
                    item {
                        TrafficMonitorCard(
                            downSpeedKbps = state.totalDownKbps,
                            upSpeedKbps = state.totalUpKbps,
                            downHistory = state.downloadSpeedHistory,
                            upHistory = state.uploadSpeedHistory,
                            baseInfo = state.routerStatus.baseInfo
                        )
                    }

                    // 3. Kartu Status Optik GPON Real-Time
                    item {
                        OpticalStatusCard(baseInfo = state.routerStatus.baseInfo)
                    }

                    // 3. Kartu Status WAN & Internet
                    state.routerStatus.internetWan?.let { wan ->
                        item {
                            WanStatusCard(wan = wan)
                        }
                    }

                    // 4. Pencarian Perangkat
                    item {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Cari nama, IP, atau MAC perangkat...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Hapus",
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CrimsonPrimary,
                                cursorColor = CrimsonPrimary
                            )
                        )
                    }

                    // 5. Tab Filter Frekuensi
                    item {
                        FilterChipsRow(
                            selectedTab = state.selectedFilter,
                            allCount = state.devices.size,
                            band24Count = state.devices.count { it.accessType.contains("2.4") },
                            band5Count = state.devices.count { it.accessType.contains("5G") },
                            lanCount = state.devices.count { it.accessType.contains("LAN") || it.rssi == 0 },
                            onSelect = { viewModel.setFilterTab(it) }
                        )
                    }

                    // 6. Header Jumlah Perangkat
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PERANGKAT TERHUBUNG (${state.filteredDevices.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // 7. Daftar Perangkat Aktif
                    val list = state.filteredDevices
                    if (list.isEmpty()) {
                        item {
                            EmptyDevicePlaceholder(searchActive = state.searchQuery.isNotBlank())
                        }
                    } else {
                        items(list, key = { it.macAddress }) { dev ->
                            DeviceCard(
                                device = dev,
                                onBlockClick = { viewModel.showBlockConfirmation(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouterStatusBanner(
    status: CombinedRouterStatus,
    totalDevices: Int,
    upSpeedKbps: Long,
    downSpeedKbps: Long
) {
    val base = status.baseInfo
    val modelName = base.modelName.ifBlank { "HG6145F3" }
    val firmware = base.softwareVersion.ifBlank { "RP3441" }
    val uptimeFormatted = base.uptimeFormatted.ifBlank { "Aktif" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(CrimsonPrimary, CrimsonPrimaryDark)
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FiberHome $modelName",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Firmware $firmware • SN: ${base.serialNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = uptimeFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.2f))

                // Stats: Klien Aktif, Total Upload, Total Download
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatMetric(
                        label = "PENGGUNA AKTIF",
                        value = "$totalDevices Perangkat",
                        icon = Icons.Rounded.Group
                    )
                    StatMetric(
                        label = "TOTAL UPLOAD",
                        value = "$upSpeedKbps KB/s",
                        icon = Icons.Rounded.ArrowUpward
                    )
                    StatMetric(
                        label = "TOTAL DOWNLOAD",
                        value = "$downSpeedKbps KB/s",
                        icon = Icons.Rounded.ArrowDownward
                    )
                }

                // Baris kedua: Beban CPU & RAM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatMetric(
                        label = "BEBAN CPU",
                        value = "${base.cpuUsagePercent}%",
                        icon = Icons.Rounded.Speed
                    )
                    StatMetric(
                        label = "PENGGUNAAN RAM",
                        value = "${base.ramUsagePercent}%",
                        icon = Icons.Rounded.Memory
                    )
                    StatMetric(
                        label = "TRAFFIC PON UNDUH",
                        value = "%.1f MB".format(base.ponDownloadedMb),
                        icon = Icons.Rounded.Download
                    )
                }
            }
        }
    }
}

@Composable
private fun OpticalStatusCard(baseInfo: BaseInfoResponse) {
    val rxPower = baseInfo.rxPowerDbm
    val rxColor = when {
        rxPower >= -24.0 && rxPower <= -8.0 -> SignalExcellent
        rxPower > -27.0 && rxPower < -24.0 -> SignalGood
        else -> SignalWeak
    }
    val rxStatusLabel = when {
        rxPower >= -24.0 && rxPower <= -8.0 -> "Sangat Baik"
        rxPower > -27.0 && rxPower < -24.0 -> "Cukup Baik"
        else -> "Redaman Tinggi / Buruk"
    }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = null,
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Status Optik GPON (Kabel Fiber)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = rxColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = rxStatusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = rxColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OpticalMetricItem(
                    label = "Rx Power (Terima)",
                    value = "${baseInfo.rxPowerRaw} dBm",
                    valueColor = rxColor
                )
                OpticalMetricItem(
                    label = "Tx Power (Pancar)",
                    value = "${baseInfo.txPowerRaw} dBm"
                )
                OpticalMetricItem(
                    label = "Suhu Modul Optik",
                    value = "${baseInfo.temperatureRaw} °C"
                )
                OpticalMetricItem(
                    label = "Tegangan Laser",
                    value = "${baseInfo.supplyVoltageRaw} V"
                )
            }
        }
    }
}

@Composable
private fun OpticalMetricItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun WanStatusCard(wan: WanItem) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = Band5GHz,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Status Sambungan Internet (WAN)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = if (wan.isConnected) SignalExcellent.copy(alpha = 0.15f) else SignalWeak.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (wan.isConnected) "Terhubung (Connected)" else "Terputus",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (wan.isConnected) SignalExcellent else SignalWeak,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("IP Publik:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(wan.externalIp.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gateway ISP:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(wan.remoteIp.ifBlank { "-" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("DNS Primer:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(wan.dnsServers.split(",").firstOrNull() ?: "-", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = Color.White.copy(alpha = 0.75f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedTab: DeviceFilterTab,
    allCount: Int,
    band24Count: Int,
    band5Count: Int,
    lanCount: Int,
    onSelect: (DeviceFilterTab) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            FilterChip(
                selected = selectedTab == DeviceFilterTab.ALL,
                onClick = { onSelect(DeviceFilterTab.ALL) },
                label = { Text("Semua ($allCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CrimsonPrimary,
                    selectedLabelColor = Color.White
                )
            )
        }
        item {
            FilterChip(
                selected = selectedTab == DeviceFilterTab.BAND_24G,
                onClick = { onSelect(DeviceFilterTab.BAND_24G) },
                label = { Text("2.4 GHz ($band24Count)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Band24GHz,
                    selectedLabelColor = Color.White
                )
            )
        }
        item {
            FilterChip(
                selected = selectedTab == DeviceFilterTab.BAND_5G,
                onClick = { onSelect(DeviceFilterTab.BAND_5G) },
                label = { Text("5 GHz ($band5Count)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Band5GHz,
                    selectedLabelColor = Color.White
                )
            )
        }
        item {
            FilterChip(
                selected = selectedTab == DeviceFilterTab.LAN,
                onClick = { onSelect(DeviceFilterTab.LAN) },
                label = { Text("Kabel LAN ($lanCount)") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BandLan,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun EmptyDevicePlaceholder(searchActive: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
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
                imageVector = if (searchActive) Icons.Rounded.SearchOff else Icons.Rounded.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Text(
                text = if (searchActive) "Tidak ada perangkat yang cocok" else "Tidak ada perangkat terhubung",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (searchActive) "Coba periksa kata kunci pencarian Anda" else "Pastikan Wi-Fi router aktif dan ada perangkat yang tersambung",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
