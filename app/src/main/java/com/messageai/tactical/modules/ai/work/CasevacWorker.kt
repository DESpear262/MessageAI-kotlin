package com.messageai.tactical.modules.ai.work

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.messageai.tactical.modules.ai.AIService
import com.messageai.tactical.modules.facility.FacilityService
import com.messageai.tactical.modules.missions.Mission
import com.messageai.tactical.modules.missions.MissionService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class CasevacWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val ai: AIService,
    private val missions: MissionService,
    private val facilities: FacilityService
) : CoroutineWorker(appContext, params) {

    private val fused: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val chatId = inputData.getString(KEY_CHAT_ID) ?: return@withContext Result.failure()
        val messageId = inputData.getString(KEY_MESSAGE_ID)
        try {
            Log.i(TAG, "CASEVAC start chatId=$chatId runAttempt=$runAttemptCount")
            com.messageai.tactical.notifications.CasevacNotifier.notifyStart(applicationContext, chatId)
            // 1) Generate 9-line (template) - optional stub
            val t0 = System.currentTimeMillis()
            val tpl = ai.generateTemplate(chatId, type = "MEDEVAC", maxMessages = 50)
            Log.d(TAG, "template ms=${System.currentTimeMillis() - t0} ok=${tpl.isSuccess}")

            // 2) Determine nearest facility
            val loc = getLocationSafely()
            val lat = loc?.latitude ?: 0.0
            val lon = loc?.longitude ?: 0.0
            val facility = facilities.nearest(lat, lon)

            // 3) Update existing mission's casualty count; if none, create one
            val createdId = missions.createMission(
                Mission(
                    chatId = chatId,
                    title = "CASEVAC",
                    description = facility?.name ?: "Nearest facility located",
                    status = "in_progress",
                    priority = 5,
                    assignees = emptyList(),
                    sourceMsgId = messageId,
                    casevacCasualties = 0
                )
            )
            // Try to increment existing; if newly created is the only one, this will increment that record
            missions.incrementCasevacCasualties(chatId, delta = 1)

            // 4) Mark complete immediately for MVP and archive
            missions.updateMission(createdId, mapOf("status" to "done"))
            missions.archiveIfCompleted(createdId)

            com.messageai.tactical.notifications.CasevacNotifier.notifyComplete(applicationContext, facility?.name)
            Log.i(TAG, "CASEVAC complete chatId=$chatId missionId=$createdId")
            Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, "CASEVAC permission error", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "CASEVAC failed chatId=$chatId", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CasevacWorker"
        private const val KEY_CHAT_ID = "chatId"
        private const val KEY_MESSAGE_ID = "messageId"

        fun enqueue(context: Context, chatId: String, messageId: String?) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val input = workDataOf(KEY_CHAT_ID to chatId, KEY_MESSAGE_ID to messageId)
            val req = OneTimeWorkRequestBuilder<CasevacWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(input)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("casevac_$chatId", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}

private fun CasevacWorker.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
        applicationContext,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private suspend fun CasevacWorker.getLocationSafely(): android.location.Location? {
    if (!hasLocationPermission()) {
        Log.w("CasevacWorker", "Location permission not granted; using fallback (0,0)")
        return null
    }
    return try { this.awaitLocationNullable() } catch (e: SecurityException) {
        Log.e("CasevacWorker", "Location SecurityException", e)
        null
    }
}

private suspend fun CasevacWorker.awaitLocationNullable(): android.location.Location? {
    return (LocationServices.getFusedLocationProviderClient(applicationContext)).awaitNullable()
}

private suspend fun FusedLocationProviderClient.awaitNullable(): android.location.Location? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        lastLocation.addOnSuccessListener { loc -> cont.resume(loc) {} }
            .addOnFailureListener { cont.resume(null) {} }
    }


