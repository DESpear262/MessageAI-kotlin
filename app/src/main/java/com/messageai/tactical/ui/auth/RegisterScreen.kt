package com.messageai.tactical.ui.auth

/**
 * MessageAI – Registration screen.
 *
 * Collects display name, email, and password; creates Firebase Auth user,
 * updates profile, and creates Firestore user document. On success, navigates
 * back to the app root via onRegistered.
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onCancel: () -> Unit) {
    val vm: AuthViewModel = hiltViewModel()

    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val error by vm.error.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (min 6)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.register(email, password, displayName) { onRegistered() } }, modifier = Modifier.fillMaxWidth()) { Text("Confirm") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        if (!error.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "")
        }
    }
}
