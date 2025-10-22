/**
 * MessageAI – Unread count calculation helper.
 *
 * Provides centralized logic for calculating unread message counts based on
 * readBy lists in message entities.
 */
package com.messageai.tactical.util

import com.messageai.tactical.data.db.MessageEntity
import kotlinx.serialization.json.Json

object UnreadHelper {
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Calculates the number of unread messages for a user in a chat.
     *
     * A message is considered unread if:
     * 1. The sender is not the current user (senderId != myUid)
     * 2. The current user's UID is not in the readBy list
     *
     * @param messages List of all messages in the chat
     * @param myUid Current user's UID
     * @return Number of unread messages
     */
    fun calculateUnreadCount(messages: List<MessageEntity>, myUid: String): Int {
        return messages.count { msg ->
            msg.senderId != myUid && run {
                val readByList = try {
                    json.decodeFromString<List<String>>(msg.readBy)
                } catch (_: Exception) {
                    emptyList()
                }
                !readByList.contains(myUid)
            }
        }
    }
}

