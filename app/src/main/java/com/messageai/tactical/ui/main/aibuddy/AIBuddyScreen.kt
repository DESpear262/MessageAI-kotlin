package com.messageai.tactical.ui.main.aibuddy

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBuddyScreen(onBackToChats: () -> Unit) {
    val vm: AIBuddyViewModel = hiltViewModel()
    val chatId by vm.chatId.collectAsState()

    LaunchedEffect(Unit) { vm.ensureBuddyChatAndOnboard() }

    if (chatId != null) {
        // Reuse the standard 1:1 chat screen for full display & behavior
        com.messageai.tactical.ui.chat.ChatScreen(chatId = chatId!!, onBack = onBackToChats)
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text("AI Buddy") }, navigationIcon = { TextButton(onClick = onBackToChats) { Text("Back") } }) }) { padding ->
            androidx.compose.foundation.layout.Box(Modifier.padding(padding))
        }
    }
}

