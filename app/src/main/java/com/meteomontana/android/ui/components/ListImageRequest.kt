package com.meteomontana.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale

/**
 * Petición de Coil para una foto de LISTA cuyo alto no está fijado (p. ej. `ContentScale.FillWidth`): sin tope,
 * Coil la decodifica a su tamaño original (fotos de móvil de 4000 px) y la lista se atasca al bajar. Se limita al
 * ancho de la pantalla y como mucho 2 pantallas de alto: se ve igual y ocupa una fracción de la memoria.
 */
@Composable
fun rememberListImageRequest(url: String?): ImageRequest {
    val ctx = LocalContext.current
    val conf = LocalConfiguration.current
    val density = LocalDensity.current
    val widthPx = with(density) { conf.screenWidthDp * this.density }.toInt().coerceAtLeast(1)
    return remember(url, widthPx) {
        ImageRequest.Builder(ctx)
            .data(url)
            .size(widthPx, widthPx * 2)
            .scale(Scale.FIT)
            .precision(Precision.INEXACT)
            .build()
    }
}
