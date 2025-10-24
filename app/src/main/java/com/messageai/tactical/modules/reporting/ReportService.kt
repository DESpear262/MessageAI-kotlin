package com.messageai.tactical.modules.reporting

import com.messageai.tactical.modules.ai.api.AiRequestEnvelope
import com.messageai.tactical.modules.ai.provider.LangChainAdapter
import java.util.UUID

class ReportService(private val adapter: LangChainAdapter) {

    suspend fun generateSITREP(chatId: String, timeWindow: String = "6h"): Result<String> = runCatching {
        val payload = mapOf("timeWindow" to timeWindow)
        val ctx = mapOf("chatId" to chatId)
        val res = adapter.post("sitrep/summarize", payload, ctx)
        (res.data?.get("content") as? String) ?: error("Missing markdown content")
    }

    suspend fun generateWarnord(): Result<String> = runCatching {
        val res = adapter.post("template/warnord", emptyMap(), emptyMap())
        (res.data?.get("content") as? String) ?: error("Missing markdown content")
    }

    suspend fun generateOpord(): Result<String> = runCatching {
        val res = adapter.post("template/opord", emptyMap(), emptyMap())
        (res.data?.get("content") as? String) ?: error("Missing markdown content")
    }

    suspend fun generateFrago(): Result<String> = runCatching {
        val res = adapter.post("template/frago", emptyMap(), emptyMap())
        (res.data?.get("content") as? String) ?: error("Missing markdown content")
    }
}


