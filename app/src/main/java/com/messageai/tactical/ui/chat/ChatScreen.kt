package com.messageai.tactical.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.firebase.auth.FirebaseAuth
import com.messageai.tactical.data.remote.MessageRepository
import com.messageai.tactical.data.remote.MessageService
import com.messageai.tactical.data.remote.model.MessageDoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit) {
    val vm: ChatViewModel = hiltViewModel()
    val messages: LazyPagingItems<com.messageai.tactical.data.db.MessageEntity> = vm.messages(chatId).collectAsLazyPagingItems()

    var input by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            LazyColumn(state = listState, reverseLayout = true, modifier = Modifier.weight(1f)) {
                items(count = messages.itemCount) { index ->
                    val msg = messages[index]
                    if (msg != null) {
                        val isMine = msg.senderId == vm.myUid
                        val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(bubbleColor)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .widthIn(min = 40.dp, max = 280.dp)
                            ) {
                                CompositionLocalProvider(LocalContentColor provides contentColor) {
                                    Text(text = msg.text ?: "[image]")
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: MessageRepository,
    private val svc: MessageService,
    private val auth: FirebaseAuth
) : androidx.lifecycle.ViewModel() {
    val myUid: String? get() = auth.currentUser?.uid

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
