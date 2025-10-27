package com.messageai.tactical.ui.main

/**
 * OutputsScreen – List/detail UI for generated documents.
 *
 * Shows buttons to filter by document type (OPORD, WARNORD, FRAGO, SITREP, MEDEVAC),
 * a list of saved documents of the selected type (from Firestore), and a detail view
 * to read/share/delete an individual document.
 */

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messageai.tactical.modules.documents.Document
import com.messageai.tactical.modules.documents.DocumentService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.launch
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputsScreen() {
    val vm: OutputsViewModel = hiltViewModel()
    val selectedType by vm.selectedType.collectAsState()
    val documents by vm.documents.collectAsState(initial = emptyList())
    val selectedDoc by vm.selectedDoc.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Outputs") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterButton(text = "OPORD", selected = selectedType == "OPORD") { vm.setType("OPORD") }
                FilterButton(text = "WARNORD", selected = selectedType == "WARNORD") { vm.setType("WARNORD") }
                FilterButton(text = "FRAGO", selected = selectedType == "FRAGO") { vm.setType("FRAGO") }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterButton(text = "SITREP", selected = selectedType == "SITREP") { vm.setType("SITREP") }
                FilterButton(text = "MEDEVAC", selected = selectedType == "MEDEVAC") { vm.setType("MEDEVAC") }
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            if (documents.isEmpty() && selectedType.isNotBlank() && selectedDoc == null) {
                Text(
                    text = "No ${selectedType} documents yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (selectedDoc == null) {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(
                        count = documents.size,
                        key = { idx -> documents[idx].first }
                    ) { idx ->
                        val (id, d) = documents[idx]
                        Column(modifier = Modifier.fillMaxWidth().clickable { vm.open(id to d) }.padding(vertical = 10.dp)) {
                            Text(d.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                            Text("Updated: ${formatTime(d.updatedAt)}", style = MaterialTheme.typography.bodySmall)
                        }
                        Divider()
                    }
                }
            } else {
                val doc = selectedDoc!!.second
                val scrollState = rememberScrollState()
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.closeDetail() }) { Text("Back") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.shareSelected() }) { Text("Share") }
                        TextButton(onClick = { vm.deleteSelected() }) { Text("Delete") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(doc.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                        Text(text = doc.content, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) FilledTonalButton(onClick = onClick) { Text(text) } else OutlinedButton(onClick = onClick) { Text(text) }
}

private fun formatTime(ts: Long): String {
    if (ts <= 0) return ""
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
    return fmt.format(java.util.Date(ts))
}

@HiltViewModel
class OutputsViewModel @Inject constructor(
    private val documentService: DocumentService,
    private val appContext: android.content.Context
) : ViewModel() {
    private val _selectedType = MutableStateFlow("OPORD")
    val selectedType: StateFlow<String> = _selectedType

    val documents: StateFlow<List<Pair<String, Document>>> = _selectedType
        .flatMapLatest { type -> documentService.observeByType(type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedDoc = MutableStateFlow<Pair<String, Document>?>(null)

    fun setType(type: String) { _selectedType.value = type; selectedDoc.value = null }

    fun open(pair: Pair<String, Document>) {
        Log.i("OutputsVM", "open docId=${pair.first} type=${pair.second.type}")
        selectedDoc.value = pair
    }

    fun closeDetail() { Log.i("OutputsVM", "closeDetail"); selectedDoc.value = null }

    fun shareSelected() {
        val doc = selectedDoc.value?.second ?: return
        com.messageai.tactical.modules.reporting.ReportShare.shareMarkdown(
            appContext,
            fileNameFor(doc),
            doc.content
        )
    }

    fun deleteSelected() {
        val id = selectedDoc.value?.first ?: return
        Log.i("OutputsVM", "deleteSelected id=${id}")
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try { documentService.delete(id) } catch (_: Exception) {}
            selectedDoc.value = null
        }
    }

    private fun fileNameFor(doc: Document): String {
        val base = doc.type.lowercase()
        return "${base}_${doc.id.take(6)}.md"
    }
}

// No-op helper removed; use viewModelScope.launch with Dispatchers.IO directly


