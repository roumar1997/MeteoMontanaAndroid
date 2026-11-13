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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meteomontana.android.R
import com.meteomontana.android.ui.components.ActionCircle
import com.meteomontana.android.ui.components.ProcessionaryIcon

/** Volver: círculo suelto, sin nombre, como en iOS. */
@Composable
internal fun BackPill(onBack: () -> Unit) {
    ActionCircle(label = null, onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack,
        description = stringResource(R.string.common_back))
}

/** Aquí (estoy aquí): activo = terracota. El estado de presencia vive en `SchoolPresenceRow`. */
@Composable
fun HereCircle(iAmHere: Boolean, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ActionCircle(label = stringResource(R.string.bar_here), onClick = onClick, modifier = modifier,
        icon = Icons.Filled.Place, active = iAmHere, enabled = !loading,
        description = stringResource(R.string.presence_here_short))
}

/**
 * Acciones de la escuela, todas a la vista y cada una con su nombre debajo: llegar, compartir, favorita, guardar
 * offline y oruga (la procesionaria; en rojo si hay alerta). Se pintan en la fila deslizable de la cabecera.
 */
@Composable
internal fun RowScope.HeaderActions(
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
    ActionCircle(label = stringResource(R.string.bar_directions), onClick = onDirections, icon = Icons.Filled.Directions,
        description = stringResource(R.string.common_directions))
    ActionCircle(label = stringResource(R.string.bar_share), onClick = onShare, icon = Icons.Filled.Share,
        description = stringResource(R.string.common_share))
    ActionCircle(
        label = stringResource(R.string.bar_favorite), onClick = onToggleFavorite, icon = Icons.Filled.Star, active = isFavorite,
        description = if (isFavorite) stringResource(R.string.school_detail_screen_quitar_de_favoritos)
        else stringResource(R.string.school_detail_screen_anadir_a_favoritos)
    )
    ActionCircle(
        label = stringResource(R.string.bar_save), onClick = onToggleSaveOffline,
        icon = if (isSavedOffline) Icons.Filled.DownloadDone else Icons.Filled.FileDownload,
        active = isSavedOffline,
        description = if (isSavedOffline) stringResource(R.string.detail_saved_offline) else stringResource(R.string.detail_save_offline),
        iconContent = savingProgress?.let { p ->
            { tint ->
                CircularProgressIndicator(progress = { p }, color = tint, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
        }
    )
    ActionCircle(
        label = stringResource(R.string.bar_caterpillar), onClick = onOpenProcessionary,
        active = processionaryAlertActive, activeColor = MaterialTheme.colorScheme.error,
        description = stringResource(R.string.bar_caterpillar),
        iconContent = { tint -> ProcessionaryIcon(tint = tint, modifier = Modifier.size(22.dp)) }
    )
}
