package com.meteomontana.android.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.BetaVideo
import com.meteomontana.android.domain.model.CommunityBadge
import com.meteomontana.android.domain.model.LineSearchHit

private val VideoTint = Color(0xFFC0392B)
private val DoneTint = Color(0xFF2E7D4F)

/**
 * Lo que sabe la comunidad de una vía, en pastillas (igual que iOS):
 * ▶ rojo = hay vídeo de beta de esa altura (tocar lo abre; si hay varios, se elige de una lista),
 * ✓ gris = cuántas personas la han hecho, ✓ verde = cuántas de ellas miden más / menos de 1,70.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CommunityBadgesRow(hit: LineSearchHit, modifier: Modifier = Modifier) {
    val badges = hit.communityBadges
    if (badges.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        badges.forEach { b ->
            when (b) {
                is CommunityBadge.Video -> VideoBadge(b)
                is CommunityBadge.Done -> Badge(
                    Icons.Outlined.Check,
                    pluralStringResource(R.plurals.badge_people, b.people, b.people),
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
                is CommunityBadge.DoneByHeight -> Badge(
                    Icons.Outlined.Check,
                    stringResource(R.string.badge_people_of_height, b.people, CommunityBadge.heightText(b.height).orEmpty()),
                    DoneTint
                )
            }
        }
    }
}

@Composable
private fun VideoBadge(b: CommunityBadge.Video) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val height = CommunityBadge.heightText(b.height)
    val label = if (b.videos.size == 1) stringResource(R.string.badge_video) else stringResource(R.string.badge_videos, b.videos.size)
    fun play(v: BetaVideo) = runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.url))) }
    Box {
        Badge(Icons.Outlined.PlayArrow, listOfNotNull(label, height).joinToString(" "), VideoTint) {
            if (b.videos.size == 1) play(b.videos.first()) else open = true
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            b.videos.forEachIndexed { i, v ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.badge_video_n, i + 1, host(v.url))) },
                    leadingIcon = { Icon(Icons.Outlined.PlayArrow, null, tint = VideoTint) },
                    onClick = { open = false; play(v) }
                )
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String, tint: Color, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.clip(CircleShape).background(tint.copy(alpha = 0.12f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = tint)
        Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** "instagram.com" de un enlace, para distinguir los vídeos en la lista. */
private fun host(url: String): String =
    runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url
