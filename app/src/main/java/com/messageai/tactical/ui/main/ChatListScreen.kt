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
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.messageai.tactical.data.db.ChatEntity
import com.messageai.tactical.data.remote.ChatService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(onOpenChat: (String) -> Unit, onLogout: () -> Unit) {
    val vm: ChatListViewModel = hiltViewModel()
    val chats by vm.chats.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Map<String, String>>() }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.startChatSubscription(scope)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") },
                actions = {
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    try {
                        if (selected.isEmpty()) {
                            // Single input path (legacy): start direct from query
                            vm.startChat(query, onOpenChat) { error = it }
                        } else {
                            val ids = selected.map { it["uid"]!! }
                            val names = selected.associate { it["uid"]!! to (it["name"] ?: it["uid"]!!) }
                            val resultChatId = vm.createOrOpen(ids, names)
                            if (resultChatId != null) onOpenChat(resultChatId)
                        }
                        error = null
                    } catch (e: Exception) {
                        error = e.message ?: "Something went wrong starting the chat"
                    }
                }
            }) { Text("Create") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Email or screen name") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = {
                        scope.launch {
                            val user = vm.lookupUserPublic(query)
                            if (user != null) {
                                val map = mapOf(
                                    "uid" to (user["uid"] as String),
                                    "name" to ((user["displayName"] as? String) ?: (user["email"] as? String) ?: "")
                                )
                                if (selected.none { it["uid"] == map["uid"] }) selected.add(map)
                                query = ""
                                error = null
                            } else {
                                error = "No such user"
                            }
                        }
                    }) { Text("Add") }
                }
            )
            if (selected.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column {
                    selected.forEach { u ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(u["name"] ?: u["uid"]!!)
                            TextButton(onClick = { selected.remove(u) }) { Text("X") }
                        }
                    }
                }
            }
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

    fun startChatSubscription(scope: kotlinx.coroutines.CoroutineScope) {
        chatService.subscribeMyChats(scope)
    }

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

    suspend fun lookupUserPublic(q: String): Map<String, Any>? = lookupUser(q)

    private suspend fun lookupUser(q: String): Map<String, Any>? {
        val byEmail = firestore.collection("users").whereEqualTo("email", q).limit(1).get().await()
        if (!byEmail.isEmpty) return byEmail.documents.first().data
        val byName = firestore.collection("users").whereEqualTo("displayNameLower", q.lowercase()).limit(1).get().await()
        if (!byName.isEmpty) return byName.documents.first().data
        return null
    }

    suspend fun createOrOpen(memberUids: List<String>, memberNames: Map<String, String>): String? {
        val me = auth.currentUser ?: return null
        val unique = (memberUids + me.uid).distinct()
        return if (unique.size == 2) {
            // direct
            val other = unique.first { it != me.uid }
            chatService.ensureDirectChat(me.uid, other, me.displayName ?: me.email ?: "Me", otherName = other)
        } else {
            // group of 3 (MVP max per request)
            val name = selectedAutoName(unique, memberNames, me.uid)
            chatService.createGroupChat(name, unique, memberNames)
        }
    }

    private fun selectedAutoName(uids: List<String>, names: Map<String, String>, me: String): String {
        val others = uids.filter { it != me }
        val parts = others.map { names[it] ?: it }
        return parts.joinToString(", ")
    }
}
