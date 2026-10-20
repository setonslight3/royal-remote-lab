package com.example.royalremotelab

import com.example.royalremotelab.model.ConnectionConfig
import org.junit.Assert.*
import org.junit.Test

class ConnectionConfigTest {

    @Test
    fun defaultHost_isValid() {
        val config = ConnectionConfig()
        assertEquals("192.168.1.38", config.host)
        assertTrue(config.isValid)
        assertNull(config.validate())
    }

    @Test
    fun validIpv4Addresses_passValidation() {
        val validIps = listOf(
            "192.168.1.1",
            "192.168.1.38",
            "10.0.0.5",
            "172.16.0.25",
            "127.0.0.1"
        )
        for (ip in validIps) {
            val config = ConnectionConfig(host = ip)
            assertTrue("Expected $ip to be valid", config.isValid)
            assertNull(config.validate())
        }
    }

    @Test
    fun invalidIpv4Addresses_failValidation() {
        val invalidIps = listOf(
            "",
            "   ",
            "999.999.999.999",
            "192.168.1",
            "192.168.1.256",
            "invalid_host_name#$@"
        )
        for (ip in invalidIps) {
            val config = ConnectionConfig(host = ip)
            assertFalse("Expected $ip to be invalid", config.isValid)
            assertNotNull(config.validate())
        }
    }

    @Test
    fun timeoutRange_validation() {
        val tooLow = ConnectionConfig(timeoutMs = 400)
        assertFalse(tooLow.isValid)
        assertTrue(tooLow.validate()!!.contains("Timeout must be between"))

        val tooHigh = ConnectionConfig(timeoutMs = 35000)
        assertFalse(tooHigh.isValid)
        assertTrue(tooHigh.validate()!!.contains("Timeout must be between"))

        val valid = ConnectionConfig(timeoutMs = 5000)
        assertTrue(valid.isValid)
    }
}
