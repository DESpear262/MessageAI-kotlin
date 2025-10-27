/**
 * MessageAI – Tracks the currently active (foreground) chat.
 *
 * Used to suppress unread count overwrites while the user is viewing a chat.
 */
package com.messageai.tactical.util

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class ActiveChatTracker @Inject constructor() {
    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId
    private val _lastNonBuddyChatId = MutableStateFlow<String?>(null)
    val lastNonBuddyChatId: StateFlow<String?> = _lastNonBuddyChatId

    fun setActive(chatId: String?) {
        _activeChatId.value = chatId
        // Track last non-buddy chat to provide context to AI Buddy when in control channel
        if (chatId != null && !chatId.endsWith("_" + com.messageai.tactical.ui.main.aibuddy.AIBuddyRouter.AI_UID)) {
            _lastNonBuddyChatId.value = chatId
        }
    }
}


