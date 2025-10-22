/**
 * MessageAI – New chat/group creation screen.
 *
 * Allows users to search for other users by email or display name and add them
 * to a participant list. If exactly one other user is selected, creates a 1:1
 * direct chat with a deterministic ID. If multiple users are selected, creates
 * a group chat with a random ID and auto-generated name.
 */
package com.messageai.tactical.ui.main

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
import com.messageai.tactical.data.remote.ChatService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChatScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val vm: CreateChatViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Map<String, String>>() }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("New chat") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    try {
                        val me = vm.meUid ?: return@launch
                        val ids = (selected.map { it["uid"]!! } + me).distinct()
                        val names = selected.associate { it["uid"]!! to (it["name"] ?: it["uid"]!!) }
                        val chatId = if (ids.size == 2) {
                            val other = ids.first { it != me }
                            vm.ensureDirect(other) ?: return@launch.also { error = "Failed to create chat" }
                        } else {
                            val name = vm.autoName(ids, names, me)
                            vm.createGroup(ids, names, name) ?: return@launch.also { error = "Failed to create group" }
                        }
                        onOpenChat(chatId)
                    } catch (e: Exception) {
                        error = e.message ?: "Failed to create chat"
                    }
                }
            }) { Text("Create") }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Email or screen name") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = {
                        scope.launch {
                            val user = vm.lookupUser(query)
                            if (user != null) {
                                val map = mapOf(
                                    "uid" to (user["uid"] as String),
                                    "name" to ((user["displayName"] as? String) ?: (user["email"] as? String) ?: "")
                                )
                                if (selected.none { it["uid"] == map["uid"] }) selected.add(map)
                                query = ""
                                error = null
                            } else error = "No such user"
                        }
                    }) { Text("Add") }
                }
            )
            if (!error.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn { items(selected) { u -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(u["name"] ?: u["uid"]!!); TextButton(onClick = { selected.remove(u) }) { Text("X") } } } }
        }
    }
}

@HiltViewModel
class CreateChatViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val chatService: ChatService
) : androidx.lifecycle.ViewModel() {
    val meUid: String? get() = auth.currentUser?.uid

    /**
     * Searches for a user by email or display name in Firestore.
     *
     * First attempts an exact match on the email field, then falls back to
     * case-insensitive display name search via the displayNameLower index.
     *
     * @param q The email or display name to search for
     * @return User document data if found, null otherwise
     */
    suspend fun lookupUser(q: String): Map<String, Any>? {
        val byEmail = firestore.collection("users").whereEqualTo("email", q).limit(1).get().await()
        if (!byEmail.isEmpty) return byEmail.documents.first().data
        val byName = firestore.collection("users").whereEqualTo("displayNameLower", q.lowercase()).limit(1).get().await()
        if (!byName.isEmpty) return byName.documents.first().data
        return null
    }

    /**
     * Creates or retrieves a 1:1 direct chat with another user.
     *
     * Uses deterministic chat IDs so the same two users always get the same chat.
     *
     * @param otherUid The UID of the other participant
     * @return The chat ID, or null if not authenticated
     */
    suspend fun ensureDirect(otherUid: String): String? {
        val me = auth.currentUser ?: return null
        val myName = me.displayName ?: me.email ?: "Me"
        // Try to resolve name for other
        val otherDoc = firestore.collection("users").document(otherUid).get().await()
        val otherName = (otherDoc.get("displayName") as? String) ?: (otherDoc.get("email") as? String) ?: otherUid
        return chatService.ensureDirectChat(me.uid, otherUid, myName, otherName)
    }

    /**
     * Creates a new group chat with the specified members.
     *
     * @param memberUids List of all participant UIDs (including creator)
     * @param memberNames Map of UID to display name for participants
     * @param name Optional group name
     * @return The new group chat ID, or null if not authenticated
     */
    suspend fun createGroup(memberUids: List<String>, memberNames: Map<String, String>, name: String?): String? {
        return chatService.createGroupChat(name, memberUids, memberNames)
    }

    /**
     * Generates an auto-name for a group by joining participant names.
     *
     * @param uids All participant UIDs
     * @param names Map of UID to display name
     * @param me Current user's UID (excluded from the name)
     * @return Comma-separated list of other participants' names
     */
    fun autoName(uids: List<String>, names: Map<String, String>, me: String): String = 
        uids.filter { it != me }.map { names[it] ?: it }.joinToString(", ")
}


