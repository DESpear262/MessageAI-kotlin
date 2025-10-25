package com.messageai.tactical.modules.ai

import com.messageai.tactical.data.db.MessageDao
import com.messageai.tactical.data.db.MessageEntity
import com.messageai.tactical.modules.ai.provider.LangChainAdapter

class AIService(
    private val dao: MessageDao,
    private val provider: IAIProvider,
    private val adapter: LangChainAdapter,
    private val contextBuilder: RagContextBuilder
) {
    suspend fun generateTemplate(chatId: String, type: String, maxMessages: Int = 50): Result<Map<String, Any?>> {
        val ctx = contextBuilder.build(
            RagContextBuilder.WindowSpec(chatId = chatId, maxMessages = maxMessages)
        )
        val serialized = ctx.joinToString("\n") { it["text"]?.toString().orEmpty() }
        return provider.generateTemplate(type, serialized)
    }

    suspend fun extractGeoData(text: String): Result<Map<String, Any?>> = provider.extractGeoData(text)

    suspend fun summarizeThreats(chatId: String, maxMessages: Int): Result<List<Map<String, Any?>>> {
        val rows = dao.getLastMessages(chatId, maxMessages)
        return provider.summarizeThreats(rows)
    }

    suspend fun detectIntent(chatId: String, minMessages: Int): Result<Map<String, Any?>> {
        val rows: List<MessageEntity> = dao.getLastMessages(chatId, minMessages)
        return provider.detectIntent(rows)
    }

    suspend fun runWorkflow(path: String, payload: Map<String, Any?>): Result<Map<String, Any?>> {
        // Simple passthrough; provider may delegate to adapter. Context can be merged into payload by provider.
        return provider.runWorkflow(path, payload)
    }

    suspend fun extractTasks(chatId: String, maxMessages: Int = 100): Result<List<Map<String, Any?>>> {
        val ctx = contextBuilder.build(
            RagContextBuilder.WindowSpec(chatId = chatId, maxMessages = maxMessages)
        )
        val serialized = ctx.joinToString("\n") { it["text"]?.toString().orEmpty() }
        // Route via LangChain adapter (stub path /tasks/extract)
        return try {
            val resp = adapter.post("tasks/extract", mapOf("contextText" to serialized), mapOf("chatId" to chatId))
            val data = resp.data ?: emptyMap()
            val tasks = (data["tasks"] as? List<Map<String, Any?>>) ?: emptyList()
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


