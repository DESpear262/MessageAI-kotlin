package com.messageai.tactical.ui.main.aibuddy

import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.messageai.tactical.data.remote.ChatService
import com.messageai.tactical.data.db.ChatDao
import com.messageai.tactical.data.remote.FirestorePaths
import com.messageai.tactical.modules.ai.AIService
import com.messageai.tactical.modules.ai.work.CasevacWorker
import com.messageai.tactical.modules.geo.GeoService
import com.messageai.tactical.data.remote.GeofenceWorker
import com.messageai.tactical.data.remote.SendWorker
import com.messageai.tactical.util.ActiveChatTracker
import com.messageai.tactical.modules.reporting.ReportService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import org.json.JSONObject
import android.util.Log

/**
 * Routes natural-language prompts to available AI capabilities.
 * MVP heuristic: use light intent keywords; otherwise forward to provider chat response.
 */
@Singleton
class AIBuddyRouter @Inject constructor(
    private val auth: FirebaseAuth,
    private val chatService: ChatService,
    private val chatDao: ChatDao,
    private val ai: AIService,
    private val geo: GeoService,
    private val appContext: android.content.Context,
    private val activeChat: ActiveChatTracker,
    private val reportService: ReportService
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
        // Prefer the last non-buddy chat for context; fall back to current active
        val contextChat = activeChat.lastNonBuddyChatId.value ?: activeChat.activeChatId.value
        // Also mirror the user prompt into the AI Buddy chat for audit
        postToBuddy(senderId = me, text = text)

        Log.i("AIBuddyRouter", "routeAssistant start chatId=${contextChat ?: "null"} promptLen=${text.length}")
        // Build candidate chats snapshot for routing context (id, name, last message snippet, updatedAt)
        val buddyChatId = FirestorePaths.directChatId(auth.currentUser?.uid ?: return, AI_UID)
        val chats = chatDao.getChats().first()
        val candidates = chats.filter { it.id != buddyChatId && (it.name ?: "").lowercase() != "ai buddy" }
            .map { ce: com.messageai.tactical.data.db.ChatEntity ->
            mapOf(
                "id" to ce.id,
                "name" to (ce.name ?: ""),
                "updatedAt" to ce.updatedAt,
                "lastMessage" to (ce.lastMessage ?: "")
            )
            }
        // Call assistant router (LLM decides). The app will still post a friendly fallback if tool='none'.
        val decision = ai.routeAssistant(contextChat, text, candidateChats = candidates).getOrElse { emptyMap() }
        val raw = decision["decision"]?.toString() ?: "{\"tool\":\"none\",\"args\":{},\"reply\":\"I didn't understand.\"}"
        Log.d("AIBuddyRouter", "routeAssistant decisionRaw=$raw")
        // Parse the assistant decision and surface a human-readable reply
        val (msg, tool) = try {
            val obj = JSONObject(raw)
            val reply = obj.optString("reply", "")
            val tool = obj.optString("tool", "none")
            Log.d("AIBuddyRouter", "routeAssistant parsed tool=$tool hasReply=${reply.isNotBlank()}")
            val human = if (reply.isNotBlank()) reply else {
                when (tool) {
                    "none" -> "I couldn't map that to a tool. Tell me which chat or be more specific."
                    else -> "Selected $tool. Proceeding."
                }
            }
            human to tool
        } catch (_: Exception) {
            Log.w("AIBuddyRouter", "routeAssistant decision parse error")
            "I'm processing that request." to "none"
        }
        onBotMessage(msg)
        postToBuddy(senderId = AI_UID, text = msg)
        // Execute the selected tool when it is a document generation request.
        try {
            when (tool) {
                // Markdown templates: warm cache so the Outputs tab shows instantly
                "template/warnord" -> reportService.generateWarnord(
                    chatId = contextChat,
                    prompt = text,
                    candidateChats = candidates
                )
                    .onSuccess { postToBuddy(senderId = AI_UID, text = "WARNORD ready. Open Outputs > WARNORD to preview and share.") }
                    .onFailure { postToBuddy(senderId = AI_UID, text = "WARNORD generation failed: ${it.message}") }
                "template/opord" -> reportService.generateOpord(
                    chatId = contextChat,
                    prompt = text,
                    candidateChats = candidates
                )
                    .onSuccess { postToBuddy(senderId = AI_UID, text = "OPORD ready. Open Outputs > OPORD to preview and share.") }
                    .onFailure { postToBuddy(senderId = AI_UID, text = "OPORD generation failed: ${it.message}") }
                "template/frago" -> reportService.generateFrago(
                    chatId = contextChat,
                    prompt = text,
                    candidateChats = candidates
                )
                    .onSuccess { postToBuddy(senderId = AI_UID, text = "FRAGO ready. Open Outputs > FRAGO to preview and share.") }
                    .onFailure { postToBuddy(senderId = AI_UID, text = "FRAGO generation failed: ${it.message}") }
                // SITREP is bound to a chat context; require a target chat
                "sitrep/summarize" -> {
                    if (contextChat.isNullOrBlank()) {
                        postToBuddy(senderId = AI_UID, text = "I need a chat selected to generate a SITREP. Open a chat and ask again.")
                    } else {
                        reportService.generateSITREP(contextChat, "6h")
                            .onSuccess { postToBuddy(senderId = AI_UID, text = "SITREP ready for the current chat. Open Outputs to view.") }
                            .onFailure { postToBuddy(senderId = AI_UID, text = "SITREP generation failed: ${it.message}") }
                    }
                }
                // threats/extract is now a proactive path via assistant/gate; Buddy won't execute it directly
            }
        } catch (e: Exception) {
            Log.w("AIBuddyRouter", "tool execution error: ${e.message}")
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


