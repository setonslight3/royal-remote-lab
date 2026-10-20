package com.example.royalremotelab.network

import com.example.royalremotelab.model.DiagnosticConfidence
import com.example.royalremotelab.model.HostReachabilityResult
import com.example.royalremotelab.model.PortProbeRecord
import com.example.royalremotelab.model.ResultStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

object HostReachabilityChecker {

    /**
     * Documented ports to audit based strictly on observed TV clues and known smart TV standards.
     * No arbitrary or brute-force scanning.
     */
    val EVIDENCE_BACKED_PORTS = listOf(
        PortAuditDefinition(
            port = 8008,
            serviceName = "DIAL REST / YouTube Launch",
            justification = "Documented DIAL standard (Netflix/Google). Matches user clue that Bellon successfully launched YouTube.",
            expectedConfidenceOnOpen = DiagnosticConfidence.HIGH_CONFIDENCE_MATCH
        ),
        PortAuditDefinition(
            port = 49152,
            serviceName = "UPnP / DLNA Dynamic Port 1",
            justification = "Standard IANA ephemeral/dynamic port commonly bound by Novatek & Linux DLNA DMR stacks.",
            expectedConfidenceOnOpen = DiagnosticConfidence.INVESTIGATIVE_MATCH
        ),
        PortAuditDefinition(
            port = 49153,
            serviceName = "UPnP / DLNA Dynamic Port 2",
            justification = "Secondary dynamic port often assigned to AVTransport / RenderingControl.",
            expectedConfidenceOnOpen = DiagnosticConfidence.INVESTIGATIVE_MATCH
        ),
        PortAuditDefinition(
            port = 7676,
            serviceName = "UPnP Media Delivery",
            justification = "Frequently used by Smart TV media renderer daemons.",
            expectedConfidenceOnOpen = DiagnosticConfidence.INVESTIGATIVE_MATCH
        ),
        PortAuditDefinition(
            port = 52235,
            serviceName = "Alternate DLNA DMR",
            justification = "Observed on embedded Linux Smart TV chassis for Digital Media Renderer.",
            expectedConfidenceOnOpen = DiagnosticConfidence.INVESTIGATIVE_MATCH
        ),
        PortAuditDefinition(
            port = 80,
            serviceName = "Standard HTTP Web Server",
            justification = "Baseline check for embedded web management or OSD service (previously refused on 192.168.1.38).",
            expectedConfidenceOnOpen = DiagnosticConfidence.INVESTIGATIVE_MATCH
        ),
        PortAuditDefinition(
            port = 8088,
            serviceName = "DLNA RenderingControl Default",
            justification = "Standard UPnP rendering port (previously refused on 192.168.1.38).",
            expectedConfidenceOnOpen = DiagnosticConfidence.INVESTIGATIVE_MATCH
        ),
        PortAuditDefinition(
            port = 56789,
            serviceName = "Vestel Smart Center Socket",
            justification = "Documented clue from Bellon app diagnostic ('REFUSED, why=vestel_virtual_remote_off').",
            expectedConfidenceOnOpen = DiagnosticConfidence.INVESTIGATIVE_MATCH
        ),
        PortAuditDefinition(
            port = 6466,
            serviceName = "Google TV Remote Pairing (TLS)",
            justification = "Verifies or definitively rules out certified Google TV / Android TV Remote service.",
            expectedConfidenceOnOpen = DiagnosticConfidence.INVESTIGATIVE_MATCH
        ),
        PortAuditDefinition(
            port = 6467,
            serviceName = "Google TV Remote Control (TLS)",
            justification = "Protobuf command channel for Google TV Remote protocol v2.",
            expectedConfidenceOnOpen = DiagnosticConfidence.INVESTIGATIVE_MATCH
        )
    )

    data class PortAuditDefinition(
        val port: Int,
        val serviceName: String,
        val justification: String,
        val expectedConfidenceOnOpen: DiagnosticConfidence
    )

    /**
     * Checks host reachability with clear technical explanation of ICMP vs socket behavior.
     */
    suspend fun auditHostReachability(host: String, timeoutMs: Int = 3000): HostReachabilityResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var isIcmpReachable = false
            var icmpDuration = 0L

            try {
                val inet = InetAddress.getByName(host.trim())
                val icmpStart = System.currentTimeMillis()
                isIcmpReachable = inet.isReachable(timeoutMs)
                icmpDuration = System.currentTimeMillis() - icmpStart
            } catch (_: Exception) {}

            // Fallback: Probe candidate smart TV ports to detect if host stack is alive
            var openFallbackPort: Int? = null
            var fallbackDuration = 0L
            val testPorts = listOf(8008, 49152, 7676, 80, 8088)

            for (port in testPorts) {
                val probe = NetworkUtils.probeTcpPort(host, port, "Reachability probe", timeoutMs = 1000)
                if (probe.isOpen) {
                    openFallbackPort = port
                    fallbackDuration = probe.latencyMs
                    break
                }
            }

            val explanation = buildReachabilityExplanation(isIcmpReachable, openFallbackPort, host)

            HostReachabilityResult(
                host = host,
                isIcmpReachable = isIcmpReachable,
                icmpLatencyMs = icmpDuration,
                openFallbackPort = openFallbackPort,
                fallbackLatencyMs = fallbackDuration,
                explanation = explanation
            )
        }

    /**
     * Executes targeted evidence-backed port audit.
     */
    suspend fun runEvidenceBackedPortAudit(
        host: String,
        timeoutMs: Int = 2000,
        onProgress: (String) -> Unit = {}
    ): List<PortProbeRecord> = withContext(Dispatchers.IO) {
        val records = mutableListOf<PortProbeRecord>()

        for (def in EVIDENCE_BACKED_PORTS) {
            onProgress("Checking Port ${def.port} (${def.serviceName})...")
            val check = NetworkUtils.probeTcpPort(
                host = host,
                port = def.port,
                serviceName = def.serviceName,
                timeoutMs = timeoutMs
            )

            val status = when {
                check.isOpen -> ResultStatus.SUCCESS
                check.details.contains("refused", ignoreCase = true) -> ResultStatus.REJECTED
                check.details.contains("time", ignoreCase = true) -> ResultStatus.TIMED_OUT
                else -> ResultStatus.ERROR
            }

            val confidence = when {
                check.isOpen -> def.expectedConfidenceOnOpen
                status == ResultStatus.REJECTED && (def.port == 56789 || def.port == 6466 || def.port == 6467) ->
                    DiagnosticConfidence.CONFIRMED_INCOMPATIBLE
                else -> DiagnosticConfidence.UNCONFIRMED
            }

            records.add(
                PortProbeRecord(
                    port = def.port,
                    serviceName = def.serviceName,
                    documentedJustification = def.justification,
                    status = status,
                    latencyMs = check.latencyMs,
                    rawDetails = check.details,
                    confidence = confidence
                )
            )
        }

        records
    }

    private fun buildReachabilityExplanation(icmpReachable: Boolean, openPort: Int?, host: String): String {
        return when {
            icmpReachable && openPort != null -> {
                "Host $host responded to ICMP Echo ping and accepted TCP connection on port $openPort. " +
                        "The device is online and actively processing network traffic."
            }
            !icmpReachable && openPort != null -> {
                "Host $host dropped ICMP Echo ping, but accepted TCP connection on port $openPort! " +
                        "Note: Many embedded smart TV network stacks (including Novatek Linux) deliberately drop ICMP ping packets " +
                        "for security and power saving. The TV is online even though ping failed."
            }
            icmpReachable && openPort == null -> {
                "Host $host responded to ICMP Echo ping, but probed TCP ports were closed or timed out. " +
                        "The TV host is active on the network, but listening services may be bound to dynamic ports or filtered."
            }
            else -> {
                "Host $host did not respond to ICMP Echo ping, and candidate TCP ports did not accept connections. " +
                        "IMPORTANT: Failure to respond to a ping does NOT necessarily mean the TV is offline. " +
                        "Embedded smart TVs frequently ignore ping, and listening services (like DLNA DMR) may be on dynamic ports. " +
                        "Use SSDP Multicast Discovery in the Diagnostics tab to locate the TV's advertised service endpoint."
            }
        }
    }
}
