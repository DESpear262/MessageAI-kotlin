/**
 * MessageAI – ThreatAnalyzeWorker
 *
 * Background worker that invokes the Geo/AI pipeline to extract and persist threats
 * from recent messages in a specific chat. This decouples UI sends from AI work and
 * ensures analysis happens even if the app is backgrounded shortly after send.
 */
package com.messageai.tactical.data.remote

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.messageai.tactical.util.WorkerHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@Deprecated("Replaced by assistant/gate proactive path")
@HiltWorker
class ThreatAnalyzeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val geo: com.messageai.tactical.modules.geo.GeoService
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return Result.failure()
        val maxMessages = inputData.getInt(KEY_MAX_MESSAGES, 100)
        return try {
            val res = geo.analyzeChatThreats(chatId, maxMessages)
            if (res.isSuccess) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_PREFIX = "threat-analyze-"
        const val KEY_CHAT_ID = "chatId"
        const val KEY_MAX_MESSAGES = "maxMessages"

        /** Enqueue a best-effort single-run analysis for the given chat. */
        fun enqueue(context: Context, chatId: String, maxMessages: Int = 100) {
            val data = androidx.work.Data.Builder()
                .putString(KEY_CHAT_ID, chatId)
                .putInt(KEY_MAX_MESSAGES, maxMessages)
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<ThreatAnalyzeWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    WorkerHelper.standardBackoffPolicy(),
                    WorkerHelper.BACKOFF_DELAY_SECONDS,
                    WorkerHelper.BACKOFF_TIME_UNIT
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_PREFIX + chatId, ExistingWorkPolicy.REPLACE, req)
        }
    }
}


