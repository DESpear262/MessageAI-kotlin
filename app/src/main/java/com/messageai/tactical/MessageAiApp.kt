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
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.data.remote.SendWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@HiltAndroidApp
class MessageAiApp : Application(), Configuration.Provider {
    @Inject lateinit var db: AppDatabase
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var networkHeartbeat: com.messageai.tactical.data.remote.NetworkHeartbeat

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Start network heartbeat monitoring (cellular)
        try { networkHeartbeat.start() } catch (_: Exception) {}
        // Start recurring geofence checks every ~5 minutes
        try { com.messageai.tactical.data.remote.GeofenceWorker.scheduleRecurring5m(this) } catch (_: Exception) {}
        // Re-scan pending sends and enqueue
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val queue = db.sendQueueDao().items()
                // Collect once then cancel; a simple seed pass at launch
                queue.collect { items ->
                    items.forEach { item ->
                        // In a more robust impl, fetch MessageEntity to get text/clientTs
                        // For MVP, schedule with minimal info
                        SendWorker.enqueue(
                            context = this@MessageAiApp,
                            messageId = item.messageId,
                            chatId = item.chatId,
                            senderId = "",
                            text = null,
                            clientTs = System.currentTimeMillis()
                        )
                    }
                    cancel()
                }
            } catch (_: Exception) {
                // Best-effort; safe to ignore at launch
            }
        }

        // Avoid early access to Hilt entrypoints to prevent dependency cycles.
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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
