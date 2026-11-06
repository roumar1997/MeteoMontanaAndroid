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

/** Avisos del flujo de proponer encima del mapa: pulsar en el mapa, corregir posición, «la foto se hizo aquí» y trazar el muro. */
@Composable
internal fun ProposalFlowBanners(bridge: ProposalMapBridge, wallEdit: WallEditState) {
        // Banner contextual con estado preciso del flujo.
        if (bridge.waitingMapTap || bridge.correctionMode) {
            val bannerText = when {
                !bridge.correctionMode -> stringResource(R.string.school_map_view_v3_pulsa_en_el_mapa_en)
                bridge.correctionTargetName == null ->
                    stringResource(R.string.school_map_view_v3_pulsa_el_marker_piedra_parking)
                bridge.correctionGhost?.newLat == null ->
                    stringResource(R.string.school_map_view_v3_has_pulsado_ahora_pulsa_la, bridge.correctionTargetName ?: "")
                else ->
                    stringResource(R.string.school_map_view_v3_posicion_fijada_para_pulsa_otra, bridge.correctionTargetName ?: "")
            }
            Column(
                modifier = Modifier.fillMaxWidth().background(terraFillColor())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bannerText, style = EyebrowTextStyle, color = Color.White,
                        modifier = Modifier.weight(1f))
                    Text(" ✕", color = Color.White, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = bridge::reset))
                }
                // Botón ACEPTAR cuando hay posición candidata fijada.
                val ghost = bridge.correctionGhost
                if (ghost?.newLat != null && ghost.newLon != null) {
                    Spacer(Modifier.size(Spacing.sm))
                    Box(modifier = Modifier.fillMaxWidth()
                        .background(Color.White)
                        .clickable(onClick = bridge::acceptCorrection)
                        .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.school_map_view_aceptar), style = EyebrowTextStyle, color = Terra)
                    }
                }
            }
        }

        // Banner de "la foto se hizo aqui": la piedra ya esta puesta en el mapa
        // (marcador fantasma) y solo falta decir si el sitio vale.
        bridge.photoConfirm?.let { _ ->
            Column(
                modifier = Modifier.fillMaxWidth().background(terraFillColor())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Text(stringResource(R.string.school_map_view_la_foto_se_hizo),
                    style = EyebrowTextStyle, color = Color.White)
                Spacer(Modifier.size(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(modifier = Modifier.weight(1f)
                        .background(Color.White)
                        .clickable { bridge.photoAccept?.invoke() }
                        .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.school_map_view_si_sigue), style = EyebrowTextStyle, color = Terra) }
                    Box(modifier = Modifier.weight(1f)
                        .border(1.dp, Color.White)
                        .clickable { bridge.photoMove?.invoke() }
                        .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.school_map_view_v2_moverla), style = EyebrowTextStyle, color = Color.White) }
                }
            }
        }

        // Banner del trazado de muro: cada tap añade un punto; DESHACER / LISTO.
        // Sirve para el flujo CREAR (bridge.wallTracing) y el de EDITAR (wallEdit.tracing).
        if (bridge.wallTracing || wallEdit.tracing) {
            val pv = if (wallEdit.tracing) wallEdit.preview else bridge.wallPreview
            val onUndo: () -> Unit = if (wallEdit.tracing) wallEdit::undoPoint else bridge::wallUndo
            val onDone: () -> Unit = if (wallEdit.tracing) wallEdit::finishTracing else bridge::wallDone
            val onCancel: () -> Unit = if (wallEdit.tracing) wallEdit::cancelTracing else bridge::reset
            Column(
                modifier = Modifier.fillMaxWidth().background(terraFillColor())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.school_map_view_v2_traza_el_muro_1_s, pv.size),
                        style = EyebrowTextStyle, color = Color.White,
                        modifier = Modifier.weight(1f))
                    Text(" ✕", color = Color.White, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(onClick = onCancel))
                }
                Spacer(Modifier.size(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(modifier = Modifier.weight(1f)
                        .background(Color.White.copy(alpha = if (pv.isEmpty()) 0.4f else 1f))
                        .clickable(enabled = pv.isNotEmpty(), onClick = onUndo)
                        .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.school_map_view_v2_deshacer), style = EyebrowTextStyle, color = Terra)
                    }
                    Box(modifier = Modifier.weight(1f)
                        .background(Color.White.copy(alpha = if (pv.size >= 2) 1f else 0.4f))
                        .clickable(enabled = pv.size >= 2, onClick = onDone)
                        .padding(vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.school_map_view_listo), style = EyebrowTextStyle, color = Terra)
                    }
                }
            }
        }
}
