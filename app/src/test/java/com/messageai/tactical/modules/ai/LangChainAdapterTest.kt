/**
 * MessageAI – LangChainAdapter Unit Tests
 * 
 * Tests the LangChain HTTP adapter that routes requests to the
 * appropriate LangChain service endpoints.
 */
package com.messageai.tactical.modules.ai

import com.messageai.tactical.modules.ai.api.AiRequestEnvelope
import com.messageai.tactical.modules.ai.api.AiResponseEnvelope
import com.messageai.tactical.modules.ai.api.LangChainApi
import com.messageai.tactical.modules.ai.provider.LangChainAdapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LangChainAdapterTest {

    private lateinit var mockApi: LangChainApi
    private lateinit var adapter: LangChainAdapter

    @Before
    fun setup() {
        mockApi = mockk()
        adapter = LangChainAdapter(mockApi)
    }

    @Test
    fun `post generates requestId and calls correct endpoint for template generation`() = runTest {
        // Given
        val path = "template/generate"
        val payload = mapOf("type" to "MEDEVAC", "context" to "test")
        val context = mapOf("chatId" to "chat123")
        val response = AiResponseEnvelope<Map<String, Any?>>(
            requestId = "req123",
            status = "success",
            data = mapOf("result" to "data")
        )

        val requestSlot = slot<AiRequestEnvelope<Map<String, Any?>>>()
        coEvery { mockApi.generateTemplate(capture(requestSlot)) } returns response

        // When
        val result = adapter.post(path, payload, context)

        // Then
        assertEquals(response, result)
        
        val capturedRequest = requestSlot.captured
        assertNotNull(capturedRequest.requestId)
        assertTrue(capturedRequest.requestId.isNotEmpty())
        assertEquals(context, capturedRequest.context)
        assertEquals(payload, capturedRequest.payload)
        
        coVerify { mockApi.generateTemplate(any()) }
    }

    @Test
    fun `post routes threats extract to correct endpoint`() = runTest {
        // Given
        val path = "threats/extract"
        val payload = mapOf("messages" to listOf("msg1", "msg2"))
        val response = AiResponseEnvelope<Map<String, Any?>>(
            requestId = "req456",
            status = "success",
            data = mapOf("threats" to emptyList<Any>())
        )

        coEvery { mockApi.extractThreats(any()) } returns response

        // When
        val result = adapter.post(path, payload, emptyMap())

        // Then
        assertEquals(response, result)
        coVerify { mockApi.extractThreats(any()) }
    }

    @Test
    fun `post routes sitrep summarize to correct endpoint`() = runTest {
        // Given
        val path = "sitrep/summarize"
        val payload = mapOf("timeWindow" to 24)
        val response = AiResponseEnvelope<Map<String, Any?>>(
            requestId = "req789",
            status = "success",
            data = mapOf("summary" to "test")
        )

        coEvery { mockApi.summarizeSitrep(any()) } returns response

        // When
        val result = adapter.post(path, payload, emptyMap())

        // Then
        assertEquals(response, result)
        coVerify { mockApi.summarizeSitrep(any()) }
    }

    @Test
    fun `post routes casevac detect to correct endpoint`() = runTest {
        // Given
        val path = "intent/casevac/detect"
        val payload = mapOf("messages" to listOf("CASEVAC needed"))
        val response = AiResponseEnvelope<Map<String, Any?>>(
            requestId = "req101",
            status = "success",
            data = mapOf("intent" to "casevac", "confidence" to 0.95)
        )

        coEvery { mockApi.detectCasevac(any()) } returns response

        // When
        val result = adapter.post(path, payload, emptyMap())

        // Then
        assertEquals(response, result)
        coVerify { mockApi.detectCasevac(any()) }
    }

    @Test
    fun `post routes casevac workflow to correct endpoint`() = runTest {
        // Given
        val path = "workflow/casevac/run"
        val payload = mapOf("action" to "execute")
        val response = AiResponseEnvelope<Map<String, Any?>>(
            requestId = "req202",
            status = "success",
            data = mapOf("workflowId" to "wf123")
        )

        coEvery { mockApi.runCasevac(any()) } returns response

        // When
        val result = adapter.post(path, payload, emptyMap())

        // Then
        assertEquals(response, result)
        coVerify { mockApi.runCasevac(any()) }
    }

    @Test
    fun `post throws IllegalArgumentException for unsupported path`() = runTest {
        // Given
        val unsupportedPath = "unknown/endpoint"
        val payload = mapOf("data" to "test")

        // When/Then
        try {
            adapter.post(unsupportedPath, payload, emptyMap())
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported path") == true)
            assertTrue(e.message?.contains(unsupportedPath) == true)
        }
    }

    @Test
    fun `post generates unique requestId for each call`() = runTest {
        // Given
        val path = "template/generate"
        val payload = mapOf("test" to "data")
        val response = AiResponseEnvelope<Map<String, Any?>>(
            requestId = "resp1",
            status = "success",
            data = emptyMap()
        )

        val requestIds = mutableListOf<String>()
        coEvery { mockApi.generateTemplate(any()) } answers {
            val request = firstArg<AiRequestEnvelope<Map<String, Any?>>>()
            requestIds.add(request.requestId)
            response
        }

        // When
        adapter.post(path, payload, emptyMap())
        adapter.post(path, payload, emptyMap())
        adapter.post(path, payload, emptyMap())

        // Then
        assertEquals(3, requestIds.size)
        assertEquals(3, requestIds.distinct().size) // All unique
    }

    @Test
    fun `post handles empty context map`() = runTest {
        // Given
        val path = "template/generate"
        val payload = mapOf("data" to "test")
        val emptyContext = emptyMap<String, Any?>()
        val response = AiResponseEnvelope(
            requestId = "req999",
            status = "success",
            data = emptyMap<String, Any?>()
        )

        val requestSlot = slot<AiRequestEnvelope<Map<String, Any?>>>()
        coEvery { mockApi.generateTemplate(capture(requestSlot)) } returns response

        // When
        adapter.post(path, payload, emptyContext)

        // Then
        val capturedRequest = requestSlot.captured
        assertTrue(capturedRequest.context.isEmpty())
    }
}

