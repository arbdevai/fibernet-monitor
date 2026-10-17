package com.fibernet.monitor.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fibernet.monitor.BuildConfig
import com.fibernet.monitor.data.model.AppUpdateInfo
import com.fibernet.monitor.data.model.UpdateDownloadState
import com.fibernet.monitor.data.preferences.RouterSettings
import com.fibernet.monitor.data.repository.RouterRepository
import com.fibernet.monitor.data.update.AppUpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

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
    val successMessage: String? = null,
    val appVersion: String = "v${BuildConfig.VERSION_NAME}",
    val updateState: UpdateDownloadState = UpdateDownloadState.Idle
)

class SettingsViewModel(
    private val repository: RouterRepository,
    private val appUpdateManager: AppUpdateManager = AppUpdateManager()
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
                    connectionTestResult = if (res.isSuccess) "Connection successful! Router credentials verified." else null,
                    errorMessage = if (res.isFailure) "Connection failed: ${res.exceptionOrNull()?.message}" else null
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
                it.copy(isSaving = false, successMessage = "Settings saved successfully.")
            }
        }
    }

    fun checkForUpdates(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(updateState = UpdateDownloadState.Checking) }
            val res = appUpdateManager.checkForUpdate(context, BuildConfig.VERSION_NAME)
            if (res.isSuccess) {
                val info = res.getOrThrow()
                if (info.isUpdateAvailable) {
                    _uiState.update { it.copy(updateState = UpdateDownloadState.Available(info)) }
                } else {
                    _uiState.update { it.copy(updateState = UpdateDownloadState.UpToDate) }
                }
            } else {
                val err = res.exceptionOrNull()?.message ?: "Failed to check for updates."
                _uiState.update { it.copy(updateState = UpdateDownloadState.Error(err)) }
            }
        }
    }

    fun downloadAndInstall(context: Context, updateInfo: AppUpdateInfo) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(updateState = UpdateDownloadState.Downloading(0, 0L, updateInfo.apkSize))
            }
            val res = appUpdateManager.downloadApk(context, updateInfo) { percent, downloaded, total ->
                _uiState.update {
                    it.copy(updateState = UpdateDownloadState.Downloading(percent, downloaded, total))
                }
            }
            if (res.isSuccess) {
                val file = res.getOrThrow()
                _uiState.update {
                    it.copy(updateState = UpdateDownloadState.ReadyToInstall(updateInfo, file.absolutePath))
                }
                appUpdateManager.installApk(context, file)
            } else {
                val err = res.exceptionOrNull()?.message ?: "Failed to download update."
                _uiState.update { it.copy(updateState = UpdateDownloadState.Error(err)) }
            }
        }
    }

    fun installDownloadedApk(context: Context, apkPath: String) {
        appUpdateManager.installApk(context, File(apkPath))
    }

    fun dismissUpdateState() {
        _uiState.update { it.copy(updateState = UpdateDownloadState.Idle) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null, connectionTestResult = null) }
    }
}
