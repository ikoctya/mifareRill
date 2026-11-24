package com.nfckeywallet.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object StealthColors {
    val Background = Color(0xFF090B0E)
    val Surface = Color(0xFF11151B)
    val SurfaceElevated = Color(0xFF151B23)
    val Border = Color(0xFF232C38)
    val BorderSubtle = Color(0xFF1B222C)

    val Emerald = Color(0xFF10B981)
    val EmeraldBright = Color(0xFF34D399)
    val EmeraldSurface = Color(0xFF0D2820)
    val EmeraldBorder = Color(0xFF165B45)

    val Crimson = Color(0xFFEF4444)
    val CrimsonBright = Color(0xFFF87171)
    val CrimsonSurface = Color(0xFF2B1215)
    val CrimsonBorder = Color(0xFF5C2228)

    val Amber = Color(0xFFF59E0B)
    val TextPrimary = Color(0xFFF3F6FA)
    val TextSecondary = Color(0xFF8A94A6)
    val TextMuted = Color(0xFF5E6878)
}

private val StealthDarkColorScheme = darkColorScheme(
    primary = StealthColors.Emerald,
    onPrimary = Color(0xFF031F16),
    primaryContainer = StealthColors.EmeraldSurface,
    onPrimaryContainer = StealthColors.EmeraldBright,
    secondary = StealthColors.EmeraldBright,
    onSecondary = Color(0xFF031F16),
    secondaryContainer = StealthColors.SurfaceElevated,
    onSecondaryContainer = StealthColors.TextPrimary,
    tertiary = StealthColors.CrimsonBright,
    background = StealthColors.Background,
    onBackground = StealthColors.TextPrimary,
    surface = StealthColors.Surface,
    onSurface = StealthColors.TextPrimary,
    surfaceVariant = StealthColors.SurfaceElevated,
    onSurfaceVariant = StealthColors.TextSecondary,
    outline = StealthColors.Border,
    error = StealthColors.Crimson,
    onError = Color.White
)

@Composable
fun NfcKeyWalletTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StealthDarkColorScheme,
        content = content
    )
}
