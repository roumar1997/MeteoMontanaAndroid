package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.domain.model.gym.GymListItem
import com.meteomontana.android.domain.model.gym.GymOpenStatus
import com.meteomontana.android.ui.theme.Serif
import com.meteomontana.android.ui.theme.Terra

/**
 * Fila de la lista de rocódromos (maqueta): icono del tipo, nombre en serif, cadena · dirección, etiquetas y la
 * distancia a la derecha. Todo puede crecer con la letra grande: nada tiene alto fijo.
 */
@Composable
internal fun GymListRow(
    item: GymListItem,
    status: GymOpenStatus,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null
) {
    val gym = item.gym
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        GymKindBadge(gym.kind)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                gym.name, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            subtitle(item)?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
            GymTags(gym, status, showKind = false, modifier = Modifier.padding(top = 3.dp))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GymPresentation.distanceText(item.distanceKm)?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (onToggleFavorite != null) GymFavoriteStar(isFavorite, onToggleFavorite)
            else if (isFavorite) Icon(Icons.Filled.Star, contentDescription = null, tint = Terra, modifier = Modifier.size(16.dp))
        }
    }
}

/** «Cadena · Dirección» o, sin ellas, «Ciudad». Lo que falte se omite. */
internal fun subtitle(item: GymListItem): String? {
    val gym = item.gym
    val parts = listOfNotNull(
        gym.chain?.takeIf { it.isNotBlank() },
        gym.address?.takeIf { it.isNotBlank() } ?: gym.city.takeIf { it.isNotBlank() }
    )
    return parts.joinToString(" · ").ifEmpty { null }
}
