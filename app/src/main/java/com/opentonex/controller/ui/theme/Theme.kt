package com.opentonex.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ToneXDarkColorScheme = darkColorScheme(
    primary = ToneXAccent,
    onPrimary = ToneXBackground,
    secondary = ToneXAccentVariant,
    background = ToneXBackground,
    onBackground = ToneXOnSurface,
    surface = ToneXSurface,
    onSurface = ToneXOnSurface,
    surfaceVariant = ToneXSurfaceVariant,
    onSurfaceVariant = ToneXOnSurfaceMuted,
    error = ToneXError
)

@Composable
fun ToneXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ToneXDarkColorScheme,
        content = content
    )
}
