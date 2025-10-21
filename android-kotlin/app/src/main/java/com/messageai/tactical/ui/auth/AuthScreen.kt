package com.messageai.tactical.ui.auth

/*
 * MessageAI – AuthScreen
 * Block B (Authentication): Email/password login and account creation with display name field,
 * entry point to Forgot Password screen. Inline error text for validation and backend errors.
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
fun AuthScreen(onAuthenticated: () -> Unit, onForgotPassword: () -> Unit) {
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
        Button(onClick = {
            vm.login(email, password) { onAuthenticated() }
        }, modifier = Modifier.fillMaxWidth()) { Text("Login") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            vm.register(email, password, displayName) { onAuthenticated() }
        }, modifier = Modifier.fillMaxWidth()) { Text("Create Account") }
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
