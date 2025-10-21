package com.messageai.tactical.ui.auth

/*
 * MessageAI – ForgotPasswordScreen
 * Block B (Authentication): Allows user to enter email, checks Firestore for existence,
 * and sends Firebase Auth password reset email if found.
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val vm: AuthViewModel = hiltViewModel()
    var email = remember { mutableStateOf("") }
    val error by vm.error.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = email.value,
            onValueChange = { email.value = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Button(onClick = { vm.checkUserAndSendReset(email.value) }, modifier = Modifier.fillMaxWidth()) {
            Text("Send reset email")
        }
        Spacer(modifier = Modifier.padding(8.dp))
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        if (!error.isNullOrEmpty()) {
            Spacer(modifier = Modifier.padding(8.dp))
            Text(error ?: "")
        }
    }
}


