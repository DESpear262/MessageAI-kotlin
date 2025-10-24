package com.messageai.tactical.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.messageai.tactical.modules.missions.MissionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MissionBoardViewModel @Inject constructor(
    private val missions: MissionService,
    private val auth: FirebaseAuth
) : ViewModel() {
    // For MVP, derive a chat context if needed; here we show recent missions across all chats the user participates in would be ideal,
    // but per requirements, missions are per-chat; use a placeholder chatId if none selected.
    private val currentChatId: String = "global" // replace with selected chat scope in future

    val missions: StateFlow<List<Pair<String, com.messageai.tactical.modules.missions.Mission>>> =
        missions.observeMissions(currentChatId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun updateStatus(missionId: String, status: String) {
        missions.updateMission(missionId, mapOf("status" to status, "updatedAt" to System.currentTimeMillis()))
        missions.archiveIfCompleted(missionId)
    }
}


