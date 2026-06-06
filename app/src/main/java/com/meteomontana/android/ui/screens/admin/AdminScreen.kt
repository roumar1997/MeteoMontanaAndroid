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
import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.domain.model.AdminLog
import com.meteomontana.android.domain.model.AdminStats
import com.meteomontana.android.domain.model.Submission
import androidx.compose.runtime.key
import com.meteomontana.android.ui.components.FullScreenMapDialog
import com.meteomontana.android.ui.components.TopoPhotoCanvas
import com.meteomontana.android.ui.components.parseBloquesJson
import com.meteomontana.android.ui.components.toTopoLines
import com.meteomontana.android.ui.components.pinBitmap
import com.meteomontana.android.ui.components.pinBitmapBoulder
import org.maplibre.android.annotations.IconFactory
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Moss
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import org.json.JSONArray
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private enum class AdminTab(val label: String) {
    Propuestas("PROPUESTAS"),
    Gestionar("GESTIONAR"),
    Stats("STATS"),
    Activity("ACTIVIDAD"),
    Push("PUSH")
}

@Composable
fun AdminScreen(
    onBack: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(AdminTab.Propuestas) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("Admin",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Menú de tabs como en la PWA: lista vertical de selectores
        TabSelector(tab) { tab = it }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (state.loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }
        state.error?.let { err ->
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(err, color = MaterialTheme.colorScheme.error)
            }
            return@Column
        }

        when (tab) {
            AdminTab.Propuestas -> PropuestasTab(
                submissions = state.pending,
                contributions = state.contributions,
                schoolBlocks = state.schoolBlocks,
                onFetchSchoolBlocks = viewModel::fetchSchoolBlocks,
                onDeleteBlock = viewModel::deleteBlock,
                onUpdateBlock = viewModel::updateBlock,
                onApproveSubmission = viewModel::approve,
                onRejectSubmission = viewModel::reject,
                onApproveContribution = viewModel::approveContribution,
                onRejectContribution = viewModel::rejectContribution
            )
            AdminTab.Gestionar -> GestionarTab(
                allSchools = state.allSchools,
                loading = state.schoolsLoading,
                schoolBlocks = state.schoolBlocks,
                onLoadSchools = viewModel::loadAllSchools,
                onFetchSchoolBlocks = viewModel::fetchSchoolBlocks,
                onDeleteBlock = viewModel::deleteBlock,
                onUpdateBlock = viewModel::updateBlock
            )
            AdminTab.Stats -> StatsTab(state.stats)
            AdminTab.Activity -> ActivityTab(state.logs)
            AdminTab.Push -> PushTab(
                busy = state.pushBusy,
                result = state.pushResult,
                onSend = viewModel::sendPush
            )
        }
    }
}

@Composable
private fun TabSelector(current: AdminTab, onChange: (AdminTab) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        AdminTab.entries.forEach { tab ->
            val selected = tab == current
            val bg = if (selected) MaterialTheme.colorScheme.primary
                     else MaterialTheme.colorScheme.surface
            val fg = if (selected) Color.White
                     else MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg, RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable { onChange(tab) }
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(tab.label, color = fg, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

// ─────────────────────────── PROPUESTAS ────────────────────────────

private enum class ContribFilter(val label: String) {
    TODAS("TODAS"), PIEDRAS("PIEDRAS"), SECTORES("SECTORES"),
    PARKINGS("PARKINGS"), MOVER("MOVER ESCUELA")
}

@Composable
private fun PropuestasTab(
    submissions: List<Submission>,
    contributions: List<ContributionDto>,
    schoolBlocks: Map<String, List<com.meteomontana.android.data.api.dto.BlockDto>>,
    onFetchSchoolBlocks: (String) -> Unit,
    onDeleteBlock: (String, String) -> Unit,
    onUpdateBlock: (String, String, com.meteomontana.android.data.api.dto.CreateBlockRequest, (Boolean) -> Unit) -> Unit,
    onApproveSubmission: (String) -> Unit,
    onRejectSubmission: (String, String?) -> Unit,
    onApproveContribution: (String) -> Unit,
    onRejectContribution: (String, String?) -> Unit
) {
    var filter by remember { mutableStateOf(ContribFilter.TODAS) }

    val filtered = contributions.filter { c ->
        when (filter) {
            ContribFilter.TODAS    -> true
            ContribFilter.PIEDRAS  -> c.type == "BOULDER"
            ContribFilter.SECTORES -> c.type == "SECTOR"
            ContribFilter.PARKINGS -> c.type == "PARKING"
            ContribFilter.MOVER    -> c.type == "POSITION_CORRECTION"
        }
    }

    // Agrupar por escuela
    val bySchool = filtered.groupBy { it.schoolName }
    val total = filtered.size + submissions.size

    LazyColumn(
        contentPadding = PaddingValues(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Contador
        item {
            Text(
                "$total propuestas pendientes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.xs)
            )
        }

        // Chips de filtro
        item {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                ContribFilter.entries.forEach { f ->
                    val sel = f == filter
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (sel) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .clickable { filter = f }
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
                    ) {
                        Text(
                            f.label,
                            style = EyebrowTextStyle,
                            color = if (sel) MaterialTheme.colorScheme.background
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        // School submissions (propuestas de escuelas nuevas) si filtro = TODAS
        if (filter == ContribFilter.TODAS && submissions.isNotEmpty()) {
            item {
                SchoolGroupHeader("ESCUELAS NUEVAS", submissions.size)
            }
            items(submissions) { s ->
                SubmissionCard(s, onApproveSubmission, onRejectSubmission)
            }
            item { Spacer(Modifier.height(Spacing.sm)) }
        }

        // Contributions agrupadas por escuela
        bySchool.forEach { (schoolName, items) ->
            item {
                SchoolGroupHeader(schoolName, items.size)
            }
            items(items) { c ->
                ContributionCard(
                    c = c,
                    existingBlocks = schoolBlocks[c.schoolId] ?: emptyList(),
                    onFetchBlocks = { onFetchSchoolBlocks(c.schoolId) },
                    onDeleteBlock = { blockId -> onDeleteBlock(blockId, c.schoolId) },
                    onUpdateBlock = { b, req -> onUpdateBlock(b.id, c.schoolId, req) {} },
                    onApprove = onApproveContribution,
                    onReject = onRejectContribution
                )
            }
            item { Spacer(Modifier.height(Spacing.xs)) }
        }

        if (filtered.isEmpty() && (filter != ContribFilter.TODAS || submissions.isEmpty())) {
            item {
                Box(Modifier.fillMaxWidth().padding(Spacing.xxl), Alignment.Center) {
                    Text("No hay propuestas pendientes",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SchoolGroupHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "- ${name.uppercase()}",
            style = EyebrowTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // Badge con cantidad
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .background(Terra)
                .padding(horizontal = Spacing.sm, vertical = 2.dp)
        ) {
            Text("$count", style = EyebrowTextStyle, color = Color.White)
        }
    }
}

@Composable
private fun ContributionCard(
    c: ContributionDto,
    existingBlocks: List<com.meteomontana.android.data.api.dto.BlockDto>,
    onFetchBlocks: () -> Unit,
    onDeleteBlock: (String) -> Unit,
    onUpdateBlock: (com.meteomontana.android.data.api.dto.BlockDto, com.meteomontana.android.data.api.dto.CreateBlockRequest) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit
) {
    val onUpdateBlockCard = onUpdateBlock
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var showFullMap by remember { mutableStateOf(false) }

    // Carga bloques existentes de la escuela al renderizar la card (una vez por schoolId).
    LaunchedEffect(c.schoolId) { onFetchBlocks() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .padding(Spacing.md)
    ) {
        // Cabecera: badge tipo + escuela + tiempo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(Terra)
                    .padding(horizontal = Spacing.sm, vertical = 2.dp)
            ) {
                Text(c.type, style = EyebrowTextStyle, color = Color.White)
            }
            Text(c.schoolName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f))
            Text("ahora",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(Spacing.sm))

        // Nombre propuesto
        c.name?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }

        // Notas
        c.notes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }

        // Coordenadas
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "${"%.6f".format(c.lat)}, ${"%.6f".format(c.lon)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Autor
        c.submittedByName?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text("por $it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ── BOULDER: foto con líneas superpuestas + bloques ─────────────────────
        if (c.type == "BOULDER") {
            // Si es propuesta de AÑADIR VÍAS, usamos la foto del bloque existente
            val isAddLines = !c.targetBlockId.isNullOrBlank()
            val targetBlock = if (isAddLines)
                existingBlocks.firstOrNull { it.id == c.targetBlockId } else null

            val photoForCanvas = when {
                isAddLines && targetBlock?.photoPath != null -> targetBlock.photoPath
                else -> c.photoUrl
            }

            if (!photoForCanvas.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                if (isAddLines) {
                    Text("AÑADIR VÍAS A \"${targetBlock?.name ?: "?"}\"",
                        style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.height(Spacing.xs))
                }
                // Para "añadir vías": dibujamos las líneas existentes en gris translúcido
                // y encima las nuevas con sus colores normales — así el admin ve qué se añade.
                val existingLines = if (isAddLines)
                    (targetBlock?.lines ?: emptyList()).toTopoLines() else emptyList()
                val newLines = parseBloquesJson(c.bloquesJson)
                TopoPhotoCanvas(
                    photoUrl = photoForCanvas,
                    lines = existingLines + newLines
                )
            }
            c.bloquesJson?.takeIf { it.isNotBlank() }?.let { json ->
                Spacer(Modifier.height(Spacing.sm))
                if (isAddLines) {
                    Text("NUEVAS VÍAS PROPUESTAS:",
                        style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xs))
                }
                BloquesSummary(json)
            }
        }

        // Mini-mapa
        Spacer(Modifier.height(Spacing.sm))
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                val mv = mapViewRef.value ?: return@LifecycleEventObserver
                when (event) {
                    Lifecycle.Event.ON_START   -> mv.onStart()
                    Lifecycle.Event.ON_RESUME  -> mv.onResume()
                    Lifecycle.Event.ON_PAUSE   -> mv.onPause()
                    Lifecycle.Event.ON_STOP    -> mv.onStop()
                    Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                mapViewRef.value?.apply { onPause(); onStop(); onDestroy() }
                mapViewRef.value = null
            }
        }
        // key() re-crea el mini-mapa cuando llegan los bloques existentes
        key(existingBlocks.size) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                factory = { context ->
                    MapView(context).apply {
                        onCreate(null)
                        mapViewRef.value = this
                        getMapAsync { map ->
                            map.setStyle(Style.Builder().fromJson(OSM_STYLE)) {
                                map.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(c.lat, c.lon)).zoom(14.0).build()

                                val iconFactory = IconFactory.getInstance(context)
                                // Bloques existentes: parking=círculo azul P, zone=círculo verde Z,
                                // block=polígono terra con el nombre de la piedra
                                existingBlocks.forEach { b ->
                                    val icon = when (b.type) {
                                        "PARKING" -> pinBitmap(android.graphics.Color.parseColor("#1D6DD6"), "P", 28)
                                        "ZONE"    -> pinBitmap(android.graphics.Color.parseColor("#1FA84E"), "Z", 28)
                                        else      -> pinBitmapBoulder(
                                            label = b.name.takeIf { it.isNotBlank() } ?: "?",
                                            fillColor = android.graphics.Color.parseColor("#C2410C"),
                                            sizeDp = 36
                                        )
                                    }
                                    map.addMarker(
                                        MarkerOptions()
                                            .position(LatLng(b.lat, b.lon))
                                            .title("[${b.type}] ${b.name}")
                                            .icon(iconFactory.fromBitmap(icon))
                                    )
                                }
                                // Marker de la propuesta (amarillo destacado).
                                // Si es BOULDER, usa forma de piedra; si no, círculo.
                                val proposalIcon = if (c.type == "BOULDER") {
                                    pinBitmapBoulder(
                                        label = c.name?.takeIf { it.isNotBlank() } ?: "★",
                                        fillColor = android.graphics.Color.parseColor("#F59E0B"),
                                        sizeDp = 42
                                    )
                                } else {
                                    pinBitmap(android.graphics.Color.parseColor("#F59E0B"), "★", 40)
                                }
                                map.addMarker(
                                    MarkerOptions()
                                        .position(LatLng(c.lat, c.lon))
                                        .title("PROPUESTA · ${c.name ?: c.type}")
                                        .icon(iconFactory.fromBitmap(proposalIcon))
                                )
                            }
                            map.uiSettings.apply {
                                isScrollGesturesEnabled  = false
                                isZoomGesturesEnabled    = false
                                isRotateGesturesEnabled  = false
                            }
                        }
                        onStart(); onResume()
                    }
                }
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        // Botones: VER EN MAPA | RECHAZAR | APROBAR
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            // VER EN MAPA → mapa interactivo dentro de la app
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                    .clickable {
                        onFetchBlocks()  // carga bloques existentes de la escuela
                        showFullMap = true
                    }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("VER EN MAPA", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            // RECHAZAR
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                    .clickable { onReject(c.id, null) }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("RECHAZAR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.error)
            }
            // APROBAR
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Moss)
                    .clickable { onApprove(c.id) }
                    .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center
            ) {
                Text("APROBAR", style = EyebrowTextStyle, color = Color.White)
            }
        }
    }

    // Mapa a pantalla completa al pulsar "VER EN MAPA"
    if (showFullMap) {
        FullScreenMapDialog(
            lat = c.lat,
            lon = c.lon,
            markerTitle = c.name ?: "${c.type} · ${c.schoolName}",
            existingBlocks = existingBlocks,
            proposalAsBlock = c.toFakeBlockDto(),
            onDeleteBlock = onDeleteBlock,
            onUpdateBlock = onUpdateBlockCard,
            onDismiss = { showFullMap = false }
        )
    }
}

/**
 * Convierte la contribución en un `BlockDto` "fantasma" para reusar el dialog
 * de detalles del mapa. Útil para que el admin vea exactamente lo mismo que
 * verá el usuario tras aprobar.
 */
private fun ContributionDto.toFakeBlockDto(): com.meteomontana.android.data.api.dto.BlockDto {
    val blockType = when (type) {
        "PARKING" -> "PARKING"
        "SECTOR"  -> "ZONE"
        else      -> "BLOCK"  // BOULDER y POSITION_CORRECTION
    }
    val lines = if (type == "BOULDER" && !bloquesJson.isNullOrBlank()) {
        try {
            val arr = JSONArray(bloquesJson)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                com.meteomontana.android.data.api.dto.BlockLineDto(
                    id = "proposal-line-$i",
                    name = o.optString("name", ""),
                    grade = o.optString("grade").takeIf { it.isNotEmpty() && it != "null" },
                    startType = o.optString("startType").takeIf { it.isNotEmpty() && it != "null" },
                    linePath = o.optString("linePath"),
                    sortOrder = i
                )
            }
        } catch (_: Throwable) { emptyList() }
    } else emptyList()
    return com.meteomontana.android.data.api.dto.BlockDto(
        id = id,
        schoolId = schoolId,
        type = blockType,
        name = name?.takeIf { it.isNotBlank() } ?: "?",
        lat = lat, lon = lon,
        photoPath = photoUrl,
        description = notes,
        createdByUid = "",
        createdAt = createdAt ?: "",
        lines = lines
    )
}

@Composable
private fun SubmissionCard(
    s: Submission,
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                .padding(horizontal = Spacing.sm, vertical = 2.dp)) {
                Text("ESCUELA", style = EyebrowTextStyle, color = Color.White)
            }
            Text(s.proposedName, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "${"%.5f".format(s.proposedLat)}, ${"%.5f".format(s.proposedLon)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        s.notes?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(Spacing.xs))
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Spacing.md))
        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                .clickable { onReject(s.id, null) }.padding(Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text("RECHAZAR", style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.error)
            }
            Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(2.dp))
                .background(Moss).clickable { onApprove(s.id) }.padding(Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text("APROBAR", style = EyebrowTextStyle, color = Color.White)
            }
        }
    }
}

/** Lista compacta de bloques parseando el JSON de la contribución. */
@Composable
private fun BloquesSummary(bloquesJson: String) {
    data class BloqueInfo(val name: String, val grade: String?, val startType: String?)

    val bloques = remember(bloquesJson) {
        try {
            val arr = JSONArray(bloquesJson)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BloqueInfo(
                    name = o.optString("name", ""),
                    grade = o.optString("grade").ifEmpty { null }.takeIf { it != "null" },
                    startType = o.optString("startType").ifEmpty { null }.takeIf { it != "null" }
                )
            }
        } catch (_: Throwable) { emptyList() }
    }

    if (bloques.isEmpty()) return

    Text("BLOQUES", style = EyebrowTextStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        bloques.forEachIndexed { idx, b ->
            val style = gradeStyle(b.grade)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(style.stroke)
                )
                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (b.grade != null) {
                    Text(b.grade, style = MaterialTheme.typography.labelMedium,
                        color = style.stroke)
                }
                if (b.startType != null) {
                    Text("· ${b.startType}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (b.name.isNotBlank()) {
                    Text("\"${b.name}\"", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private const val OSM_STYLE = """{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}"""

// ─────────────────────────── GESTIONAR ────────────────────────────

/**
 * Tab "GESTIONAR" — el admin busca una escuela y abre su mapa interactivo
 * con todos los bloques. Desde el mapa puede tocar cada bloque y borrarlo.
 */
@Composable
private fun GestionarTab(
    allSchools: List<com.meteomontana.android.data.api.dto.SchoolDto>,
    loading: Boolean,
    schoolBlocks: Map<String, List<com.meteomontana.android.data.api.dto.BlockDto>>,
    onLoadSchools: () -> Unit,
    onFetchSchoolBlocks: (String) -> Unit,
    onDeleteBlock: (String, String) -> Unit,
    onUpdateBlock: (String, String, com.meteomontana.android.data.api.dto.CreateBlockRequest, (Boolean) -> Unit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedSchool by remember {
        mutableStateOf<com.meteomontana.android.data.api.dto.SchoolDto?>(null)
    }

    LaunchedEffect(Unit) { onLoadSchools() }

    val filtered = remember(query, allSchools) {
        if (query.isBlank()) allSchools
        else allSchools.filter {
            it.name.contains(query, ignoreCase = true) ||
            (it.location?.contains(query, ignoreCase = true) == true) ||
            (it.region?.contains(query, ignoreCase = true) == true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        Text("Buscar escuela por nombre, lugar o región",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ej: Albarracín, Madrid…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = MaterialTheme.shapes.small
        )
        Spacer(Modifier.height(Spacing.sm))

        if (loading && allSchools.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(Spacing.lg), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(
                "${filtered.size} escuela${if (filtered.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.xs))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { school ->
                    SchoolListRow(school) {
                        onFetchSchoolBlocks(school.id)
                        selectedSchool = school
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }

    // Mapa fullscreen de la escuela seleccionada, con BORRAR + EDITAR habilitados
    selectedSchool?.let { school ->
        FullScreenMapDialog(
            lat = school.lat,
            lon = school.lon,
            markerTitle = school.name,
            existingBlocks = schoolBlocks[school.id] ?: emptyList(),
            onDeleteBlock = { blockId -> onDeleteBlock(blockId, school.id) },
            onUpdateBlock = { block, req -> onUpdateBlock(block.id, school.id, req) {} },
            onDismiss = { selectedSchool = null }
        )
    }
}

@Composable
private fun SchoolListRow(
    school: com.meteomontana.android.data.api.dto.SchoolDto,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(school.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            val subtitle = listOfNotNull(school.location, school.region)
                .joinToString(" · ")
                .ifEmpty { school.style ?: "" }
            if (subtitle.isNotBlank()) {
                Text(subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("▸", style = MaterialTheme.typography.titleMedium,
            color = Terra)
    }
}

// ─────────────────────────── STATS ────────────────────────────
@Composable
private fun StatsTab(stats: AdminStats?) {
    if (stats == null) return
    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("USUARIOS", stats.totalUsers, Modifier.weight(1f))
            StatCard("ADMINS", stats.totalAdmins, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("ESCUELAS", stats.totalSchools, Modifier.weight(1f))
            StatCard("NOTAS", stats.totalNotes, Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("PENDING", stats.submissionsPending, Modifier.weight(1f))
            StatCard("APROBADAS", stats.submissionsApproved, Modifier.weight(1f))
            StatCard("RECHAZADAS", stats.submissionsRejected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(),
            fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─────────────────────────── ACTIVITY ────────────────────────────
@Composable
private fun ActivityTab(logs: List<AdminLog>) {
    if (logs.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("Sin actividad",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn {
        items(logs) { log ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(log.action,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground)
                Text("${log.targetType}/${log.targetId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                log.details?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                Text(log.createdAt.take(16),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ─────────────────────────── PUSH ────────────────────────────
@Composable
private fun PushTab(
    busy: Boolean,
    result: String?,
    onSend: (String?, String, String) -> Unit
) {
    var target by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
           verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enviar push", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)
        Text("Si dejas Target UID vacío, se manda a TODOS los usuarios con token.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(value = target, onValueChange = { target = it },
            placeholder = { Text("Target UID (opcional)") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = title, onValueChange = { title = it },
            placeholder = { Text("Título") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = body, onValueChange = { body = it },
            placeholder = { Text("Mensaje") },
            modifier = Modifier.fillMaxWidth().height(120.dp))

        Button(
            onClick = { onSend(target.takeIf { it.isNotBlank() }, title, body) },
            enabled = !busy && title.isNotBlank() && body.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1C1C1A), contentColor = Color.White
            ),
            shape = MaterialTheme.shapes.small
        ) {
            Text(if (busy) "Enviando..." else "ENVIAR PUSH")
        }
        result?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
