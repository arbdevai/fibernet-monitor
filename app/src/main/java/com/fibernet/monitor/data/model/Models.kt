package com.fibernet.monitor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceItem(
    @SerialName("HostName") val hostName: String = "",
    @SerialName("IPAddress") val ipAddressRaw: String = "",
    @SerialName("MACAddress") val macAddress: String = "",
    @SerialName("AccessType") val accessType: String = "Unknown",
    @SerialName("rssi") val rssi: Int = 0,
    @SerialName("UpSpeed") val upSpeedRaw: String = "0",
    @SerialName("DownSpeed") val downSpeedRaw: String = "0",
    @SerialName("Tx_rate") val txRate: String = "0",
    @SerialName("rx_rate") val rxRate: String = "0",
    @SerialName("Active") val active: String = "0",
    @SerialName("ClientUpTime") val clientUpTime: String = "0",
    @SerialName("OperatingStandard") val operatingStandard: String = "",
    @SerialName("DeviceType") val deviceType: String = ""
) {
    val ipAddress: String
        get() = ipAddressRaw.replace("_point_", ".")

    val displayName: String
        get() = if (hostName.isNotBlank() && hostName != "null") hostName else "Perangkat Tanpa Nama"

    val isOnline: Boolean
        get() = active == "1"

    val upSpeedKbps: Long
        get() = upSpeedRaw.toLongOrNull() ?: 0L

    val downSpeedKbps: Long
        get() = downSpeedRaw.toLongOrNull() ?: 0L
}

@Serializable
data class LanStatusResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("lan_status") val lanStatus: LanStatusData = LanStatusData()
)

@Serializable
data class LanStatusData(
    @SerialName("lan_port_num") val lanPortNum: Int = 4,
    @SerialName("wifi_num_all") val wifiNumAll: Int = 16,
    @SerialName("data") val devices: List<DeviceItem> = emptyList()
)

@Serializable
data class HomeInfoResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("brmac") val bridgeMac: String = "",
    @SerialName("uptime") val uptimeSeconds: String = "0",
    @SerialName("IPInterfaceIPAddress") val gatewayIp: String = "192.168.1.1",
    @SerialName("hostnum") val hostCount: String = "0"
)

@Serializable
data class DeviceInfoResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("ModelName") val modelName: String = "HG6145F3",
    @SerialName("SoftwareVersion") val softwareVersion: String = "",
    @SerialName("operator_name") val operatorName: String = "IDN_SURGE",
    @SerialName("wifi_cap") val wifiCap: String = "1",
    @SerialName("wifi5_cap") val wifi5Cap: String = "1"
)

@Serializable
data class MacFilterResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("ipv4_mac_filter_info") val filterInfo: MacFilterInfo = MacFilterInfo()
)

@Serializable
data class MacFilterInfo(
    @SerialName("MACFEnable") val enable: String = "0",
    @SerialName("MACFMode") val mode: String = "black",
    @SerialName("mac_filter_data") val filterList: List<MacFilterRule> = emptyList()
)

@Serializable
data class MacFilterRule(
    @SerialName("mac") val mac: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("id") val id: String = ""
)

@Serializable
data class WifiBasicResponse(
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("SSID") val ssid: String = "",
    @SerialName("Enable") val enable: String = "1",
    @SerialName("Channel") val channel: String = "0",
    @SerialName("Hide") val hide: String = "0"
)

@Serializable
data class WifiAdvancedResponse(
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("PreSharedKey") val preSharedKey: String = "",
    @SerialName("BeaconType") val beaconType: String = ""
)

@Serializable
data class PingResultResponse(
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("SuccessCount") val successCount: String = "0",
    @SerialName("FailureCount") val failureCount: String = "0",
    @SerialName("AverageResponseTime") val avgResponseTime: String = "0",
    @SerialName("MinimumResponseTime") val minResponseTime: String = "0",
    @SerialName("MaximumResponseTime") val maxResponseTime: String = "0"
)
