/**
 * MessageAI – Shared UI components used across multiple screens.
 *
 * Contains reusable composables like presence indicators, badges, and other
 * common UI elements to maintain consistency and reduce code duplication.
 */
package com.messageai.tactical.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Displays a presence indicator dot showing online/offline status.
 *
 * @param isOnline True for green (online), false for gray (offline)
 */
@Composable
fun PresenceDot(isOnline: Boolean) {
    val color = if (isOnline) Color(0xFF2ECC71) else Color(0xFFB0B0B0)
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(MaterialTheme.shapes.small)
            .background(color)
    )
}

