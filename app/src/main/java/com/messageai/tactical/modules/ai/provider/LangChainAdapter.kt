package com.messageai.tactical.modules.ai.provider

import com.messageai.tactical.modules.ai.api.AiRequestEnvelope
import com.messageai.tactical.modules.ai.api.AiResponseEnvelope
import com.messageai.tactical.modules.ai.api.LangChainApi
import java.util.UUID

class LangChainAdapter(private val api: LangChainApi) {
    suspend fun post(path: String, payload: Map<String, Any?>, context: Map<String, Any?>): AiResponseEnvelope<Map<String, Any?>> {
        val req = AiRequestEnvelope(
            requestId = UUID.randomUUID().toString(),
            context = context,
            payload = payload
        )
        return when (path) {
            "template/generate" -> api.generateTemplate(req)
            "template/warnord" -> api.generateWarnord(req)
            "template/opord" -> api.generateOpord(req)
            "template/frago" -> api.generateFrago(req)
            "threats/extract" -> api.extractThreats(req)
            "sitrep/summarize" -> api.summarizeSitrep(req)
            "intent/casevac/detect" -> api.detectCasevac(req)
            "workflow/casevac/run" -> api.runCasevac(req)
            "geo/extract" -> api.extractGeo(req)
            "assistant/route" -> api.assistantRoute(req)
            else -> throw IllegalArgumentException("Unsupported path $path")
        }
    }
}


