package com.messageai.tactical.modules.ai.provider

import com.messageai.tactical.data.db.MessageEntity
import com.messageai.tactical.modules.ai.IAIProvider

/**
 * LangChain-backed AI provider.
 *
 * Delegates AI operations to the LangChain HTTP adapter, which targets the
 * Firebase Cloud Functions proxy (`aiRouter`). This enables fully remote
 * AI functionality when `BuildConfig.AI_ENABLED` is true.
 */
class LangChainProvider(
    private val adapter: LangChainAdapter
) : IAIProvider {

    override suspend fun generateTemplate(type: String, context: String): Result<Map<String, Any?>> = runCatching {
        val payload = mapOf(
            "type" to type,
            "contextText" to context,
        )
        adapter.post(
            path = "template/generate",
            payload = payload,
            context = emptyMap()
        ).data ?: emptyMap()
    }

    override suspend fun extractGeoData(text: String): Result<Map<String, Any?>> {
        // No dedicated remote endpoint yet; return a minimal, schema-shaped payload.
        return Result.success(mapOf("lat" to 0.0, "lng" to 0.0, "format" to "latlng"))
    }

    override suspend fun summarizeThreats(messages: List<MessageEntity>): Result<List<Map<String, Any?>>> = runCatching {
        val payload = mapOf("messages" to messages.map { it.text })
        val data = adapter.post(
            path = "threats/extract",
            payload = payload,
            context = emptyMap()
        ).data ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        (data["threats"] as? List<Map<String, Any?>>) ?: emptyList()
    }

    override suspend fun detectIntent(messages: List<MessageEntity>): Result<Map<String, Any?>> = runCatching {
        val payload = mapOf("messages" to messages.map { it.text })
        adapter.post(
            path = "intent/casevac/detect",
            payload = payload,
            context = emptyMap()
        ).data ?: emptyMap()
    }

    override suspend fun runWorkflow(endpoint: String, payload: Map<String, Any?>): Result<Map<String, Any?>> = runCatching {
        adapter.post(
            path = endpoint,
            payload = payload,
            context = emptyMap()
        ).data ?: emptyMap()
    }
}


