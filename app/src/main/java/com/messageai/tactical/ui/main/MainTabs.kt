/**
 * MessageAI – Main tabs container (currently single tab).
 *
 * In the MVP, this simply displays the chat list. Post-MVP, this will expand
 * to include multiple tabs for chats, contacts, settings, etc.
 */
package com.messageai.tactical.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

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
            }
        }
    ) { padding ->
        when (tab) {
            0 -> ChatListScreen(onOpenChat = onOpenChat, onLogout = onLogout, onCreateChat = onCreateChat)
            1 -> MissionBoardScreen(chatId = "global")
        }
    }
}
