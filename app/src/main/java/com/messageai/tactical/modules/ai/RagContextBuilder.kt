package com.messageai.tactical.modules.ai

import com.messageai.tactical.data.db.MessageDao
import com.messageai.tactical.data.db.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds contextual windows from local Room messages for RAG.
 */
class RagContextBuilder(
    private val messageDao: MessageDao
) {
    data class WindowSpec(
        val chatId: String,
        val maxMessages: Int,
        val includeGeo: Boolean = true
    )

    suspend fun build(spec: WindowSpec): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val rows: List<MessageEntity> = messageDao.getLastMessages(spec.chatId, spec.maxMessages)
        rows.map { m ->
            mutableMapOf<String, Any?>(
                "id" to m.id,
                "senderId" to m.senderId,
                "timestamp" to m.timestamp,
                "text" to m.text
            )
        }
    }
}


