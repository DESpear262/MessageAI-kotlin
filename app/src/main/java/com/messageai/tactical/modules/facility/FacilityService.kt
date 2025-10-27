package com.messageai.tactical.modules.facility

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

data class Facility(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val capabilities: List<String> = emptyList(),
    val available: Boolean = true
)

@Singleton
class FacilityService @Inject constructor(private val db: FirebaseFirestore) {
    private fun col() = db.collection(COL)

    suspend fun nearest(lat: Double, lon: Double, requireAvailable: Boolean = true): Facility? {
        val snap = col().limit(500).get().await()
        val list = snap.documents.mapNotNull { d ->
            val name = d.getString("name") ?: return@mapNotNull null
            val flat = (d.get("lat") as? Number)?.toDouble() ?: return@mapNotNull null
            val flon = (d.get("lon") as? Number)?.toDouble() ?: return@mapNotNull null
            val caps = (d.get("capabilities") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val avail = d.getBoolean("available") ?: true
            if (requireAvailable && !avail) return@mapNotNull null
            Facility(d.id, name, flat, flon, caps, avail)
        }
        return list.minByOrNull { metersBetween(lat, lon, it.lat, it.lon) }
    }

    companion object {
        private const val COL = "facilities"
        private fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val R = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return R * c
        }
    }
}


