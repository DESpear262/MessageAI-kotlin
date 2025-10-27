package com.messageai.tactical.modules.ai

import com.messageai.tactical.data.db.MessageEntity

interface IAIProvider {
    suspend fun generateTemplate(type: String, context: String): Result<Map<String, Any?>>
    // Keep no-op stub for future use (hardware provides geo data in Block C)
    suspend fun extractGeoData(text: String): Result<Map<String, Any?>>
    suspend fun summarizeThreats(messages: List<MessageEntity>): Result<List<Map<String, Any?>>>
    suspend fun detectIntent(messages: List<MessageEntity>): Result<Map<String, Any?>>
    suspend fun runWorkflow(endpoint: String, payload: Map<String, Any?>): Result<Map<String, Any?>>
}


