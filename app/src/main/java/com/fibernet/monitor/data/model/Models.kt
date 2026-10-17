package com.fibernet.monitor.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder): Int {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            return if (element is JsonPrimitive) {
                element.intOrNull ?: element.content.toIntOrNull() ?: 0
            } else {
                0
            }
        }
        return try {
            decoder.decodeInt()
        } catch (_: Exception) {
            try {
                decoder.decodeString().toIntOrNull() ?: 0
            } catch (_: Exception) {
                0
            }
        }
    }
}

object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            return if (element is JsonPrimitive) {
                element.content
            } else {
                element.toString()
            }
        }
        return try {
            decoder.decodeString()
        } catch (_: Exception) {
            try {
                decoder.decodeInt().toString()
            } catch (_: Exception) {
                ""
            }
        }
    }
}

class FlexibleListSerializer<T>(elementSerializer: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(elementSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            if (element !is JsonArray) {
                return emptyList()
            }
            return jsonDecoder.json.decodeFromJsonElement(delegate, element)
        }
        return try {
            delegate.deserialize(decoder)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Serializable
data class DeviceItem(
    @SerialName("HostName") val hostName: String = "",
    @SerialName("IPAddress") val ipAddressRaw: String = "",
    @SerialName("MACAddress") val macAddress: String = "",
    @SerialName("AccessType") val accessType: String = "Unknown",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("rssi") val rssi: Int = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("UpSpeed") val upSpeedRaw: String = "0",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("DownSpeed") val downSpeedRaw: String = "0",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("Active") val active: String = "0",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("ClientUpTime") val clientUpTime: String = "0",
    @SerialName("OperatingStandard") val operatingStandard: String = "",
    @SerialName("DeviceType") val deviceType: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("LeaseTimeRemaining") val leaseTimeRemaining: String = "0"
) {
    val ipAddress: String
        get() = ipAddressRaw.replace("_point_", ".")

    val displayName: String
        get() = if (hostName.isNotBlank() && hostName != "null") hostName else "Device ($macAddress)"

    val isOnline: Boolean
        get() = active == "1"

    val upSpeedKbps: Long
        get() = (upSpeedRaw.toLongOrNull() ?: 0L) / 1024L

    val downSpeedKbps: Long
        get() = (downSpeedRaw.toLongOrNull() ?: 0L) / 1024L
}

object DeviceItemListSerializer : KSerializer<List<DeviceItem>> {
    private val delegate = FlexibleListSerializer(DeviceItem.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<DeviceItem>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): List<DeviceItem> = delegate.deserialize(decoder)
}

@Serializable
data class LanStatusResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("lan_status") val lanStatus: LanStatusData = LanStatusData()
)

@Serializable
data class LanStatusData(
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("lan_port_num") val lanPortNum: Int = 4,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("wifi_num_all") val wifiNumAll: Int = 16,
    @Serializable(with = DeviceItemListSerializer::class)
    @SerialName("data") val devices: List<DeviceItem> = emptyList()
)

@Serializable
data class HomeInfoResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
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
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("ModelName") val modelName: String = "HG6145F3",
    @SerialName("SoftwareVersion") val softwareVersion: String = "",
    @SerialName("operator_name") val operatorName: String = "IDN_SURGE",
    @SerialName("wifi_cap") val wifiCap: String = "1",
    @SerialName("wifi5_cap") val wifi5Cap: String = "1",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("lan_port_num") val lanPortNum: Int = 4
)

@Serializable
data class BaseInfoResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
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

    val ponDownloadedGb: Double
        get() = (ponBytesReceived.toDoubleOrNull() ?: 0.0) / (1024.0 * 1024.0 * 1024.0)

    val ponUploadedGb: Double
        get() = (ponBytesSent.toDoubleOrNull() ?: 0.0) / (1024.0 * 1024.0 * 1024.0)
}

@Serializable
data class WanItem(
    @Serializable(with = FlexibleIntSerializer::class)
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
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("Uptime") val uptime: String = "0",
    @Serializable(with = FlexibleStringSerializer::class)
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

object WanItemListSerializer : KSerializer<List<WanItem>> {
    private val delegate = FlexibleListSerializer(WanItem.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<WanItem>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): List<WanItem> = delegate.deserialize(decoder)
}

@Serializable
data class AllWanResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("pon_type") val ponType: String = "GPON",
    @Serializable(with = WanItemListSerializer::class)
    @SerialName("wan") val wanList: List<WanItem> = emptyList()
)

@Serializable
data class WifiStatusItem(
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("index") val index: Int = 1,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("ConfigActive") val configActive: String = "1",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("Enable") val enable: String = "1",
    @SerialName("SSID") val ssid: String = "",
    @SerialName("PreSharedKey") val preSharedKey: String = "",
    @SerialName("BSSID") val bssid: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("X_FH_SSIDHide") val hideSsid: String = "1",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("Channel") val channel: String = "0",
    @SerialName("Standard") val standard: String = "bgn",
    @Serializable(with = FlexibleStringSerializer::class)
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

object WifiStatusItemListSerializer : KSerializer<List<WifiStatusItem>> {
    private val delegate = FlexibleListSerializer(WifiStatusItem.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<WifiStatusItem>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): List<WifiStatusItem> = delegate.deserialize(decoder)
}

@Serializable
data class WifiStatus2GResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("session_valid") val sessionValid: Int = 0,
    @Serializable(with = WifiStatusItemListSerializer::class)
    @SerialName("wifi_status") val list: List<WifiStatusItem> = emptyList()
)

@Serializable
data class WifiStatus5GResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("session_valid") val sessionValid: Int = 0,
    @Serializable(with = WifiStatusItemListSerializer::class)
    @SerialName("wifi_status5g") val list: List<WifiStatusItem> = emptyList()
)

@Serializable
data class WlanControlResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("session_valid") val sessionValid: Int = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("TransmitPower") val transmitPower: String = "100"
)

@Serializable(with = MacFilterRuleSerializer::class)
data class MacFilterRule(
    val mac: String = "",
    val name: String = "",
    val id: String = "",
    val timeStart: String = "00:00",
    val timeStop: String = "23:59",
    val enable: String = "1"
)

object MacFilterRuleSerializer : KSerializer<MacFilterRule> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MacFilterRule", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: MacFilterRule) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
        if (jsonEncoder != null) {
            val obj = kotlinx.serialization.json.buildJsonObject {
                put("mac", kotlinx.serialization.json.JsonPrimitive(value.mac))
                put("name", kotlinx.serialization.json.JsonPrimitive(value.name))
                put("id", kotlinx.serialization.json.JsonPrimitive(value.id))
                put("TimeStart", kotlinx.serialization.json.JsonPrimitive(value.timeStart))
                put("TimeStop", kotlinx.serialization.json.JsonPrimitive(value.timeStop))
                put("Enable", kotlinx.serialization.json.JsonPrimitive(value.enable))
            }
            jsonEncoder.encodeJsonElement(obj)
        } else {
            encoder.encodeString(value.mac)
        }
    }

    override fun deserialize(decoder: Decoder): MacFilterRule {
        val jsonDecoder = decoder as? JsonDecoder ?: return MacFilterRule()
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonObject) return MacFilterRule()

        val mac = element["MAC"]?.jsonPrimitive?.content
            ?: element["mac"]?.jsonPrimitive?.content
            ?: element["MACAddress"]?.jsonPrimitive?.content
            ?: element["macAddress"]?.jsonPrimitive?.content
            ?: ""

        val name = element["name"]?.jsonPrimitive?.content
            ?: element["Name"]?.jsonPrimitive?.content
            ?: element["HostName"]?.jsonPrimitive?.content
            ?: element["hostname"]?.jsonPrimitive?.content
            ?: ""

        val id = element["id"]?.jsonPrimitive?.content
            ?: element["ID"]?.jsonPrimitive?.content
            ?: element["Index"]?.jsonPrimitive?.content
            ?: element["index"]?.jsonPrimitive?.content
            ?: element["ipv4_mac_filter_index"]?.jsonPrimitive?.content
            ?: ""

        val timeStart = element["TimeStart"]?.jsonPrimitive?.content
            ?: element["timeStart"]?.jsonPrimitive?.content
            ?: "00:00"

        val timeStop = element["TimeStop"]?.jsonPrimitive?.content
            ?: element["timeStop"]?.jsonPrimitive?.content
            ?: "23:59"

        val enable = element["Enable"]?.jsonPrimitive?.content
            ?: element["enable"]?.jsonPrimitive?.content
            ?: "1"

        return MacFilterRule(
            mac = mac,
            name = name,
            id = id,
            timeStart = timeStart,
            timeStop = timeStop,
            enable = enable
        )
    }
}

object MacFilterRuleListSerializer : KSerializer<List<MacFilterRule>> {
    private val delegate = FlexibleListSerializer(MacFilterRuleSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<MacFilterRule>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): List<MacFilterRule> = delegate.deserialize(decoder)
}

@Serializable(with = MacFilterInfoSerializer::class)
data class MacFilterInfo(
    val enable: String = "0",
    val mode: String = "black",
    val filterList: List<MacFilterRule> = emptyList()
)

object MacFilterInfoSerializer : KSerializer<MacFilterInfo> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MacFilterInfo", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: MacFilterInfo) {
        val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
        if (jsonEncoder != null) {
            val obj = kotlinx.serialization.json.buildJsonObject {
                put("MACFEnable", kotlinx.serialization.json.JsonPrimitive(value.enable))
                put("MACFMode", kotlinx.serialization.json.JsonPrimitive(value.mode))
                put("mac_filter_data", jsonEncoder.json.encodeToJsonElement(MacFilterRuleListSerializer, value.filterList))
            }
            jsonEncoder.encodeJsonElement(obj)
        }
    }

    override fun deserialize(decoder: Decoder): MacFilterInfo {
        val jsonDecoder = decoder as? JsonDecoder ?: return MacFilterInfo()
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonObject) return MacFilterInfo()

        val enable = element["MACFEnable"]?.jsonPrimitive?.content
            ?: element["ipv4_mac_filter_enable"]?.jsonPrimitive?.content
            ?: element["enable"]?.jsonPrimitive?.content
            ?: "0"

        val mode = element["MACFMode"]?.jsonPrimitive?.content
            ?: element["ipv4_mac_filter_mode"]?.jsonPrimitive?.content
            ?: element["mode"]?.jsonPrimitive?.content
            ?: "black"

        val filterListData = element["mac_filter_data"]
        val list = if (filterListData != null) {
            try {
                jsonDecoder.json.decodeFromJsonElement(MacFilterRuleListSerializer, filterListData)
            } catch (_: Exception) {
                emptyList()
            }
        } else emptyList()

        return MacFilterInfo(enable = enable, mode = mode, filterList = list)
    }
}

@Serializable
data class MacFilterResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("session_valid") val sessionValid: Int = 0,
    @SerialName("ipv4_mac_filter_info") val filterInfo: MacFilterInfo = MacFilterInfo()
)

@Serializable
data class PingResultResponse(
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("session_valid") val sessionValid: Int = 1,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("DiagnosticsState") val diagnosticsState: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("SuccessCount") val successCount: String = "0",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("FailureCount") val failureCount: String = "0",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("AverageResponseTime") val avgResponseTime: String = "0",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("MinimumResponseTime") val minResponseTime: String = "0",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("MaximumResponseTime") val maxResponseTime: String = "0"
) {
    val isComplete: Boolean
        get() = diagnosticsState.equals("Complete", ignoreCase = true)
}

@Serializable
data class TracerouteResultResponse(
    @SerialName("sessionid") val sessionId: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("session_valid") val sessionValid: Int = 1,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("DiagnosticsState") val diagnosticsState: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
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
    @SerialName("DiagnosticsResult10") val res10: String? = null,
    @SerialName("DiagnosticsResult11") val res11: String? = null,
    @SerialName("DiagnosticsResult12") val res12: String? = null,
    @SerialName("DiagnosticsResult13") val res13: String? = null,
    @SerialName("DiagnosticsResult14") val res14: String? = null,
    @SerialName("DiagnosticsResult15") val res15: String? = null
) {
    val hops: List<String>
        get() = listOfNotNull(res1, res2, res3, res4, res5, res6, res7, res8, res9, res10, res11, res12, res13, res14, res15)
            .filter { it.isNotBlank() }
}
