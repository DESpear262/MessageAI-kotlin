package com.messageai.tactical.modules.documents

/**
 * MessageAI – Document models for generated outputs.
 *
 * These models represent user-owned generated documents (OPORD, WARNORD, FRAGO, SITREP, MEDEVAC).
 * Documents are stored in Firestore under users/{uid}/documents/{docId} to keep ownership clear.
 */

enum class DocumentType { OPORD, WARNORD, FRAGO, SITREP, MEDEVAC }

data class Document(
    val id: String = "",
    val type: String = "",
    val title: String = "",
    val content: String = "",
    val format: String = "markdown",
    val chatId: String? = null,
    val ownerUid: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val metadata: Map<String, Any?>? = null
)


