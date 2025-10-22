package com.messageai.tactical.ui.main

import androidx.compose.runtime.Composable

@Composable
fun MainTabs(onLogout: () -> Unit, onOpenChat: (String) -> Unit = {}) {
    ChatListScreen(onOpenChat = onOpenChat, onLogout = onLogout)
}
