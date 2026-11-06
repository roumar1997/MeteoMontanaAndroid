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

/** Botonera lateral del mapa de una escuela (estilo Radar): centrar, satélite/topográfico, capas y «dónde estoy». */
@Composable
internal fun SchoolMapSideButtons(
    hiddenTypes: Set<String>,
    onRecenter: () -> Unit,
    onToggleStyle: () -> Unit,
    onToggleLayer: (String) -> Unit,
    /** null = sin ubicación (no sale el botón). */
    onGoToMe: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        // ARRIBA a la derecha, bajo PROPONER — centrada se solapaba
        // con el botón de ubicación (abajo a la derecha).
        modifier = modifier
            .padding(top = 48.dp, end = Spacing.sm, bottom = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Re-centrar en la escuela (vuelta al encuadre inicial).
        SideMapButton(active = true, onClick = onRecenter) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.GpsFixed,
                contentDescription = stringResource(R.string.school_map_view_v2_centrar_en_la_escuela),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp))
        }
        SideMapButton(active = true, onClick = onToggleStyle) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Outlined.Layers,
                contentDescription = stringResource(R.string.school_map_view_topografico_satelite),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp))
        }
        SideMapButton(active = "PARKING" !in hiddenTypes,
            onClick = { onToggleLayer("PARKING") }) { ParkingShapeIcon() }
        SideMapButton(active = "BLOCK" !in hiddenTypes,
            onClick = { onToggleLayer("BLOCK") }) { StoneShapeIcon() }
        SideMapButton(active = "ZONE" !in hiddenTypes,
            onClick = { onToggleLayer("ZONE") }) { ZoneShapeIcon() }
        if (onGoToMe != null) {
            SideMapButton(active = true, onClick = onGoToMe) {
                Text("◎", color = Color(0xFF1A56DB),
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
