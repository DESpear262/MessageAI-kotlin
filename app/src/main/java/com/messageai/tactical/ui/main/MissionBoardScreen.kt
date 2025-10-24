package com.messageai.tactical.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.messageai.tactical.modules.missions.Mission

@Composable
fun MissionBoardScreen() {
    val vm: MissionBoardViewModel = hiltViewModel()
    val missions by vm.missions.collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("Missions", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(missions.size) { idx ->
                val (id, m) = missions[idx]
                MissionRow(m = m, onStatusChange = { newStatus -> vm.updateStatus(id, newStatus) })
                Divider()
            }
        }
    }
}

@Composable
private fun MissionRow(m: Mission, onStatusChange: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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


