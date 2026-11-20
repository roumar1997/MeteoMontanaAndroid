package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymReportIssue
import com.meteomontana.android.domain.model.gym.GymScalePresets
import com.meteomontana.android.ui.components.CumbreChip
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import com.meteomontana.android.ui.theme.Spacing
import com.meteomontana.android.ui.theme.terraFillColor

/**
 * «Añadir foto» de la ficha: va como reporte con foto para que un admin la apruebe («Solo fotos tuyas»). El servidor
 * aún no devuelve fotos aprobadas de la ficha, así que no se muestra ninguna (ni hay «Denunciar foto»): ver
 * BACKEND-PENDIENTE.md.
 */
@Composable
internal fun GymPhotoContribute(gym: Gym, viewModel: GymReportViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var started by remember { mutableStateOf(false) }
    val pick = rememberGymPhotoPicker { uri ->
        started = true
        viewModel.sendCorrection(gym.id, setOf(GymReportIssue.OTHER), ctxComment, uri)
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GymActionPill(
            stringResource(R.string.gym_photo_add), { viewModel.reset(); pick() }, Modifier.fillMaxWidth(),
            enabled = status != ReportStatus.SENDING
        )
        val (text, color) = when {
            started && status == ReportStatus.SENT -> R.string.gym_sent to MaterialTheme.colorScheme.onSurfaceVariant
            started && status == ReportStatus.FAILED -> R.string.gym_send_error to MaterialTheme.colorScheme.error
            started && status == ReportStatus.SENDING -> R.string.gym_review_note to MaterialTheme.colorScheme.onSurfaceVariant
            else -> R.string.gym_photo_own_only to MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(stringResource(text), fontSize = 12.sp, color = color, textAlign = TextAlign.Center)
    }
}

private const val ctxComment = "Foto de la comunidad"

/** Editor de «Escala de colores o grados»: Colores | Grados y, con colores, tocarlos de fácil a difícil. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GymScaleEditor(
    grades: Boolean,
    onGrades: (Boolean) -> Unit,
    picked: List<String>,
    onPicked: (List<String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = Spacing.xs)) {
        Text(stringResource(R.string.gym_scale_title).uppercase(), style = EyebrowTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CumbreChip(stringResource(R.string.gym_scale_colors), !grades, { onGrades(false) })
            CumbreChip(stringResource(R.string.gym_scale_grades), grades, { onGrades(true) })
        }
        if (!grades) {
            Text(stringResource(R.string.gym_scale_pick), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GymScalePresets.colors.forEach { lv ->
                    val order = picked.indexOf(lv.name) + 1
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(parseHex(lv.hex))
                            .border(if (order > 0) 3.dp else 1.dp, if (order > 0) terraFillColor() else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { onPicked(if (order > 0) picked - lv.name else picked + lv.name) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (order > 0) Text(
                            order.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = if (lv.name == "blanco" || lv.name == "amarillo") Color.Black else Color.White
                        )
                    }
                }
            }
            if (picked.size < GymScalePresets.MIN_LEVELS) Text(stringResource(R.string.gym_scale_need), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
