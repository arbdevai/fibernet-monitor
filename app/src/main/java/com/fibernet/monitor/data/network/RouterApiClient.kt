package com.fibernet.monitor.data.network

import com.fibernet.monitor.data.crypto.RouterCrypto
import com.fibernet.monitor.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class RouterApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // Bounded concurrency (maksimal 2 request simultan) agar CPU router embedded tidak drop/hang
    private val requestSemaphore = Semaphore(2)

    @Volatile
    private var lastSessionId: String = ""

    @Volatile
    private var currentAesKey: String = ""

    @Volatile
    private var isLoggedIn: Boolean = false

    @Volatile
    private var activeRouterIp: String = ""

    fun resetSession() {
        lastSessionId = ""
        currentAesKey = ""
        isLoggedIn = false
        activeRouterIp = ""
    }

    private suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        requestSemaphore.withPermit {
            try {
                Result.success(block())
            } catch (e: SocketTimeoutException) {
                Result.failure(Exception("Connection to router timed out. Please verify Wi-Fi connection."))
            } catch (e: ConnectException) {
                resetSession()
                Result.failure(Exception("Unable to reach router gateway. Please verify router IP address."))
            } catch (e: UnknownHostException) {
                resetSession()
                Result.failure(Exception("Router host not found. Please verify gateway configuration."))
            } catch (e: Exception) {
                val msg = e.message ?: "An unexpected router communication error occurred."
                Result.failure(Exception(msg))
            }
        }
    }

    suspend fun getFreshSessionId(ip: String): String = withContext(Dispatchers.IO) {
        val sessUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_refresh_sessionid"
        val sessReq = Request.Builder().url(sessUrl).get().build()
        client.newCall(sessReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Failed to acquire fresh session token (HTTP ${resp.code})")
            val body = resp.body?.string() ?: ""
            val obj = json.parseToJsonElement(body).jsonObject
            val id = obj["sessionid"]?.jsonPrimitive?.content ?: ""
            if (id.isNotBlank()) lastSessionId = id
            id
        }
    }

    suspend fun login(ip: String, username: String, pass: String): Result<Boolean> = safeApiCall {
        if (ip != activeRouterIp) {
            resetSession()
            activeRouterIp = ip
        }

        // 1. Dapatkan acsRandom
        val acsUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_acs_random"
        val acsReq = Request.Builder().url(acsUrl).get().build()
        val acsResp = client.newCall(acsReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Failed to contact router (HTTP ${resp.code})")
            val body = resp.body?.string() ?: throw Exception("Empty response from router")
            val obj = json.parseToJsonElement(body).jsonObject
            obj["acsRandom"]?.jsonPrimitive?.content ?: throw Exception("Invalid security token from router")
        }

        val aesKey = RouterCrypto.extractAesKey(acsResp)
        currentAesKey = aesKey
        val encryptedPass = RouterCrypto.encryptPassword(pass, aesKey)

        // 2. Dapatkan refresh session id
        val sessId = getFreshSessionId(ip)

        // 3. Kirim do_login
        val loginBody = FormBody.Builder()
            .add("username", username)
            .add("loginpd", encryptedPass)
            .add("port", "0")
            .add("sessionid", sessId)
            .add("ajaxmethod", "do_login")
            .add("_", System.currentTimeMillis().toString())
            .build()

        val loginReq = Request.Builder()
            .url("http://$ip/cgi-bin/ajax")
            .post(loginBody)
            .build()

        client.newCall(loginReq).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val obj = json.parseToJsonElement(body).jsonObject
            val loginResult = obj["login_result"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
            if (loginResult == 0) {
                lastSessionId = sessId
                isLoggedIn = true
                activeRouterIp = ip
                true
            } else {
                isLoggedIn = false
                throw Exception("Authentication failed (Code $loginResult). Check username and password.")
            }
        }
    }

    private suspend fun ensureAuthenticated(ip: String, username: String, pass: String) {
        if (ip != activeRouterIp) {
            resetSession()
            activeRouterIp = ip
        }
        if (!isLoggedIn || currentAesKey.isBlank()) {
            val res = login(ip, username, pass)
            if (res.isFailure) throw res.exceptionOrNull() ?: Exception("Router authentication failed")
        }
    }

    // Helper untuk auto re-login saat FiberHome mengembalikan session_valid == 0
    private suspend fun executeGet(ip: String, user: String, pass: String, method: String): String {
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=$method"
        var respBody = client.newCall(Request.Builder().url(url).get().build()).execute().use { it.body?.string() ?: "" }

        if (respBody.contains("\"session_valid\":0") || respBody.contains("\"session_valid\":\"0\"")) {
            // Sesi kedaluwarsa atau router berganti, re-login transparan
            resetSession()
            ensureAuthenticated(ip, user, pass)
            respBody = client.newCall(Request.Builder().url(url).get().build()).execute().use { it.body?.string() ?: "" }
        }
        return respBody
    }

    suspend fun getBaseInfo(ip: String, user: String, pass: String): Result<BaseInfoResponse> = safeApiCall {
        val body = executeGet(ip, user, pass, "get_base_info")
        json.decodeFromString<BaseInfoResponse>(body)
    }

    suspend fun getAllWanInfo(ip: String, user: String, pass: String): Result<AllWanResponse> = safeApiCall {
        val body = executeGet(ip, user, pass, "get_allwan_info")
        json.decodeFromString<AllWanResponse>(body)
    }

    suspend fun getDeviceInfo(ip: String, user: String, pass: String): Result<DeviceInfoResponse> = safeApiCall {
        val body = executeGet(ip, user, pass, "show_device_info")
        json.decodeFromString<DeviceInfoResponse>(body)
    }

    suspend fun getHomeInfo(ip: String, user: String, pass: String): Result<HomeInfoResponse> = safeApiCall {
        val body = executeGet(ip, user, pass, "get_home_info")
        json.decodeFromString<HomeInfoResponse>(body)
    }

    suspend fun getLanStatus(ip: String, user: String, pass: String): Result<List<DeviceItem>> = safeApiCall {
        val body = executeGet(ip, user, pass, "get_lan_status")
        val parsed = json.decodeFromString<LanStatusResponse>(body)
        parsed.lanStatus.devices
    }

    suspend fun getWifiStatus(ip: String, user: String, pass: String, is5G: Boolean): Result<List<WifiStatusItem>> = safeApiCall {
        val method = if (is5G) "get_wifi_status_5G" else "get_wifi_status"
        val body = executeGet(ip, user, pass, method)
        val rawList = if (is5G) {
            json.decodeFromString<WifiStatus5GResponse>(body).list
        } else {
            json.decodeFromString<WifiStatus2GResponse>(body).list
        }

        // Dekripsi SSID dan PreSharedKey menggunakan AES key aktif
        val key = currentAesKey
        rawList.map { item ->
            item.copy(
                ssid = RouterCrypto.decrypt(item.ssid, key),
                preSharedKey = RouterCrypto.decrypt(item.preSharedKey, key)
            )
        }
    }

    suspend fun setWifiConfig(
        ip: String,
        user: String,
        pass: String,
        is5G: Boolean,
        wifiIndex: Int,
        ssid: String,
        password: String,
        enable: Boolean,
        hideBroadcast: Boolean
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val freshSessionId = getFreshSessionId(ip)
        val method = if (is5G) "setWlanAdvancedCfg_5G" else "setWlanAdvancedCfg"

        val encSsid = RouterCrypto.encryptPassword(ssid, currentAesKey)
        val encPass = RouterCrypto.encryptPassword(password, currentAesKey)

        val form = FormBody.Builder()
            .add("ajaxmethod", method)
            .add("sessionid", freshSessionId)
            .add("wifiIndex", wifiIndex.toString())
            .add("Enable", if (enable) "1" else "0")
            .add("SSID", encSsid)
            .add("PreSharedKey", encPass)
            .add("X_FH_SSIDHide", if (hideBroadcast) "0" else "1")
            .add("BeaconType", "11i")
            .add("WPAEncryptionModes", "AESEncryption")
            .add("_", System.currentTimeMillis().toString())
            .build()

        val req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    }

    suspend fun getWifiTxPower(ip: String, user: String, pass: String, is5G: Boolean): Result<Int> = safeApiCall {
        val method = if (is5G) "getWlanControl_5G" else "getWlanControl"
        val body = executeGet(ip, user, pass, method)
        val parsed = json.decodeFromString<WlanControlResponse>(body)
        parsed.transmitPower.toIntOrNull() ?: 100
    }

    suspend fun setWifiTxPower(
        ip: String,
        user: String,
        pass: String,
        is5G: Boolean,
        percentage: Int
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val freshSessionId = getFreshSessionId(ip)
        val method = if (is5G) "setWlanControl_5G" else "setWlanControl"
        val form = FormBody.Builder()
            .add("ajaxmethod", method)
            .add("sessionid", freshSessionId)
            .add("TransmitPower", percentage.toString())
            .add("_", System.currentTimeMillis().toString())
            .build()
        val req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    }

    suspend fun getMacFilterInfo(ip: String, user: String, pass: String): Result<MacFilterResponse> = safeApiCall {
        val body = executeGet(ip, user, pass, "get_ipv4_mac_filter_info")
        json.decodeFromString<MacFilterResponse>(body)
    }

    suspend fun blockMacAddress(
        ip: String,
        user: String,
        pass: String,
        targetMac: String
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)

        val enableSess = getFreshSessionId(ip)
        val enableForm = FormBody.Builder()
            .add("ajaxmethod", "set_ipv4_mac_filter_info")
            .add("sessionid", enableSess)
            .add("action", "enable")
            .add("ipv4_mac_filter_enable", "1")
            .add("ipv4_mac_filter_mode", "black")
            .add("_", System.currentTimeMillis().toString())
            .build()
        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(enableForm).build()).execute().close()

        val addSess = getFreshSessionId(ip)
        val addForm = FormBody.Builder()
            .add("ajaxmethod", "set_ipv4_mac_filter_info")
            .add("sessionid", addSess)
            .add("action", "add")
            .add("MAC", targetMac.trim().uppercase())
            .add("Enable", "1")
            .add("TimeStart", "00:00")
            .add("TimeStop", "23:59")
            .add("_", System.currentTimeMillis().toString())
            .build()

        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(addForm).build()).execute().use { resp ->
            resp.isSuccessful
        }
    }

    suspend fun unblockMacAddress(
        ip: String,
        user: String,
        pass: String,
        filterIndex: Int
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val freshSessionId = getFreshSessionId(ip)
        val form = FormBody.Builder()
            .add("ajaxmethod", "set_ipv4_mac_filter_info")
            .add("sessionid", freshSessionId)
            .add("action", "delete")
            .add("ipv4_mac_filter_index", filterIndex.toString())
            .add("_", System.currentTimeMillis().toString())
            .build()

        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()).execute().use { resp ->
            resp.isSuccessful
        }
    }

    suspend fun rebootRouter(ip: String, user: String, pass: String): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val freshSessionId = getFreshSessionId(ip)
        val form = FormBody.Builder()
            .add("ajaxmethod", "set_onu_reboot")
            .add("sessionid", freshSessionId)
            .add("_", System.currentTimeMillis().toString())
            .build()
        val req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        client.newCall(req).execute().use { resp ->
            resetSession()
            resp.isSuccessful
        }
    }

    suspend fun triggerWps(ip: String, user: String, pass: String): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val freshSessionId = getFreshSessionId(ip)
        val form = FormBody.Builder()
            .add("ajaxmethod", "set_wps_pbc")
            .add("sessionid", freshSessionId)
            .add("_", System.currentTimeMillis().toString())
            .build()
        val req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    }

    suspend fun runPingTest(
        ip: String,
        user: String,
        pass: String,
        host: String,
        wanUrl: String
    ): Result<PingResultResponse> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val sess1 = getFreshSessionId(ip)
        val startForm = FormBody.Builder()
            .add("ajaxmethod", "set_ping_info")
            .add("sessionid", sess1)
            .add("action", "start")
            .add("host", host.trim())
            .add("WAN_url", wanUrl)
            .add("IPVersion", "1")
            .add("NumberOfRepetitions", "4")
            .add("DataBlockSize", "32")
            .add("Timeout", "1000")
            .add("_", System.currentTimeMillis().toString())
            .build()
        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(startForm).build()).execute().close()

        kotlinx.coroutines.delay(2000)

        val sess2 = getFreshSessionId(ip)
        val queryForm = FormBody.Builder()
            .add("ajaxmethod", "get_ping_info")
            .add("sessionid", sess2)
            .add("_", System.currentTimeMillis().toString())
            .build()

        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(queryForm).build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<PingResultResponse>(body)
        }
    }

    suspend fun runTraceroute(
        ip: String,
        user: String,
        pass: String,
        host: String,
        wanUrl: String
    ): Result<List<String>> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val sess1 = getFreshSessionId(ip)
        val startForm = FormBody.Builder()
            .add("ajaxmethod", "set_traceroute_info")
            .add("sessionid", sess1)
            .add("action", "start")
            .add("host", host.trim())
            .add("WAN_url", wanUrl)
            .add("IPVersion", "1")
            .add("MaxHopCount", "15")
            .add("Timeout", "2000")
            .add("_", System.currentTimeMillis().toString())
            .build()
        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(startForm).build()).execute().close()

        kotlinx.coroutines.delay(3000)

        val sess2 = getFreshSessionId(ip)
        val queryForm = FormBody.Builder()
            .add("ajaxmethod", "get_traceroute_info")
            .add("sessionid", sess2)
            .add("_", System.currentTimeMillis().toString())
            .build()

        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(queryForm).build()).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val parsed = json.decodeFromString<TracerouteResultResponse>(body)
            parsed.hops
        }
    }
}
