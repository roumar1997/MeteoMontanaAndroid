@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.meteomontana.android.R
import com.meteomontana.android.domain.model.FeedComment
import com.meteomontana.android.domain.model.FeedPost
import com.meteomontana.android.domain.usecase.feed.FeedKind
import com.meteomontana.android.ui.components.TopoLine
import com.meteomontana.android.ui.components.TopoPhotoCanvas
import com.meteomontana.android.ui.screens.topo.parseLineStroke
import com.meteomontana.android.ui.theme.EyebrowTextStyle
import kotlinx.coroutines.launch

/**
 * Pestaña Comunidad: feed social (SIGUIENDO | TODOS) + ranking de
 * contribuidores (RANKING, reutiliza CommunityScreen).
 */
// Tarjeta de un post del feed + etiquetas de tipo y hora relativa.
// Reparto del antiguo FeedScreen.kt de 1.098 lineas.

/** Tarjeta de actividad: cabecera + topo (foto con SOLO la línea del post) +
 *  acciones. Reutilizada por FeedScreen y por el detalle de post (push). */
@Composable
internal fun FeedPostCard(
    post: FeedPost,
    onOpenSchool: (String, String?, String?, String?) -> Unit,
    onOpenUser: (String) -> Unit,
    onToggleLike: () -> Unit,
    onOpenComments: () -> Unit,
    onDelete: () -> Unit,
    /** Denunciar el post (solo posts ajenos); null = sin bandera. */
    onReport: (() -> Unit)? = null,
    /** Líneas máximas de la caption (Int.MAX_VALUE en el detalle = entera). */
    captionMaxLines: Int = 3
) {
    val shape = RoundedCornerShape(2.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
    ) {
        // ── Cabecera: avatar + nombre + tiempo relativo + eyebrow del tipo ──
        Row(
            Modifier.fillMaxWidth()
                .clickable { onOpenUser(post.author.uid) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (post.author.photoUrl != null) {
                AsyncImage(
                    model = post.author.photoUrl, contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            } else {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    post.author.displayName
                        ?: post.author.username?.let { "@$it" }
                        ?: post.author.uid.take(6),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                Text(
                    kindLabel(post.kind, post.discipline),
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                relativeTime(post.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Imagen: foto de la cara con SOLO la línea de esta vía ──
        val points = remember(post.linePath) { parseLineStroke(post.linePath).points }
        val hasTopo = !post.photoPath.isNullOrBlank()
        val celebrationUrl = post.photoUrl?.takeIf { it.isNotBlank() }
        // Foto de celebración ampliada a pantalla completa (tap en la miniatura).
        var fullPhoto by remember { mutableStateOf<String?>(null) }
        if (hasTopo) {
            Box(
                Modifier.fillMaxWidth().clickable {
                    post.schoolId?.let { onOpenSchool(it, post.lineId, post.lineName, post.blockId) }
                }
            ) {
                // Post de ascenso/vía nueva: SU línea. Post de piedra nueva
                // (NEW_BLOCK, sin lineId): TODAS las vías de la cara portada
                // (blockLines, campo del backend — antes la foto salía pelada).
                val blockLines = remember(post.blockLines) {
                    post.blockLines.orEmpty().mapNotNull { l ->
                        val pts = parseLineStroke(l.linePath).points
                        if (pts.isEmpty()) null
                        else TopoLine(name = l.name, grade = l.grade,
                            startType = l.startType, points = pts)
                    }
                }
                TopoPhotoCanvas(
                    photoUrl = post.photoPath!!,
                    lines = when {
                        points.isNotEmpty() -> listOf(
                            TopoLine(
                                name = post.lineName, grade = post.grade,
                                startType = post.startType, points = points
                            )
                        )
                        else -> blockLines
                    }
                )
                // Miniatura de la foto de celebración superpuesta arriba-derecha.
                if (celebrationUrl != null) {
                    AsyncImage(
                        model = celebrationUrl,
                        contentDescription = stringResource(R.string.feed_celebration_photo),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(width = 88.dp, height = 110.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(2.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
                            .clickable { fullPhoto = celebrationUrl }
                    )
                }
            }
        } else if (celebrationUrl != null) {
            // Sin topo: la foto de celebración ES la imagen principal.
            AsyncImage(
                model = celebrationUrl,
                contentDescription = stringResource(R.string.feed_celebration_photo),
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clickable { fullPhoto = celebrationUrl }
            )
        }
        fullPhoto?.let { url ->
            com.meteomontana.android.ui.components.FullScreenPhotoDialog(
                photoUrl = url,
                onDismiss = { fullPhoto = null }
            )
        }

        // ── Texto: «vía · grado · inicio — piedra · escuela» ──
        // Tipo de inicio (Sentado/Pie/Lance/Trav.) junto al grado — mismo
        // mapeo que el editor de topos (StartTypeLabel.kt).
        val startLabel = com.meteomontana.android.ui.components.startTypeLabel(post.startType)
        val title = buildString {
            append(post.lineName ?: post.blockName ?: "")
            post.grade?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            startLabel?.let { append(" · ").append(it) }
        }
        val place = buildString {
            post.blockName?.takeIf { it.isNotBlank() && post.lineName != null }?.let { append(it) }
            post.schoolName?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
            // Tipo de roca (si el backend lo manda), como texto secundario.
            post.rockType?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .clickable { post.schoolId?.let { onOpenSchool(it, post.lineId, post.lineName, post.blockId) } }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (title.isNotBlank()) {
                Text(
                    title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (place.isNotBlank()) {
                Text(
                    place, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Descripción del autor (caption): recortada en la tarjeta, entera
            // en el detalle (tap en la tarjeta lo abre).
            post.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                com.meteomontana.android.ui.components.MentionText(
                    text = caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = captionMaxLines,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                    onOpenUser = onOpenUser
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // ── Acciones: like + comentarios + borrar (si es mío) ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleLike) {
                Icon(
                    if (post.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(R.string.feed_like),
                    tint = if (post.likedByMe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (post.likeCount > 0) {
                Text(
                    "${post.likeCount}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenComments) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = stringResource(R.string.feed_comments_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (post.commentCount > 0) {
                Text(
                    "${post.commentCount}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Compartir como IMAGEN 1080×1920 (sin enlace) — reutiliza la
            // infraestructura de ShareLineImage.
            val shareCtx = androidx.compose.ui.platform.LocalContext.current
            val shareScope = rememberCoroutineScope()
            IconButton(onClick = {
                shareScope.launch {
                    com.meteomontana.android.ui.share.shareFeedPostAsImage(shareCtx, post)
                }
            }) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.feed_share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            // Directo a Instagram Stories — SOLO si hay Facebook App ID
            // configurado (ver FACEBOOK_APP_ID en ShareFeedPostImage.kt).
            if (com.meteomontana.android.ui.share.canShareToStories()) {
                Text(
                    stringResource(R.string.feed_share_stories),
                    style = EyebrowTextStyle,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .clickable {
                            shareScope.launch {
                                com.meteomontana.android.ui.share.shareFeedPostToStories(shareCtx, post)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (post.mine) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.feed_delete_post),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (onReport != null) {
                // Bandera de denuncia (posts ajenos), zona táctil ≥40dp.
                IconButton(onClick = onReport, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Outlined.Flag,
                        contentDescription = stringResource(R.string.feed_report),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Hoja de comentarios de un post (patrón de los comentarios de vías).
 *  Recibe lambdas (no el VM) para poder reutilizarse desde el perfil público. */

@Composable
internal fun kindLabel(kind: String, discipline: String? = null): String = stringResource(
    when (kind) {
        FeedKind.PROJECT_DONE -> R.string.feed_kind_project_done
        FeedKind.NEW_BLOCK -> R.string.feed_kind_new_block
        FeedKind.NEW_LINE -> R.string.feed_kind_new_line
        else -> when {
            discipline.equals("BOULDER", ignoreCase = true) -> R.string.feed_kind_tick_boulder
            discipline.equals("ROUTE", ignoreCase = true) -> R.string.feed_kind_tick_route
            else -> R.string.feed_kind_tick
        }
    }
)

/** "hace 2 h" a partir de un createdAt "yyyy-MM-ddTHH:mm:ss" (hora del servidor,
 *  que es UTC — interpretarla como local sumaba 2h de error en España). */
@Composable
internal fun relativeTime(createdAt: String): String {
    val minutes = remember(createdAt) {
        runCatching {
            val t = java.time.LocalDateTime.parse(createdAt.take(19))
                .toInstant(java.time.ZoneOffset.UTC)
            java.time.Duration.between(t, java.time.Instant.now()).toMinutes()
                .coerceAtLeast(0)
        }.getOrDefault(0L)
    }
    return when {
        minutes < 1 -> stringResource(R.string.feed_time_now)
        minutes < 60 -> stringResource(R.string.feed_time_min, minutes)
        minutes < 60 * 24 -> stringResource(R.string.feed_time_hours, minutes / 60)
        else -> stringResource(R.string.feed_time_days, minutes / (60 * 24))
    }
}

