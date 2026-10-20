package com.example.royalremotelab.protocols

import com.example.royalremotelab.model.CommandCategory
import com.example.royalremotelab.model.CommandResult
import com.example.royalremotelab.model.ConnectionConfig
import com.example.royalremotelab.model.DiagnosticResult
import com.example.royalremotelab.model.DiagnosticStatus
import com.example.royalremotelab.model.PortCheck
import com.example.royalremotelab.model.RemoteCommand
import com.example.royalremotelab.model.ResultStatus
import com.example.royalremotelab.network.NetworkUtils

/**
 * Candidate A & D: DLNA / UPnP Media Renderer Protocol.
 *
 * Grounded in observed TV behavior:
 * - "Digital Media Renderer enabled" in Royal TV settings.
 * - XCast discovers TV as "Smart TV DLNA" and volume up/down functioned.
 * - UPnP AV specifications explicitly define RenderingControl (Volume, Mute)
 *   and AVTransport (Play, Pause, Stop), but DO NOT support D-Pad navigation.
 */
class DlnaProtocol : TvProtocol {
    override val id: String = "dlna_upnp"
    override val name: String = "DLNA / UPnP Media Renderer"
    override val category: String = "Media & Volume (DMR)"
    override val description: String =
        "Communicates with the TV's Digital Media Renderer (DMR) for volume and media playback control."
    override val supportedCommands: Set<RemoteCommand> = setOf(
        RemoteCommand.VOLUME_UP,
        RemoteCommand.VOLUME_DOWN,
        RemoteCommand.MUTE,
        RemoteCommand.PLAY,
        RemoteCommand.PAUSE,
        RemoteCommand.STOP
    )
    override val caveats: String =
        "IMPORTANT: DLNA DMR inherently lacks system navigation (Up, Down, Left, Right, Home, Back, Menu). " +
                "It explains why XCast could control volume but not navigate the TV menus."
    override val documentationReference: String =
        "UPnP Forum AV Architecture v1.0 / RenderingControl:1 & AVTransport:1 specifications."

    // Commonly used UPnP/DLNA listening ports on smart TVs
    private val candidateDlnaPorts = listOf(8088, 7676, 49152, 2869, 52235, 8080)

    override suspend fun runDiagnostics(config: ConnectionConfig): DiagnosticResult {
        if (config.isDemoMode) return DiagnosticResult.simulated(name)

        val portChecks = mutableListOf<PortCheck>()
        var foundOpenPort: Int? = null

        for (port in candidateDlnaPorts) {
            val check = NetworkUtils.probeTcpPort(
                host = config.host,
                port = port,
                serviceName = "DLNA/UPnP Port $port",
                timeoutMs = (config.timeoutMs / 2).coerceAtLeast(1000)
            )
            portChecks.add(check)
            if (check.isOpen && foundOpenPort == null) {
                foundOpenPort = port
            }
        }

        val status = if (foundOpenPort != null) DiagnosticStatus.RESPONDING else DiagnosticStatus.REJECTED
        val headline = if (foundOpenPort != null) {
            "DLNA DMR port $foundOpenPort is OPEN on ${config.host}"
        } else {
            "No standard DLNA listening ports responded on ${config.host}"
        }

        val summary = if (foundOpenPort != null) {
            "A potential DLNA DMR control endpoint was detected on port $foundOpenPort. " +
                    "This matches your observation that XCast connected to Digital Media Renderer. " +
                    "Media volume and playback commands may be accepted, but system navigation is unsupported by design."
        } else {
            "Ports checked (${candidateDlnaPorts.joinToString()}) were refused or timed out. " +
                    "Ensure the TV is powered on, Wi-Fi is connected, and 'Digital Media Renderer' is enabled in TV settings."
        }

        return DiagnosticResult(
            status = status,
            headline = headline,
            summary = summary,
            portChecks = portChecks,
            recommendation = if (foundOpenPort != null)
                "Test Volume Up/Down and Mute in the Remote tab. Note that navigation buttons are intentionally disabled."
            else
                "Verify TV IP address and ensure TV is awake on 2.4 GHz Wi-Fi.",
            isSimulated = false
        )
    }

    override suspend fun sendCommand(command: RemoteCommand, config: ConnectionConfig): CommandResult {
        if (config.isDemoMode) return CommandResult.simulated(command)

        if (!supportedCommands.contains(command)) {
            return CommandResult.unsupported(name, command)
        }

        val port = config.customPort ?: 8088
        val (serviceUrn, actionName, soapBody) = when (command) {
            RemoteCommand.VOLUME_UP -> Triple(
                "urn:schemas-upnp-org:service:RenderingControl:1",
                "SetVolume",
                "<u:SetVolume xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\"><InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>25</DesiredVolume></u:SetVolume>"
            )
            RemoteCommand.VOLUME_DOWN -> Triple(
                "urn:schemas-upnp-org:service:RenderingControl:1",
                "SetVolume",
                "<u:SetVolume xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\"><InstanceID>0</InstanceID><Channel>Master</Channel><DesiredVolume>15</DesiredVolume></u:SetVolume>"
            )
            RemoteCommand.MUTE -> Triple(
                "urn:schemas-upnp-org:service:RenderingControl:1",
                "SetMute",
                "<u:SetMute xmlns:u=\"urn:schemas-upnp-org:service:RenderingControl:1\"><InstanceID>0</InstanceID><Channel>Master</Channel><DesiredMute>1</DesiredMute></u:SetMute>"
            )
            RemoteCommand.PLAY -> Triple(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "Play",
                "<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID><Speed>1</Speed></u:Play>"
            )
            RemoteCommand.PAUSE -> Triple(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "Pause",
                "<u:Pause xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID></u:Pause>"
            )
            RemoteCommand.STOP -> Triple(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "Stop",
                "<u:Stop xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\"><InstanceID>0</InstanceID></u:Stop>"
            )
            else -> return CommandResult.unsupported(name, command)
        }

        val envelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
            <s:Body>$soapBody</s:Body>
            </s:Envelope>
        """.trimIndent()

        val endpoint = "http://${config.host}:$port/upnp/control/RenderingControl1"
        val headers = mapOf(
            "Content-Type" to "text/xml; charset=\"utf-8\"",
            "SOAPAction" to "\"$serviceUrn#$actionName\""
        )

        val response = NetworkUtils.executeHttpRequest(
            urlString = endpoint,
            method = "POST",
            headers = headers,
            body = envelope,
            timeoutMs = config.timeoutMs
        )

        return if (response.isSuccess) {
            CommandResult(
                status = ResultStatus.NETWORK_RESPONSE,
                summary = "DLNA SOAP $actionName acknowledged (HTTP ${response.statusCode})",
                details = "Endpoint $endpoint responded in ${response.durationMs}ms. Check TV audio/screen for reaction.",
                isSimulated = false,
                latencyMs = response.durationMs
            )
        } else {
            CommandResult(
                status = if (response.statusCode == -1) ResultStatus.REJECTED else ResultStatus.ERROR,
                summary = "DLNA $actionName failed (HTTP ${response.statusCode})",
                details = "Target: $endpoint. Response/Error: ${response.responseBody}. Verify port and DMR status.",
                isSimulated = false,
                latencyMs = response.durationMs
            )
        }
    }
}
