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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Close
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.meteomontana.android.ui.components.ScoreRing
import com.meteomontana.android.ui.screens.gyms.GymActionPill
import com.meteomontana.android.ui.screens.gyms.GymTag
import com.meteomontana.android.ui.theme.Serif

// Tarjeta de la escuela tocada en el mapa de Escuelas y sus botones y chips.

/**
 * Tarjeta de la escuela tocada en el mapa de Roca: la misma de cristal que la de rocódromos (radio 24, fondo del tema
 * al 92 %, canto blanco al 55 %). La nota de hoy va en el anillo de la izquierda, en el sitio del icono.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun MarkerPreviewCard(
    school: School,
    score: Int?,
    wet: Boolean = false,
    onFix: () -> Unit = {},
    userLat: Double?,
    userLon: Double?,
    onClose: () -> Unit,
    onSchoolDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val distKm = haversineKm(userLat, userLon, school.lat, school.lon)
    val shape = RoundedCornerShape(24.dp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .shadow(12.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreRing(score, wet = wet)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    school.name, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 21.sp,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    school.rockType?.let { GymTag(CatalogLabels.rock(it), muted) }
                    school.style?.let { GymTag(it, muted) }
                    distKm?.let { GymTag("${it.toInt()} km", muted) }
                }
            }
            Row(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
                    .clickable(onClick = onClose),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Close, stringResource(R.string.common_close),
                    tint = muted, modifier = Modifier.size(16.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GymActionPill(
                stringResource(R.string.gym_directions),
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${school.lat},${school.lon}")
                    )
                    runCatching { ctx.startActivity(intent) }
                },
                modifier = Modifier.weight(1f), primary = true, icon = R.drawable.ic_directions
            )
            GymActionPill(stringResource(R.string.schools_map_panel_v3_ver_detalle), onSchoolDetail, Modifier.weight(1f))
        }
        // Corregir o aportar sobre esta escuela: abre la hoja «Aportar» de siempre.
        Text(
            stringResource(R.string.schools_card_fix), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).clickable(onClick = onFix).padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun Tag(text: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(
            text.uppercase(),
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun OutlinedAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline,
                androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
internal fun FilledAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            color = androidx.compose.ui.graphics.Color.White)
    }
}

/* ─────────────────────────────────────────────────────────────────────────── */

/** Estilos MapLibre raster (fuente única en MapStyles). Mismo origen que la PWA. */
internal val OSM_RASTER_STYLE get() = MapStyles.osmPaper
internal val DARK_RASTER_STYLE get() = MapStyles.darkPaper
internal val SATELLITE_RASTER_STYLE get() =
    MapStyles.raster("esri", MapStyles.SATELLITE, "© Esri", MapStyles.PAPER_BG)

@Composable
internal fun MapStyleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline,
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}
