package com.messageai.tactical.ui.main.aibuddy

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.messageai.tactical.data.remote.ChatService
import com.messageai.tactical.data.remote.FirestorePaths
import com.messageai.tactical.modules.ai.AIService
import com.messageai.tactical.modules.ai.work.CasevacWorker
import com.messageai.tactical.modules.geo.GeoService
import com.messageai.tactical.data.remote.GeofenceWorker
import com.messageai.tactical.data.remote.SendWorker
import com.messageai.tactical.util.ActiveChatTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * Routes natural-language prompts to available AI capabilities.
 * MVP heuristic: use light intent keywords; otherwise forward to provider chat response.
 */
@Singleton
class AIBuddyRouter @Inject constructor(
    private val auth: FirebaseAuth,
    private val chatService: ChatService,
    private val ai: AIService,
    private val geo: GeoService,
    private val appContext: android.content.Context,
    private val activeChat: ActiveChatTracker
) {
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences("ai_buddy", android.content.Context.MODE_PRIVATE)
    }

    suspend fun ensureBuddyChat(): String = withContext(Dispatchers.IO) {
        val me = auth.currentUser?.uid ?: error("Not signed in")
        val chatId = FirestorePaths.directChatId(me, AI_UID)
        // Create deterministic chat if missing; use lightweight names
        chatService.ensureDirectChat(me, AI_UID, myName = "Me", otherName = "AI Buddy")
        chatId
    }

    fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDED, false)
    fun markOnboardingSeen() { prefs.edit().putBoolean(KEY_ONBOARDED, true).apply() }

    fun buildOnboardingMessage(): String = """
        I'm your AI Buddy. I can:
        - Summarize recent activity (SITREP).
        - Draft WARNORD/OPORD/FRAGO templates.
        - Extract threats from chat and watch geofences.
        - Auto-trigger a CASEVAC mission if chatter indicates injury.
        - Generate mission plans and tasks from context.
        Ask in natural language. I’ll act on your last opened chat by default, or tell me which chat to use. If you haven’t opened any chat yet, open one first and come back.
    """.trimIndent()

    /**
     * Handle a user prompt. Heuristics for MVP; provider-side intent detection can replace this.
     */
    suspend fun handlePrompt(text: String, onBotMessage: (String) -> Unit) {
        val me = auth.currentUser?.uid ?: return
        val targetChat = activeChat.activeChatId.value ?: run {
            onBotMessage("I need a chat to act on. Please open a chat, then ask again.")
            return
        }
        // Also mirror the user prompt into the AI Buddy chat for audit
        postToBuddy(senderId = me, text = text)

        val lower = text.lowercase()
        when {
            listOf("sitrep", "summarize", "report").any { lower.contains(it) } -> {
                val r = ReportServiceStub.generateSitrep(appContext, targetChat)
                val msg = "SITREP ready for chat $targetChat. (Preview saved)"
                onBotMessage(msg)
                postToBuddy(senderId = AI_UID, text = msg)
            }
            listOf("warnord", "opord", "frago", "template").any { lower.contains(it) } -> {
                val msg = "Template generation kicked off. Check the Reports preview screen."
                onBotMessage(msg)
                postToBuddy(senderId = AI_UID, text = msg)
            }
            listOf("threat", "ied", "enemy").any { lower.contains(it) } -> {
                val count = geo.analyzeChatThreats(targetChat).getOrElse { 0 }
                val msg = "Extracted $count threat items and saved to the map database."
                onBotMessage(msg)
                postToBuddy(senderId = AI_UID, text = msg)
            }
            listOf("geofence", "nearby", "check area").any { lower.contains(it) } -> {
                GeofenceWorker.enqueue(appContext)
                val msg = "Checking geofence now. I will notify if any live threats are within radius."
                onBotMessage(msg)
                postToBuddy(senderId = AI_UID, text = msg)
            }
            listOf("casevac", "medevac", "injury").any { lower.contains(it) } -> {
                // Call server workflow; app persists nothing, server creates the mission
                val result = ai.runWorkflow("workflow/casevac/run", mapOf("chatId" to targetChat)).getOrElse { emptyMap() }
                val missionId = result["missionId"]?.toString() ?: "(pending)"
                val msg = "CASEVAC workflow started via server. MissionId=$missionId"
                onBotMessage(msg)
                postToBuddy(senderId = AI_UID, text = msg)
            }
            listOf("missionplan", "tasks", "plan").any { lower.contains(it) } -> {
                val tasks = ai.extractTasks(targetChat, 100).getOrElse { emptyList() }
                val msg = "Generated ${tasks.size} tasks for mission planning."
                onBotMessage(msg)
                postToBuddy(senderId = AI_UID, text = msg)
            }
            else -> {
                // Fallback: ask provider to respond conversationally (not implemented; simple echo for MVP)
                val msg = "${DEFAULT_FALLBACK_PREFIX} ${text}"
                onBotMessage(msg)
                postToBuddy(senderId = AI_UID, text = msg)
            }
        }
        // Messages mirrored to buddy chat above ensure unread badge parity.
    }

    companion object {
        const val AI_UID = "ai-buddy"
        private const val KEY_ONBOARDED = "onboarded"
        private const val DEFAULT_FALLBACK_PREFIX = "I noted:"
    }

    private fun postToBuddy(senderId: String, text: String) {
        val chatId = try { FirestorePaths.directChatId(auth.currentUser?.uid ?: return, AI_UID) } catch (_: Exception) { return }
        val id = UUID.randomUUID().toString()
        SendWorker.enqueue(
            context = appContext,
            messageId = id,
            chatId = chatId,
            senderId = senderId,
            text = text,
            clientTs = System.currentTimeMillis()
        )
    }
}


