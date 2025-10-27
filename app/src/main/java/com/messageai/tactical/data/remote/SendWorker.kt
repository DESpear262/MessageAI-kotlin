/**
 * MessageAI – SendWorker for background message sending.
 *
 * Handles uploading message documents to Firestore with retry logic and backoff.
 * Updates local Room database on successful send. Part of the offline-first
 * architecture, ensuring messages are eventually delivered even after app restarts.
 */
package com.messageai.tactical.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.util.WorkerHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

@HiltWorker
class SendWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val firestore: FirebaseFirestore,
    private val db: AppDatabase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
        val senderId = inputData.getString(KEY_SENDER_ID) ?: return Result.failure()
        val text = inputData.getString(KEY_TEXT)
        val clientTs = inputData.getLong(KEY_CLIENT_TS, System.currentTimeMillis())
        val imageLocalPath = inputData.getString(KEY_IMAGE_LOCAL_PATH)

        val doc = hashMapOf(
            "id" to messageId,
            "chatId" to chatId,
            "senderId" to senderId,
            "text" to text,
            "clientTimestamp" to clientTs,
            "status" to (if (imageLocalPath != null) "SENDING" else "SENT"),
            "readBy" to emptyList<String>(),
            "deliveredBy" to emptyList<String>(),
            "localOnly" to false,
            "timestamp" to FieldValue.serverTimestamp(),
            "metadata" to null
        )
        return try {
            firestore.collection(FirestorePaths.CHATS)
                .document(chatId)
                .collection(FirestorePaths.MESSAGES)
                .document(messageId)
                .set(doc, SetOptions.merge())
                .await()
            // Gate each human text message for escalation using cheap LLM
            if (!text.isNullOrBlank() && senderId != com.messageai.tactical.ui.main.aibuddy.AIBuddyRouter.AI_UID) {
                try {
                    // Call CF proxy to gate; on escalate=true, trigger assistant/route flow asynchronously
                    val gate = gateAssistant(text)
                    android.util.Log.i("SendWorker", "assistant/gate escalate=${gate}")
                    if (gate) {
                        com.messageai.tactical.modules.ai.backfill.RouteExecutor.enqueue(applicationContext, chatId, messageId, text)
                    }
                } catch (_: Exception) {
                    // No-op on error per spec
                }
            }
            // Update lastMessage metadata
            val last = hashMapOf(
                "text" to text,
                "senderId" to senderId,
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(FirestorePaths.CHATS).document(chatId)
                .update(mapOf("lastMessage" to last, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            // Mark local row as SENT and synced if no image upload is pending
            if (imageLocalPath == null) {
                db.messageDao().updateStatusSynced(messageId, "SENT", true)
            }
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("SendWorker", "Failed to send message $messageId to chat $chatId", e)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PREFIX = "send-"
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_CHAT_ID = "chatId"
        const val KEY_SENDER_ID = "senderId"
        const val KEY_TEXT = "text"
        const val KEY_CLIENT_TS = "clientTs"
        const val KEY_IMAGE_LOCAL_PATH = "imageLocalPath"

        fun enqueue(context: Context, messageId: String, chatId: String, senderId: String, text: String?, clientTs: Long, imageLocalPath: String? = null) {
            val data = androidx.work.Data.Builder()
                .putString(KEY_MESSAGE_ID, messageId)
                .putString(KEY_CHAT_ID, chatId)
                .putString(KEY_SENDER_ID, senderId)
                .putString(KEY_TEXT, text)
                .putLong(KEY_CLIENT_TS, clientTs)
                .putString(KEY_IMAGE_LOCAL_PATH, imageLocalPath)
                .build()

            val request = OneTimeWorkRequestBuilder<SendWorker>()
                .setConstraints(WorkerHelper.standardConstraints())
                .setBackoffCriteria(
                    WorkerHelper.standardBackoffPolicy(),
                    WorkerHelper.BACKOFF_DELAY_SECONDS,
                    WorkerHelper.BACKOFF_TIME_UNIT
                )
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PREFIX + messageId,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

private suspend fun SendWorker.gateAssistant(prompt: String): Boolean {
    // Minimal Retrofit-free call: use OkHttp since it's already a transitive dependency
    return try {
        val base = com.messageai.tactical.BuildConfig.CF_BASE_URL
        val url = java.net.URL(base + "assistant/gate")
        val body = ("{" + "\"requestId\":\"" + java.util.UUID.randomUUID().toString() + "\"," +
            "\"context\":{}," +
            "\"payload\":{\"prompt\":\"" + prompt.replace("\"", "\\\"") + "\"}" + "}").toByteArray()
        val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            // Best-effort auth: ID token header if available
            try {
                val token = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                if (!token.isNullOrEmpty()) setRequestProperty("Authorization", "Bearer $token")
            } catch (_: Exception) {}
        }
        conn.outputStream.use { it.write(body) }
        val resp = conn.inputStream.bufferedReader().use { it.readText() }
        val obj = org.json.JSONObject(resp)
        val data = obj.optJSONObject("data")
        data?.optBoolean("escalate", false) ?: false
    } catch (_: Exception) {
        false
    }
}
