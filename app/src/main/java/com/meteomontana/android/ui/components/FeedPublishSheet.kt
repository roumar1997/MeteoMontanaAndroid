package com.meteomontana.android.ui.components

import com.meteomontana.android.data.local.saveCelebrationToGallery

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
import androidx.compose.runtime.collectAsState
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

/**
 * PUBLICAR EN EL FEED al marcar una via como hecha — hoja de confirmacion,
 * foto de celebracion (camara/galeria) y dialogo de exito de propuestas.
 * Espejo de FeedPublish.swift + FeedCelebrationPhoto.swift en iOS.
 *
 * Unica responsabilidad: el flujo social post-tick. El mapa (SchoolMap) solo
 * abre esta hoja; nada de aqui toca MapLibre.
 */

/**
 * Diálogo de éxito estilo Cumbre (mismo look que ProposeContributionFlow.SuccessDialog).
 * Se usa tras enviar la propuesta de "AÑADIR VÍAS".
 */
@Composable
internal fun CumbreSuccessDialog(
    onClose: () -> Unit,
    onMyProposals: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(Spacing.lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(com.meteomontana.android.ui.theme.Moss),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", style = MaterialTheme.typography.headlineLarge,
                        color = Color.White)
                }
                Spacer(Modifier.height(Spacing.lg))
                Text("PROPUESTA ENVIADA",
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text("Un admin la revisará en ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("24-48h.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Terra)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Te avisaremos por email y notificación\npush cuando haya respuesta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.xl))
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(modifier = Modifier.weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(onClick = onClose)
                        .padding(vertical = Spacing.md),
                        contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.common_close).uppercase(), style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    Box(modifier = Modifier.weight(1.5f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.onBackground)
                        .clickable(onClick = onMyProposals)
                        .padding(vertical = Spacing.md),
                        contentAlignment = Alignment.Center) {
                        Text("VER MIS PROPUESTAS", style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }
}

/** Tick pendiente de confirmar (hoja "Publicar en el feed"). */
internal data class PendingTick(
    val block: Block,
    val line: com.meteomontana.android.domain.model.BlockLine,
    val index: Int,
    val schoolName: String,
    val sectorName: String?,
    val wasProject: Boolean
)

/**
 * Hoja de publicar un ascenso (estilo Cumbre: fondo Paper, borde Rule, eyebrow
 * mono, primario Terra). Tres acciones: PUBLICAR EN EL FEED (primario), "Solo
 * en mi diario" (texto) y el checkbox "Publicar siempre sin preguntar" (solo
 * aplica si se publica). Cerrar la hoja = no marcar nada.
 */
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun FeedPublishSheet(
    lineLabel: String,
    wasProject: Boolean,
    onPublish: (always: Boolean, caption: String?, photoUri: Uri?) -> Unit,
    onDiaryOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    var always by remember { mutableStateOf(false) }
    // Descripción opcional del autor (viaja como "caption", max 500).
    var caption by remember { mutableStateOf("") }
    // Foto de celebración: cámara del sistema (TakePicture sobre un URI del
    // FileProvider; sin permiso CAMERA en el Manifest → no hace falta runtime
    // permission) o elegida de la GALERÍA (Photo Picker, tampoco pide permiso).
    // OJO robustez (paridad con el blindaje iOS 2026-07-18): los URIs van en
    // rememberSaveable — con la cámara abierta MIUI mata el proceso a menudo
    // y un remember simple perdía la foto al volver (se quedaba en null).
    val photoCtx = LocalContext.current
    var photoUri by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<Uri?>(null)
    }
    var pendingCameraUri by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf<Uri?>(null)
    }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        val pending = pendingCameraUri
        if (ok && pending != null) {
            photoUri = pending
            // Red de seguridad: copia la foto también al carrete del usuario
            // (MediaStore, sin permisos en API 29+). Aunque algo fallara
            // después, la foto del grupo existe en Galería. Best effort.
            saveCelebrationToGallery(photoCtx, pending)
        } else if (ok) {
            // NUNCA en silencio: la cámara dijo OK pero el URI pendiente se
            // perdió (no debería pasar con rememberSaveable) → avisar YA.
            android.widget.Toast.makeText(
                photoCtx, R.string.feed_photo_failed, android.widget.Toast.LENGTH_LONG
            ).show()
        }
        // ok=false = cancelado por el usuario → sin aviso (es lo esperado).
    }
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }
    fun launchCamera() {
        val dir = java.io.File(photoCtx.cacheDir, "feed").apply { mkdirs() }
        val file = java.io.File(dir, "celebration-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            photoCtx, "${photoCtx.packageName}.fileprovider", file
        )
        pendingCameraUri = uri
        runCatching { cameraLauncher.launch(uri) }
    }
    fun launchGallery() {
        runCatching {
            galleryLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts
                        .PickVisualMedia.ImageOnly
                )
            )
        }
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg)
        ) {
            // Eyebrow del tipo de logro.
            Text(
                stringResource(
                    if (wasProject) R.string.feed_kind_project_done else R.string.feed_kind_tick
                ),
                style = EyebrowTextStyle,
                color = Terra
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.feed_mark_done_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                lineLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.md))
            // Autocompletado de @menciones al escribir la descripción.
            com.meteomontana.android.ui.components.MentionSuggestions(
                text = caption, onReplace = { if (it.length <= 500) caption = it })
            // Descripción opcional del post.
            androidx.compose.material3.OutlinedTextField(
                value = caption,
                onValueChange = { if (it.length <= 500) caption = it },
                placeholder = {
                    Text(stringResource(R.string.feed_caption_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(2.dp)
            )
            Spacer(Modifier.height(Spacing.md))
            // Foto de celebración (opcional): fila para abrir la cámara o
            // miniatura con ✕ para quitarla/repetir.
            val currentPhoto = photoUri
            if (currentPhoto == null) {
                // Dos accesos: hacer la foto ahora o elegirla de la galería
                // (para publicar después sin estar con el móvil en el momento).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                            .clickable { launchCamera() }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = Terra,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.feed_take_photo),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
                            .clickable { launchGallery() }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = Terra,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.feed_pick_from_gallery),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Box {
                        coil.compose.AsyncImage(
                            model = currentPhoto,
                            contentDescription = stringResource(R.string.feed_celebration_photo),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(width = 88.dp, height = 110.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        )
                        // ✕ quita la foto (se puede volver a hacer otra).
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp)
                                .size(22.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { photoUri = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Column {
                        Text(
                            stringResource(R.string.feed_retake_celebration_photo),
                            style = EyebrowTextStyle,
                            color = Terra,
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .clickable { launchCamera() }
                                .padding(Spacing.sm)
                        )
                        Text(
                            stringResource(R.string.feed_pick_from_gallery_caps),
                            style = EyebrowTextStyle,
                            color = Terra,
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .clickable { launchGallery() }
                                .padding(Spacing.sm)
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            // Checkbox "Publicar siempre sin preguntar".
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, if (always) Terra else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(2.dp))
                    .clickable { always = !always }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(if (always) "☑" else "☐",
                    color = if (always) Terra else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.feed_publish_always),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (always) Terra else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.md))
            // Primario: PUBLICAR EN EL FEED (Terra, texto blanco).
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Terra)
                    .clickable { onPublish(always, caption.trim().ifBlank { null }, photoUri) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.feed_publish_action),
                    style = EyebrowTextStyle,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            // Secundario: solo diario.
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(onClick = onDiaryOnly)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.feed_publish_diary_only),
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
