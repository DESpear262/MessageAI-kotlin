package com.messageai.tactical.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.messageai.tactical.R

object CasevacNotifier {
    private const val CHANNEL_ID = "casevac_channel"

    fun notifyStart(context: Context, chatId: String) {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CASEVAC started")
            .setContentText("Coordinating medical evacuation…")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        try {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(), builder.build())
        } catch (_: SecurityException) { /* Notification permission may be denied on Android 13+ */ }
    }

    fun notifyComplete(context: Context, facilityName: String?) {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("CASEVAC completed")
            .setContentText(facilityName ?: "Nearest facility assigned")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        try {
            NotificationManagerCompat.from(context).notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(), builder.build())
        } catch (_: SecurityException) { /* Notification permission may be denied on Android 13+ */ }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "CASEVAC", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}


