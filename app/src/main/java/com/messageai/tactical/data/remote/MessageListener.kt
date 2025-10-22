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

    fun start(chatId: String, scope: CoroutineScope) {
        stop()
        val col = firestore.collection(FirestorePaths.CHATS).document(chatId).collection(FirestorePaths.MESSAGES)
        registration = col.addSnapshotListener { snapshot, _ ->
            if (snapshot == null) return@addSnapshotListener
            scope.launch(Dispatchers.IO) {
                val docs = mutableListOf<MessageDoc>()
                for (dc in snapshot.documentChanges) {
                    when (dc.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            dc.document.toObject(MessageDoc::class.java)?.let { docs.add(it) }
                        }
                        DocumentChange.Type.REMOVED -> {}
                    }
                }
                if (docs.isEmpty()) return@launch
                // Write-through to Room
                val entities = docs.map { Mapper.messageDocToEntity(it) }
                db.messageDao().upsertAll(entities)

                // deliveredBy: mark delivery for messages I received (not authored by me)
                val myUid = auth.currentUser?.uid
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
