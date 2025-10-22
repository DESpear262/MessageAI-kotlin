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
        val uid = auth.currentUser?.uid ?: return
        val ref = rtdb.getReference("status/${'$'}uid")
        ref.onDisconnect().setValue(mapOf("state" to "offline", "last_changed" to System.currentTimeMillis()))
        ref.setValue(mapOf("state" to "online", "last_changed" to System.currentTimeMillis()))
    }

    fun goOffline() {
        val uid = auth.currentUser?.uid ?: return
        val ref = rtdb.getReference("status/${'$'}uid")
        ref.setValue(mapOf("state" to "offline", "last_changed" to System.currentTimeMillis()))
    }

    fun setTyping(chatId: String, typing: Boolean, scope: CoroutineScope) {
        val uid = auth.currentUser?.uid ?: return
        val ref = rtdb.getReference("typing/${'$'}chatId/${'$'}uid")
        scope.launch(Dispatchers.IO) {
            ref.setValue(typing)
            if (typing) {
                ref.onDisconnect().setValue(false)
            }
        }
    }
}
