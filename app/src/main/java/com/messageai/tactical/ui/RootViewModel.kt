/**
 * MessageAI – Root authentication view model.
 *
 * Exposes a simple authenticated flag derived from `FirebaseAuth` state and
 * convenience methods to refresh or sign out.
 */
package com.messageai.tactical.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {
    private val _isAuthenticated = MutableStateFlow(auth.currentUser != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    /** Refreshes `_isAuthenticated` from the current auth user. */
    fun refreshAuthState() {
        _isAuthenticated.value = auth.currentUser != null
    }

    /** Signs out the user and updates state. */
    fun logout() {
        viewModelScope.launch {
            auth.signOut()
            _isAuthenticated.value = false
        }
    }
}
