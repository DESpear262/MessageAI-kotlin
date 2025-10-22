package com.messageai.tactical.ui.main

import androidx.compose.runtime.Composable

@Composable
fun MainTabs(onLogout: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    // For now, show chat list as main content; tab scaffold can be added later
    ChatListScreen(onOpenChat = onOpenChat)
}
