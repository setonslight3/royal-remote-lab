package com.example.royalremotelab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.royalremotelab.data.LabRepository
import com.example.royalremotelab.ui.screens.ConnectionScreen
import com.example.royalremotelab.ui.screens.LogsScreen
import com.example.royalremotelab.ui.screens.ProtocolLabScreen
import com.example.royalremotelab.ui.screens.RemoteScreen

enum class AppTab(val label: String, val iconText: String) {
    REMOTE("Remote", "📱"),
    PROTOCOL_LAB("Protocol Lab", "🔬"),
    CONNECTION("Connection", "📡"),
    LOGS("Logs", "📋")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    repository: LabRepository,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableStateOf(AppTab.REMOTE) }

    val config by repository.connectionConfig.collectAsState()
    val activeProtocol by repository.selectedProtocol.collectAsState()
    val statusNotice by repository.statusNotice.collectAsState()
    val logs by repository.logs.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Royal Remote Lab",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Smart TV Protocol Investigator",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (config.isDemoMode) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFE65100),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Text(
                                    text = "DEMO MODE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1B5E20),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Text(
                                    text = "LAN: ${config.host}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Quick Status Notice Bar
                statusNotice?.let { notice ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            maxLines = 1
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        icon = {
                            if (tab == AppTab.LOGS && logs.isNotEmpty()) {
                                BadgedBox(badge = {
                                    Badge { Text(logs.size.toString()) }
                                }) {
                                    Text(tab.iconText, fontSize = 18.sp)
                                }
                            } else {
                                Text(tab.iconText, fontSize = 18.sp)
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                AppTab.REMOTE -> RemoteScreen(repository = repository)
                AppTab.PROTOCOL_LAB -> ProtocolLabScreen(repository = repository)
                AppTab.CONNECTION -> ConnectionScreen(repository = repository)
                AppTab.LOGS -> LogsScreen(repository = repository)
            }
        }
    }
}
