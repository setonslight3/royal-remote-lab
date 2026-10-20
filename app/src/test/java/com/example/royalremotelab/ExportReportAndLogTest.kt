package com.example.royalremotelab

import com.example.royalremotelab.data.LabRepository
import com.example.royalremotelab.model.*
import org.junit.Assert.*
import org.junit.Test

class ExportReportAndLogTest {

    @Test
    fun testLogEntry_ipScrubbingDefault() {
        val entry = TestLogEntry(
            protocolId = "vestel_smartcenter",
            protocolName = "Vestel Smart Center",
            targetHost = "192.168.1.38",
            actionName = "UP",
            result = CommandResult(
                status = ResultStatus.REJECTED,
                summary = "Connection Refused",
                details = "Port 56789 closed"
            ),
            userFeedback = UserFeedback.NO_REACTION
        )

        val scrubbed = entry.toExportString(includeIp = false)
        assertFalse("Export string must not expose IP when includeIp=false", scrubbed.contains("192.168.1.38"))
        assertTrue("Export string must contain [IP_REDACTED]", scrubbed.contains("[IP_REDACTED]"))

        val unscrubbed = entry.toExportString(includeIp = true)
        assertTrue("Export string must contain IP when includeIp=true", unscrubbed.contains("192.168.1.38"))
    }

    @Test
    fun labRepository_exportReportContainsStructure() {
        val repo = LabRepository()
        repo.updateConfig(ConnectionConfig(host = "192.168.1.38"))

        val reportDefault = repo.exportReport(includeIp = false)
        assertTrue(reportDefault.contains("ROYAL REMOTE LAB - PROTOCOL INVESTIGATION REPORT"))
        assertTrue(reportDefault.contains("Royal RTV65D8100"))
        assertTrue(reportDefault.contains("Target Host: [IP_REDACTED]"))
        assertTrue(reportDefault.contains("HONEST ASSESSMENT NOTICE"))

        val reportWithIp = repo.exportReport(includeIp = true)
        assertTrue(reportWithIp.contains("Target Host: 192.168.1.38"))
    }
}
