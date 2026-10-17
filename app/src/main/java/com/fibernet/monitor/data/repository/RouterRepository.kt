package com.fibernet.monitor.data.repository

import com.fibernet.monitor.data.model.*
import com.fibernet.monitor.data.network.RouterApiClient
import com.fibernet.monitor.data.preferences.AppPreferences
import com.fibernet.monitor.data.preferences.RouterSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class CombinedRouterStatus(
    val baseInfo: BaseInfoResponse = BaseInfoResponse(),
    val wanInfo: AllWanResponse = AllWanResponse(),
    val homeInfo: HomeInfoResponse = HomeInfoResponse(),
    val deviceInfo: DeviceInfoResponse = DeviceInfoResponse(),
    val isConnected: Boolean = false,
    val errorMessage: String? = null
) {
    val internetWan: WanItem?
        get() = wanInfo.wanList.find { it.serviceList.contains("INTERNET", ignoreCase = true) }
            ?: wanInfo.wanList.firstOrNull()
}

data class WifiFullConfig(
    val ssid24: String = "",
    val password24: String = "",
    val enabled24: Boolean = true,
    val bssid24: String = "",
    val channel24: String = "",
    val txPower24: Int = 100,
    val ssid5G: String = "",
    val password5G: String = "",
    val enabled5G: Boolean = true,
    val bssid5G: String = "",
    val channel5G: String = "",
    val txPower5G: Int = 100
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
        val baseRes = apiClient.getBaseInfo(s.routerIp, s.username, s.password)
        val wanRes = apiClient.getAllWanInfo(s.routerIp, s.username, s.password)
        val homeRes = apiClient.getHomeInfo(s.routerIp, s.username, s.password)
        val devRes = apiClient.getDeviceInfo(s.routerIp, s.username, s.password)

        return if (baseRes.isSuccess) {
            Result.success(
                CombinedRouterStatus(
                    baseInfo = baseRes.getOrNull() ?: BaseInfoResponse(),
                    wanInfo = wanRes.getOrNull() ?: AllWanResponse(),
                    homeInfo = homeRes.getOrNull() ?: HomeInfoResponse(),
                    deviceInfo = devRes.getOrNull() ?: DeviceInfoResponse(),
                    isConnected = true
                )
            )
        } else {
            val err = baseRes.exceptionOrNull()?.message
                ?: wanRes.exceptionOrNull()?.message
                ?: "Gagal terhubung ke router"
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

    suspend fun blockDevice(mac: String): Result<Boolean> {
        val s = currentSettings()
        return apiClient.blockMacAddress(s.routerIp, s.username, s.password, mac)
    }

    suspend fun unblockDevice(ruleIndex: Int): Result<Boolean> {
        val s = currentSettings()
        return apiClient.unblockMacAddress(s.routerIp, s.username, s.password, ruleIndex)
    }

    suspend fun getWifiConfig(): Result<WifiFullConfig> {
        val s = currentSettings()
        val wifi2gList = apiClient.getWifiStatus(s.routerIp, s.username, s.password, is5G = false).getOrDefault(emptyList())
        val wifi5gList = apiClient.getWifiStatus(s.routerIp, s.username, s.password, is5G = true).getOrDefault(emptyList())
        val tx2g = apiClient.getWifiTxPower(s.routerIp, s.username, s.password, is5G = false).getOrDefault(100)
        val tx5g = apiClient.getWifiTxPower(s.routerIp, s.username, s.password, is5G = true).getOrDefault(100)

        // Cari SSID 2.4G aktif utama (misal index 2 atau 1 yang enable)
        val active2g = wifi2gList.find { it.enable == "1" } ?: wifi2gList.firstOrNull() ?: WifiStatusItem()
        // Cari SSID 5G aktif utama (index 9)
        val active5g = wifi5gList.find { it.enable == "1" } ?: wifi5gList.firstOrNull() ?: WifiStatusItem()

        return Result.success(
            WifiFullConfig(
                ssid24 = active2g.ssid,
                password24 = active2g.preSharedKey,
                enabled24 = active2g.enable == "1",
                bssid24 = active2g.bssid,
                channel24 = active2g.channel,
                txPower24 = tx2g,
                ssid5G = active5g.ssid,
                password5G = active5g.preSharedKey,
                enabled5G = active5g.enable == "1",
                bssid5G = active5g.bssid,
                channel5G = active5g.channelInUse.ifBlank { active5g.channel },
                txPower5G = tx5g
            )
        )
    }

    suspend fun updateSsid(is5G: Boolean, ssid: String, enabled: Boolean): Result<Boolean> {
        val s = currentSettings()
        val currentConfig = getWifiConfig().getOrNull()
        val currentPass = if (is5G) currentConfig?.password5G ?: "" else currentConfig?.password24 ?: ""
        val targetIndex = if (is5G) 9 else 2
        return apiClient.setWifiConfig(
            ip = s.routerIp,
            user = s.username,
            pass = s.password,
            is5G = is5G,
            wifiIndex = targetIndex,
            ssid = ssid,
            password = currentPass,
            enable = enabled,
            hideBroadcast = false
        )
    }

    suspend fun updateWifiPassword(is5G: Boolean, password: String): Result<Boolean> {
        val s = currentSettings()
        val currentConfig = getWifiConfig().getOrNull()
        val currentSsid = if (is5G) currentConfig?.ssid5G ?: "" else currentConfig?.ssid24 ?: ""
        val enabled = if (is5G) currentConfig?.enabled5G ?: true else currentConfig?.enabled24 ?: true
        val targetIndex = if (is5G) 9 else 2
        return apiClient.setWifiConfig(
            ip = s.routerIp,
            user = s.username,
            pass = s.password,
            is5G = is5G,
            wifiIndex = targetIndex,
            ssid = currentSsid,
            password = password,
            enable = enabled,
            hideBroadcast = false
        )
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

    suspend fun runTraceroute(host: String): Result<List<String>> {
        val s = currentSettings()
        return apiClient.runTraceroute(s.routerIp, s.username, s.password, host)
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
