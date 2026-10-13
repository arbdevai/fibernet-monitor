package com.fibernet.monitor.ui.screens.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fibernet.monitor.data.repository.RouterRepository
import com.fibernet.monitor.data.repository.WifiFullConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WifiControlUiState(
    val isLoading: Boolean = false,
    val isActionInProgress: Boolean = false,
    val config: WifiFullConfig = WifiFullConfig(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Edit SSID Dialog
    val editingIs5G: Boolean = false,
    val showEditSsidDialog: Boolean = false,
    val inputSsid: String = "",
    // Edit Password Dialog
    val showEditPasswordDialog: Boolean = false,
    val inputPassword: String = "",
    // WPS
    val wpsActive: Boolean = false
)

class WifiControlViewModel(
    private val repository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiControlUiState())
    val uiState: StateFlow<WifiControlUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = repository.getWifiConfig()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    config = res.getOrDefault(state.config),
                    errorMessage = if (res.isFailure) res.exceptionOrNull()?.message else null
                )
            }
        }
    }

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
                        successMessage = "Nama Wi-Fi (${if (is5G) "5 GHz" else "2.4 GHz"}) berhasil diperbarui."
                    )
                }
                loadConfig()
            } else {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = "Gagal memperbarui SSID: ${res.exceptionOrNull()?.message}"
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
            _uiState.update { it.copy(errorMessage = "Kata sandi Wi-Fi minimal 8 karakter") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, showEditPasswordDialog = false) }
            val res = repository.updateWifiPassword(is5G, newPass)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Kata sandi Wi-Fi (${if (is5G) "5 GHz" else "2.4 GHz"}) berhasil diubah."
                    )
                }
                loadConfig()
            } else {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = "Gagal mengubah password: ${res.exceptionOrNull()?.message}"
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
                    successMessage = if (res.isSuccess) "Daya transmisi diatur ke $percentage%" else null,
                    errorMessage = if (res.isFailure) "Gagal mengatur daya: ${res.exceptionOrNull()?.message}" else null
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
                    successMessage = if (res.isSuccess) "Mode WPS aktif selama 2 menit. Silakan tekan tombol WPS pada perangkat klien." else null,
                    errorMessage = if (res.isFailure) "Gagal memicu WPS: ${res.exceptionOrNull()?.message}" else null
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
