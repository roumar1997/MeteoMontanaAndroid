package com.meteomontana.android.ui.components

import com.meteomontana.android.util.CalendarLabels
import com.meteomontana.android.domain.model.FeatureKey
import com.meteomontana.android.ui.theme.terraFillColor

import com.meteomontana.android.data.local.saveCelebrationToGallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.ui.unit.sp
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
                Text(stringResource(R.string.feed_publish_sheet_v2_propuesta_enviada),
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.feed_publish_sheet_un_admin_la_revisara),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("24-48h.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Terra)
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.feed_publish_sheet_v2_te_avisaremos_por_email_y),
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
                        Text(stringResource(R.string.feed_publish_sheet_v2_ver_mis_propuestas), style = EyebrowTextStyle,
                            color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }
}

/** Tick pendiente de confirmar (hoja de ascenso). */
internal data class PendingTick(
    val block: Block,
    val line: com.meteomontana.android.domain.model.BlockLine,
    val index: Int,
    val schoolName: String,
    val sectorName: String?,
    val wasProject: Boolean
)

/** Lo que se decide en la hoja de ascenso al guardar. */
internal data class AscentChoice(
    val sessionDate: String?,
    val aVista: Boolean,
    val alFlash: Boolean,
    val gradeVote: String?,
    val publish: Boolean,
    val caption: String?,
    val photoUri: Uri?
)

/**
 * Voto que queda al pulsar [g]: como [nextGradeVote], pero pulsar el único grado elegido lo quita (null).
 * Pares de grados contiguos (7a/7a+) como en la hoja de votar grado.
 */
internal fun nextAscentVote(g: String, current: String?): String? {
    val parts = gradeParts(current)
    return if (parts.size == 1 && g in parts) null else nextGradeVote(g, current)
}

/**
 * Hoja de ascenso (espejo de AscentSheet de iOS, build 323): cuándo, estilo (a vista / al flash, uno u otro),
 * voto de grado (5 rápidos alrededor del grado del equipador + rejilla completa; se pueden elegir dos contiguos),
 * interruptor "Compartir en Comunidad" (descripción y foto solo si se publica) y UN botón que dice qué hará.
 * Cerrar la hoja = no marcar nada.
 */
@androidx.compose.runtime.Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun AscentSheet(
    lineName: String,
    setterGrade: String?,
    wasProject: Boolean,
    myHeight: com.meteomontana.android.domain.model.HeightGroup?,
    onSetHeight: (com.meteomontana.android.domain.model.HeightGroup) -> Unit,
    initialShare: Boolean,
    onSave: (AscentChoice) -> Unit,
    onDismiss: () -> Unit
) {
    // CUÁNDO la encadenaste: null = hoy. La fecha se guarda en el diario SIEMPRE, publiques o no.
    var sessionDate by remember { mutableStateOf<String?>(null) }
    // A vista y al flash son cosas distintas: uno u otro (encender uno apaga el otro).
    var aVista by remember { mutableStateOf(false) }
    var alFlash by remember { mutableStateOf(false) }
    // Voto de grado y el que espera a que se diga la altura (solo la primera vez en la vida).
    var vote by remember { mutableStateOf<String?>(null) }
    var pendingVote by remember { mutableStateOf<String?>(null) }
    var showAllGrades by remember { mutableStateOf(false) }
    var shareChoice by remember { mutableStateOf(initialShare) }
    // Si el servidor ha puesto en pausa publicar, el interruptor no se puede encender; guardar en el diario sigue igual.
    val flags = LocalFeatureFlags.current
    val publishEnabled = flags.isEnabled(FeatureKey.FEED_PUBLISH)
    val share = effectiveShare(shareChoice, publishEnabled)
    var showDatePicker by remember { mutableStateOf(false) }
    // Descripción opcional del autor (viaja como "caption", max 500).
    var caption by remember { mutableStateOf("") }
    // Foto de celebración: cámara del sistema (TakePicture sobre un URI del FileProvider) o galería (Photo Picker).
    // rememberSaveable: con la cámara abierta MIUI mata el proceso a menudo y un remember simple perdía la foto.
    val photoCtx = LocalContext.current
    var photoUri by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok ->
        val pending = pendingCameraUri
        if (ok && pending != null) {
            photoUri = pending
            // Red de seguridad: copia también al carrete (MediaStore, sin permisos en API 29+). Best effort.
            saveCelebrationToGallery(photoCtx, pending)
        } else if (ok) {
            // NUNCA en silencio: la cámara dijo OK pero el URI pendiente se perdió → avisar YA.
            android.widget.Toast.makeText(photoCtx, R.string.feed_photo_failed, android.widget.Toast.LENGTH_LONG).show()
        }
    }
    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }
    fun launchCamera() {
        val dir = java.io.File(photoCtx.cacheDir, "feed").apply { mkdirs() }
        val file = java.io.File(dir, "celebration-${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(photoCtx, "${photoCtx.packageName}.fileprovider", file)
        pendingCameraUri = uri
        runCatching { cameraLauncher.launch(uri) }
    }
    fun launchGallery() {
        runCatching {
            galleryLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
    }
    // La altura se pide UNA vez; en cuanto se sabe, el voto que esperaba pasa a ser el voto.
    androidx.compose.runtime.LaunchedEffect(myHeight) {
        if (myHeight != null && pendingVote != null) { vote = pendingVote; pendingVote = null }
    }
    fun tapGrade(g: String) {
        if (myHeight == null) pendingVote = nextAscentVote(g, pendingVote)
        else vote = nextAscentVote(g, vote)
    }
    // Dos por abajo, el del equipador y dos por arriba. Vacío si su grado no está en la lista (doble, fuera de rango…).
    val quickGrades = remember(setterGrade) {
        val i = ALL_GRADES.indexOf(setterGrade?.trim()?.lowercase())
        if (i < 0) emptyList() else (i - 2..i + 2).mapNotNull { ALL_GRADES.getOrNull(it) }
    }
    if (showDatePicker) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            })
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        sessionDate = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            }
        ) { androidx.compose.material3.DatePicker(state = state) }
    }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        // El fondo lo pone cumbreSheetSurface; si lo pintase el sheet, taparía el borde y el canto.
        containerColor = Color.Transparent,
        shape = CumbreSheetShape
    ) {
        com.meteomontana.android.ui.guide.GuideHost(HEIGHT_GUIDE, Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .cumbreSheetSurface(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg)
        ) {
            AscentHeader(lineName, setterGrade, wasProject)
            AscentSectionLabel(stringResource(R.string.ascent_when))
            AscentDateSegment(sessionDate, onToday = { sessionDate = null },
                onYesterday = { sessionDate = java.time.LocalDate.now().minusDays(1).toString() },
                onOther = { showDatePicker = true })
            AscentSectionLabel(stringResource(R.string.ascent_style))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AscentStyleTile(Icons.Outlined.Visibility, stringResource(R.string.ascent_onsight),
                    stringResource(R.string.ascent_onsight_sub), aVista, Modifier.weight(1f)) {
                    aVista = !aVista; if (aVista) alFlash = false
                }
                AscentStyleTile(Icons.Outlined.FlashOn, stringResource(R.string.ascent_flash),
                    stringResource(R.string.ascent_flash_sub), alFlash, Modifier.weight(1f)) {
                    alFlash = !alFlash; if (alFlash) aVista = false
                }
            }
            // TU ALTURA: con la que cuenta este encadene (+1,70 / −1,70) para saber cuánta gente de cada altura hace la
            // vía. Si ya la dijiste (mochila), una línea; si no, se elige aquí mismo.
            AscentSectionLabel(stringResource(R.string.ascent_height))
            AscentHeightRow(myHeight, onSetHeight)
            AscentSectionLabel(stringResource(R.string.ascent_grade_title))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val chosen = gradeParts(vote ?: pendingVote)
                if (quickGrades.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                        quickGrades.forEach { g -> AscentGradeButton(g, g in chosen, Modifier.weight(1f)) { tapGrade(g) } }
                    }
                }
                if (showAllGrades || quickGrades.isEmpty()) {
                    listOf("4", "5", "6", "7", "8").forEach { n ->
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                            SUFFIXES.forEach { suf ->
                                val g = n + suf
                                AscentGradeButton(g, g in chosen, Modifier.weight(1f)) { tapGrade(g) }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (quickGrades.isEmpty()) stringResource(R.string.ascent_grade_hint_free)
                        else stringResource(R.string.ascent_grade_hint_setter, setterGrade.orEmpty()),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (quickGrades.isNotEmpty()) {
                        Text(
                            stringResource(if (showAllGrades) R.string.ascent_less else R.string.ascent_other_grade),
                            style = EyebrowTextStyle.copy(fontSize = 10.sp), color = Terra,
                            modifier = Modifier.clickable { showAllGrades = !showAllGrades }.padding(vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AscentShareRow(share, publishEnabled, flags.messageFor(FeatureKey.FEED_PUBLISH)) { shareChoice = it }
            if (share) {
                Spacer(Modifier.height(10.dp))
                com.meteomontana.android.ui.components.MentionSuggestions(
                    text = caption, onReplace = { if (it.length <= 500) caption = it })
                androidx.compose.material3.OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 500) caption = it },
                    placeholder = {
                        Text(stringResource(R.string.feed_caption_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2, maxLines = 4,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(Spacing.sm))
                AscentPhotoRow(photoUri, onCamera = { launchCamera() }, onGallery = { launchGallery() },
                    onRemove = { photoUri = null })
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(terraFillColor())
                    .clickable {
                        if (publishEnabled) com.meteomontana.android.data.local.FeedSharePref.set(photoCtx, share)
                        onSave(AscentChoice(
                            sessionDate = sessionDate, aVista = aVista, alFlash = alFlash,
                            gradeVote = vote, publish = share,
                            caption = if (share) caption.trim().ifBlank { null } else null,
                            photoUri = if (share) photoUri else null))
                    }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(if (share) R.string.ascent_save_publish else R.string.ascent_save_diary),
                    style = EyebrowTextStyle, color = Color.White
                )
            }
        }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AscentHeader(lineName: String, setterGrade: String?, wasProject: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(if (wasProject) R.string.feed_kind_project_done else R.string.feed_kind_tick),
                style = EyebrowTextStyle, color = Terra)
            Text(lineName, fontFamily = com.meteomontana.android.ui.theme.Serif, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        if (!setterGrade.isNullOrBlank()) {
            val style = com.meteomontana.android.ui.theme.gradeChipStyle(setterGrade)
            Text(setterGrade, style = EyebrowTextStyle.copy(fontSize = 14.sp),
                color = if (style.dark) Color.Black else Color.White,
                modifier = Modifier
                    .clip(com.meteomontana.android.ui.theme.CumbrePillShape)
                    .background(com.meteomontana.android.ui.theme.gradeChipColor(setterGrade))
                    .border(1.dp, MaterialTheme.colorScheme.outline, com.meteomontana.android.ui.theme.CumbrePillShape)
                    .padding(horizontal = 13.dp, vertical = 6.dp))
        }
    }
}

@androidx.compose.runtime.Composable
private fun AscentSectionLabel(text: String) {
    Text(text, style = EyebrowTextStyle.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 7.dp))
}

/** Hoy | Ayer | Otra fecha…, como un selector segmentado. */
@androidx.compose.runtime.Composable
private fun AscentDateSegment(sessionDate: String?, onToday: () -> Unit, onYesterday: () -> Unit, onOther: () -> Unit) {
    val yesterday = java.time.LocalDate.now().minusDays(1).toString()
    val custom = sessionDate?.takeIf { it != yesterday }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surface).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        AscentSegment(stringResource(R.string.w_today), sessionDate == null, Modifier.weight(1f), onToday)
        AscentSegment(stringResource(R.string.w_yesterday), sessionDate == yesterday, Modifier.weight(1f), onYesterday)
        AscentSegment(custom?.let { formatShortDate(it) } ?: stringResource(R.string.feed_publish_sheet_v4_otra_fecha),
            custom != null, Modifier.weight(1f), onOther)
    }
}

@androidx.compose.runtime.Composable
private fun AscentSegment(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(9.dp))
            .background(if (on) MaterialTheme.colorScheme.background else Color.Transparent)
            .border(1.dp, if (on) MaterialTheme.colorScheme.outline else Color.Transparent, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick).padding(vertical = 9.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@androidx.compose.runtime.Composable
private fun AscentStyleTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String,
    on: Boolean, modifier: Modifier, onClick: () -> Unit
) {
    Column(
        modifier.fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) Terra.copy(alpha = 0.11f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) Terra else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
            tint = if (on) Terra else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp))
        Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Cada grado con SU color; el elegido va relleno. */
@androidx.compose.runtime.Composable
private fun AscentGradeButton(g: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val style = com.meteomontana.android.ui.theme.gradeChipStyle(g)
    val accent = if (style.dark) MaterialTheme.colorScheme.onSurface else style.stroke
    Box(
        modifier
            .background(if (selected) com.meteomontana.android.ui.theme.gradeChipColor(g)
                        else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, if (selected && !style.dark) Color.Transparent else accent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(g, style = EyebrowTextStyle.copy(fontSize = 12.sp), maxLines = 1,
            color = if (selected) (if (style.dark) Color.Black else Color.White) else accent)
    }
}

/** Foto de celebración: dos accesos (cámara / galería) o la miniatura con ✕ y repetir. */
@androidx.compose.runtime.Composable
private fun AscentPhotoRow(photo: Uri?, onCamera: () -> Unit, onGallery: () -> Unit, onRemove: () -> Unit) {
    if (photo == null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AscentPhotoButton(Icons.Outlined.PhotoCamera, stringResource(R.string.feed_take_photo), Modifier.weight(1f), onCamera)
            AscentPhotoButton(Icons.Outlined.PhotoLibrary, stringResource(R.string.feed_pick_from_gallery), Modifier.weight(1f), onGallery)
        }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box {
            coil.compose.AsyncImage(
                model = photo,
                contentDescription = stringResource(R.string.feed_celebration_photo),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(width = 88.dp, height = 110.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) { Text("✕", color = Color.White, style = MaterialTheme.typography.labelMedium) }
        }
        Column {
            Text(stringResource(R.string.feed_retake_celebration_photo), style = EyebrowTextStyle, color = Terra,
                modifier = Modifier.clip(RoundedCornerShape(2.dp)).clickable(onClick = onCamera).padding(Spacing.sm))
            Text(stringResource(R.string.feed_pick_from_gallery_caps), style = EyebrowTextStyle, color = Terra,
                modifier = Modifier.clip(RoundedCornerShape(2.dp)).clickable(onClick = onGallery).padding(Spacing.sm))
        }
    }
}

