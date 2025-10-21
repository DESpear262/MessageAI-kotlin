package com.messageai.tactical.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.messageai.tactical.data.remote.model.ChatDoc
import com.messageai.tactical.data.remote.model.LastMessage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val chats = firestore.collection(FirestorePaths.CHATS)

    suspend fun ensureDirectChat(uidA: String, uidB: String): String {
        val chatId = FirestorePaths.directChatId(uidA, uidB)
        val docRef = chats.document(chatId)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val doc = ChatDoc(
                id = chatId,
                participants = listOf(uidA, uidB),
                participantDetails = null,
                lastMessage = null,
                metadata = null
            )
            docRef.set(doc).await()
        }
        // touch updatedAt
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
