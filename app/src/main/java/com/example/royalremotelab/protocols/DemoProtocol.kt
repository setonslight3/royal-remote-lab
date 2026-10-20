package com.example.royalremotelab.protocols

import com.example.royalremotelab.model.CommandResult
import com.example.royalremotelab.model.ConnectionConfig
import com.example.royalremotelab.model.DiagnosticResult
import com.example.royalremotelab.model.RemoteCommand
import kotlinx.coroutines.delay

/**
 * Offline simulation protocol for UI verification, demonstration, and offline lab evaluation.
 * Does not emit network packets.
 */
class DemoProtocol : TvProtocol {
    override val id: String = "demo_mode"
    override val name: String = "Demo Mode (Offline Simulation)"
    override val category: String = "Simulation"
    override val description: String =
        "Simulates all TV remote and diagnostic interactions locally without sending packets over Wi-Fi."
    override val supportedCommands: Set<RemoteCommand> = RemoteCommand.values().toSet()
    override val caveats: String =
        "WARNING: For interface demonstration only. Simulated successes DO NOT indicate physical TV response."
    override val documentationReference: String = "Internal UI Mock & Protocol Simulator"

    override suspend fun runDiagnostics(config: ConnectionConfig): DiagnosticResult {
        delay(250) // Brief delay to mimic latency
        return DiagnosticResult.simulated(name)
    }

    override suspend fun sendCommand(command: RemoteCommand, config: ConnectionConfig): CommandResult {
        delay(120) // Brief delay to mimic network latency
        return CommandResult.simulated(command)
    }
}
