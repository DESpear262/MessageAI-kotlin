package com.messageai.tactical.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Central hub for in-app notification banners and deep link events.
 */
object NotificationCenter {
    data class InAppMessage(
        val chatId: String,
        val title: String,
        val preview: String
    )

    private val _inAppMessages = MutableSharedFlow<InAppMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val inAppMessages: SharedFlow<InAppMessage> = _inAppMessages

    fun emitInApp(message: InAppMessage) {
        _inAppMessages.tryEmit(message)
    }
}

object DeepLinkCenter {
    data class ChatLink(val chatId: String)

    private val _chatLinks = MutableSharedFlow<ChatLink>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val chatLinks: SharedFlow<ChatLink> = _chatLinks

    fun emitChat(chatId: String) {
        _chatLinks.tryEmit(ChatLink(chatId))
    }
}


