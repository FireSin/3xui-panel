package com.firesin.xuipanel.core.xui.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val json = Json { ignoreUnknownKeys = true }

class ServerStatusResponseDtoTest {

    @Test
    fun `happy path - success true with full server status`() {
        val dto = json.decodeFromString<ServerStatusResponseDto>(
            """{"success":true,"obj":{"cpu":12.5,"mem":{"current":1024000000,"total":2048000000},"xray":{"state":"running","errorMsg":"","version":"1.8.0"},"uptime":86400,"loads":[0.5,0.6,0.7],"tcpCount":128,"udpCount":256,"netIO":{"up":1000000,"down":2000000},"netTraffic":{"sent":5000000000,"recv":10000000000},"publicIP":{"ipv4":"203.0.113.1","ipv6":"2001:db8::1"},"appStats":{"threads":42,"mem":512000000}}}"""
        )
        assertTrue(dto.success)
        assertNotNull(dto.obj)
        assertEquals(12.5, dto.obj!!.cpu)
        assertEquals(86400L, dto.obj!!.uptime)
        assertEquals("running", dto.obj!!.xray.state)
        assertEquals(128, dto.obj!!.tcpCount)
        assertEquals(256, dto.obj!!.udpCount)
        assertEquals(5000000000L, dto.obj!!.netTraffic.sent)
        assertEquals(10000000000L, dto.obj!!.netTraffic.recv)
        assertEquals("203.0.113.1", dto.obj!!.publicIP.ipv4)
        assertEquals("2001:db8::1", dto.obj!!.publicIP.ipv6)
        assertNotNull(dto.obj!!.appStats)
        assertEquals(42, dto.obj!!.appStats!!.threads)
        assertNull(dto.msg)
    }

    @Test
    fun `success true with null obj and no appStats`() {
        val dto = json.decodeFromString<ServerStatusResponseDto>(
            """{"success":true,"obj":{"cpu":5.0,"mem":{"current":500000000,"total":1000000000},"xray":{"state":"stopped","errorMsg":"connection failed","version":"1.7.0"},"uptime":0,"loads":[0.1,0.2,0.3],"tcpCount":0,"udpCount":0,"netIO":{"up":0,"down":0},"netTraffic":{"sent":0,"recv":0},"publicIP":{"ipv4":"","ipv6":""}}}"""
        )
        assertTrue(dto.success)
        assertNotNull(dto.obj)
        assertEquals("stopped", dto.obj!!.xray.state)
        assertEquals("connection failed", dto.obj!!.xray.errorMsg)
        assertNull(dto.obj!!.appStats)
    }

    @Test
    fun `success false with msg`() {
        val dto = json.decodeFromString<ServerStatusResponseDto>(
            """{"success":false,"msg":"authentication failed"}"""
        )
        assertFalse(dto.success)
        assertNull(dto.obj)
        assertEquals("authentication failed", dto.msg)
    }

    @Test
    fun `unknown fields are ignored`() {
        val dto = json.decodeFromString<ServerStatusResponseDto>(
            """{"success":true,"obj":{"cpu":1.0,"mem":{"current":100,"total":200},"xray":{"state":"running","errorMsg":"","version":"1.0"},"uptime":1,"loads":[],"tcpCount":1,"udpCount":1,"netIO":{"up":1,"down":1},"netTraffic":{"sent":1,"recv":1},"publicIP":{"ipv4":"1","ipv6":"1"}},"extra":"ignored","timestamp":123456789}"""
        )
        assertTrue(dto.success)
        assertNotNull(dto.obj)
        assertEquals(1.0, dto.obj!!.cpu)
    }
}
