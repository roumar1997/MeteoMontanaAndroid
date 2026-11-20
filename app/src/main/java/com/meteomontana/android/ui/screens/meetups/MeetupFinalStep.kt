package com.meteomontana.android.ui.screens.meetups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.MeetupDraft

/** Paso 3 de 3: ¿Quién puede unirse? Privacidad, límite de participantes, nombre y foto. */
@Composable
internal fun MeetupFinalStep(
    draft: MeetupDraft,
    onDraft: (MeetupDraft) -> Unit,
    uploadingPhoto: Boolean,
    onPickPhoto: () -> Unit,
    defaultName: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WizardQuestion(stringResource(R.string.mw_step3_q))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrivacyCard("OPEN", R.string.create_meetup_privacy_open, R.string.mw_privacy_open_sub, draft, onDraft)
            PrivacyCard("FOLLOWERS", R.string.create_meetup_privacy_followers, R.string.mw_privacy_followers_sub, draft, onDraft)
            PrivacyCard("WOMEN", R.string.create_meetup_privacy_women, R.string.mw_privacy_women_sub, draft, onDraft)
        }
        WizardEyebrow(stringResource(R.string.mw_limit))
        WizardStepper(
            value = draft.memberLimit?.toString() ?: "∞",
            unit = if (draft.memberLimit != null) stringResource(R.string.mw_limit_max) else stringResource(R.string.create_meetup_no_limit),
            onMinus = if (draft.memberLimit != null) ({ onDraft(draft.stepLimit(-1)) }) else null,
            onPlus = if ((draft.memberLimit ?: 0) < MeetupDraft.MAX_LIMIT) ({ onDraft(draft.stepLimit(1)) }) else null
        )
        WizardEyebrow(stringResource(R.string.mw_name_label))
        OutlinedTextField(
            value = draft.name, onValueChange = { onDraft(draft.copy(name = it)) },
            placeholder = { Text(defaultName, maxLines = 1) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp)
        )
        PhotoBox(draft.photoUrl, uploadingPhoto, onPickPhoto)
    }
}

@Composable
private fun PrivacyCard(key: String, title: Int, subtitle: Int, draft: MeetupDraft, onDraft: (MeetupDraft) -> Unit) {
    WizardOptionCard(draft.privacy == key, { onDraft(draft.copy(privacy = key)) }) {
        WizardCardTitle(stringResource(title), stringResource(subtitle))
    }
}

/** Foto del grupo: recuadro de borde discontinuo y redondeado; con foto, la foto. */
@Composable
private fun PhotoBox(photoUrl: String?, uploading: Boolean, onPick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).clickable(onClick = onPick)
            .drawBehind {
                if (photoUrl == null) drawRoundRect(
                    outline, size = Size(size.width, size.height), cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (photoUrl != null) AsyncImage(photoUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Outlined.AddAPhoto, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            Text(stringResource(R.string.mw_photo_add), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (uploading) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp))
        }
    }
}
