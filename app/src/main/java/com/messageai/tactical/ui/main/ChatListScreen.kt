package com.messageai.tactical.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.messageai.tactical.data.db.ChatEntity
import com.messageai.tactical.data.remote.ChatService
import com.messageai.tactical.data.remote.PresenceService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit, onLogout: () -> Unit) {
    val vm: ChatListViewModel = hiltViewModel()
    val chats by vm.chats.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.startChatSubscription(scope) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = { TextButton(onClick = onLogout) { Text("Logout") } }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    try {
                        vm.startChat(query, onOpenChat) { error = it }
                        error = null
                    } catch (e: Exception) {
                        error = e.message ?: "Something went wrong starting the chat"
                    }
                }
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
            LazyColumn { items(chats) { chat -> ChatRow(vm, chat, onClick = { onOpenChat(chat.id) }) } }
        }
    }
}

@Composable
private fun PresenceDot(isOnline: Boolean) {
    val color = if (isOnline) Color(0xFF2ECC71) else Color(0xFFB0B0B0)
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(MaterialTheme.shapes.small)
            .background(color)
    )
}

@Composable
private fun ChatRow(vm: ChatListViewModel, chat: ChatEntity, onClick: () -> Unit) {
    val myUid = vm.meUid ?: ""
    val otherUid = remember(chat.participants, myUid) {
        // participants is stored as JSON array string
        val list = try { kotlinx.serialization.json.Json.decodeFromString<List<String>>(chat.participants) } catch (_: Exception) { emptyList() }
        list.firstOrNull { it != myUid } ?: myUid
    }
    val online by vm.userOnline(otherUid).collectAsState(initial = false)

    ListItem(
        leadingContent = { PresenceDot(online) },
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
    private val chatDao: com.messageai.tactical.data.db.ChatDao,
    private val presence: PresenceService
) : androidx.lifecycle.ViewModel() {
    val meUid: String? get() = auth.currentUser?.uid
    val chats = chatDao.getChats()

    fun userOnline(uid: String): kotlinx.coroutines.flow.Flow<Boolean> = presence.isUserOnline(uid)

    fun startChatSubscription(scope: kotlinx.coroutines.CoroutineScope) { chatService.subscribeMyChats(scope) }

    suspend fun startChat(input: String, onOpenChat: (String) -> Unit, onError: (String) -> Unit) {
        val me = auth.currentUser ?: return onError("Not signed in")
        val myName = me.displayName ?: me.email ?: "Me"
        val normalized = input.trim()
        if (normalized.isEmpty()) return onError("Enter email or screen name")

        val other = lookupUser(normalized) ?: return onError("We couldn't find that user")
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
