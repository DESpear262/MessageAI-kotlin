package com.messageai.tactical.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.items
import com.google.firebase.auth.FirebaseAuth
import com.messageai.tactical.data.remote.MessageRepository
import com.messageai.tactical.data.remote.MessageService
import com.messageai.tactical.data.remote.model.MessageDoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@Composable
fun ChatScreen(chatId: String) {
    val vm: ChatViewModel = hiltViewModel()
    val messages = vm.messages(chatId).collectAsLazyPagingItems()

    var input by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        LazyColumn(reverseLayout = true, modifier = Modifier.weight(1f)) {
            items(messages) { msg ->
                if (msg != null) {
                    Text(text = msg.text ?: "[image]", modifier = Modifier.padding(4.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val text = input.text.trim()
                if (text.isNotEmpty()) {
                    scope.launch { vm.send(chatId, text) }
                    input = TextFieldValue("")
                }
            }) { Text("Send") }
        }
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: MessageRepository,
    private val svc: MessageService,
    private val auth: FirebaseAuth
) : androidx.lifecycle.ViewModel() {
    fun messages(chatId: String) = repo.messages(chatId)

    suspend fun send(chatId: String, text: String) {
        val me = auth.currentUser ?: return
        val id = UUID.randomUUID().toString()
        val doc = MessageDoc(
            id = id,
            chatId = chatId,
            senderId = me.uid,
            text = text,
            clientTimestamp = System.currentTimeMillis(),
            status = "SENDING",
            localOnly = false
        )
        svc.sendMessage(doc)
    }
}
