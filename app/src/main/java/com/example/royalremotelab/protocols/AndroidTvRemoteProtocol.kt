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
 * Candidate C1: Android TV / Google TV Remote v2 Protocol (Ports 6466 & 6467).
 *
 * Certified Android TV / Google TV devices run a remote daemon on TLS ports 6466 (Pairing)
 * and 6467 (Remote Protocol v2).
 * Investigating these ports helps verify whether the Royal TV's OS has an Android TV remote daemon.
 */
class AndroidTvRemoteProtocol : TvProtocol {
    override val id: String = "android_tv_remote"
    override val name: String = "Android TV Remote v2 (TLS 6466/6467)"
    override val category: String = "TLS Remote Protocol"
    override val description: String =
        "Checks for certified Google TV / Android TV Remote services on TLS ports 6466 (Pairing) and 6467 (Control)."
    override val supportedCommands: Set<RemoteCommand> = setOf(
        RemoteCommand.UP,
        RemoteCommand.DOWN,
        RemoteCommand.LEFT,
        RemoteCommand.RIGHT,
        RemoteCommand.OK,
        RemoteCommand.BACK,
        RemoteCommand.HOME,
        RemoteCommand.VOLUME_UP,
        RemoteCommand.VOLUME_DOWN,
        RemoteCommand.MUTE
    )
    override val caveats: String =
        "The Royal RTV65D8100 software string 'v8-nt671la-lf1v680.000' indicates a Novatek Linux/custom smart TV OS. " +
                "If ports 6466/6467 are closed, this TV does not run the Google TV remote daemon."
    override val documentationReference: String =
        "Google Android TV Remote Service v2 Specification (TLS Certificate Pairing & Protobuf)"

    override suspend fun runDiagnostics(config: ConnectionConfig): DiagnosticResult {
        if (config.isDemoMode) return DiagnosticResult.simulated(name)

        val pairingCheck = NetworkUtils.probeTcpPort(
            host = config.host,
            port = 6466,
            serviceName = "Android TV Pairing (TLS 6466)",
            timeoutMs = config.timeoutMs
        )
        val controlCheck = NetworkUtils.probeTcpPort(
            host = config.host,
            port = 6467,
            serviceName = "Android TV Control (TLS 6467)",
            timeoutMs = config.timeoutMs
        )

        val portChecks = listOf(pairingCheck, controlCheck)
        val anyOpen = pairingCheck.isOpen || controlCheck.isOpen

        return if (anyOpen) {
            DiagnosticResult(
                status = DiagnosticStatus.RESPONDING,
                headline = "Android TV Remote daemon detected on ${config.host}!",
                summary = "One or more Google TV remote ports responded. " +
                        "The TV may support TLS certificate pairing and Android TV Remote v2 commands.",
                portChecks = portChecks,
                recommendation = "An Android TV Remote pairing session would be required before sending keystrokes.",
                isSimulated = false
            )
        } else {
            DiagnosticResult(
                status = DiagnosticStatus.REJECTED,
                headline = "Android TV Remote ports 6466 & 6467 are CLOSED",
                summary = "Both pairing (6466) and control (6467) ports refused connection or timed out. " +
                        "This confirms the Royal RTV65D8100 is not running a certified Android TV remote service.",
                portChecks = portChecks,
                recommendation = "Focus investigation on DLNA media rendering or Novatek DIAL instead.",
                isSimulated = false
            )
        }
    }

    override suspend fun sendCommand(command: RemoteCommand, config: ConnectionConfig): CommandResult {
        if (config.isDemoMode) return CommandResult.simulated(command)

        return CommandResult(
            status = ResultStatus.REJECTED,
            summary = "Android TV Remote pairing required",
            details = "Android TV Remote v2 requires mutual TLS certificate exchange over port 6466 before commands can be dispatched.",
            isSimulated = false,
            latencyMs = 0L
        )
    }
}
