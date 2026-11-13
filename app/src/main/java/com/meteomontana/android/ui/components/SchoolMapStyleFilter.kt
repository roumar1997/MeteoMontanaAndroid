package com.meteomontana.android.ui.components

import com.meteomontana.android.util.AppText
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.meteomontana.android.ui.theme.CumbrePillShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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

// Filtro Vía/Bloque del mapa de una escuela (fila de chips y qué piedras pasan) y texto de propuesta enviada.

// ── Filtro Vía/Bloque de la escuela ─────────────────────────────────────
// Paridad con styleFilterRow/matchesStyleFilter de SchoolMapSection.swift.

@Composable
internal fun StyleFilterRow(
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    Row(
        // Centrado y con aire arriba y abajo (Álvaro, 2026-11-13: iba pegado a la izquierda).
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        // Mismas etiquetas que styleLabelForDiscipline (antes "Bloque" fijo: en inglés no casaba).
        listOf(stringResource(R.string.w_route), stringResource(R.string.w_boulder)).forEachIndexed { i, opt ->
            val active = opt in selected
            val fg = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .clip(CumbrePillShape)
                    .background(if (active) Terra else MaterialTheme.colorScheme.surface, CumbrePillShape)
                    .border(1.dp, if (active) Terra else MaterialTheme.colorScheme.outline, CumbrePillShape)
                    .clickable { onToggle(opt) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Vía = mosquetón/cuerda (eslabón); Bloque = piedra/crashpad (cubo).
                // Los mismos dibujos de la app: mosquetón (vía) y crashpad (bloque), como en la lista de escuelas y el mapa.
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(if (i == 0) com.meteomontana.android.R.drawable.kind_route else com.meteomontana.android.R.drawable.kind_boulder),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(fg),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.height(14.dp),
                )
                Text(opt.uppercase(), style = EyebrowTextStyle, color = fg, maxLines = 1)
            }
        }
    }
}

/** "ROUTE" en el backend = estilo "Vía"; "BOULDER" = "Bloque". */
internal fun styleLabelForDiscipline(discipline: String): String =
    if (discipline.uppercase() == "ROUTE") AppText.get(R.string.w_route) else AppText.get(R.string.w_boulder)

/**
 * ¿Esta piedra/sector pasa el filtro Vía/Bloque? Vacío = sin filtrar. Un
 * sector sin piedras dentro (sectorDisciplines vacío/null) siempre se ve —
 * no hay disciplina que comparar todavía (evita que desaparezca).
 */
internal fun matchesStyleFilter(b: Block, styleFilter: Set<String>): Boolean {
    if (styleFilter.isEmpty()) return true
    return when (b.type.uppercase()) {
        "BLOCK" -> styleLabelForDiscipline(b.discipline) in styleFilter
        "ZONE" -> {
            val disciplines = b.sectorDisciplines ?: emptyList()
            if (disciplines.isEmpty()) true
            else disciplines.any { styleLabelForDiscipline(it) in styleFilter }
        }
        else -> true
    }
}
/** Texto tras enviar desde el mapa: las personas de confianza publican directo; el resto espera revisión. */
internal fun submitSuccessText(): String = when {
    com.meteomontana.android.domain.usecase.contributions.ContributionOutcome.publishedDirectly ->
        AppText.get(R.string.school_map_v3_publicado_de_confianza)
    else -> AppText.get(R.string.school_map_v3_propuesta_enviada_un_admin_la)
}
