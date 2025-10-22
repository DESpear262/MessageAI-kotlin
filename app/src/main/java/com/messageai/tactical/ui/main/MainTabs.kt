/**
 * MessageAI – Main tabs container (currently single tab).
 *
 * In the MVP, this simply displays the chat list. Post-MVP, this will expand
 * to include multiple tabs for chats, contacts, settings, etc.
 */
package com.messageai.tactical.ui.main

import androidx.compose.runtime.Composable

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
    ChatListScreen(onOpenChat = onOpenChat, onLogout = onLogout, onCreateChat = onCreateChat)
}
