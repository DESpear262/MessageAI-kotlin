package com.messageai.tactical.modules.ai.api

import retrofit2.http.Body
import retrofit2.http.POST

// Envelope contracts (simplified). Per-endpoint Data models live in provider package.
data class AiRequestEnvelope<T>(
    val requestId: String,
    val context: Map<String, Any?> = emptyMap(),
    val payload: T
)

data class AiResponseEnvelope<D>(
    val requestId: String,
    val status: String,
    val data: D?,
    val error: String? = null
)

interface LangChainApi {
    @POST("template/generate")
    suspend fun generateTemplate(@Body body: AiRequestEnvelope<Map<String, Any?>>): AiResponseEnvelope<Map<String, Any?>>

    @POST("threats/extract")
    suspend fun extractThreats(@Body body: AiRequestEnvelope<Map<String, Any?>>): AiResponseEnvelope<Map<String, Any?>>

    @POST("sitrep/summarize")
    suspend fun summarizeSitrep(@Body body: AiRequestEnvelope<Map<String, Any?>>): AiResponseEnvelope<Map<String, Any?>>

    @POST("intent/casevac/detect")
    suspend fun detectCasevac(@Body body: AiRequestEnvelope<Map<String, Any?>>): AiResponseEnvelope<Map<String, Any?>>

    @POST("workflow/casevac/run")
    suspend fun runCasevac(@Body body: AiRequestEnvelope<Map<String, Any?>>): AiResponseEnvelope<Map<String, Any?>>
}


