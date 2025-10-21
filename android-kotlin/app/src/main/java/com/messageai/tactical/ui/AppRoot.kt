package com.messageai.tactical.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.messageai.tactical.ui.auth.AuthScreen
import com.messageai.tactical.ui.main.MainTabs
import com.messageai.tactical.ui.theme.MessageAITheme

@Composable
fun MessageAiAppRoot() {
    MessageAITheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            val vm: RootViewModel = hiltViewModel()
            val isAuthenticated by vm.isAuthenticated.collectAsState()

            NavHost(
                navController = navController,
                startDestination = if (isAuthenticated) "main" else "auth"
            ) {
                composable("auth") {
                    AuthScreen(onAuthenticated = {
                        vm.refreshAuthState()
                    })
                }
                composable("main") {
                    MainTabs(onLogout = {
                        vm.logout()
                    })
                }
            }
        }
    }
}
