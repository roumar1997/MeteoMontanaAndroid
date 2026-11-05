@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
            androidx.compose.material3.ExperimentalMaterial3Api::class)
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

@Composable
internal fun StatsTab(
    stats: AdminStats?,
    users: List<com.meteomontana.android.domain.model.AdminUserRow>? = null,
    notes: List<com.meteomontana.android.domain.model.AdminNoteRow>? = null,
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
        Text(stringResource(R.string.admin_tabs_misc_v2_toca_una_tarjeta_para_ver),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(stringResource(R.string.w_users_caps), stats.totalUsers, Modifier.weight(1f)) {
                onLoadUsers(); openList = "users"
            }
            StatCard("ADMINS", stats.totalAdmins, Modifier.weight(1f)) {
                onLoadUsers(); openList = "admins"
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(stringResource(R.string.w_schools_caps), stats.totalSchools, Modifier.weight(1f)) { onGoToTab("gestionar") }
            StatCard(stringResource(R.string.w_notes_caps), stats.totalNotes, Modifier.weight(1f)) {
                onLoadNotes(); openList = "notes"
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("PENDING", stats.submissionsPending, Modifier.weight(1f)) { onGoToTab("propuestas") }
            // P6: cada cifra abre PROPUESTAS ya filtrada por su estado.
            StatCard(stringResource(R.string.w_approved_caps), stats.submissionsApproved, Modifier.weight(1f)) { onGoToTab("propuestas:APPROVED") }
            StatCard(stringResource(R.string.w_rejected_caps), stats.submissionsRejected, Modifier.weight(1f)) { onGoToTab("propuestas:REJECTED") }
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
                        "users" -> stringResource(R.string.w_users_caps); "admins" -> "ADMINS"; else -> stringResource(R.string.w_notes_caps)
                    }, style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.admin_tabs_misc_v2_cerrar_3), style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
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
                                            if (u.isAdmin) Text(stringResource(R.string.admin_tabs_misc_v2_admin),
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
                            // R12: pulsar la nota la ENSEÑA entera (con VER
                            // ESCUELA); antes saltaba a la escuela y la nota
                            // no se veía por ningún lado.
                            var noteDetail by remember {
                                androidx.compose.runtime.mutableStateOf<com.meteomontana.android.domain.model.AdminNoteRow?>(null)
                            }
                            noteDetail?.let { nd ->
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { noteDetail = null },
                                    confirmButton = {
                                        if (nd.schoolId != null) {
                                            androidx.compose.material3.TextButton(onClick = {
                                                noteDetail = null
                                                nd.schoolId?.let(onOpenSchool)
                                            }) { Text(stringResource(R.string.admin_tabs_misc_v2_ver_escuela)) }
                                        }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(onClick = { noteDetail = null }) {
                                            Text(stringResource(R.string.admin_tabs_misc_v2_cerrar_2))
                                        }
                                    },
                                    title = { Text(stringResource(R.string.admin_tabs_misc_v2_nota_de_1_s, nd.author ?: stringResource(R.string.notes_section_anonimo))) },
                                    text = {
                                        Column {
                                            Text(nd.text, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(Modifier.height(Spacing.sm))
                                            Text(listOfNotNull(nd.schoolId, nd.createdAt?.take(10))
                                                    .joinToString(" · "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                )
                            }
                            LazyColumn {
                                items(list.size) { i ->
                                    val n = list[i]
                                    Column(Modifier.fillMaxWidth()
                                        .clickable { noteDetail = n }
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
internal fun StatCard(label: String, value: Long, modifier: Modifier = Modifier,
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
            fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold,
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
            Text(stringResource(R.string.admin_tabs_misc_v2_sin_actividad),
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
        Text(stringResource(R.string.admin_tabs_misc_v2_enviar_push), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground)

        // Destinatario: buscador de usuarios o TODOS.
        if (targetUid == null) {
            val closeKeyboard = com.meteomontana.android.ui.components.rememberKeyboardDismisser()
            OutlinedTextField(value = query,
                onValueChange = { query = it; onSearchUser(it) },
                placeholder = { Text(stringResource(R.string.admin_tabs_misc_v2_buscar_destinatario_por_usuario_o)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            if (userResults.isNotEmpty() && query.trim().length >= 2) {
                Column(Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))) {
                    userResults.take(6).forEach { u ->
                        Row(Modifier.fillMaxWidth()
                            .clickable {
                                closeKeyboard()
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
            Text(stringResource(R.string.admin_tabs_misc_sin_destinatario_elegido_se),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.admin_tabs_misc_v2_para) + (targetLabel ?: targetUid),
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = com.meteomontana.android.ui.theme.Terra)
                Text(stringResource(R.string.admin_tabs_misc_v2_quitar),
                    style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(2.dp))
                        .clickable { targetUid = null; targetLabel = null }
                        .padding(4.dp))
            }
        }
        OutlinedTextField(value = title, onValueChange = { title = it },
            placeholder = { Text(stringResource(R.string.admin_tabs_misc_titulo)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = body, onValueChange = { body = it },
            placeholder = { Text(stringResource(R.string.admin_tabs_misc_v2_mensaje)) },
            modifier = Modifier.fillMaxWidth().height(120.dp))

        Button(
            onClick = {
                if (targetUid == null) confirmAll = true
                else onSend(targetUid, title, body)
            },
            enabled = !busy && title.isNotBlank() && body.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = inkButtonColor(), contentColor = Color.White
            ),
            shape = MaterialTheme.shapes.small
        ) {
            Text(if (busy) "Enviando..."
                 else if (targetUid == null) stringResource(R.string.admin_tabs_misc_v3_enviar_a_todos_los_usuarios)
                 else stringResource(R.string.common_send))
        }
        if (confirmAll) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmAll = false },
                title = { Text(stringResource(R.string.admin_tabs_misc_enviar_a_todos)) },
                text = { Text(stringResource(R.string.admin_tabs_misc_el_push_llegara_a)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmAll = false
                        onSend(null, title, body)
                    }) { Text(stringResource(R.string.admin_tabs_misc_si_a_todos), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmAll = false }) {
                        Text(stringResource(R.string.admin_tabs_misc_v2_cancelar_2))
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
