package com.messageai.tactical.modules.ai.backfill

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.messageai.tactical.modules.ai.AIService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Executes assistant/route with full context after the gate says to escalate.
 */
@HiltWorker
class RouteExecutor @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ai: AIService
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val chatId = inputData.getString(KEY_CHAT_ID)
        val text = inputData.getString(KEY_TEXT) ?: return Result.failure()
        return try {
            ai.routeAssistant(chatId, text)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_CHAT_ID = "chatId"
        private const val KEY_TEXT = "text"

        fun enqueue(context: Context, chatId: String?, messageId: String, text: String) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val data = androidx.work.workDataOf(KEY_CHAT_ID to chatId, KEY_TEXT to text)
            val req = OneTimeWorkRequestBuilder<RouteExecutor>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("route-" + messageId, ExistingWorkPolicy.REPLACE, req)
        }
    }
}


