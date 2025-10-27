/**
 * MessageAI – Compose theme wrapper.
 *
 * Provides a cohesive Material3 theme aligned with the tactical mockups:
 * - Deep charcoal background, muted surfacing, and olive primary accents
 * - Rounded, compact shapes for inputs and chips
 * - Slightly larger title weights for improved readability in the field
 */
package com.messageai.tactical.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MessageAITheme(content: @Composable () -> Unit) {
    val colorsDark = darkColorScheme(
        primary = Color(0xFF6B8F5E),          // olive green (buttons, own bubbles)
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF4B6443),
        onPrimaryContainer = Color(0xFFEFF6EC),
        secondary = Color(0xFF98A29A),
        onSecondary = Color(0xFF0F120F),
        secondaryContainer = Color(0xFF2C312E),
        onSecondaryContainer = Color(0xFFDDE3DE),
        background = Color(0xFF0F1110),        // deep charcoal
        onBackground = Color(0xFFE6E8E7),
        surface = Color(0xFF151816),
        onSurface = Color(0xFFE6E8E7),
        surfaceVariant = Color(0xFF242926),    // chat bubbles (other)
        onSurfaceVariant = Color(0xFFDDE1DE),
        error = Color(0xFFE57373),
        onError = Color(0xFF0F1110)
    )

    val colorsLight = lightColorScheme(
        primary = Color(0xFF3D5A40),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF6B8F5E),
        onPrimaryContainer = Color(0xFF0F120F),
        secondary = Color(0xFF6A756F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE7EBE8),
        onSecondaryContainer = Color(0xFF0F120F),
        background = Color(0xFFF7F8F7),
        onBackground = Color(0xFF121513),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF121513),
        surfaceVariant = Color(0xFFEDEFEF),
        onSurfaceVariant = Color(0xFF1A1D1B)
    )

    val typography = Typography(
        displayLarge = Typography().displayLarge.copy(fontWeight = FontWeight.Bold),
        titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = Typography().bodyLarge,
        bodyMedium = Typography().bodyMedium,
        bodySmall = Typography().bodySmall
    )

    val shapes = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    )

    val useDark = true // app prefers dark tactical styling by default

    MaterialTheme(
        colorScheme = if (useDark) colorsDark else colorsLight,
        typography = typography,
        shapes = shapes,
        content = content
    )
}
