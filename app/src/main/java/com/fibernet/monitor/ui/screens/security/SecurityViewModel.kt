package com.fibernet.monitor.ui.screens.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fibernet.monitor.data.model.MacFilterInfo
import com.fibernet.monitor.data.model.MacFilterRule
import com.fibernet.monitor.data.repository.RouterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SecurityUiState(
    val isLoading: Boolean = false,
    val isActionInProgress: Boolean = false,
    val filterInfo: MacFilterInfo = MacFilterInfo(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val showAddDialog: Boolean = false,
    val inputMac: String = "",
    val inputName: String = "",
    val macToUnblock: MacFilterRule? = null
)

class SecurityViewModel(
    private val repository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

    init {
        loadFilterList()
    }

    fun loadFilterList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val res = repository.getMacFilterList()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    filterInfo = res.getOrDefault(state.filterInfo),
                    errorMessage = if (res.isFailure) res.exceptionOrNull()?.message else null
                )
            }
        }
    }

    fun showAddBlockDialog() {
        _uiState.update { it.copy(showAddDialog = true, inputMac = "", inputName = "") }
    }

    fun dismissAddBlockDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun onInputMacChanged(value: String) {
        _uiState.update { it.copy(inputMac = value) }
    }

    fun onInputNameChanged(value: String) {
        _uiState.update { it.copy(inputName = value) }
    }

    fun submitManualBlock() {
        val mac = _uiState.value.inputMac.trim()
        val name = _uiState.value.inputName.trim().ifBlank { "Blocked Device" }

        if (!isValidMac(mac)) {
            _uiState.update { it.copy(errorMessage = "Invalid MAC address format (Example: AA:BB:CC:DD:EE:FF)") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, showAddDialog = false) }
            val res = repository.blockDevice(mac)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "MAC $mac added to router blocklist."
                    )
                }
                loadFilterList()
            } else {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = "Failed to block device: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun showUnblockConfirmation(rule: MacFilterRule) {
        _uiState.update { it.copy(macToUnblock = rule) }
    }

    fun dismissUnblockConfirmation() {
        _uiState.update { it.copy(macToUnblock = null) }
    }

    fun confirmUnblock() {
        val target = _uiState.value.macToUnblock ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, macToUnblock = null) }
            val ruleIndex = _uiState.value.filterInfo.filterList.indexOf(target).takeIf { it >= 0 }
                ?: (target.id.toIntOrNull()?.minus(1) ?: 0)
            val res = repository.unblockDevice(ruleIndex)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "MAC ${target.mac} unblocked successfully."
                    )
                }
                loadFilterList()
            } else {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = "Failed to unblock device: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun isValidMac(mac: String): Boolean {
        val regex = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return regex.matches(mac)
    }
}
