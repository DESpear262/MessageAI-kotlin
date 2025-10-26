/**
 * MessageAI – Chat screen UI and view model.
 *
 * Displays a paginated message list with real-time updates, optimistic sending,
 * image attachments (gallery + camera), and presence indicators. Handles message
 * lifecycle including Room persistence, WorkManager queuing, and Firestore sync.
 */
package com.messageai.tactical.ui.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.catch
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.messageai.tactical.data.db.ChatDao
import com.messageai.tactical.data.media.ImageService
import com.messageai.tactical.data.remote.ImageUploadWorker
import com.messageai.tactical.data.remote.MessageListener
import com.messageai.tactical.data.remote.MessageRepository
import com.messageai.tactical.data.remote.MessageService
import com.messageai.tactical.data.remote.ReadReceiptUpdater
import com.messageai.tactical.data.remote.PresenceService
import com.messageai.tactical.data.remote.model.MessageDoc
import com.messageai.tactical.ui.components.PresenceDot
import com.messageai.tactical.util.CameraHelper
import com.messageai.tactical.util.ActiveChatTracker
import com.messageai.tactical.modules.geo.GeoService
import com.messageai.tactical.util.ParticipantHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import com.messageai.tactical.modules.reporting.ReportService
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatId: String, onBack: () -> Unit) {
    val vm: ChatViewModel = hiltViewModel()
    val messages: LazyPagingItems<com.messageai.tactical.data.db.MessageEntity> = vm.messages(chatId).collectAsLazyPagingItems()
    val title by vm.chatTitle(chatId).collectAsState(initial = "Chat")
    val otherUid by vm.otherParticipant(chatId).collectAsState(initial = null)
    val online by vm.userOnline(otherUid).collectAsState(initial = false)

    var input by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Photo picker for gallery
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedImageUri = it }
    }

    // Camera state
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    var shouldLaunchCamera by remember { mutableStateOf(false) }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { selectedImageUri = it }
        }
        shouldLaunchCamera = false
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            shouldLaunchCamera = true
        }
    }
    
    // Handle camera launch after permission granted
    LaunchedEffect(shouldLaunchCamera) {
        if (shouldLaunchCamera) {
            val (_, uri) = CameraHelper.createImageFile(context)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
            shouldLaunchCamera = false
        }
    }

    // Handle selected image
    LaunchedEffect(selectedImageUri) {
        selectedImageUri?.let { uri ->
            vm.sendImage(chatId, uri, context)
            selectedImageUri = null
            cameraImageUri = null
        }
    }

    LaunchedEffect(chatId) { 
        vm.startListener(chatId, scope)
        vm.markAsRead(chatId)
        vm.markAllAsRead(chatId, scope)
    }

    /*
     * BUG NOTE (temporary workaround):
     * Precise unread decrementor via visibility tracking is disabled for now due to
     * intermittent paging snapshot index issues reported in testing. We currently
     * clear unread count on chat open instead. Re-enable after fixing underlying bug.
     *
     * // LaunchedEffect(listState, vm.myUid) { ... }
     */
    DisposableEffect(chatId) {
        onDispose { vm.stopListener() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PresenceDot(online)
                        Spacer(Modifier.width(8.dp))
                        Text(title)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { }
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
                                    Column {
                                        if (msg.imageUrl != null) {
                                            AsyncImage(
                                                model = msg.imageUrl,
                                                contentDescription = "Sent image",
                                                modifier = Modifier
                                                    .heightIn(max = 200.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else if (msg.text.isNullOrBlank() && msg.status == "SENDING") {
                                            // Show loading indicator for pending image
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = contentColor,
                                                    strokeWidth = 2.dp
                                                )
                                                Text("Sending image...", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        if (!msg.text.isNullOrBlank()) {
                                            Text(text = msg.text)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Gallery button
                IconButton(onClick = { photoPicker.launch("image/*") }) {
                    Icon(Icons.Default.Image, contentDescription = "Gallery")
                }
                // Camera button
                IconButton(onClick = {
                    val permission = android.Manifest.permission.CAMERA
                    when {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context, permission
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                            // Permission already granted - create file and launch camera
                            val (_, uri) = CameraHelper.createImageFile(context)
                            cameraImageUri = uri
                            cameraLauncher.launch(uri)
                        }
                        else -> {
                            // Request permission
                            cameraPermissionLauncher.launch(permission)
                        }
                    }
                }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                }
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
        }
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: MessageRepository,
    private val svc: MessageService,
    private val auth: FirebaseAuth,
    private val chatDao: ChatDao,
    private val presence: PresenceService,
    private val messageListener: MessageListener,
    private val imageService: ImageService,
    private val readReceiptUpdater: ReadReceiptUpdater,
    private val activeChat: ActiveChatTracker,
    private val missionService: com.messageai.tactical.modules.missions.MissionService,
    private val aiService: com.messageai.tactical.modules.ai.AIService,
    private val reportService: ReportService
) : androidx.lifecycle.ViewModel() {
    val myUid: String? get() = auth.currentUser?.uid

    fun messages(chatId: String) = repo.messages(chatId)

    fun chatTitle(chatId: String) = chatDao.getChat(chatId).map { it?.name ?: "Chat" }

    fun otherParticipant(chatId: String) = chatDao.getChat(chatId).map { entity ->
        entity?.let {
            ParticipantHelper.getOtherParticipant(it.participants, myUid ?: "")
        }
    }

    fun userOnline(uid: String?): kotlinx.coroutines.flow.Flow<Boolean> =
        if (uid.isNullOrEmpty()) kotlinx.coroutines.flow.flowOf(false) else presence.isUserOnline(uid)

    fun startListener(chatId: String, scope: kotlinx.coroutines.CoroutineScope) {
        activeChat.setActive(chatId)
        messageListener.start(chatId, scope)
    }
    fun stopListener() { 
        activeChat.setActive(null)
        messageListener.stop() 
    }

    suspend fun markAsRead(chatId: String) {
        // Clear unread count when user opens chat
        repo.db.chatDao().updateUnread(chatId, 0)
    }

    /**
     * Marks the given messages as read for the current user by adding the user's UID
     * to each message's readBy list in Firestore. This triggers listeners to recalculate
     * unread counts so badges decrement as messages are viewed.
     */
    fun markMessagesRead(chatId: String, messageIds: List<String>, scope: kotlinx.coroutines.CoroutineScope) {
        readReceiptUpdater.markRead(chatId, messageIds, scope)
    }

    /** Marks all messages in this chat as read for the current user (one-time on open). */
    fun markAllAsRead(chatId: String, scope: kotlinx.coroutines.CoroutineScope) {
        val myUidLocal = myUid ?: return
        scope.launch(Dispatchers.IO) {
            val all = repo.db.messageDao().getAllMessagesForChat(chatId)
            val json = Json { ignoreUnknownKeys = true }
            val toMark = all.asSequence()
                .filter { it.senderId != myUidLocal }
                .mapNotNull { m ->
                    val readBy = try { json.decodeFromString<List<String>>(m.readBy) } catch (_: Exception) { emptyList() }
                    if (readBy.contains(myUidLocal)) null else m.id
                }
                .toList()
            if (toMark.isNotEmpty()) {
                readReceiptUpdater.markRead(chatId, toMark, scope)
            }
        }
    }

    suspend fun send(chatId: String, text: String, context: android.content.Context) {
        val me = auth.currentUser ?: return
        val id = UUID.randomUUID().toString()
        val entity = com.messageai.tactical.data.db.MessageEntity(
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
        repo.db.messageDao().upsert(entity)
        com.messageai.tactical.data.remote.SendWorker.enqueue(context, id, chatId, me.uid, text, entity.timestamp)
        // Heuristic: if message text appears to describe a nearby threat, trigger analysis for its chat.
        if (!text.isNullOrBlank()) {
            val t = text.lowercase()
            val looksLikeThreat = listOf("gunfire", "shots fired", "active shooter", "enemy", "contact", "ied", "ambush", "hostile", "attack").any { t.contains(it) }
            if (looksLikeThreat) {
                try { com.messageai.tactical.data.remote.ThreatAnalyzeWorker.enqueue(context, chatId, 100) } catch (_: Exception) {}
            }
        }

        // If this is the AI Buddy chat, route the prompt and persist the assistant reply to the same chat
        val buddyChatId = com.messageai.tactical.data.remote.FirestorePaths.directChatId(
            me.uid, com.messageai.tactical.ui.main.aibuddy.AIBuddyRouter.AI_UID
        )
        if (chatId == buddyChatId) {
            val contextChat = activeChat.lastNonBuddyChatId.value
            // Build candidate chats snapshot for routing context (id, name, last message snippet, updatedAt)
            val buddyChatId = com.messageai.tactical.data.remote.FirestorePaths.directChatId(
                me.uid, com.messageai.tactical.ui.main.aibuddy.AIBuddyRouter.AI_UID
            )
            val chats = repo.db.chatDao().getChats().first()
            val candidates = chats.filter { it.id != buddyChatId && (it.name ?: "").lowercase() != "ai buddy" }
                .map { ce ->
                mapOf(
                    "id" to ce.id,
                    "name" to (ce.name ?: ""),
                    "updatedAt" to ce.updatedAt,
                    "lastMessage" to (ce.lastMessage ?: "")
                )
            }
            android.util.Log.i(
                "ChatScreen",
                "Buddy routeAssistant start buddyChatId=$chatId contextChat=$contextChat promptLen=${text.length} candidates=${candidates.size}"
            )
            // Log a fresh Firebase ID token for manual testing of aiRouter
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.getIdToken(false)
                ?.addOnSuccessListener { android.util.Log.i("ID_TOKEN", it.token ?: "") }
            // Always hit the router with prompt + candidates
            val decision = aiService.routeAssistant(contextChat, text, candidateChats = candidates).getOrElse { emptyMap() }
            val raw = decision["decision"]?.toString() ?: "{\"tool\":\"none\",\"args\":{},\"reply\":\"I didn't understand.\"}"
            android.util.Log.d("ChatScreen", "Buddy routeAssistant decisionRaw=$raw")
            val (humanReadable, selectedTool) = try {
                val obj = org.json.JSONObject(raw)
                val reply = obj.optString("reply", "")
                val tool = obj.optString("tool", "none")
                android.util.Log.d("ChatScreen", "Buddy routeAssistant parsed tool=$tool hasReply=${reply.isNotBlank()}")
                val textForUser = if (reply.isNotBlank()) reply else {
                    when (tool) {
                        "none" -> "I couldn't map that to a tool. Tell me which chat or be more specific."
                        else -> "Selected $tool. Proceeding."
                    }
                }
                textForUser to tool
            } catch (_: Exception) {
                android.util.Log.w("ChatScreen", "Buddy routeAssistant decision parse error")
                "I'm processing that request." to "none"
            }
            val aiMsgId = UUID.randomUUID().toString()
            // Persist AI reply locally for offline-first before enqueuing
            val aiEntity = com.messageai.tactical.data.db.MessageEntity(
                id = aiMsgId,
                chatId = chatId,
                senderId = com.messageai.tactical.ui.main.aibuddy.AIBuddyRouter.AI_UID,
                text = humanReadable,
                imageUrl = null,
                timestamp = System.currentTimeMillis(),
                status = "SENDING",
                readBy = "[]",
                deliveredBy = "[]",
                synced = false,
                createdAt = System.currentTimeMillis()
            )
            repo.db.messageDao().upsert(aiEntity)
            com.messageai.tactical.data.remote.SendWorker.enqueue(
                context = context,
                messageId = aiMsgId,
                chatId = chatId,
                senderId = com.messageai.tactical.ui.main.aibuddy.AIBuddyRouter.AI_UID,
                text = humanReadable,
                clientTs = System.currentTimeMillis()
            )

            // Execute the selected tool (generate and warm cache), then notify user.
            try {
                when (selectedTool) {
                    "template/warnord" -> reportService.generateWarnord(
                        chatId = contextChat,
                        prompt = text,
                        candidateChats = candidates
                    ).onSuccess { postImmediate("WARNORD ready. Open Outputs > WARNORD to preview and share.", context, chatId) }
                        .onFailure { postImmediate("WARNORD generation failed: ${it.message}", context, chatId) }
                    "template/opord" -> reportService.generateOpord(
                        chatId = contextChat,
                        prompt = text,
                        candidateChats = candidates
                    ).onSuccess { postImmediate("OPORD ready. Open Outputs > OPORD to preview and share.", context, chatId) }
                        .onFailure { postImmediate("OPORD generation failed: ${it.message}", context, chatId) }
                    "template/frago" -> reportService.generateFrago(
                        chatId = contextChat,
                        prompt = text,
                        candidateChats = candidates
                    ).onSuccess { postImmediate("FRAGO ready. Open Outputs > FRAGO to preview and share.", context, chatId) }
                        .onFailure { postImmediate("FRAGO generation failed: ${it.message}", context, chatId) }
                    "sitrep/summarize" -> {
                        if (contextChat.isNullOrBlank()) {
                            postImmediate("I need a chat selected to generate a SITREP. Open a chat and ask again.", context, chatId)
                        } else {
                            reportService.generateSITREP(contextChat, "6h")
                                .onSuccess { postImmediate("SITREP ready for the current chat. Open Outputs to view.", context, chatId) }
                                .onFailure { postImmediate("SITREP generation failed: ${it.message}", context, chatId) }
                        }
                    }
                    "threats/extract" -> {
                        val targetChat = contextChat
                        if (targetChat.isNullOrBlank()) {
                            postImmediate("I need a chat selected to analyze threats. Open a chat and ask again.", context, chatId)
                        } else {
                            val saved = com.messageai.tactical.modules.geo.GeoService(
                                context = context,
                                firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                                auth = com.google.firebase.auth.FirebaseAuth.getInstance(),
                                aiService = aiService
                            ).analyzeChatThreats(targetChat)
                            saved.onSuccess { count ->
                                postImmediate("Logged $count threat(s) from the selected chat.", context, chatId)
                            }.onFailure { e ->
                                postImmediate("Threat analysis failed: ${e.message}", context, chatId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ChatScreen", "tool execution error: ${e.message}")
            }
        }
    }

    private suspend fun postImmediate(text: String, context: Context, chatId: String) {
        val id = java.util.UUID.randomUUID().toString()
        val entity = com.messageai.tactical.data.db.MessageEntity(
            id = id,
            chatId = chatId,
            senderId = com.messageai.tactical.ui.main.aibuddy.AIBuddyRouter.AI_UID,
            text = text,
            imageUrl = null,
            timestamp = System.currentTimeMillis(),
            status = "SENDING",
            readBy = "[]",
            deliveredBy = "[]",
            synced = false,
            createdAt = System.currentTimeMillis()
        )
        repo.db.messageDao().upsert(entity)
        com.messageai.tactical.data.remote.SendWorker.enqueue(
            context,
            id,
            chatId,
            com.messageai.tactical.ui.main.aibuddy.AIBuddyRouter.AI_UID,
            text,
            entity.timestamp
        )
    }

    suspend fun sendImage(chatId: String, uri: Uri, context: android.content.Context) {
        val me = auth.currentUser ?: return
        val id = UUID.randomUUID().toString()
        
        // Persist image to cache for retry
        val cachedFile = imageService.persistToCache(uri)
        
        // Create placeholder entity
        val entity = com.messageai.tactical.data.db.MessageEntity(
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
        
        // Enqueue both message creation and image upload
        com.messageai.tactical.data.remote.SendWorker.enqueue(
            context, id, chatId, me.uid, null, entity.timestamp, cachedFile.absolutePath
        )
        ImageUploadWorker.enqueue(context, id, chatId, me.uid, cachedFile.absolutePath)
    }
}
