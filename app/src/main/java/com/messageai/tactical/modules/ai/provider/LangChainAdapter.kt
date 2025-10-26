package com.messageai.tactical.modules.ai.provider

import com.messageai.tactical.modules.ai.api.AiRequestEnvelope
import com.messageai.tactical.modules.ai.api.AiResponseEnvelope
import com.messageai.tactical.modules.ai.api.LangChainApi
import java.util.UUID

class LangChainAdapter(private val api: LangChainApi) {
    suspend fun post(path: String, payload: Map<String, Any?>, context: Map<String, Any?>): AiResponseEnvelope<Map<String, Any?>> {
        val requestId = UUID.randomUUID().toString()
        val req = AiRequestEnvelope(
            requestId = requestId,
            context = context,
            payload = payload
        )
        return when (path) {
            "template/generate" -> api.generateTemplate(requestId, req)
            "template/warnord" -> api.generateWarnord(requestId, req)
            "template/opord" -> api.generateOpord(requestId, req)
            "template/frago" -> api.generateFrago(requestId, req)
            "template/medevac" -> api.generateMedevac(requestId, req)
            "threats/extract" -> api.extractThreats(requestId, req)
            "sitrep/summarize" -> api.summarizeSitrep(requestId, req)
            "intent/casevac/detect" -> api.detectCasevac(requestId, req)
            "workflow/casevac/run" -> api.runCasevac(requestId, req)
            "geo/extract" -> api.extractGeo(requestId, req)
            "assistant/route" -> api.assistantRoute(requestId, req)
            "missions/plan" -> api.missionsPlan(requestId, req)
            else -> throw IllegalArgumentException("Unsupported path $path")
        }
    }
}


