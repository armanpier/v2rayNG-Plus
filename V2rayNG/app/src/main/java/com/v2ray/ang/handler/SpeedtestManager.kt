package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit

object SpeedtestManager {

    data class RemoteEndpointInfo(
        val country: String?,
        val ipAddress: String?,
    )

    /**
     * Finds a free local TCP port for speed test SOCKS listening.
     */
    fun findFreePort(): Int {
        return try {
            ServerSocket(0).use { socket ->
                socket.reuseAddress = true
                socket.localPort
            }
        } catch (_: Exception) {
            (10000..60000).random()
        }
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
        if (proxyPort <= 0 || testUrl.isBlank()) return ""
        val proxyType = if (isSocks) Proxy.Type.SOCKS else Proxy.Type.HTTP
        val client = OkHttpClient.Builder()
            .proxy(Proxy(proxyType, InetSocketAddress(proxyHost, proxyPort)))
            .connectTimeout(5000, TimeUnit.MILLISECONDS)
            .readTimeout((timeoutSeconds * 1000).toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val request = Request.Builder()
            .url(testUrl)
            .header("User-Agent", "v2rayNG/${BuildConfig.VERSION_NAME}")
            .header("Connection", "close")
            .get()
            .build()

        val buffer = ByteArray(32 * 1024)
        var maxSpeedBytesPerSec = 0.0
        var totalBytes = 0L
        val startTime = System.currentTimeMillis()
        var lastWindowTime = startTime
        var lastWindowBytes = 0L

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    LogUtil.w(AppConfig.TAG, "Speed test request failed with status: ${response.code}")
                    return ""
                }
                val stream = response.body.byteStream()
                val maxDurationMs = timeoutSeconds * 1000L

                while (!isCancelled()) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                    val now = System.currentTimeMillis()
                    val totalElapsed = now - startTime

                    if (now - lastWindowTime >= 1000) {
                        val windowBytes = totalBytes - lastWindowBytes
                        val windowDuration = (now - lastWindowTime) / 1000.0
                        val currentSpeed = if (windowDuration > 0) windowBytes / windowDuration else 0.0
                        if (currentSpeed > maxSpeedBytesPerSec) {
                            maxSpeedBytesPerSec = currentSpeed
                        }
                        lastWindowTime = now
                        lastWindowBytes = totalBytes
                        onProgress?.invoke(formatSpeed(maxSpeedBytesPerSec))
                    }

                    if (totalElapsed >= maxDurationMs) {
                        break
                    }
                }
            }
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Speed test exception: ${e.message}")
        }

        val finalElapsed = (System.currentTimeMillis() - startTime) / 1000.0
        val avgSpeed = if (finalElapsed > 0 && totalBytes > 0) totalBytes / finalElapsed else 0.0
        val effectiveSpeed = maxOf(maxSpeedBytesPerSec, avgSpeed)
        return if (effectiveSpeed > 0) formatSpeed(effectiveSpeed) else ""
    }

    /**
     * Measures the time taken to establish a TCP connection to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
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
                } catch (closeEx: IOException) {
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
