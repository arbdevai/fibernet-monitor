package com.fibernet.monitor.data.model

data class WifiSignalSample(
    val timestamp: Long = System.currentTimeMillis(),
    val rssiDbm: Int = -100,
    val qualityPercent: Int = 0,
    val linkSpeedMbps: Int = 0,
    val frequencyMhz: Int = 0,
    val channel: Int = 0,
    val ssid: String = "",
    val bssid: String = ""
)

data class AccessPointInfo(
    val ssid: String = "",
    val bssid: String = "",
    val rssiDbm: Int = -100,
    val frequencyMhz: Int = 0,
    val channel: Int = 0,
    val band: String = "2.4 GHz",
    val channelWidthMhz: Int = 20,
    val capabilities: String = "",
    val isCurrentConnection: Boolean = false
)

data class ChannelRating(
    val channel: Int,
    val frequencyMhz: Int,
    val band: String,
    val apCount: Int,
    val scoreStars: Int,
    val statusText: String,
    val isBestChoice: Boolean = false,
    val accessPoints: List<AccessPointInfo> = emptyList()
)

data class WifiAnalyzerState(
    val isScanning: Boolean = false,
    val hasPermission: Boolean = false,
    val isLocationEnabled: Boolean = false,
    val currentSignal: WifiSignalSample? = null,
    val signalHistory: List<WifiSignalSample> = emptyList(),
    val channels24G: List<ChannelRating> = emptyList(),
    val channels5G: List<ChannelRating> = emptyList(),
    val bestChannel24G: Int = 1,
    val bestChannel5G: Int = 36,
    val allAccessPoints: List<AccessPointInfo> = emptyList(),
    val lastScanTime: Long = 0L,
    val errorMessage: String? = null
)
