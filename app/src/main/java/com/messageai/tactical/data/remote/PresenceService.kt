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
    fun isUserOnline(userId: String): Flow<Boolean> = callbackFlow {
        val ref = rtdb.getReference("status/$userId/state")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(String::class.java) == "online")
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun meOnline(): Flow<Boolean> {
        val uid = auth.currentUser?.uid ?: ""
        return if (uid.isEmpty()) callbackFlow { trySend(false); close() } else isUserOnline(uid)
    }
}
