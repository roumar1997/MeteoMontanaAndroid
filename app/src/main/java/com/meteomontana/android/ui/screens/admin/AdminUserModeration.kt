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

// Hoja de moderación de un usuario (admin) y sus botones de acción.

/**
 * Ficha de moderación de un usuario (se abre con "VER AUTOR"). Muestra su
 * historial de denuncias y deja aplicar consecuencias: aviso, suspensión
 * temporal o baneo de login (reversible).
 */
@Composable
internal fun UserModerationSheet(
    mod: com.meteomontana.android.domain.model.UserModeration?,
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
                append(stringResource(R.string.admin_tabs_misc_v4_denuncia_s_aviso_s, mod.reportCount, mod.warnings))
                if (mod.banned) append(stringResource(R.string.admin_tabs_misc_v4_baneado))
                mod.suspendedUntil?.let { append(stringResource(R.string.admin_tabs_misc_v4_suspendido_hasta, it.take(10))) }
            }, style = MaterialTheme.typography.labelMedium,
                color = if (mod.banned) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(stringResource(R.string.admin_tabs_misc_v2_historial_de_denuncias), style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (mod.reports.isEmpty()) {
                Text(stringResource(R.string.admin_tabs_misc_v2_sin_denuncias_de_contenido_registradas),
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
                Text(stringResource(R.string.admin_tabs_misc_v2_acciones_aplicadas), style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                mod.actions.take(8).forEach { act ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${modActionLabel(act.action)}${act.createdAt?.let { " · ${it.take(10)}" } ?: ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                        act.reason?.takeIf { it.isNotBlank() }?.let {
                            Text(stringResource(R.string.admin_tabs_misc_v2_motivo_1_s, it), style = MaterialTheme.typography.bodySmall,
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
            Text(stringResource(R.string.admin_tabs_misc_v2_consecuencias), style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Motivo (opcional) — se guarda con la acción para justificar/revocar.
            OutlinedTextField(
                value = reason, onValueChange = { reason = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.admin_tabs_misc_v2_motivo_se_guarda_para_pruebas),
                    style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 2)
            // Aviso
            ModActionButton(stringResource(R.string.admin_tabs_misc_v2_enviar_aviso), MaterialTheme.colorScheme.primary) { onWarn(mod.uid, r()) }
            // Suspensión temporal
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ModActionButton(stringResource(R.string.admin_tabs_misc_v2_suspender_7_d), MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f)) { onSuspend(mod.uid, 7, r()) }
                ModActionButton(stringResource(R.string.admin_tabs_misc_v2_suspender_30_d), MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f)) { onSuspend(mod.uid, 30, r()) }
            }
            // Baneo / desbaneo
            if (mod.banned) {
                ModActionButton(stringResource(R.string.admin_tabs_misc_v2_desbanear), MaterialTheme.colorScheme.primary) { onUnban(mod.uid, r()) }
            } else {
                ModActionButton(stringResource(R.string.admin_tabs_misc_v2_banear_cuenta), MaterialTheme.colorScheme.error) { onBan(mod.uid, r()) }
            }

            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onDismiss).padding(vertical = Spacing.sm),
                contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.admin_tabs_misc_v2_cerrar), style = com.meteomontana.android.ui.theme.EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun modActionLabel(a: String) = when (a) {
    "WARN" -> "Aviso"; "SUSPEND" -> AppText.get(R.string.admin_tabs_misc_v4_suspension); "BAN" -> "Baneo"; "UNBAN" -> "Desbaneo"
    "DELETE_NOTE" -> AppText.get(R.string.admin_tabs_misc_v4_nota_borrada); "DELETE_COMMENT" -> AppText.get(R.string.admin_tabs_misc_v4_comentario_borrado)
    "DELETE_MEETUP" -> AppText.get(R.string.admin_tabs_misc_v4_quedada_borrada); else -> a
}

@Composable
internal fun ModActionButton(label: String, color: androidx.compose.ui.graphics.Color,
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
