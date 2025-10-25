package com.messageai.tactical.ui.main.aibuddy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AIBuddyViewModel @Inject constructor(
    private val router: AIBuddyRouter
) : ViewModel() {

    data class UiMessage(val text: String?, val imageUrl: String?, val isMine: Boolean)

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages

    fun ensureBuddyChatAndOnboard() {
        viewModelScope.launch {
            router.ensureBuddyChat()
            if (!router.hasSeenOnboarding()) {
                val intro = router.buildOnboardingMessage()
                appendBot(intro)
                router.markOnboardingSeen()
            }
        }
    }

    fun onUserPrompt(text: String) {
        appendMine(text)
        viewModelScope.launch {
            router.handlePrompt(text, onBotMessage = { msg -> appendBot(msg) })
        }
    }

    private fun appendMine(text: String) {
        _messages.value = _messages.value + UiMessage(text = text, imageUrl = null, isMine = true)
    }

    private fun appendBot(text: String) {
        _messages.value = _messages.value + UiMessage(text = text, imageUrl = null, isMine = false)
    }
}


