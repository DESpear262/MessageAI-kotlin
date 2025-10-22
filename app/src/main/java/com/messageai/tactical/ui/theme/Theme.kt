/**
 * MessageAI – Compose theme wrapper.
 *
 * Provides a minimal Material3 theme. Extend with dark theme and typography as
 * UI matures.
 */
package com.messageai.tactical.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun MessageAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}
