/**
 * MessageAI – Chat Firestore operations.
 *
 * Handles chat creation for direct conversations and updating lastMessage
 * metadata for the chat list. Ensures deterministic 1:1 chat IDs.
 */
package com.messageai.tactical.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.messageai.tactical.data.db.ChatDao
import com.messageai.tactical.data.remote.model.ChatDoc
import com.messageai.tactical.data.remote.model.LastMessage
import com.messageai.tactical.data.remote.model.ParticipantInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatService @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val chatDao: ChatDao
) {
    private val chats = firestore.collection(FirestorePaths.CHATS)
    private var reg: ListenerRegistration? = null

    suspend fun ensureDirectChat(myUid: String, otherUid: String, myName: String, otherName: String): String {
        val chatId = FirestorePaths.directChatId(myUid, otherUid)
        val docRef = chats.document(chatId)
        val uniqueParticipants = if (myUid == otherUid) listOf(myUid) else listOf(myUid, otherUid)
        val participantDetails = if (myUid == otherUid) {
            mapOf(myUid to ParticipantInfo(name = "Note to self", photoUrl = null))
        } else {
            mapOf(
                myUid to ParticipantInfo(name = myName, photoUrl = null),
                otherUid to ParticipantInfo(name = otherName, photoUrl = null)
            )
        }
        val doc = ChatDoc(
            id = chatId,
            participants = uniqueParticipants,
            participantDetails = participantDetails,
            lastMessage = null,
            metadata = null
        )
        docRef.set(doc, SetOptions.merge()).await()
        docRef.update("updatedAt", FieldValue.serverTimestamp()).await()
        return chatId
    }

    /** Creates a group chat with the given members and optional name. */
    suspend fun createGroupChat(name: String?, memberUids: List<String>): String {
        val me = auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")
        val unique = memberUids.distinct()
        require(unique.size >= 3) { "Group requires at least 3 members" }
        val docRef = chats.document() // random UUID id
        val participantDetails = unique.associateWith { uid ->
            // Best-effort: for myself, use displayName/email; others unknown for now
            if (uid == me) ParticipantInfo(name = auth.currentUser?.displayName ?: (auth.currentUser?.email ?: "Me"), photoUrl = null)
            else ParticipantInfo(name = "", photoUrl = null)
        }
        val doc = ChatDoc(
            id = docRef.id,
            name = name,
            participants = unique,
            participantDetails = participantDetails,
            lastMessage = null,
            metadata = null
        )
        docRef.set(doc, SetOptions.merge()).await()
        docRef.update("updatedAt", FieldValue.serverTimestamp()).await()
        return docRef.id
    }

    /** Renames a chat; any member may rename during MVP. */
    suspend fun renameChat(chatId: String, newName: String?) {
        chats.document(chatId).update(
            mapOf(
                "name" to newName,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun subscribeMyChats(scope: CoroutineScope) {
        reg?.remove()
        val me = auth.currentUser?.uid ?: return
        reg = chats.whereArrayContains("participants", me).addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            scope.launch(Dispatchers.IO) {
                val entities = snap.documents.mapNotNull { it.toObject(ChatDoc::class.java) }
                    .map { Mapper.chatDocToEntityForUser(it, me) }
                chatDao.upsertAll(entities)
            }
        }
    }

    fun stop() {
        reg?.remove()
        reg = null
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
