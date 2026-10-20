package com.example.royalremotelab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalremotelab.data.LabRepository
import com.example.royalremotelab.model.*

@Composable
fun RemoteScreen(
    repository: LabRepository,
    modifier: Modifier = Modifier
) {
    val config by repository.connectionConfig.collectAsState()
    val activeProtocol by repository.selectedProtocol.collectAsState()
    val isBusy by repository.isBusy.collectAsState()
    val latestResultPair by repository.latestResult.collectAsState()
    val logs by repository.logs.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Protocol & Target Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (config.isDemoMode)
                    MaterialTheme.colorScheme.tertiaryContainer
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (config.isDemoMode) "DEMO MODE (OFFLINE)" else "TARGET: ${config.host}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (config.isDemoMode)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Badge(
                        containerColor = if (config.isDemoMode) Color(0xFFE65100) else Color(0xFF1B5E20)
                    ) {
                        Text(
                            text = if (config.isDemoMode) "SIMULATED" else "LAN",
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Active Adapter: ${activeProtocol.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = activeProtocol.caveats,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Latest Command Result & Feedback Prompt
        latestResultPair?.let { (command, result) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (result.status) {
                        ResultStatus.SUCCESS, ResultStatus.NETWORK_RESPONSE -> Color(0xFF1E3A2F)
                        ResultStatus.REJECTED -> Color(0xFF3E2723)
                        ResultStatus.TIMED_OUT -> Color(0xFF3E2723)
                        ResultStatus.UNSUPPORTED -> Color(0xFF263238)
                        ResultStatus.ERROR -> Color(0xFF3E1F1F)
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last Action: ${command.label}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (result.status) {
                                ResultStatus.SUCCESS, ResultStatus.NETWORK_RESPONSE -> Color(0xFF2E7D32)
                                ResultStatus.REJECTED -> Color(0xFFC62828)
                                ResultStatus.TIMED_OUT -> Color(0xFFEF6C00)
                                ResultStatus.UNSUPPORTED -> Color(0xFF546E7A)
                                ResultStatus.ERROR -> Color(0xFFB71C1C)
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

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = result.summary,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (result.details.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.details,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Did the TV screen/sound physically react?",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { repository.updateLatestFeedback(UserFeedback.VISIBLE_REACTION) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Text("✅ TV Reacted", fontSize = 12.sp, color = Color.White)
                        }
                        Button(
                            onClick = { repository.updateLatestFeedback(UserFeedback.NO_REACTION) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                        ) {
                            Text("❌ No Reaction", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Remote Controls Section
        Text(
            text = "SYSTEM NAVIGATION",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // D-Pad Control
        DPadControl(
            supportedCommands = activeProtocol.supportedCommands,
            isBusy = isBusy,
            onCommand = { cmd -> repository.sendCommand(cmd) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // System Function Keys (Back, Home, Menu, Source)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RemoteButton(
                command = RemoteCommand.BACK,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.BACK),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.BACK) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
            RemoteButton(
                command = RemoteCommand.HOME,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.HOME),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.HOME) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
            RemoteButton(
                command = RemoteCommand.MENU,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.MENU),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.MENU) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
            RemoteButton(
                command = RemoteCommand.INPUT_SOURCE,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.INPUT_SOURCE),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.INPUT_SOURCE) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Audio & Volume Section
        Text(
            text = "AUDIO & VOLUME CONTROLS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RemoteButton(
                command = RemoteCommand.VOLUME_UP,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.VOLUME_UP),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.VOLUME_UP) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
            RemoteButton(
                command = RemoteCommand.MUTE,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.MUTE),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.MUTE) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
            RemoteButton(
                command = RemoteCommand.VOLUME_DOWN,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.VOLUME_DOWN),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.VOLUME_DOWN) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Media Playback Section
        Text(
            text = "MEDIA PLAYBACK (DLNA / DIAL)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RemoteButton(
                command = RemoteCommand.PLAY,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.PLAY),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.PLAY) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
            RemoteButton(
                command = RemoteCommand.PAUSE,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.PAUSE),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.PAUSE) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
            RemoteButton(
                command = RemoteCommand.STOP,
                supported = activeProtocol.supportedCommands.contains(RemoteCommand.STOP),
                isBusy = isBusy,
                onClick = { repository.sendCommand(RemoteCommand.STOP) },
                modifier = Modifier.weight(1f).padding(4.dp)
            )
        }
    }
}

@Composable
fun DPadControl(
    supportedCommands: Set<RemoteCommand>,
    isBusy: Boolean,
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val dpadSize = 240.dp
    val buttonSize = 64.dp

    Box(
        modifier = modifier
            .size(dpadSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // UP
        Button(
            onClick = { onCommand(RemoteCommand.UP) },
            enabled = !isBusy && supportedCommands.contains(RemoteCommand.UP),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(buttonSize),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text("▲", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // DOWN
        Button(
            onClick = { onCommand(RemoteCommand.DOWN) },
            enabled = !isBusy && supportedCommands.contains(RemoteCommand.DOWN),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .size(buttonSize),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text("▼", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // LEFT
        Button(
            onClick = { onCommand(RemoteCommand.LEFT) },
            enabled = !isBusy && supportedCommands.contains(RemoteCommand.LEFT),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 10.dp)
                .size(buttonSize),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text("◀", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // RIGHT
        Button(
            onClick = { onCommand(RemoteCommand.RIGHT) },
            enabled = !isBusy && supportedCommands.contains(RemoteCommand.RIGHT),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp)
                .size(buttonSize),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text("▶", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // CENTER OK
        Button(
            onClick = { onCommand(RemoteCommand.OK) },
            enabled = !isBusy && supportedCommands.contains(RemoteCommand.OK),
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("OK", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RemoteButton(
    command: RemoteCommand,
    supported: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = !isBusy && supported,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (supported)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = command.iconSymbol,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = command.label,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
