package com.messageai.tactical.ui.main

import android.util.Log
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
/**
 * ViewModel for the Mission Board screen.
 *
 * Observability:
 * - Emits lightweight JSON logs when the active `chatId` changes and when status updates are submitted.
 */
class MissionBoardViewModel @Inject constructor(
    private val missionService: MissionService,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val chatId = MutableStateFlow<String?>(null)

    val missions: StateFlow<List<Pair<String, com.messageai.tactical.modules.missions.Mission>>> =
        chatId.flatMapLatest { id ->
            if (id.isNullOrBlank()) flowOf(emptyList()) else missionService.observeMissions(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setChatId(id: String) {
        chatId.value = id
        Log.d(TAG, json("event" to "mission_board_set_chat", "chatId" to id))
    }

    suspend fun updateStatus(missionId: String, status: String) {
        Log.i(TAG, json("event" to "mission_board_update_status", "missionId" to missionId, "status" to status))
        missionService.updateMission(missionId, mapOf("status" to status, "updatedAt" to System.currentTimeMillis()))
        missionService.archiveIfCompleted(missionId)
    }

    companion object {
        private const val TAG = "MissionBoardVM"
        private fun json(vararg pairs: Pair<String, Any?>): String = buildString {
            append('{')
            pairs.forEachIndexed { index, (k, v) ->
                if (index > 0) append(',')
                append('"').append(k).append('"').append(':')
                when (v) {
                    null -> append("null")
                    is Number, is Boolean -> append(v.toString())
                    else -> {
                        val s = v.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                        append('"').append(s).append('"')
                    }
                }
            }
            append('}')
        }
    }
}


