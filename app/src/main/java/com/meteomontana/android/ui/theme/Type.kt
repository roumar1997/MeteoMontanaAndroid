package com.meteomontana.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// PWA usa Inter (sans), Source Serif 4 (serif), JetBrains Mono (mono).
// De momento usamos las families del sistema. Más adelante añadiremos los
// fonts custom como assets.
private val Sans  = FontFamily.Default
private val Mono  = FontFamily.Monospace
private val Serif = FontFamily.Serif

val CumbreTypography = Typography(
    displayLarge   = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Bold,    fontSize = 32.sp, letterSpacing = (-0.5).sp),
    displayMedium  = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold,fontSize = 28.sp),
    headlineLarge  = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Bold,    fontSize = 24.sp),
    headlineMedium = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.SemiBold,fontSize = 20.sp),
    titleLarge     = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.SemiBold,fontSize = 18.sp),
    titleMedium    = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Medium,  fontSize = 16.sp),
    bodyLarge      = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Normal,  fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Normal,  fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge     = TextStyle(fontFamily = Sans,  fontWeight = FontWeight.Medium,  fontSize = 14.sp, letterSpacing = 0.5.sp),
    labelMedium    = TextStyle(fontFamily = Mono,  fontWeight = FontWeight.Medium,  fontSize = 12.sp),
)
