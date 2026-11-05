package com.meteomontana.android.ui.screens.admin

import com.meteomontana.android.util.AppText
import com.meteomontana.android.ui.theme.inkButtonColor
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

// Pestaña de denuncias (admin): lista y tarjetas de denuncia de contenido y de usuario.

@Composable
internal fun DenunciasTab(
    reports: List<MeetupReport>,
    contentReports: List<com.meteomontana.android.domain.model.ContentReport> = emptyList(),
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
            Text(stringResource(R.string.admin_tabs_misc_v2_sin_denuncias_pendientes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(Spacing.md)) {
        if (contentReports.isNotEmpty()) {
            item {
                Text(stringResource(R.string.admin_tabs_misc_v2_contenido_comentarios_notas_usuarios),
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
                Text(stringResource(R.string.admin_tabs_misc_v2_quedadas),
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
internal fun ContentReportCard(
    r: com.meteomontana.android.domain.model.ContentReport,
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
                    "COMMENT" -> stringResource(R.string.w_comment_caps); "NOTE" -> stringResource(R.string.w_note_caps)
                    "FEED_POST" -> stringResource(R.string.admin_tabs_misc_v4_post_del_feed)
                    "FEED_COMMENT" -> stringResource(R.string.admin_tabs_misc_v4_comentario_del_feed)
                    else -> stringResource(R.string.w_user_caps)
                } + " - " + reasonLabel(r.reason),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error)
            Text(r.createdAt?.take(10) ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(r.snapshot ?: stringResource(R.string.admin_tabs_misc_v3_contenido_no_disponible),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(2.dp))
                .clickable(onClick = onRemove)
                .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text(if (r.targetType == "USER") stringResource(R.string.admin_tabs_misc_v3_marcar_revisado) else stringResource(R.string.admin_tabs_misc_v3_retirar_contenido),
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.error)
            }
            Box(Modifier.weight(1f)
                .clip(RoundedCornerShape(2.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                .clickable(onClick = onIgnore)
                .padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.admin_tabs_misc_v2_ignorar),
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
                Text(stringResource(R.string.admin_tabs_misc_v2_ver_post),
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
                Text(stringResource(R.string.admin_tabs_misc_v2_ver_autor_2),
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = com.meteomontana.android.ui.theme.Terra)
            }
        }
    }
}

@Composable
internal fun ReportCard(
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
        Text(stringResource(R.string.admin_tabs_misc_v2_quedada_1_s, report.meetupId.take(8)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.admin_tabs_misc_v2_denunciante_1_s, report.reporterUid.take(8)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        report.reportedUid?.let {
            Text(stringResource(R.string.admin_tabs_misc_v2_denunciado_1_s, it.take(8)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        report.context?.let {
            Text(stringResource(R.string.admin_tabs_misc_v2_contexto_1_s, it),
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
            Text(stringResource(R.string.admin_tabs_misc_v2_ver_quedada),
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
            Text(stringResource(R.string.admin_tabs_misc_v2_eliminar_quedada),
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
                Text(stringResource(R.string.admin_tabs_misc_v2_ver_autor),
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = com.meteomontana.android.ui.theme.Terra)
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        // Cerrar la denuncia sin borrar la quedada.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            androidx.compose.material3.OutlinedButton(
                onClick = onResolve, modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.admin_tabs_misc_v2_ok_revisada)) }
            androidx.compose.material3.OutlinedButton(
                onClick = onDismiss, modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.admin_tabs_misc_v2_desestimar)) }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.admin_tabs_misc_eliminar_la_quedada)) },
            text = { Text(stringResource(R.string.admin_tabs_misc_se_borrara_para_todos)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.admin_tabs_misc_v2_eliminar), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.admin_tabs_misc_v2_cancelar)) }
            }
        )
    }
}

internal fun reasonLabel(reason: String) = when (reason) {
    "SPAM" -> "Spam"
    "INAPPROPRIATE" -> AppText.get(R.string.admin_tabs_misc_v4_contenido_inapropiado)
    "HARASSMENT" -> AppText.get(R.string.w_harassment)
    else -> AppText.get(R.string.w_other)
}

