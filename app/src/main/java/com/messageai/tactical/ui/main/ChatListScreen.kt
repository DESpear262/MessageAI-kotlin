package com.messageai.tactical.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.messageai.tactical.data.db.ChatEntity
import com.messageai.tactical.data.remote.ChatService
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit) {
    val vm: ChatListViewModel = hiltViewModel()
    val chats by vm.chats.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch { vm.startChat(query, onOpenChat) { error = it } }
            }) { Text("+") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Email or screen name") },
                modifier = Modifier.fillMaxWidth()
            )
            if (!error.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                items(chats) { chat ->
                    ChatRow(chat, onClick = { onOpenChat(chat.id) })
                }
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(chat.name ?: "Chat") },
        supportingContent = { Text(chat.lastMessage ?: "") },
        trailingContent = { Text(chat.lastMessageTime?.let { "" } ?: "") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val chatService: ChatService,
    private val chatDao: com.messageai.tactical.data.db.ChatDao
) : androidx.lifecycle.ViewModel() {
    val chats = chatDao.getChats()

    suspend fun startChat(input: String, onOpenChat: (String) -> Unit, onError: (String) -> Unit) {
        val me = auth.currentUser ?: return onError("Not signed in")
        val myName = me.displayName ?: me.email ?: "Me"
        val normalized = input.trim()
        if (normalized.isEmpty()) return onError("Enter email or screen name")

        val other = lookupUser(normalized) ?: run {
            return onError("We couldn't find that user")
        }
        val otherUid = other["uid"] as String
        val otherName = (other["displayName"] as? String) ?: (other["email"] as? String) ?: otherUid

        val chatId = chatService.ensureDirectChat(
            myUid = me.uid,
            otherUid = otherUid,
            myName = myName,
            otherName = otherName
        )
        onOpenChat(chatId)
    }

    private suspend fun lookupUser(q: String): Map<String, Any>? {
        val byEmail = firestore.collection("users").whereEqualTo("email", q).limit(1).get().await()
        if (!byEmail.isEmpty) return byEmail.documents.first().data
        val byName = firestore.collection("users").whereEqualTo("displayNameLower", q.lowercase()).limit(1).get().await()
        if (!byName.isEmpty) return byName.documents.first().data
        return null
    }
}
