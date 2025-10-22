/**
 * MessageAI – Chat Firestore operations.
 *
 * Handles chat creation for direct conversations and updating lastMessage
 * metadata for the chat list. Ensures deterministic 1:1 chat IDs.
 */
package com.messageai.tactical.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.messageai.tactical.data.remote.model.ChatDoc
import com.messageai.tactical.data.remote.model.LastMessage
import com.messageai.tactical.data.remote.model.ParticipantInfo
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val chats = firestore.collection(FirestorePaths.CHATS)

    suspend fun ensureDirectChat(myUid: String, otherUid: String, myName: String, otherName: String): String {
        val chatId = FirestorePaths.directChatId(myUid, otherUid)
        val docRef = chats.document(chatId)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val participantDetails = mapOf(
                myUid to ParticipantInfo(name = myName, photoUrl = null),
                otherUid to ParticipantInfo(name = if (myUid == otherUid) "Note to self" else otherName, photoUrl = null)
            )
            val doc = ChatDoc(
                id = chatId,
                participants = listOf(myUid, otherUid),
                participantDetails = participantDetails,
                lastMessage = null,
                metadata = null
            )
            docRef.set(doc).await()
        }
        docRef.update("updatedAt", FieldValue.serverTimestamp()).await()
        return chatId
    }

    suspend fun updateLastMessage(chatId: String, text: String?, imageUrl: String?, senderId: String) {
        val last = LastMessage(text = text, imageUrl = imageUrl, senderId = senderId)
        chats.document(chatId).update(
            mapOf(
                "lastMessage" to last,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
