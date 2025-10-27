/**
 * MessageAI – LocalProvider Unit Tests
 * 
 * Tests the local mock provider used for offline testing and development.
 */
package com.messageai.tactical.modules.ai

import com.messageai.tactical.data.db.MessageEntity
import com.messageai.tactical.modules.ai.provider.LocalProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocalProviderTest {

    private lateinit var provider: LocalProvider

    @Before
    fun setup() {
        provider = LocalProvider()
    }

    @Test
    fun `generateTemplate returns mock template with confidence`() = runTest {
        // Given
        val type = "MEDEVAC"
        val context = "Soldier injured at grid 123456"

        // When
        val result = provider.generateTemplate(type, context)

        // Then
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals(type, data?.get("type"))
        assertTrue(data?.containsKey("fields") == true)
        assertTrue(data?.containsKey("confidence") == true)
        
        val confidence = data?.get("confidence") as? Double
        assertNotNull(confidence)
        assertTrue(confidence!! >= 0.0 && confidence <= 1.0)
    }

    @Test
    fun `extractGeoData returns mock coordinates`() = runTest {
        // Given
        val text = "Position at 34.0522° N, 118.2437° W"

        // When
        val result = provider.extractGeoData(text)

        // Then
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertTrue(data?.containsKey("lat") == true)
        assertTrue(data?.containsKey("lng") == true)
        assertTrue(data?.containsKey("format") == true)
        
        val lat = data?.get("lat") as? Double
        val lng = data?.get("lng") as? Double
        assertNotNull(lat)
        assertNotNull(lng)
        assertEquals("latlng", data?.get("format"))
    }

    @Test
    fun `summarizeThreats returns mock threat summary`() = runTest {
        // Given
        val messages = listOf(
            MessageEntity("1", "chat1", "user1", "Enemy spotted", null, 1000L, "SENT", "[]", "[]", true, 1000L),
            MessageEntity("2", "chat1", "user2", "Position secure", null, 2000L, "SENT", "[]", "[]", true, 2000L)
        )

        // When
        val result = provider.summarizeThreats(messages)

        // Then
        assertTrue(result.isSuccess)
        val threats = result.getOrNull()
        assertNotNull(threats)
        assertTrue(threats!!.isNotEmpty())
        
        val firstThreat = threats[0]
        assertTrue(firstThreat.containsKey("summary"))
        assertTrue(firstThreat.containsKey("severity"))
        
        val severity = firstThreat["severity"] as? Int
        assertNotNull(severity)
    }

    @Test
    fun `detectIntent returns mock intent with confidence`() = runTest {
        // Given
        val messages = listOf(
            MessageEntity("1", "chat1", "user1", "Need medical evacuation", null, 1000L, "SENT", "[]", "[]", true, 1000L)
        )

        // When
        val result = provider.detectIntent(messages)

        // Then
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertTrue(data?.containsKey("intent") == true)
        assertTrue(data?.containsKey("confidence") == true)
        
        val confidence = data?.get("confidence") as? Double
        assertNotNull(confidence)
        assertTrue(confidence!! >= 0.0 && confidence <= 1.0)
    }

    @Test
    fun `runWorkflow returns mock workflow result`() = runTest {
        // Given
        val endpoint = "workflow/casevac/run"
        val payload = mapOf("action" to "start")

        // When
        val result = provider.runWorkflow(endpoint, payload)

        // Then
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertTrue(data?.containsKey("endpoint") == true)
        assertTrue(data?.containsKey("status") == true)
        assertTrue(data?.containsKey("requestId") == true)
        
        assertEquals(endpoint, data?.get("endpoint"))
        assertEquals("ok", data?.get("status"))
    }

    @Test
    fun `all provider methods never throw exceptions`() = runTest {
        // Test that LocalProvider is robust for testing

        val messages = listOf(
            MessageEntity("1", "chat1", "user1", "test", null, 1000L, "SENT", "[]", "[]", true, 1000L)
        )

        // None of these should throw
        assertDoesNotThrow {
            provider.generateTemplate("TYPE", "context")
            provider.extractGeoData("text")
            provider.summarizeThreats(messages)
            provider.detectIntent(messages)
            provider.runWorkflow("endpoint", emptyMap())
        }
    }

    private fun assertDoesNotThrow(block: suspend () -> Unit) {
        runTest {
            try {
                block()
            } catch (e: Exception) {
                fail("Expected no exception but got: ${e.message}")
            }
        }
    }
}

