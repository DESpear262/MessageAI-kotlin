/**
 * MessageAI – Main activity entry point.
 *
 * Hosts the Compose root and enables edge-to-edge rendering. Navigation and
 * authentication flow are delegated to `MessageAiAppRoot`.
 */
package com.messageai.tactical

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.messageai.tactical.ui.MessageAiAppRoot
import com.messageai.tactical.notifications.DeepLinkCenter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    /** Sets up the Compose content hierarchy and theme. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPostNotificationsIfNeeded()
        // If launched from a notification with chatId extra
        intent?.getStringExtra("chatId")?.let { chatId ->
            DeepLinkCenter.emitChat(chatId)
        }
        setContent {
            MessageAiAppRoot()
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val has = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (has != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
