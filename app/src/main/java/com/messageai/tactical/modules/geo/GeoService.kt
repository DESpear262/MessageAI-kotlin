package com.messageai.tactical.modules.geo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.messageai.tactical.modules.ai.AIService

/**
 * GeoService: presence monitoring, signal-loss alerts, geofence checks, and threat summarization.
 * - Presence: fire signal loss after 2 missed heartbeats (~30s x2)
 * - Geofences: alert on entering threat radius; summarize button can re-alert all live threats
 * - Threats: global collection; entries expire after ~8h
 */
class GeoService(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val aiService: AIService
) {
    private val alertsChannelId = "alerts_channel"

    init {
        ensureChannel()
    }

    private val fused: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    fun monitorPresence(/* implementation hook for heartbeat source */) {
        // Placeholder: presence is app-level; network layer should report connectivity heartbeat
        // This service exposes a simple helper for alerting when missed twice.
    }

    /**
     * Analyze recent chat messages with AI and persist extracted threats to Firestore.
     * Falls back to device location for centerpoint when AI omits geo.
     */
    @android.annotation.SuppressLint("MissingPermission")
    suspend fun analyzeChatThreats(chatId: String, maxMessages: Int = 10): Result<Int> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                val loc = runCatching { fused.lastLocation.await() }.getOrNull()
                val fallbackLat = loc?.latitude
                val fallbackLon = loc?.longitude
                val result = aiService.summarizeThreats(chatId, maxMessages, fallbackLat, fallbackLon)
                val items: List<Map<String, Any?>> = result.getOrThrow()
                var saved = 0
                items.forEach { threatMap ->
                    val summary = threatMap["summary"]?.toString() ?: return@forEach
                    val severity = (threatMap["severity"] as? Number)?.toInt() ?: 3
                    val radiusM = (threatMap["radiusM"] as? Number)?.toInt() ?: DEFAULT_RADIUS_M
                    val tags = (threatMap["tags"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                    // Absolute or offset location from backend
                    val positionMode = threatMap["positionMode"]?.toString() ?: "offset"
                    val abs = threatMap["abs"] as? Map<*, *>
                    val off = threatMap["offset"] as? Map<*, *>

                    val latLon: Pair<Double?, Double?> = when (positionMode) {
                        "absolute" -> {
                            val la = (abs?.get("lat") as? Number)?.toDouble()
                            val lo = (abs?.get("lon") as? Number)?.toDouble()
                            la to lo
                        }
                        else -> {
                            val north = (off?.get("north") as? Number)?.toDouble() ?: 0.0
                            val east = (off?.get("east") as? Number)?.toDouble() ?: 0.0
                            if (fallbackLat != null && fallbackLon != null) {
                                metersOffsetToLatLonCalc(fallbackLat, fallbackLon, north, east)
                            } else {
                                null to null
                            }
                        }
                    }

                    val lat = latLon.first ?: fallbackLat
                    val lon = latLon.second ?: fallbackLon
                    val data = hashMapOf(
                        "summary" to summary,
                        "severity" to severity,
                        "confidence" to ((threatMap["confidence"] as? Number)?.toDouble() ?: 0.75),
                        "geo" to if (lat != null && lon != null) mapOf("lat" to lat, "lon" to lon) else null,
                        "radiusM" to radiusM,
                        "tags" to tags,
                        "source" to mapOf(
                            "chatId" to chatId,
                            "messageId" to (threatMap["sourceMsgId"]?.toString() ?: "")
                        ),
                        "ts" to Timestamp.now()
                    ).filterValues { it != null }
                    firestore.collection(THREATS_COLLECTION).add(data).await()
                    saved += 1
                }
                saved
            }
        }

    fun alertSignalLossIfNeeded(consecutiveMisses: Int) {
        if (consecutiveMisses >= 2) {
            showAlert("Signal loss detected", "No connectivity for > 60s")
        }
    }

    fun summarizeThreatsNear(latitude: Double, longitude: Double, maxMiles: Double = 500.0, limit: Int = 50, onAlert: (Threat) -> Unit) {
        // Pull threats (global) from Firestore, filter recent (< 8h), within radius, sort by severity/recency, take top N
        firestore.collection(THREATS_COLLECTION)
            .orderBy("ts")
            .limit(500)
            .get()
            .addOnSuccessListener { snap ->
                val nowMs = System.currentTimeMillis()
                val threats = snap.documents.mapNotNull { doc ->
                    val lat = (doc.get("geo.lat") as? Number)?.toDouble()
                    val lon = (doc.get("geo.lon") as? Number)?.toDouble()
                    val radiusM = (doc.get("radiusM") as? Number)?.toInt() ?: DEFAULT_RADIUS_M
                    val ts = (doc.get("ts") as? Timestamp)?.toDate()?.time ?: 0L
                    val summary = doc.getString("summary") ?: return@mapNotNull null
                    val severity = (doc.get("severity") as? Number)?.toInt() ?: 3
                    if (lat == null || lon == null) return@mapNotNull null
                    Threat(doc.id, summary, severity, lat, lon, radiusM, ts)
                }
                val fresh = threats.filter { t -> (nowMs - t.ts) <= EIGHT_HOURS_MS }
                val within = fresh.filter { t -> milesBetween(latitude, longitude, t.lat, t.lon) <= maxMiles }
                val ranked = within.sortedWith(compareByDescending<Threat> { it.severity }.thenByDescending { it.ts }).take(limit)
                ranked.forEach(onAlert)
            }
    }

    fun appendThreat(summary: String, reporterLat: Double, reporterLon: Double, severity: Int = 3, radiusM: Int = DEFAULT_RADIUS_M) {
        val data = hashMapOf(
            "summary" to summary,
            "severity" to severity,
            "confidence" to 0.75,
            "geo" to mapOf("lat" to reporterLat, "lon" to reporterLon),
            "radiusM" to radiusM,
            "ts" to Timestamp.now()
        )
        firestore.collection(THREATS_COLLECTION).add(data)
    }

    fun checkGeofenceEnter(latitude: Double, longitude: Double, onEnter: (Threat) -> Unit) {
        firestore.collection(THREATS_COLLECTION)
            .orderBy("ts")
            .limit(500)
            .get()
            .addOnSuccessListener { snap ->
                val nowMs = System.currentTimeMillis()
                snap.documents.forEach { doc ->
                    val lat = (doc.get("geo.lat") as? Number)?.toDouble() ?: return@forEach
                    val lon = (doc.get("geo.lon") as? Number)?.toDouble() ?: return@forEach
                    val radiusM = (doc.get("radiusM") as? Number)?.toInt() ?: DEFAULT_RADIUS_M
                    val ts = (doc.get("ts") as? Timestamp)?.toDate()?.time ?: 0L
                    if ((nowMs - ts) > EIGHT_HOURS_MS) return@forEach
                    val distanceM = metersBetween(latitude, longitude, lat, lon)
                    if (distanceM <= radiusM) {
                        val t = Threat(doc.id, doc.getString("summary") ?: "Threat", (doc.get("severity") as? Number)?.toInt() ?: 3, lat, lon, radiusM, ts)
                        onEnter(t)
                        showAlert("Threat nearby", t.summary)
                    }
                }
            }
    }

    private fun showAlert(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val builder = NotificationCompat.Builder(context, alertsChannelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(context)) {
            val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            try { notify(id, builder.build()) } catch (_: SecurityException) {}
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Operational Alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(alertsChannelId, name, importance)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    data class Threat(
        val id: String,
        val summary: String,
        val severity: Int,
        val lat: Double,
        val lon: Double,
        val radiusM: Int,
        val ts: Long
    )

    companion object {
        private const val THREATS_COLLECTION = "threats"
        private const val DEFAULT_RADIUS_M = 500 // reasonable default for now
        private const val EIGHT_HOURS_MS = 8 * 60 * 60 * 1000L

        // Convert a (north,east) meters offset to absolute lat/lon
        private fun metersOffsetToLatLonCalc(baseLat: Double, baseLon: Double, northM: Double, eastM: Double): Pair<Double, Double> {
            val dLat = northM / 111320.0
            val dLon = eastM / (111320.0 * cos(Math.toRadians(baseLat)))
            return (baseLat + dLat) to (baseLon + dLon)
        }

        private fun metersOffsetToLatLon(baseLat: Double, baseLon: Double, northM: Double, eastM: Double): Double {
            // Deprecated helper retained to avoid signature breakage if referenced elsewhere
            return baseLat + northM / 111320.0
        }

        private fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val R = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return R * c
        }

        private fun milesBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            return metersBetween(lat1, lon1, lat2, lon2) / 1609.344
        }
    }
}


