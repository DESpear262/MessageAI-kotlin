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

/**
 * CASEVAC background workflow coordinator.
 *
 * Steps:
 * 1) Generate 9-line MEDEVAC template from recent chat context (remote AI).
 * 2) Determine nearest facility using device location and FacilityService (local).
 * 3) Create/Update a CASEVAC mission and increment casualty count (Firestore via MissionService).
 * 4) Mark mission complete (MVP) and archive if completed.
 *
 * Logging: Emits structured logs per step using a stable `runId` and `chatId` so failures can
 * be correlated across retries. Also posts user notifications on start and completion.
 */
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
        val runId = java.util.UUID.randomUUID().toString()
        try {
            Log.i(TAG, json(
                "event" to "casevac_start",
                "runId" to runId,
                "chatId" to chatId,
                "attempt" to runAttemptCount
            ))
            com.messageai.tactical.notifications.CasevacNotifier.notifyStart(applicationContext, chatId)
            // 1) Generate 9-line (template) - optional stub
            val t0 = System.currentTimeMillis()
            val tpl = ai.generateTemplate(chatId, type = "MEDEVAC", maxMessages = 50)
            Log.d(TAG, json(
                "event" to "casevac_template",
                "runId" to runId,
                "chatId" to chatId,
                "latencyMs" to (System.currentTimeMillis() - t0),
                "ok" to tpl.isSuccess
            ))

            // 2) Determine nearest facility
            val loc = getLocationSafely()
            val lat = loc?.latitude ?: 0.0
            val lon = loc?.longitude ?: 0.0
            val facility = facilities.nearest(lat, lon)
            Log.d(TAG, json(
                "event" to "casevac_facility",
                "runId" to runId,
                "chatId" to chatId,
                "lat" to lat,
                "lon" to lon,
                "facility" to (facility?.name ?: "none")
            ))

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
            Log.d(TAG, json(
                "event" to "casevac_mission",
                "runId" to runId,
                "chatId" to chatId,
                "missionId" to createdId,
                "incremented" to true
            ))

            // 4) Mark complete immediately for MVP and archive
            missions.updateMission(createdId, mapOf("status" to "done"))
            missions.archiveIfCompleted(createdId)

            com.messageai.tactical.notifications.CasevacNotifier.notifyComplete(applicationContext, facility?.name)
            Log.i(TAG, json(
                "event" to "casevac_complete",
                "runId" to runId,
                "chatId" to chatId,
                "missionId" to createdId
            ))
            Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, json(
                "level" to "error",
                "event" to "casevac_permission_error",
                "runId" to runId,
                "chatId" to chatId,
                "message" to (e.message ?: "SecurityException")
            ))
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, json(
                "level" to "error",
                "event" to "casevac_failed",
                "runId" to runId,
                "chatId" to chatId,
                "attempt" to runAttemptCount,
                "message" to (e.message ?: e::class.java.simpleName)
            ))
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
        try {
            lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) {} }
                .addOnFailureListener { cont.resume(null) {} }
        } catch (_: SecurityException) {
            cont.resume(null) {}
        }
    }

private fun json(vararg pairs: Pair<String, Any?>): String {
    // Minimal JSON string builder for structured Logcat scanning without extra deps.
    return buildString {
        append('{')
        pairs.forEachIndexed { index, (k, v) ->
            if (index > 0) append(',')
            append('"').append(k).append('"').append(':')
            when (v) {
                null -> append("null")
                is Number, is Boolean -> append(v.toString())
                else -> {
                    val s = v.toString().replace("\\", "\\\\").replace("\"", "\\\"")
                    append('"').append(s).append('"')
                }
            }
        }
        append('}')
    }
}


