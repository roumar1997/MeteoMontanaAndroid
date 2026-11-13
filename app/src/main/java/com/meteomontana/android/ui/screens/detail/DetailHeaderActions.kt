package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
    IconAction(onClick = onBack, modifier = modifier, icon = Icons.AutoMirrored.Filled.ArrowBack,
        description = stringResource(R.string.common_back))
}

/** Aquí (estoy aquí): activo = terracota. El estado de presencia vive en `SchoolPresenceRow`. */
@Composable
fun HereTile(iAmHere: Boolean, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconAction(onClick = onClick, modifier = modifier, icon = Icons.Filled.Place, active = iAmHere, enabled = !loading,
        description = stringResource(R.string.presence_here_short))
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
    IconAction(onClick = onDirections, modifier = item, icon = Icons.Filled.Directions,
        description = stringResource(R.string.common_directions))
    IconAction(onClick = onShare, modifier = item, icon = Icons.Filled.Share,
        description = stringResource(R.string.common_share))
    IconAction(
        onClick = onToggleFavorite, modifier = item, icon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, active = isFavorite,
        description = if (isFavorite) stringResource(R.string.school_detail_screen_quitar_de_favoritos)
        else stringResource(R.string.school_detail_screen_anadir_a_favoritos)
    )
    IconAction(
        onClick = onToggleSaveOffline, modifier = item,
        icon = if (isSavedOffline) Icons.Filled.DownloadDone else Icons.Filled.FileDownload,
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
