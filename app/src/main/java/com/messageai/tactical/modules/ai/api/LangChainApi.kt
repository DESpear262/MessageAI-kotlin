package com.messageai.tactical.modules.ai.api

import retrofit2.http.Body
import retrofit2.http.Header
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
    suspend fun generateTemplate(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>

    @POST("template/warnord")
    suspend fun generateWarnord(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>

    @POST("template/opord")
    suspend fun generateOpord(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>

    @POST("template/frago")
    suspend fun generateFrago(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>

    @POST("threats/extract")
    suspend fun extractThreats(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>

    @POST("sitrep/summarize")
    suspend fun summarizeSitrep(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>

    @POST("intent/casevac/detect")
    suspend fun detectCasevac(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>

    @POST("workflow/casevac/run")
    suspend fun runCasevac(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>

    @POST("geo/extract")
    suspend fun extractGeo(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>

    @POST("assistant/route")
    suspend fun assistantRoute(
        @Header("x-request-id") requestId: String,
        @Body body: AiRequestEnvelope<Map<String, Any?>>, 
    ): AiResponseEnvelope<Map<String, Any?>>
}


