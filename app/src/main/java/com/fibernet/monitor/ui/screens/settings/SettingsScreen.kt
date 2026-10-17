package com.fibernet.monitor.ui.screens.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.fibernet.monitor.data.model.AppUpdateInfo
import com.fibernet.monitor.data.model.UpdateDownloadState
import com.fibernet.monitor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage, state.successMessage, state.connectionTestResult) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.connectionTestResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Router Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "ROUTER CREDENTIALS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = state.routerIp,
                        onValueChange = { viewModel.onIpChanged(it) },
                        label = { Text("Gateway IP Address") },
                        placeholder = { Text("192.168.1.1") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Router, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            cursorColor = CrimsonPrimary
                        )
                    )

                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { viewModel.onUserChanged(it) },
                        label = { Text("Username") },
                        placeholder = { Text("admin") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CrimsonPrimary,
                            cursorColor = CrimsonPrimary
                        )
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.onPassChanged(it) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
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

                    // Interval Refresh
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "AUTO-REFRESH INTERVAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                5 to "5s",
                                10 to "10s",
                                30 to "30s",
                                0 to "Manual"
                            ).forEach { (sec, label) ->
                                val isSelected = state.refreshIntervalSeconds == sec
                                OutlinedButton(
                                    onClick = { viewModel.onIntervalChanged(sec) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = if (isSelected) ButtonDefaults.outlinedButtonColors(
                                        containerColor = CrimsonPrimary,
                                        contentColor = androidx.compose.ui.graphics.Color.White
                                    ) else ButtonDefaults.outlinedButtonColors(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.testConnection() },
                            enabled = !state.isTesting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Test Connection")
                            }
                        }

                        Button(
                            onClick = { viewModel.save() },
                            enabled = !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Settings")
                        }
                    }
                }
            }

            // In-App Updates & Version Card
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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "App Updates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = state.appVersion,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    when (val uState = state.updateState) {
                        is UpdateDownloadState.Idle -> {
                            Text(
                                text = "Keep FiberNet Monitor updated for the latest router compatibility and security features.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            OutlinedButton(
                                onClick = { viewModel.checkForUpdates(context) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Check for Updates")
                            }
                        }
                        is UpdateDownloadState.Checking -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = CrimsonPrimary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Checking for latest release on GitHub...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        is UpdateDownloadState.UpToDate -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = SignalExcellent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Your application is up to date (${state.appVersion}).",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = SignalExcellent
                                )
                            }
                            TextButton(
                                onClick = { viewModel.checkForUpdates(context) },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Check Again", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        is UpdateDownloadState.Available -> {
                            val info = uState.updateInfo
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CrimsonContainerLight.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "New Update: ${info.latestVersion}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = OnCrimsonContainerLight
                                        )
                                        if (info.apkSize > 0) {
                                            Text(
                                                text = "${info.apkSize / (1024 * 1024)} MB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = OnCrimsonContainerLight.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    Text(
                                        text = info.releaseNotes.take(200).ifBlank { "New version available with improvements and fixes." },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OnCrimsonContainerLight.copy(alpha = 0.9f)
                                    )
                                }
                            }

                            Button(
                                onClick = { viewModel.downloadAndInstall(context, info) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = if (info.isLocalFileReady) Icons.Rounded.InstallMobile else Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (info.isLocalFileReady) "Install Update (Ready)" else "Download & Install Update")
                            }
                        }
                        is UpdateDownloadState.Downloading -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Downloading update...",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${uState.progressPercent}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = CrimsonPrimary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { uState.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = CrimsonPrimary
                                )
                            }
                        }
                        is UpdateDownloadState.ReadyToInstall -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = SignalExcellent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Update downloaded (${uState.updateInfo.latestVersion}). Ready to install.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Button(
                                    onClick = { viewModel.installDownloadedApk(context, uState.apkPath) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.InstallMobile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install APK Now")
                                }
                            }
                        }
                        is UpdateDownloadState.Error -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = CrimsonPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = uState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CrimsonPrimary
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.checkForUpdates(context) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            // Connection Guide Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = CrimsonPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Connection Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "1. Connect your smartphone to the FiberHome router Wi-Fi network.\n2. The default FiberHome gateway IP is 192.168.1.1.\n3. The block feature registers target MAC addresses directly to the router hardware firewall blacklist.\n4. In-App Updates download directly from official GitHub releases and install seamlessly via FileProvider.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
