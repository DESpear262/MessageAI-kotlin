/**
 * MessageAI – Compose app root and navigation graph.
 *
 * Sets theme, hosts the `NavHost`, and switches start destination based on
 * authentication state managed by `RootViewModel`.
 */
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
import com.messageai.tactical.ui.auth.ForgotPasswordScreen
import com.messageai.tactical.ui.auth.RegisterScreen
import com.messageai.tactical.ui.main.MainTabs
import com.messageai.tactical.ui.theme.MessageAITheme

@Composable
/** Root composable establishing theme, nav controller, and routes. */
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
                    AuthScreen(
                        onAuthenticated = { vm.refreshAuthState() },
                        onForgotPassword = { navController.navigate("forgot") },
                        onRegister = { navController.navigate("register") }
                    )
                }
                composable("register") {
                    RegisterScreen(
                        onRegistered = {
                            vm.refreshAuthState()
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }
                composable("forgot") {
                    ForgotPasswordScreen(onBack = { navController.popBackStack() })
                }
                composable("main") {
                    MainTabs(onLogout = { vm.logout() })
                }
            }
        }
    }
}
