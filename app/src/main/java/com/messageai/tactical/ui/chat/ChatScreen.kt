package com.messageai.tactical.ui.chat

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.messageai.tactical.data.db.ChatDao
import com.messageai.tactical.data.media.ImageService
import com.messageai.tactical.data.remote.ImageUploadWorker
import com.messageai.tactical.data.remote.MessageListener
import com.messageai.tactical.data.remote.MessageRepository
import com.messageai.tactical.data.remote.MessageService
import com.messageai.tactical.data.remote.PresenceService
import com.messageai.tactical.data.remote.model.MessageDoc
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
            // Create file in cache/images/ subdirectory to match file_paths.xml
            val cacheImagesDir = File(context.cacheDir, "images").apply { mkdirs() }
            val imageFile = File(cacheImagesDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile
            )
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
                            // Permission already granted - create file in cache/images/ subdirectory
                            val cacheImagesDir = File(context.cacheDir, "images").apply { mkdirs() }
                            val imageFile = File(cacheImagesDir, "camera_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                imageFile
                            )
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

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repo: MessageRepository,
    private val svc: MessageService,
    private val auth: FirebaseAuth,
    private val chatDao: ChatDao,
    private val presence: PresenceService,
    private val messageListener: MessageListener,
    private val imageService: ImageService
) : androidx.lifecycle.ViewModel() {
    val myUid: String? get() = auth.currentUser?.uid

    fun messages(chatId: String) = repo.messages(chatId)

    fun chatTitle(chatId: String) = chatDao.getChat(chatId).map { it?.name ?: "Chat" }

    fun otherParticipant(chatId: String) = chatDao.getChat(chatId).map { entity ->
        entity?.let {
            val list = try { kotlinx.serialization.json.Json.decodeFromString<List<String>>(it.participants) } catch (_: Exception) { emptyList() }
            list.firstOrNull { uid -> uid != myUid } ?: myUid
        }
    }

    fun userOnline(uid: String?): kotlinx.coroutines.flow.Flow<Boolean> =
        if (uid.isNullOrEmpty()) kotlinx.coroutines.flow.flowOf(false) else presence.isUserOnline(uid)

    fun startListener(chatId: String, scope: kotlinx.coroutines.CoroutineScope) {
        messageListener.start(chatId, scope)
    }
    fun stopListener() { messageListener.stop() }

    suspend fun markAsRead(chatId: String) {
        // Clear unread count when user opens chat
        repo.db.chatDao().updateUnread(chatId, 0)
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
