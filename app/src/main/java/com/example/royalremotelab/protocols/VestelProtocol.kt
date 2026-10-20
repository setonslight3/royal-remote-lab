package com.example.royalremotelab.protocols

import com.example.royalremotelab.model.CommandResult
import com.example.royalremotelab.model.ConnectionConfig
import com.example.royalremotelab.model.DiagnosticResult
import com.example.royalremotelab.model.DiagnosticStatus
import com.example.royalremotelab.model.RemoteCommand
import com.example.royalremotelab.model.ResultStatus
import com.example.royalremotelab.network.HttpErrorType
import com.example.royalremotelab.network.NetworkUtils
import java.nio.charset.Charset

data class VestelHttpExchange(
    val endpointUrl: String,
    val requestHeaders: Map<String, String>,
    val requestBody: String,
    val httpStatusCode: Int,
    val responseHeaders: Map<String, List<String>>,
    val responseBodyPreview: String,
    val durationMs: Long,
    val stage: String,
    val exceptionDetail: String?
)

/**
 * Task A: Correct Vestel Smart Center Protocol Implementation.
 *
 * Communicates via HTTP POST to http://<host>:56789/apps/SmartCenter
 * with header "application_name: vestel smart center",
 * charset ISO-8859-1, and XML key payload: <?xml version='1.0' ?><remote><key code='...'/></remote>
 */
class VestelProtocol : TvProtocol {
    override val id: String = "vestel_smartcenter"
    override val name: String = "Vestel Smart Center (HTTP 56789)"
    override val category: String = "HTTP / SmartCenter REST"
    override val description: String =
        "Communicates with Vestel SmartCenter service on TCP port 56789 via HTTP POST /apps/SmartCenter."
    override val supportedCommands: Set<RemoteCommand> = setOf(
        RemoteCommand.VOLUME_UP,
        RemoteCommand.VOLUME_DOWN,
        RemoteCommand.MUTE,
        RemoteCommand.UP,
        RemoteCommand.DOWN,
        RemoteCommand.LEFT,
        RemoteCommand.RIGHT,
        RemoteCommand.OK,
        RemoteCommand.BACK,
        RemoteCommand.HOME,
        RemoteCommand.MENU,
        RemoteCommand.INPUT_SOURCE
    )
    override val caveats: String =
        "DISCLAIMER: An HTTP 200 response confirms only server receipt. " +
                "The published key-codes are an investigative hypothesis. Look at TV to confirm physical reaction."
    override val documentationReference: String =
        "Vestel SmartCenter HTTP API: POST http://<ip>:56789/apps/SmartCenter (application_name: vestel smart center)"

    private val defaultPort = 56789
    private val isoCharset = Charset.forName("ISO-8859-1")

    // Retain latest exchange for deep diagnostic inspection
    var latestExchange: VestelHttpExchange? = null
        private set

    override suspend fun runDiagnostics(config: ConnectionConfig): DiagnosticResult {
        if (config.isDemoMode) return DiagnosticResult.simulated(name)

        val port = config.customPort ?: defaultPort
        val host = config.host

        // Step 1: Probe TCP socket connect
        val tcpCheck = NetworkUtils.probeTcpPort(
            host = host,
            port = port,
            serviceName = "Vestel Port $port TCP Socket",
            timeoutMs = config.timeoutMs
        )

        if (!tcpCheck.isOpen) {
            return DiagnosticResult(
                status = DiagnosticStatus.REJECTED,
                headline = "Port $port TCP Connection Refused on $host",
                summary = "TCP socket connection was refused (${tcpCheck.details}). " +
                        "Vestel service is not running, TV is asleep, or port is closed.",
                portChecks = listOf(tcpCheck),
                recommendation = "Verify TV IP address and ensure TV is awake.",
                isSimulated = false
            )
        }

        // Step 2: Test HTTP endpoint /apps/SmartCenter with read-only ping (Volume Up hypothesis test)
        val endpoint = "http://$host:$port/apps/SmartCenter"
        val exchange = executeVestelHttpPost(
            host = host,
            port = port,
            keyCode = 1016, // Vol+ test code
            timeoutMs = config.timeoutMs
        )
        latestExchange = exchange

        val status = if (exchange.httpStatusCode in 200..299) DiagnosticStatus.RESPONDING else DiagnosticStatus.EXPERIMENTAL
        val headline = if (exchange.httpStatusCode in 200..299) {
            "Port $port OPEN & /apps/SmartCenter responded HTTP ${exchange.httpStatusCode}!"
        } else {
            "Port $port TCP OPEN, but HTTP returned status ${exchange.httpStatusCode}"
        }

        val summary = "Stage: ${exchange.stage} in ${exchange.durationMs}ms. " +
                "Endpoint: $endpoint | Headers: ${exchange.responseHeaders.keys.joinToString()}. " +
                "Body: ${if (exchange.responseBodyPreview.isBlank()) "[Empty response]" else exchange.responseBodyPreview}. " +
                "NOTE: HTTP response received does NOT prove the TV screen/volume reacted."

        return DiagnosticResult(
            status = status,
            headline = headline,
            summary = summary,
            portChecks = listOf(tcpCheck),
            recommendation = "Test Volume and Navigation buttons in Remote tab and observe physical TV.",
            isSimulated = false
        )
    }

    override suspend fun sendCommand(command: RemoteCommand, config: ConnectionConfig): CommandResult {
        if (config.isDemoMode) return CommandResult.simulated(command)

        if (!supportedCommands.contains(command)) {
            return CommandResult.unsupported(name, command)
        }

        val port = config.customPort ?: defaultPort
        val keyCode = mapCommandToVestelKeyCode(command)
        val exchange = executeVestelHttpPost(
            host = config.host,
            port = port,
            keyCode = keyCode,
            timeoutMs = config.timeoutMs
        )
        latestExchange = exchange

        return when {
            exchange.httpStatusCode in 200..299 -> {
                CommandResult(
                    status = ResultStatus.NETWORK_RESPONSE,
                    summary = "HTTP ${exchange.httpStatusCode} OK: ${command.label} (code $keyCode) accepted by TV",
                    details = "Stage: ${exchange.stage} in ${exchange.durationMs}ms. Response: '${exchange.responseBodyPreview}'. " +
                            "Waiting for user physical observation: Did the TV visibly or audibly react?",
                    isSimulated = false,
                    latencyMs = exchange.durationMs
                )
            }
            exchange.httpStatusCode > 0 -> {
                CommandResult(
                    status = ResultStatus.ERROR,
                    summary = "HTTP ${exchange.httpStatusCode} returned for ${command.label} (code $keyCode)",
                    details = "Stage: ${exchange.stage}. Response: ${exchange.responseBodyPreview}",
                    isSimulated = false,
                    latencyMs = exchange.durationMs
                )
            }
            exchange.exceptionDetail?.contains("refused", ignoreCase = true) == true -> {
                CommandResult(
                    status = ResultStatus.REJECTED,
                    summary = "Connection Refused on ${config.host}:$port",
                    details = "TCP socket connection was actively refused. Vestel service not responding.",
                    isSimulated = false,
                    latencyMs = exchange.durationMs
                )
            }
            exchange.exceptionDetail?.contains("time", ignoreCase = true) == true -> {
                CommandResult(
                    status = ResultStatus.TIMED_OUT,
                    summary = "Timed out on ${config.host}:$port after ${config.timeoutMs}ms",
                    details = "TV did not acknowledge HTTP POST within timeout.",
                    isSimulated = false,
                    latencyMs = exchange.durationMs
                )
            }
            else -> {
                CommandResult(
                    status = ResultStatus.ERROR,
                    summary = "Failed to deliver ${command.label}: ${exchange.exceptionDetail}",
                    details = "Stage: ${exchange.stage}",
                    isSimulated = false,
                    latencyMs = exchange.durationMs
                )
            }
        }
    }

    /**
     * Executes the exact HTTP POST specification for Vestel Smart Center.
     */
    suspend fun executeVestelHttpPost(
        host: String,
        port: Int,
        keyCode: Int,
        timeoutMs: Int
    ): VestelHttpExchange {
        val endpoint = "http://$host:$port/apps/SmartCenter"
        val headers = mapOf(
            "application_name" to "vestel smart center",
            "Content-Type" to "text/plain; charset=ISO-8859-1"
        )
        val body = "<?xml version='1.0' ?><remote><key code='$keyCode'/></remote>"

        val httpResponse = NetworkUtils.executeHttpRequest(
            urlString = endpoint,
            method = "POST",
            headers = headers,
            body = body,
            timeoutMs = timeoutMs,
            charset = isoCharset
        )

        val stage = when {
            httpResponse.statusCode in 200..299 -> "HTTP Status Successful (${httpResponse.statusCode} OK)"
            httpResponse.statusCode > 0 -> "HTTP Response Received (${httpResponse.statusCode})"
            httpResponse.errorType == HttpErrorType.CONNECTION_REFUSED -> "TCP Connection Refused"
            httpResponse.errorType == HttpErrorType.TIMED_OUT -> "TCP Connection / Read Timed Out"
            else -> "Network Error: ${httpResponse.rawException}"
        }

        return VestelHttpExchange(
            endpointUrl = endpoint,
            requestHeaders = headers,
            requestBody = body,
            httpStatusCode = httpResponse.statusCode,
            responseHeaders = httpResponse.headers,
            responseBodyPreview = httpResponse.responseBody.take(1024),
            durationMs = httpResponse.durationMs,
            stage = stage,
            exceptionDetail = httpResponse.rawException
        )
    }

    fun mapCommandToVestelKeyCode(command: RemoteCommand): Int {
        return when (command) {
            RemoteCommand.VOLUME_UP -> 1016
            RemoteCommand.VOLUME_DOWN -> 1017
            RemoteCommand.MUTE -> 1009
            RemoteCommand.UP -> 1012
            RemoteCommand.DOWN -> 1013
            RemoteCommand.LEFT -> 1014
            RemoteCommand.RIGHT -> 1015
            RemoteCommand.OK -> 1053
            RemoteCommand.BACK -> 1022
            RemoteCommand.HOME -> 1046
            RemoteCommand.MENU -> 1048
            RemoteCommand.INPUT_SOURCE -> 1056
            else -> 1053
        }
    }
}
