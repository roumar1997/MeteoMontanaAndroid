package com.meteomontana.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R

// =============================================================================
// CUMBRE Typography — paridad con la PWA (css/style.css :root).
//
// Las tres familias se descargan via el provider de Google Fonts (mismo
// origen que la PWA: fonts.googleapis.com), así que el "look" tipográfico
// es idéntico sin tener que empaquetar .ttf en la APK.
//
// Cuando hagamos iOS, se empaquetan los mismos archivos .ttf de Google Fonts
// en el bundle y se exponen con los mismos nombres en Typography.swift.
// =============================================================================

private val GoogleProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

private val InterGF       = GoogleFont("Inter")
private val SourceSerif4GF = GoogleFont("Source Serif 4")
private val JetBrainsMonoGF = GoogleFont("JetBrains Mono")

// Replicamos exactamente los pesos que la PWA pide a Google Fonts en index.html.
val InterFamily = FontFamily(
    Font(googleFont = InterGF, fontProvider = GoogleProvider, weight = FontWeight.Light),
    Font(googleFont = InterGF, fontProvider = GoogleProvider, weight = FontWeight.Normal),
    Font(googleFont = InterGF, fontProvider = GoogleProvider, weight = FontWeight.Medium),
    Font(googleFont = InterGF, fontProvider = GoogleProvider, weight = FontWeight.SemiBold),
    Font(googleFont = InterGF, fontProvider = GoogleProvider, weight = FontWeight.Bold),
    Font(googleFont = InterGF, fontProvider = GoogleProvider, weight = FontWeight.ExtraBold),
)

val SourceSerif4Family = FontFamily(
    Font(googleFont = SourceSerif4GF, fontProvider = GoogleProvider, weight = FontWeight.Light),
    Font(googleFont = SourceSerif4GF, fontProvider = GoogleProvider, weight = FontWeight.Normal),
    Font(googleFont = SourceSerif4GF, fontProvider = GoogleProvider, weight = FontWeight.SemiBold),
    Font(googleFont = SourceSerif4GF, fontProvider = GoogleProvider, weight = FontWeight.Bold),
    Font(googleFont = SourceSerif4GF, fontProvider = GoogleProvider, weight = FontWeight.Normal, style = FontStyle.Italic),
)

val JetBrainsMonoFamily = FontFamily(
    Font(googleFont = JetBrainsMonoGF, fontProvider = GoogleProvider, weight = FontWeight.Normal),
    Font(googleFont = JetBrainsMonoGF, fontProvider = GoogleProvider, weight = FontWeight.Medium),
    Font(googleFont = JetBrainsMonoGF, fontProvider = GoogleProvider, weight = FontWeight.SemiBold),
    Font(googleFont = JetBrainsMonoGF, fontProvider = GoogleProvider, weight = FontWeight.Bold),
)

// Aliases con los nombres que usa la PWA en CSS para encontrarlos rápido.
val Sans  = InterFamily
val Serif = SourceSerif4Family
val Mono  = JetBrainsMonoFamily

// =============================================================================
// Roles tipográficos Material3, mapeados al uso real en la PWA:
//
//   PWA → Android
//   ─────────────────────────────────────────────────────
//   .serif (titulares hero)    → displayLarge / displayMedium
//   h1/h2 sans bold             → headlineLarge / headlineMedium
//   títulos de card sans 600    → titleLarge / titleMedium
//   párrafos                    → bodyLarge / bodyMedium
//   .eyebrow (mono tracked)     → labelMedium
//   etiquetas / chips sans      → labelLarge
//
// Letterspacing/sizes copiados de los estilos inline observados en
// index.html (eyebrow 0.62rem ≈ 10sp, etc.).
// =============================================================================
val CumbreTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, letterSpacing = (-0.3).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, letterSpacing = 0.5.sp
    ),
    // OJO: este estilo se usa por todos lados (km/h, mm, "09", "10"…) — no
    // metas tracking aquí o se te parten los dígitos en columnas.
    // Para los "eyebrow" con tracking ancho, usa EyebrowTextStyle (abajo).
    labelMedium = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, letterSpacing = 0.sp
    ),
)

/**
 * "Eyebrow" PWA — etiqueta sobre un bloque, mono peso 700, tracking
 * 0.18em ≈ 1.8sp. Úsalo SOLO en headers que en la PWA llevan
 * `class="eyebrow"` ("DISTANCIA", "VER MAPA", "PRÓXIMAS 16 HORAS"...).
 *
 * Nunca como style por defecto: el tracking ancho destroza dígitos
 * cortos como "09" o "10".
 */
val EyebrowTextStyle = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    letterSpacing = 1.8.sp
)
