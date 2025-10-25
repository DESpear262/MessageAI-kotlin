/**
 * MessageAI – Main tabs container (currently single tab).
 *
 * In the MVP, this simply displays the chat list. Post-MVP, this will expand
 * to include multiple tabs for chats, contacts, settings, etc.
 */
package com.messageai.tactical.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.messageai.tactical.modules.reporting.ReportPreviewScreen
import com.messageai.tactical.modules.reporting.ReportShare
import androidx.compose.ui.platform.LocalContext

/**
 * Main screen container after authentication.
 *
 * Currently displays only the chat list, but structured to support
 * future tab navigation expansion.
 *
 * @param onLogout Callback to sign out and return to auth screen
 * @param onOpenChat Callback to navigate to a specific chat
 * @param onCreateChat Callback to navigate to the new chat creation screen
 */
@Composable
fun MainTabs(onLogout: () -> Unit, onOpenChat: (String) -> Unit = {}, onCreateChat: () -> Unit = {}) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        bottomBar = {
            if (tab != 2) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                        label = { Text("Chats") }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Icon(Icons.Default.ListAlt, contentDescription = "Missions") },
                        label = { Text("Missions") }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Icon(Icons.Default.SmartToy, contentDescription = "AI Buddy") },
                        label = { Text("AI Buddy") }
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { Icon(Icons.Default.Description, contentDescription = "Outputs") },
                        label = { Text("Outputs") }
                    )
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> {
                // Landing banner pointing to AI Buddy
                Column(modifier = Modifier.padding(padding)) {
                    AssistChipRow(onOpenAIBuddy = { tab = 2 })
                    ChatListScreen(onOpenChat = onOpenChat, onLogout = onLogout, onCreateChat = onCreateChat)
                }
            }
            1 -> MissionBoardScreen(chatId = "global")
            2 -> com.messageai.tactical.ui.main.aibuddy.AIBuddyScreen(onBackToChats = { tab = 0 })
            3 -> {
                val ctx = LocalContext.current
                var kind by remember { mutableStateOf<String?>(null) }
                Column(modifier = Modifier.padding(padding)) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { kind = "sitrep" }) { Text("SITREP") }
                        Button(onClick = { kind = "warnord" }) { Text("WARNORD") }
                        Button(onClick = { kind = "opord" }) { Text("OPORD") }
                        Button(onClick = { kind = "frago" }) { Text("FRAGO") }
                    }
                    if (kind == null) {
                        Text(
                            text = "Select a report type to preview.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    } else {
                        ReportPreviewScreen(
                            chatId = null,
                            kind = kind!!,
                            onShare = { md ->
                                val file = when (kind) {
                                    "warnord" -> "warnord_preview.md"
                                    "opord" -> "opord_preview.md"
                                    "frago" -> "frago_preview.md"
                                    else -> "sitrep_preview.md"
                                }
                                ReportShare.shareMarkdown(ctx, file, md)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistChipRow(onOpenAIBuddy: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "New: AI Buddy can summarize, plan, and auto-trigger actions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenAIBuddy) { Text("Open AI Buddy") }
        }
    }
}
