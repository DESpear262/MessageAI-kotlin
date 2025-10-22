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
import androidx.work.ExistingWorkPolicy
import javax.inject.Inject
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.data.remote.SendWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class MessageAiApp : Application() {
    @Inject lateinit var db: AppDatabase

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
