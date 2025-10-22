/**
 * MessageAI – Realtime Database presence and typing.
 *
 * Maintains an online/offline presence flag under `status/{uid}` and a typing
 * indicator under `typing/{chatId}/{uid}`. Uses onDisconnect to ensure a sane
 * offline state when the client terminates unexpectedly.
 */
package com.messageai.tactical.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RtdbPresenceService @Inject constructor(
    private val auth: FirebaseAuth,
    private val rtdb: FirebaseDatabase
) {
    fun goOnline() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            android.util.Log.w("RtdbPresence", "goOnline called but no user is authenticated")
            return
        }
        android.util.Log.d("RtdbPresence", "Setting user $uid to online")
        val ref = rtdb.getReference("status/$uid")
        val presenceData = mapOf("state" to "online", "last_changed" to System.currentTimeMillis())
        
        // Set onDisconnect first
        ref.onDisconnect().setValue(mapOf("state" to "offline", "last_changed" to System.currentTimeMillis()))
            .addOnSuccessListener { android.util.Log.d("RtdbPresence", "onDisconnect handler set for $uid") }
            .addOnFailureListener { e -> android.util.Log.e("RtdbPresence", "Failed to set onDisconnect for $uid", e) }
        
        // Then set online
        ref.setValue(presenceData)
            .addOnSuccessListener { android.util.Log.d("RtdbPresence", "Successfully set $uid to online") }
            .addOnFailureListener { e -> android.util.Log.e("RtdbPresence", "Failed to set $uid to online", e) }
    }

    fun goOffline() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            android.util.Log.w("RtdbPresence", "goOffline called but no user is authenticated")
            return
        }
        android.util.Log.d("RtdbPresence", "Setting user $uid to offline")
        val ref = rtdb.getReference("status/$uid")
        ref.setValue(mapOf("state" to "offline", "last_changed" to System.currentTimeMillis()))
            .addOnSuccessListener { android.util.Log.d("RtdbPresence", "Successfully set $uid to offline") }
            .addOnFailureListener { e -> android.util.Log.e("RtdbPresence", "Failed to set $uid to offline", e) }
    }

    fun setTyping(chatId: String, typing: Boolean, scope: CoroutineScope) {
        val uid = auth.currentUser?.uid ?: return
        val ref = rtdb.getReference("typing/$chatId/$uid")
        scope.launch(Dispatchers.IO) {
            ref.setValue(typing)
            if (typing) {
                ref.onDisconnect().setValue(false)
            }
        }
    }
}
