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
}
