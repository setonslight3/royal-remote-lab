package com.example.royalremotelab.protocols

import com.example.royalremotelab.model.CommandResult
import com.example.royalremotelab.model.ConnectionConfig
import com.example.royalremotelab.model.DiagnosticResult
import com.example.royalremotelab.model.RemoteCommand

/**
 * Common interface for all smart TV protocol adapters investigated in Royal Remote Lab.
 */
interface TvProtocol {
    val id: String
    val name: String
    val category: String
    val description: String
    val supportedCommands: Set<RemoteCommand>
    val caveats: String
    val documentationReference: String

    /**
     * Executes safe, bounded diagnostics to test endpoint availability and service status.
     */
    suspend fun runDiagnostics(config: ConnectionConfig): DiagnosticResult

    /**
     * Sends an individual remote control command to the target TV using this protocol adapter.
     */
    suspend fun sendCommand(command: RemoteCommand, config: ConnectionConfig): CommandResult
}
