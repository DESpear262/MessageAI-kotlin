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
            val loc = fused.awaitNullable()
            if (loc != null) {
                geo.checkGeofenceEnter(loc.latitude, loc.longitude) { /* alerts happen in service */ }
            }
            // Schedule next run in ~5 minutes
            scheduleNext(applicationContext)
            Result.success()
        } catch (_: Exception) {
            scheduleNext(applicationContext)
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "geo_geofence_check"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val req = OneTimeWorkRequestBuilder<GeofenceWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, req)
        }

        fun scheduleRecurring5m(context: Context) {
            scheduleNext(context)
        }

        private fun scheduleNext(context: Context) {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val req = OneTimeWorkRequestBuilder<GeofenceWorker>()
                .setConstraints(constraints)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

private suspend fun FusedLocationProviderClient.lastLocation(): Location? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        try {
            this.lastLocation
                .addOnSuccessListener { loc -> cont.resume(loc) {} }
                .addOnFailureListener { _ -> cont.resume(null) {} }
        } catch (_: SecurityException) {
            cont.resume(null) {}
        }
    }

private suspend fun FusedLocationProviderClient.awaitNullable(): Location? = lastLocation()


