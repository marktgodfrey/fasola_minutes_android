package org.fasola.minutes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PaperColor = Color(0xFFEDECE4)
val FontColor = Color(0xFF1A1A1A)
val BookColor = Color(0xFF115740)
val NavigationColor = Color(0xFFF1EABC)
val BarColor = Color(0xFF22362A)
val SearchBarColor = Color(0xFF2E2E2C)

private val Colors = lightColorScheme(
    primary = BookColor,
    onPrimary = NavigationColor,
    primaryContainer = BookColor,
    onPrimaryContainer = NavigationColor,
    background = PaperColor,
    onBackground = FontColor,
    surface = PaperColor,
    onSurface = FontColor,
    surfaceVariant = PaperColor,
    onSurfaceVariant = FontColor,
    surfaceContainer = PaperColor,
    surfaceContainerLow = PaperColor,
    surfaceContainerHigh = PaperColor,
    outline = Color.DarkGray,
)

@Composable
fun MinutesTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
