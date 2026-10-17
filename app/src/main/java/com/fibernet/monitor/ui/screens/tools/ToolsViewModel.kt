package com.fibernet.monitor.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fibernet.monitor.data.db.DisconnectionLog
import com.fibernet.monitor.data.db.FiberNetDatabaseHelper
import com.fibernet.monitor.data.db.KnownDevice
import com.fibernet.monitor.data.model.PingResultResponse
import com.fibernet.monitor.data.repository.CombinedRouterStatus
import com.fibernet.monitor.data.repository.RouterRepository
import com.fibernet.monitor.data.speedtest.MlabSpeedtestClient
import com.fibernet.monitor.data.speedtest.SpeedtestFinalResult
import com.fibernet.monitor.data.speedtest.SpeedtestPhase
import com.fibernet.monitor.data.speedtest.SpeedtestProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ToolsUiState(
    val isActionInProgress: Boolean = false,
    val isPinging: Boolean = false,
    val pingHostInput: String = "8.8.8.8",
    val pingResult: PingResultResponse? = null,
    val isTracing: Boolean = false,
    val traceHostInput: String = "1.1.1.1",
    val traceResults: List<String> = emptyList(),
    val routerStatus: CombinedRouterStatus = CombinedRouterStatus(),
    val showRebootDialog: Boolean = false,
    val isRebooting: Boolean = false,
    val speedtestProgress: SpeedtestProgress? = null,
    val speedtestFinalResult: SpeedtestFinalResult? = null,
    val isSpeedtesting: Boolean = false,
    val disconnectionLogs: List<DisconnectionLog> = emptyList(),
    val knownDevices: List<KnownDevice> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ToolsViewModel(
    private val repository: RouterRepository,
    private val dbHelper: FiberNetDatabaseHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    private val speedtestClient = MlabSpeedtestClient()
    private var speedtestJob: Job? = null

    init {
        loadStatus()
        loadHistory()
    }

    fun loadStatus() {
        viewModelScope.launch {
            val res = repository.getRouterStatus()
            if (res.isSuccess) {
                _uiState.update { it.copy(routerStatus = res.getOrThrow()) }
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            val logs = dbHelper.getDisconnectionLogs(limit = 20)
            val devices = dbHelper.getKnownDevices()
            _uiState.update {
                it.copy(
                    disconnectionLogs = logs,
                    knownDevices = devices
                )
            }
        }
    }

    fun updateDeviceAlias(mac: String, alias: String) {
        viewModelScope.launch {
            dbHelper.setCustomDeviceName(mac, alias)
            loadHistory()
            _uiState.update { it.copy(successMessage = "Nama perangkat berhasil diperbarui.") }
        }
    }

    fun startSpeedtest() {
        if (_uiState.value.isSpeedtesting) return

        speedtestJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSpeedtesting = true,
                    speedtestProgress = SpeedtestProgress(phase = SpeedtestPhase.LOCATING),
                    speedtestFinalResult = null,
                    errorMessage = null
                )
            }

            try {
                val finalResult = speedtestClient.executeSpeedtest { progress ->
                    _uiState.update { it.copy(speedtestProgress = progress) }
                }
                _uiState.update {
                    it.copy(
                        isSpeedtesting = false,
                        speedtestFinalResult = finalResult,
                        successMessage = "Uji kecepatan M-Lab selesai."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSpeedtesting = false,
                        errorMessage = "Speedtest gagal: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun cancelSpeedtest() {
        speedtestJob?.cancel()
        _uiState.update {
            it.copy(
                isSpeedtesting = false,
                speedtestProgress = null
            )
        }
    }

    fun onPingHostChanged(value: String) {
        _uiState.update { it.copy(pingHostInput = value) }
    }

    fun onTraceHostChanged(value: String) {
        _uiState.update { it.copy(traceHostInput = value) }
    }

    fun runPing() {
        val host = _uiState.value.pingHostInput.trim()
        if (host.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Masukkan host atau IP untuk tes ping") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isPinging = true, pingResult = null, errorMessage = null) }
            val res = repository.runPingTest(host)
            _uiState.update {
                it.copy(
                    isPinging = false,
                    pingResult = res.getOrNull(),
                    errorMessage = if (res.isFailure) res.exceptionOrNull()?.message else null
                )
            }
        }
    }

    fun runTraceroute() {
        val host = _uiState.value.traceHostInput.trim()
        if (host.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Masukkan host atau IP untuk traceroute") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTracing = true, traceResults = emptyList(), errorMessage = null) }
            val res = repository.runTraceroute(host)
            _uiState.update {
                it.copy(
                    isTracing = false,
                    traceResults = res.getOrDefault(emptyList()),
                    errorMessage = if (res.isFailure) res.exceptionOrNull()?.message else null
                )
            }
        }
    }

    fun showRebootConfirmation() {
        _uiState.update { it.copy(showRebootDialog = true) }
    }

    fun dismissRebootConfirmation() {
        _uiState.update { it.copy(showRebootDialog = false) }
    }

    fun confirmReboot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRebooting = true, showRebootDialog = false) }
            val res = repository.rebootRouter()
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isRebooting = false,
                        successMessage = "Perintah reboot berhasil dikirim ke router. Perangkat akan restart dalam 60 detik."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isRebooting = false,
                        errorMessage = "Gagal me-reboot: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
