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
    private val _lastActiveExitAtMs = MutableStateFlow(0L)
    val lastActiveExitAtMs: StateFlow<Long> = _lastActiveExitAtMs

    fun setActive(chatId: String?) {
        _activeChatId.value = chatId
        if (chatId == null) {
            _lastActiveExitAtMs.value = System.currentTimeMillis()
        }
    }

    /** Returns true if the chat is active or we are within the grace window after exit. */
    fun shouldSkipUnreadUpdates(chatId: String, graceMs: Long = 3000L): Boolean {
        val active = _activeChatId.value
        if (active == chatId) return true
        val lastExit = _lastActiveExitAtMs.value
        return lastExit > 0 && (System.currentTimeMillis() - lastExit) <= graceMs
    }
}


