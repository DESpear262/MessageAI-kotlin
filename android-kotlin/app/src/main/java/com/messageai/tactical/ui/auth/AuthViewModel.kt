package com.messageai.tactical.ui.auth

/*
 * MessageAI – AuthViewModel
 * Block B (Authentication): Handles login, registration (with display name),
 * Firestore user document creation, and password reset with Firestore existence check.
 * Session persistence leverages FirebaseAuth defaults.
 */

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.length < 6) {
            _error.value = "Invalid email or password"
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { _error.value = it.message }
    }

    fun register(email: String, password: String, displayName: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.length < 6 || displayName.isBlank()) {
            _error.value = "Enter name, valid email, and 6+ char password"
            return
        }
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    _error.value = "Registration failed: No user returned"
                    return@addOnSuccessListener
                }
                // Update FirebaseAuth user profile with display name
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profileUpdates)
                    .addOnCompleteListener {
                        // Create Firestore user document
                        val userDoc = mapOf(
                            "uid" to user.uid,
                            "email" to (user.email ?: email),
                            "displayName" to displayName,
                            "photoURL" to null,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "lastSeen" to FieldValue.serverTimestamp(),
                            "isOnline" to false,
                            "fcmToken" to null,
                            "metadata" to emptyMap<String, Any>()
                        )
                        firestore.collection("users").document(user.uid)
                            .set(userDoc)
                            .addOnSuccessListener { onSuccess() }
                            .addOnFailureListener { e -> _error.value = e.message }
                    }
            }
            .addOnFailureListener { _error.value = it.message }
    }

    fun checkUserAndSendReset(email: String) {
        if (email.isBlank()) {
            _error.value = "Enter email"
            return
        }
        // Check Firestore for user existence by email, then send reset if exists
        firestore.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    _error.value = "That account doesn't exist"
                } else {
                    auth.sendPasswordResetEmail(email)
                        .addOnSuccessListener { _error.value = "Reset link sent" }
                        .addOnFailureListener { _error.value = it.message }
                }
            }
            .addOnFailureListener { _error.value = it.message }
    }
}
