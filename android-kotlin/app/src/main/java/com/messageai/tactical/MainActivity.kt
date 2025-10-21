/**
 * MessageAI – Main activity entry point.
 *
 * Hosts the Compose root and enables edge-to-edge rendering. Navigation and
 * authentication flow are delegated to `MessageAiAppRoot`.
 */
package com.messageai.tactical

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.messageai.tactical.ui.MessageAiAppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /** Sets up the Compose content hierarchy and theme. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MessageAiAppRoot()
        }
    }
}
