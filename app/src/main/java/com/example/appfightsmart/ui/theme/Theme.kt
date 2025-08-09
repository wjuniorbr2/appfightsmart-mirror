package com.example.appfightsmart.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Color Scheme (Blue Theme)
val LightColorScheme = lightColorScheme(
    primary = Color(0xff3f51b5), // Blue
    primaryContainer = Color(0xFF1976D2), // Darker Blue
    secondary = Color(0xFF03A9F4), // Light Blue
    secondaryContainer = Color(0xFF0288D1), // Darker Light Blue
    background = Color(0xFFFFFFFF), // White
    surface = Color(0xFFFFFFFF), // White
    onPrimary = Color(0xFFFFFFFF), // White
    onSecondary = Color(0xFF000000), // Black
    onBackground = Color(0xFF000000), // Black
    onSurface = Color(0xFF000000) // Black
)

// Dark Color Scheme (Blue Theme)
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6), // Light Blue
    primaryContainer = Color(0xFF1976D2), // Darker Blue
    secondary = Color(0xFF4FC3F7), // Light Blue
    secondaryContainer = Color(0xFF0288D1), // Darker Light Blue
    background = Color(0xFF121212), // Dark Background
    surface = Color(0xFF121212), // Dark Background
    onPrimary = Color(0xFF000000), // Black
    onSecondary = Color(0xFFFFFFFF), // White
    onBackground = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFFFFFFFF) // White
)

// Typography
val Typography = androidx.compose.material3.Typography(
    // Define your typography here (if needed)
)

// MaterialTheme Composable
@Composable
fun AppFightSmartTheme(
    isDarkMode: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (isDarkMode) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}