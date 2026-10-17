package com.fibernet.monitor.ui.screens.wifi

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fibernet.monitor.data.model.AccessPointInfo
import com.fibernet.monitor.data.model.ChannelRating
import com.fibernet.monitor.data.model.WifiStatusItem
import com.fibernet.monitor.ui.components.CreateEditSsidDialog
import com.fibernet.monitor.ui.components.WifiControlSkeleton
import com.fibernet.monitor.ui.components.WifiQrDialog
import com.fibernet.monitor.ui.components.WifiSignalWaveform
import com.fibernet.monitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiControlScreen(
    viewModel: WifiControlViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Permission Launcher untuk Wi-Fi Analyzer & Walk Test
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            if (state.selectedSubTab == WifiScreenSubTab.ANALYZER) {
                viewModel.scanChannels(context)
            } else if (state.selectedSubTab == WifiScreenSubTab.SIGNAL_METER) {
                viewModel.startLiveSignalMonitor(context)
            }
        }
    }

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

    var qrShareTarget by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    qrShareTarget?.let { (ssid, password, band) ->
        WifiQrDialog(
            ssid = ssid,
            password = password,
            bandLabel = band,
            onDismiss = { qrShareTarget = null }
        )
    }

    // Modal Create / Edit SSID (Multi-SSID)
    if (state.showCreateEditDialog) {
        val target = state.targetSsidItem
        CreateEditSsidDialog(
            initialIs5G = state.targetIs5G,
            initialIndex = target?.index ?: 2,
            initialSsid = target?.ssid ?: "",
            initialPassword = target?.preSharedKey ?: "",
            initialEnabled = target?.isSsidEnabled ?: true,
            initialHidden = target?.isBroadcastHidden ?: false,
            onDismiss = { viewModel.dismissCreateSsidDialog() },
            onSave = { is5G, wifiIndex, ssid, password, enabled, hideBroadcast ->
                viewModel.saveSsidFull(is5G, wifiIndex, ssid, password, enabled, hideBroadcast)
            }
        )
    }

    // Modal Quick Edit SSID (Primary)
    if (state.showEditSsidDialog) {
        val freqLabel = if (state.editingIs5G) "5 GHz" else "2.4 GHz"
        AlertDialog(
            onDismissRequest = { viewModel.dismissEditSsid() },
            title = { Text("Change Wi-Fi Name ($freqLabel)", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = state.inputSsid,
                    onValueChange = { viewModel.onInputSsidChanged(it) },
                    label = { Text("Wi-Fi Name (SSID)") },
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
                    Text("Save to Router")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEditSsid() }) { Text("Cancel") }
            }
        )
    }

    // Modal Quick Edit Password (Primary)
    if (state.showEditPasswordDialog) {
        val freqLabel = if (state.editingIs5G) "5 GHz" else "2.4 GHz"
        var showPasswordText by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { viewModel.dismissEditPassword() },
            title = { Text("Change Wi-Fi Password ($freqLabel)", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = state.inputPassword,
                    onValueChange = { viewModel.onInputPasswordChanged(it) },
                    label = { Text("New Password (Min. 8 characters)") },
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
                    Text("Save to Router")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissEditPassword() }) { Text("Cancel") }
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
                        Icon(
                            imageVector = Icons.Rounded.SettingsInputAntenna,
                            contentDescription = null,
                            tint = CrimsonPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Wi-Fi & RF Spectrum",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Multi-SSID • Net Analyzer • Live Coverage Walk-Test",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            when (state.selectedSubTab) {
                                WifiScreenSubTab.CONFIG -> viewModel.loadConfig()
                                WifiScreenSubTab.ANALYZER -> viewModel.scanChannels(context)
                                WifiScreenSubTab.SIGNAL_METER -> viewModel.startLiveSignalMonitor(context)
                            }
                        },
                        enabled = !state.isLoading && !state.isActionInProgress
                    ) {
                        if (state.isLoading || state.analyzerState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = CrimsonPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sub-Tab Switcher (Config vs Net Analyzer vs Coverage Meter)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WifiScreenSubTab.values().forEach { tab ->
                        val isSelected = state.selectedSubTab == tab
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectSubTab(tab, context) },
                            label = { Text(tab.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (tab) {
                                        WifiScreenSubTab.CONFIG -> Icons.Rounded.SettingsInputAntenna
                                        WifiScreenSubTab.ANALYZER -> Icons.Rounded.WifiFind
                                        WifiScreenSubTab.SIGNAL_METER -> Icons.Rounded.Speed
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonPrimary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Sub-Tab Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                when (state.selectedSubTab) {
                    WifiScreenSubTab.CONFIG -> {
                        if (state.isLoading && state.config.ssid24.isBlank() && state.config.ssid5G.isBlank()) {
                            WifiControlSkeleton()
                        } else {
                            WifiConfigContent(
                                state = state,
                                viewModel = viewModel,
                                onShareQr = { ssid, pass, band -> qrShareTarget = Triple(ssid, pass, band) }
                            )
                        }
                    }

                    WifiScreenSubTab.ANALYZER -> {
                        WifiAnalyzerContent(
                            state = state,
                            viewModel = viewModel,
                            onRequestPermission = {
                                val perms = mutableListOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            }
                        )
                    }

                    WifiScreenSubTab.SIGNAL_METER -> {
                        WifiSignalMeterContent(
                            state = state,
                            onRequestPermission = {
                                val perms = mutableListOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            }
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SUB-TAB 1: CONFIG & MULTI-SSID MANAGEMENT
// -------------------------------------------------------------
@Composable
private fun WifiConfigContent(
    state: WifiControlUiState,
    viewModel: WifiControlViewModel,
    onShareQr: (String, String, String) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Action: Create / Edit Multi-SSID Button
        Button(
            onClick = { viewModel.showCreateSsidDialog(is5G = false) },
            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create / Configure New SSID (Multi-SSID Profile)", fontWeight = FontWeight.Bold)
        }

        // Primary 2.4 GHz Card
        WifiBandCard(
            bandLabel = "2.4 GHz Frequency",
            bandBadgeColor = Band24GHz,
            ssid = state.config.ssid24,
            password = state.config.password24,
            bssid = state.config.bssid24,
            channel = state.config.channel24,
            txPower = state.config.txPower24,
            enabled = state.config.enabled24,
            onEditSsid = { viewModel.showEditSsid(is5G = false) },
            onEditPassword = { viewModel.showEditPassword(is5G = false) },
            onSetTxPower = { viewModel.setTxPower(is5G = false, it) },
            onShareQr = { onShareQr(state.config.ssid24, state.config.password24, "2.4 GHz") }
        )

        // Primary 5 GHz Card
        WifiBandCard(
            bandLabel = "5 GHz High-Speed",
            bandBadgeColor = Band5GHz,
            ssid = state.config.ssid5G,
            password = state.config.password5G,
            bssid = state.config.bssid5G,
            channel = state.config.channel5G,
            txPower = state.config.txPower5G,
            enabled = state.config.enabled5G,
            onEditSsid = { viewModel.showEditSsid(is5G = true) },
            onEditPassword = { viewModel.showEditPassword(is5G = true) },
            onSetTxPower = { viewModel.setTxPower(is5G = true, it) },
            onShareQr = { onShareQr(state.config.ssid5G, state.config.password5G, "5 GHz") }
        )

        // Multi-SSID Profiles Section (List of all secondary SSIDs from router)
        val secondary2G = state.config.allSsid24.filter { it.index > 1 }
        val secondary5G = state.config.allSsid5G.filter { it.index > 1 }
        val allSecondary = secondary2G.map { false to it } + secondary5G.map { true to it }

        if (allSecondary.isNotEmpty()) {
            Text(
                text = "Guest & Secondary SSID Profiles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            allSecondary.forEach { (is5G, item) ->
                SecondarySsidCard(
                    is5G = is5G,
                    item = item,
                    onEdit = { viewModel.showCreateSsidDialog(is5G = is5G, existingItem = item) },
                    onToggle = { enabled ->
                        viewModel.saveSsidFull(
                            is5G = is5G,
                            wifiIndex = item.index,
                            ssid = item.ssid,
                            password = item.preSharedKey,
                            enabled = enabled,
                            hideBroadcast = item.isBroadcastHidden
                        )
                    },
                    onShareQr = { onShareQr(item.ssid, item.preSharedKey, if (is5G) "5 GHz" else "2.4 GHz") }
                )
            }
        }

        // WPS Push Button Card
        WpsControlCard(
            wpsActive = state.wpsActive,
            isActionInProgress = state.isActionInProgress,
            onTrigger = { viewModel.triggerWps() }
        )
    }
}

// -------------------------------------------------------------
// SUB-TAB 2: NET ANALYZER & SPECTRUM SCANNER
// -------------------------------------------------------------
@Composable
private fun WifiAnalyzerContent(
    state: WifiControlUiState,
    viewModel: WifiControlViewModel,
    onRequestPermission: () -> Unit
) {
    val analyzer = state.analyzerState
    val scrollState = rememberScrollState()
    var selectedSpectrumBand by remember { mutableStateOf(false) } // false: 2.4G, true: 5G

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!analyzer.hasPermission) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationSearching,
                        contentDescription = null,
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Location Permission Required for Wi-Fi Scan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Android OS requires fine location permission to scan nearby Wi-Fi channel frequencies and calculate channel congestion without dummy data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Grant Permission & Scan")
                    }
                }
            }
        } else {
            // Best Channel Recommendation Banner
            val bestCh = if (!selectedSpectrumBand) analyzer.bestChannel24G else analyzer.bestChannel5G
            val currentRouterCh = if (!selectedSpectrumBand) {
                state.config.channel24.toIntOrNull() ?: 0
            } else {
                state.config.channel5G.toIntOrNull() ?: 0
            }

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CrimsonContainerLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text(
                                    text = "Recommended Channel: Ch $bestCh",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OnCrimsonContainerLight
                                )
                                Text(
                                    text = if (!selectedSpectrumBand) "2.4 GHz Band (Least Congestion)" else "5 GHz Band (Clean Spectrum)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnCrimsonContainerLight.copy(alpha = 0.8f)
                                )
                            }
                        }

                        if (currentRouterCh != bestCh) {
                            Button(
                                onClick = { viewModel.applyChannelToRouter(is5G = selectedSpectrumBand, channel = bestCh) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Apply to Router", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Surface(
                                color = SignalExcellent,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Active on Router",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Band Selector (2.4 GHz vs 5 GHz)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !selectedSpectrumBand,
                    onClick = { selectedSpectrumBand = false },
                    label = { Text("2.4 GHz Channels (${analyzer.channels24G.size})") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Band24GHz,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedSpectrumBand,
                    onClick = { selectedSpectrumBand = true },
                    label = { Text("5 GHz Channels (${analyzer.channels5G.size})") },
                    modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Band5GHz,
                        selectedLabelColor = Color.White
                    )
                )
            }

            // Channel Congestion & Rating Cards
            val activeRatings = if (!selectedSpectrumBand) analyzer.channels24G else analyzer.channels5G
            if (activeRatings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Wi-Fi channels scanned yet. Tap refresh to scan nearby spectrum.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Text(
                    text = "Spectrum Channel Star Ratings (1-5 ⭐)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                activeRatings.forEach { rating ->
                    ChannelRatingItemCard(
                        rating = rating,
                        isCurrentRouterChannel = currentRouterCh == rating.channel,
                        onApply = { viewModel.applyChannelToRouter(is5G = selectedSpectrumBand, channel = rating.channel) }
                    )
                }
            }

            // Nearby Detected Access Points
            if (analyzer.allAccessPoints.isNotEmpty()) {
                Text(
                    text = "Nearby Access Points (${analyzer.allAccessPoints.size} Detected)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                analyzer.allAccessPoints.take(15).forEach { ap ->
                    NearbyApItemCard(ap = ap)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SUB-TAB 3: COVERAGE METER (REAL-TIME WALK TEST)
// -------------------------------------------------------------
@Composable
private fun WifiSignalMeterContent(
    state: WifiControlUiState,
    onRequestPermission: () -> Unit
) {
    val analyzer = state.analyzerState
    val signal = analyzer.currentSignal
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!analyzer.hasPermission) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Location Permission Required for Live Walk Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(onClick = onRequestPermission, colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)) {
                        Text("Grant Permission")
                    }
                }
            }
        } else if (signal == null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No Wi-Fi Connection Detected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Connect your device to your router Wi-Fi to start live signal coverage testing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            // Signal Speedometer / Gauge Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "REAL-TIME RSSI SIGNAL METER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )

                    // Big dBm & Quality Number
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${signal.rssiDbm}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                signal.rssiDbm >= -60 -> SignalExcellent
                                signal.rssiDbm >= -72 -> SignalGood
                                signal.rssiDbm >= -82 -> SignalFair
                                else -> SignalWeak
                            }
                        )
                        Text(
                            text = " dBm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Linear Quality Progress Bar
                    LinearProgressIndicator(
                        progress = { signal.qualityPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = when {
                            signal.rssiDbm >= -60 -> SignalExcellent
                            signal.rssiDbm >= -72 -> SignalGood
                            signal.rssiDbm >= -82 -> SignalFair
                            else -> SignalWeak
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Signal Quality: ${signal.qualityPercent}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = when {
                                signal.rssiDbm >= -60 -> "Near Router (Excellent)"
                                signal.rssiDbm >= -72 -> "Mid-Range (Good)"
                                signal.rssiDbm >= -82 -> "Edge Zone (Weak)"
                                else -> "Dead Zone"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                signal.rssiDbm >= -60 -> SignalExcellent
                                signal.rssiDbm >= -72 -> SignalGood
                                signal.rssiDbm >= -82 -> SignalFair
                                else -> SignalWeak
                            }
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Connected Info Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Connected SSID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(signal.ssid, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Link Speed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${signal.linkSpeedMbps} Mbps", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = CrimsonPrimary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Frequency & Channel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text("${signal.frequencyMhz} MHz (Ch ${signal.channel})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("BSSID (AP MAC)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(signal.bssid, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            // Live Waveform Canvas Chart (Walk Test)
            WifiSignalWaveform(samples = analyzer.signalHistory)
        }
    }
}

// -------------------------------------------------------------
// HELPER COMPONENTS FOR CARDS & RATINGS
// -------------------------------------------------------------
@Composable
private fun ChannelRatingItemCard(
    rating: ChannelRating,
    isCurrentRouterChannel: Boolean,
    onApply: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rating.isBestChoice) CrimsonContainerLight.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = if (rating.isBestChoice) CrimsonPrimary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Ch ${rating.channel}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rating.isBestChoice) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(5) { starIndex ->
                            val filled = starIndex < rating.scoreStars
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = if (filled) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (isCurrentRouterChannel) {
                            Text(
                                text = "• Current",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SignalExcellent
                            )
                        }
                    }
                    Text(
                        text = "${rating.statusText} • ${rating.apCount} APs nearby",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (!isCurrentRouterChannel) {
                OutlinedButton(
                    onClick = onApply,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Apply", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun NearbyApItemCard(ap: AccessPointInfo) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ap.ssid,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${ap.band} • Ch ${ap.channel} • ${ap.bssid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontFamily = FontFamily.Monospace
                )
            }
            Surface(
                color = when {
                    ap.rssiDbm >= -65 -> SignalExcellent.copy(alpha = 0.15f)
                    ap.rssiDbm >= -80 -> SignalGood.copy(alpha = 0.15f)
                    else -> SignalWeak.copy(alpha = 0.15f)
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "${ap.rssiDbm} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        ap.rssiDbm >= -65 -> SignalExcellent
                        ap.rssiDbm >= -80 -> SignalGood
                        else -> SignalWeak
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun SecondarySsidCard(
    is5G: Boolean,
    item: WifiStatusItem,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onShareQr: () -> Unit
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (is5G) Band5GHz.copy(alpha = 0.15f) else Band24GHz.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = if (is5G) Band5GHz else Band24GHz,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = item.ssid.ifBlank { "SSID Slot ${item.index}" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = if (is5G) Band5GHz else Band24GHz,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (is5G) "5G Slot ${item.index}" else "2.4G Slot ${item.index}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 9.sp
                            )
                        }
                    }
                    Text(
                        text = if (item.preSharedKey.isNotBlank()) "Protected (WPA2-PSK)" else "Open Network",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.isSsidEnabled && item.ssid.isNotBlank()) {
                    IconButton(onClick = onShareQr) {
                        Icon(Icons.Rounded.QrCode, contentDescription = "QR Code", tint = CrimsonPrimary)
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit SSID", tint = MaterialTheme.colorScheme.outline)
                }
                Switch(
                    checked = item.isSsidEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = CrimsonPrimary
                    )
                )
            }
        }
    }
}

@Composable
private fun WifiBandCard(
    bandLabel: String,
    bandBadgeColor: Color,
    ssid: String,
    password: String,
    bssid: String,
    channel: String,
    txPower: Int,
    enabled: Boolean,
    onEditSsid: () -> Unit,
    onEditPassword: () -> Unit,
    onSetTxPower: (Int) -> Unit,
    onShareQr: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = bandBadgeColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = bandLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onShareQr) {
                        Icon(Icons.Rounded.QrCode, contentDescription = "QR Code", tint = CrimsonPrimary)
                    }
                    Surface(
                        color = if (enabled) SignalExcellent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (enabled) "BROADCASTING" else "DISABLED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) SignalExcellent else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // SSID Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SSID Name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(ssid.ifBlank { "Not set" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onEditSsid) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rename")
                }
            }

            // Password Row
            var showPassword by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Password", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = if (showPassword) password else "••••••••",
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = if (showPassword) FontFamily.Default else FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                    TextButton(onClick = onEditPassword) {
                        Text("Change")
                    }
                }
            }

            // BSSID & Active Channel Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("BSSID (MAC)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(bssid.ifBlank { "--" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Current Channel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("Channel ${channel.ifBlank { "Auto" }}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // Transmit Power Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Transmit Power (Tx Power)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("$txPower%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = CrimsonPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(20, 40, 60, 80, 100).forEach { p ->
                        val selected = txPower == p
                        FilterChip(
                            selected = selected,
                            onClick = { onSetTxPower(p) },
                            label = { Text("$p%") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonPrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WpsControlCard(
    wpsActive: Boolean,
    isActionInProgress: Boolean,
    onTrigger: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WPS Push Button Connect",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (wpsActive) "WPS PBC Broadcast Active (2 min)" else "Quick pairing without password",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (wpsActive) SignalExcellent else MaterialTheme.colorScheme.outline
                )
            }
            Button(
                onClick = onTrigger,
                enabled = !isActionInProgress && !wpsActive,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Rounded.Sensors, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (wpsActive) "Active" else "Start WPS")
            }
        }
    }
}
