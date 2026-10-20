package com.example.royalremotelab.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalremotelab.data.LabRepository
import com.example.royalremotelab.network.NetworkUtils
import kotlinx.coroutines.launch

@Composable
fun ConnectionScreen(
    repository: LabRepository,
    modifier: Modifier = Modifier
) {
    val config by repository.connectionConfig.collectAsState()
    val isBusy by repository.isBusy.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var hostInput by remember(config.host) { mutableStateOf(config.host) }
    var timeoutInput by remember(config.timeoutMs) { mutableStateOf(config.timeoutMs.toString()) }
    var reachabilityResult by remember { mutableStateOf<String?>(null) }
    var isCheckingReachability by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val validationError = config.validate()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode & Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (config.isDemoMode)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "OFFLINE DEMO MODE",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Test UI and remote controls safely without Wi-Fi or TV.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.isDemoMode,
                        onCheckedChange = { enabled ->
                            repository.setDemoMode(enabled)
                            reachabilityResult = null
                        }
                    )
                }
            }
        }

        // TV Host & Network Settings
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TARGET SMART TV NETWORK SETTINGS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = hostInput,
                    onValueChange = { newValue ->
                        hostInput = newValue
                        val updated = config.copy(host = newValue)
                        repository.updateConfig(updated)
                    },
                    label = { Text("TV IP Address or Hostname") },
                    placeholder = { Text("e.g. 192.168.1.38") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = validationError != null,
                    supportingText = {
                        if (validationError != null) {
                            Text(validationError, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Current IP of your Royal smart TV on 2.4 GHz Wi-Fi")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = timeoutInput,
                    onValueChange = { newValue ->
                        timeoutInput = newValue
                        val parsed = newValue.toIntOrNull()
                        if (parsed != null && parsed in 500..30000) {
                            repository.updateConfig(config.copy(timeoutMs = parsed))
                        }
                    },
                    label = { Text("Network Timeout (milliseconds)") },
                    placeholder = { Text("3000") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Standard timeout: 3000ms (3 seconds)") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (config.isDemoMode) {
                            reachabilityResult = "✅ [SIMULATED] TV host virtual reachability confirmed (Demo Mode)."
                            return@Button
                        }
                        isCheckingReachability = true
                        reachabilityResult = null
                        coroutineScope.launch {
                            val probe = NetworkUtils.probeTcpPort(
                                host = config.host,
                                port = 8088,
                                serviceName = "DLNA DMR Reachability",
                                timeoutMs = config.timeoutMs
                            )
                            isCheckingReachability = false
                            reachabilityResult = if (probe.isOpen) {
                                "✅ Connected to ${config.host} on DLNA port 8088 in ${probe.latencyMs}ms."
                            } else {
                                val altProbe = NetworkUtils.probeTcpPort(
                                    host = config.host,
                                    port = 80,
                                    serviceName = "Web Port 80",
                                    timeoutMs = config.timeoutMs
                                )
                                if (altProbe.isOpen) {
                                    "✅ Connected to ${config.host} on port 80 in ${altProbe.latencyMs}ms."
                                } else {
                                    "⚠️ Host ${config.host} did not accept connections on ports 8088 or 80 (${probe.details}). " +
                                            "Ensure TV is awake on 2.4 GHz Wi-Fi."
                                }
                            }
                        }
                    },
                    enabled = !isBusy && !isCheckingReachability && config.isValid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isCheckingReachability) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pinging TV Socket...")
                    } else {
                        Text("📡 Test TV Host Reachability")
                    }
                }

                reachabilityResult?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Setup Instructions Checklist
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "LOCAL WI-FI CHECKLIST",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                ChecklistItem("1", "Phone and TV must be connected to the SAME 2.4 GHz Wi-Fi network.")
                ChecklistItem("2", "TV must be powered ON (many smart TVs turn off Wi-Fi in standby).")
                ChecklistItem("3", "Verify TV IP in TV Settings -> Network & Internet (currently 192.168.1.38).")
                ChecklistItem("4", "Ensure 'Digital Media Renderer' is ENABLED in TV Settings.")
            }
        }

        // Known TV Specifications Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DISCOVERED TV PROFILE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                TvSpecRow("Brand / Model", "Royal RTV65D8100 (65\" QLED)")
                TvSpecRow("Discovery Name", "Smart TV (DLNA DMR)")
                TvSpecRow("Software Build", "v8-nt671la-lf1v680.000")
                TvSpecRow("UI Version", "u_1.0.129.46 0906")
                TvSpecRow("Chassis Platform", "Novatek NT671LA (Linux 4.9.118+)")
                TvSpecRow("Observed Features", "DLNA DMR, T-Link CEC, Wi-Fi 2.4G")
                TvSpecRow("Bellon App Test", "YouTube launch (DIAL OK), Port 56789 (REFUSED)")
            }
        }
    }
}

@Composable
fun ChecklistItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TvSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
