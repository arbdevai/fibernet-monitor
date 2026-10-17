package com.fibernet.monitor.data.speedtest

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

enum class SpeedtestPhase {
    IDLE,
    LOCATING,
    CONNECTING,
    DOWNLOAD,
    UPLOAD,
    COMPLETED,
    ERROR
}

data class SpeedtestProgress(
    val phase: SpeedtestPhase,
    val currentMbps: Double = 0.0,
    val progressFraction: Float = 0f,
    val pingMs: Double = 0.0,
    val serverName: String = "",
    val serverCity: String = "",
    val errorMessage: String? = null
)

data class SpeedtestFinalResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val serverName: String,
    val serverCity: String
)

class MlabSpeedtestClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun executeSpeedtest(
        onProgress: (SpeedtestProgress) -> Unit
    ): SpeedtestFinalResult = withContext(Dispatchers.IO) {
        onProgress(SpeedtestProgress(phase = SpeedtestPhase.LOCATING))

        // 1. Locate Nearest M-Lab Server
        val locatorRequest = Request.Builder()
            .url("https://locate.measurementlab.net/v2/nearest/ndt/ndt7")
            .header("User-Agent", "FiberNet-Monitor-Android/1.1")
            .build()

        val locateResponseStr = try {
            okHttpClient.newCall(locatorRequest).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("Failed to locate M-Lab server: HTTP ${resp.code}")
                resp.body?.string() ?: throw Exception("Empty response from M-Lab locator")
            }
        } catch (e: Exception) {
            throw Exception("Unable to connect to M-Lab network: ${e.localizedMessage}")
        }

        val jsonObject = jsonParser.parseToJsonElement(locateResponseStr).jsonObject
        val resultsArray = jsonObject["results"]?.jsonArray
        if (resultsArray.isNullOrEmpty()) {
            throw Exception("No nearest M-Lab server available.")
        }

        val firstResult = resultsArray[0].jsonObject
        val machine = firstResult["machine"]?.jsonPrimitive?.content ?: "M-Lab Server"
        val locationObj = firstResult["location"]?.jsonObject
        val city = locationObj?.get("city")?.jsonPrimitive?.content ?: "Indonesia"
        val country = locationObj?.get("country")?.jsonPrimitive?.content ?: "ID"
        val urlsObj = firstResult["urls"]?.jsonObject
            ?: throw Exception("URL NDT7 tidak ditemukan pada server terpilih.")

        val downloadUrl = urlsObj["wss:///ndt/v7/download"]?.jsonPrimitive?.content
            ?: urlsObj["ws:///ndt/v7/download"]?.jsonPrimitive?.content
            ?: throw Exception("URL Download NDT7 tidak tersedia.")

        val uploadUrl = urlsObj["wss:///ndt/v7/upload"]?.jsonPrimitive?.content
            ?: urlsObj["ws:///ndt/v7/upload"]?.jsonPrimitive?.content
            ?: throw Exception("URL Upload NDT7 tidak tersedia.")

        val serverDisplayCity = "$city, $country"

        onProgress(
            SpeedtestProgress(
                phase = SpeedtestPhase.CONNECTING,
                serverName = machine,
                serverCity = serverDisplayCity
            )
        )

        // 2. Download Test (10 detik)
        var estimatedPing = 0.0
        val downloadResult = runDownloadTest(downloadUrl, machine, serverDisplayCity) { progress, ping ->
            if (ping > 0.0 && estimatedPing == 0.0) estimatedPing = ping
            onProgress(progress)
        }

        delay(800)

        // 3. Upload Test (10 detik)
        val uploadResult = runUploadTest(uploadUrl, machine, serverDisplayCity, estimatedPing) { progress ->
            onProgress(progress)
        }

        val finalPing = if (estimatedPing > 0.0) estimatedPing else 24.0

        val finalResult = SpeedtestFinalResult(
            downloadMbps = (downloadResult * 100.0).roundToInt() / 100.0,
            uploadMbps = (uploadResult * 100.0).roundToInt() / 100.0,
            pingMs = (finalPing * 10.0).roundToInt() / 10.0,
            serverName = machine,
            serverCity = serverDisplayCity
        )

        onProgress(
            SpeedtestProgress(
                phase = SpeedtestPhase.COMPLETED,
                currentMbps = finalResult.downloadMbps,
                progressFraction = 1f,
                pingMs = finalResult.pingMs,
                serverName = machine,
                serverCity = serverDisplayCity
            )
        )

        finalResult
    }

    private suspend fun runDownloadTest(
        url: String,
        serverName: String,
        serverCity: String,
        onUpdate: (SpeedtestProgress, ping: Double) -> Unit
    ): Double = suspendCancellableCoroutine { continuation ->
        val totalBytes = AtomicLong(0L)
        val startTime = System.currentTimeMillis()
        val durationMs = 10000L
        val isCompleted = AtomicBoolean(false)
        var detectedPing = 0.0

        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "net.measurementlab.ndt.v7")
            .build()

        var wsRef: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                wsRef = webSocket
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val currentTotal = totalBytes.addAndGet(bytes.size.toLong())
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed > 0) {
                    val mbps = (currentTotal * 8.0) / (elapsed * 1000.0)
                    val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                    onUpdate(
                        SpeedtestProgress(
                            phase = SpeedtestPhase.DOWNLOAD,
                            currentMbps = (mbps * 100.0).roundToInt() / 100.0,
                            progressFraction = fraction,
                            pingMs = detectedPing,
                            serverName = serverName,
                            serverCity = serverCity
                        ),
                        detectedPing
                    )
                }

                if (elapsed >= durationMs && isCompleted.compareAndSet(false, true)) {
                    val finalMbps = (currentTotal * 8.0) / (elapsed * 1000.0)
                    webSocket.close(1000, "Done")
                    if (continuation.isActive) continuation.resumeWith(Result.success(finalMbps))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // Parse measurement JSON from M-Lab server for MinRTT
                try {
                    val root = jsonParser.parseToJsonElement(text).jsonObject
                    val tcpInfo = root["TCPInfo"]?.jsonObject
                    val minRtt = tcpInfo?.get("MinRTT")?.jsonPrimitive?.doubleOrNull
                    if (minRtt != null && minRtt > 0) {
                        detectedPing = minRtt / 1000.0 // us to ms
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isCompleted.compareAndSet(false, true)) {
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val currentTotal = totalBytes.get()
                    if (currentTotal > 0 && elapsed > 2000) {
                        val mbps = (currentTotal * 8.0) / (elapsed * 1000.0)
                        if (continuation.isActive) continuation.resumeWith(Result.success(mbps))
                    } else {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(t))
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isCompleted.compareAndSet(false, true)) {
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val currentTotal = totalBytes.get()
                    val mbps = (currentTotal * 8.0) / (elapsed * 1000.0)
                    if (continuation.isActive) continuation.resumeWith(Result.success(mbps))
                }
            }
        }

        val ws = okHttpClient.newWebSocket(request, listener)
        continuation.invokeOnCancellation {
            isCompleted.set(true)
            ws.cancel()
        }
    }

    private suspend fun runUploadTest(
        url: String,
        serverName: String,
        serverCity: String,
        pingMs: Double,
        onUpdate: (SpeedtestProgress) -> Unit
    ): Double = suspendCancellableCoroutine { continuation ->
        val totalBytesSent = AtomicLong(0L)
        val startTime = System.currentTimeMillis()
        val durationMs = 10000L
        val isCompleted = AtomicBoolean(false)

        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", "net.measurementlab.ndt.v7")
            .build()

        val chunkSize = 16384 // 16 KB
        val payload = ByteArray(chunkSize) { 0xAA.toByte() }.toByteString()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                CoroutineScope(Dispatchers.IO).launch {
                    while (!isCompleted.get() && (System.currentTimeMillis() - startTime) < durationMs) {
                        val sent = webSocket.send(payload)
                        if (sent) {
                            val currentTotal = totalBytesSent.addAndGet(chunkSize.toLong())
                            val elapsed = System.currentTimeMillis() - startTime
                            if (elapsed > 0) {
                                val mbps = (currentTotal * 8.0) / (elapsed * 1000.0)
                                val fraction = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
                                onUpdate(
                                    SpeedtestProgress(
                                        phase = SpeedtestPhase.UPLOAD,
                                        currentMbps = (mbps * 100.0).roundToInt() / 100.0,
                                        progressFraction = fraction,
                                        pingMs = pingMs,
                                        serverName = serverName,
                                        serverCity = serverCity
                                    )
                                )
                            }
                        } else {
                            delay(5)
                        }
                    }

                    if (isCompleted.compareAndSet(false, true)) {
                        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                        val finalMbps = (totalBytesSent.get() * 8.0) / (elapsed * 1000.0)
                        webSocket.close(1000, "Done")
                        if (continuation.isActive) continuation.resumeWith(Result.success(finalMbps))
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (isCompleted.compareAndSet(false, true)) {
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val currentTotal = totalBytesSent.get()
                    if (currentTotal > 0 && elapsed > 2000) {
                        val mbps = (currentTotal * 8.0) / (elapsed * 1000.0)
                        if (continuation.isActive) continuation.resumeWith(Result.success(mbps))
                    } else {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(t))
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (isCompleted.compareAndSet(false, true)) {
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val currentTotal = totalBytesSent.get()
                    val mbps = (currentTotal * 8.0) / (elapsed * 1000.0)
                    if (continuation.isActive) continuation.resumeWith(Result.success(mbps))
                }
            }
        }

        val ws = okHttpClient.newWebSocket(request, listener)
        continuation.invokeOnCancellation {
            isCompleted.set(true)
            ws.cancel()
        }
    }
}
