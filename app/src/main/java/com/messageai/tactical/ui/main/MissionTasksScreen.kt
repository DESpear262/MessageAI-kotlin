package com.messageai.tactical.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messageai.tactical.modules.missions.MissionService
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionTasksScreen(missionId: String, chatId: String?, onBack: () -> Unit) {
    val vm: MissionTasksViewModel = hiltViewModel()
    LaunchedEffect(missionId) { vm.setMissionId(missionId) }
    val tasks by vm.tasks.collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Tasks") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } })
        LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            items(tasks.size) { idx ->
                val (id, t) = tasks[idx]
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleMedium)
                    if (!t.description.isNullOrBlank()) Text(t.description!!, style = MaterialTheme.typography.bodySmall)
                    Text("Priority: ${t.priority}", style = MaterialTheme.typography.labelSmall)
                }
                Divider()
            }
        }
    }
}

@HiltViewModel
class MissionTasksViewModel @Inject constructor(
    private val missionService: MissionService
) : ViewModel() {
    private val missionIdState = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val tasks: StateFlow<List<Pair<String, com.messageai.tactical.modules.missions.MissionTask>>> =
        missionIdState
            .flatMapLatest { id ->
                if (id.isNullOrBlank()) flowOf(emptyList()) else missionService.observeTasks(id)
            }
            .onEach { list ->
                try {
                    Log.d(TAG, "{\"event\":\"mission_tasks_emit\",\"count\":${list.size},\"firstId\":\"${list.firstOrNull()?.first ?: ""}\"}")
                } catch (_: Exception) {}
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setMissionId(id: String) {
        missionIdState.value = id
        try { Log.d(TAG, "{\"event\":\"mission_tasks_set_id\",\"missionId\":\"$id\"}") } catch (_: Exception) {}
    }

    companion object { private const val TAG = "MissionTasksVM" }
}
