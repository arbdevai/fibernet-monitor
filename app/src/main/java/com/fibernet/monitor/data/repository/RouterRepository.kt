package com.fibernet.monitor.data.repository

import com.fibernet.monitor.data.model.*
import com.fibernet.monitor.data.network.RouterApiClient
import com.fibernet.monitor.data.preferences.AppPreferences
import com.fibernet.monitor.data.preferences.RouterSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    // In-memory cache untuk mencegah request berlebihan ke router saat berpindah tab
    private var cachedStatus: CombinedRouterStatus? = null
    private var statusCacheTime: Long = 0L

    private var cachedDevices: List<DeviceItem>? = null
    private var devicesCacheTime: Long = 0L

    private var cachedWifi: WifiFullConfig? = null
    private var wifiCacheTime: Long = 0L

    private var cachedMacFilter: MacFilterInfo? = null
    private var macFilterCacheTime: Long = 0L

    private val CACHE_TTL_MS = 6000L // 6 detik masa berlaku cache

    private suspend fun currentSettings(): RouterSettings = preferences.settingsFlow.first()

    fun invalidateCache() {
        cachedStatus = null
        statusCacheTime = 0L
        cachedDevices = null
        devicesCacheTime = 0L
        cachedWifi = null
        wifiCacheTime = 0L
        cachedMacFilter = null
        macFilterCacheTime = 0L
        apiClient.resetSession()
    }

    suspend fun saveSettings(
        routerIp: String,
        user: String,
        pass: String,
        refreshSec: Int,
        notify: Boolean
    ) {
        invalidateCache()
        preferences.updateSettings(
            routerIp = routerIp,
            username = user,
            password = pass,
            refreshIntervalSeconds = refreshSec,
            notifyNewDevice = notify
        )
    }

    suspend fun testConnection(): Result<Boolean> {
        val s = currentSettings()
        return apiClient.login(s.routerIp, s.username, s.password)
    }

    suspend fun getConnectedDevices(force: Boolean = false): Result<List<DeviceItem>> {
        val now = System.currentTimeMillis()
        if (!force && cachedDevices != null && (now - devicesCacheTime) < CACHE_TTL_MS) {
            return Result.success(cachedDevices!!)
        }

        val s = currentSettings()
        val res = apiClient.getLanStatus(s.routerIp, s.username, s.password)
        if (res.isSuccess) {
            cachedDevices = res.getOrNull()
            devicesCacheTime = now
        }
        return res
    }

    suspend fun getRouterStatus(force: Boolean = false): Result<CombinedRouterStatus> = coroutineScope {
        val now = System.currentTimeMillis()
        if (!force && cachedStatus != null && (now - statusCacheTime) < CACHE_TTL_MS) {
            return@coroutineScope Result.success(cachedStatus!!)
        }

        val s = currentSettings()

        // Autentikasi sekali di awal secara berurutan agar 4 request paralel tidak saling tunggu / berebut sesi router
        try {
            apiClient.ensureAuthenticated(s.routerIp, s.username, s.password)
        } catch (e: Exception) {
            return@coroutineScope Result.success(
                CombinedRouterStatus(
                    isConnected = false,
                    errorMessage = e.message ?: "Router authentication failed"
                )
            )
        }

        // Panggilan paralel dibatasi Semaphore(2) di RouterApiClient agar router embedded tidak overload
        val baseDeferred = async { apiClient.getBaseInfo(s.routerIp, s.username, s.password) }
        val wanDeferred = async { apiClient.getAllWanInfo(s.routerIp, s.username, s.password) }
        val homeDeferred = async { apiClient.getHomeInfo(s.routerIp, s.username, s.password) }
        val devDeferred = async { apiClient.getDeviceInfo(s.routerIp, s.username, s.password) }

        val baseRes = baseDeferred.await()
        val wanRes = wanDeferred.await()
        val homeRes = homeDeferred.await()
        val devRes = devDeferred.await()

        if (baseRes.isSuccess) {
            val status = CombinedRouterStatus(
                baseInfo = baseRes.getOrNull() ?: BaseInfoResponse(),
                wanInfo = wanRes.getOrNull() ?: AllWanResponse(),
                homeInfo = homeRes.getOrNull() ?: HomeInfoResponse(),
                deviceInfo = devRes.getOrNull() ?: DeviceInfoResponse(),
                isConnected = true
            )
            cachedStatus = status
            statusCacheTime = now
            Result.success(status)
        } else {
            val err = baseRes.exceptionOrNull()?.message
                ?: wanRes.exceptionOrNull()?.message
                ?: "Unable to communicate with router"
            Result.success(
                CombinedRouterStatus(
                    isConnected = false,
                    errorMessage = err
                )
            )
        }
    }

    suspend fun getMacFilterList(force: Boolean = false): Result<MacFilterInfo> {
        val now = System.currentTimeMillis()
        if (!force && cachedMacFilter != null && (now - macFilterCacheTime) < CACHE_TTL_MS) {
            return Result.success(cachedMacFilter!!)
        }

        val s = currentSettings()
        val res = apiClient.getMacFilterInfo(s.routerIp, s.username, s.password).map { it.filterInfo }
        if (res.isSuccess) {
            cachedMacFilter = res.getOrNull()
            macFilterCacheTime = now
        }
        return res
    }

    suspend fun blockDevice(mac: String): Result<Boolean> {
        val s = currentSettings()
        cachedMacFilter = null
        return apiClient.blockMacAddress(s.routerIp, s.username, s.password, mac)
    }

    suspend fun unblockDevice(mac: String, ruleIndex: Int = -1, ruleId: String = ""): Result<Boolean> {
        val s = currentSettings()
        cachedMacFilter = null
        return apiClient.unblockMacAddress(s.routerIp, s.username, s.password, mac, ruleIndex, ruleId)
    }

    suspend fun setMacFilterEnabled(enabled: Boolean): Result<Boolean> {
        val s = currentSettings()
        cachedMacFilter = null
        return apiClient.setMacFilterEnabled(s.routerIp, s.username, s.password, enabled)
    }

    suspend fun getWifiConfig(force: Boolean = false): Result<WifiFullConfig> = coroutineScope {
        val now = System.currentTimeMillis()
        if (!force && cachedWifi != null && (now - wifiCacheTime) < CACHE_TTL_MS) {
            return@coroutineScope Result.success(cachedWifi!!)
        }

        val s = currentSettings()

        val wifi2gDeferred = async { apiClient.getWifiStatus(s.routerIp, s.username, s.password, is5G = false) }
        val wifi5gDeferred = async { apiClient.getWifiStatus(s.routerIp, s.username, s.password, is5G = true) }
        val tx2gDeferred = async { apiClient.getWifiTxPower(s.routerIp, s.username, s.password, is5G = false) }
        val tx5gDeferred = async { apiClient.getWifiTxPower(s.routerIp, s.username, s.password, is5G = true) }

        val wifi2gList = wifi2gDeferred.await().getOrDefault(emptyList())
        val wifi5gList = wifi5gDeferred.await().getOrDefault(emptyList())
        val tx2g = tx2gDeferred.await().getOrDefault(100)
        val tx5g = tx5gDeferred.await().getOrDefault(100)

        val active2g = wifi2gList.find { it.enable == "1" } ?: wifi2gList.firstOrNull() ?: WifiStatusItem()
        val active5g = wifi5gList.find { it.enable == "1" } ?: wifi5gList.firstOrNull() ?: WifiStatusItem()

        val config = WifiFullConfig(
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
        cachedWifi = config
        wifiCacheTime = now
        Result.success(config)
    }

    suspend fun updateSsid(is5G: Boolean, ssid: String, enabled: Boolean): Result<Boolean> {
        val s = currentSettings()
        val currentConfig = getWifiConfig().getOrNull()
        val currentPass = if (is5G) currentConfig?.password5G ?: "" else currentConfig?.password24 ?: ""
        val targetIndex = if (is5G) 9 else 2
        cachedWifi = null
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
        cachedWifi = null
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
        cachedWifi = null
        return apiClient.setWifiTxPower(s.routerIp, s.username, s.password, is5G, percentage)
    }

    suspend fun triggerWps(): Result<Boolean> {
        val s = currentSettings()
        return apiClient.triggerWps(s.routerIp, s.username, s.password)
    }

    suspend fun rebootRouter(): Result<Boolean> {
        val s = currentSettings()
        invalidateCache()
        return apiClient.rebootRouter(s.routerIp, s.username, s.password)
    }

    suspend fun runPingTest(host: String): Result<PingResultResponse> {
        val s = currentSettings()
        val wanStatus = getRouterStatus().getOrNull()
        val wanUrl = wanStatus?.internetWan?.wanUrl ?: "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1."
        return apiClient.runPingTest(s.routerIp, s.username, s.password, host, wanUrl)
    }

    suspend fun runTraceroute(host: String): Result<List<String>> {
        val s = currentSettings()
        val wanStatus = getRouterStatus().getOrNull()
        val wanUrl = wanStatus?.internetWan?.wanUrl ?: "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1."
        return apiClient.runTraceroute(s.routerIp, s.username, s.password, host, wanUrl)
    }
}
