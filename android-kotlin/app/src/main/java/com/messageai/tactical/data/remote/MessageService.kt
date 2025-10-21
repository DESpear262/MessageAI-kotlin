package com.messageai.tactical.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.messageai.tactical.data.remote.model.MessageDoc
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun messageCollection(chatId: String) = firestore
        .collection(FirestorePaths.CHATS)
        .document(chatId)
        .collection(FirestorePaths.MESSAGES)

    suspend fun sendMessage(doc: MessageDoc) {
        messageCollection(doc.chatId)
            .document(doc.id)
            .set(doc)
            .await()
    }

    suspend fun pageMessages(chatId: String, pageSize: Int, startAfterTs: Long? = null): List<MessageDoc> {
        var query: Query = messageCollection(chatId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
        // For offline sends where timestamp may be null, fallback to clientTimestamp ordering
        // Secondary ordering requires composite index; keep primary on timestamp
        val snaps = query.get().await()
        return snaps.documents.mapNotNull { it.toObject(MessageDoc::class.java) }
    }
}
