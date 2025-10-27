/**
 * MessageAI – AIService Unit Tests
 * 
 * Tests the AIService facade that coordinates AI operations,
 * RAG context building, and provider delegation.
 */
package com.messageai.tactical.modules.ai

import com.messageai.tactical.data.db.MessageDao
import com.messageai.tactical.data.db.MessageEntity
import com.messageai.tactical.modules.ai.provider.LangChainAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AIServiceTest {

    private lateinit var mockDao: MessageDao
    private lateinit var mockProvider: IAIProvider
    private lateinit var mockAdapter: LangChainAdapter
    private lateinit var mockContextBuilder: RagContextBuilder
    private lateinit var aiService: AIService

    @Before
    fun setup() {
        mockDao = mockk()
        mockProvider = mockk()
        mockAdapter = mockk()
        mockContextBuilder = mockk()
        aiService = AIService(mockDao, mockProvider, mockAdapter, mockContextBuilder)
    }

    @Test
    fun `generateTemplate builds context and delegates to provider`() = runTest {
        // Given
        val chatId = "chat123"
        val type = "MEDEVAC"
        val maxMessages = 50
        val mockContext = listOf(
            mapOf("text" to "Message 1", "id" to "1"),
            mapOf("text" to "Message 2", "id" to "2")
        )
        val expectedResult = mapOf("type" to type, "fields" to mapOf("field1" to "value1"))

        coEvery { 
            mockContextBuilder.build(
                RagContextBuilder.WindowSpec(chatId = chatId, maxMessages = maxMessages)
            ) 
        } returns mockContext

        coEvery { 
            mockProvider.generateTemplate(type, "Message 1\nMessage 2") 
        } returns Result.success(expectedResult)

        // When
        val result = aiService.generateTemplate(chatId, type, maxMessages)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedResult, result.getOrNull())
        coVerify { mockContextBuilder.build(any()) }
        coVerify { mockProvider.generateTemplate(type, "Message 1\nMessage 2") }
    }

    @Test
    fun `extractGeoData delegates to provider`() = runTest {
        // Given
        val text = "Coordinates: 34.0522° N, 118.2437° W"
        val expectedResult = mapOf("lat" to 34.0522, "lng" to -118.2437)

        coEvery { mockProvider.extractGeoData(text) } returns Result.success(expectedResult)

        // When
        val result = aiService.extractGeoData(text)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedResult, result.getOrNull())
        coVerify { mockProvider.extractGeoData(text) }
    }

    @Test
    fun `summarizeThreats fetches messages and delegates to provider`() = runTest {
        // Given
        val chatId = "chat123"
        val maxMessages = 20
        val mockMessages = listOf(
            MessageEntity("1", chatId, "user1", "Threat detected", null, 1000L, "SENT", "[]", "[]", true, 1000L),
            MessageEntity("2", chatId, "user2", "All clear", null, 2000L, "SENT", "[]", "[]", true, 2000L)
        )
        val expectedResult = listOf(mapOf("summary" to "Low threat", "severity" to 1))

        coEvery { mockDao.getLastMessages(chatId, maxMessages) } returns mockMessages
        coEvery { mockProvider.summarizeThreats(mockMessages) } returns Result.success(expectedResult)

        // When
        val result = aiService.summarizeThreats(chatId, maxMessages)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedResult, result.getOrNull())
        coVerify { mockDao.getLastMessages(chatId, maxMessages) }
        coVerify { mockProvider.summarizeThreats(mockMessages) }
    }

    @Test
    fun `detectIntent fetches messages and delegates to provider`() = runTest {
        // Given
        val chatId = "chat456"
        val minMessages = 10
        val mockMessages = listOf(
            MessageEntity("1", chatId, "user1", "CASEVAC needed", null, 1000L, "SENT", "[]", "[]", true, 1000L)
        )
        val expectedResult = mapOf("intent" to "casevac", "confidence" to 0.95)

        coEvery { mockDao.getLastMessages(chatId, minMessages) } returns mockMessages
        coEvery { mockProvider.detectIntent(mockMessages) } returns Result.success(expectedResult)

        // When
        val result = aiService.detectIntent(chatId, minMessages)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedResult, result.getOrNull())
        coVerify { mockDao.getLastMessages(chatId, minMessages) }
        coVerify { mockProvider.detectIntent(mockMessages) }
    }

    @Test
    fun `runWorkflow delegates to provider`() = runTest {
        // Given
        val path = "workflow/casevac/run"
        val payload = mapOf("action" to "start", "priority" to "urgent")
        val expectedResult = mapOf("status" to "ok", "requestId" to "req123")

        coEvery { mockProvider.runWorkflow(path, payload) } returns Result.success(expectedResult)

        // When
        val result = aiService.runWorkflow(path, payload)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedResult, result.getOrNull())
        coVerify { mockProvider.runWorkflow(path, payload) }
    }

    @Test
    fun `generateTemplate handles provider failure gracefully`() = runTest {
        // Given
        val chatId = "chat123"
        val type = "SITREP"
        val mockContext = listOf(mapOf("text" to "Message"))
        val exception = RuntimeException("Network error")

        coEvery { mockContextBuilder.build(any()) } returns mockContext
        coEvery { mockProvider.generateTemplate(any(), any()) } returns Result.failure(exception)

        // When
        val result = aiService.generateTemplate(chatId, type)

        // Then
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}

