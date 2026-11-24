package com.nfckeywallet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C9BFF),
    onPrimary = Color(0xFF0A1638),
    primaryContainer = Color(0xFF1F2E66),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF5CE1B8),
    onSecondary = Color(0xFF003828),
    secondaryContainer = Color(0xFF0D4E3B),
    onSecondaryContainer = Color(0xFFA7F7DC),
    tertiary = Color(0xFFFFB77C),
    background = Color(0xFF0B0E17),
    onBackground = Color(0xFFF1F4FF),
    surface = Color(0xFF121726),
    onSurface = Color(0xFFF1F4FF),
    surfaceVariant = Color(0xFF1D2438),
    onSurfaceVariant = Color(0xFFA9B4D0),
    outline = Color(0xFF333D5A),
    error = Color(0xFFFF6E7A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3454D1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF0E1D5C),
    secondary = Color(0xFF0D9468),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F7E6),
    onSecondaryContainer = Color(0xFF003322),
    tertiary = Color(0xFFD96B27),
    background = Color(0xFFF5F7FC),
    onBackground = Color(0xFF121826),
    surface = Color.White,
    onSurface = Color(0xFF121826),
    surfaceVariant = Color(0xFFE9EEF9),
    onSurfaceVariant = Color(0xFF4C5773),
    outline = Color(0xFFC7D0E4),
    error = Color(0xFFCC2936)
)

@Composable
fun NfcKeyWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
