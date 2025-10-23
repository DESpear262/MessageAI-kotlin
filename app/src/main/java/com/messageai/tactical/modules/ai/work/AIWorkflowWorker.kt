package com.messageai.tactical.modules.ai.work

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
import androidx.work.workDataOf
import com.messageai.tactical.modules.ai.AIService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class AIWorkflowWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val aiService: AIService
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val endpoint = inputData.getString(KEY_ENDPOINT) ?: return Result.failure()
        val payload = inputData.keyValueMap
            .filterKeys { it.startsWith(KEY_ARG_PREFIX) }
            .mapKeys { it.key.removePrefix(KEY_ARG_PREFIX) }
        return try {
            aiService.runWorkflow(endpoint, payload).fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() }
            )
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_ARG_PREFIX = "arg_"

        fun enqueue(context: Context, endpoint: String, args: Map<String, Any?>) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        val basePairs = mutableListOf<Pair<String, Any?>>()
        basePairs.add(KEY_ENDPOINT to endpoint)
        args.forEach { (k, v) -> basePairs.add(KEY_ARG_PREFIX + k to v) }
        val input = workDataOf(*basePairs.toTypedArray())
            val req = OneTimeWorkRequestBuilder<AIWorkflowWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(input)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ai_${endpoint}", ExistingWorkPolicy.APPEND_OR_REPLACE, req
            )
        }
    }
}


