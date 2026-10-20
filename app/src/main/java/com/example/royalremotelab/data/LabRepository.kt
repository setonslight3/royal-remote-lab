package com.example.royalremotelab.data

import com.example.royalremotelab.model.CommandResult
import com.example.royalremotelab.model.ConnectionConfig
import com.example.royalremotelab.model.DiagnosticResult
import com.example.royalremotelab.model.RemoteCommand
import com.example.royalremotelab.model.ResultStatus
import com.example.royalremotelab.model.TestLogEntry
import com.example.royalremotelab.model.UserFeedback
import com.example.royalremotelab.network.NetworkUtils
import com.example.royalremotelab.protocols.ProtocolRegistry
import com.example.royalremotelab.protocols.TvProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single source of truth managing TV connection configuration, active protocol,
 * test logs, and asynchronous protocol diagnostics/commands.
 */
class LabRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _connectionConfig = MutableStateFlow(ConnectionConfig())
    val connectionConfig: StateFlow<ConnectionConfig> = _connectionConfig.asStateFlow()

    private val _selectedProtocol = MutableStateFlow<TvProtocol>(ProtocolRegistry.dlnaProtocol)
    val selectedProtocol: StateFlow<TvProtocol> = _selectedProtocol.asStateFlow()

    private val _logs = MutableStateFlow<List<TestLogEntry>>(emptyList())
    val logs: StateFlow<List<TestLogEntry>> = _logs.asStateFlow()

    private val _latestResult = MutableStateFlow<Pair<RemoteCommand, CommandResult>?>(null)
    val latestResult: StateFlow<Pair<RemoteCommand, CommandResult>?> = _latestResult.asStateFlow()

    private val _diagnosticResult = MutableStateFlow<DiagnosticResult?>(null)
    val diagnosticResult: StateFlow<DiagnosticResult?> = _diagnosticResult.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusNotice = MutableStateFlow<String?>("Ready. Select a protocol or test TV reachability.")
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    fun updateConfig(config: ConnectionConfig) {
        _connectionConfig.value = config
        if (config.isDemoMode && _selectedProtocol.value.id != ProtocolRegistry.demoProtocol.id) {
            _selectedProtocol.value = ProtocolRegistry.demoProtocol
        } else if (!config.isDemoMode && _selectedProtocol.value.id == ProtocolRegistry.demoProtocol.id) {
            _selectedProtocol.value = ProtocolRegistry.dlnaProtocol
        }
    }

    fun setDemoMode(enabled: Boolean) {
        val updated = _connectionConfig.value.copy(isDemoMode = enabled)
        updateConfig(updated)
        _statusNotice.value = if (enabled) {
            "Demo Mode enabled (offline simulated behavior)."
        } else {
            "Real TV Mode enabled (targeting ${_connectionConfig.value.host})."
        }
    }

    fun selectProtocol(protocolId: String) {
        val protocol = ProtocolRegistry.getById(protocolId)
        _selectedProtocol.value = protocol
        _statusNotice.value = "Switched to ${protocol.name}."
    }

    fun runDiagnostics() {
        if (_isBusy.value) return
        val currentProtocol = _selectedProtocol.value
        val currentConfig = _connectionConfig.value

        _isBusy.value = true
        _statusNotice.value = "Running safe diagnostics for ${currentProtocol.name}..."

        scope.launch(Dispatchers.IO) {
            try {
                val result = currentProtocol.runDiagnostics(currentConfig)
                _diagnosticResult.value = result
                _statusNotice.value = "Diagnostics complete: ${result.headline}"

                // Add to test log
                val logEntry = TestLogEntry(
                    protocolId = currentProtocol.id,
                    protocolName = currentProtocol.name,
                    targetHost = currentConfig.host,
                    actionName = "Diagnostics Audit",
                    result = CommandResult(
                        status = if (result.status.name == "RESPONDING") ResultStatus.NETWORK_RESPONSE else ResultStatus.REJECTED,
                        summary = result.headline,
                        details = result.summary,
                        isSimulated = result.isSimulated
                    )
                )
                appendLog(logEntry)
            } catch (e: Exception) {
                _statusNotice.value = "Diagnostic error: ${e.message}"
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun sendCommand(command: RemoteCommand) {
        if (_isBusy.value) return
        val currentProtocol = _selectedProtocol.value
        val currentConfig = _connectionConfig.value

        _isBusy.value = true
        _statusNotice.value = "Sending ${command.label} via ${currentProtocol.name}..."

        scope.launch(Dispatchers.IO) {
            try {
                val result = currentProtocol.sendCommand(command, currentConfig)
                _latestResult.value = Pair(command, result)
                _statusNotice.value = "${command.label}: ${result.summary}"

                val logEntry = TestLogEntry(
                    protocolId = currentProtocol.id,
                    protocolName = currentProtocol.name,
                    targetHost = currentConfig.host,
                    actionName = command.label,
                    result = result
                )
                appendLog(logEntry)
            } catch (e: Exception) {
                val errResult = CommandResult(
                    status = ResultStatus.ERROR,
                    summary = "Unexpected failure: ${e.message}",
                    details = e.javaClass.simpleName,
                    isSimulated = currentConfig.isDemoMode
                )
                _latestResult.value = Pair(command, errResult)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun updateLogFeedback(entryId: String, feedback: UserFeedback, notes: String = "") {
        _logs.value = _logs.value.map { entry ->
            if (entry.id == entryId) {
                entry.copy(userFeedback = feedback, userNotes = if (notes.isNotBlank()) notes else entry.userNotes)
            } else {
                entry
            }
        }
    }

    fun updateLatestFeedback(feedback: UserFeedback) {
        val list = _logs.value
        if (list.isNotEmpty()) {
            val first = list.first()
            updateLogFeedback(first.id, feedback)
        }
    }

    private fun appendLog(entry: TestLogEntry) {
        // Prepend so newest is at the top
        _logs.value = listOf(entry) + _logs.value
    }

    fun clearLogs() {
        _logs.value = emptyList()
        _latestResult.value = null
        _statusNotice.value = "Test logs cleared."
    }

    /**
     * Generates a comprehensive exportable report.
     * When [includeIp] is false (the default), the TV IP address is scrubbed.
     */
    fun exportReport(includeIp: Boolean = false): String {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val hostDisplay = if (includeIp) _connectionConfig.value.host else "[IP_REDACTED]"
        val currentList = _logs.value

        val totalTests = currentList.size
        val confirmedReacted = currentList.count { it.userFeedback == UserFeedback.VISIBLE_REACTION }
        val confirmedNoReaction = currentList.count { it.userFeedback == UserFeedback.NO_REACTION }
        val unconfirmed = currentList.count { it.userFeedback == UserFeedback.UNCONFIRMED }

        val builder = StringBuilder()
        builder.appendLine("=======================================================")
        builder.appendLine("ROYAL REMOTE LAB - PROTOCOL INVESTIGATION REPORT")
        builder.appendLine("Date/Time: $now")
        builder.appendLine("TV Model: Royal RTV65D8100 (Novatek NT671LA / LF1V680)")
        builder.appendLine("Target Host: $hostDisplay")
        builder.appendLine("Active Protocol: ${_selectedProtocol.value.name}")
        builder.appendLine("Mode: ${if (_connectionConfig.value.isDemoMode) "DEMO / SIMULATED" else "PHYSICAL NETWORK"}")
        builder.appendLine("=======================================================")
        builder.appendLine()
        builder.appendLine("TEST SUMMARY METRICS:")
        builder.appendLine("- Total Commands/Probes Run: $totalTests")
        builder.appendLine("- Confirmed Physical TV Reactions: $confirmedReacted")
        builder.appendLine("- Confirmed NO Visual Reaction: $confirmedNoReaction")
        builder.appendLine("- Unconfirmed (Look at TV screen): $unconfirmed")
        builder.appendLine()
        builder.appendLine("DETAILED TEST LOG:")
        if (currentList.isEmpty()) {
            builder.appendLine("No test logs recorded yet.")
        } else {
            currentList.forEachIndexed { index, entry ->
                builder.appendLine("${index + 1}. ${entry.toExportString(includeIp)}")
            }
        }
        builder.appendLine()
        builder.appendLine("HONEST ASSESSMENT NOTICE:")
        builder.appendLine("Network acknowledgments alone do not prove TV system control.")
        builder.appendLine("Only physical screen reaction confirms navigation functionality.")
        builder.appendLine("=======================================================")
        return builder.toString()
    }
}
