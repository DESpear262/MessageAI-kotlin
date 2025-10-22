/**
 * MessageAI – Compose app root and navigation graph.
 *
 * Sets theme, hosts the `NavHost`, and switches start destination based on
 * authentication state managed by `RootViewModel`.
 */
package com.messageai.tactical.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.messageai.tactical.ui.auth.AuthScreen
import com.messageai.tactical.ui.auth.ForgotPasswordScreen
import com.messageai.tactical.ui.auth.RegisterScreen
import com.messageai.tactical.ui.chat.ChatScreen
import com.messageai.tactical.ui.main.MainTabs
import com.messageai.tactical.ui.theme.MessageAITheme
import com.messageai.tactical.notifications.NotificationCenter
import com.messageai.tactical.util.FcmTokenHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
/** Root composable establishing theme, nav controller, and routes. */
fun MessageAiAppRoot() {
    MessageAITheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()
            val vm: RootViewModel = hiltViewModel()
            val isAuthenticated by vm.isAuthenticated.collectAsState()
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

            // Track app lifecycle for presence
            DisposableEffect(lifecycleOwner, isAuthenticated) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_RESUME -> vm.onAppForeground()
                        androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> vm.onAppBackground()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            LaunchedEffect(Unit) {
                NotificationCenter.inAppMessages.collect { msg ->
                    // Show in-app banner; on action tap, navigate to chat
                    val result = snackbarHostState.showSnackbar(
                        message = "${msg.title}: ${msg.preview}",
                        actionLabel = "Open",
                        withDismissAction = true
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        navController.navigate("chat/${'$'}{msg.chatId}")
                    }
                }
            }

            // Register/refresh FCM token when authenticated
            LaunchedEffect(isAuthenticated) {
                if (isAuthenticated) {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        FcmTokenHelper.updateTokenForUser(user.uid, FirebaseFirestore.getInstance())
                    } else {
                        android.util.Log.w("AppRoot", "No user logged in, skipping FCM token registration")
                    }
                }
            }

            androidx.compose.material3.Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { padding ->
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
                    MainTabs(
                        onLogout = { vm.logout() },
                        onOpenChat = { chatId -> navController.navigate("chat/$chatId") },
                        onCreateChat = { navController.navigate("createChat") }
                    )
                }
                composable("createChat") {
                    com.messageai.tactical.ui.main.CreateChatScreen(
                        onBack = { navController.popBackStack() },
                        onOpenChat = { chatId ->
                            navController.popBackStack()
                            navController.navigate("chat/$chatId")
                        }
                    )
                }
                composable(
                    route = "chat/{chatId}",
                    arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
                    ChatScreen(chatId = chatId, onBack = { navController.popBackStack() })
                }
            }
            }
        }
    }
}
