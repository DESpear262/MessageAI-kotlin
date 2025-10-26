package com.messageai.tactical.modules.reporting

import com.messageai.tactical.modules.ai.api.AiRequestEnvelope
import com.messageai.tactical.modules.ai.provider.LangChainAdapter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ReportService(private val adapter: LangChainAdapter) {
    private val cache = mutableMapOf<CacheKey, CachedReport>()
    private val cacheMutex = Mutex()

    private data class CacheKey(
        val type: String,
        val chatId: String?,
        val params: String
    )

    private data class CachedReport(
        val markdown: String,
        val timestamp: Long,
        val ttlMs: Long
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean = (now - timestamp) > ttlMs
    }

    private companion object {
        private const val SITREP_TTL_MS = 5 * 60 * 1000L
        private const val TEMPLATE_TTL_MS = 30 * 60 * 1000L
    }

    suspend fun generateSITREP(chatId: String, timeWindow: String = "6h"): Result<String> = runCatching {
        val key = CacheKey("sitrep", chatId, timeWindow)
        cacheMutex.withLock {
            cache[key]?.let { if (!it.isExpired()) return@runCatching it.markdown else cache.remove(key) }
        }

        val payload = mapOf("timeWindow" to timeWindow)
        val ctx = mapOf("chatId" to chatId)
        val res = adapter.post("sitrep/summarize", payload, ctx)
        val md = (res.data?.get("content") as? String) ?: error("Missing markdown content")

        cacheMutex.withLock { cache[key] = CachedReport(md, System.currentTimeMillis(), SITREP_TTL_MS) }
        md
    }

    suspend fun generateWarnord(chatId: String?, prompt: String? = null, candidateChats: List<Map<String, Any?>> = emptyList()): Result<String> = runCatching {
        android.util.Log.i("ReportService", "generateWarnord start chatId=$chatId")
        val key = CacheKey("warnord", chatId, "")
        cacheMutex.withLock {
            cache[key]?.let { if (!it.isExpired()) return@runCatching it.markdown else cache.remove(key) }
        }
        val payload = buildMap<String, Any?> {
            if (!prompt.isNullOrBlank()) put("prompt", prompt)
            if (candidateChats.isNotEmpty()) put("candidateChats", candidateChats)
        }
        val ctx = if (!chatId.isNullOrBlank()) mapOf("chatId" to chatId) else emptyMap()
        val res = adapter.post("template/warnord", payload, ctx)
        val md = (res.data?.get("content") as? String) ?: error("Missing markdown content")
        cacheMutex.withLock { cache[key] = CachedReport(md, System.currentTimeMillis(), TEMPLATE_TTL_MS) }
        android.util.Log.i("ReportService", "generateWarnord success len=${md.length}")
        md
    }

    suspend fun generateOpord(chatId: String?, prompt: String? = null, candidateChats: List<Map<String, Any?>> = emptyList()): Result<String> = runCatching {
        android.util.Log.i("ReportService", "generateOpord start chatId=$chatId")
        val key = CacheKey("opord", chatId, "")
        cacheMutex.withLock {
            cache[key]?.let { if (!it.isExpired()) return@runCatching it.markdown else cache.remove(key) }
        }
        val payload = buildMap<String, Any?> {
            if (!prompt.isNullOrBlank()) put("prompt", prompt)
            if (candidateChats.isNotEmpty()) put("candidateChats", candidateChats)
        }
        val ctx = if (!chatId.isNullOrBlank()) mapOf("chatId" to chatId) else emptyMap()
        val res = adapter.post("template/opord", payload, ctx)
        val md = (res.data?.get("content") as? String) ?: error("Missing markdown content")
        cacheMutex.withLock { cache[key] = CachedReport(md, System.currentTimeMillis(), TEMPLATE_TTL_MS) }
        android.util.Log.i("ReportService", "generateOpord success len=${md.length}")
        md
    }

    suspend fun generateFrago(chatId: String?, prompt: String? = null, candidateChats: List<Map<String, Any?>> = emptyList()): Result<String> = runCatching {
        android.util.Log.i("ReportService", "generateFrago start chatId=$chatId")
        val key = CacheKey("frago", chatId, "")
        cacheMutex.withLock {
            cache[key]?.let { if (!it.isExpired()) return@runCatching it.markdown else cache.remove(key) }
        }
        val payload = buildMap<String, Any?> {
            if (!prompt.isNullOrBlank()) put("prompt", prompt)
            if (candidateChats.isNotEmpty()) put("candidateChats", candidateChats)
        }
        val ctx = if (!chatId.isNullOrBlank()) mapOf("chatId" to chatId) else emptyMap()
        val res = adapter.post("template/frago", payload, ctx)
        val md = (res.data?.get("content") as? String) ?: error("Missing markdown content")
        cacheMutex.withLock { cache[key] = CachedReport(md, System.currentTimeMillis(), TEMPLATE_TTL_MS) }
        android.util.Log.i("ReportService", "generateFrago success len=${md.length}")
        md
    }
}


