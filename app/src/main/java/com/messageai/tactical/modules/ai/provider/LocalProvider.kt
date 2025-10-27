package com.messageai.tactical.modules.ai.provider

import com.messageai.tactical.data.db.MessageEntity
import com.messageai.tactical.modules.ai.IAIProvider
import kotlinx.coroutines.delay
import kotlin.random.Random

class LocalProvider : IAIProvider {
    override suspend fun generateTemplate(type: String, context: String) = Result.success(
        mapOf("type" to type, "fields" to mapOf("example" to "value"), "confidence" to 0.8)
    )

    override suspend fun extractGeoData(text: String) = Result.success(
        mapOf("lat" to 34.000, "lng" to -117.000, "format" to "latlng")
    )

    override suspend fun summarizeThreats(messages: List<MessageEntity>) = Result.success(
        listOf(mapOf("summary" to "Low risk", "severity" to 1))
    )

    override suspend fun detectIntent(messages: List<MessageEntity>) = Result.success(
        mapOf("intent" to "none", "confidence" to 0.3)
    )

    override suspend fun runWorkflow(endpoint: String, payload: Map<String, Any?>) = Result.success(
        mapOf("endpoint" to endpoint, "status" to "ok", "requestId" to Random.nextInt().toString())
    )
}


