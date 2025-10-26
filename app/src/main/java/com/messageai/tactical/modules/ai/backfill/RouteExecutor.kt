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
            val decision = ai.routeAssistant(chatId, text).getOrElse { emptyMap() }
            val raw = decision["decision"]?.toString() ?: "{}"
            val obj = try { org.json.JSONObject(raw) } catch (_: Exception) { org.json.JSONObject("{}") }
            val tool = obj.optString("tool", "none")
            val args = obj.optJSONObject("args") ?: org.json.JSONObject()

            when (tool) {
                // Run threats extraction strictly on this triggering message
                "threats/extract" -> {
                    val loc = getLastKnownLocationSafe()
                    val threats = ai.extractThreatsFromMessage(
                        chatId = chatId,
                        messageId = inputData.getString(KEY_MESSAGE_ID),
                        text = text,
                        currentLat = loc?.first,
                        currentLon = loc?.second
                    ).getOrElse { emptyList() }
                    // Persist threats immediately to Firestore
                    persistThreats(chatId, threats, loc)
                }
                // CASEVAC remote-first; fall back to local worker if remote fails
                "workflow/casevac/run" -> {
                    val ok = ai.runCasevacRemote(chatId, emptyMap()).isSuccess
                    if (!ok && !chatId.isNullOrBlank()) {
                        com.messageai.tactical.modules.ai.work.CasevacWorker.enqueue(applicationContext, chatId, inputData.getString(KEY_MESSAGE_ID))
                    }
                }
                // For other tools, do nothing here; existing modules will execute with their own context
                else -> { /* no-op */ }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val KEY_CHAT_ID = "chatId"
        private const val KEY_TEXT = "text"
        private const val KEY_MESSAGE_ID = "messageId"

        fun enqueue(context: Context, chatId: String?, messageId: String, text: String) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val data = androidx.work.workDataOf(KEY_CHAT_ID to chatId, KEY_TEXT to text, KEY_MESSAGE_ID to messageId)
            val req = OneTimeWorkRequestBuilder<RouteExecutor>()
                .setConstraints(constraints)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("route-" + messageId, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

private fun RouteExecutor.getLastKnownLocationSafe(): Pair<Double, Double>? {
    return try {
        val fused = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(applicationContext)
        val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
            applicationContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
            applicationContext,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null
        val task = fused.lastLocation
        val loc = com.google.android.gms.tasks.Tasks.await(task)
        if (loc != null) (loc.latitude to loc.longitude) else null
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun RouteExecutor.persistThreats(
    chatId: String?,
    threats: List<Map<String, Any?>>,
    fallbackLoc: Pair<Double, Double>?
) {
    try {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val now = com.google.firebase.Timestamp.now()
        var saved = 0
        threats.forEach { th ->
            val summary = th["summary"]?.toString()?.takeIf { it.isNotBlank() } ?: return@forEach
            val severity = (th["severity"] as? Number)?.toInt() ?: 3
            val confidence = (th["confidence"] as? Number)?.toDouble() ?: 0.6
            val radiusM = (th["radiusM"] as? Number)?.toInt() ?: 500
            val tags = (th["tags"] as? List<*>)?.mapNotNull { it?.toString() }
            val sourceMsgId = th["sourceMsgId"]?.toString()
            val sourceMsgText = th["sourceMsgText"]?.toString()

            var lat: Double? = null
            var lon: Double? = null
            val abs = th["abs"] as? Map<*, *>
            val geo = th["geo"] as? Map<*, *>
            val off = th["offset"] as? Map<*, *>
            lat = (abs?.get("lat") as? Number)?.toDouble() ?: (geo?.get("lat") as? Number)?.toDouble()
            lon = (abs?.get("lon") as? Number)?.toDouble() ?: (geo?.get("lon") as? Number)?.toDouble()
            if (lat == null || lon == null) {
                // No absolute coordinate; use fallback location if available
                if (fallbackLoc != null) {
                    lat = fallbackLoc.first
                    lon = fallbackLoc.second
                }
            }

            val doc = hashMapOf(
                "summary" to summary,
                "severity" to severity,
                "confidence" to confidence,
                "radiusM" to radiusM,
                "geo" to if (lat != null && lon != null) mapOf("lat" to lat!!, "lon" to lon!!) else null,
                "tags" to tags,
                "source" to mapOf(
                    "chatId" to (chatId ?: ""),
                    "messageId" to (sourceMsgId ?: ""),
                    "summary" to (sourceMsgText ?: "")
                ),
                "ts" to now
            ).filterValues { it != null }

            db.collection("threats").add(doc)
            saved += 1
        }
        android.util.Log.i("RouteExecutor", "persistThreats saved=$saved for chatId=${chatId ?: ""}")
    } catch (e: Exception) {
        android.util.Log.w("RouteExecutor", "persistThreats error: ${e.message}")
    }
}


