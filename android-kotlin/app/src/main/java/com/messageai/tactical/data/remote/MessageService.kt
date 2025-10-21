/**
 * MessageAI – Firestore message operations.
 *
 * Thin service over Firestore for message-related CRUD and pagination. Uses
 * descending timestamp ordering and supports keyset pagination by startAfter
 * with the message's server timestamp. Ensure composite indexes exist for
 * queries used here.
 */
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
    /** Returns the message sub-collection for a given chat. */
    fun messageCollection(chatId: String) = firestore
        .collection(FirestorePaths.CHATS)
        .document(chatId)
        .collection(FirestorePaths.MESSAGES)

    /** Creates or replaces a message document by its deterministic id. */
    suspend fun sendMessage(doc: MessageDoc) {
        messageCollection(doc.chatId)
            .document(doc.id)
            .set(doc)
            .await()
    }

    /**
     * Pages messages newest-first with optional keyset pagination.
     *
     * @param chatId Chat identifier
     * @param pageSize Max items to fetch
     * @param startAfterTs Optional epoch millis to continue after
     */
    suspend fun pageMessages(chatId: String, pageSize: Int, startAfterTs: Long? = null): List<MessageDoc> {
        var query: Query = messageCollection(chatId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())
        if (startAfterTs != null) {
            // We page by timestamp value; ensuring index exists
            query = query.startAfter(com.google.firebase.Timestamp(startAfterTs / 1000, ((startAfterTs % 1000) * 1_000_000).toInt()))
        }
        val snaps = query.get().await()
        return snaps.documents.mapNotNull { it.toObject(MessageDoc::class.java) }
    }
}
