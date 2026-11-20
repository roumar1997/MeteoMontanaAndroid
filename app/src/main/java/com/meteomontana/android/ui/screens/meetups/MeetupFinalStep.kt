package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.MeetupDraft
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * Paso 3 de 3: Material y detalles. Baldosas de material (de dos en dos), mensaje, quién puede unirse (tres chips),
 * nombre con la foto al lado y el resumen. Sin scroll en una pantalla normal. El límite de participantes ya no se
 * pregunta: «¿Cuántos buscáis?» del paso anterior lo cubre.
 */
@Composable
internal fun MeetupFinalStep(
    draft: MeetupDraft,
    onDraft: (MeetupDraft) -> Unit,
    uploadingPhoto: Boolean,
    onPickPhoto: () -> Unit,
    defaultName: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WizardQuestion(stringResource(R.string.mw_step3_q))
        if (com.meteomontana.android.domain.model.MeetupGearItem.forPlace(draft.discipline, draft.placeIsGym).isNotEmpty()) {
            WizardEyebrow(stringResource(R.string.mw_gear_tap))
            GearTiles(draft, onDraft)
        }
        WizardEyebrow(stringResource(R.string.mw_message))
        OutlinedTextField(
            value = draft.description.orEmpty(), onValueChange = { onDraft(draft.copy(description = it.take(300).ifBlank { null })) },
            placeholder = { Text(stringResource(R.string.mw_message_hint), maxLines = 1) },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3, shape = RoundedCornerShape(16.dp)
        )
        WizardEyebrow(stringResource(R.string.mw_who_joins))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrivacyChip("OPEN", R.string.create_meetup_privacy_open, draft, onDraft)
            PrivacyChip("FOLLOWERS", R.string.create_meetup_privacy_followers, draft, onDraft)
            PrivacyChip("WOMEN", R.string.create_meetup_privacy_women, draft, onDraft)
        }
        WizardEyebrow(stringResource(R.string.mw_name_label))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.name, onValueChange = { onDraft(draft.copy(name = it)) },
                placeholder = { Text(defaultName, maxLines = 1) },
                modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(16.dp)
            )
            PhotoButton(draft.photoUrl != null, uploadingPhoto, onPickPhoto)
        }
        MeetupSummaryCard(draft)
    }
}

@Composable
private fun PrivacyChip(key: String, title: Int, draft: MeetupDraft, onDraft: (MeetupDraft) -> Unit) {
    CumbreChip(stringResource(title), draft.privacy == key, { onDraft(draft.copy(privacy = key)) })
}

/** Foto del grupo en un botón cuadrado junto al nombre: vacío con la cámara, con foto un check. */
@Composable
private fun PhotoButton(hasPhoto: Boolean, uploading: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier.size(56.dp).clip(shape).border(1.dp, if (hasPhoto) terraFillColor() else MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surface).clickable(onClick = onPick),
        contentAlignment = Alignment.Center
    ) {
        if (uploading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else Icon(
            if (hasPhoto) Icons.Outlined.CheckCircle else Icons.Outlined.AddAPhoto, stringResource(R.string.mw_photo_add),
            tint = if (hasPhoto) terraFillColor() else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
