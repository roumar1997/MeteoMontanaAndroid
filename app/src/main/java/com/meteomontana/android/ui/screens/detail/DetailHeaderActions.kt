package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.IconAction
import com.meteomontana.android.ui.components.ProcessionaryIcon

/** Volver: icono sin nombre, el primero de la fila de acciones (a la izquierda). */
@Composable
internal fun BackPill(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconAction(onClick = onBack, modifier = modifier, icon = Icons.Outlined.ChevronLeft,
        description = stringResource(R.string.common_back))
}

/**
 * Acciones de la escuela, solo icono: llegar, compartir, favorita, guardar offline y oruga (la procesionaria: siempre
 * visible, se puede avisar en cualquier escuela; en rojo si hay alerta). Se pintan en la fila de iconos de la cabecera.
 */
@Composable
internal fun RowScope.HeaderActions(
    item: Modifier,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isSavedOffline: Boolean,
    savingProgress: Float?,
    onToggleSaveOffline: () -> Unit,
    onDirections: () -> Unit,
    onShare: () -> Unit,
    processionaryAlertActive: Boolean,
    onOpenProcessionary: () -> Unit
) {
    IconAction(onClick = onDirections, modifier = item, icon = Icons.Outlined.Directions,
        description = stringResource(R.string.common_directions))
    IconAction(onClick = onShare, modifier = item, icon = Icons.Outlined.IosShare,
        description = stringResource(R.string.common_share))
    IconAction(
        onClick = onToggleFavorite, modifier = item, icon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, active = isFavorite,
        description = if (isFavorite) stringResource(R.string.school_detail_screen_quitar_de_favoritos)
        else stringResource(R.string.school_detail_screen_anadir_a_favoritos)
    )
    IconAction(
        onClick = onToggleSaveOffline, modifier = item,
        icon = if (isSavedOffline) Icons.Outlined.Check else Icons.Outlined.FileDownload,
        active = isSavedOffline,
        description = if (isSavedOffline) stringResource(R.string.detail_saved_offline) else stringResource(R.string.detail_save_offline),
        iconContent = savingProgress?.let { p ->
            { tint ->
                CircularProgressIndicator(progress = { p }, color = tint, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    )
    IconAction(
        onClick = onOpenProcessionary, modifier = item,
        active = processionaryAlertActive, activeColor = MaterialTheme.colorScheme.error,
        description = stringResource(R.string.bar_caterpillar),
        iconContent = { tint -> ProcessionaryIcon(tint = tint, modifier = Modifier.size(22.dp)) }
    )
}
