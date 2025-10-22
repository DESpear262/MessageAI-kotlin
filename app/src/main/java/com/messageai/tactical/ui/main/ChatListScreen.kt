/**
 * MessageAI – Chat list screen UI and view model.
 *
 * Displays the user's active chats with presence indicators, unread badges,
 * and last message previews. Subscribes to real-time Firestore chat updates.
 */
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.messageai.tactical.data.db.ChatEntity
import com.messageai.tactical.data.remote.ChatService
import com.messageai.tactical.data.remote.PresenceService
import com.messageai.tactical.ui.components.PresenceDot
import com.messageai.tactical.util.ParticipantHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit, onLogout: () -> Unit, onCreateChat: () -> Unit) {
    val vm: ChatListViewModel = hiltViewModel()
    val chats by vm.chats.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.startChatSubscription(scope) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = { TextButton(onClick = onLogout) { Text("Logout") } }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onCreateChat) { Text("+") } }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(12.dp)) {
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
private fun ChatRow(vm: ChatListViewModel, chat: ChatEntity, onClick: () -> Unit) {
    val myUid = vm.meUid ?: ""
    val otherUid = remember(chat.participants, myUid) {
        ParticipantHelper.getOtherParticipant(chat.participants, myUid)
    }
    val online by vm.userOnline(otherUid).collectAsState(initial = false)

    ListItem(
        leadingContent = { PresenceDot(online) },
        headlineContent = { Text(chat.name ?: "Chat") },
        supportingContent = { Text(chat.lastMessage ?: "") },
        trailingContent = { 
            if (chat.unreadCount > 0) {
                Badge(
                    modifier = Modifier.size(24.dp),
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
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
}
