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
// Hoja de comentarios del feed (hilo estilo Instagram) + orden del hilo.
// Reparto del antiguo FeedScreen.kt.

@Composable
internal fun FeedCommentsSheet(
    post: FeedPost,
    loadComments: suspend (Long) -> Result<List<FeedComment>>,
    addComment: suspend (Long, String, String?) -> Result<FeedComment>,
    deleteComment: suspend (Long, String) -> Result<Unit>,
    /** (commentId, like) → likeCount actualizado. */
    toggleCommentLike: suspend (String, Boolean) -> Result<Long>,
    onOpenUser: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var comments by remember { mutableStateOf<List<FeedComment>?>(null) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // Comentario al que se está respondiendo (banner sobre el campo).
    var replyTo by remember { mutableStateOf<FeedComment?>(null) }

    fun patchComment(id: String, transform: (FeedComment) -> FeedComment) {
        comments = comments?.map { if (it.id == id) transform(it) else it }
    }

    fun toggleLike(comment: FeedComment) {
        val liked = !comment.likedByMe
        // Optimista (como el like del post); si el server falla, se revierte.
        patchComment(comment.id) {
            it.copy(likedByMe = liked,
                likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0))
        }
        scope.launch {
            toggleCommentLike(comment.id, liked)
                .onSuccess { count -> patchComment(comment.id) { it.copy(likeCount = count) } }
                .onFailure { patchComment(comment.id) { comment } }
        }
    }
    // Denuncia de comentario ajeno (target FEED_COMMENT).
    val moderation: com.meteomontana.android.ui.components.ModerationViewModel = hiltViewModel()
    val hiddenIds by moderation.hiddenIds.collectAsState()
    var reportComment by remember { mutableStateOf<FeedComment?>(null) }

    LaunchedEffect(post.id) {
        comments = loadComments(post.id).getOrDefault(emptyList())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
        ) {
            Text(
                stringResource(R.string.feed_comments_title).uppercase(),
                style = EyebrowTextStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            when (val list = comments) {
                null -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                else -> LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false)
                ) {
                    if (list.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.feed_comments_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    items(threadOrder(list.filter { "FEED_COMMENT:${it.id}" !in hiddenIds }), key = { it.id }) { comment ->
                        FeedCommentRow(
                            comment = comment,
                            onOpenUser = onOpenUser,
                            onDelete = if (comment.mine) ({
                                scope.launch {
                                    if (deleteComment(post.id, comment.id).isSuccess) {
                                        comments = comments?.filter { it.id != comment.id }
                                    }
                                }
                            }) else null,
                            onReport = if (!comment.mine) ({ reportComment = comment }) else null,
                            isReply = comment.parentId != null,
                            onToggleLike = { toggleLike(comment) },
                            onReply = {
                                replyTo = comment
                                // Mención automática (estilo Instagram) para que se
                                // vea a quién contestas también dentro del hilo.
                                val mention = replyMention(comment)
                                if (mention.isNotEmpty() && !text.startsWith(mention)) {
                                    text = mention + text
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            // Banner "Respondiendo a X" con ✕ para volver a comentario raíz.
            replyTo?.let { target ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(
                            R.string.feed_replying_to,
                            target.author?.displayName
                                ?: target.author?.username?.let { "@$it" } ?: ""
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "✕",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .clickable { replyTo = null }
                            .padding(8.dp)
                    )
                }
            }
            // Autocompletado de @menciones (encima del campo).
            com.meteomontana.android.ui.components.MentionSuggestions(
                text = text, onReplace = { text = it })
            // Campo de texto + enviar.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.feed_comment_hint)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    shape = RoundedCornerShape(2.dp)
                )
                IconButton(
                    onClick = {
                        val t = text.trim()
                        if (t.isEmpty() || sending) return@IconButton
                        sending = true
                        scope.launch {
                            addComment(post.id, t, replyTo?.id).onSuccess { created ->
                                comments = (comments ?: emptyList()) + created
                                text = ""
                                replyTo = null
                            }
                            sending = false
                        }
                    },
                    enabled = text.isNotBlank() && !sending
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = stringResource(R.string.common_send),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    reportComment?.let { c ->
        com.meteomontana.android.ui.components.ReportDialog(
            title = stringResource(R.string.feed_report_comment),
            authorLabel = c.author?.displayName ?: c.author?.username?.let { "@$it" },
            onReport = { reason, alsoBlock ->
                moderation.report(
                    "FEED_COMMENT", c.id, reason,
                    alsoBlockUid = if (alsoBlock) (c.author?.uid ?: c.uid) else null
                )
                reportComment = null
            },
            onDismiss = { reportComment = null }
        )
    }
}

/**
 * Ordena los comentarios en hilos: cada comentario raíz seguido de TODAS sus
 * respuestas (también las respuestas a respuestas, aplanadas bajo el mismo
 * raíz, estilo Instagram) en orden cronológico. Respuestas cuyo raíz no está
 * visible (borrado/oculto) van al final.
 */
/**
 * Mención a insertar en el campo al responder: "@username " o, si el autor no
 * tiene username (p. ej. nunca se lo puso), su nombre visible — así pulsar
 * RESPONDER siempre produce un cambio visible en el campo.
 */
internal fun replyMention(comment: FeedComment): String =
    comment.author?.username?.let { "@$it " }
        ?: comment.author?.displayName?.let { "$it " } ?: ""

internal fun threadOrder(list: List<FeedComment>): List<FeedComment> {
    val byId = list.associateBy { it.id }
    fun rootIdOf(c: FeedComment): String {
        var cur = c
        var guard = 0
        while (cur.parentId != null && guard++ < 50) cur = byId[cur.parentId!!] ?: return cur.parentId!!
        return cur.id
    }
    val roots = list.filter { it.parentId == null }
    val replies = list.filter { it.parentId != null }.groupBy(::rootIdOf)
    val rootIds = roots.map { it.id }.toSet()
    return buildList {
        roots.forEach { r -> add(r); replies[r.id]?.forEach { add(it) } }
        replies.forEach { (rootId, group) -> if (rootId !in rootIds) addAll(group) }
    }
}

@Composable
internal fun FeedCommentRow(
    comment: FeedComment,
    onOpenUser: (String) -> Unit,
    onDelete: (() -> Unit)?,
    /** Denunciar comentario ajeno; null = sin bandera. */
    onReport: (() -> Unit)? = null,
    /** true = respuesta (se indenta bajo su comentario raíz). */
    isReply: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onReply: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth()
            .padding(start = if (isReply) 44.dp else 16.dp, end = 16.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val author = comment.author
        val authorUid = author?.uid ?: comment.uid
        if (author?.photoUrl != null) {
            AsyncImage(
                model = author.photoUrl, contentDescription = null,
                modifier = Modifier.size(28.dp).clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { authorUid?.let(onOpenUser) }
            )
        } else {
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { authorUid?.let(onOpenUser) }
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    author?.displayName ?: author?.username?.let { "@$it" } ?: "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable { authorUid?.let(onOpenUser) }
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text(
                    relativeTime(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            com.meteomontana.android.ui.components.MentionText(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                onOpenUser = onOpenUser
            )
            // Acciones del comentario: like (corazón + contador) y responder.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onToggleLike != null) {
                    // Zona táctil ≥40dp (padding generoso), como las banderas.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(onClick = onToggleLike)
                            .padding(horizontal = 10.dp, vertical = 11.dp)
                    ) {
                        Icon(
                            if (comment.likedByMe) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.feed_comment_like),
                            tint = if (comment.likedByMe) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        if (comment.likeCount > 0) {
                            Text(
                                "${comment.likeCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (comment.likedByMe) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (onReply != null) {
                    Text(
                        stringResource(R.string.feed_reply).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(onClick = onReply)
                            .padding(horizontal = 10.dp, vertical = 11.dp)
                    )
                }
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Outlined.Delete, contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (onReport != null) {
            // Zona táctil ≥40dp, como las banderas existentes.
            IconButton(onClick = onReport, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Outlined.Flag, contentDescription = stringResource(R.string.feed_report),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Eyebrow del tipo de post; para TICK distingue por modalidad:
 *  BOULDER → "BLOQUE HECHO", ROUTE → "VÍA HECHA", null → "HECHO". */
