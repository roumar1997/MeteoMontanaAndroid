package com.meteomontana.android.ui.screens.radar

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Tab Radar: WebView con Windy.com embebido.
 * Misma solución que la PWA, así reutilizamos el viewer de Windy
 * (animación de viento, lluvia, temp).
 */
@Composable
fun RadarScreen() {
    // Centro: península ibérica. Zoom 5 cubre España completa.
    // Más adelante: pasar lat/lon del usuario en lugar de Madrid.
    val url = "https://embed.windy.com/embed2.html?" +
            "lat=40&lon=-3&detailLat=40&detailLon=-3" +
            "&width=650&height=450&zoom=5" +
            "&level=surface&overlay=rain&product=ecmwf" +
            "&menu=&message=true&marker=&calendar=now&pressure=&type=map" +
            "&location=coordinates&detail=" +
            "&metricWind=km/h&metricTemp=%C2%B0C&radarRange=-1"

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                // Forzar viewport para que se ajuste al ancho del móvil
                setInitialScale(1)
                loadUrl(url)
            }
        }
    )
}
