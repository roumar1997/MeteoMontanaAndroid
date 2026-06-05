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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.meteomontana.android.data.api.dto.AdminLogDto
import com.meteomontana.android.data.api.dto.AdminStatsDto
import com.meteomontana.android.data.api.dto.ContributionDto
import com.meteomontana.android.data.api.dto.SubmissionDto
import com.meteomontana.android.ui.components.FullScreenMapDialog
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Moss
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private enum class AdminTab(val label: String) {
    Propuestas("PROPUESTAS"),
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
                onApproveSubmission = viewModel::approve,
                onRejectSubmission = viewModel::reject,
                onApproveContribution = viewModel::approveContribution,
                onRejectContribution = viewModel::rejectContribution
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
    submissions: List<SubmissionDto>,
    contributions: List<ContributionDto>,
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
                ContributionCard(c, onApproveContribution, onRejectContribution)
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
    onApprove: (String) -> Unit,
    onReject: (String, String?) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    var showFullMap by remember { mutableStateOf(false) }

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
                            map.addMarker(MarkerOptions().position(LatLng(c.lat, c.lon))
                                .title(c.name ?: c.type))
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
                    .clickable { showFullMap = true }
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
            onDismiss = { showFullMap = false }
        )
    }
}

@Composable
private fun SubmissionCard(
    s: SubmissionDto,
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

private const val OSM_STYLE = """{"version":8,"sources":{"osm":{"type":"raster","tiles":["https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256}},"layers":[{"id":"osm","type":"raster","source":"osm"}]}"""

// ─────────────────────────── STATS ────────────────────────────
@Composable
private fun StatsTab(stats: AdminStatsDto?) {
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
private fun ActivityTab(logs: List<AdminLogDto>) {
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
