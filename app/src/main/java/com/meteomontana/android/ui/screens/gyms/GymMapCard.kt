package com.meteomontana.android.ui.screens.gyms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.gym.Gym
import com.meteomontana.android.domain.model.gym.GymOpenStatus
import com.meteomontana.android.ui.theme.Serif

/**
 * Tarjeta de cristal del marcador pulsado (misma que la de sector/parking de la escuela: radio 24, fondo del tema
 * al 92 %, canto de luz blanco). Dos acciones: «Cómo llegar» (primaria) y «Ver ficha».
 */
@Composable
internal fun GymMapCard(
    gym: Gym,
    status: GymOpenStatus,
    distanceText: String?,
    onClose: () -> Unit,
    onDirections: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GymKindBadge(gym.kind, size = 52.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    gym.name, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 21.sp,
                    color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                GymTags(gym, status)
                distanceText?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (onToggleFavorite != null) GymFavoriteStar(isFavorite, onToggleFavorite)
            Row(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f))
                    .clickable(onClick = onClose),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Close, stringResource(R.string.common_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GymActionPill(stringResource(R.string.gym_directions), onDirections, Modifier.weight(1f), primary = true,
                icon = R.drawable.ic_directions)
            GymActionPill(stringResource(R.string.gym_view_card), onOpen, Modifier.weight(1f))
        }
    }
}
