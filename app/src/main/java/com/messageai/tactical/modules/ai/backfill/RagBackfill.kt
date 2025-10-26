package com.messageai.tactical.modules.ai.backfill

/**
 * RAG Backfill Helper (isolated)
 * --------------------------------
 * Purpose: Retroactively warm embeddings in the LangChain service for all chats a user participates in.
 * Usage: Not wired to UI. Call RagBackfill.run(context) from a one-off admin/debug action only.
 * Behavior:
 *  - Enumerates chats for the current user via Firestore
 *  - Calls Cloud Functions proxy to LangChain `/rag/warm` for each chat
 *  - Server chunks texts, creates embeddings, and stores them under `chats/{id}/messages/{id}/chunks/{seq}`
 */
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object RagBackfill {
    fun run(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val auth = FirebaseAuth.getInstance()
                val uid = auth.currentUser?.uid ?: return@launch
                val chatsSnap = db.collection("chats").whereArrayContains("participants", uid).get().await()
                val chats = chatsSnap.documents.map { it.id }
                for (chatId in chats) {
                    try {
                        val url = java.net.URL("${com.messageai.tactical.BuildConfig.CF_BASE_URL}v1/rag/warm")
                        val conn = (url.openConnection() as java.net.HttpURLConnection)
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        // Attach Firebase ID token for auth; ignore if unavailable
                        try {
                            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
                        } catch (_: Exception) { }
                        conn.doOutput = true
                        val body = """{"requestId":"${java.util.UUID.randomUUID()}","context":{"chatId":"$chatId"},"payload":{"limit":200}}"""
                        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                        try { conn.inputStream.bufferedReader().use { it.readText() } } catch (_: Exception) {}
                        conn.disconnect()
                    } catch (_: Exception) { }
                }
            } catch (_: Exception) {
            }
        }
    }
}


