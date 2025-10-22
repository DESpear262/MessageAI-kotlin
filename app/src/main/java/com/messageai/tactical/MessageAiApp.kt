/**
 * MessageAI – Application class for Hilt initialization.
 *
 * Serves as the DI root for the Android application. Keep lightweight.
 */
package com.messageai.tactical

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MessageAiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "msg_channel",
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
}
