package com.example.royalremotelab.protocols

import com.example.royalremotelab.model.CommandResult
import com.example.royalremotelab.model.ConnectionConfig
import com.example.royalremotelab.model.DiagnosticResult
import com.example.royalremotelab.model.DiagnosticStatus
import com.example.royalremotelab.model.RemoteCommand
import com.example.royalremotelab.model.ResultStatus
import com.example.royalremotelab.network.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Candidate B: Vestel Smart Center Protocol Adapter.
 *
 * Grounded in investigative clue:
 * - Bellon Universal TV Remote probed TCP 56789 and reported REFUSED (vestel_virtual_remote_off).
 * - This protocol provides bounded diagnostics on port 56789 and documented XML key commands.
 */
class VestelProtocol : TvProtocol {
    override val id: String = "vestel_smartcenter"
    override val name: String = "Vestel Smart Center (TCP 56789)"
    override val category: String = "Proprietary Socket"
    override val description: String =
        "Tests the Vestel Smart Center TCP remote protocol on port 56789 (reported as REFUSED in diagnostic clue)."
    override val supportedCommands: Set<RemoteCommand> = setOf(
        RemoteCommand.UP,
        RemoteCommand.DOWN,
        RemoteCommand.LEFT,
        RemoteCommand.RIGHT,
        RemoteCommand.OK,
        RemoteCommand.BACK,
        RemoteCommand.HOME,
        RemoteCommand.MENU,
        RemoteCommand.INPUT_SOURCE,
        RemoteCommand.VOLUME_UP,
        RemoteCommand.VOLUME_DOWN,
        RemoteCommand.MUTE
    )
    override val caveats: String =
        "The TV previously returned 'outcome=REFUSED, why=vestel_virtual_remote_off'. " +
                "If port 56789 remains closed, the TV either uses different hardware or lacks this remote service."
    override val documentationReference: String =
        "Vestel Smart Center Reverse-Engineered Protocol (TCP 56789 XML/JSON keycode dispatch)"

    private val defaultPort = 56789

    override suspend fun runDiagnostics(config: ConnectionConfig): DiagnosticResult {
        if (config.isDemoMode) return DiagnosticResult.simulated(name)

        val port = config.customPort ?: defaultPort
        val check = NetworkUtils.probeTcpPort(
            host = config.host,
            port = port,
            serviceName = "Vestel Smart Center Port $port",
            timeoutMs = config.timeoutMs
        )

        return if (check.isOpen) {
            DiagnosticResult(
                status = DiagnosticStatus.RESPONDING,
                headline = "Port $port ACCEPTED connection on ${config.host}!",
                summary = "TCP port $port is actively listening. The TV may support Vestel Smart Center protocol commands.",
                portChecks = listOf(check),
                recommendation = "Test navigation buttons in Remote tab to verify whether TV accepts key payloads.",
                isSimulated = false
            )
        } else {
            DiagnosticResult(
                status = DiagnosticStatus.REJECTED,
                headline = "Port $port was REFUSED on ${config.host}",
                summary = "The TV actively refused connection on port $port (${check.details}). " +
                        "This confirms the Bellon report: the TV is either not a Vestel chassis (e.g. Novatek NT671LA) " +
                        "or the Virtual Remote service is not enabled in firmware.",
                portChecks = listOf(check),
                recommendation = "Do not force retries. Check candidate DLNA or Novatek DIAL instead.",
                isSimulated = false
            )
        }
    }

    override suspend fun sendCommand(command: RemoteCommand, config: ConnectionConfig): CommandResult {
        if (config.isDemoMode) return CommandResult.simulated(command)

        if (!supportedCommands.contains(command)) {
            return CommandResult.unsupported(name, command)
        }

        val port = config.customPort ?: defaultPort
        val vestelKeyCode = mapCommandToKeyCode(command)

        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(config.host, port), config.timeoutMs)
                socket.soTimeout = config.timeoutMs

                val writer = OutputStreamWriter(socket.getOutputStream(), "UTF-8")
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))

                // Documented Vestel key command format
                val payload = "<?xml version=\"1.0\" ?><remote><key code=\"$vestelKeyCode\"/></remote>\r\n"
                writer.write(payload)
                writer.flush()

                // Read socket response if TV echoes or acknowledges
                val responseLine = if (reader.ready()) reader.readLine() else "No payload echo (socket kept open)"
                val duration = System.currentTimeMillis() - startTime

                CommandResult(
                    status = ResultStatus.NETWORK_RESPONSE,
                    summary = "Vestel key $vestelKeyCode sent successfully",
                    details = "Payload delivered to ${config.host}:$port. Response: $responseLine. Did TV react?",
                    isSimulated = false,
                    latencyMs = duration
                )
            } catch (e: ConnectException) {
                val duration = System.currentTimeMillis() - startTime
                CommandResult(
                    status = ResultStatus.REJECTED,
                    summary = "Connection Refused on port $port",
                    details = "Failed to connect to ${config.host}:$port: ${e.message}. " +
                            "Vestel Virtual Remote is closed or not supported on this TV.",
                    isSimulated = false,
                    latencyMs = duration
                )
            } catch (e: SocketTimeoutException) {
                val duration = System.currentTimeMillis() - startTime
                CommandResult(
                    status = ResultStatus.TIMED_OUT,
                    summary = "Timed out on port $port",
                    details = "Host ${config.host}:$port did not respond within ${config.timeoutMs}ms.",
                    isSimulated = false,
                    latencyMs = duration
                )
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                CommandResult(
                    status = ResultStatus.ERROR,
                    summary = "Socket error on ${config.host}:$port",
                    details = e.message ?: e.javaClass.simpleName,
                    isSimulated = false,
                    latencyMs = duration
                )
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {}
            }
        }
    }

    private fun mapCommandToKeyCode(command: RemoteCommand): Int {
        return when (command) {
            RemoteCommand.UP -> 1012
            RemoteCommand.DOWN -> 1013
            RemoteCommand.LEFT -> 1014
            RemoteCommand.RIGHT -> 1015
            RemoteCommand.OK -> 1053
            RemoteCommand.BACK -> 1022
            RemoteCommand.HOME -> 1046
            RemoteCommand.MENU -> 1048
            RemoteCommand.VOLUME_UP -> 1016
            RemoteCommand.VOLUME_DOWN -> 1017
            RemoteCommand.MUTE -> 1009
            RemoteCommand.INPUT_SOURCE -> 1056
            else -> 1053
        }
    }
}
