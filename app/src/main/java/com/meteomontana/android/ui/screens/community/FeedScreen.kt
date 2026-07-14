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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
@Composable
fun FeedScreen(
    onOpenSchool: (schoolId: String, lineId: String?, lineName: String?) -> Unit,
    onOpenUser: (uid: String) -> Unit,
    onSearchUsers: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Frescura: recarga silenciosa cada vez que la pestaña vuelve a primer
    // plano (patrón ON_RESUME del panel admin/perfil).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshSilent()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hoja de comentarios abierta (post seleccionado).
    var commentsPost by remember { mutableStateOf<FeedPost?>(null) }
    // Confirmación de borrado del post propio.
    var deleteCandidate by remember { mutableStateOf<FeedPost?>(null) }
    // Denuncias (moderación): post pendiente de denunciar + ids ocultados al
    // instante para quien denuncia (patrón notas/comentarios).
    val moderation: com.meteomontana.android.ui.components.ModerationViewModel = hiltViewModel()
    val hiddenIds by moderation.hiddenIds.collectAsState()
    var reportPost by remember { mutableStateOf<FeedPost?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Cabecera: título + ayuda "?" + chips SIGUIENDO | TODOS | MÍAS | RANKING.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.feed_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp)
            )
            com.meteomontana.android.ui.components.HelpButton(topicKey = "community")
        }
        // Selector estilo "pestañas subrayadas" (tipo Instagram): Explorar |
        // Siguiendo | Mías a la izquierda + trofeo (RANKING) a la derecha.
        // Con el ranking activo, ninguna pestaña lleva subrayado.
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeedTextTab(stringResource(R.string.feed_tab_explore), state.tab == FeedTab.ALL) {
                viewModel.selectTab(FeedTab.ALL)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
            FeedTextTab(stringResource(R.string.feed_tab_following), state.tab == FeedTab.FOLLOWING) {
                viewModel.selectTab(FeedTab.FOLLOWING)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
            FeedTextTab(stringResource(R.string.feed_tab_mine), state.tab == FeedTab.MINE) {
                viewModel.selectTab(FeedTab.MINE)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.selectTab(FeedTab.RANKING) }) {
                Icon(
                    Icons.Outlined.EmojiEvents,
                    contentDescription = stringResource(R.string.feed_tab_ranking),
                    tint = if (state.tab == FeedTab.RANKING) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        // Fila de filtro "Mostrar:" (solo en las vistas de feed, no en ranking).
        if (state.tab != FeedTab.RANKING) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.feed_filter_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterPill(stringResource(R.string.feed_filter_all), state.filter == FeedFilter.ALL) {
                    viewModel.selectFilter(FeedFilter.ALL)
                }
                FilterPill(stringResource(R.string.feed_filter_sends), state.filter == FeedFilter.SENDS) {
                    viewModel.selectFilter(FeedFilter.SENDS)
                }
                FilterPill(stringResource(R.string.feed_filter_new), state.filter == FeedFilter.NEW_BLOCKS) {
                    viewModel.selectFilter(FeedFilter.NEW_BLOCKS)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        if (state.tab == FeedTab.RANKING) {
            // Ranking de contribuidores: la pantalla existente, embebida sin cabecera.
            com.meteomontana.android.ui.screens.users.CommunityScreen(
                onBack = {},
                onUserClick = onOpenUser,
                showHeader = false
            )
        } else {
            FeedList(
                state = state,
                hiddenIds = hiddenIds,
                viewModel = viewModel,
                onOpenSchool = onOpenSchool,
                onOpenUser = onOpenUser,
                onSearchUsers = onSearchUsers,
                onOpenComments = { commentsPost = it },
                onDeletePost = { deleteCandidate = it },
                onReportPost = { reportPost = it }
            )
        }
    }

    commentsPost?.let { post ->
        FeedCommentsSheet(
            post = post,
            loadComments = viewModel::loadComments,
            addComment = viewModel::addComment,
            deleteComment = viewModel::deleteComment,
            toggleCommentLike = viewModel::toggleCommentLike,
            onOpenUser = onOpenUser,
            onDismiss = { commentsPost = null }
        )
    }

    // Hoja de denuncia de un post ajeno (target nuevo FEED_POST; mismo
    // endpoint /api/reports que comentarios de vías, 409 idempotente).
    reportPost?.let { post ->
        com.meteomontana.android.ui.components.ReportDialog(
            title = stringResource(R.string.feed_report_post),
            authorLabel = post.author.displayName ?: post.author.username?.let { "@$it" },
            onReport = { reason, alsoBlock ->
                moderation.report(
                    "FEED_POST", post.id.toString(), reason,
                    alsoBlockUid = if (alsoBlock) post.author.uid else null
                )
                reportPost = null
            },
            onDismiss = { reportPost = null }
        )
    }

    deleteCandidate?.let { post ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.feed_delete_post)) },
            text = { Text(stringResource(R.string.feed_delete_post_confirm)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.deletePost(post)
                    deleteCandidate = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun FeedList(
    state: FeedUiState,
    hiddenIds: Set<String>,
    viewModel: FeedViewModel,
    onOpenSchool: (String, String?, String?) -> Unit,
    onOpenUser: (String) -> Unit,
    onSearchUsers: () -> Unit,
    onOpenComments: (FeedPost) -> Unit,
    onDeletePost: (FeedPost) -> Unit,
    onReportPost: (FeedPost) -> Unit
) {
    // Lo denunciado desaparece al instante para quien denuncia (Apple 1.2) y
    // el filtro "Mostrar:" solo OCULTA en cliente (la paginación trae todo).
    val visiblePosts = state.posts
        .filter { it.id.toString() !in hiddenIds }
        .filter { matchesFilter(it, state.filter) }
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.offline -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                // Estado sin conexión estándar (icono nube + reintentar).
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        androidx.compose.material.icons.Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        stringResource(R.string.feed_offline),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        stringResource(R.string.common_retry), style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                            .clickable { viewModel.refreshSilent() }.padding(8.dp)
                    )
                }
            }
            state.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(R.string.common_retry), style = EyebrowTextStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                            .clickable { viewModel.refreshSilent() }.padding(8.dp)
                    )
                }
            }
            state.posts.isEmpty() -> {
                // Estado vacío: LazyColumn de un item para que el pull-to-refresh funcione.
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 80.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                stringResource(
                                    when (state.tab) {
                                        FeedTab.FOLLOWING -> R.string.feed_empty_following
                                        FeedTab.MINE -> R.string.feed_empty_mine
                                        else -> R.string.feed_empty_all
                                    }
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (state.tab == FeedTab.FOLLOWING) {
                                Text(
                                    stringResource(R.string.feed_empty_following_cta),
                                    style = EyebrowTextStyle,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 16.dp)
                                        .clickable(onClick = onSearchUsers).padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hay posts cargados pero ninguno pasa el filtro → mensaje
                // genérico; el sentinel de abajo sigue paginando por si lo
                // filtrado está en páginas más antiguas.
                if (visiblePosts.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.feed_empty_filtered),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                items(visiblePosts, key = { it.id }) { post ->
                    FeedPostCard(
                        post = post,
                        onOpenSchool = onOpenSchool,
                        onOpenUser = onOpenUser,
                        onToggleLike = { viewModel.toggleLike(post) },
                        onOpenComments = { onOpenComments(post) },
                        onDelete = { onDeletePost(post) },
                        onReport = { onReportPost(post) }
                    )
                }
                if (!state.endReached) {
                    item {
                        // Sentinel: al componerse (final de la lista) pide otra página.
                        LaunchedEffect(state.posts.size) { viewModel.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** ¿El post pasa el filtro "Mostrar:"? */
private fun matchesFilter(post: FeedPost, filter: FeedFilter): Boolean = when (filter) {
    FeedFilter.ALL -> true
    FeedFilter.SENDS -> post.kind == FeedKind.TICK || post.kind == FeedKind.PROJECT_DONE
    FeedFilter.NEW_BLOCKS -> post.kind == FeedKind.NEW_BLOCK || post.kind == FeedKind.NEW_LINE
}

/** Pestaña de texto con subrayado 2dp Terra cuando está activa (tipo Instagram). */
@Composable
private fun FeedTextTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .clip(RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
        )
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(
                if (selected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}

/** Píldora pequeña del filtro "Mostrar:": activa fondo Terra + texto blanco,
 *  inactiva borde Rule. */
@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(2.dp)
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) androidx.compose.ui.graphics.Color.White
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface
            )
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** Tarjeta de actividad: cabecera + topo (foto con SOLO la línea del post) +
 *  acciones. Reutilizada por FeedScreen y por el detalle de post (push). */
@Composable
internal fun FeedPostCard(
    post: FeedPost,
    onOpenSchool: (String, String?, String?) -> Unit,
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
                    post.schoolId?.let { onOpenSchool(it, post.lineId, post.lineName) }
                }
            ) {
                TopoPhotoCanvas(
                    photoUrl = post.photoPath!!,
                    lines = if (points.isNotEmpty()) listOf(
                        TopoLine(
                            name = post.lineName, grade = post.grade,
                            startType = post.startType, points = points
                        )
                    ) else emptyList()
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
                .clickable { post.schoolId?.let { onOpenSchool(it, post.lineId, post.lineName) } }
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
                Text(
                    caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = captionMaxLines,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
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
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding()
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
                    items(threadOrder(list.filter { it.id !in hiddenIds }), key = { it.id }) { comment ->
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
            Text(
                comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            // Acciones del comentario: like (corazón + contador) y responder.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onToggleLike != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .clickable(onClick = onToggleLike)
                            .padding(horizontal = 6.dp, vertical = 6.dp)
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
                            .padding(horizontal = 6.dp, vertical = 6.dp)
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

/** "hace 2 h" a partir de un createdAt "yyyy-MM-ddTHH:mm:ss" (hora del servidor). */
@Composable
internal fun relativeTime(createdAt: String): String {
    val minutes = remember(createdAt) {
        runCatching {
            val t = java.time.LocalDateTime.parse(createdAt.take(19))
            java.time.Duration.between(t, java.time.LocalDateTime.now()).toMinutes()
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
