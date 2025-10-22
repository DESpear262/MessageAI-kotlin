package com.messageai.tactical.data.remote

import com.google.firebase.Timestamp
import com.google.truth.Truth.assertThat
import com.messageai.tactical.data.remote.model.ChatDoc
import com.messageai.tactical.data.remote.model.LastMessage
import com.messageai.tactical.data.remote.model.MessageDoc
import com.messageai.tactical.data.remote.model.MessageStatus
import org.junit.Test

/**
 * Unit tests for [Mapper].
 *
 * Verifies bidirectional conversions between Firestore DTOs and Room entities,
 * including JSON encoding for list fields and LWW timestamp resolution.
 */
class MapperTest {

    @Test
    fun `messageDocToEntity maps all fields correctly`() {
        val doc = MessageDoc(
            id = "msg1",
            chatId = "chat1",
            senderId = "user1",
            text = "Hello",
            imageUrl = "https://example.com/image.jpg",
            timestamp = Timestamp(1609459200, 0),
            clientTimestamp = null,
            status = MessageStatus.SENT.name,
            readBy = listOf("user2", "user3"),
            deliveredBy = listOf("user2"),
            localOnly = false
        )

        val entity = Mapper.messageDocToEntity(doc)

        assertThat(entity.id).isEqualTo("msg1")
        assertThat(entity.chatId).isEqualTo("chat1")
        assertThat(entity.senderId).isEqualTo("user1")
        assertThat(entity.text).isEqualTo("Hello")
        assertThat(entity.imageUrl).isEqualTo("https://example.com/image.jpg")
        assertThat(entity.timestamp).isEqualTo(1609459200000L)
        assertThat(entity.status).isEqualTo(MessageStatus.SENT.name)
        assertThat(entity.readBy).contains("user2")
        assertThat(entity.deliveredBy).contains("user2")
        assertThat(entity.synced).isTrue()
    }

    @Test
    fun `messageDocToEntity prefers server timestamp over client timestamp`() {
        val doc = MessageDoc(
            id = "msg1",
            chatId = "chat1",
            senderId = "user1",
            timestamp = Timestamp(1609459300, 0),
            clientTimestamp = 1609459200000L,
            status = MessageStatus.SENT.name
        )

        val entity = Mapper.messageDocToEntity(doc)
        assertThat(entity.timestamp).isEqualTo(1609459300000L)
    }

    @Test
    fun `messageDocToEntity uses client timestamp when server is null`() {
        val doc = MessageDoc(
            id = "msg1",
            chatId = "chat1",
            senderId = "user1",
            timestamp = null,
            clientTimestamp = 1609459200000L,
            status = MessageStatus.SENDING.name
        )

        val entity = Mapper.messageDocToEntity(doc)
        assertThat(entity.timestamp).isEqualTo(1609459200000L)
    }

    @Test
    fun `messageDocToEntity marks localOnly as not synced`() {
        val doc = MessageDoc(
            id = "msg1",
            chatId = "chat1",
            senderId = "user1",
            localOnly = true,
            status = MessageStatus.SENDING.name
        )

        val entity = Mapper.messageDocToEntity(doc)
        assertThat(entity.synced).isFalse()
    }

    @Test
    fun `entityToMessageDoc converts back correctly`() {
        val entity = com.messageai.tactical.data.db.MessageEntity(
            id = "msg1",
            chatId = "chat1",
            senderId = "user1",
            text = "Hello",
            imageUrl = null,
            timestamp = 1609459200000L,
            status = MessageStatus.DELIVERED.name,
            readBy = """["user2"]""",
            deliveredBy = """["user2","user3"]""",
            synced = true,
            createdAt = System.currentTimeMillis()
        )

        val doc = Mapper.entityToMessageDoc(entity)

        assertThat(doc.id).isEqualTo("msg1")
        assertThat(doc.chatId).isEqualTo("chat1")
        assertThat(doc.senderId).isEqualTo("user1")
        assertThat(doc.text).isEqualTo("Hello")
        assertThat(doc.imageUrl).isNull()
        assertThat(doc.clientTimestamp).isEqualTo(1609459200000L)
        assertThat(doc.status).isEqualTo(MessageStatus.DELIVERED.name)
        assertThat(doc.readBy).containsExactly("user2")
        assertThat(doc.deliveredBy).containsExactly("user2", "user3")
        assertThat(doc.localOnly).isFalse()
    }

    @Test
    fun `entityToMessageDoc handles malformed JSON gracefully`() {
        val entity = com.messageai.tactical.data.db.MessageEntity(
            id = "msg1",
            chatId = "chat1",
            senderId = "user1",
            text = "Hello",
            imageUrl = null,
            timestamp = 1609459200000L,
            status = MessageStatus.SENT.name,
            readBy = "invalid json",
            deliveredBy = "also bad",
            synced = false,
            createdAt = System.currentTimeMillis()
        )

        val doc = Mapper.entityToMessageDoc(entity)

        assertThat(doc.readBy).isEmpty()
        assertThat(doc.deliveredBy).isEmpty()
    }

    @Test
    fun `chatDocToEntity maps direct chat correctly`() {
        val doc = ChatDoc(
            id = "chat1",
            participants = listOf("user1", "user2"),
            participantDetails = null,
            lastMessage = LastMessage(
                text = "Last message",
                imageUrl = null,
                senderId = "user1",
                timestamp = Timestamp(1609459200, 0)
            ),
            createdAt = Timestamp(1609459100, 0),
            updatedAt = Timestamp(1609459200, 0)
        )

        val entity = Mapper.chatDocToEntity(doc)

        assertThat(entity.id).isEqualTo("chat1")
        assertThat(entity.type).isEqualTo("direct")
        assertThat(entity.lastMessage).isEqualTo("Last message")
        assertThat(entity.lastMessageTime).isEqualTo(1609459200000L)
        assertThat(entity.participants).contains("user1")
        assertThat(entity.participants).contains("user2")
    }

    @Test
    fun `chatDocToEntity identifies group chats`() {
        val doc = ChatDoc(
            id = "chat1",
            participants = listOf("user1", "user2", "user3"),
            updatedAt = Timestamp(1609459200, 0)
        )

        val entity = Mapper.chatDocToEntity(doc)
        assertThat(entity.type).isEqualTo("group")
    }

    @Test
    fun `chatDocToEntity shows image placeholder for image messages`() {
        val doc = ChatDoc(
            id = "chat1",
            participants = listOf("user1", "user2"),
            lastMessage = LastMessage(
                text = "Check this out",
                imageUrl = "https://example.com/image.jpg",
                senderId = "user1"
            ),
            updatedAt = Timestamp(1609459200, 0)
        )

        val entity = Mapper.chatDocToEntity(doc)
        assertThat(entity.lastMessage).isEqualTo("[image]")
    }

    @Test
    fun `chatDocToEntity handles null lastMessage`() {
        val doc = ChatDoc(
            id = "chat1",
            participants = listOf("user1", "user2"),
            lastMessage = null,
            updatedAt = Timestamp(1609459200, 0)
        )

        val entity = Mapper.chatDocToEntity(doc)
        assertThat(entity.lastMessage).isNull()
        assertThat(entity.lastMessageTime).isNull()
    }

    @Test
    fun `newQueueItem creates valid queue entity`() {
        val queueItem = Mapper.newQueueItem("msg1", "chat1")

        assertThat(queueItem.id).isEqualTo("msg1")
        assertThat(queueItem.messageId).isEqualTo("msg1")
        assertThat(queueItem.chatId).isEqualTo("chat1")
        assertThat(queueItem.retryCount).isEqualTo(0)
        assertThat(queueItem.createdAt).isGreaterThan(0L)
    }

    @Test
    fun `round trip conversion preserves message data`() {
        val originalDoc = MessageDoc(
            id = "msg1",
            chatId = "chat1",
            senderId = "user1",
            text = "Round trip test",
            timestamp = Timestamp(1609459200, 0),
            status = MessageStatus.SENT.name,
            readBy = listOf("user2"),
            deliveredBy = listOf("user2", "user3")
        )

        val entity = Mapper.messageDocToEntity(originalDoc)
        val convertedDoc = Mapper.entityToMessageDoc(entity)

        assertThat(convertedDoc.id).isEqualTo(originalDoc.id)
        assertThat(convertedDoc.chatId).isEqualTo(originalDoc.chatId)
        assertThat(convertedDoc.senderId).isEqualTo(originalDoc.senderId)
        assertThat(convertedDoc.text).isEqualTo(originalDoc.text)
        assertThat(convertedDoc.status).isEqualTo(originalDoc.status)
        assertThat(convertedDoc.readBy).containsExactlyElementsIn(originalDoc.readBy)
        assertThat(convertedDoc.deliveredBy).containsExactlyElementsIn(originalDoc.deliveredBy)
    }
}


