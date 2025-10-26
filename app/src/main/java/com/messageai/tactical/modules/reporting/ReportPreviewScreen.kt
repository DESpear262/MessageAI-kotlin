package com.messageai.tactical.modules.reporting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportPreviewScreen(
    chatId: String?,
    kind: String, // "sitrep" | "warnord" | "opord" | "frago"
    onShare: (String) -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val md by viewModel.markdown.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(kind, chatId) {
        when (kind.lowercase()) {
            "sitrep" -> viewModel.loadSitrep(chatId ?: "", "6h")
            "warnord", "opord", "frago", "medevac" -> viewModel.loadTemplate(kind, chatId)
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(text = "Report Preview") })
    }, floatingActionButton = {
        if (!loading && !md.isNullOrBlank()) {
            FloatingActionButton(onClick = { onShare(md!!) }) {
                Text("Share")
            }
        }
    }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = md ?: "", fontFamily = FontFamily.Monospace)
            }
        }
    }
}


