package com.meteomontana.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R

// =============================================================================
// CUMBRE Typography — las tres familias van DENTRO del APK.
//
// Antes se descargaban con el provider de Google Fonts, y ahí estaba el fallo
// que hacía que la app se viera "genérica" en unos móviles y no en otros: ese
// provider va a través de Google Play Services y, cuando no responde —MIUI de
// Xiaomi es el caso típico—, Android cae a la tipografía del sistema. Sin la
// mono de los eyebrows ni la serif de los titulares, la app pierde de golpe su
// carácter, y el síntoma es difícil de atribuir porque no falla nada: solo se
// ve distinta.
//
// PARIDAD CON iOS: la serif y la mono son EXACTAMENTE los mismos ficheros que
// hay en `iosApp/iosApp/Fonts/`, así que las dos apps dibujan el mismo trazo.
// Si algún día se cambia una, hay que cambiar la otra.
//
// Licencia: las tres son SIL Open Font License 1.1, que permite expresamente
// incrustarlas y distribuirlas junto con software, incluido software de pago.
// =============================================================================

/**
 * Inter en formato VARIABLE: un solo fichero contiene todos los pesos, de Thin
 * a Black. Ocupa menos que los seis estáticos que harían falta si no, y permite
 * pedir cualquier peso intermedio.
 *
 * iOS no la empaqueta —allí el texto corriente va con la fuente del sistema—,
 * pero Inter se parece mucho más a la San Francisco de Apple que la sans
 * genérica de Android, así que incrustarla acerca las dos apps en vez de
 * separarlas.
 */
val InterFamily = FontFamily(
    interWeight(FontWeight.Light),
    interWeight(FontWeight.Normal),
    interWeight(FontWeight.Medium),
    interWeight(FontWeight.SemiBold),
    interWeight(FontWeight.Bold),
    interWeight(FontWeight.ExtraBold),
)

/**
 * Una instancia de la variable fijada a un peso concreto.
 *
 * `FontVariation` sigue marcada como experimental en Compose, de ahí el opt-in.
 * El riesgo es asumible: si la API cambia, **el build falla en voz alta** —no se
 * degrada en silencio—, y la alternativa era empaquetar seis ficheros estáticos
 * de Inter, casi un mega más de APK. Si algún día molesta, se cambia por los
 * estáticos y aquí no se entera nadie más.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun interWeight(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/**
 * Source Serif 4 — nombres de escuela, titulares, cifras de score.
 *
 * Solo tres pesos, los mismos que iOS empaqueta. Los que falten (Light, Medium)
 * los resuelve Compose con el más cercano, igual que hace `Cumbre.serif` en
 * Swift: negrita para bold, semibold para medio, regular para el resto.
 */
val SourceSerif4Family = FontFamily(
    Font(R.font.source_serif4_regular, FontWeight.Normal),
    Font(R.font.source_serif4_semibold, FontWeight.SemiBold),
    Font(R.font.source_serif4_bold, FontWeight.Bold),
)

/**
 * JetBrains Mono — eyebrows, dígitos y etiquetas técnicas.
 *
 * Regular y negrita, ni uno más: es exactamente lo que empaqueta iOS. Cuando
 * se pide un peso intermedio, Compose elige el más próximo.
 */
val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
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
