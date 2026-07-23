@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
            androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.model.Submission
import com.meteomontana.android.domain.usecase.walls.WallRouteStatus
import androidx.compose.runtime.key
import com.meteomontana.android.ui.components.FullScreenMapDialog
import com.meteomontana.android.ui.components.TopoLine
import com.meteomontana.android.ui.components.TopoPhotoCanvas
import com.meteomontana.android.ui.components.parseBloquesJson
import com.meteomontana.android.ui.components.toTopoLines
import com.meteomontana.android.ui.components.pinBitmap
import com.meteomontana.android.ui.components.pinBitmapBoulder
import org.maplibre.android.annotations.IconFactory
import androidx.compose.ui.res.stringResource
import com.meteomontana.android.R
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Mono
import com.meteomontana.android.ui.theme.Moss
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.screens.detail.BoulderBloqueForm
import com.meteomontana.android.ui.screens.detail.ContributionTopoDialog
import com.meteomontana.android.ui.screens.detail.toBloquesJson
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import org.json.JSONArray
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style


// Topo ampliable de la propuesta + caras editables (EDITAR Y APROBAR).
// Reparto del antiguo ContributionCard.kt.

@Composable
internal fun ZoomableTopo(photoUrl: String, lines: List<TopoLine>) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        TopoPhotoCanvas(photoUrl = photoUrl, lines = lines,
            modifier = Modifier.clickable { open = true })
        Text("TOCA PARA AMPLIAR", style = EyebrowTextStyle, color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.xs)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp))
    }
    if (open) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { open = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().background(Color.Black).clickable { open = false }
            ) {
                TopoPhotoCanvas(photoUrl = photoUrl, lines = lines,
                    modifier = Modifier.align(Alignment.Center))
                Text("✕ CERRAR", style = EyebrowTextStyle, color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.md)
                        .clickable { open = false }
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp))
            }
        }
    }
}


/** Caras (fotos) editables de la propuesta, en orden de aparición. Vacía =
 *  no editable (sin vías o sin ninguna foto). Multi-cara: se editan en cadena. */
internal fun editableFacesOf(c: Contribution): List<String> {
    val json = c.bloquesJson?.takeIf { it.isNotBlank() } ?: return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        if (arr.length() == 0) return emptyList()
        val photos = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val p = o.optString("photoUrl").takeIf { it.isNotEmpty() && it != "null" }
            if (p != null && p !in photos) photos.add(p)
        }
        if (photos.isEmpty()) listOfNotNull(c.photoUrl?.takeIf { it.isNotBlank() })
        else photos
    }.getOrElse { emptyList() }
}

/** bloquesJson → formularios editables (nombre/grado/inicio/variante/desc/trazo),
 *  conservando targetLineId y cara para que el round-trip sea fiel. */
internal fun parseBloquesForms(json: String?): List<BoulderBloqueForm> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val startUi = when (o.optString("startType").uppercase()) {
                "STAND", "PIE" -> "PIE"
                "SIT" -> "SIT"
                "SEMI" -> "SEMI"
                "JUMP", "LANCE" -> "LANCE"
                "TRAV" -> "TRAV"
                else -> null
            }
            BoulderBloqueForm(
                name = o.optString("name"),
                grade = o.optString("grade").takeIf { it.isNotEmpty() && it != "null" },
                startType = startUi,
                linePath = parseLineStroke(o.optString("linePath")).points,
                existingLineId = o.optString("targetLineId").takeIf { it.isNotEmpty() && it != "null" },
                facePhoto = o.optString("photoUrl").takeIf { it.isNotEmpty() && it != "null" },
                description = o.optString("description").takeIf { it.isNotEmpty() && it != "null" },
                variant = o.optString("variant").takeIf { it.isNotEmpty() && it != "null" }
            )
        }
    }.getOrElse { emptyList() }
}

