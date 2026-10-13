package com.audiocast.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TwitchPurple = Color(0xFF9146FF)
val PurpleLight = Color(0xFFB79CFF)
val Background = Color(0xFF0F1117)
val SurfaceDark = Color(0xFF181B22)
val SurfaceVariantDark = Color(0xFF23272F)
val OnSurfaceDark = Color(0xFFECEEF2)
val OnSurfaceDim = Color(0xFF9BA1AD)
val LiveRed = Color(0xFFEB0400)
val SuccessGreen = Color(0xFF35C759)

private val DarkColors = darkColorScheme(
    primary = TwitchPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B2A63),
    onPrimaryContainer = PurpleLight,
    secondary = PurpleLight,
    background = Background,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceDim,
    error = Color(0xFFFF6B6B),
    onError = Color.Black,
    outline = Color(0xFF3A3F4A),
)

@Composable
fun AudioCastTheme(content: @Composable () -> Unit) {
    // The app is dark-only by design; the flag is read for future flexibility.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkColors, content = content)
}
