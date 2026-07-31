package com.hausgroup.vpn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// HausVPN brand palette.
val HausBg = Color(0xFF0A0A0B)
val HausSurface = Color(0xFF141417)
val HausSurfaceAlt = Color(0xFF1E1E23)
val HausTeal = Color(0xFF3FD8C6)
val HausGold = Color(0xFFC9B08A)
val HausTextPrimary = Color(0xFFF5F5F7)
val HausTextMuted = Color(0xFF8A8A93)
val HausDanger = Color(0xFFE5674B)

private val HausColorScheme = darkColorScheme(
    primary = HausTeal,
    onPrimary = HausBg,
    secondary = HausGold,
    background = HausBg,
    onBackground = HausTextPrimary,
    surface = HausSurface,
    onSurface = HausTextPrimary,
    surfaceVariant = HausSurfaceAlt,
    error = HausDanger,
)

@Composable
fun HausVpnTheme(
    // The brand is a dark-first product; we keep the dark scheme regardless.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = HausColorScheme,
        typography = Typography(),
        content = content,
    )
}
