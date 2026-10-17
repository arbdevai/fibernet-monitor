package com.fibernet.monitor.ui.screens.wifi

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fibernet.monitor.data.model.WifiAnalyzerState
import com.fibernet.monitor.data.model.WifiSignalSample
import com.fibernet.monitor.data.model.WifiStatusItem
import com.fibernet.monitor.data.repository.RouterRepository
import com.fibernet.monitor.data.repository.WifiFullConfig
import com.fibernet.monitor.data.wifi.WifiAnalyzerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WifiScreenSubTab(val title: String) {
    CONFIG("Wi-Fi & SSIDs"),
    ANALYZER("Net Analyzer"),
    SIGNAL_METER("Coverage Meter")
}

data class WifiControlUiState(
    val isLoading: Boolean = false,
    val isActionInProgress: Boolean = false,
    val config: WifiFullConfig = WifiFullConfig(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedSubTab: WifiScreenSubTab = WifiScreenSubTab.CONFIG,

    // Edit SSID Dialog (Primary)
    val editingIs5G: Boolean = false,
    val showEditSsidDialog: Boolean = false,
    val inputSsid: String = "",

    // Edit Password Dialog (Primary)
    val showEditPasswordDialog: Boolean = false,
    val inputPassword: String = "",

    // Create / Edit Multi-SSID Dialog
    val showCreateEditDialog: Boolean = false,
    val targetSsidItem: WifiStatusItem? = null,
    val targetIs5G: Boolean = false,

    // Net Analyzer & Live Signal State
    val analyzerState: WifiAnalyzerState = WifiAnalyzerState(),

    // WPS
    val wpsActive: Boolean = false
)

class WifiControlViewModel(
    private val repository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiControlUiState())
    val uiState: StateFlow<WifiControlUiState> = _uiState.asStateFlow()

    private var signalMonitorJob: Job? = null

    init {
        loadConfig()
    }

    fun selectSubTab(tab: WifiScreenSubTab, context: Context) {
        _uiState.update { it.copy(selectedSubTab = tab) }
        when (tab) {
            WifiScreenSubTab.CONFIG -> {
                stopLiveSignalMonitor()
                loadConfig()
            }
            WifiScreenSubTab.ANALYZER -> {
                stopLiveSignalMonitor()
                scanChannels(context)
            }
            WifiScreenSubTab.SIGNAL_METER -> {
                startLiveSignalMonitor(context)
            }
        }
    }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = repository.getWifiConfig(force = true)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    config = res.getOrDefault(state.config),
                    errorMessage = if (res.isFailure) res.exceptionOrNull()?.message else null
                )
            }
        }
    }

    // --- Multi-SSID Dialog Management ---
    fun showCreateSsidDialog(is5G: Boolean, existingItem: WifiStatusItem? = null) {
        _uiState.update {
            it.copy(
                showCreateEditDialog = true,
                targetIs5G = is5G,
                targetSsidItem = existingItem
            )
        }
    }

    fun dismissCreateSsidDialog() {
        _uiState.update { it.copy(showCreateEditDialog = false, targetSsidItem = null) }
    }

    fun saveSsidFull(
        is5G: Boolean,
        wifiIndex: Int,
        ssid: String,
        password: String,
        enabled: Boolean,
        hideBroadcast: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, showCreateEditDialog = false) }
            val res = repository.saveSsidFull(
                is5G = is5G,
                wifiIndex = wifiIndex,
                ssid = ssid,
                password = password,
                enable = enabled,
                hideBroadcast = hideBroadcast
            )
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "SSID '$ssid' (${if (is5G) "5 GHz" else "2.4 GHz"}) successfully updated on router."
                    )
                }
                loadConfig()
            } else {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = "Failed to apply SSID: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    // --- Net Analyzer & Real-time Wi-Fi Scanner ---
    fun scanChannels(context: Context) {
        viewModelScope.launch {
            val hasPerms = WifiAnalyzerManager.hasRequiredPermissions(context)
            val isLocOn = WifiAnalyzerManager.isLocationServiceEnabled(context)

            _uiState.update {
                it.copy(
                    analyzerState = it.analyzerState.copy(
                        isScanning = true,
                        hasPermission = hasPerms,
                        isLocationEnabled = isLocOn
                    )
                )
            }

            if (!hasPerms) {
                _uiState.update {
                    it.copy(
                        analyzerState = it.analyzerState.copy(
                            isScanning = false,
                            errorMessage = "Location and Wi-Fi scan permissions are required to scan real-time channels."
                        )
                    )
                }
                return@launch
            }

            val (channels24G, channels5G, allAps) = WifiAnalyzerManager.scanAndAnalyzeChannels(context)
            val best24G = channels24G.find { it.isBestChoice }?.channel ?: 1
            val best5G = channels5G.find { it.isBestChoice }?.channel ?: 36

            _uiState.update {
                it.copy(
                    analyzerState = it.analyzerState.copy(
                        isScanning = false,
                        channels24G = channels24G,
                        channels5G = channels5G,
                        allAccessPoints = allAps,
                        bestChannel24G = best24G,
                        bestChannel5G = best5G,
                        lastScanTime = System.currentTimeMillis(),
                        errorMessage = null
                    )
                )
            }
        }
    }

    // --- Real-time Coverage Walk-Test Monitor ---
    fun startLiveSignalMonitor(context: Context) {
        signalMonitorJob?.cancel()
        val hasPerms = WifiAnalyzerManager.hasRequiredPermissions(context)
        _uiState.update {
            it.copy(
                analyzerState = it.analyzerState.copy(
                    hasPermission = hasPerms,
                    isLocationEnabled = WifiAnalyzerManager.isLocationServiceEnabled(context)
                )
            )
        }

        signalMonitorJob = viewModelScope.launch {
            WifiAnalyzerManager.monitorLiveSignal(context, intervalMs = 1000L).collect { sample ->
                _uiState.update { state ->
                    val currentHistory = state.analyzerState.signalHistory.toMutableList()
                    if (sample != null) {
                        currentHistory.add(sample)
                        if (currentHistory.size > 30) {
                            currentHistory.removeAt(0)
                        }
                    }
                    state.copy(
                        analyzerState = state.analyzerState.copy(
                            currentSignal = sample,
                            signalHistory = currentHistory
                        )
                    )
                }
            }
        }
    }

    fun stopLiveSignalMonitor() {
        signalMonitorJob?.cancel()
        signalMonitorJob = null
    }

    // --- Auto-Apply Recommended Channel to Router ---
    fun applyChannelToRouter(is5G: Boolean, channel: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val res = repository.setWifiChannel(is5G, channel)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Channel $channel successfully applied to router (${if (is5G) "5 GHz" else "2.4 GHz"})."
                    )
                }
                loadConfig()
            } else {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = "Failed to change router channel: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    // --- Legacy / Primary SSID Helpers ---
    fun showEditSsid(is5G: Boolean) {
        val currentSsid = if (is5G) _uiState.value.config.ssid5G else _uiState.value.config.ssid24
        _uiState.update {
            it.copy(
                editingIs5G = is5G,
                showEditSsidDialog = true,
                inputSsid = currentSsid
            )
        }
    }

    fun dismissEditSsid() {
        _uiState.update { it.copy(showEditSsidDialog = false) }
    }

    fun onInputSsidChanged(value: String) {
        _uiState.update { it.copy(inputSsid = value) }
    }

    fun saveSsid() {
        val is5G = _uiState.value.editingIs5G
        val newSsid = _uiState.value.inputSsid.trim()
        if (newSsid.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Nama SSID tidak boleh kosong") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, showEditSsidDialog = false) }
            val enabled = if (is5G) _uiState.value.config.enabled5G else _uiState.value.config.enabled24
            val res = repository.updateSsid(is5G, newSsid, enabled)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Wi-Fi SSID (${if (is5G) "5 GHz" else "2.4 GHz"}) updated successfully."
                    )
                }
                loadConfig()
            } else {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = "Failed to update SSID: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun showEditPassword(is5G: Boolean) {
        val currentPass = if (is5G) _uiState.value.config.password5G else _uiState.value.config.password24
        _uiState.update {
            it.copy(
                editingIs5G = is5G,
                showEditPasswordDialog = true,
                inputPassword = currentPass
            )
        }
    }

    fun dismissEditPassword() {
        _uiState.update { it.copy(showEditPasswordDialog = false) }
    }

    fun onInputPasswordChanged(value: String) {
        _uiState.update { it.copy(inputPassword = value) }
    }

    fun savePassword() {
        val is5G = _uiState.value.editingIs5G
        val newPass = _uiState.value.inputPassword
        if (newPass.length < 8) {
            _uiState.update { it.copy(errorMessage = "Wi-Fi password must be at least 8 characters.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, showEditPasswordDialog = false) }
            val res = repository.updateWifiPassword(is5G, newPass)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Wi-Fi password (${if (is5G) "5 GHz" else "2.4 GHz"}) updated successfully."
                    )
                }
                loadConfig()
            } else {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = "Failed to update password: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun setTxPower(is5G: Boolean, percentage: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val res = repository.updateTxPower(is5G, percentage)
            _uiState.update {
                it.copy(
                    isActionInProgress = false,
                    successMessage = if (res.isSuccess) "Transmit power set to $percentage%" else null,
                    errorMessage = if (res.isFailure) "Failed to set transmit power: ${res.exceptionOrNull()?.message}" else null
                )
            }
        }
    }

    fun triggerWps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true) }
            val res = repository.triggerWps()
            _uiState.update {
                it.copy(
                    isActionInProgress = false,
                    wpsActive = res.isSuccess,
                    successMessage = if (res.isSuccess) "WPS active for 2 minutes. Press the WPS button on your client device." else null,
                    errorMessage = if (res.isFailure) "Failed to trigger WPS: ${res.exceptionOrNull()?.message}" else null
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveSignalMonitor()
    }
}
