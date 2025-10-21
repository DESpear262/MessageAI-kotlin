package com.messageai.tactical.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadReceiptUpdater @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    fun markRead(chatId: String, fullyVisibleMessageIds: List<String>, scope: CoroutineScope) {
        val myUid = auth.currentUser?.uid ?: return
        if (fullyVisibleMessageIds.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val batch = firestore.batch()
            val col = firestore.collection(FirestorePaths.CHATS).document(chatId).collection(FirestorePaths.MESSAGES)
            fullyVisibleMessageIds.forEach { id ->
                batch.update(col.document(id), mapOf("readBy" to FieldValue.arrayUnion(myUid)))
            }
            batch.commit()
        }
    }
}
