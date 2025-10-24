package com.messageai.tactical.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.messageai.tactical.modules.geo.GeoService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Periodic on-demand geofence check to alert on enter events. */
@HiltWorker
class GeofenceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val geo: GeoService
) : CoroutineWorker(context, params) {

    private val fused: FusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(applicationContext) }

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        return try {
            val loc = fused.lastLocation.awaitNullable()
            if (loc != null) {
                geo.checkGeofenceEnter(loc.latitude, loc.longitude) { /* alerts happen in service */ }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val req = OneTimeWorkRequestBuilder<GeofenceWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("geo_geofence_check", ExistingWorkPolicy.KEEP, req)
        }
    }
}

private suspend fun FusedLocationProviderClient.lastLocation(): Location? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        this.lastLocation.addOnSuccessListener { loc -> cont.resume(loc) {} }
            .addOnFailureListener { e -> cont.resume(null) {} }
    }

private suspend fun FusedLocationProviderClient.awaitNullable(): Location? = lastLocation()


