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
    @SerialName("Active") val active: String = "0",
    @SerialName("ClientUpTime") val clientUpTime: String = "0",
    @SerialName("OperatingStandard") val operatingStandard: String = "",
    @SerialName("DeviceType") val deviceType: String = "",
    @SerialName("LeaseTimeRemaining") val leaseTimeRemaining: String = "0"
) {
    val ipAddress: String
        get() = ipAddressRaw.replace("_point_", ".")

    val displayName: String
        get() = if (hostName.isNotBlank() && hostName != "null") hostName else "Perangkat ($macAddress)"

    val isOnline: Boolean
        get() = active == "1"

    val upSpeedKbps: Long
        get() = (upSpeedRaw.toLongOrNull() ?: 0L) / 1024L

    val downSpeedKbps: Long
        get() = (downSpeedRaw.toLongOrNull() ?: 0L) / 1024L
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
    @SerialName("IPInterfaceIPAddress") val gatewayIpRaw: String = "192_point_168_point_1_point_1",
    @SerialName("hostnum") val hostCount: String = "0"
) {
    val gatewayIp: String
        get() = gatewayIpRaw.replace("_point_", ".")
}

@Serializable
data class DeviceInfoResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("ModelName") val modelName: String = "HG6145F3",
    @SerialName("SoftwareVersion") val softwareVersion: String = "",
    @SerialName("operator_name") val operatorName: String = "IDN_SURGE",
    @SerialName("wifi_cap") val wifiCap: String = "1",
    @SerialName("wifi5_cap") val wifi5Cap: String = "1",
    @SerialName("lan_port_num") val lanPortNum: Int = 4
)

@Serializable
data class BaseInfoResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("ModelName") val modelName: String = "HG6145F3",
    @SerialName("HardwareVersion") val hardwareVersion: String = "",
    @SerialName("SoftwareVersion") val softwareVersion: String = "",
    @SerialName("SerialNumber") val serialNumber: String = "",
    @SerialName("Manufacturer") val manufacturer: String = "FiberHome",
    @SerialName("gponsn") val gponSn: String = "",
    @SerialName("loid") val loid: String = "",
    @SerialName("WANAccessType") val wanAccessType: String = "GPON",
    @SerialName("uptime") val uptimeSeconds: String = "0",
    @SerialName("brmac") val bridgeMac: String = "",
    @SerialName("cpu_usage") val cpuUsage: String = "0",
    @SerialName("mem_total") val memTotalKb: String = "524288",
    @SerialName("mem_free") val memFreeKb: String = "0",
    @SerialName("flash_usage") val flashUsage: String = "0",
    @SerialName("txpower") val txPowerRaw: String = "0.0",
    @SerialName("rxpower") val rxPowerRaw: String = "0.0",
    @SerialName("supplyvottage") val supplyVoltageRaw: String = "0.0",
    @SerialName("biascurrent") val biasCurrentRaw: String = "0.0",
    @SerialName("transceivertemperature") val temperatureRaw: String = "0.0",
    @SerialName("ponBytesSent") val ponBytesSent: String = "0",
    @SerialName("ponBytesReceived") val ponBytesReceived: String = "0",
    @SerialName("ponPacketsSent") val ponPacketsSent: String = "0",
    @SerialName("ponPacketsReceived") val ponPacketsReceived: String = "0"
) {
    val rxPowerDbm: Double
        get() = rxPowerRaw.toDoubleOrNull() ?: -40.0

    val txPowerDbm: Double
        get() = txPowerRaw.toDoubleOrNull() ?: 0.0

    val temperatureCelsius: Double
        get() = temperatureRaw.toDoubleOrNull() ?: 0.0

    val voltageVolts: Double
        get() = supplyVoltageRaw.toDoubleOrNull() ?: 0.0

    val cpuUsagePercent: Int
        get() = cpuUsage.toIntOrNull() ?: 0

    val ramUsagePercent: Int
        get() {
            val total = memTotalKb.toDoubleOrNull() ?: 524288.0
            val free = memFreeKb.toDoubleOrNull() ?: 0.0
            if (total <= 0) return 0
            val used = total - free
            return ((used / total) * 100).toInt().coerceIn(0, 100)
        }

    val uptimeFormatted: String
        get() {
            val secs = uptimeSeconds.toLongOrNull() ?: 0L
            val days = secs / 86400
            val hours = (secs % 86400) / 3600
            val mins = (secs % 3600) / 60
            return buildString {
                if (days > 0) append("${days}h ")
                if (hours > 0 || days > 0) append("${hours}j ")
                append("${mins}m")
            }
        }

    val ponDownloadedMb: Double
        get() = (ponBytesReceived.toDoubleOrNull() ?: 0.0) / (1024.0 * 1024.0)

    val ponUploadedMb: Double
        get() = (ponBytesSent.toDoubleOrNull() ?: 0.0) / (1024.0 * 1024.0)
}

@Serializable
data class AllWanResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("pon_type") val ponType: String = "GPON",
    @SerialName("wan") val wanList: List<WanItem> = emptyList()
)

@Serializable
data class WanItem(
    @SerialName("wan_index") val wanIndex: Int = 1,
    @SerialName("Name") val name: String = "",
    @SerialName("ConnectionType") val connectionType: String = "",
    @SerialName("ServiceList") val serviceList: String = "",
    @SerialName("AddressingType") val addressingType: String = "",
    @SerialName("ConnectionStatus") val connectionStatus: String = "Disconnected",
    @SerialName("ExternalIPAddress") val externalIpRaw: String = "",
    @SerialName("RemoteIPAddress") val remoteIpRaw: String = "",
    @SerialName("DNSServers") val dnsServersRaw: String = "",
    @SerialName("MACAddress") val macAddress: String = "",
    @SerialName("Uptime") val uptime: String = "0",
    @SerialName("mtu") val mtu: String = "1492",
    @SerialName("WAN_url") val wanUrl: String = ""
) {
    val isConnected: Boolean
        get() = connectionStatus.equals("Connected", ignoreCase = true)

    val externalIp: String
        get() = externalIpRaw.replace("_point_", ".")

    val remoteIp: String
        get() = remoteIpRaw.replace("_point_", ".")

    val dnsServers: String
        get() = dnsServersRaw.replace("_point_", ".")
}

@Serializable
data class WifiStatusItem(
    @SerialName("index") val index: Int = 1,
    @SerialName("ConfigActive") val configActive: String = "1",
    @SerialName("Enable") val enable: String = "1",
    @SerialName("SSID") val ssid: String = "",
    @SerialName("PreSharedKey") val preSharedKey: String = "",
    @SerialName("BSSID") val bssid: String = "",
    @SerialName("X_FH_SSIDHide") val hideSsid: String = "1",
    @SerialName("Channel") val channel: String = "0",
    @SerialName("Standard") val standard: String = "bgn",
    @SerialName("RadioEnabled") val radioEnabled: String = "1",
    @SerialName("BeaconType") val beaconType: String = "11i",
    @SerialName("WPAEncryptionModes") val wpaEncryptionModes: String = "AESEncryption",
    @SerialName("channelIsInUse") val channelInUse: String = ""
) {
    val isBroadcastHidden: Boolean
        get() = hideSsid == "0"

    val isSsidEnabled: Boolean
        get() = enable == "1"
}

@Serializable
data class WifiStatus2GResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("wifi_status") val list: List<WifiStatusItem> = emptyList()
)

@Serializable
data class WifiStatus5GResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("wifi_status5g") val list: List<WifiStatusItem> = emptyList()
)

@Serializable
data class WlanControlResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("TransmitPower") val transmitPower: String = "100"
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
    @SerialName("id") val id: String = "",
    @SerialName("TimeStart") val timeStart: String = "00:00",
    @SerialName("TimeStop") val timeStop: String = "23:59",
    @SerialName("Enable") val enable: String = "1"
)

@Serializable
data class PingResultResponse(
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("DiagnosticsState") val diagnosticsState: String = "",
    @SerialName("SuccessCount") val successCount: String = "0",
    @SerialName("FailureCount") val failureCount: String = "0",
    @SerialName("AverageResponseTime") val avgResponseTime: String = "0",
    @SerialName("MinimumResponseTime") val minResponseTime: String = "0",
    @SerialName("MaximumResponseTime") val maxResponseTime: String = "0"
) {
    val isComplete: Boolean
        get() = diagnosticsState.equals("Complete", ignoreCase = true)
}

@Serializable
data class TracerouteResultResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("DiagnosticsState") val diagnosticsState: String = "",
    @SerialName("Resultcount") val resultCount: Int = 0,
    @SerialName("DiagnosticsResult1") val res1: String? = null,
    @SerialName("DiagnosticsResult2") val res2: String? = null,
    @SerialName("DiagnosticsResult3") val res3: String? = null,
    @SerialName("DiagnosticsResult4") val res4: String? = null,
    @SerialName("DiagnosticsResult5") val res5: String? = null,
    @SerialName("DiagnosticsResult6") val res6: String? = null,
    @SerialName("DiagnosticsResult7") val res7: String? = null,
    @SerialName("DiagnosticsResult8") val res8: String? = null,
    @SerialName("DiagnosticsResult9") val res9: String? = null,
    @SerialName("DiagnosticsResult10") val res10: String? = null
) {
    val hops: List<String>
        get() = listOfNotNull(res1, res2, res3, res4, res5, res6, res7, res8, res9, res10)
            .filter { it.isNotBlank() }
}
