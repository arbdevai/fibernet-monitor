package com.fibernet.monitor.data.network

import com.fibernet.monitor.data.crypto.RouterCrypto
import com.fibernet.monitor.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var lastSessionId: String = ""

    @Volatile
    private var currentAesKey: String = ""

    @Volatile
    private var isLoggedIn: Boolean = false

    private suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Koneksi ke router terputus (Timeout). Periksa apakah HP Anda terhubung ke Wi-Fi router."))
        } catch (e: ConnectException) {
            Result.failure(Exception("Tidak dapat terhubung ke router. Pastikan IP Router ($e) sudah benar dan terhubung."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Alamat router tidak ditemukan. Periksa konfigurasi IP Router Anda."))
        } catch (e: Exception) {
            val msg = e.message ?: "Terjadi kesalahan pada komunikasi dengan router."
            Result.failure(Exception(msg))
        }
    }

    suspend fun getFreshSessionId(ip: String): String = withContext(Dispatchers.IO) {
        val sessUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_refresh_sessionid"
        val sessReq = Request.Builder().url(sessUrl).get().build()
        client.newCall(sessReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Gagal mendapatkan refresh sessionid (HTTP ${resp.code})")
            val body = resp.body?.string() ?: ""
            val obj = json.parseToJsonElement(body).jsonObject
            val id = obj["sessionid"]?.jsonPrimitive?.content ?: ""
            if (id.isNotBlank()) lastSessionId = id
            id
        }
    }

    suspend fun login(ip: String, username: String, pass: String): Result<Boolean> = safeApiCall {
        // 1. Dapatkan acsRandom
        val acsUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_acs_random"
        val acsReq = Request.Builder().url(acsUrl).get().build()
        val acsResp = client.newCall(acsReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Gagal menghubungi router (HTTP ${resp.code})")
            val body = resp.body?.string() ?: throw Exception("Respons kosong dari router")
            val obj = json.parseToJsonElement(body).jsonObject
            obj["acsRandom"]?.jsonPrimitive?.content ?: throw Exception("Format acsRandom tidak valid")
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
                true
            } else {
                isLoggedIn = false
                throw Exception("Login gagal (Kode hasil: $loginResult). Periksa username dan password Anda.")
            }
        }
    }

    private suspend fun ensureAuthenticated(ip: String, username: String, pass: String) {
        if (!isLoggedIn || currentAesKey.isBlank()) {
            val res = login(ip, username, pass)
            if (res.isFailure) throw res.exceptionOrNull() ?: Exception("Gagal autentikasi router")
        }
    }

    suspend fun getBaseInfo(ip: String, user: String, pass: String): Result<BaseInfoResponse> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=get_base_info"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<BaseInfoResponse>(body)
        }
    }

    suspend fun getAllWanInfo(ip: String, user: String, pass: String): Result<AllWanResponse> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=get_allwan_info"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<AllWanResponse>(body)
        }
    }

    suspend fun getDeviceInfo(ip: String, user: String, pass: String): Result<DeviceInfoResponse> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=show_device_info"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<DeviceInfoResponse>(body)
        }
    }

    suspend fun getHomeInfo(ip: String, user: String, pass: String): Result<HomeInfoResponse> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=get_home_info"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<HomeInfoResponse>(body)
        }
    }

    suspend fun getLanStatus(ip: String, user: String, pass: String): Result<List<DeviceItem>> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=get_lan_status"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val parsed = json.decodeFromString<LanStatusResponse>(body)
            parsed.lanStatus.devices
        }
    }

    suspend fun getWifiStatus(ip: String, user: String, pass: String, is5G: Boolean): Result<List<WifiStatusItem>> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val method = if (is5G) "get_wifi_status_5G" else "get_wifi_status"
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=$method"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val rawList = if (is5G) {
                json.decodeFromString<WifiStatus5GResponse>(body).list
            } else {
                json.decodeFromString<WifiStatus2GResponse>(body).list
            }

            // Dekripsi SSID dan PreSharedKey menggunakan AES key
            val key = currentAesKey
            rawList.map { item ->
                item.copy(
                    ssid = RouterCrypto.decrypt(item.ssid, key),
                    preSharedKey = RouterCrypto.decrypt(item.preSharedKey, key)
                )
            }
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
        ensureAuthenticated(ip, user, pass)
        val method = if (is5G) "getWlanControl_5G" else "getWlanControl"
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=$method"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val parsed = json.decodeFromString<WlanControlResponse>(body)
            parsed.transmitPower.toIntOrNull() ?: 100
        }
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
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=get_ipv4_mac_filter_info"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<MacFilterResponse>(body)
        }
    }

    suspend fun blockMacAddress(
        ip: String,
        user: String,
        pass: String,
        targetMac: String
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)

        // 1. Pastikan MAC Filter diaktifkan dalam mode "black" (Blacklist)
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

        // 2. Tambahkan aturan blokir MAC
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
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    }

    suspend fun triggerWps(ip: String, user: String, pass: String): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val freshSessionId = getFreshSessionId(ip)
        val form = FormBody.Builder()
            .add("ajaxmethod", "set_wps_pin_button")
            .add("WPS_PBC", "1")
            .add("sessionid", freshSessionId)
            .add("_", System.currentTimeMillis().toString())
            .build()
        val req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    }

    suspend fun runPing(
        ip: String,
        user: String,
        pass: String,
        host: String
    ): Result<PingResultResponse> = safeApiCall {
        ensureAuthenticated(ip, user, pass)

        // 1. Dapatkan objek URL WAN Internet secara dinamis
        val allWan = getAllWanInfo(ip, user, pass).getOrNull()
        val internetWan = allWan?.wanList?.find { it.serviceList.contains("INTERNET", ignoreCase = true) }
            ?: allWan?.wanList?.firstOrNull()
        val wanUrl = internetWan?.wanUrl ?: "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1."

        // 2. Trigger do_ping
        val freshSessionId = getFreshSessionId(ip)
        val startForm = FormBody.Builder()
            .add("ajaxmethod", "do_ping")
            .add("address", host)
            .add("count", "4")
            .add("WAN_url", wanUrl)
            .add("IPVersion", "1")
            .add("X_FH_WhoRequest", "web")
            .add("sessionid", freshSessionId)
            .add("_", System.currentTimeMillis().toString())
            .build()

        val startReq = Request.Builder().url("http://$ip/cgi-bin/ajax").post(startForm).build()
        client.newCall(startReq).execute().close()

        // 3. Polling get_ping_result sampai selesai (maksimal 6 siklus)
        var lastResult = PingResultResponse()
        for (i in 0 until 6) {
            delay(1500)
            val resUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_ping_result&_=${System.currentTimeMillis()}"
            val resReq = Request.Builder().url(resUrl).get().build()
            client.newCall(resReq).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val parsed = json.decodeFromString<PingResultResponse>(body)
                lastResult = parsed
                if (parsed.isComplete) {
                    return@safeApiCall parsed
                }
            }
        }
        lastResult
    }

    suspend fun runTraceroute(
        ip: String,
        user: String,
        pass: String,
        host: String
    ): Result<List<String>> = safeApiCall {
        ensureAuthenticated(ip, user, pass)

        val allWan = getAllWanInfo(ip, user, pass).getOrNull()
        val internetWan = allWan?.wanList?.find { it.serviceList.contains("INTERNET", ignoreCase = true) }
            ?: allWan?.wanList?.firstOrNull()
        val wanUrl = internetWan?.wanUrl ?: "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1."

        val freshSessionId = getFreshSessionId(ip)
        val form = FormBody.Builder()
            .add("ajaxmethod", "do_traceroute")
            .add("address", host)
            .add("WAN_url", wanUrl)
            .add("IPVersion", "1")
            .add("X_FH_WhoRequest", "web")
            .add("sessionid", freshSessionId)
            .add("_", System.currentTimeMillis().toString())
            .build()

        client.newCall(Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()).execute().close()

        var hops = emptyList<String>()
        for (i in 0 until 8) {
            delay(2000)
            val resUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_traceroute_result&_=${System.currentTimeMillis()}"
            client.newCall(Request.Builder().url(resUrl).get().build()).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val parsed = json.decodeFromString<TracerouteResultResponse>(body)
                hops = parsed.hops
                if (parsed.diagnosticsState.equals("Complete", ignoreCase = true)) {
                    return@safeApiCall hops
                }
            }
        }
        hops
    }
}
