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
import com.meteomontana.android.domain.model.MeetupReport
import com.meteomontana.android.domain.model.Contribution
import com.meteomontana.android.domain.model.School
import com.meteomontana.android.domain.model.Submission
import androidx.compose.runtime.key
import com.meteomontana.android.ui.components.FullScreenMapDialog
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
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.colorForGrade
import com.meteomontana.android.ui.theme.gradeStyle
import org.json.JSONArray
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style


@Composable
internal fun StatsTab(
    stats: AdminStats?,
    users: List<com.meteomontana.android.data.api.AdminUserRowDto>? = null,
    notes: List<com.meteomontana.android.data.api.AdminNoteRowDto>? = null,
    onLoadUsers: () -> Unit = {},
    onLoadNotes: () -> Unit = {},
    onOpenUserProfile: (String) -> Unit = {},
    onOpenSchool: (String) -> Unit = {},
    onGoToTab: (String) -> Unit = {}
) {
    if (stats == null) return
    // Qué lista está abierta: "users" / "notes" / null.
    var openList by remember { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
           verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Toca una tarjeta para ver su lista",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("USUARIOS", stats.totalUsers, Modifier.weight(1f)) {
                onLoadUsers(); openList = "users"
            }
            StatCard("ADMINS", stats.totalAdmins, Modifier.weight(1f)) {
                onLoadUsers(); openList = "admins"
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("ESCUELAS", stats.totalSchools, Modifier.weight(1f)) { onGoToTab("gestionar") }
            StatCard("NOTAS", stats.totalNotes, Modifier.weight(1f)) {
                onLoadNotes(); openList = "notes"
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("PENDING", stats.submissionsPending, Modifier.weight(1f)) { onGoToTab("propuestas") }
            StatCard("APROBADAS", stats.submissionsApproved, Modifier.weight(1f)) { onGoToTab("actividad") }
            StatCard("RECHAZADAS", stats.submissionsRejected, Modifier.weight(1f)) { onGoToTab("actividad") }
        }
    }

    // Lista en diálogo a pantalla casi completa.
    openList?.let { kind ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { openList = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(Spacing.md)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(when (kind) {
                        "users" -> "USUARIOS"; "admins" -> "ADMINS"; else -> "NOTAS"
                    }, style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("✕ CERRAR", style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(2.dp))
                            .clickable { openList = null }.padding(4.dp))
                }
                Spacer(Modifier.height(Spacing.sm))
                when (kind) {
                    "users", "admins" -> {
                        val list = users
                        if (list == null) {
                            Box(Modifier.fillMaxWidth().padding(Spacing.lg), Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            val shown = if (kind == "admins") list.filter { it.isAdmin } else list
                            LazyColumn {
                                items(shown.size) { i ->
                                    val u = shown[i]
                                    Row(Modifier.fillMaxWidth()
                                        .clickable { u.uid.let(onOpenUserProfile) }
                                        .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column {
                                            Text(u.username?.let { "@" + it } ?: (u.displayName ?: u.uid.take(10)),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface)
                                            if (u.isAdmin) Text("ADMIN",
                                                style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                                                color = com.meteomontana.android.ui.theme.Terra)
                                        }
                                        Text(u.createdAt?.take(10) ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                    else -> {
                        val list = notes
                        if (list == null) {
                            Box(Modifier.fillMaxWidth().padding(Spacing.lg), Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            LazyColumn {
                                items(list.size) { i ->
                                    val n = list[i]
                                    Column(Modifier.fillMaxWidth()
                                        .clickable { n.schoolId?.let(onOpenSchool) }
                                        .padding(vertical = 8.dp)) {
                                        Text(n.text, style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text(listOfNotNull(n.author, n.schoolId, n.createdAt?.take(10))
                                                .joinToString(" · "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Long, modifier: Modifier = Modifier,
                     onClick: (() -> Unit)? = null) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(2.dp)).clickable(onClick = onClick) else Modifier)
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
internal fun ActivityTab(logs: List<AdminLog>) {
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
internal fun PushTab(
    busy: Boolean,
    result: String?,
    userResults: List<com.meteomontana.android.domain.model.PublicProfile> = emptyList(),
    onSearchUser: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onSend: (String?, String, String) -> Unit
) {
    // Destinatario elegido (null = TODOS los usuarios).
    var targetUid by remember { mutableStateOf<String?>(null) }
    var targetLabel by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp),
           verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enviar push", style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)

        // Destinatario: buscador de usuarios o TODOS.
        if (targetUid == null) {
            OutlinedTextField(value = query,
                onValueChange = { query = it; onSearchUser(it) },
                placeholder = { Text("Buscar destinatario por @usuario o nombre…") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            if (userResults.isNotEmpty() && query.trim().length >= 2) {
                Column(Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))) {
                    userResults.take(6).forEach { u ->
                        Row(Modifier.fillMaxWidth()
                            .clickable {
                                targetUid = u.uid
                                targetLabel = u.username?.let { "@" + it } ?: u.displayName
                                query = ""; onClearSearch()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(u.username?.let { "@" + it } ?: (u.displayName ?: u.uid.take(8)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Text("Sin destinatario elegido → se enviará a TODOS los usuarios.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PARA: " + (targetLabel ?: targetUid),
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = com.meteomontana.android.ui.theme.Terra)
                Text("✕ QUITAR",
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(2.dp))
                        .clickable { targetUid = null; targetLabel = null }
                        .padding(4.dp))
            }
        }
        OutlinedTextField(value = title, onValueChange = { title = it },
            placeholder = { Text("Título") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = body, onValueChange = { body = it },
            placeholder = { Text("Mensaje") },
            modifier = Modifier.fillMaxWidth().height(120.dp))

        Button(
            onClick = {
                if (targetUid == null) confirmAll = true
                else onSend(targetUid, title, body)
            },
            enabled = !busy && title.isNotBlank() && body.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1C1C1A), contentColor = Color.White
            ),
            shape = MaterialTheme.shapes.small
        ) {
            Text(if (busy) "Enviando..."
                 else if (targetUid == null) "ENVIAR A TODOS LOS USUARIOS"
                 else stringResource(R.string.common_send))
        }
        if (confirmAll) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmAll = false },
                title = { Text("¿Enviar a TODOS?") },
                text = { Text("El push llegará a todos los usuarios de Cumbre. Esta acción no se puede deshacer.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmAll = false
                        onSend(null, title, body)
                    }) { Text("SÍ, A TODOS", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmAll = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
        result?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ─────────────────────────── DENUNCIAS ────────────────────────────
@Composable
internal fun DenunciasTab(
    reports: List<MeetupReport>,
    contentReports: List<com.meteomontana.android.data.api.ContentReportDto> = emptyList(),
    onResolve: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onRemoveContent: (String) -> Unit = {},
    onIgnoreContent: (String) -> Unit = {},
    onDeleteMeetup: (String) -> Unit = {},
    onOpenAuthor: (String) -> Unit = {},
    onOpenMeetup: (String) -> Unit = {},
    onOpenFeedPost: (String) -> Unit = {}
) {
    if (reports.isEmpty() && contentReports.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sin denuncias pendientes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(Spacing.md)) {
        if (contentReports.isNotEmpty()) {
            item {
                Text("CONTENIDO (comentarios / notas / usuarios)",
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm))
            }
            items(contentReports, key = { "c-" + it.id }) { r ->
                ContentReportCard(r,
                    onRemove = { onRemoveContent(r.id) },
                    onIgnore = { onIgnoreContent(r.id) },
                    onOpenAuthor = { r.authorUid?.let(onOpenAuthor) },
                    onOpenFeedPost = { onOpenFeedPost(r.targetId) })
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        if (reports.isNotEmpty()) {
            item {
                Text("QUEDADAS",
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.sm))
            }
            items(reports, key = { it.id }) { report ->
                ReportCard(report = report, onResolve = { onResolve(report.id) },
                    onDismiss = { onDismiss(report.id) },
                    onDelete = { onDeleteMeetup(report.id) },
                    onOpenAuthor = { report.reportedUid?.let(onOpenAuthor) },
                    onOpenMeetup = { onOpenMeetup(report.meetupId) })
                Spacer(Modifier.height(Spacing.sm))
            }
        }
    }
}

/** Card de denuncia de CONTENIDO: snapshot + motivo + RETIRAR / IGNORAR. */
@Composable
private fun ContentReportCard(
    r: com.meteomontana.android.data.api.ContentReportDto,
    onRemove: () -> Unit,
    onIgnore: () -> Unit,
    onOpenAuthor: () -> Unit = {},
    onOpenFeedPost: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                when (r.targetType) {
                    "COMMENT" -> "COMENTARIO"; "NOTE" -> "NOTA"
                    "FEED_POST" -> "POST DEL FEED"
                    "FEED_COMMENT" -> "COMENTARIO DEL FEED"
                    else -> "USUARIO"
                } + " - " + reasonLabel(r.reason),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error)
            Text(r.createdAt?.take(10) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(r.snapshot ?: "(contenido no disponible)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                .clickable(onClick = onRemove)
                .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text(if (r.targetType == "USER") "MARCAR REVISADO" else "RETIRAR CONTENIDO",
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.error)
            }
            Box(Modifier.weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .clickable(onClick = onIgnore)
                .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text("IGNORAR",
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Denuncias del feed: VER POST abre el FeedPostDetailScreen (para
        // FEED_COMMENT también abre el post; el snapshot ya enseña el texto
        // del comentario). Patrón de VER QUEDADA.
        if (r.targetType == "FEED_POST" || r.targetType == "FEED_COMMENT") {
            Box(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .background(com.meteomontana.android.ui.theme.Terra)
                .clickable(onClick = onOpenFeedPost)
                .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text("VER POST ▸",
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = Color.White)
            }
        }
        // Ver al AUTOR del contenido (historial de denuncias + consecuencias).
        if (r.authorUid != null) {
            Box(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .clickable(onClick = onOpenAuthor)
                .padding(vertical = Spacing.xs),
                contentAlignment = Alignment.Center) {
                Text("VER AUTOR ▸",
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = com.meteomontana.android.ui.theme.Terra)
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: MeetupReport,
    onResolve: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit = {},
    onOpenAuthor: () -> Unit = {},
    onOpenMeetup: () -> Unit = {}
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        // Razón + estado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                reasonLabel(report.reason),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                report.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // IDs
        Text("Quedada: ${report.meetupId.take(8)}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Denunciante: ${report.reporterUid.take(8)}…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        report.reportedUid?.let {
            Text("Denunciado: ${it.take(8)}…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        report.context?.let {
            Text("Contexto: $it",
                style = MaterialTheme.typography.bodySmall)
        }
        Text(report.createdAt.take(10),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Ver la quedada ENTERA (nombre, escuela, participantes) para juzgar
        // por qué la denunciaron antes de decidir.
        Box(Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(com.meteomontana.android.ui.theme.Terra)
            .clickable(onClick = onOpenMeetup)
            .padding(vertical = Spacing.sm),
            contentAlignment = Alignment.Center) {
            Text("VER QUEDADA ▸",
                style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = Color.White)
        }
        Spacer(Modifier.height(Spacing.xs))
        // Acción: ELIMINAR la quedada denunciada (con confirmación).
        Box(Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
            .clickable { confirmDelete = true }
            .padding(vertical = Spacing.sm),
            contentAlignment = Alignment.Center) {
            Text("ELIMINAR QUEDADA",
                style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(Spacing.xs))
        // Ver al organizador denunciado (historial + consecuencias).
        if (report.reportedUid != null) {
            Box(Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .clickable(onClick = onOpenAuthor)
                .padding(vertical = Spacing.xs),
                contentAlignment = Alignment.Center) {
                Text("VER AUTOR ▸",
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = com.meteomontana.android.ui.theme.Terra)
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        // Cerrar la denuncia sin borrar la quedada.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            androidx.compose.material3.OutlinedButton(
                onClick = onResolve, modifier = Modifier.weight(1f)
            ) { Text("OK, REVISADA") }
            androidx.compose.material3.OutlinedButton(
                onClick = onDismiss, modifier = Modifier.weight(1f)
            ) { Text("DESESTIMAR") }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar la quedada?") },
            text = { Text("Se borrará para todos los participantes. La denuncia quedará resuelta.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("ELIMINAR", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("CANCELAR") }
            }
        )
    }
}

private fun reasonLabel(reason: String) = when (reason) {
    "SPAM" -> "Spam"
    "INAPPROPRIATE" -> "Contenido inapropiado"
    "HARASSMENT" -> "Acoso"
    else -> "Otro"
}

/**
 * Ficha de moderación de un usuario (se abre con "VER AUTOR"). Muestra su
 * historial de denuncias y deja aplicar consecuencias: aviso, suspensión
 * temporal o baneo de login (reversible).
 */
@Composable
internal fun UserModerationSheet(
    mod: com.meteomontana.android.data.api.UserModerationDto?,
    loading: Boolean,
    onWarn: (String, String?) -> Unit,
    onSuspend: (String, Int, String?) -> Unit,
    onBan: (String, String?) -> Unit,
    onUnban: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    fun r(): String? = reason.trim().ifBlank { null }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (loading || mod == null) {
                Box(Modifier.fillMaxWidth().padding(Spacing.lg), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }
            val name = mod.username?.let { "@$it" } ?: (mod.displayName ?: mod.uid.take(10))
            Text(name, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            // Estado + contadores
            Text(buildString {
                append("${mod.reportCount} denuncia(s) · ${mod.warnings} aviso(s)")
                if (mod.banned) append(" · BANEADO")
                mod.suspendedUntil?.let { append(" · suspendido hasta ${it.take(10)}") }
            }, style = MaterialTheme.typography.labelMedium,
                color = if (mod.banned) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("HISTORIAL DE DENUNCIAS", style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mod.reports.isEmpty()) {
                Text("Sin denuncias de contenido registradas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                mod.reports.take(8).forEach { rep ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${rep.type} · ${reasonLabel(rep.reason)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
                        rep.snapshot?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                        }
                    }
                }
            }

            // Historial de acciones ya aplicadas (auditoría con motivo).
            if (mod.actions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text("ACCIONES APLICADAS", style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                mod.actions.take(8).forEach { act ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${modActionLabel(act.action)}${act.createdAt?.let { " · ${it.take(10)}" } ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        act.reason?.takeIf { it.isNotBlank() }?.let {
                            Text("Motivo: $it", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        act.snapshot?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text("CONSECUENCIAS", style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Motivo (opcional) — se guarda con la acción para justificar/revocar.
            OutlinedTextField(
                value = reason, onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Motivo (se guarda para pruebas)",
                    style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 2)
            // Aviso
            ModActionButton("ENVIAR AVISO", MaterialTheme.colorScheme.primary) { onWarn(mod.uid, r()) }
            // Suspensión temporal
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ModActionButton("SUSPENDER 7 D", MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f)) { onSuspend(mod.uid, 7, r()) }
                ModActionButton("SUSPENDER 30 D", MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f)) { onSuspend(mod.uid, 30, r()) }
            }
            // Baneo / desbaneo
            if (mod.banned) {
                ModActionButton("DESBANEAR", MaterialTheme.colorScheme.primary) { onUnban(mod.uid, r()) }
            } else {
                ModActionButton("BANEAR CUENTA", MaterialTheme.colorScheme.error) { onBan(mod.uid, r()) }
            }

            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onDismiss).padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text("CERRAR", style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun modActionLabel(a: String) = when (a) {
    "WARN" -> "Aviso"; "SUSPEND" -> "Suspensión"; "BAN" -> "Baneo"; "UNBAN" -> "Desbaneo"
    "DELETE_NOTE" -> "Nota borrada"; "DELETE_COMMENT" -> "Comentario borrado"
    "DELETE_MEETUP" -> "Quedada borrada"; else -> a
}

@Composable
private fun ModActionButton(label: String, color: androidx.compose.ui.graphics.Color,
                            modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxWidth()
        .clip(RoundedCornerShape(2.dp))
        .border(1.dp, color, RoundedCornerShape(2.dp))
        .clickable(onClick = onClick)
        .padding(vertical = Spacing.sm),
        contentAlignment = Alignment.Center) {
        Text(label, style = com.meteomontana.android.ui.theme.EyebrowTextStyle, color = color)
    }
}
