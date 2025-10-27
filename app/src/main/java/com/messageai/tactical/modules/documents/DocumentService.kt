package com.messageai.tactical.modules.documents

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import android.util.Log

/**
 * DocumentService – Firestore CRUD for generated documents.
 *
 * Collection layout: users/{uid}/documents/{docId}
 */
class DocumentService(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    private fun docsCol(uid: String) = db.collection("users").document(uid).collection("documents")

    suspend fun create(type: String, title: String, content: String, chatId: String? = null, format: String = "markdown", metadata: Map<String, Any?>? = null): String {
        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any?>(
            "type" to type,
            "title" to title,
            "content" to content,
            "format" to format,
            "chatId" to chatId,
            "ownerUid" to (auth.currentUser?.uid ?: ""),
            "createdAt" to now,
            "updatedAt" to now,
            "metadata" to metadata
        )
        val uid = auth.currentUser?.uid ?: ""
        if (uid.isBlank()) error("Not signed in")
        val ref = docsCol(uid).add(data).await()
        return ref.id
    }

    suspend fun update(docId: String, updates: Map<String, Any?>) {
        val uid = auth.currentUser?.uid ?: ""
        if (uid.isBlank()) return
        docsCol(uid).document(docId).update(updates).await()
    }

    fun observeByType(type: String): Flow<List<Pair<String, Document>>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            Log.w(TAG, "observeByType: no auth user; emitting empty list")
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val q: Query = docsCol(uid)
            .whereEqualTo("type", type)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(200)
        val reg = q.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.e(TAG, "observeByType error: ${err.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents?.map { d ->
                val doc = Document(
                    id = d.id,
                    type = d.getString("type") ?: "",
                    title = d.getString("title") ?: "",
                    content = d.getString("content") ?: "",
                    format = d.getString("format") ?: "markdown",
                    chatId = d.getString("chatId"),
                    ownerUid = d.getString("ownerUid") ?: "",
                    createdAt = (d.get("createdAt") as? Number)?.toLong() ?: 0L,
                    updatedAt = (d.get("updatedAt") as? Number)?.toLong() ?: 0L,
                    metadata = d.get("metadata") as? Map<String, Any?>
                )
                d.id to doc
            } ?: emptyList()
            Log.i(TAG, "observeByType emit type=${type} count=${list.size}")
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun delete(docId: String) {
        val uid = auth.currentUser?.uid ?: ""
        if (uid.isBlank()) return
        docsCol(uid).document(docId).delete().await()
    }

    companion object { private const val TAG = "DocumentService" }
}


