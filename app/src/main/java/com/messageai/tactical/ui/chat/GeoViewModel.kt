package com.messageai.tactical.ui.chat

import android.annotation.SuppressLint
import android.location.Location
import androidx.lifecycle.ViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.messageai.tactical.modules.geo.GeoService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GeoViewModel @Inject constructor(
    private val appContext: android.app.Application,
    private val geo: GeoService
) : ViewModel() {

    private val fused: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(appContext)
    }

    @SuppressLint("MissingPermission")
    fun summarizeNearby() {
        fused.lastLocation.addOnSuccessListener { loc: Location? ->
            val lat = loc?.latitude ?: return@addOnSuccessListener
            val lon = loc?.longitude ?: return@addOnSuccessListener
            geo.summarizeThreatsNear(lat, lon, maxMiles = 500.0, limit = 50) { threat ->
                // Alerts are shown inside GeoService.showAlert
            }
        }
    }

    fun analyzeChatThreats(chatId: String) {
        geo.analyzeChatThreats(chatId) { /* count -> could show a toast/snackbar */ }
    }
}


