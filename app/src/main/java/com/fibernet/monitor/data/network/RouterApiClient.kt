package com.fibernet.monitor.data.network

import com.fibernet.monitor.data.crypto.RouterCrypto
import com.fibernet.monitor.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
    private val authMutex = Mutex()

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

    private fun extractJson(body: String): String {
        val trimmed = body.trim()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start != -1 && end != -1 && end >= start) {
            return trimmed.substring(start, end + 1)
        }
        val arrayStart = trimmed.indexOf('[')
        val arrayEnd = trimmed.lastIndexOf(']')
        if (arrayStart != -1 && arrayEnd != -1 && arrayEnd >= arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1)
        }
        throw Exception("Invalid response received from router. Gateway may be busy or rebooting.")
    }

    suspend fun getFreshSessionId(ip: String): String = withContext(Dispatchers.IO) {
        val sessUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_refresh_sessionid"
        val sessReq = Request.Builder().url(sessUrl).get().build()
        client.newCall(sessReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Failed to acquire fresh session token (HTTP ${resp.code})")
            val body = resp.body?.string() ?: ""
            val jsonClean = extractJson(body)
            val obj = json.parseToJsonElement(jsonClean).jsonObject
            val id = obj["sessionid"]?.jsonPrimitive?.content ?: ""
            if (id.isNotBlank()) lastSessionId = id
            id
        }
    }

    private fun executeLoginInternal(ip: String, username: String, pass: String): Boolean {
        // 1. Dapatkan acsRandom
        val acsUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_acs_random"
        val acsReq = Request.Builder().url(acsUrl).get().build()
        val acsResp = client.newCall(acsReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Failed to contact router (HTTP ${resp.code})")
            val body = resp.body?.string() ?: throw Exception("Empty response from router")
            val jsonClean = extractJson(body)
            val obj = json.parseToJsonElement(jsonClean).jsonObject
            obj["acsRandom"]?.jsonPrimitive?.content ?: throw Exception("Invalid security token from router")
        }

        val aesKey = RouterCrypto.extractAesKey(acsResp)
        currentAesKey = aesKey
        val encryptedPass = RouterCrypto.encryptPassword(pass, aesKey)

        // 2. Dapatkan refresh session id
        val sessUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_refresh_sessionid"
        val sessReq = Request.Builder().url(sessUrl).get().build()
        val sessId = client.newCall(sessReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Failed to acquire session token (HTTP ${resp.code})")
            val body = resp.body?.string() ?: ""
            val jsonClean = extractJson(body)
            val obj = json.parseToJsonElement(jsonClean).jsonObject
            obj["sessionid"]?.jsonPrimitive?.content ?: ""
        }
        if (sessId.isNotBlank()) lastSessionId = sessId

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

        return client.newCall(loginReq).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val jsonClean = extractJson(body)
            val obj = json.parseToJsonElement(jsonClean).jsonObject
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

    suspend fun login(ip: String, username: String, pass: String): Result<Boolean> = safeApiCall {
        authMutex.withLock {
            resetSession()
            activeRouterIp = ip
            executeLoginInternal(ip, username, pass)
        }
    }

    suspend fun ensureAuthenticated(ip: String, username: String, pass: String) {
        if (isLoggedIn && ip == activeRouterIp && currentAesKey.isNotBlank()) {
            return
        }
        authMutex.withLock {
            if (isLoggedIn && ip == activeRouterIp && currentAesKey.isNotBlank()) {
                return
            }
            if (ip != activeRouterIp) {
                resetSession()
                activeRouterIp = ip
            }
            val success = executeLoginInternal(ip, username, pass)
            if (!success) throw Exception("Router authentication failed")
        }
    }

    // Helper untuk auto re-login saat FiberHome mengembalikan session_valid == 0
    private suspend fun executeGet(ip: String, user: String, pass: String, method: String): String {
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=$method"
        var respBody = client.newCall(Request.Builder().url(url).get().build()).execute().use { it.body?.string() ?: "" }

        if (respBody.contains("\"session_valid\":0") || respBody.contains("\"session_valid\":\"0\"")) {
            // Sesi kedaluwarsa atau router berganti, re-login sekali via authMutex
            authMutex.withLock {
                resetSession()
                activeRouterIp = ip
                executeLoginInternal(ip, user, pass)
            }
            respBody = client.newCall(Request.Builder().url(url).get().build()).execute().use { it.body?.string() ?: "" }
        }
        return extractJson(respBody)
    }

    // Helper untuk POST request mutasi dengan auto re-login dan validasi respons router
    private suspend fun executePost(
        ip: String,
        user: String,
        pass: String,
        formBuilderAction: (freshSessionId: String) -> FormBody
    ): String {
        ensureAuthenticated(ip, user, pass)
        var sessionId = getFreshSessionId(ip)
        var form = formBuilderAction(sessionId)
        var req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        var respBody = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} from router")
            resp.body?.string() ?: ""
        }

        if (respBody.contains("\"session_valid\":0") || respBody.contains("\"session_valid\":\"0\"")) {
            authMutex.withLock {
                resetSession()
                activeRouterIp = ip
                executeLoginInternal(ip, user, pass)
            }
            sessionId = getFreshSessionId(ip)
            form = formBuilderAction(sessionId)
            req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
            respBody = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} from router after re-auth")
                resp.body?.string() ?: ""
            }
        }

        val trimmed = respBody.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val obj = json.parseToJsonElement(trimmed).jsonObject
                val success = obj["success"]?.jsonPrimitive?.content
                if (success != null && (success.equals("false", ignoreCase = true) || success == "0")) {
                    val err = obj["error"]?.jsonPrimitive?.content ?: obj["message"]?.jsonPrimitive?.content ?: "Router rejected operation."
                    throw Exception(err)
                }
            } catch (e: Exception) {
                if (e.message?.contains("Router rejected") == true) throw e
            }
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

    suspend fun setMacFilterEnabled(
        ip: String,
        user: String,
        pass: String,
        enabled: Boolean
    ): Result<Boolean> = safeApiCall {
        val flag = if (enabled) "1" else "0"
        executePost(ip, user, pass) { sess ->
            FormBody.Builder()
                .add("ajaxmethod", "set_ipv4_mac_filter_info")
                .add("sessionid", sess)
                .add("action", "enable")
                .add("ipv4_mac_filter_enable", flag)
                .add("MACFEnable", flag)
                .add("ipv4_mac_filter_mode", "black")
                .add("MACFMode", "black")
                .add("_", System.currentTimeMillis().toString())
                .build()
        }
        true
    }

    suspend fun blockMacAddress(
        ip: String,
        user: String,
        pass: String,
        targetMac: String
    ): Result<Boolean> = safeApiCall {
        val cleanMac = targetMac.trim().uppercase()
        if (cleanMac.isBlank()) throw Exception("Invalid or empty MAC address.")

        // 1. Pastikan MAC Filter diaktifkan dalam mode "black" (Blacklist)
        executePost(ip, user, pass) { sess ->
            FormBody.Builder()
                .add("ajaxmethod", "set_ipv4_mac_filter_info")
                .add("sessionid", sess)
                .add("action", "enable")
                .add("ipv4_mac_filter_enable", "1")
                .add("MACFEnable", "1")
                .add("ipv4_mac_filter_mode", "black")
                .add("MACFMode", "black")
                .add("_", System.currentTimeMillis().toString())
                .build()
        }

        // 2. Tambahkan aturan blokir MAC ke tabel firewall router
        executePost(ip, user, pass) { sess ->
            FormBody.Builder()
                .add("ajaxmethod", "set_ipv4_mac_filter_info")
                .add("sessionid", sess)
                .add("action", "add")
                .add("MAC", cleanMac)
                .add("mac", cleanMac)
                .add("Enable", "1")
                .add("TimeStart", "00:00")
                .add("TimeStop", "23:59")
                .add("_", System.currentTimeMillis().toString())
                .build()
        }
        true
    }

    suspend fun unblockMacAddress(
        ip: String,
        user: String,
        pass: String,
        targetMac: String,
        filterIndex: Int = -1,
        ruleId: String = ""
    ): Result<Boolean> = safeApiCall {
        val cleanMac = targetMac.trim().uppercase()
        val currentInfo = getMacFilterInfo(ip, user, pass).getOrNull()?.filterInfo
        val currentList = currentInfo?.filterList.orEmpty()

        val resolvedIndex = if (filterIndex >= 0) filterIndex else currentList.indexOfFirst { it.mac.equals(cleanMac, ignoreCase = true) }
        val foundRule = currentList.find { it.mac.equals(cleanMac, ignoreCase = true) }
        val resolvedId = ruleId.ifBlank { foundRule?.id ?: "" }
        val idxToSend = if (resolvedIndex >= 0) resolvedIndex.toString() else "0"

        // 1. Kirim perintah delete dengan parameter lengkap (MAC + Index + ID)
        executePost(ip, user, pass) { sess ->
            val builder = FormBody.Builder()
                .add("ajaxmethod", "set_ipv4_mac_filter_info")
                .add("sessionid", sess)
                .add("action", "delete")
                .add("ipv4_mac_filter_index", idxToSend)
                .add("index", idxToSend)
                .add("Index", idxToSend)

            if (cleanMac.isNotBlank()) {
                builder.add("MAC", cleanMac)
                builder.add("mac", cleanMac)
            }
            if (resolvedId.isNotBlank()) {
                builder.add("id", resolvedId)
                builder.add("ID", resolvedId)
            }
            builder.add("_", System.currentTimeMillis().toString())
            builder.build()
        }

        // 2. Fallback delete jika router menggunakan indeks 1-based tabel (ID berbeda dari 0-based idx)
        if (resolvedId.isNotBlank() && resolvedId != idxToSend) {
            try {
                executePost(ip, user, pass) { sess ->
                    val builder = FormBody.Builder()
                        .add("ajaxmethod", "set_ipv4_mac_filter_info")
                        .add("sessionid", sess)
                        .add("action", "delete")
                        .add("ipv4_mac_filter_index", resolvedId)
                        .add("index", resolvedId)
                    if (cleanMac.isNotBlank()) {
                        builder.add("MAC", cleanMac)
                        builder.add("mac", cleanMac)
                    }
                    builder.add("_", System.currentTimeMillis().toString())
                    builder.build()
                }
            } catch (_: Exception) {}
        }

        // 3. Jika daftar aturan tersisa kosong, otomatis nonaktifkan filter agar router firewall membebaskan koneksi
        val remaining = currentList.filterNot { it.mac.equals(cleanMac, ignoreCase = true) }
        if (remaining.isEmpty()) {
            executePost(ip, user, pass) { sess ->
                FormBody.Builder()
                    .add("ajaxmethod", "set_ipv4_mac_filter_info")
                    .add("sessionid", sess)
                    .add("action", "enable")
                    .add("ipv4_mac_filter_enable", "0")
                    .add("MACFEnable", "0")
                    .add("ipv4_mac_filter_mode", "black")
                    .add("MACFMode", "black")
                    .add("_", System.currentTimeMillis().toString())
                    .build()
            }
        }
        true
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
        val targetWan = wanUrl.ifBlank { "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1." }
        val startForm = FormBody.Builder()
            .add("ajaxmethod", "do_ping")
            .add("sessionid", sess1)
            .add("address", host.trim())
            .add("count", "4")
            .add("WAN_url", targetWan)
            .add("X_FH_WhoRequest", "web")
            .add("IPVersion", "1")
            .add("_", System.currentTimeMillis().toString())
            .build()
        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(startForm).build()).execute().close()

        var finalResult = PingResultResponse()
        for (i in 1..5) {
            kotlinx.coroutines.delay(1500)
            try {
                val body = executeGet(ip, user, pass, "get_ping_result")
                val parsed = json.decodeFromString<PingResultResponse>(body)
                finalResult = parsed
                if (parsed.isComplete || (parsed.successCount.toIntOrNull() ?: 0) > 0) {
                    break
                }
            } catch (_: Exception) {}
        }
        finalResult
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
        val targetWan = wanUrl.ifBlank { "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1." }
        val startForm = FormBody.Builder()
            .add("ajaxmethod", "do_traceroute")
            .add("sessionid", sess1)
            .add("address", host.trim())
            .add("WAN_url", targetWan)
            .add("X_FH_WhoRequest", "web")
            .add("IPVersion", "1")
            .add("_", System.currentTimeMillis().toString())
            .build()
        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(startForm).build()).execute().close()

        var hops = emptyList<String>()
        for (i in 1..6) {
            kotlinx.coroutines.delay(2000)
            try {
                val body = executeGet(ip, user, pass, "get_traceroute_result")
                val parsed = json.decodeFromString<TracerouteResultResponse>(body)
                if (parsed.hops.isNotEmpty()) {
                    hops = parsed.hops
                }
                if (parsed.diagnosticsState.equals("Complete", ignoreCase = true)) {
                    break
                }
            } catch (_: Exception) {}
        }
        hops
    }
}
