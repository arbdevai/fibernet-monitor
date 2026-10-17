package com.fibernet.monitor.data.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.fibernet.monitor.data.model.AccessPointInfo
import com.fibernet.monitor.data.model.ChannelRating
import com.fibernet.monitor.data.model.WifiSignalSample
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

object WifiAnalyzerManager {

    /**
     * Memeriksa apakah aplikasi telah diberikan izin lokasi & Wi-Fi yang diperlukan
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val wifiState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED || fineLocation
        } else {
            true
        }

        return fineLocation && wifiState && nearbyDevices
    }

    /**
     * Memeriksa apakah Location Service / GPS aktif (dibutuhkan Android untuk Wi-Fi Scanning)
     */
    fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Mengambil sampel sinyal Wi-Fi yang sedang terhubung secara live (100% data sensor nyata)
     */
    fun getCurrentSignalSample(context: Context): WifiSignalSample? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null

        val info: WifiInfo = wifiManager.connectionInfo ?: return null
        val rawSsid = info.ssid ?: ""
        val cleanSsid = rawSsid.removePrefix("\"").removeSuffix("\"")
        if (cleanSsid.isBlank() || cleanSsid == "<unknown ssid>" || info.bssid == null) {
            return null
        }

        val rssi = info.rssi
        val quality = (2 * (rssi + 100)).coerceIn(0, 100)
        val freq = info.frequency
        val channel = frequencyToChannel(freq)

        return WifiSignalSample(
            timestamp = System.currentTimeMillis(),
            rssiDbm = rssi,
            qualityPercent = quality,
            linkSpeedMbps = if (info.linkSpeed > 0) info.linkSpeed else 0,
            frequencyMhz = freq,
            channel = channel,
            ssid = cleanSsid,
            bssid = info.bssid ?: ""
        )
    }

    /**
     * Flow pemantauan kekuatan jangkauan real-time (1 Hz live sampling)
     */
    fun monitorLiveSignal(context: Context, intervalMs: Long = 1000L): Flow<WifiSignalSample?> = flow {
        while (currentCoroutineContext().isActive) {
            val sample = getCurrentSignalSample(context)
            emit(sample)
            delay(intervalMs)
        }
    }

    /**
     * Memindai seluruh Access Point di sekitar dan menghitung Channel Congestion & Rating
     */
    @Suppress("DEPRECATION")
    fun scanAndAnalyzeChannels(context: Context): Triple<List<ChannelRating>, List<ChannelRating>, List<AccessPointInfo>> {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return Triple(emptyList(), emptyList(), emptyList())

        if (!hasRequiredPermissions(context)) {
            return Triple(emptyList(), emptyList(), emptyList())
        }

        // Memicu scan baru di sistem jika diizinkan (best-effort)
        try {
            wifiManager.startScan()
        } catch (_: Exception) {}

        val scanResults: List<ScanResult> = try {
            wifiManager.scanResults ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val currentBssid = wifiManager.connectionInfo?.bssid ?: ""

        val apList = scanResults.map { scan ->
            val freq = scan.frequency
            val ch = frequencyToChannel(freq)
            val band = if (freq >= 4900) "5 GHz" else "2.4 GHz"
            AccessPointInfo(
                ssid = scan.SSID.ifBlank { "Hidden Network (${scan.BSSID.takeLast(5)})" },
                bssid = scan.BSSID ?: "",
                rssiDbm = scan.level,
                frequencyMhz = freq,
                channel = ch,
                band = band,
                channelWidthMhz = 20,
                capabilities = scan.capabilities ?: "",
                isCurrentConnection = scan.BSSID.equals(currentBssid, ignoreCase = true)
            )
        }

        val channels24G = analyze24GHzChannels(apList.filter { it.band == "2.4 GHz" })
        val channels5G = analyze5GHzChannels(apList.filter { it.band == "5 GHz" })

        return Triple(channels24G, channels5G, apList)
    }

    /**
     * Analisis Channel 2.4 GHz (Fokus Channel 1 - 13 dengan penalti adjacent channel)
     */
    private fun analyze24GHzChannels(apList: List<AccessPointInfo>): List<ChannelRating> {
        val standardChannels = (1..11).toList()

        val ratings = standardChannels.map { ch ->
            val freq = 2407 + (ch * 5)
            val exactAps = apList.filter { it.channel == ch }

            // Menghitung interferensi kanal bertetangga (co-channel dan adjacent overlap)
            var penalty = 0
            for (ap in apList) {
                val distance = kotlin.math.abs(ap.channel - ch)
                if (distance == 0) {
                    // Co-channel: interferensi langsung
                    penalty += if (ap.rssiDbm > -65) 30 else if (ap.rssiDbm > -80) 15 else 8
                } else if (distance in 1..2) {
                    // Adjacent overlap (karena 2.4G lebar 20MHz mencakup 4 channel)
                    penalty += if (ap.rssiDbm > -65) 18 else if (ap.rssiDbm > -80) 10 else 4
                }
            }

            val score = when {
                penalty == 0 -> 5
                penalty < 20 -> 4
                penalty < 45 -> 3
                penalty < 80 -> 2
                else -> 1
            }

            val status = when (score) {
                5 -> "Optimal (Clean)"
                4 -> "Good"
                3 -> "Moderate"
                2 -> "Crowded"
                else -> "Severe Interference"
            }

            ChannelRating(
                channel = ch,
                frequencyMhz = freq,
                band = "2.4 GHz",
                apCount = exactAps.size,
                scoreStars = score,
                statusText = status,
                isBestChoice = false,
                accessPoints = exactAps
            )
        }

        // Tentukan Best Choice di antara non-overlapping channels (1, 6, 11)
        val bestNonOverlapping = ratings.filter { it.channel in listOf(1, 6, 11) }
            .maxByOrNull { it.scoreStars * 100 - it.apCount }
            ?: ratings.maxByOrNull { it.scoreStars * 100 - it.apCount }

        return ratings.map { r ->
            if (r.channel == bestNonOverlapping?.channel) r.copy(isBestChoice = true) else r
        }
    }

    /**
     * Analisis Channel 5 GHz (Channel 36, 40, 44, 48, 149, 153, 157, 161)
     */
    private fun analyze5GHzChannels(apList: List<AccessPointInfo>): List<ChannelRating> {
        val standard5G = listOf(36, 40, 44, 48, 149, 153, 157, 161)

        val ratings = standard5G.map { ch ->
            val freq = if (ch <= 48) 5000 + (ch * 5) else 5000 + (ch * 5)
            val exactAps = apList.filter { it.channel == ch }

            var penalty = 0
            for (ap in exactAps) {
                penalty += if (ap.rssiDbm > -65) 25 else if (ap.rssiDbm > -80) 12 else 5
            }

            val score = when {
                penalty == 0 -> 5
                penalty < 15 -> 4
                penalty < 35 -> 3
                penalty < 60 -> 2
                else -> 1
            }

            val status = when (score) {
                5 -> "Optimal (Clean)"
                4 -> "Good"
                3 -> "Moderate"
                2 -> "Crowded"
                else -> "Congested"
            }

            ChannelRating(
                channel = ch,
                frequencyMhz = freq,
                band = "5 GHz",
                apCount = exactAps.size,
                scoreStars = score,
                statusText = status,
                isBestChoice = false,
                accessPoints = exactAps
            )
        }

        val best = ratings.maxByOrNull { it.scoreStars * 100 - it.apCount }
        return ratings.map { r ->
            if (r.channel == best?.channel) r.copy(isBestChoice = true) else r
        }
    }

    /**
     * Konversi frekuensi MHz ke nomor Channel Wi-Fi
     */
    fun frequencyToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2407) / 5
            freq in 5170..5825 -> (freq - 5000) / 5
            freq in 5925..7125 -> (freq - 5940) / 5 // Wi-Fi 6E
            else -> 0
        }
    }
}
