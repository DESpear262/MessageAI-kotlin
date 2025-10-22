/**
 * MessageAI – DTO <-> Entity mapping helpers.
 *
 * Contains pure functions to translate between Firestore models (network layer)
 * and Room entities (local persistence). Also provides helpers to construct
 * queue items for outbound messages. These mappers encapsulate decisions like
 * LWW timestamp resolution and JSON encoding for list fields.
 */
package com.messageai.tactical.data.remote

import com.messageai.tactical.data.db.ChatEntity
import com.messageai.tactical.data.db.MessageEntity
import com.messageai.tactical.data.db.SendQueueEntity
import com.messageai.tactical.data.remote.TimeUtils.lwwMillis
import com.messageai.tactical.data.remote.TimeUtils.toEpochMillis
import com.messageai.tactical.data.remote.model.ChatDoc
import com.messageai.tactical.data.remote.model.LastMessage
import com.messageai.tactical.data.remote.model.MessageDoc
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Mapper {
    private val json = Json { ignoreUnknownKeys = true }

    /** Maps a Firestore [MessageDoc] to a Room [MessageEntity]. */
    fun messageDocToEntity(doc: MessageDoc): MessageEntity {
        val ts = lwwMillis(doc.timestamp, doc.clientTimestamp)
        val readByJson = json.encodeToString(doc.readBy)
        val deliveredByJson = json.encodeToString(doc.deliveredBy)
        return MessageEntity(
            id = doc.id,
            chatId = doc.chatId,
            senderId = doc.senderId,
            text = doc.text,
            imageUrl = doc.imageUrl,
            timestamp = ts,
            status = doc.status,
            readBy = readByJson,
            deliveredBy = deliveredByJson,
            synced = !doc.localOnly,
            createdAt = System.currentTimeMillis()
        )
    }

    /** Maps a Room [MessageEntity] back to a Firestore [MessageDoc]. */
    fun entityToMessageDoc(entity: MessageEntity): MessageDoc {
        val readBy: List<String> = try {
            json.decodeFromString(entity.readBy)
        } catch (_: Exception) { emptyList() }
        val deliveredBy: List<String> = try {
            json.decodeFromString(entity.deliveredBy)
        } catch (_: Exception) { emptyList() }
        return MessageDoc(
            id = entity.id,
            chatId = entity.chatId,
            senderId = entity.senderId,
            text = entity.text,
            imageUrl = entity.imageUrl,
            clientTimestamp = entity.timestamp,
            status = entity.status,
            readBy = readBy,
            deliveredBy = deliveredBy,
            localOnly = !entity.synced
        )
    }

    /** Maps a Firestore [ChatDoc] to a Room [ChatEntity] with derived fields. */
    fun chatDocToEntity(doc: ChatDoc): ChatEntity {
        val last = doc.lastMessage
        val lastMsgPreview = when {
            last?.imageUrl != null -> "[image]"
            !last?.text.isNullOrBlank() -> last?.text
            else -> null
        }
        return ChatEntity(
            id = doc.id,
            type = if ((doc.participants.size) > 2) "group" else "direct",
            name = null,
            participants = json.encodeToString(doc.participants),
            lastMessage = lastMsgPreview,
            lastMessageTime = toEpochMillis(last?.timestamp),
            unreadCount = 0,
            updatedAt = toEpochMillis(doc.updatedAt) ?: System.currentTimeMillis()
        )
    }

    // Friendly chat entity for a specific user (computes name)
    fun chatDocToEntityForUser(doc: ChatDoc, myUid: String): ChatEntity {
        val base = chatDocToEntity(doc)
        val name = when {
            doc.participants.size <= 1 -> "Note to self"
            doc.participants.size == 2 -> {
                val other = doc.participants.firstOrNull { it != myUid } ?: myUid
                doc.participantDetails?.get(other)?.name ?: "Chat"
            }
            else -> doc.participantDetails?.values?.joinToString(", ") { it.name } ?: "Group"
        }
        return base.copy(name = name)
    }

    /** Constructs a new [SendQueueEntity] for an outbound message. */
    fun newQueueItem(messageId: String, chatId: String): SendQueueEntity =
        SendQueueEntity(id = messageId, messageId = messageId, chatId = chatId, createdAt = System.currentTimeMillis())
}
