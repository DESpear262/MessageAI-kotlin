/**
 * MessageAI – Real-time listener bridging Firestore to Room.
 *
 * Listens for message document changes within a chat and writes-through to the
 * local database. Also performs best-effort delivery acknowledgements by adding
 * the current user's UID to `deliveredBy` for received messages.
 */
package com.messageai.tactical.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.data.remote.model.MessageDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageListener @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val db: AppDatabase
) {
    private var registration: ListenerRegistration? = null
    private var onDataChanged: (() -> Unit)? = null
    
    fun setOnDataChanged(callback: () -> Unit) {
        onDataChanged = callback
    }

    fun start(chatId: String, scope: CoroutineScope) {
        stop()
        android.util.Log.d("MessageListener", "Starting listener for chat $chatId")
        val col = firestore.collection(FirestorePaths.CHATS).document(chatId).collection(FirestorePaths.MESSAGES)
        registration = col.addSnapshotListener { snapshot, error ->
            if (error != null) {
                android.util.Log.e("MessageListener", "Firestore listener error for chat $chatId", error)
                return@addSnapshotListener
            }
            if (snapshot == null) {
                android.util.Log.w("MessageListener", "Snapshot is null for chat $chatId")
                return@addSnapshotListener
            }
            android.util.Log.d("MessageListener", "Received snapshot for chat $chatId with ${snapshot.documents.size} total documents, ${snapshot.documentChanges.size} changes")
            scope.launch(Dispatchers.IO) {
                val docs = mutableListOf<MessageDoc>()
                for (dc in snapshot.documentChanges) {
                    when (dc.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            dc.document.toObject(MessageDoc::class.java)?.let { 
                                android.util.Log.d("MessageListener", "Processing ${dc.type} message: ${it.id}")
                                docs.add(it) 
                            }
                        }
                        DocumentChange.Type.REMOVED -> {
                            android.util.Log.d("MessageListener", "Message removed: ${dc.document.id}")
                        }
                    }
                }
                if (docs.isEmpty()) {
                    android.util.Log.d("MessageListener", "No documents to process")
                    return@launch
                }
                // Write-through to Room
                val entities = docs.map { Mapper.messageDocToEntity(it) }
                android.util.Log.d("MessageListener", "Upserting ${entities.size} messages to Room")
                db.messageDao().upsertAll(entities)
                
                // Update unread count for this chat - query ALL messages, not just snapshot
                val myUid = auth.currentUser?.uid
                if (myUid != null) {
                    // Get ALL messages for this chat from Room
                    val allMessages = db.messageDao().getAllMessagesForChat(chatId)
                    val unreadMessages = allMessages.filter { entity ->
                        entity.senderId != myUid && run {
                            // Parse readBy JSON array
                            val readByList = try {
                                kotlinx.serialization.json.Json.decodeFromString<List<String>>(entity.readBy)
                            } catch (_: Exception) { emptyList() }
                            !readByList.contains(myUid)
                        }
                    }
                    android.util.Log.d("MessageListener", "Unread count for chat $chatId: ${unreadMessages.size} (${allMessages.size} total messages in chat)")
                    db.chatDao().updateUnread(chatId, unreadMessages.size)
                }
                
                // Notify UI to refresh
                android.util.Log.d("MessageListener", "Notifying UI of data change")
                onDataChanged?.invoke()

                // deliveredBy: mark delivery for messages I received (not authored by me)
                if (myUid != null) {
                    docs.filter { it.senderId != myUid && !it.deliveredBy.contains(myUid) }.forEach { m ->
                        col.document(m.id).update("deliveredBy", FieldValue.arrayUnion(myUid))
                    }
                }
            }
        }
    }

    fun stop() {
        registration?.remove()
        registration = null
    }
}
