package com.example.royalremotelab.protocols

import com.example.royalremotelab.model.CommandResult
import com.example.royalremotelab.model.ConnectionConfig
import com.example.royalremotelab.model.DiagnosticResult
import com.example.royalremotelab.model.DiagnosticStatus
import com.example.royalremotelab.model.PortCheck
import com.example.royalremotelab.model.RemoteCommand
import com.example.royalremotelab.model.ResultStatus
import com.example.royalremotelab.network.NetworkUtils

/**
 * Candidate C3: Targeted Smart TV Port Investigator.
 *
 * Safely probes candidate smart TV network services strictly against the user-specified TV IP.
 * Never performs broad network discovery or brute-force scans.
 */
class RawTcpProbeProtocol : TvProtocol {
    override val id: String = "targeted_port_probe"
    override val name: String = "Targeted Smart TV Port Audit"
    override val category: String = "Diagnostic Audit"
    override val description: String =
        "Checks candidate smart TV ports on the user's TV IP to identify active listeners (DIAL, DLNA, ADB, Web OSD)."
    override val supportedCommands: Set<RemoteCommand> = emptySet()
    override val caveats: String =
        "CRITICAL RULE: An open port alone must NEVER be treated as proof that remote control commands work. " +
                "Only observe physical TV reaction to confirm control."
    override val documentationReference: String =
        "Smart TV Network Architecture & Well-Known Service Ports (IANA / UPnP / DIAL)"

    private val candidateAuditPorts = listOf(
        Pair(80, "HTTP Web Server / OSD"),
        Pair(8008, "DIAL REST / YouTube (Netflix/Google)"),
        Pair(8080, "Alternate HTTP / Web Management"),
        Pair(8088, "DLNA DMR RenderingControl"),
        Pair(56789, "Vestel Smart Center Virtual Remote"),
        Pair(6466, "Google TV Remote Pairing (TLS)"),
        Pair(6467, "Google TV Remote Control (TLS)"),
        Pair(5555, "Android ADB Debug Bridge"),
        Pair(7676, "UPnP Media Delivery"),
        Pair(9080, "Novatek / Linux Embedded Web Server")
    )

    override suspend fun runDiagnostics(config: ConnectionConfig): DiagnosticResult {
        if (config.isDemoMode) return DiagnosticResult.simulated(name)

        val portChecks = mutableListOf<PortCheck>()
        val openPorts = mutableListOf<Int>()

        for ((port, serviceName) in candidateAuditPorts) {
            val check = NetworkUtils.probeTcpPort(
                host = config.host,
                port = port,
                serviceName = serviceName,
                timeoutMs = (config.timeoutMs / 2).coerceAtLeast(800)
            )
            portChecks.add(check)
            if (check.isOpen) {
                openPorts.add(port)
            }
        }

        val status = if (openPorts.isNotEmpty()) DiagnosticStatus.RESPONDING else DiagnosticStatus.REJECTED
        val headline = if (openPorts.isNotEmpty()) {
            "Found ${openPorts.size} open port(s) on ${config.host}: ${openPorts.joinToString()}"
        } else {
            "All candidate ports on ${config.host} refused connection or timed out"
        }

        val summary = if (openPorts.isNotEmpty()) {
            "Active services detected on: ${openPorts.joinToString { "Port $it" }}. " +
                    "Compare these open ports against the specific protocol adapters in Protocol Lab."
        } else {
            "No common smart TV control ports were open on ${config.host}. " +
                    "Verify the TV is turned on, connected to the same Wi-Fi subnet, or check if the TV IP has changed."
        }

        return DiagnosticResult(
            status = status,
            headline = headline,
            summary = summary,
            portChecks = portChecks,
            recommendation = "Select the specific protocol adapter matching any open port.",
            isSimulated = false
        )
    }

    override suspend fun sendCommand(command: RemoteCommand, config: ConnectionConfig): CommandResult {
        return CommandResult(
            status = ResultStatus.UNSUPPORTED,
            summary = "Targeted Port Audit is a diagnostic-only adapter",
            details = "To send remote control commands, switch to DLNA, Vestel, or DIAL in the Protocol Lab tab.",
            isSimulated = config.isDemoMode,
            latencyMs = 0L
        )
    }
}
