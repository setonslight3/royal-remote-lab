package com.example.royalremotelab.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.royalremotelab.model.ResultStatus
import com.example.royalremotelab.model.TestLogEntry
import com.example.royalremotelab.model.UserFeedback

@Composable
fun LogsScreen(
    repository: LabRepository,
    modifier: Modifier = Modifier
) {
    val logs by repository.logs.collectAsState()
    val context = LocalContext.current

    var showExportDialog by remember { mutableStateOf(false) }
    var includeIpInExport by remember { mutableStateOf(false) } // Default false per Section 10!
    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "LOGS & OBSERVATION METRICS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                val totalTests = logs.size
                val reactedCount = logs.count { it.userFeedback == UserFeedback.VISIBLE_REACTION }
                val noReactCount = logs.count { it.userFeedback == UserFeedback.NO_REACTION }
                val unconfirmedCount = logs.count { it.userFeedback == UserFeedback.UNCONFIRMED }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricChip("Total Tests", totalTests.toString(), MaterialTheme.colorScheme.primary)
                    MetricChip("TV Reacted", reactedCount.toString(), Color(0xFF2E7D32))
                    MetricChip("No Reaction", noReactCount.toString(), Color(0xFFC62828))
                    MetricChip("Unconfirmed", unconfirmedCount.toString(), Color(0xFFEF6C00))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = logs.isNotEmpty()
                    ) {
                        Text("📋 Export Report")
                    }
                    OutlinedButton(
                        onClick = { showClearConfirm = true },
                        enabled = logs.isNotEmpty()
                    ) {
                        Text("Clear")
                    }
                }
            }
        }

        // Honest Observation Notice
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ℹ️ Note: This app cannot see your TV. Mark whether the TV visibly responded after each command.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(10.dp)
            )
        }

        // Test Logs List
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No test logs yet.\nSend commands from the Remote tab or run diagnostics in Protocol Lab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { entry ->
                    LogEntryCard(
                        entry = entry,
                        onUpdateFeedback = { feedback ->
                            repository.updateLogFeedback(entry.id, feedback)
                        }
                    )
                }
            }
        }
    }

    // Export Report Dialog
    if (showExportDialog) {
        val reportText = repository.exportReport(includeIp = includeIpInExport)
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Protocol Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Report ready to copy. By default, the TV IP is scrubbed for privacy.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include TV IP address in export:")
                        Switch(
                            checked = includeIpInExport,
                            onCheckedChange = { includeIpInExport = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Royal Remote Lab Report", reportText)
                        clipboard.setPrimaryClip(clip)
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

    // Clear Logs Confirmation Dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear All Logs?") },
            text = { Text("This will remove all recorded tests and TV reaction confirmations.") },
            confirmButton = {
                Button(
                    onClick = {
                        repository.clearLogs()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MetricChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LogEntryCard(
    entry: TestLogEntry,
    onUpdateFeedback: (UserFeedback) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${entry.formattedTime} • ${entry.actionName}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (entry.result.status) {
                        ResultStatus.SUCCESS, ResultStatus.NETWORK_RESPONSE -> Color(0xFF2E7D32)
                        ResultStatus.REJECTED -> Color(0xFFC62828)
                        ResultStatus.TIMED_OUT -> Color(0xFFEF6C00)
                        ResultStatus.UNSUPPORTED -> Color(0xFF546E7A)
                        ResultStatus.ERROR -> Color(0xFFB71C1C)
                    }
                ) {
                    Text(
                        text = entry.result.status.label,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Protocol: ${entry.protocolName} | Target: ${entry.targetHost}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = entry.result.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(6.dp))

            // User Reaction Prompt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Physical Reaction: ${entry.userFeedback.badge} ${entry.userFeedback.label}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalIconButton(
                        onClick = { onUpdateFeedback(UserFeedback.VISIBLE_REACTION) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("✅", fontSize = 12.sp)
                    }
                    FilledTonalIconButton(
                        onClick = { onUpdateFeedback(UserFeedback.NO_REACTION) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("❌", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
