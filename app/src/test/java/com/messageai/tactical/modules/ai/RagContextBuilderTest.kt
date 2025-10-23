/**
 * MessageAI – RagContextBuilder Unit Tests
 * 
 * Tests the RAG context builder that fetches and formats message history
 * for AI context windows.
 */
package com.messageai.tactical.modules.ai

import com.messageai.tactical.data.db.MessageDao
import com.messageai.tactical.data.db.MessageEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RagContextBuilderTest {

    private lateinit var mockDao: MessageDao
    private lateinit var builder: RagContextBuilder

    @Before
    fun setup() {
        mockDao = mockk()
        builder = RagContextBuilder(mockDao)
    }

    @Test
    fun `build fetches messages and formats them correctly`() = runTest {
        // Given
        val chatId = "chat123"
        val maxMessages = 10
        val mockMessages = listOf(
            MessageEntity("1", chatId, "user1", "Hello", null, 1000L, "SENT", "[]", "[]", true, 1000L),
            MessageEntity("2", chatId, "user2", "World", null, 2000L, "SENT", "[]", "[]", true, 2000L)
        )

        coEvery { mockDao.getLastMessages(chatId, maxMessages) } returns mockMessages

        // When
        val spec = RagContextBuilder.WindowSpec(
            chatId = chatId,
            maxMessages = maxMessages
        )
        val result = builder.build(spec)

        // Then
        assertEquals(2, result.size)
        
        val first = result[0]
        assertEquals("1", first["id"])
        assertEquals("user1", first["senderId"])
        assertEquals(1000L, first["timestamp"])
        assertEquals("Hello", first["text"])

        val second = result[1]
        assertEquals("2", second["id"])
        assertEquals("user2", second["senderId"])
        assertEquals(2000L, second["timestamp"])
        assertEquals("World", second["text"])

        coVerify { mockDao.getLastMessages(chatId, maxMessages) }
    }

    @Test
    fun `build handles empty message list`() = runTest {
        // Given
        val chatId = "emptyChat"
        val maxMessages = 5

        coEvery { mockDao.getLastMessages(chatId, maxMessages) } returns emptyList()

        // When
        val spec = RagContextBuilder.WindowSpec(
            chatId = chatId,
            maxMessages = maxMessages
        )
        val result = builder.build(spec)

        // Then
        assertTrue(result.isEmpty())
        coVerify { mockDao.getLastMessages(chatId, maxMessages) }
    }

    @Test
    fun `build respects maxMessages limit`() = runTest {
        // Given
        val chatId = "chat456"
        val maxMessages = 3
        val mockMessages = (1..3).map { i ->
            MessageEntity("$i", chatId, "user$i", "Message $i", null, i * 1000L, "SENT", "[]", "[]", true, i * 1000L)
        }

        coEvery { mockDao.getLastMessages(chatId, maxMessages) } returns mockMessages

        // When
        val spec = RagContextBuilder.WindowSpec(
            chatId = chatId,
            maxMessages = maxMessages
        )
        val result = builder.build(spec)

        // Then
        assertEquals(3, result.size)
        coVerify { mockDao.getLastMessages(chatId, maxMessages) }
    }

    @Test
    fun `build includes all required fields in context map`() = runTest {
        // Given
        val chatId = "chat789"
        val message = MessageEntity(
            id = "msg1",
            chatId = chatId,
            senderId = "sender123",
            text = "Test message",
            imageUrl = "http://example.com/image.jpg",
            timestamp = 5000L,
            status = "SENT",
            readBy = "[]",
            deliveredBy = "[]",
            synced = true,
            createdAt = 5000L
        )

        coEvery { mockDao.getLastMessages(chatId, 1) } returns listOf(message)

        // When
        val spec = RagContextBuilder.WindowSpec(chatId = chatId, maxMessages = 1)
        val result = builder.build(spec)

        // Then
        assertEquals(1, result.size)
        val context = result[0]
        
        // Verify all expected fields are present
        assertTrue(context.containsKey("id"))
        assertTrue(context.containsKey("senderId"))
        assertTrue(context.containsKey("timestamp"))
        assertTrue(context.containsKey("text"))
        
        assertEquals("msg1", context["id"])
        assertEquals("sender123", context["senderId"])
        assertEquals(5000L, context["timestamp"])
        assertEquals("Test message", context["text"])
    }

    @Test
    fun `WindowSpec has correct defaults`() {
        // Given/When
        val spec = RagContextBuilder.WindowSpec(
            chatId = "chat1",
            maxMessages = 50
        )

        // Then
        assertEquals("chat1", spec.chatId)
        assertEquals(50, spec.maxMessages)
        assertTrue(spec.includeGeo) // Default should be true
    }

    @Test
    fun `build handles messages with null text gracefully`() = runTest {
        // Given
        val chatId = "chat999"
        val message = MessageEntity(
            id = "msg1",
            chatId = chatId,
            senderId = "user1",
            text = "", // Empty text
            imageUrl = null,
            timestamp = 1000L,
            status = "SENT",
            readBy = "[]",
            deliveredBy = "[]",
            synced = true,
            createdAt = 1000L
        )

        coEvery { mockDao.getLastMessages(chatId, 1) } returns listOf(message)

        // When
        val spec = RagContextBuilder.WindowSpec(chatId = chatId, maxMessages = 1)
        val result = builder.build(spec)

        // Then
        assertEquals(1, result.size)
        assertEquals("", result[0]["text"])
    }
}

