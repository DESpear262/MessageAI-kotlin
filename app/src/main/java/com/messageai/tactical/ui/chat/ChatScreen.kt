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
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.catch
import android.util.Log
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
                actions = {
                    // Testing-only: summarize threats in current area
                    val geo = androidx.hilt.navigation.compose.hiltViewModel<GeoViewModel>()
                    TextButton(onClick = { geo.summarizeNearby() }) { Text("Summarize threats") }
                    // Testing-only: invoke AI summarization and persist to Firestore
                    TextButton(onClick = { geo.analyzeChatThreats(chatId) }) { Text("AI summarize") }
                    TextButton(onClick = { com.messageai.tactical.data.remote.GeofenceWorker.enqueue(context) }) { Text("Check geofence") }
                }
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
    private val aiService: com.messageai.tactical.modules.ai.AIService
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

        if (chatId == "system" && text.startsWith("/casevac")) {
            com.messageai.tactical.modules.ai.work.CasevacWorker.enqueue(context, chatId, id)
        }

        if (text.startsWith("/missionplan")) {
            // Create a mission seeded by AI tasks
            val missionId = missionService.createMission(
                com.messageai.tactical.modules.missions.Mission(
                    chatId = chatId,
                    title = "Mission Plan",
                    description = "Auto-generated from /missionplan",
                    status = "open",
                    priority = 3,
                    assignees = listOf(me.uid),
                    sourceMsgId = id
                )
            )
            aiService.extractTasks(chatId, maxMessages = 100).onSuccess { tasks ->
                tasks.forEach { t ->
                    val title = t["title"]?.toString() ?: return@forEach
                    val desc = t["description"]?.toString()
                    val priority = (t["priority"] as? Number)?.toInt() ?: 3
                    missionService.addTask(
                        missionId,
                        com.messageai.tactical.modules.missions.MissionTask(
                            missionId = missionId,
                            title = title,
                            description = desc,
                            priority = priority
                        )
                    )
                }
                // Archive if all tasks immediately done (unlikely on creation)
                missionService.archiveIfCompleted(missionId)
            }
        }
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
