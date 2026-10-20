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
 * Candidate C: DIAL (Discovery and Launch) & Novatek Smart Services.
 *
 * Grounded in observed TV behavior:
 * - "Bellon Apps' Universal TV Remote discovers/connects to the TV and launching YouTube appeared to work, but navigation did not."
 * - DIAL (port 8008/8080) is an open smart-TV standard used to launch apps like YouTube.
 * - DIAL explicitly DOES NOT support system navigation or directional arrow keys.
 */
class DialNovatekProtocol : TvProtocol {
    override val id: String = "dial_novatek"
    override val name: String = "DIAL REST / Novatek App Launch"
    override val category: String = "REST / App Control"
    override val description: String =
        "Communicates with DIAL REST service (ports 8008/8080) to inspect device description and launch smart apps like YouTube."
    override val supportedCommands: Set<RemoteCommand> = setOf(
        RemoteCommand.HOME, // Triggers DIAL App status check
        RemoteCommand.PLAY, // Launch/Resume App
        RemoteCommand.STOP  // Stop App instance
    )
    override val caveats: String =
        "DIAL is specifically designed to discover and launch TV apps (such as YouTube). " +
                "It explains why Bellon could launch YouTube on your Royal TV, while D-Pad navigation buttons failed."
    override val documentationReference: String =
        "DIAL (Discovery and Launch) Specification v2.2.1 / Netflix & Google"

    private val dialPorts = listOf(8008, 8080, 9080)

    override suspend fun runDiagnostics(config: ConnectionConfig): DiagnosticResult {
        if (config.isDemoMode) return DiagnosticResult.simulated(name)

        val portChecks = mutableListOf<PortCheck>()
        var activePort: Int? = null

        for (port in dialPorts) {
            val check = NetworkUtils.probeTcpPort(
                host = config.host,
                port = port,
                serviceName = "DIAL REST Port $port",
                timeoutMs = config.timeoutMs
            )
            portChecks.add(check)
            if (check.isOpen && activePort == null) {
                activePort = port
            }
        }

        if (activePort != null) {
            // Test DIAL endpoint
            val dialResponse = NetworkUtils.executeHttpRequest(
                urlString = "http://${config.host}:$activePort/ssdp/device-desc.xml",
                timeoutMs = config.timeoutMs
            )

            return DiagnosticResult(
                status = DiagnosticStatus.RESPONDING,
                headline = "DIAL Service responding on port $activePort!",
                summary = "Port $activePort is OPEN. Query returned HTTP ${dialResponse.statusCode}. " +
                        "This confirms why Bellon was able to launch YouTube: DIAL allows app control over HTTP REST, " +
                        "even when D-Pad navigation protocols are absent.",
                portChecks = portChecks,
                recommendation = "You can test launching YouTube via DIAL. Note that system navigation remains unsupported by DIAL.",
                isSimulated = false
            )
        } else {
            return DiagnosticResult(
                status = DiagnosticStatus.REJECTED,
                headline = "DIAL ports (${dialPorts.joinToString()}) did not respond",
                summary = "Neither port 8008 nor 8080 accepted connection on ${config.host}. " +
                        "The TV may be in deep standby, DIAL daemon might use a non-standard port, or IP address changed.",
                portChecks = portChecks,
                recommendation = "Check TV power state and verify IP in Connection tab.",
                isSimulated = false
            )
        }
    }

    override suspend fun sendCommand(command: RemoteCommand, config: ConnectionConfig): CommandResult {
        if (config.isDemoMode) return CommandResult.simulated(command)

        if (!supportedCommands.contains(command)) {
            return CommandResult.unsupported(name, command)
        }

        val port = config.customPort ?: 8008
        return when (command) {
            RemoteCommand.PLAY, RemoteCommand.HOME -> {
                // DIAL launch YouTube
                val url = "http://${config.host}:$port/apps/YouTube"
                val response = NetworkUtils.executeHttpRequest(
                    urlString = url,
                    method = "POST",
                    body = "",
                    timeoutMs = config.timeoutMs
                )
                if (response.isSuccess || response.statusCode == 201 || response.statusCode == 200) {
                    CommandResult(
                        status = ResultStatus.NETWORK_RESPONSE,
                        summary = "DIAL YouTube launch requested (HTTP ${response.statusCode})",
                        details = "POST $url returned ${response.statusCode} in ${response.durationMs}ms. Look at TV to confirm YouTube opened.",
                        isSimulated = false,
                        latencyMs = response.durationMs
                    )
                } else {
                    CommandResult(
                        status = ResultStatus.ERROR,
                        summary = "DIAL request failed (HTTP ${response.statusCode})",
                        details = "POST $url: ${response.responseBody}",
                        isSimulated = false,
                        latencyMs = response.durationMs
                    )
                }
            }
            RemoteCommand.STOP -> {
                val url = "http://${config.host}:$port/apps/YouTube/run"
                val response = NetworkUtils.executeHttpRequest(
                    urlString = url,
                    method = "DELETE",
                    timeoutMs = config.timeoutMs
                )
                CommandResult(
                    status = if (response.isSuccess) ResultStatus.NETWORK_RESPONSE else ResultStatus.ERROR,
                    summary = "DIAL Stop YouTube sent (HTTP ${response.statusCode})",
                    details = "DELETE $url in ${response.durationMs}ms: ${response.responseBody}",
                    isSimulated = false,
                    latencyMs = response.durationMs
                )
            }
            else -> CommandResult.unsupported(name, command)
        }
    }
}
