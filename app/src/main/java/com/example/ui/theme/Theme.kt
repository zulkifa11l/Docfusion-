package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TealAccent,
    secondary = EmeraldA700,
    tertiary = GoldPremium,
    background = Slate900,
    surface = Slate800,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Slate100,
    onSurface = Slate100,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate100,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = TealAccent,
    secondary = EmeraldA700,
    tertiary = GoldPremium,
    background = Slate100,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Slate900,
    onBackground = Slate900,
    onSurface = Slate900,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Slate900,
    error = ErrorRed
)

@Composable
fun DocfusionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
