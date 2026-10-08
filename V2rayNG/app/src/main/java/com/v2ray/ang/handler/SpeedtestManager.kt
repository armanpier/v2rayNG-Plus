package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit

object SpeedtestManager {

    data class SpeedTestResult(
        val downloadSpeed: String = "",
        val uploadSpeed: String = "",
    )

    data class RemoteEndpointInfo(
        val country: String?,
        val ipAddress: String?,
    )

    /**
     * Finds a free local TCP port for speed test SOCKS listening, avoiding the active VPN ports.
     */
    fun findFreePort(): Int {
        val reservedPorts = try {
            setOf(
                SettingsManager.getSocksPort(),
                SettingsManager.getHttpPort(),
                10808,
                10809
            ).filter { it > 0 }.toSet()
        } catch (_: Throwable) {
            setOf(10808, 10809)
        }

        for (attempt in 0..10) {
            try {
                ServerSocket(0).use { socket ->
                    socket.reuseAddress = true
                    val port = socket.localPort
                    if (port > 1024 && port !in reservedPorts) {
                        return port
                    }
                }
            } catch (_: Exception) {
            }
        }
        var randomPort: Int
        do {
            randomPort = (20000..60000).random()
        } while (randomPort in reservedPorts)
        return randomPort
    }

    /**
     * Formats bytes per second into human-readable string (e.g. "12.5 MB/s").
     */
    fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024.0 * 1024.0 -> String.format(Locale.ENGLISH, "%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024.0 -> String.format(Locale.ENGLISH, "%.2f KB/s", bytesPerSec / 1024.0)
            bytesPerSec > 0.0 -> String.format(Locale.ENGLISH, "%.0f B/s", bytesPerSec)
            else -> ""
        }
    }

    /**
     * Parses a speed string back to bytes per second for sorting.
     */
    fun parseSpeedToBytes(speedStr: String?): Double {
        if (speedStr.isNullOrBlank()) return 0.0
        val trimmed = speedStr.trim().uppercase(Locale.ENGLISH)
        return try {
            when {
                trimmed.endsWith("MB/S") -> {
                    val num = trimmed.removeSuffix("MB/S").trim().toDoubleOrNull() ?: 0.0
                    num * 1024.0 * 1024.0
                }
                trimmed.endsWith("KB/S") -> {
                    val num = trimmed.removeSuffix("KB/S").trim().toDoubleOrNull() ?: 0.0
                    num * 1024.0
                }
                trimmed.endsWith("B/S") -> {
                    val num = trimmed.removeSuffix("B/S").trim().toDoubleOrNull() ?: 0.0
                    num
                }
                else -> 0.0
            }
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * Builds the download URL using the given payload size.
     */
    fun buildDownloadUrl(
        payloadSizeMb: Int = SettingsManager.getSpeedTestPayloadSize(),
        customUrl: String = SettingsManager.getSpeedTestUrl()
    ): String {
        val downloadBytes = (payloadSizeMb.toLong() * 1024L * 1024L)
        if (customUrl.isNotBlank() && customUrl != AppConfig.DEFAULT_SPEED_TEST_URL) {
            return if (customUrl.contains("cloudflare.com")) {
                if (customUrl.contains("?bytes=")) customUrl
                else "${customUrl.trimEnd('/')}?bytes=$downloadBytes"
            } else {
                customUrl
            }
        }
        return "${AppConfig.CLOUDFLARE_SPEED_TEST_DOWN_URL}?bytes=$downloadBytes"
    }

    /**
     * Builds the upload endpoint URL.
     */
    fun buildUploadUrl(customUrl: String = SettingsManager.getSpeedTestUrl()): String {
        if (customUrl.isNotBlank() && customUrl != AppConfig.DEFAULT_SPEED_TEST_URL && !customUrl.contains("cloudflare.com")) {
            return try {
                val uri = java.net.URI(customUrl)
                "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}/__up"
            } catch (_: Exception) {
                AppConfig.CLOUDFLARE_SPEED_TEST_UP_URL
            }
        }
        return AppConfig.CLOUDFLARE_SPEED_TEST_UP_URL
    }

    /**
     * Calculates the upload payload size based on selected download payload size.
     */
    fun calculateUploadSizeBytes(payloadSizeMb: Int): Long {
        return when (payloadSizeMb) {
            5 -> 2L * 1024 * 1024
            10 -> 4L * 1024 * 1024
            25 -> 10L * 1024 * 1024
            50 -> 20L * 1024 * 1024
            else -> (payloadSizeMb.toLong() * 1024L * 1024L) * 2 / 5
        }
    }

    /**
     * Tests real download speed through the given proxy port.
     */
    fun testDownloadSpeed(
        proxyPort: Int,
        isSocks: Boolean = true,
        testUrl: String = SettingsManager.getSpeedTestUrl(),
        timeoutSeconds: Int = SettingsManager.getSpeedTestTimeout(),
        proxyHost: String = AppConfig.LOOPBACK,
        onProgress: ((String) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): String {
        return testSpeed(
            proxyPort = proxyPort,
            isSocks = isSocks,
            testUrl = testUrl,
            timeoutSeconds = timeoutSeconds,
            proxyHost = proxyHost,
            onProgress = onProgress,
            isCancelled = isCancelled
        ).downloadSpeed
    }

    /**
     * Tests real download and upload speed through the given proxy port.
     */
    fun testSpeed(
        proxyPort: Int,
        isSocks: Boolean = true,
        payloadSizeMb: Int = SettingsManager.getSpeedTestPayloadSize(),
        testUrl: String = buildDownloadUrl(payloadSizeMb),
        uploadUrl: String = buildUploadUrl(),
        timeoutSeconds: Int = SettingsManager.getSpeedTestTimeout(),
        proxyHost: String = AppConfig.LOOPBACK,
        onProgress: ((String) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): SpeedTestResult {
        if (proxyPort <= 0) return SpeedTestResult()
        val proxyType = if (isSocks) Proxy.Type.SOCKS else Proxy.Type.HTTP
        val client = OkHttpClient.Builder()
            .proxy(Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort)))
            .connectTimeout(5000, TimeUnit.MILLISECONDS)
            .readTimeout((timeoutSeconds * 1000).toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout((timeoutSeconds * 1000).toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        var activeCall: Call? = null

        var downloadSpeed = ""
        var uploadSpeed = ""

        try {
            // 1. Download Test
            if (!isCancelled()) {
                val dlRequest = Request.Builder()
                    .url(testUrl)
                    .header("User-Agent", "v2rayNG/${BuildConfig.VERSION_NAME}")
                    .header("Connection", "close")
                    .get()
                    .build()

                val call = client.newCall(dlRequest)
                activeCall = call

                val buffer = ByteArray(32 * 1024)
                var maxDlSpeed = 0.0
                var totalDlBytes = 0L
                val dlStartTime = System.currentTimeMillis()
                var lastDlWindowTime = dlStartTime
                var lastDlWindowBytes = 0L

                try {
                    call.execute().use { response ->
                        if (response.isSuccessful) {
                            val stream = response.body.byteStream()
                            val maxDurationMs = timeoutSeconds * 1000L

                            while (!isCancelled()) {
                                val read = stream.read(buffer)
                                if (read == -1) break
                                totalDlBytes += read
                                val now = System.currentTimeMillis()
                                val totalElapsed = now - dlStartTime

                                if (now - lastDlWindowTime >= 1000) {
                                    val windowBytes = totalDlBytes - lastDlWindowBytes
                                    val windowDuration = (now - lastDlWindowTime) / 1000.0
                                    val currentSpeed = if (windowDuration > 0) windowBytes / windowDuration else 0.0
                                    if (currentSpeed > maxDlSpeed) {
                                        maxDlSpeed = currentSpeed
                                    }
                                    lastDlWindowTime = now
                                    lastDlWindowBytes = totalDlBytes
                                    onProgress?.invoke("↓ " + formatSpeed(maxDlSpeed))
                                }

                                if (totalElapsed >= maxDurationMs) {
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (!isCancelled()) {
                        LogUtil.e(AppConfig.TAG, "Download speed test exception: ${e.message}")
                    }
                }

                val finalDlElapsed = (System.currentTimeMillis() - dlStartTime) / 1000.0
                val avgDlSpeed = if (finalDlElapsed > 0 && totalDlBytes > 0) totalDlBytes / finalDlElapsed else 0.0
                val effectiveDlSpeed = maxOf(maxDlSpeed, avgDlSpeed)
                if (effectiveDlSpeed > 0) {
                    downloadSpeed = formatSpeed(effectiveDlSpeed)
                }
            }

            // 2. Upload Test (Only if download succeeded and not cancelled)
            if (!isCancelled() && downloadSpeed.isNotEmpty()) {
                val uploadSizeBytes = calculateUploadSizeBytes(payloadSizeMb)
                var maxUlSpeed = 0.0
                var totalUlBytes = 0L
                val ulStartTime = System.currentTimeMillis()
                var lastUlWindowTime = ulStartTime
                var lastUlWindowBytes = 0L

                val requestBody = object : RequestBody() {
                    override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                    override fun contentLength() = uploadSizeBytes

                    override fun writeTo(sink: BufferedSink) {
                        val chunk = ByteArray(32 * 1024)
                        var remaining = uploadSizeBytes
                        val maxDurationMs = timeoutSeconds * 1000L

                        while (remaining > 0 && !isCancelled()) {
                            val toWrite = minOf(chunk.size.toLong(), remaining).toInt()
                            sink.write(chunk, 0, toWrite)
                            sink.flush()
                            remaining -= toWrite
                            totalUlBytes += toWrite

                            val now = System.currentTimeMillis()
                            val totalElapsed = now - ulStartTime

                            if (now - lastUlWindowTime >= 1000) {
                                val windowBytes = totalUlBytes - lastUlWindowBytes
                                val windowDuration = (now - lastUlWindowTime) / 1000.0
                                val currentSpeed = if (windowDuration > 0) windowBytes / windowDuration else 0.0
                                if (currentSpeed > maxUlSpeed) {
                                    maxUlSpeed = currentSpeed
                                }
                                lastUlWindowTime = now
                                lastUlWindowBytes = totalUlBytes
                                onProgress?.invoke("↑ " + formatSpeed(maxUlSpeed))
                            }

                            if (totalElapsed >= maxDurationMs) {
                                break
                            }
                        }
                    }
                }

                val ulRequest = Request.Builder()
                    .url(uploadUrl)
                    .header("User-Agent", "v2rayNG/${BuildConfig.VERSION_NAME}")
                    .header("Connection", "close")
                    .post(requestBody)
                    .build()

                val call = client.newCall(ulRequest)
                activeCall = call

                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            LogUtil.w(AppConfig.TAG, "Upload speed test response status: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    if (!isCancelled()) {
                        LogUtil.e(AppConfig.TAG, "Upload speed test exception: ${e.message}")
                    }
                }

                val finalUlElapsed = (System.currentTimeMillis() - ulStartTime) / 1000.0
                val avgUlSpeed = if (finalUlElapsed > 0 && totalUlBytes > 0) totalUlBytes / finalUlElapsed else 0.0
                val effectiveUlSpeed = maxOf(maxUlSpeed, avgUlSpeed)
                if (effectiveUlSpeed > 0) {
                    uploadSpeed = formatSpeed(effectiveUlSpeed)
                }
            }
        } finally {
            try {
                if (isCancelled()) {
                    activeCall?.cancel()
                }
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            } catch (_: Throwable) {
            }
        }

        return SpeedTestResult(
            downloadSpeed = downloadSpeed,
            uploadSpeed = uploadSpeed
        )
    }

    /**
     * Measures the time taken to establish a TCP connection to a given URL and port.
     */
    fun socketConnectTime(url: String, port: Int, timeoutMs: Int = 1500): Long {
        var socket: Socket? = null
        val start = System.currentTimeMillis()

        try {
            socket = Socket()
            socket.connect(InetSocketAddress(url, port), timeoutMs)

            return System.currentTimeMillis() - start
        } catch (e: UnknownHostException) {
            LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: ${e.message}")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        } finally {
            socket?.let { s ->
                try {
                    if (!s.isClosed) {
                        s.close()
                    }
                } catch (_: IOException) {
                }
            }
        }
        return -1
    }

    fun getRemoteIPInfo(): RemoteEndpointInfo? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        if (httpPort == 0) return null
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000,
                httpPort = httpPort,
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() }

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        return RemoteEndpointInfo(
            country = country,
            ipAddress = ip,
        )
    }
}
