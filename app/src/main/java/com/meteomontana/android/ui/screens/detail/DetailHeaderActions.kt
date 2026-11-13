package com.meteomontana.android.ui.screens.detail

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.ActionTile
import com.meteomontana.android.ui.components.ProcessionaryIcon

/** Volver: baldosa pequeña sin nombre, a la izquierda de la fila de acciones. */
@Composable
internal fun BackPill(onBack: () -> Unit) {
    ActionTile(label = null, onClick = onBack, modifier = Modifier.width(38.dp), icon = Icons.AutoMirrored.Outlined.ArrowBack,
        description = stringResource(R.string.common_back))
}

/** Aquí (estoy aquí): activo = terracota. El estado de presencia vive en `SchoolPresenceRow`. */
@Composable
fun HereTile(iAmHere: Boolean, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ActionTile(label = stringResource(R.string.bar_here), onClick = onClick, modifier = modifier,
        icon = Icons.Outlined.Place, active = iAmHere, enabled = !loading,
        description = stringResource(R.string.presence_here_short))
}

/**
 * Acciones de la escuela, todas a la vista y cada una con su nombre debajo: llegar, compartir, favorita, guardar
 * offline y oruga (la procesionaria; en rojo si hay alerta). Se pintan en la fila de baldosas de la cabecera.
 */
@Composable
internal fun RowScope.HeaderActions(
    tile: Modifier,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isSavedOffline: Boolean,
    savingProgress: Float?,
    onToggleSaveOffline: () -> Unit,
    onDirections: () -> Unit,
    onShare: () -> Unit,
    processionaryAlertActive: Boolean,
    processionaryActiveNow: Boolean,
    onOpenProcessionary: () -> Unit
) {
    val hasCaterpillar = processionaryAlertActive || processionaryActiveNow
    ActionTile(label = stringResource(R.string.bar_directions), onClick = onDirections, modifier = tile, icon = Icons.Outlined.Directions,
        description = stringResource(R.string.common_directions))
    ActionTile(label = stringResource(R.string.bar_share), onClick = onShare, modifier = tile, icon = Icons.Outlined.Share,
        description = stringResource(R.string.common_share))
    ActionTile(
        label = stringResource(R.string.bar_favorite), onClick = onToggleFavorite, modifier = tile, icon = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, active = isFavorite,
        description = if (isFavorite) stringResource(R.string.school_detail_screen_quitar_de_favoritos)
        else stringResource(R.string.school_detail_screen_anadir_a_favoritos)
    )
    ActionTile(
        label = stringResource(R.string.bar_save), onClick = onToggleSaveOffline, modifier = tile,
        icon = if (isSavedOffline) Icons.Outlined.DownloadDone else Icons.Outlined.FileDownload,
        active = isSavedOffline,
        description = if (isSavedOffline) stringResource(R.string.detail_saved_offline) else stringResource(R.string.detail_save_offline),
        iconContent = savingProgress?.let { p ->
            { tint ->
                CircularProgressIndicator(progress = { p }, color = tint, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    )
    // La oruga solo aparece si hay procesionaria activa o aviso: si no, no ocupa hueco.
    if (hasCaterpillar) ActionTile(
        label = stringResource(R.string.bar_caterpillar), onClick = onOpenProcessionary, modifier = tile,
        active = processionaryAlertActive, activeColor = MaterialTheme.colorScheme.error,
        description = stringResource(R.string.bar_caterpillar),
        iconContent = { tint -> ProcessionaryIcon(tint = tint, modifier = Modifier.size(22.dp)) }
    )
}
