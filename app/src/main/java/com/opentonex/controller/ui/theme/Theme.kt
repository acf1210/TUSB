package com.opentonex.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.opentonex.controller.R

private val RobotoFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

/** Roboto e a fonte usada pelo app oficial (extraida de assets/Paks/TONEX Control.pak). */
private val ToneXTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = RobotoFamily),
        displayMedium = base.displayMedium.copy(fontFamily = RobotoFamily),
        displaySmall = base.displaySmall.copy(fontFamily = RobotoFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = RobotoFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = RobotoFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = RobotoFamily),
        titleLarge = base.titleLarge.copy(fontFamily = RobotoFamily, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.copy(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = RobotoFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = RobotoFamily),
        bodySmall = base.bodySmall.copy(fontFamily = RobotoFamily),
        labelLarge = base.labelLarge.copy(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = RobotoFamily, fontWeight = FontWeight.Medium)
    )
}

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
        typography = ToneXTypography,
        content = content
    )
}
