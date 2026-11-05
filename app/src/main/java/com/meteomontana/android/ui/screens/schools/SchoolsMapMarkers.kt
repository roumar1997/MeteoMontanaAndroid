package com.meteomontana.android.ui.screens.schools

import com.meteomontana.android.util.CatalogLabels
import com.meteomontana.android.data.map.MapStyles
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.LocationDisabled
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.meteomontana.android.domain.util.Geo
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R

// Marcadores del mapa de Escuelas: sincronizar con MapLibre y dibujar el punto o el rombo con la nota.

/**
 * Borra los markers anteriores y crea uno por escuela visible. Hace fit-bounds
 * para que la cámara encuadre lo que el usuario está filtrando.
 */
internal fun syncMarkers(
    ctx: android.content.Context,
    map: MapLibreMap,
    schools: List<School>,
    scoresById: Map<String, Int>,
    showLabels: Boolean,
    fitBounds: Boolean,
    userLat: Double?,
    userLon: Double?,
    tiny: Boolean = false,   // zoom lejano: puntito en vez de diamante
    onMarkerTap: (School) -> Unit
) {
    // Limpia los anteriores
    activeMarkers.forEach { map.removeMarker(it) }
    activeMarkers.clear()
    markerSchoolBySnippet.clear()
    map.setOnMarkerClickListener(null)

    // Punto azul con la posición del usuario (si la tenemos).
    if (userLat != null && userLon != null) {
        activeMarkers += map.addMarker(
            MarkerOptions()
                .position(LatLng(userLat, userLon))
                .icon(cachedPanelIcon(ctx, "user") {
                    com.meteomontana.android.ui.components.userDotBitmap() })
        )
    }

    if (schools.isEmpty()) return

    val boundsBuilder = LatLngBounds.Builder()
    schools.forEach { s ->
        val score = scoresById[s.id]
        // Escalar por densidad: en px fijos el pin salía diminuto en pantallas
        // de alta densidad (los móviles modernos, o sea, todos).
        val density = ctx.resources.displayMetrics.density
        // Icono CACHEADO por clave: sin caché, cada re-sync registra sprites
        // nuevos en el atlas de MapLibre y acaba corrompiéndolo (markers
        // pintados como bandas gigantes al hacer zoom).
        val label = if (showLabels) s.name else null
        val key = if (tiny) "dot:$score" else "dia:$score:${label ?: ""}"
        val icon = cachedPanelIcon(ctx, key) {
            if (tiny) dotBitmap(score, density)
            else diamondBitmap(score, label, density)
        }
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(s.lat, s.lon))
                .icon(icon)
                .snippet(s.id)         // truco: snippet = id para mapear back
        )
        activeMarkers += marker
        markerSchoolBySnippet[s.id] = s.id
        boundsBuilder.include(LatLng(s.lat, s.lon))
    }

    // Recupera la escuela a partir del marker pulsado
    val schoolsById = schools.associateBy { it.id }
    map.setOnMarkerClickListener { marker ->
        val id = marker.snippet ?: return@setOnMarkerClickListener false
        schoolsById[id]?.let(onMarkerTap)
        true   // consumimos el evento; evita que MapLibre abra su infoWindow por defecto
    }

    if (fitBounds) runCatching {
        if (schools.size == 1) {
            // Una sola escuela (p.ej. buscador): encuadrar "bounds" de un único
            // punto hace que MapLibre se vaya al mundo entero. Zoom directo a ella
            // (como iOS), en vez de bounds degenerados.
            val s = schools.first()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(s.lat, s.lon), 13.5), 400)
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 48), 400)
        }
    }
}

/** Color hex del pin según score, igual que la PWA (`scoreColor` en map-panel.js). */
internal fun pinColorHex(score: Int?): String = when {
    score == null -> "#888888"
    score >= 70   -> "#4A7C59"
    score >= 50   -> "#C8843A"
    else          -> "#B94040"
}

/**
 * Genera el pin diamante con el score blanco encima y el nombre de la escuela
 * debajo (con halo blanco para que se lea sobre el mapa). Se construye en
 * código porque MapLibre no acepta vistas Compose como icon, sólo Bitmaps.
 */
/** Puntito de color por score: para zoom país, donde 191 diamantes taparían todo. */
internal fun dotBitmap(score: Int?, density: Float = 2f): Bitmap {
    val k = density / 2f
    val size = (22 * k).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val c = size / 2f
    canvas.drawCircle(c, c, 8f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pinColorHex(score).toColorInt()
    })
    canvas.drawCircle(c, c, 8f * k, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * k
        color = AndroidColor.WHITE
    })
    return bmp
}

internal fun diamondBitmap(score: Int?, name: String?, density: Float = 2f): Bitmap {
    // Todo se dibuja a escala k para que el pin mida lo mismo (en dp) en
    // cualquier pantalla — igual de grande que en iPhone.
    val k = density / 2f
    val pinPx = (64 * k).toInt()
    val label = when {
        name == null -> null
        name.length > 16 -> name.take(15).trimEnd() + "…"
        else -> name
    }

    // Paints del nombre: halo blanco grueso debajo + texto tinta encima.
    val nameHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * k
        color = AndroidColor.WHITE
        textSize = 22f * k
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val nameInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#1A1A1A".toColorInt()
        textSize = 22f * k
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val nameWidth = if (label != null) nameHalo.measureText(label) + 12f * k else 0f
    val widthPx = maxOf(pinPx, nameWidth.toInt())
    val nameHeightPx = if (label != null) (28 * k).toInt() else 0
    val bmp = Bitmap.createBitmap(widthPx, pinPx + nameHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val color = pinColorHex(score).toColorInt()

    // Sombra suave bajo el diamante
    val cx = widthPx / 2f
    val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.argb(60, 0, 0, 0)
    }
    canvas.drawOval(RectF(cx - 22f * k, pinPx - 16f * k, cx + 22f * k, pinPx - 6f * k), shadow)

    // Diamante: cuadrado rotado 45º, esquina inferior es la "punta"
    val side = pinPx * 0.55f
    val cy = pinPx / 2f - 4f * k
    canvas.save()
    canvas.rotate(45f, cx, cy)
    val rect = RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    canvas.drawRoundRect(rect, 6f * k, 6f * k, fill)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * k
        this.color = AndroidColor.WHITE
    }
    canvas.drawRoundRect(rect, 6f * k, 6f * k, stroke)
    canvas.restore()

    // Score blanco centrado
    val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.WHITE
        textSize = 18f * k
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    canvas.drawText(score?.toString() ?: "·", cx, cy + 6f * k, txt)

    // Nombre debajo del pin (halo primero, tinta encima)
    if (label != null) {
        val nameY = pinPx + nameHeightPx - 8f * k
        canvas.drawText(label, cx, nameY, nameHalo)
        canvas.drawText(label, cx, nameY, nameInk)
    }

    return bmp
}

/* ─────────────────────────────────────────────────────────────────────────── */
/*  Tarjeta del marker seleccionado                                            */
/* ─────────────────────────────────────────────────────────────────────────── */

