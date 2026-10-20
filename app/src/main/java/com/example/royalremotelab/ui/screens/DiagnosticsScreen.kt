package com.example.royalremotelab.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalremotelab.data.LabRepository
import com.example.royalremotelab.model.DiagnosticConfidence
import com.example.royalremotelab.model.DiscoveredUpnpDevice
import com.example.royalremotelab.model.PortProbeRecord
import com.example.royalremotelab.model.ResultStatus
import com.example.royalremotelab.network.Port9080AuditSummary
import com.example.royalremotelab.network.Port9080PathResult

@Composable
fun DiagnosticsScreen(
    repository: LabRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by repository.connectionConfig.collectAsState()
    val reachability by repository.reachabilityResult.collectAsState()
    val isReachabilityRunning by repository.isReachabilityRunning.collectAsState()
    val port9080Summary by repository.port9080Summary.collectAsState()
    val isPort9080Running by repository.isPort9080Running.collectAsState()
    val devices by repository.discoveredDevices.collectAsState()
    val isDiscoveryRunning by repository.isDiscoveryRunning.collectAsState()
    val portRecords by repository.portAuditRecords.collectAsState()
    val isPortAuditRunning by repository.isPortAuditRunning.collectAsState()
    val consoleLogs by repository.diagnosticLogs.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var includeIpInExport by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "SAFE NETWORK INVESTIGATION LAB",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Investigates target TV at ${config.host}. Empirically tests Vestel HTTP (Port 56789), Port 9080 HTTP 200 service, and SSDP UPnP endpoints without guessing arbitrary payloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        // --- 1. Host Reachability Audit ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "1. HOST REACHABILITY AUDIT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Evaluates whether host ${config.host} responds to ICMP echo or socket connections.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { repository.runHostReachabilityAudit() },
                    enabled = !isReachabilityRunning && config.isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isReachabilityRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auditing Reachability...")
                    } else {
                        Text("📡 Audit Host Reachability")
                    }
                }

                reachability?.let { r ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("ICMP Echo Ping:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (r.isIcmpReachable) "RESPONDED (${r.icmpLatencyMs}ms)" else "NO RESPONSE / DROPPED",
                                    color = if (r.isIcmpReachable) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("TCP Socket Reachable:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (r.openFallbackPort != null) "PORT ${r.openFallbackPort} OPEN (${r.fallbackLatencyMs}ms)" else "CLOSED / REFUSED",
                                    color = if (r.openFallbackPort != null) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Technical Analysis:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = r.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // --- 2. Task B: Port 9080 Investigation & DIAL Audit ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. TASK B: PORT 9080 INVESTIGATION (HTTP 200)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Port 9080 was discovered returning HTTP 200. Probes GET /, checks response headers for advertised Application-URL, and performs read-only app state query.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { repository.runPort9080Investigation() },
                    enabled = !isPort9080Running && config.isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isPort9080Running) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probing Port 9080 Endpoints...")
                    } else {
                        Text("🔬 Investigate Port 9080 (GET / & DIAL Audit)")
                    }
                }

                port9080Summary?.let { p90 ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Port9080SummaryCard(summary = p90)
                }
            }
        }

        // --- 3. SSDP / UPnP Multicast Discovery ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "3. SSDP / UPNP MULTICAST DISCOVERY",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dispatches standard M-SEARCH queries over UDP 239.255.255.250:1900 to discover the TV's advertised device description URL.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { repository.runSsdpDiscovery(context) },
                    enabled = !isDiscoveryRunning && config.isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isDiscoveryRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Listening for M-SEARCH Responses (4s)...")
                    } else {
                        Text("🔍 Run SSDP Discovery (UDP 1900)")
                    }
                }

                if (devices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Discovered Endpoints (${devices.size}):",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    devices.forEach { dev ->
                        DiscoveredDeviceCard(device = dev)
                    }
                }
            }
        }

        // --- 4. Evidence-Backed Targeted Port Audit ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "4. TARGETED PORT AUDIT (EVIDENCE-BACKED)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Probes only candidate ports with documented justifications (Port 56789, Port 9080, DIAL 8008, dynamic UPnP 49152/49153, 7676, 52235, Google TV 6466).",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { repository.runEvidenceBackedPortAudit() },
                    enabled = !isPortAuditRunning && config.isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isPortAuditRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probing Documented Ports...")
                    } else {
                        Text("🛡️ Run Evidence-Backed Port Audit")
                    }
                }

                if (portRecords.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    portRecords.forEach { record ->
                        PortRecordRow(record = record)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // --- 5. Live Diagnostic Console & Export ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "5. ACTIVITY CONSOLE & EXPORT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { showExportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📋 Export Report", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (consoleLogs.isEmpty()) {
                    Text(
                        text = "No diagnostic events yet. Run reachability, Port 9080 audit, or SSDP discovery above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            consoleLogs.take(15).forEach { line ->
                                Text(
                                    text = line,
                                    fontSize = 11.sp,
                                    color = Color(0xFF81C784),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Export Dialog
    if (showExportDialog) {
        val report = repository.exportReport(includeIp = includeIpInExport)
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Diagnostics Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Report compiled from all diagnostic tests. IP is scrubbed by default for privacy.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include TV IP address:")
                        Switch(checked = includeIpInExport, onCheckedChange = { includeIpInExport = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostics Report", report))
                        Toast.makeText(context, "Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    }
                ) {
                    Text("Copy to Clipboard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun Port9080SummaryCard(summary: Port9080AuditSummary) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Root: GET http://${summary.host}:9080/",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (summary.rootResult.statusCode in 200..299) Color(0xFF2E7D32) else Color(0xFFC62828)
                ) {
                    Text(
                        text = "HTTP ${summary.rootResult.statusCode} (${summary.rootResult.durationMs}ms)",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Verdict: ${summary.technicalVerdict}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (summary.advertisedApplicationUrl != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1B3A2B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🎯 Advertised Application-URL: ${summary.advertisedApplicationUrl}",
                        fontSize = 11.sp,
                        color = Color(0xFFA5D6A7),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            if (summary.youtubeQueryStatus != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "YouTube State Query: ${summary.youtubeQueryStatus}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (summary.rootResult.bodyPreview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Response Preview: '${summary.rootResult.bodyPreview.take(180)}...'",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (summary.probeResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Labelled Path Probes:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                summary.probeResults.forEach { pr ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${pr.label} (${pr.path}):", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (pr.statusCode > 0) "HTTP ${pr.statusCode} (${pr.durationMs}ms)" else "Failed",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (pr.statusCode in 200..299) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveredDeviceCard(device: DiscoveredUpnpDevice) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = device.friendlyName ?: "Discovered Smart TV Device",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Manufacturer: ${device.manufacturer ?: "Unknown"} | Model: ${device.modelName ?: "Unknown"} (${device.modelNumber ?: ""})",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Location URL: ${device.locationUrl}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (device.services.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Discovered Services (${device.services.size}):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
                device.services.forEach { service ->
                    Text(
                        text = "• ${service.serviceType.substringAfterLast(':')} (Control: ${service.controlUrl})",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF263238),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ Do NOT assume this media renderer supports remote navigation. UPnP DLNA defines volume/playback rendering, not system D-Pad keys.",
                    color = Color(0xFFFFB74D),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}

@Composable
fun PortRecordRow(record: PortProbeRecord) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Port ${record.port} • ${record.serviceName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (record.status) {
                        ResultStatus.SUCCESS -> Color(0xFF2E7D32)
                        ResultStatus.REJECTED -> Color(0xFFC62828)
                        ResultStatus.TIMED_OUT -> Color(0xFFEF6C00)
                        else -> Color(0xFF546E7A)
                    }
                ) {
                    Text(
                        text = if (record.status == ResultStatus.SUCCESS) "OPEN (${record.latencyMs}ms)" else record.status.label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Reason: ${record.documentedJustification}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Confidence: ${record.confidence.label} | ${record.rawDetails}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
