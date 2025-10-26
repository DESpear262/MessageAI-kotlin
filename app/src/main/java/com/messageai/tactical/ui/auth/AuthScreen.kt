package com.messageai.tactical.ui.auth

/**
 * MessageAI – Login screen.
 *
 * Email/password login only. "Create Account" navigates to a dedicated
 * registration screen where display name is collected.
 */

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AuthScreen(onAuthenticated: () -> Unit, onForgotPassword: () -> Unit, onRegister: () -> Unit) {
    val vm: AuthViewModel = hiltViewModel()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val error by vm.error.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.Center) {
        // Branding header
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // Optional: if you have a shield/star asset, place it in res and uncomment
            // Image(painterResource(id = R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(72.dp))
            Text("BATTLEBUDDY", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(4.dp))
            Text("AI-POWERED TACTICAL COMMUNICATIONS", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "Forgot password?",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.clickable { onForgotPassword() }
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.login(email, password) { onAuthenticated() } },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) { Text("LOG IN") }
        Spacer(Modifier.height(8.dp))
        TextButtonRow(onRegister)
        if (!error.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "")
        }
    }
}

@Composable
private fun TextButtonRow(onRegister: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(text = "Don't have an account? ")
        androidx.compose.material3.TextButton(onClick = onRegister) {
            Text("Sign up")
        }
    }
}
