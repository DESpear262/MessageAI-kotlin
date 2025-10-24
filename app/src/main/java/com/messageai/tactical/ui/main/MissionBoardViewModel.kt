package com.messageai.tactical.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.messageai.tactical.modules.missions.MissionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class MissionBoardViewModel @Inject constructor(
    private val missionService: MissionService,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val chatId = MutableStateFlow<String?>(null)

    val missions: StateFlow<List<Pair<String, com.messageai.tactical.modules.missions.Mission>>> =
        chatId.flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList()) else missionService.observeMissions(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setChatId(id: String) { chatId.value = id }

    suspend fun updateStatus(missionId: String, status: String) {
        missionService.updateMission(missionId, mapOf("status" to status, "updatedAt" to System.currentTimeMillis()))
        missionService.archiveIfCompleted(missionId)
    }
}


