package com.messageai.tactical.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.messageai.tactical.modules.missions.Mission

@Composable
fun MissionBoardScreen(chatId: String, onOpenMission: (missionId: String, chatId: String?) -> Unit = { _, _ -> }) {
    val vm: MissionBoardViewModel = hiltViewModel()
    LaunchedEffect(chatId) { vm.setChatId(chatId) }
    val missions by vm.missions.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Missions", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(missions.size) { idx ->
                val (id, m) = missions[idx]
                MissionRow(
                    m = m,
                    onStatusChange = { newStatus -> scope.launch { vm.updateStatus(id, newStatus) } },
                    onClick = { onOpenMission(id, m.chatId) }
                )
                Divider()
            }
        }
    }
}

@Composable
private fun MissionRow(m: Mission, onStatusChange: (String) -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleMedium)
            if (!m.description.isNullOrBlank()) Text(m.description!!, style = MaterialTheme.typography.bodySmall)
        }
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) { Text(m.status) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf("open", "in_progress", "done").forEach { s ->
                    DropdownMenuItem(text = { Text(s) }, onClick = { expanded = false; onStatusChange(s) })
                }
            }
        }
    }
}


