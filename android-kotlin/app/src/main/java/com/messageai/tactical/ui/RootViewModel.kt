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

    fun refreshAuthState() {
        _isAuthenticated.value = auth.currentUser != null
    }

    fun logout() {
        viewModelScope.launch {
            auth.signOut()
            _isAuthenticated.value = false
        }
    }
}
