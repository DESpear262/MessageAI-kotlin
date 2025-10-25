package com.messageai.tactical.ui.main.aibuddy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@HiltViewModel
class AIBuddyViewModel @Inject constructor(
    private val router: AIBuddyRouter,
    private val appContext: android.content.Context
) : ViewModel() {

    private val _chatId = MutableStateFlow<String?>(null)
    val chatId: StateFlow<String?> = _chatId

    fun ensureBuddyChatAndOnboard() {
        viewModelScope.launch {
            val id = router.ensureBuddyChat()
            _chatId.value = id
            if (!router.hasSeenOnboarding()) {
                // Persist onboarding as a real message from the buddy
                val intro = router.buildOnboardingMessage()
                val messageId = UUID.randomUUID().toString()
                com.messageai.tactical.data.remote.SendWorker.enqueue(
                    context = appContext,
                    messageId = messageId,
                    chatId = id,
                    senderId = AIBuddyRouter.AI_UID,
                    text = intro,
                    clientTs = System.currentTimeMillis()
                )
                router.markOnboardingSeen()
            }
        }
    }

    fun onUserPrompt(text: String) {
        viewModelScope.launch {
            router.handlePrompt(text, onBotMessage = { msg ->
                viewModelScope.launch {
                    // Persist the bot reply as a message as well to preserve context
                    val idLocal = chatId.value ?: router.ensureBuddyChat()
                    val messageId = UUID.randomUUID().toString()
                    com.messageai.tactical.data.remote.SendWorker.enqueue(
                        context = appContext,
                        messageId = messageId,
                        chatId = idLocal,
                        senderId = AIBuddyRouter.AI_UID,
                        text = msg,
                        clientTs = System.currentTimeMillis()
                    )
                }
            })
        }
    }
}


