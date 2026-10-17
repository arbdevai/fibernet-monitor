package com.fibernet.monitor.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fibernet.monitor.data.model.DeviceItem
import com.fibernet.monitor.data.repository.CombinedRouterStatus
import com.fibernet.monitor.data.repository.RouterRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DeviceFilterTab {
    ALL, BAND_24G, BAND_5G, LAN
}

data class DashboardUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val devices: List<DeviceItem> = emptyList(),
    val routerStatus: CombinedRouterStatus = CombinedRouterStatus(),
    val selectedFilter: DeviceFilterTab = DeviceFilterTab.ALL,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val deviceToBlock: DeviceItem? = null,
    val isActionInProgress: Boolean = false
) {
    val filteredDevices: List<DeviceItem>
        get() = devices.filter { dev ->
            val matchTab = when (selectedFilter) {
                DeviceFilterTab.ALL -> true
                DeviceFilterTab.BAND_24G -> dev.accessType.contains("2.4", ignoreCase = true)
                DeviceFilterTab.BAND_5G -> dev.accessType.contains("5G", ignoreCase = true)
                DeviceFilterTab.LAN -> dev.accessType.contains("LAN", ignoreCase = true) || dev.rssi == 0
            }
            val matchSearch = if (searchQuery.isBlank()) true else {
                dev.hostName.contains(searchQuery, ignoreCase = true) ||
                dev.ipAddress.contains(searchQuery, ignoreCase = true) ||
                dev.macAddress.contains(searchQuery, ignoreCase = true)
            }
            matchTab && matchSearch
        }

    val totalUpKbps: Long
        get() = devices.sumOf { it.upSpeedKbps }

    val totalDownKbps: Long
        get() = devices.sumOf { it.downSpeedKbps }
}

class DashboardViewModel(
    private val repository: RouterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        loadData(isInitial = true)
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                val interval = settings.refreshIntervalSeconds
                if (interval > 0) {
                    while (isActive) {
                        delay(interval * 1000L)
                        loadData(isInitial = false)
                    }
                }
            }
        }
    }

    fun loadData(isInitial: Boolean = false) {
        viewModelScope.launch {
            if (isInitial) _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            else _uiState.update { it.copy(isRefreshing = true) }

            val statusRes = repository.getRouterStatus()
            val devicesRes = repository.getConnectedDevices()

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isRefreshing = false,
                    routerStatus = statusRes.getOrDefault(state.routerStatus),
                    devices = devicesRes.getOrDefault(state.devices),
                    errorMessage = if (devicesRes.isFailure) devicesRes.exceptionOrNull()?.message else null
                )
            }
        }
    }

    fun setFilterTab(tab: DeviceFilterTab) {
        _uiState.update { it.copy(selectedFilter = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun showBlockConfirmation(device: DeviceItem) {
        _uiState.update { it.copy(deviceToBlock = device) }
    }

    fun dismissBlockConfirmation() {
        _uiState.update { it.copy(deviceToBlock = null) }
    }

    fun confirmBlockDevice() {
        val target = _uiState.value.deviceToBlock ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionInProgress = true, deviceToBlock = null) }
            val res = repository.blockDevice(target.macAddress)
            if (res.isSuccess) {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        successMessage = "Perangkat '${target.displayName}' berhasil diblokir."
                    )
                }
                loadData(isInitial = false)
            } else {
                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        errorMessage = "Gagal memblokir: ${res.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
