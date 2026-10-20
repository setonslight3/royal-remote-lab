package com.example.royalremotelab.data

import android.content.Context
import com.example.royalremotelab.model.CommandResult
import com.example.royalremotelab.model.ConnectionConfig
import com.example.royalremotelab.model.DiagnosticConfidence
import com.example.royalremotelab.model.DiagnosticResult
import com.example.royalremotelab.model.DiscoveredUpnpDevice
import com.example.royalremotelab.model.HostReachabilityResult
import com.example.royalremotelab.model.PortProbeRecord
import com.example.royalremotelab.model.RemoteCommand
import com.example.royalremotelab.model.ResultStatus
import com.example.royalremotelab.model.TestLogEntry
import com.example.royalremotelab.model.UserFeedback
import com.example.royalremotelab.network.DeviceDescriptionParser
import com.example.royalremotelab.network.HostReachabilityChecker
import com.example.royalremotelab.network.NetworkUtils
import com.example.royalremotelab.network.SsdpDiscoveryManager
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
 * test logs, asynchronous protocol diagnostics/commands, and safe network discovery.
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

    private val _statusNotice = MutableStateFlow<String?>("Ready. Select a protocol or run safe diagnostics.")
    val statusNotice: StateFlow<String?> = _statusNotice.asStateFlow()

    // --- Focused Network Diagnostics State ---
    private val _reachabilityResult = MutableStateFlow<HostReachabilityResult?>(null)
    val reachabilityResult: StateFlow<HostReachabilityResult?> = _reachabilityResult.asStateFlow()

    private val _isReachabilityRunning = MutableStateFlow(false)
    val isReachabilityRunning: StateFlow<Boolean> = _isReachabilityRunning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredUpnpDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredUpnpDevice>> = _discoveredDevices.asStateFlow()

    private val _isDiscoveryRunning = MutableStateFlow(false)
    val isDiscoveryRunning: StateFlow<Boolean> = _isDiscoveryRunning.asStateFlow()

    private val _portAuditRecords = MutableStateFlow<List<PortProbeRecord>>(emptyList())
    val portAuditRecords: StateFlow<List<PortProbeRecord>> = _portAuditRecords.asStateFlow()

    private val _isPortAuditRunning = MutableStateFlow(false)
    val isPortAuditRunning: StateFlow<Boolean> = _isPortAuditRunning.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<String>>(emptyList())
    val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

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

    fun logDiagnostic(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _diagnosticLogs.value = listOf("[$time] $message") + _diagnosticLogs.value.take(49)
    }

    /**
     * Executes safe host reachability audit (ICMP + TCP fallback with ping limitation explanation).
     */
    fun runHostReachabilityAudit() {
        if (_isReachabilityRunning.value) return
        val host = _connectionConfig.value.host
        _isReachabilityRunning.value = true
        logDiagnostic("Starting reachability audit for host: $host")

        scope.launch(Dispatchers.IO) {
            try {
                if (_connectionConfig.value.isDemoMode) {
                    val demoResult = HostReachabilityResult(
                        host = host,
                        isIcmpReachable = true,
                        icmpLatencyMs = 8L,
                        openFallbackPort = 8008,
                        fallbackLatencyMs = 12L,
                        explanation = "[SIMULATED] Reachability check simulated in Demo Mode."
                    )
                    _reachabilityResult.value = demoResult
                    logDiagnostic("Demo reachability completed.")
                } else {
                    val result = HostReachabilityChecker.auditHostReachability(
                        host = host,
                        timeoutMs = _connectionConfig.value.timeoutMs
                    )
                    _reachabilityResult.value = result
                    logDiagnostic("Reachability completed: ${result.explanation}")

                    appendLog(
                        TestLogEntry(
                            protocolId = "reachability_audit",
                            protocolName = "Host Reachability",
                            targetHost = host,
                            actionName = "ICMP & Socket Ping",
                            result = CommandResult(
                                status = if (result.isIcmpReachable || result.openFallbackPort != null)
                                    ResultStatus.NETWORK_RESPONSE else ResultStatus.REJECTED,
                                summary = if (result.openFallbackPort != null)
                                    "Host reachable on port ${result.openFallbackPort}"
                                else if (result.isIcmpReachable) "ICMP ping acknowledged"
                                else "Host unreachable on ping and tested ports",
                                details = result.explanation
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                logDiagnostic("Reachability error: ${e.message}")
            } finally {
                _isReachabilityRunning.value = false
            }
        }
    }

    /**
     * Runs bounded SSDP/UPnP M-SEARCH discovery on the local network.
     */
    fun runSsdpDiscovery(context: Context?) {
        if (_isDiscoveryRunning.value) return
        val host = _connectionConfig.value.host
        _isDiscoveryRunning.value = true
        logDiagnostic("Initiating bounded SSDP/UPnP M-SEARCH discovery (target: $host)...")

        scope.launch(Dispatchers.IO) {
            try {
                if (_connectionConfig.value.isDemoMode) {
                    val demoDevice = DiscoveredUpnpDevice(
                        locationUrl = "http://$host:8008/ssdp/device-desc.xml",
                        serverHeader = "Linux/4.9.118 UPnP/1.0 DLNADOC/1.50",
                        searchTarget = "urn:schemas-upnp-org:device:MediaRenderer:1",
                        usn = "uuid:royal-rtv65d8100-demo::upnp:rootdevice",
                        targetIp = host,
                        friendlyName = "Smart TV (Simulated)",
                        manufacturer = "Royal / Novatek",
                        modelName = "RTV65D8100",
                        modelNumber = "v8-nt671la-lf1v680.000",
                        modelDescription = "QLED Smart TV Media Renderer",
                        deviceType = "urn:schemas-upnp-org:device:MediaRenderer:1"
                    )
                    _discoveredDevices.value = listOf(demoDevice)
                    logDiagnostic("Demo SSDP discovery yielded simulated device.")
                } else {
                    val devices = SsdpDiscoveryManager.discoverDevices(
                        context = context,
                        filterHost = host,
                        timeoutMs = 4000L,
                        onLog = { msg -> logDiagnostic(msg) }
                    )

                    // Retrieve description XML for each discovered device safely
                    val hydratedDevices = devices.map { device ->
                        logDiagnostic("Retrieving XML description from ${device.locationUrl}...")
                        try {
                            val httpRes = NetworkUtils.executeHttpRequest(
                                urlString = device.locationUrl,
                                method = "GET",
                                timeoutMs = 2500
                            )
                            if (httpRes.isSuccess && httpRes.responseBody.isNotBlank()) {
                                logDiagnostic("Successfully parsed device description for ${device.locationUrl}")
                                DeviceDescriptionParser.parse(httpRes.responseBody, device.locationUrl, device)
                            } else {
                                logDiagnostic("GET ${device.locationUrl} returned status ${httpRes.statusCode}")
                                device
                            }
                        } catch (e: Exception) {
                            logDiagnostic("Failed to fetch ${device.locationUrl}: ${e.message}")
                            device
                        }
                    }

                    _discoveredDevices.value = hydratedDevices
                    logDiagnostic("SSDP Discovery finished. Found ${hydratedDevices.size} device endpoint(s).")

                    appendLog(
                        TestLogEntry(
                            protocolId = "ssdp_discovery",
                            protocolName = "SSDP / UPnP Discovery",
                            targetHost = host,
                            actionName = "M-SEARCH Multicast",
                            result = CommandResult(
                                status = if (hydratedDevices.isNotEmpty()) ResultStatus.SUCCESS else ResultStatus.REJECTED,
                                summary = "SSDP discovered ${hydratedDevices.size} device(s)",
                                details = hydratedDevices.joinToString { "${it.friendlyName ?: "Device"} @ ${it.locationUrl}" }
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                logDiagnostic("SSDP Discovery error: ${e.message}")
            } finally {
                _isDiscoveryRunning.value = false
            }
        }
    }

    /**
     * Runs targeted evidence-backed port audit against the configured host.
     */
    fun runEvidenceBackedPortAudit() {
        if (_isPortAuditRunning.value) return
        val host = _connectionConfig.value.host
        _isPortAuditRunning.value = true
        logDiagnostic("Starting evidence-backed port audit on $host...")

        scope.launch(Dispatchers.IO) {
            try {
                if (_connectionConfig.value.isDemoMode) {
                    val demoRecords = HostReachabilityChecker.EVIDENCE_BACKED_PORTS.map { def ->
                        PortProbeRecord(
                            port = def.port,
                            serviceName = def.serviceName,
                            documentedJustification = def.justification,
                            status = if (def.port == 8008 || def.port == 49152) ResultStatus.SUCCESS else ResultStatus.REJECTED,
                            latencyMs = 10L,
                            rawDetails = "[SIMULATED] Demo port probe",
                            confidence = if (def.port == 8008) DiagnosticConfidence.HIGH_CONFIDENCE_MATCH else DiagnosticConfidence.UNCONFIRMED
                        )
                    }
                    _portAuditRecords.value = demoRecords
                    logDiagnostic("Demo port audit completed.")
                } else {
                    val records = HostReachabilityChecker.runEvidenceBackedPortAudit(
                        host = host,
                        timeoutMs = 1800,
                        onProgress = { msg -> logDiagnostic(msg) }
                    )
                    _portAuditRecords.value = records
                    val openCount = records.count { it.status == ResultStatus.SUCCESS }
                    logDiagnostic("Port audit completed. $openCount of ${records.size} tested ports open.")

                    appendLog(
                        TestLogEntry(
                            protocolId = "port_audit",
                            protocolName = "Targeted Port Audit",
                            targetHost = host,
                            actionName = "Evidence-Backed Probe (${records.size} ports)",
                            result = CommandResult(
                                status = if (openCount > 0) ResultStatus.SUCCESS else ResultStatus.REJECTED,
                                summary = "$openCount port(s) open: ${records.filter { it.status == ResultStatus.SUCCESS }.joinToString { it.port.toString() }}",
                                details = records.joinToString("; ") { "P${it.port}:${it.status.name}" }
                            )
                        )
                    )
                }
            } catch (e: Exception) {
                logDiagnostic("Port audit error: ${e.message}")
            } finally {
                _isPortAuditRunning.value = false
            }
        }
    }

    fun clearDiagnostics() {
        _reachabilityResult.value = null
        _discoveredDevices.value = emptyList()
        _portAuditRecords.value = emptyList()
        _diagnosticLogs.value = emptyList()
        _statusNotice.value = "Network diagnostics cleared."
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

        // Include Network Diagnostics if available
        val reach = _reachabilityResult.value
        if (reach != null) {
            builder.appendLine("HOST REACHABILITY AUDIT:")
            builder.appendLine("- Host: ${if (includeIp) reach.host else "[IP_REDACTED]"}")
            builder.appendLine("- ICMP Reachable: ${reach.isIcmpReachable} (${reach.icmpLatencyMs}ms)")
            builder.appendLine("- Fallback Socket: ${reach.openFallbackPort?.let { "Port $it open (${reach.fallbackLatencyMs}ms)" } ?: "Closed"}")
            builder.appendLine("- Technical Explanation: ${reach.explanation}")
            builder.appendLine()
        }

        val devices = _discoveredDevices.value
        if (devices.isNotEmpty()) {
            builder.appendLine("SSDP / UPNP DISCOVERED DEVICES (${devices.size}):")
            devices.forEachIndexed { i, dev ->
                val devHost = if (includeIp) dev.targetIp else "[IP_REDACTED]"
                builder.appendLine("Device #${i + 1}:")
                builder.appendLine("  - Friendly Name: ${dev.friendlyName ?: "Unknown"}")
                builder.appendLine("  - Manufacturer: ${dev.manufacturer ?: "Unknown"}")
                builder.appendLine("  - Model Name: ${dev.modelName ?: "Unknown"}")
                builder.appendLine("  - Model Number: ${dev.modelNumber ?: "Unknown"}")
                builder.appendLine("  - Device Type: ${dev.deviceType ?: "Unknown"}")
                builder.appendLine("  - Location URL: ${if (includeIp) dev.locationUrl else dev.locationUrl.replace(Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b"""), "[IP_REDACTED]")}")
                builder.appendLine("  - Services Discovered (${dev.services.size}):")
                dev.services.forEach { s ->
                    builder.appendLine("      * ${s.serviceType} (ID: ${s.serviceId}) -> Control: ${if (includeIp) s.controlUrl else "[IP_REDACTED]"}")
                }
            }
            builder.appendLine()
        }

        val portProbes = _portAuditRecords.value
        if (portProbes.isNotEmpty()) {
            builder.appendLine("TARGETED EVIDENCE-BACKED PORT PROBES:")
            portProbes.forEach { p ->
                builder.appendLine("- Port ${p.port} (${p.serviceName}): Status=${p.status.name} (${p.latencyMs}ms) | Confidence=${p.confidence.label}")
                builder.appendLine("    Justification: ${p.documentedJustification}")
                builder.appendLine("    Details: ${p.rawDetails}")
            }
            builder.appendLine()
        }

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
        builder.appendLine("Network acknowledgments or open ports alone do not prove TV system control.")
        builder.appendLine("Only physical screen reaction confirms navigation functionality.")
        builder.appendLine("=======================================================")
        return builder.toString()
    }
}
