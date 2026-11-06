package com.meteomontana.android.ui.components

import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.theme.terraFillColor
import com.meteomontana.android.data.map.MapStyles
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.Block
import com.meteomontana.android.ui.screens.detail.SchoolDetailViewModel
import com.meteomontana.android.ui.screens.detail.ProposeContributionFlow
import com.meteomontana.android.ui.screens.detail.AddLinesFlow
import com.meteomontana.android.ui.guide.guideTarget
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.meteomontana.android.domain.util.Geo

// Estilos del mapa de una escuela (satélite/topográfico), zooms y encuadre inicial.


internal enum class MapStyleOption(val labelResId: Int) {
    SATELLITE(R.string.map_satellite),
    TOPO(R.string.map_topo)
}

internal fun styleJsonFor(style: MapStyleOption): String = when (style) {
    MapStyleOption.SATELLITE -> MapStyles.satellitePaper
    MapStyleOption.TOPO      -> MapStyles.topoPaper
}

// Si el usuario está más lejos de esto del centro de los elementos de la
// escuela, su ubicación NO entra en el encuadre inicial (evita que "verla
// desde casa" estire el zoom para abarcarte a ti también).
internal const val MAX_USER_LOCATION_INCLUDE_KM = 20.0

// ── Cuánto se acerca el mapa al tocar algo ───────────────────────────────────
// Son MÍNIMOS, nunca máximos: si el usuario ya está más cerca, se respeta su
// zoom y solo se centra. Forzar un zoom exacto le deshacía el trabajo — se
// acercaba con los dedos, tocaba una piedra y el mapa le alejaba de vuelta.
//
// Zoom FIJO y no encuadre calculado a propósito: los encuadres automáticos se
// iban a los extremos según cómo estuvieran repartidos los datos (una piedra
// despistada a 2 km dejaba el resto diminuto).
/** Al tocar un SECTOR: lo bastante cerca para distinguir sus piedras sueltas. */
internal const val ZOOM_SECTOR = 16.5
/** Al tocar una PIEDRA: un punto más cerca, ya estás mirando una en concreto. */
internal const val ZOOM_PIEDRA = 17.0

/**
 * Encuadre inicial del mapa: TODOS los elementos de la escuela (parkings,
 * sectores, piedras), por muy separados que estén — antes era zoom fijo 15
 * centrado en la escuela, que dejaba fuera sectores a kilómetros. La
 * ubicación del usuario se incluye SOLO si ya está razonablemente cerca.
 */
internal fun fitSchoolBoundsCameraUpdate(
    markers: List<Block>,
    userLoc: com.meteomontana.android.domain.model.UserLocation?,
    padding: Int = 90
): org.maplibre.android.camera.CameraUpdate? {
    if (markers.isEmpty()) return null
    val boundsBuilder = LatLngBounds.Builder()
    markers.forEach { boundsBuilder.include(LatLng(it.lat, it.lon)) }
    if (userLoc != null) {
        val avgLat = markers.map { it.lat }.average()
        val avgLon = markers.map { it.lon }.average()
        val distKm = Geo.haversineKm(userLoc.lat, userLoc.lon, avgLat, avgLon)
        if (distKm <= MAX_USER_LOCATION_INCLUDE_KM) {
            boundsBuilder.include(LatLng(userLoc.lat, userLoc.lon))
        }
    }
    return try {
        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), padding)
    } catch (_: Exception) {
        // Un único punto (o todos coincidentes) → bounds degenerado, cae a zoom fijo.
        val first = markers.first()
        CameraUpdateFactory.newLatLngZoom(LatLng(first.lat, first.lon), 15.0)
    }
}

// ─── SchoolMapView (el mapa MapLibre en si) ─────────────────────────────────────────────────────────────────
