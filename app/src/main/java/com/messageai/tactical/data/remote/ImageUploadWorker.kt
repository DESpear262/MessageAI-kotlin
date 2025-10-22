package com.messageai.tactical.data.remote

import android.content.Context
import android.net.Uri
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
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.data.media.ImageService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

@HiltWorker
class ImageUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val imageService: ImageService,
    private val firestore: FirebaseFirestore,
    private val db: AppDatabase
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
        val localPath = inputData.getString(KEY_IMAGE_LOCAL_PATH) ?: return Result.failure()
        val senderId = inputData.getString(KEY_SENDER_ID) ?: return Result.failure()

        return try {
            val uri = Uri.fromFile(java.io.File(localPath))
            val downloadUrl = imageService.processAndUpload(chatId, messageId, senderId, uri)

            val col = firestore.collection(FirestorePaths.CHATS).document(chatId)
                .collection(FirestorePaths.MESSAGES)
            col.document(messageId)
                .update(mapOf(
                    "imageUrl" to downloadUrl,
                    "status" to "SENT",
                    "timestamp" to FieldValue.serverTimestamp()
                ))
                .await()

            // Update lastMessage on chat
            val last = hashMapOf(
                "imageUrl" to downloadUrl,
                "text" to null,
                "senderId" to senderId,
                "timestamp" to FieldValue.serverTimestamp()
            )
            firestore.collection(FirestorePaths.CHATS).document(chatId)
                .update(mapOf("lastMessage" to last, "updatedAt" to FieldValue.serverTimestamp()))
                .await()

            // Patch local entity
            db.messageDao().updateImageAndStatus(messageId, downloadUrl, "SENT")

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PREFIX = "upload-"
        const val KEY_MESSAGE_ID = "messageId"
        const val KEY_CHAT_ID = "chatId"
        const val KEY_IMAGE_LOCAL_PATH = "imageLocalPath"
        const val KEY_SENDER_ID = "senderId"

        fun enqueue(context: Context, messageId: String, chatId: String, senderId: String, localPath: String) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val data = androidx.work.Data.Builder()
                .putString(KEY_MESSAGE_ID, messageId)
                .putString(KEY_CHAT_ID, chatId)
                .putString(KEY_IMAGE_LOCAL_PATH, localPath)
                .putString(KEY_SENDER_ID, senderId)
                .build()

            val request = OneTimeWorkRequestBuilder<ImageUploadWorker>()
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


