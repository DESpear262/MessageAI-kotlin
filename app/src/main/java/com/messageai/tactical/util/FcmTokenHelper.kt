/**
 * MessageAI – FCM token management helper.
 *
 * Provides centralized logic for fetching and updating FCM tokens in Firestore.
 * This ensures consistent token handling across different parts of the app.
 */
package com.messageai.tactical.util

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

object FcmTokenHelper {
    /**
     * Fetches the current FCM token and updates it in Firestore for the given user.
     *
     * @param userId The UID of the user whose token should be updated
     * @param firestore FirebaseFirestore instance
     */
    fun updateTokenForUser(userId: String, firestore: FirebaseFirestore) {
        android.util.Log.d("FcmTokenHelper", "Requesting FCM token for user $userId")
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                android.util.Log.d("FcmTokenHelper", "FCM token received: $token")
                firestore.collection("users").document(userId)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        android.util.Log.d("FcmTokenHelper", "FCM token saved to Firestore for user $userId")
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("FcmTokenHelper", "Failed to save FCM token to Firestore", e)
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FcmTokenHelper", "Failed to get FCM token", e)
            }
    }
}

