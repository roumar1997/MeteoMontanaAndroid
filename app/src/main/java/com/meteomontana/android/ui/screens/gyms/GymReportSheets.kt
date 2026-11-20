package com.meteomontana.android.ui.screens.gyms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymKind
import com.meteomontana.android.domain.model.gym.GymReportIssue
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.components.CumbreSheetShape
import com.meteomontana.android.ui.components.cumbreSheetSurface
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.Terra
import com.meteomontana.android.ui.theme.TerraBg
import kotlinx.coroutines.delay

private val issueLabels = mapOf(
    GymReportIssue.IS_FREE to R.string.gym_issue_free,
    GymReportIssue.IS_PAID to R.string.gym_issue_paid,
    GymReportIssue.CLOSED to R.string.gym_issue_closed,
    GymReportIssue.IS_BOULDER to R.string.gym_issue_boulder,
    GymReportIssue.IS_ROPE to R.string.gym_issue_rope,
    GymReportIssue.WRONG_LOCATION to R.string.gym_issue_location,
    GymReportIssue.OTHER to R.string.gym_issue_other
)

/**
 * «Corregir datos» (maqueta): chips de lo que falla, comentario, foto opcional y «Enviar». Lo recibe un admin.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GymReportSheet(gym: Gym, onDismiss: () -> Unit, viewModel: GymReportViewModel = hiltViewModel()) {
    var issues by rememberSaveable { mutableStateOf(setOf<String>()) }
    var comment by rememberSaveable { mutableStateOf("") }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val pickPhoto = rememberGymPhotoPicker { photoUri = it }
    CloseWhenSent(status, onDismiss)

    ModalBottomSheet(
        onDismissRequest = { viewModel.reset(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        shape = CumbreSheetShape
    ) {
        Column(
            Modifier.fillMaxWidth().cumbreSheetSurface().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(stringResource(R.string.gym_fix_title), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground)
            Text(gym.name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Eyebrow(stringResource(R.string.gym_fix_what))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                issueLabels.forEach { (issue, label) ->
                    CumbreChip(stringResource(label), issue.code in issues, {
                        issues = if (issue.code in issues) issues - issue.code else issues + issue.code
                    })
                }
            }
            Eyebrow(stringResource(R.string.gym_comment))
            CommentField(comment, { comment = it }, stringResource(R.string.gym_comment_hint))
            PhotoRow(photoUri, pickPhoto)
            SendBlock(
                status = status,
                enabled = issues.isNotEmpty() || comment.isNotBlank(),
                onSend = {
                    viewModel.sendCorrection(gym.id, issues.mapNotNull { GymReportIssue.fromCode(it) }.toSet(), comment, photoUri)
                }
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** «Añadir un muro que falta»: nombre, tipo, modalidad y el punto del mapa. Se publica tras revisión. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewGymSheet(lat: Double, lon: Double, onDismiss: () -> Unit, viewModel: GymReportViewModel = hiltViewModel()) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(GymKind.FREE) }
    var boulder by rememberSaveable { mutableStateOf(false) }
    var rope by rememberSaveable { mutableStateOf(false) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val status by viewModel.status.collectAsStateWithLifecycle()
    val pickPhoto = rememberGymPhotoPicker { photoUri = it }
    LaunchedEffect(Unit) { viewModel.reset() }
    CloseWhenSent(status, onDismiss)

    ModalBottomSheet(
        onDismissRequest = { viewModel.reset(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        shape = CumbreSheetShape
    ) {
        Column(
            Modifier.fillMaxWidth().cumbreSheetSurface().verticalScroll(rememberScrollState()).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(stringResource(R.string.gym_new_title), fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CumbreChip(stringResource(R.string.gym_new_kind_free), kind == GymKind.FREE, { kind = GymKind.FREE })
                CumbreChip(stringResource(R.string.gym_new_kind_municipal), kind == GymKind.MUNICIPAL, { kind = GymKind.MUNICIPAL })
                CumbreChip(stringResource(R.string.gym_new_kind_commercial), kind == GymKind.COMMERCIAL, { kind = GymKind.COMMERCIAL })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CumbreChip(stringResource(R.string.gym_disc_boulder), boulder, { boulder = !boulder })
                CumbreChip(stringResource(R.string.gym_disc_rope), rope, { rope = !rope })
            }
            Eyebrow(stringResource(R.string.gym_new_name))
            CommentField(name, { name = it }, stringResource(R.string.gym_new_name_hint), singleLine = true)
            Text(
                stringResource(R.string.gym_new_place_here, "%.5f, %.5f".format(lat, lon)),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground
            )
            Text(stringResource(R.string.gym_new_place_note), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PhotoRow(photoUri, pickPhoto)
            SendBlock(
                status = status, enabled = name.isNotBlank(),
                onSend = { viewModel.sendNewGym(name, lat, lon, kind, boulder, rope, photoUri) }
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** Enviado: se enseña la confirmación un momento y la hoja se cierra sola. */
@Composable
private fun CloseWhenSent(status: ReportStatus, onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(status) { if (status == ReportStatus.SENT) { delay(1500); dismiss() } }
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm))
}

@Composable
private fun CommentField(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        placeholder = { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PhotoRow(photoUri: String?, onPick: () -> Unit) {
    Row(Modifier.padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CumbreChip(
            stringResource(if (photoUri == null) R.string.gym_add_photo else R.string.gym_change_photo), photoUri != null, onPick
        )
        Text(stringResource(R.string.gym_optional), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Botón «Enviar», su nota («La revisará un admin») y el resultado (enviado / error). */
@Composable
private fun SendBlock(status: ReportStatus, enabled: Boolean, onSend: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
        val sending = status == ReportStatus.SENDING
        val canSend = enabled && !sending && status != ReportStatus.SENT
        GymActionPill(
            text = stringResource(R.string.gym_send), onClick = onSend,
            modifier = Modifier.fillMaxWidth(), primary = true, enabled = canSend
        )
        if (sending) CircularProgressIndicator(Modifier.padding(top = Spacing.sm).height(20.dp))
        val note = when (status) {
            ReportStatus.SENT -> R.string.gym_sent
            ReportStatus.FAILED -> R.string.gym_send_error
            else -> R.string.gym_review_note
        }
        Text(
            stringResource(note), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp),
            color = if (status == ReportStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Selector de foto del sistema (DOCUMENTOS, no el selector de fotos): conserva el EXIF y no necesita el permiso
 * READ_MEDIA_IMAGES, que Play prohíbe. Devuelve el URI como texto para el VM.
 */
@Composable
internal fun rememberGymPhotoPicker(onPick: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onPick(it.toString()) }
    }
    return { launcher.launch(arrayOf("image/*")) }
}
