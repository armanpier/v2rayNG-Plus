package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeedtestManagerTest {

    @Test
    fun testFormatSpeed() {
        assertEquals("", SpeedtestManager.formatSpeed(0.0))
        assertEquals("500 B/s", SpeedtestManager.formatSpeed(500.0))
        assertEquals("1.00 KB/s", SpeedtestManager.formatSpeed(1024.0))
        assertEquals("1.50 KB/s", SpeedtestManager.formatSpeed(1536.0))
        assertEquals("1.00 MB/s", SpeedtestManager.formatSpeed(1024.0 * 1024.0))
        assertEquals("12.50 MB/s", SpeedtestManager.formatSpeed(12.5 * 1024.0 * 1024.0))
    }

    @Test
    fun testParseSpeedToBytes() {
        assertEquals(0.0, SpeedtestManager.parseSpeedToBytes(""))
        assertEquals(0.0, SpeedtestManager.parseSpeedToBytes("invalid"))
        assertEquals(500.0, SpeedtestManager.parseSpeedToBytes("500 B/s"))
        assertEquals(1536.0, SpeedtestManager.parseSpeedToBytes("1.50 KB/s"), 0.01)
        assertEquals(1024.0 * 1024.0, SpeedtestManager.parseSpeedToBytes("1.00 MB/s"), 0.01)
        assertEquals(12.5 * 1024.0 * 1024.0, SpeedtestManager.parseSpeedToBytes("12.50 MB/s"), 0.01)
    }

    @Test
    fun testFindFreePort() {
        val port = SpeedtestManager.findFreePort()
        assertTrue(port in 1024..65535, "Port should be within unprivileged port range: $port")
    }

    @Test
    fun testBuildDownloadUrl() {
        val url5 = SpeedtestManager.buildDownloadUrl(payloadSizeMb = 5, customUrl = "")
        assertTrue(url5.contains("speed.cloudflare.com/__down?bytes=5242880"))

        val url10 = SpeedtestManager.buildDownloadUrl(payloadSizeMb = 10, customUrl = "")
        assertTrue(url10.contains("speed.cloudflare.com/__down?bytes=10485760"))

        val custom = SpeedtestManager.buildDownloadUrl(payloadSizeMb = 10, customUrl = "https://speed.cloudflare.com/__down")
        assertTrue(custom.contains("bytes=10485760"))

        val customOther = SpeedtestManager.buildDownloadUrl(payloadSizeMb = 10, customUrl = "https://example.com/test.bin")
        assertEquals("https://example.com/test.bin", customOther)
    }

    @Test
    fun testBuildUploadUrl() {
        val defaultUrl = SpeedtestManager.buildUploadUrl("")
        assertEquals("https://speed.cloudflare.com/__up", defaultUrl)

        val custom = SpeedtestManager.buildUploadUrl("https://mycdn.net/down")
        assertEquals("https://mycdn.net/__up", custom)
    }

    @Test
    fun testCalculateUploadSizeBytes() {
        assertEquals(2L * 1024 * 1024, SpeedtestManager.calculateUploadSizeBytes(5))
        assertEquals(4L * 1024 * 1024, SpeedtestManager.calculateUploadSizeBytes(10))
        assertEquals(10L * 1024 * 1024, SpeedtestManager.calculateUploadSizeBytes(25))
        assertEquals(20L * 1024 * 1024, SpeedtestManager.calculateUploadSizeBytes(50))
        assertEquals(8L * 1024 * 1024, SpeedtestManager.calculateUploadSizeBytes(20))
    }
}
