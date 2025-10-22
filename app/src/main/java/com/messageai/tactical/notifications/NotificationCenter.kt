/**
 * MessageAI – Notification and deep link event buses.
 *
 * Provides SharedFlow-based event buses for coordinating notification display
 * and navigation across the app. Decouples the Firebase Messaging Service from
 * the Compose UI layer.
 */
package com.messageai.tactical.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Central hub for in-app notification banners.
 *
 * When a message arrives while the app is in the foreground, the messaging
 * service emits an InAppMessage event that the UI can collect and display
 * as a Snackbar or banner notification.
 */
object NotificationCenter {
    /**
     * Represents an in-app notification to be shown to the user.
     *
     * @property chatId The chat the message belongs to
     * @property title The notification title (usually sender name)
     * @property preview A preview of the message content
     */
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
    
    /** SharedFlow that UI components can collect to show in-app notifications. */
    val inAppMessages: SharedFlow<InAppMessage> = _inAppMessages

    /** Emits an in-app notification event. Called by MessagingService. */
    fun emitInApp(message: InAppMessage) {
        _inAppMessages.tryEmit(message)
    }
}

/**
 * Central hub for deep link navigation events.
 *
 * When the app is opened via a notification tap with a chatId intent extra,
 * MainActivity emits a ChatLink event that the navigation layer can handle.
 */
object DeepLinkCenter {
    /**
     * Represents a deep link to a specific chat.
     *
     * @property chatId The chat to navigate to
     */
    data class ChatLink(val chatId: String)

    private val _chatLinks = MutableSharedFlow<ChatLink>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    
    /** SharedFlow that navigation components can collect to handle deep links. */
    val chatLinks: SharedFlow<ChatLink> = _chatLinks

    /**
     * Emits a deep link event to navigate to a specific chat.
     *
     * @param chatId The ID of the chat to open
     */
    fun emitChat(chatId: String) {
        _chatLinks.tryEmit(ChatLink(chatId))
    }
}


