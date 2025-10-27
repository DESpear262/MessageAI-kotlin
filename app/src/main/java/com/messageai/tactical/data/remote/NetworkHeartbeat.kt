package com.messageai.tactical.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.messageai.tactical.modules.geo.GeoService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NetworkHeartbeat monitors cellular connectivity and triggers signal-loss alert
 * after 2 missed heartbeats (approx), as per Block C requirements.
 */
@Singleton
class NetworkHeartbeat @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geoService: GeoService
) {
    private var misses: Int = 0

    fun start() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                misses = 0
            }
            override fun onLost(network: Network) {
                misses += 1
                geoService.alertSignalLossIfNeeded(misses)
            }
        })
    }
}


