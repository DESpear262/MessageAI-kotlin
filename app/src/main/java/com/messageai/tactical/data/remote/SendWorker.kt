package com.messageai.tactical.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@HiltWorker
class SendWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val firestore: FirebaseFirestore
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
        val senderId = inputData.getString(KEY_SENDER_ID) ?: return Result.failure()
        val text = inputData.getString(KEY_TEXT)
        val clientTs = inputData.getLong(KEY_CLIENT_TS, System.currentTimeMillis())

        val doc = hashMapOf(
            "id" to messageId,
            "chatId" to chatId,
            "senderId" to senderId,
            "text" to text,
            "clientTimestamp" to clientTs,
            "status" to "SENT",
            "localOnly" to false,
            "timestamp" to FieldValue.serverTimestamp()
        )
        return try {
            firestore.collection(FirestorePaths.CHATS)
                .document(chatId)
                .collection(FirestorePaths.MESSAGES)
                .document(messageId)
                .set(doc, SetOptions.merge())
                .await()
            Result.success()
        } catch (e: Exception) {
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

        fun enqueue(context: Context, messageId: String, chatId: String, senderId: String, text: String?, clientTs: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val data = androidx.work.Data.Builder()
                .putString(KEY_MESSAGE_ID, messageId)
                .putString(KEY_CHAT_ID, chatId)
                .putString(KEY_SENDER_ID, senderId)
                .putString(KEY_TEXT, text)
                .putLong(KEY_CLIENT_TS, clientTs)
                .build()

            val request = OneTimeWorkRequestBuilder<SendWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.SECONDS)
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
