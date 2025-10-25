package com.messageai.tactical.ui.main.aibuddy

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIBuddyScreen(onBackToChats: () -> Unit) {
    val vm: AIBuddyViewModel = hiltViewModel()
    val messages = vm.messages.collectAsState()
    val title = "AI Buddy"

    var input by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(Unit) {
        vm.ensureBuddyChatAndOnboard()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBackToChats) { Text("Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages.value.size) { idx ->
                    val msg = messages.value[idx]
                    val isMine = msg.isMine
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
                                            contentDescription = "Image",
                                            modifier = Modifier.heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    if (!msg.text.isNullOrBlank()) {
                                        Text(text = msg.text!!)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Parity with ChatScreen UI (icons present, no-op for camera/gallery for now)
                IconButton(onClick = { /* gallery not used in buddy MVP */ }) {
                    Icon(Icons.Default.Image, contentDescription = "Gallery")
                }
                IconButton(onClick = { /* camera not used in buddy MVP */ }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask the AI Buddy…") }
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val text = input.text.trim()
                    if (text.isNotEmpty()) {
                        vm.onUserPrompt(text)
                        input = TextFieldValue("")
                    }
                }) { Text("Send") }
            }
        }
    }
}


