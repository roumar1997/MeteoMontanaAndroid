package com.meteomontana.android.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.unit.TextUnit
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val CumbreLightColors = lightColorScheme(
    primary            = Terra,
    onPrimary          = androidx.compose.ui.graphics.Color.White,
    primaryContainer   = TerraBg,
    onPrimaryContainer = Ink,
    secondary          = Moss,
    onSecondary        = androidx.compose.ui.graphics.Color.White,
    tertiary           = Warn,
    onTertiary         = androidx.compose.ui.graphics.Color.White,
    background         = Bg,
    onBackground       = Ink,
    surface            = Paper,
    onSurface          = Ink,
    surfaceVariant     = Paper2,
    onSurfaceVariant   = Ink2,
    outline            = Rule,
    error              = Bad,
    onError            = androidx.compose.ui.graphics.Color.White
)

private val CumbreDarkColors = darkColorScheme(
    // TerraFillDark, no TerraDark: `primary` se usa como RELLENO de botones con
    // texto blanco encima, y el terracota claro de oscuro (#E0612B) daba 3.55:1
    // con el blanco. Ver TerraFill en Color.kt.
    primary            = TerraFillDark,
    onPrimary          = androidx.compose.ui.graphics.Color.White,
    primaryContainer   = Paper2Dark,
    onPrimaryContainer = InkDark,
    secondary          = MossDark,
    onSecondary        = androidx.compose.ui.graphics.Color.White,
    tertiary           = WarnDark,
    onTertiary         = androidx.compose.ui.graphics.Color.White,
    background         = BgDark,
    onBackground       = InkDark,
    surface            = PaperDark,
    onSurface          = InkDark,
    surfaceVariant     = Paper2Dark,
    onSurfaceVariant   = Ink2Dark,
    outline            = RuleDark,
    error              = BadDark,
    onError            = androidx.compose.ui.graphics.Color.White
)

@Composable
fun MeteoMontanaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You: usa los colores del sistema si Android 12+. Lo dejamos
    // OFF porque queremos los colores Cumbre exactos, no los del sistema.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> CumbreDarkColors
        else      -> CumbreLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = CumbreTypography,
        shapes      = CumbreShapes
    ) {
        // Un Text con tamaño propio (fontSize = 13.sp) heredaba el alto de línea de 24 sp de bodyLarge y todo salía
        // más alto que en iOS. Sin alto fijo, cada texto usa el natural de su letra, como en iOS.
        // Una pastilla de letra 11 medía 30 dp (24 de línea + 6 de margen) frente a 19 pt en iOS.
        // OJO: ProvideTextStyle MEZCLA con el estilo de fuera y un lineHeight «sin especificar» no pisa el 24 → hay
        // que REEMPLAZAR el estilo con CompositionLocalProvider.
        androidx.compose.runtime.CompositionLocalProvider(
            LocalTextStyle provides CumbreTypography.bodyLarge.copy(lineHeight = TextUnit.Unspecified),
            content = content
        )
    }
}
