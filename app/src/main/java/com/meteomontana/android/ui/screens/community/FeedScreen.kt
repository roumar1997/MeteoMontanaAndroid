@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.meteomontana.android.ui.screens.community

import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.layout.heightIn
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
    // blockId: en posts de piedra nueva (sin vía) abre esa piedra directamente.
    onOpenSchool: (schoolId: String, lineId: String?, lineName: String?, blockId: String?) -> Unit,
    onOpenUser: (uid: String) -> Unit,
    onSearchUsers: () -> Unit,
    // Abrir el detalle del post (pantalla PUBLICACIÓN a pantalla completa). Los
    // comentarios se ven ahí, NO en un ModalBottomSheet: la hoja de Material se
    // dibuja en su propia ventana y no empuja el campo por encima del teclado
    // (el texto que escribías quedaba tapado). El detalle sí lo hace bien.
    onOpenPost: (postId: String) -> Unit,
    // true cuando la pestaña Feed está seleccionada Y sin overlay encima. Con las
    // tabs keep-alive, cambiar de pestaña NO dispara ON_RESUME ni recompone →
    // publicabas un ascenso en Escuelas y el Feed seguía enseñando la lista vieja
    // ("mis publicaciones nuevas no salen"). Mismo patrón `visible` que
    // ProfileScreen/MeetupsScreen (equivalente al .task {} de iOS).
    visible: Boolean = true,
    // Sube cada vez que se vuelve a tocar la pestaña Feed estando YA en Feed
    // (mismo patrón que SchoolListScreen.volverArribaSignal — lo publica
    // MainScreen). La lista vuelve arriba del todo, como Instagram/Twitter.
    volverArribaSignal: Int = 0,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Frescura: recarga silenciosa al MOSTRARSE la pestaña (visible) y también
    // en ON_RESUME (volver de background con la pestaña ya seleccionada).
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible) viewModel.refreshSilent()
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshSilent()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hoja de comentarios abierta (post seleccionado).
    // Confirmación de borrado del post propio.
    var deleteCandidate by remember { mutableStateOf<FeedPost?>(null) }
    // Denuncias (moderación): post pendiente de denunciar + ids ocultados al
    // instante para quien denuncia (patrón notas/comentarios).
    val moderation: com.meteomontana.android.ui.components.ModerationViewModel = hiltViewModel()
    val hiddenIds by moderation.hiddenIds.collectAsStateWithLifecycle()
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
        // Pestañas estilo "mochila" (mismas celdas planas que las stats de
        // Perfil): borde fino Rule, activa con borde interior Terra y texto
        // Terra — reemplaza el subrayado tipo Instagram de antes.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            com.meteomontana.android.ui.components.MochilaCard(
                stringResource(R.string.feed_tab_explore), state.tab == FeedTab.ALL,
                Modifier.weight(1f)
            ) { viewModel.selectTab(FeedTab.ALL) }
            com.meteomontana.android.ui.components.MochilaCard(
                stringResource(R.string.feed_tab_following), state.tab == FeedTab.FOLLOWING,
                Modifier.weight(1f)
            ) { viewModel.selectTab(FeedTab.FOLLOWING) }
            com.meteomontana.android.ui.components.MochilaCard(
                stringResource(R.string.feed_tab_mine), state.tab == FeedTab.MINE,
                Modifier.weight(1f)
            ) { viewModel.selectTab(FeedTab.MINE) }
            com.meteomontana.android.ui.components.MochilaIconCard(
                icon = Icons.Outlined.EmojiEvents,
                contentDescription = stringResource(R.string.feed_tab_ranking),
                selected = state.tab == FeedTab.RANKING,
                modifier = Modifier.weight(1f)
            ) { viewModel.selectTab(FeedTab.RANKING) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        // Fila de filtro "Mostrar:" (solo en las vistas de feed, no en ranking).
        // Mismo estilo mochila que las pestañas de arriba.
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
                com.meteomontana.android.ui.components.MochilaCard(stringResource(R.string.feed_filter_all), state.filter == FeedFilter.ALL) {
                    viewModel.selectFilter(FeedFilter.ALL)
                }
                com.meteomontana.android.ui.components.MochilaCard(stringResource(R.string.feed_filter_sends), state.filter == FeedFilter.SENDS) {
                    viewModel.selectFilter(FeedFilter.SENDS)
                }
                com.meteomontana.android.ui.components.MochilaCard(stringResource(R.string.feed_filter_new), state.filter == FeedFilter.NEW_BLOCKS) {
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
                onOpenComments = { onOpenPost(it.id.toString()) },
                onDeletePost = { deleteCandidate = it },
                onReportPost = { reportPost = it },
                volverArribaSignal = volverArribaSignal
            )
        }
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
    onOpenSchool: (String, String?, String?, String?) -> Unit,
    onOpenUser: (String) -> Unit,
    onSearchUsers: () -> Unit,
    onOpenComments: (FeedPost) -> Unit,
    onDeletePost: (FeedPost) -> Unit,
    onReportPost: (FeedPost) -> Unit,
    volverArribaSignal: Int = 0
) {
    // Lo denunciado desaparece al instante para quien denuncia (Apple 1.2) y
    // el filtro "Mostrar:" solo OCULTA en cliente (la paginación trae todo).
    val visiblePosts = state.posts
        .filter { "FEED_POST:${it.id}" !in hiddenIds }
        .filter { matchesFilter(it, state.filter) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(volverArribaSignal) {
        if (volverArribaSignal > 0) listState.animateScrollToItem(0)
    }
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
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, top = 12.dp,
                    // Hueco al final: el contenido corre por detrás de la
                    // cápsula y sin esto el último post queda tapado.
                    bottom = 12.dp + com.meteomontana.android.ui.components.LocalTabBarInset.current
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
