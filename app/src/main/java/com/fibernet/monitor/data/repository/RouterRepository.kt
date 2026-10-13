package com.fibernet.monitor.data.repository

import com.fibernet.monitor.data.model.*
import com.fibernet.monitor.data.network.RouterApiClient
import com.fibernet.monitor.data.preferences.AppPreferences
import com.fibernet.monitor.data.preferences.RouterSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class CombinedRouterStatus(
    val info: DeviceInfoResponse = DeviceInfoResponse(),
    val home: HomeInfoResponse = HomeInfoResponse(),
    val isConnected: Boolean = false,
    val errorMessage: String? = null
)

data class WifiFullConfig(
    val ssid24: String = "",
    val password24: String = "",
    val enabled24: Boolean = true,
    val ssid5G: String = "",
    val password5G: String = "",
    val enabled5G: Boolean = true
)

class RouterRepository(
    private val apiClient: RouterApiClient,
    private val preferences: AppPreferences
) {
    val settingsFlow: Flow<RouterSettings> = preferences.settingsFlow

    private suspend fun currentSettings(): RouterSettings = preferences.settingsFlow.first()

    suspend fun testConnection(): Result<Boolean> {
        val s = currentSettings()
        return apiClient.login(s.routerIp, s.username, s.password)
    }

    suspend fun getConnectedDevices(): Result<List<DeviceItem>> {
        val s = currentSettings()
        return apiClient.getLanStatus(s.routerIp, s.username, s.password)
    }

    suspend fun getRouterStatus(): Result<CombinedRouterStatus> {
        val s = currentSettings()
        val devRes = apiClient.getDeviceInfo(s.routerIp, s.username, s.password)
        val homeRes = apiClient.getHomeInfo(s.routerIp, s.username, s.password)

        return if (devRes.isSuccess && homeRes.isSuccess) {
            Result.success(
                CombinedRouterStatus(
                    info = devRes.getOrNull() ?: DeviceInfoResponse(),
                    home = homeRes.getOrNull() ?: HomeInfoResponse(),
                    isConnected = true
                )
            )
        } else {
            val err = devRes.exceptionOrNull()?.message ?: homeRes.exceptionOrNull()?.message
            Result.success(
                CombinedRouterStatus(
                    isConnected = false,
                    errorMessage = err
                )
            )
        }
    }

    suspend fun getMacFilterList(): Result<MacFilterInfo> {
        val s = currentSettings()
        return apiClient.getMacFilterInfo(s.routerIp, s.username, s.password).map { it.filterInfo }
    }

    suspend fun blockDevice(mac: String, name: String): Result<Boolean> {
        val s = currentSettings()
        return apiClient.blockMacAddress(s.routerIp, s.username, s.password, mac, name)
    }

    suspend fun unblockDevice(mac: String): Result<Boolean> {
        val s = currentSettings()
        return apiClient.unblockMacAddress(s.routerIp, s.username, s.password, mac)
    }

    suspend fun getWifiConfig(): Result<WifiFullConfig> {
        val s = currentSettings()
        val b24 = apiClient.getWifiBasic(s.routerIp, s.username, s.password, false).getOrNull()
        val a24 = apiClient.getWifiAdvanced(s.routerIp, s.username, s.password, false).getOrNull()
        val b5 = apiClient.getWifiBasic(s.routerIp, s.username, s.password, true).getOrNull()
        val a5 = apiClient.getWifiAdvanced(s.routerIp, s.username, s.password, true).getOrNull()

        return Result.success(
            WifiFullConfig(
                ssid24 = b24?.ssid ?: "FiberHome-2.4G",
                password24 = a24?.preSharedKey ?: "",
                enabled24 = b24?.enable == "1",
                ssid5G = b5?.ssid ?: "FiberHome-5G",
                password5G = a5?.preSharedKey ?: "",
                enabled5G = b5?.enable == "1"
            )
        )
    }

    suspend fun updateSsid(is5G: Boolean, ssid: String, enabled: Boolean): Result<Boolean> {
        val s = currentSettings()
        return apiClient.setWifiBasic(s.routerIp, s.username, s.password, is5G, ssid, enabled)
    }

    suspend fun updateWifiPassword(is5G: Boolean, password: String): Result<Boolean> {
        val s = currentSettings()
        return apiClient.setWifiPassword(s.routerIp, s.username, s.password, is5G, password)
    }

    suspend fun updateTxPower(is5G: Boolean, percentage: Int): Result<Boolean> {
        val s = currentSettings()
        return apiClient.setWifiTxPower(s.routerIp, s.username, s.password, is5G, percentage)
    }

    suspend fun triggerWps(): Result<Boolean> {
        val s = currentSettings()
        return apiClient.triggerWps(s.routerIp, s.username, s.password)
    }

    suspend fun rebootRouter(): Result<Boolean> {
        val s = currentSettings()
        return apiClient.rebootRouter(s.routerIp, s.username, s.password)
    }

    suspend fun runPingTest(host: String): Result<PingResultResponse> {
        val s = currentSettings()
        return apiClient.runPing(s.routerIp, s.username, s.password, host)
    }

    suspend fun saveSettings(
        routerIp: String,
        user: String,
        pass: String,
        refreshSec: Int,
        notify: Boolean
    ) {
        preferences.updateSettings(routerIp, user, pass, refreshSec, notify)
    }
}
