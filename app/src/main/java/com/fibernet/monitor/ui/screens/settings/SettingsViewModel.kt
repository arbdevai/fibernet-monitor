package com.fibernet.monitor.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fibernet.monitor.data.preferences.RouterSettings
import com.fibernet.monitor.data.repository.RouterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val routerIp: String = "192.168.1.1",
    val username: String = "admin",
    val password: String = "%0|F?H@f!berhO3e",
    val refreshIntervalSeconds: Int = 10,
    val notifyNewDevice: Boolean = true,
    val isTesting: Boolean = false,
    val isSaving: Boolean = false,
    val connectionTestResult: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class SettingsViewModel(
    private val repository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.settingsFlow.collect { s ->
                _uiState.update {
                    it.copy(
                        routerIp = s.routerIp,
                        username = s.username,
                        password = s.password,
                        refreshIntervalSeconds = s.refreshIntervalSeconds,
                        notifyNewDevice = s.notifyNewDevice
                    )
                }
            }
        }
    }

    fun onIpChanged(v: String) = _uiState.update { it.copy(routerIp = v) }
    fun onUserChanged(v: String) = _uiState.update { it.copy(username = v) }
    fun onPassChanged(v: String) = _uiState.update { it.copy(password = v) }
    fun onIntervalChanged(v: Int) = _uiState.update { it.copy(refreshIntervalSeconds = v) }
    fun onNotifyChanged(v: Boolean) = _uiState.update { it.copy(notifyNewDevice = v) }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, connectionTestResult = null, errorMessage = null) }
            val res = repository.testConnection()
            _uiState.update {
                it.copy(
                    isTesting = false,
                    connectionTestResult = if (res.isSuccess) "Koneksi Berhasil! Kredensial router cocok." else null,
                    errorMessage = if (res.isFailure) "Koneksi Gagal: ${res.exceptionOrNull()?.message}" else null
                )
            }
        }
    }

    fun save() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            repository.saveSettings(
                routerIp = s.routerIp,
                user = s.username,
                pass = s.password,
                refreshSec = s.refreshIntervalSeconds,
                notify = s.notifyNewDevice
            )
            _uiState.update {
                it.copy(isSaving = false, successMessage = "Pengaturan berhasil disimpan.")
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, connectionTestResult = null) }
    }
}
