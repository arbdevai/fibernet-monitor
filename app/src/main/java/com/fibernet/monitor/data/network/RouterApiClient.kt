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
    private var isLoggedIn: Boolean = false

    private suspend fun <T> safeApiCall(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (e: SocketTimeoutException) {
            Result.failure(Exception("Koneksi ke router terputus (Timeout). Periksa apakah HP Anda terhubung ke Wi-Fi router."))
        } catch (e: ConnectException) {
            Result.failure(Exception("Tidak dapat terhubung ke router. Pastikan IP Router sudah benar dan Anda terhubung ke jaringan Wi-Fi."))
        } catch (e: UnknownHostException) {
            Result.failure(Exception("Alamat router tidak ditemukan. Periksa konfigurasi IP Router Anda."))
        } catch (e: Exception) {
            val msg = e.message ?: "Terjadi kesalahan pada komunikasi dengan router."
            Result.failure(Exception(msg))
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
        val encryptedPass = RouterCrypto.encryptPassword(pass, aesKey)

        // 2. Dapatkan refresh session id
        val sessUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_refresh_sessionid"
        val sessReq = Request.Builder().url(sessUrl).get().build()
        val sessId = client.newCall(sessReq).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Gagal mendapatkan session id (HTTP ${resp.code})")
            val body = resp.body?.string() ?: ""
            val obj = json.parseToJsonElement(body).jsonObject
            obj["sessionid"]?.jsonPrimitive?.content ?: ""
        }

        // 3. Kirim do_login
        val loginBody = FormBody.Builder()
            .add("username", username)
            .add("loginpd", encryptedPass)
            .add("port", "0")
            .add("sessionid", sessId)
            .add("ajaxmethod", "do_login")
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
                throw Exception("Login gagal. Kode hasil: $loginResult (Periksa username dan password).")
            }
        }
    }

    private suspend fun ensureAuthenticated(ip: String, username: String, pass: String) {
        if (!isLoggedIn) {
            val res = login(ip, username, pass)
            if (res.isFailure) throw res.exceptionOrNull() ?: Exception("Gagal autentikasi")
        }
    }

    suspend fun getLanStatus(ip: String, user: String, pass: String): Result<List<DeviceItem>> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=get_lan_status"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 403 || resp.code == 401) {
                // Sesi habis, login ulang sekali
                isLoggedIn = false
                ensureAuthenticated(ip, user, pass)
                return@use client.newCall(req).execute().use { retryResp ->
                    val body = retryResp.body?.string() ?: ""
                    parseLanStatus(body)
                }
            }
            val body = resp.body?.string() ?: ""
            parseLanStatus(body)
        }
    }

    private fun parseLanStatus(body: String): List<DeviceItem> {
        val parsed = json.decodeFromString<LanStatusResponse>(body)
        return parsed.lanStatus.devices
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

    suspend fun getDeviceInfo(ip: String, user: String, pass: String): Result<DeviceInfoResponse> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=show_device_info"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<DeviceInfoResponse>(body)
        }
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
        targetMac: String,
        targetName: String
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        // Ambil daftar filter saat ini
        val currentInfo = getMacFilterInfo(ip, user, pass).getOrThrow().filterInfo
        val list = currentInfo.filterList.toMutableList()

        if (list.none { it.mac.equals(targetMac, ignoreCase = true) }) {
            list.add(MacFilterRule(mac = targetMac, name = targetName, id = "${list.size + 1}"))
        }

        val formBuilder = FormBody.Builder()
            .add("ajaxmethod", "set_ipv4_mac_filter_info")
            .add("MACFEnable", "1")
            .add("MACFMode", "black")

        list.forEachIndexed { idx, rule ->
            formBuilder.add("mac_filter_data[$idx][mac]", rule.mac)
            formBuilder.add("mac_filter_data[$idx][name]", rule.name)
            formBuilder.add("mac_filter_data[$idx][id]", rule.id)
        }

        val req = Request.Builder()
            .url("http://$ip/cgi-bin/ajax")
            .post(formBuilder.build())
            .build()

        client.newCall(req).execute().use { resp ->
            resp.isSuccessful
        }
    }

    suspend fun unblockMacAddress(
        ip: String,
        user: String,
        pass: String,
        targetMac: String
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val currentInfo = getMacFilterInfo(ip, user, pass).getOrThrow().filterInfo
        val list = currentInfo.filterList.filterNot { it.mac.equals(targetMac, ignoreCase = true) }

        val formBuilder = FormBody.Builder()
            .add("ajaxmethod", "set_ipv4_mac_filter_info")
            .add("MACFEnable", if (list.isNotEmpty()) "1" else "0")
            .add("MACFMode", "black")

        list.forEachIndexed { idx, rule ->
            formBuilder.add("mac_filter_data[$idx][mac]", rule.mac)
            formBuilder.add("mac_filter_data[$idx][name]", rule.name)
            formBuilder.add("mac_filter_data[$idx][id]", rule.id)
        }

        val req = Request.Builder()
            .url("http://$ip/cgi-bin/ajax")
            .post(formBuilder.build())
            .build()

        client.newCall(req).execute().use { resp ->
            resp.isSuccessful
        }
    }

    suspend fun getWifiBasic(ip: String, user: String, pass: String, is5G: Boolean): Result<WifiBasicResponse> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val method = if (is5G) "wlanBasicSettings_5G" else "wlanBasicSettings"
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=$method"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<WifiBasicResponse>(body)
        }
    }

    suspend fun setWifiBasic(
        ip: String,
        user: String,
        pass: String,
        is5G: Boolean,
        ssid: String,
        enable: Boolean
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val method = if (is5G) "setWlanBasicCfg_5G" else "setWlanBasicCfg"
        val form = FormBody.Builder()
            .add("ajaxmethod", method)
            .add("SSID", ssid)
            .add("Enable", if (enable) "1" else "0")
            .build()
        val req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    }

    suspend fun getWifiAdvanced(ip: String, user: String, pass: String, is5G: Boolean): Result<WifiAdvancedResponse> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val method = if (is5G) "wlanAdvancedSettings_5G" else "wlanAdvancedSettings"
        val url = "http://$ip/cgi-bin/ajax?ajaxmethod=$method"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<WifiAdvancedResponse>(body)
        }
    }

    suspend fun setWifiPassword(
        ip: String,
        user: String,
        pass: String,
        is5G: Boolean,
        newKey: String
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val method = if (is5G) "setWlanAdvancedCfg_5G" else "setWlanAdvancedCfg"
        val form = FormBody.Builder()
            .add("ajaxmethod", method)
            .add("PreSharedKey", newKey)
            .build()
        val req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    }

    suspend fun setWifiTxPower(
        ip: String,
        user: String,
        pass: String,
        is5G: Boolean,
        percentage: Int
    ): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val method = if (is5G) "setWlanControl_5G" else "setWlanControl"
        val form = FormBody.Builder()
            .add("ajaxmethod", method)
            .add("TransmitPower", percentage.toString())
            .build()
        val req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    }

    suspend fun rebootRouter(ip: String, user: String, pass: String): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val form = FormBody.Builder()
            .add("ajaxmethod", "set_onu_reboot")
            .build()
        val req = Request.Builder().url("http://$ip/cgi-bin/ajax").post(form).build()
        client.newCall(req).execute().use { resp -> resp.isSuccessful }
    }

    suspend fun triggerWps(ip: String, user: String, pass: String): Result<Boolean> = safeApiCall {
        ensureAuthenticated(ip, user, pass)
        val form = FormBody.Builder()
            .add("ajaxmethod", "set_wps_pin_button")
            .add("WPS_PBC", "1")
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
        // 1. Trigger do_ping
        val startForm = FormBody.Builder()
            .add("ajaxmethod", "do_ping")
            .add("Host", host)
            .add("NumberOfRepetitions", "4")
            .build()
        val startReq = Request.Builder().url("http://$ip/cgi-bin/ajax").post(startForm).build()
        client.newCall(startReq).execute().close()

        // Tunggu ping selesai di router
        delay(4000)

        // 2. Ambil hasil get_ping_result
        val resUrl = "http://$ip/cgi-bin/ajax?ajaxmethod=get_ping_result"
        val resReq = Request.Builder().url(resUrl).get().build()
        client.newCall(resReq).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            json.decodeFromString<PingResultResponse>(body)
        }
    }
}
