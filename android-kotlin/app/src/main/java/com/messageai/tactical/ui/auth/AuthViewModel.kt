package com.messageai.tactical.ui.auth

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.length < 8) {
            _error.value = "Invalid email or password"
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { _error.value = it.message }
    }

    fun register(email: String, password: String, onSuccess: () -> Unit) {
        if (email.isBlank() || password.length < 8) {
            _error.value = "Invalid email or password"
            return
        }
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { _error.value = it.message }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) {
            _error.value = "Enter email"
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { _error.value = "Reset link sent" }
            .addOnFailureListener { _error.value = it.message }
    }
}
