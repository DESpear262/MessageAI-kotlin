package com.messageai.tactical.ui.main

/**
 * MessageAI – Main tabs placeholder.
 *
 * Minimal main area with a profile stub and logout action. Full chat UI will
 * be implemented in subsequent blocks.
 */
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import com.google.firebase.auth.FirebaseAuth

@Composable
fun MainTabs(onLogout: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val user = FirebaseAuth.getInstance().currentUser
            val displayNameState = remember { mutableStateOf(user?.displayName ?: "") }
            LaunchedEffect(user?.displayName) {
                displayNameState.value = user?.displayName ?: ""
            }
            val displayName = if (displayNameState.value.isNullOrBlank()) "User" else displayNameState.value
            // Default avatar placeholder with first initial
            val initial = displayName.trim().firstOrNull()?.uppercase() ?: "U"
            Text(
                text = initial,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF90CAF9))
                    .align(Alignment.CenterHorizontally),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = androidx.compose.ui.Modifier.size(12.dp))
            Text("Welcome, $displayName")
            Button(onClick = onLogout) { Text("Logout") }
        }
    }
}
