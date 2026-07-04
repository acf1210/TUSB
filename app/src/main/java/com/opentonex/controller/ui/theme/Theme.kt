package com.opentonex.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.opentonex.controller.R

/**
 * O design TUSB usa DM Sans + DM Mono. Como as fontes nao estao empacotadas no app,
 * usamos Roboto (ja bundled) como sans e a monoespacada do sistema como mono; a troca
 * por DM Sans/DM Mono e' so' substituir estas duas familias.
 */
private val SansFamily = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold)
)

val MonoFamily = FontFamily.Monospace

/** Rotulo mono uppercase com tracking largo (labels de knob, chips, rodapes). */
val MonoLabelStyle = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    letterSpacing = 1.2.sp
)

private val ToneXTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = SansFamily),
        displayMedium = base.displayMedium.copy(fontFamily = SansFamily),
        displaySmall = base.displaySmall.copy(fontFamily = SansFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = SansFamily, fontWeight = FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = SansFamily, fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = SansFamily, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = SansFamily, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = SansFamily, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = SansFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = SansFamily),
        bodySmall = base.bodySmall.copy(fontFamily = SansFamily),
        labelLarge = base.labelLarge.copy(fontFamily = SansFamily, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = SansFamily, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = SansFamily, fontWeight = FontWeight.Medium)
    )
}

private val ToneXDarkColorScheme = darkColorScheme(
    primary = ToneXAccent,
    onPrimary = ToneXBackground,
    secondary = ToneXGreen,
    onSecondary = ToneXBackground,
    background = ToneXBackground,
    onBackground = ToneXOnSurface,
    surface = ToneXSurface,
    onSurface = ToneXOnSurface,
    surfaceVariant = ToneXSurfaceVariant,
    onSurfaceVariant = ToneXOnSurfaceMuted,
    outline = ToneXDivider,
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
