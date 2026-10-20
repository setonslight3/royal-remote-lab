package com.example.royalremotelab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalremotelab.data.LabRepository
import com.example.royalremotelab.model.DiagnosticResult
import com.example.royalremotelab.model.DiagnosticStatus
import com.example.royalremotelab.protocols.ProtocolRegistry
import com.example.royalremotelab.protocols.TvProtocol

@Composable
fun ProtocolLabScreen(
    repository: LabRepository,
    modifier: Modifier = Modifier
) {
    val selectedProtocol by repository.selectedProtocol.collectAsState()
    val diagnosticResult by repository.diagnosticResult.collectAsState()
    val isBusy by repository.isBusy.collectAsState()
    val config by repository.connectionConfig.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Critical Honest Investigation Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "⚠️ PROTOCOL INVESTIGATION PRINCIPLE",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB74D),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "An open network port alone must NEVER be treated as proof that remote commands work. " +
                            "Honest protocol diagnosis requires matching socket responses with verified physical TV behavior.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Diagnostic Action Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DIAGNOSTIC PROBE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Runs safe, bounded read-only connectivity checks for: ${selectedProtocol.name}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { repository.runDiagnostics() },
                    enabled = !isBusy && config.isValid,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Probing Endpoints...")
                    } else {
                        Text("▶ Run Safe Diagnostics for ${selectedProtocol.name}")
                    }
                }
            }
        }

        // Diagnostic Result Display
        diagnosticResult?.let { result ->
            DiagnosticCard(result = result)
        }

        // Candidate Protocol Selector
        Text(
            text = "AVAILABLE PROTOCOL CANDIDATES",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        ProtocolRegistry.allProtocols.forEach { protocol ->
            val isSelected = protocol.id == selectedProtocol.id
            ProtocolCard(
                protocol = protocol,
                isSelected = isSelected,
                onSelect = { repository.selectProtocol(protocol.id) }
            )
        }
    }
}

@Composable
fun DiagnosticCard(result: DiagnosticResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (result.status) {
                DiagnosticStatus.RESPONDING -> Color(0xFF1B3A2B)
                DiagnosticStatus.REJECTED -> Color(0xFF3E2222)
                DiagnosticStatus.TIMED_OUT -> Color(0xFF3E2E1E)
                DiagnosticStatus.UNSUPPORTED -> Color(0xFF263238)
                DiagnosticStatus.NOT_TESTED, DiagnosticStatus.EXPERIMENTAL -> Color(0xFF2E2C38)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DIAGNOSTIC OUTCOME",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (result.status) {
                        DiagnosticStatus.RESPONDING -> Color(0xFF2E7D32)
                        DiagnosticStatus.REJECTED -> Color(0xFFC62828)
                        DiagnosticStatus.TIMED_OUT -> Color(0xFFEF6C00)
                        DiagnosticStatus.UNSUPPORTED -> Color(0xFF546E7A)
                        DiagnosticStatus.NOT_TESTED, DiagnosticStatus.EXPERIMENTAL -> Color(0xFF6A1B9A)
                    }
                ) {
                    Text(
                        text = result.status.label,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = result.headline,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = result.summary,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium
            )

            if (result.portChecks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Targeted Port Probes:",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(6.dp))
                result.portChecks.forEach { portCheck ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${portCheck.serviceName}:",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (portCheck.isOpen) "OPEN (${portCheck.latencyMs}ms)" else "CLOSED",
                            color = if (portCheck.isOpen) Color(0xFF81C784) else Color(0xFFE57373),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (result.recommendation.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Recommendation: ${result.recommendation}",
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ProtocolCard(
    protocol: TvProtocol,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
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
                        text = protocol.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Category: ${protocol.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                RadioButton(
                    selected = isSelected,
                    onClick = onSelect
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = protocol.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = "⚠️ ${protocol.caveats}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Supported Commands (${protocol.supportedCommands.size}): " +
                        if (protocol.supportedCommands.isEmpty()) "Diagnostic only"
                        else protocol.supportedCommands.joinToString(", ") { it.label },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
