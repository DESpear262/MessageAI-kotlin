package com.messageai.tactical.ui.auth

/**
 * MessageAI – Login screen.
 *
 * Email/password login only. "Create Account" navigates to a dedicated
 * registration screen where display name is collected.
 */

import androidx.compose.foundation.clickable
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
fun AuthScreen(onAuthenticated: () -> Unit, onForgotPassword: () -> Unit, onRegister: () -> Unit) {
    val vm: AuthViewModel = hiltViewModel()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val error by vm.error.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password (min 6)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            vm.login(email, password) { onAuthenticated() }
        }, modifier = Modifier.fillMaxWidth()) { Text("Login") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Create Account") }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Forgot password?",
            modifier = Modifier.clickable { onForgotPassword() }
        )
        if (!error.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "")
        }
    }
}
