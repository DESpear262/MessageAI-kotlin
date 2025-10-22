/**
 * MessageAI – User presence monitoring via Realtime Database.
 *
 * Provides Flow-based APIs to observe user online/offline status. Connects to
 * RTDB `status/{uid}/state` paths and emits boolean presence indicators. Used
 * throughout the UI to show presence dots and online status.
 */
package com.messageai.tactical.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PresenceService @Inject constructor(
    private val auth: FirebaseAuth,
    private val rtdb: FirebaseDatabase
) {
    /**
     * Returns a Flow that emits the online status of a specific user.
     *
     * Listens to `status/{userId}/state` in RTDB and emits true when the value
     * is "online", false otherwise. The Flow remains active until cancelled.
     *
     * @param userId The UID of the user to monitor
     * @return Flow<Boolean> emitting true for online, false for offline
     */
    fun isUserOnline(userId: String): Flow<Boolean> = callbackFlow {
        android.util.Log.d("PresenceService", "Starting presence listener for user: $userId")
        val ref = rtdb.getReference("status/$userId/state")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(String::class.java)
                val isOnline = state == "online"
                android.util.Log.d("PresenceService", "User $userId presence: $state (online=$isOnline)")
                trySend(isOnline)
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("PresenceService", "Presence listener cancelled for $userId: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { 
            android.util.Log.d("PresenceService", "Removing presence listener for $userId")
            ref.removeEventListener(listener) 
        }
    }

    /**
     * Returns a Flow that emits the current user's own online status.
     *
     * Convenience wrapper around isUserOnline for the authenticated user.
     *
     * @return Flow<Boolean> emitting the current user's presence
     */
    fun meOnline(): Flow<Boolean> {
        val uid = auth.currentUser?.uid ?: ""
        return if (uid.isEmpty()) callbackFlow { trySend(false); close() } else isUserOnline(uid)
    }
}
