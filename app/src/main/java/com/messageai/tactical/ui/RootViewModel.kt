/**
 * MessageAI – Root authentication view model.
 *
 * Exposes a simple authenticated flag derived from `FirebaseAuth` state and
 * convenience methods to refresh or sign out. Also manages presence updates.
 */
package com.messageai.tactical.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.messageai.tactical.data.remote.RtdbPresenceService
import com.messageai.tactical.data.db.AppDatabase
import com.messageai.tactical.data.remote.ChatService
import com.messageai.tactical.data.remote.MessageListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val presenceService: RtdbPresenceService,
    private val db: AppDatabase,
    private val chatService: ChatService,
    private val messageListener: MessageListener
) : ViewModel() {
    private val _isAuthenticated = MutableStateFlow(auth.currentUser != null)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    init {
        // Set initial presence if already authenticated
        if (auth.currentUser != null) {
            presenceService.goOnline()
        }
    }

    /** Refreshes `_isAuthenticated` from the current auth user and sets presence online. */
    fun refreshAuthState() {
        _isAuthenticated.value = auth.currentUser != null
        if (auth.currentUser != null) {
            presenceService.goOnline()
        }
    }

    /** Called when app goes to background. */
    fun onAppBackground() {
        if (auth.currentUser != null) {
            presenceService.goOffline()
        }
    }

    /** Called when app comes to foreground. */
    fun onAppForeground() {
        if (auth.currentUser != null) {
            presenceService.goOnline()
        }
    }

    /** Signs out the user, sets presence offline, and updates state. */
    fun logout() {
        viewModelScope.launch {
            presenceService.goOffline()
            // Stop any active listeners to avoid cross-user data streaming
            chatService.stop()
            messageListener.stop()
            auth.signOut()
            // Clear local caches to prevent cross-account leakage
            db.clearAllTables()
            _isAuthenticated.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        presenceService.goOffline()
    }
}
