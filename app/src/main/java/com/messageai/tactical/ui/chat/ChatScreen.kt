package com.messageai.tactical.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.messageai.tactical.data.db.MessageEntity
import com.messageai.tactical.data.remote.Mapper
import com.messageai.tactical.data.remote.MessageRepository
import com.messageai.tactical.data.remote.MessageService
import com.messageai.tactical.data.remote.SendWorker
import com.messageai.tactical.data.remote.model.MessageDoc
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// Added for images and picking
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File
import coil.imageLoader
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit) {
    val vm: ChatViewModel = hiltViewModel()
    val refreshTrigger: Int by vm.refreshTrigger.collectAsState()
    
    // Recreate paging flow when trigger changes - this forces new PagingSource
    val messages: LazyPagingItems<com.messageai.tactical.data.db.MessageEntity> = remember(refreshTrigger) {
        android.util.Log.d("ChatScreen", "Creating new paging flow for trigger: $refreshTrigger")
        vm.messages(chatId)
    }.collectAsLazyPagingItems()
    
    val title by vm.chatTitle(chatId).collectAsState(initial = "Chat")

    var input by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Pending image flow (gallery or camera)
    var pendingImage by remember { mutableStateOf<File?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // Copy to cache for stability
            val cached = vm.cachePickedUri(context, uri)
            pendingImage = cached
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        if (success) {
            cameraUri?.let { uri ->
                val cached = vm.cachePickedUri(context, uri)
                pendingImage = cached
            }
        }
    }

    // Start listening for realtime updates
    LaunchedEffect(chatId) {
        vm.startListener(chatId, scope)
    }

    // Prefetch images for visible + next items
    val imageLoader = context.imageLoader
    LaunchedEffect(listState.firstVisibleItemIndex, messages.itemSnapshotList.items.size) {
        val start = listState.firstVisibleItemIndex
        val end = (start + 6).coerceAtMost(messages.itemSnapshotList.items.lastIndex)
        if (start in 0..end) {
            for (i in start..end) {
                val item = messages.itemSnapshotList.items.getOrNull(i)
                val url = item?.imageUrl
                if (!url.isNullOrEmpty()) {
                    val req = ImageRequest.Builder(context).data(url).build()
                    imageLoader.enqueue(req)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                        ) {
                            if (!isMine) {
                                // Placeholder avatar circle
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer)
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                                if (!isMine) {
                                    val senderName = vm.nameFor(msg.senderId)
                                    Text(senderName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(2.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(bubbleColor)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .widthIn(min = 60.dp, max = 280.dp)
                                ) {
                                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                                        if (!msg.imageUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(msg.imageUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Inside,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                                            )
                                        } else {
                                            Text(text = msg.text ?: "[image]")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Mark read for fully visible messages
            LaunchedEffect(listState, messages.itemCount) {
                snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                    .map { infos ->
                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                        infos.filter { it.offset >= 0 && (it.offset + it.size) <= viewportEnd }
                            .mapNotNull { vi -> messages.peek(vi.index)?.id }
                    }
                    .distinctUntilChanged()
                    .collectLatest { fullyVisibleIds ->
                        if (fullyVisibleIds.isNotEmpty()) vm.onFullyVisible(chatId, fullyVisibleIds)
                    }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Attach actions
                TextButton(onClick = { pickLauncher.launch("image/*") }) { Text("Gallery") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    val cacheDir = File(context.cacheDir, "images").apply { mkdirs() }
                    val file = File(cacheDir, "camera-${'$'}{UUID.randomUUID()}.jpg")
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                    cameraUri = uri
                    takePictureLauncher.launch(uri)
                }) { Text("Camera") }
                Spacer(Modifier.width(8.dp))
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
                        scope.launch { vm.send(chatId, text, context) }
                        input = TextFieldValue("")
                    }
                }) { Text("Send") }
            }
            if (pendingImage != null) {
                Spacer(Modifier.height(8.dp))
                Text("Image ready to send", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { pendingImage = null }) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val file = pendingImage ?: return@Button
                        scope.launch {
                            vm.sendImage(chatId, file, context)
                            pendingImage = null
                        }
                    }) { Text("Send Image") }
                }
            }
        }
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: MessageRepository,
    private val svc: MessageService,
    private val auth: FirebaseAuth,
    private val messageListener: com.messageai.tactical.data.remote.MessageListener,
    private val firestore: com.google.firebase.firestore.FirebaseFirestore,
    private val readUpdater: com.messageai.tactical.data.remote.ReadReceiptUpdater
) : androidx.lifecycle.ViewModel() {
    val myUid: String? get() = auth.currentUser?.uid

    private val _refreshTrigger = kotlinx.coroutines.flow.MutableStateFlow(0)
    val refreshTrigger = _refreshTrigger.asStateFlow()

    fun messages(chatId: String) = repo.messages(chatId)

    fun startListener(chatId: String, scope: kotlinx.coroutines.CoroutineScope) {
        messageListener.setOnDataChanged {
            android.util.Log.d("ChatViewModel", "Data changed, incrementing refresh trigger")
            _refreshTrigger.value++
        }
        messageListener.start(chatId, scope)

        // Also listen for participantDetails to populate name map
        chatDetailsReg?.remove()
        chatDetailsReg = firestore.collection(com.messageai.tactical.data.remote.FirestorePaths.CHATS)
            .document(chatId)
            .addSnapshotListener { doc, _ ->
                val map = doc?.get("participantDetails") as? Map<*, *> ?: return@addSnapshotListener
                val names = mutableMapOf<String, String>()
                map.forEach { (k, v) ->
                    val uid = k as? String ?: return@forEach
                    val name = (v as? Map<*, *>)?.get("name") as? String ?: uid
                    names[uid] = name
                }
                participantNames.clear()
                participantNames.putAll(names)
            }
    }

    override fun onCleared() {
        super.onCleared()
        messageListener.stop()
        chatDetailsReg?.remove()
    }

    fun chatTitle(chatId: String) = repo.db.chatDao().getChat(chatId).map { it?.name ?: "Chat" }

    // Cache of participant names for quick lookup
    private val participantNames = mutableStateMapOf<String, String>()
    private var chatDetailsReg: ListenerRegistration? = null
    fun nameFor(uid: String): String = participantNames[uid] ?: uid

    fun onFullyVisible(chatId: String, ids: List<String>) {
        readUpdater.markRead(chatId, ids, androidx.lifecycle.viewModelScope)
    }

    suspend fun send(chatId: String, text: String, context: android.content.Context) {
        val me = auth.currentUser ?: return
        val id = UUID.randomUUID().toString()
        val entity = MessageEntity(
            id = id,
            chatId = chatId,
            senderId = me.uid,
            text = text,
            imageUrl = null,
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            readBy = "[]",
            deliveredBy = "[]",
            synced = false,
            createdAt = System.currentTimeMillis()
        )
        // Optimistic local insert
        repo.db.messageDao().upsert(entity)
        // Enqueue background send
        SendWorker.enqueue(context, id, chatId, me.uid, text, entity.timestamp)
    }

    suspend fun sendImage(chatId: String, file: File, context: android.content.Context) {
        val me = auth.currentUser ?: return
        val id = UUID.randomUUID().toString()
        val entity = MessageEntity(
            id = id,
            chatId = chatId,
            senderId = me.uid,
            text = null,
            imageUrl = null,
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            readBy = "[]",
            deliveredBy = "[]",
            synced = false,
            createdAt = System.currentTimeMillis()
        )
        repo.db.messageDao().upsert(entity)
        // Create message doc as SENDING so recipients see placeholder
        SendWorker.enqueue(context, id, chatId, me.uid, null, entity.timestamp, imageLocalPath = file.absolutePath)
        // Upload image via dedicated worker which patches URL and status
        com.messageai.tactical.data.remote.ImageUploadWorker.enqueue(context, id, chatId, me.uid, file.absolutePath)
    }

    fun cachePickedUri(context: android.content.Context, uri: Uri): File {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open input stream")
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val out = File(dir, "picked-${'$'}{UUID.randomUUID()}.jpg")
        out.outputStream().use { output -> input.copyTo(output) }
        return out
    }
}
