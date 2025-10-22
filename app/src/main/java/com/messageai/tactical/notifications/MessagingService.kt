package com.messageai.tactical.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.messageai.tactical.MainActivity
import com.messageai.tactical.R

/** Firebase Messaging service: updates token and handles incoming messages. */
class MessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        android.util.Log.d("MessagingService", "New FCM token: $token")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update("fcmToken", token)
                .addOnSuccessListener { android.util.Log.d("MessagingService", "FCM token updated for user $uid") }
                .addOnFailureListener { e -> android.util.Log.e("MessagingService", "Failed to update FCM token", e) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        android.util.Log.d("MessagingService", "===== Message received from: ${message.from} =====")
        android.util.Log.d("MessagingService", "Data payload: ${message.data}")
        android.util.Log.d("MessagingService", "Notification payload: title='${message.notification?.title}', body='${message.notification?.body}'")

        // Handle both data-only messages and notification messages
        val chatId = message.data["chatId"]
        val title = message.data["title"] ?: message.notification?.title ?: "New message"
        val body = message.data["body"] ?: message.notification?.body ?: ""

        android.util.Log.d("MessagingService", "Extracted: chatId=$chatId, title=$title, body=$body")

        // Prefer in-app banner when app is in foreground. We emit regardless; UI decides visibility.
        if (chatId != null) {
            android.util.Log.d("MessagingService", "Emitting in-app notification")
            NotificationCenter.emitInApp(
                NotificationCenter.InAppMessage(
                    chatId = chatId,
                    title = title,
                    preview = body
                )
            )
        }

        // Show system notification (works both in foreground and background)
        android.util.Log.d("MessagingService", "Showing system notification")
        showSystemNotification(title, body, chatId)
    }

    private fun showSystemNotification(title: String, body: String, chatId: String?) {
        val channelId = "msg_channel"
        ensureChannel(channelId)

        // Create intent to open the app (and navigate to chat if chatId is available)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (chatId != null) {
                putExtra("chatId", chatId)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(this)) {
            val notificationId = chatId?.hashCode() ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            notify(notificationId, builder.build())
            android.util.Log.d("MessagingService", "Notification shown with ID: $notificationId")
        }
    }

    private fun ensureChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Messages"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, name, importance)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}


